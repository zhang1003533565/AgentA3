#!/usr/bin/env bash
set -euo pipefail

PROJECT_NAME="smart-campus-ai"
AI_SERVER_HOST="${AI_SERVER_HOST:-127.0.0.1}"
PYTHON_SERVER_PORT="${PYTHON_SERVER_PORT:-8081}"
RAG_VECTOR_STORE_BACKEND="${RAG_VECTOR_STORE_BACKEND:-milvus}"
RAG_DOCKER_WAIT_SECONDS="${RAG_DOCKER_WAIT_SECONDS:-90}"
RAG_DOCKER_PULL_RETRIES="${RAG_DOCKER_PULL_RETRIES:-3}"
START_DOCKER=1
BUILD_KB=0

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
cd "$SCRIPT_DIR"

log() {
  printf '[%s] %s\n' "$PROJECT_NAME" "$*"
}

fail() {
  printf '[%s] ERROR: %s\n' "$PROJECT_NAME" "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: ./start-ai-server.sh [options]

Options:
  --backend <milvus|local_jsonl>  Select RAG vector store backend, default milvus.
  --build-kb                     Build knowledge base before starting API.
  --host <host>                  API bind host, default 127.0.0.1.
  --no-docker                    Do not start Docker services.
  --port <port>                  Python API port, default 8081.
  -h, --help                     Show help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backend)
      RAG_VECTOR_STORE_BACKEND="${2:-}"
      [[ -n "$RAG_VECTOR_STORE_BACKEND" ]] || fail "--backend requires a value"
      shift 2
      ;;
    --build-kb)
      BUILD_KB=1
      shift
      ;;
    --host)
      AI_SERVER_HOST="${2:-}"
      [[ -n "$AI_SERVER_HOST" ]] || fail "--host requires a value"
      shift 2
      ;;
    --no-docker)
      START_DOCKER=0
      shift
      ;;
    --port)
      PYTHON_SERVER_PORT="${2:-}"
      [[ -n "$PYTHON_SERVER_PORT" ]] || fail "--port requires a value"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown option: $1"
      ;;
  esac
done

find_compose() {
  if docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose)
  elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE=(docker-compose)
  else
    fail "Docker Compose is not available. Install Docker Desktop first."
  fi
}

wait_for_docker() {
  if docker info >/dev/null 2>&1; then
    return
  fi

  if [[ "$(uname -s)" == "Darwin" ]]; then
    log "Docker is not running; opening Docker Desktop..."
    open -a Docker >/dev/null 2>&1 || true
  fi

  log "Waiting for Docker to become available..."
  for _ in $(seq 1 60); do
    if docker info >/dev/null 2>&1; then
      return
    fi
    sleep 2
  done

  fail "Docker is still unavailable. Start Docker Desktop and run this script again."
}

wait_for_milvus() {
  log "Waiting for Milvus at http://localhost:9091/healthz ..."
  for _ in $(seq 1 "$RAG_DOCKER_WAIT_SECONDS"); do
    if command -v curl >/dev/null 2>&1 && curl -fsS http://localhost:9091/healthz >/dev/null 2>&1; then
      return
    fi
    sleep 1
  done

  fail "Milvus did not become ready within ${RAG_DOCKER_WAIT_SECONDS}s."
}

pull_docker_images() {
  local attempt
  for attempt in $(seq 1 "$RAG_DOCKER_PULL_RETRIES"); do
    log "Pulling RAG Docker images (${attempt}/${RAG_DOCKER_PULL_RETRIES})..."
    if "${COMPOSE[@]}" -f "$RAG_COMPOSE_FILE" pull; then
      return
    fi
    log "Docker image pull failed; retrying in 5 seconds..."
    sleep 5
  done

  fail "Failed to pull RAG Docker images. Check Docker network access and rerun this script."
}

ensure_python() {
  command -v python3 >/dev/null 2>&1 || fail "python3 is not available."
}

ensure_venv() {
  if [[ ! -d ".venv" ]]; then
    log "Creating Python virtual environment..."
    python3 -m venv .venv
  fi
  # shellcheck disable=SC1091
  source .venv/bin/activate
}

install_requirements() {
  log "Installing Python dependencies..."
  python -m pip install -r requirements.txt
}

start_docker_services() {
  [[ "$START_DOCKER" -eq 1 ]] || return 0
  [[ "$RAG_VECTOR_STORE_BACKEND" == "milvus" ]] || return 0
  [[ -f "$RAG_COMPOSE_FILE" ]] || fail "Missing compose file: $RAG_COMPOSE_FILE"
  command -v docker >/dev/null 2>&1 || fail "Docker is not installed."
  find_compose
  wait_for_docker
  pull_docker_images
  log "Starting RAG Docker services..."
  "${COMPOSE[@]}" -f "$RAG_COMPOSE_FILE" up -d
  wait_for_milvus
}

build_knowledge_base() {
  [[ "$BUILD_KB" -eq 1 ]] || return 0
  log "Building knowledge base with backend=${RAG_VECTOR_STORE_BACKEND} ..."
  python scripts/build_knowledge_base.py --backend "$RAG_VECTOR_STORE_BACKEND"
}

start_ai_server() {
  export AI_SERVER_HOST
  export PYTHON_SERVER_PORT
  export RAG_VECTOR_STORE_BACKEND
  log "Starting AI Server at http://${AI_SERVER_HOST}:${PYTHON_SERVER_PORT} ..."
  exec python -m uvicorn app.main:app --host "$AI_SERVER_HOST" --port "$PYTHON_SERVER_PORT"
}

main() {
  ensure_python
  ensure_venv
  install_requirements
  start_docker_services
  build_knowledge_base
  start_ai_server
}

main "$@"
