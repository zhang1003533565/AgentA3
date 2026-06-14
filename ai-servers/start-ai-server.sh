#!/usr/bin/env bash
set -euo pipefail

PROJECT_NAME="smart-campus-ai"
SERVER_HOST="127.0.0.1"
SERVER_PORT="8081"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
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
  --host <host>                  API bind host, default 127.0.0.1.
  --port <port>                  Python API port, default 8081.
  -h, --help                     Show help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      SERVER_HOST="${2:-}"
      [[ -n "$SERVER_HOST" ]] || fail "--host requires a value"
      shift 2
      ;;
    --port)
      SERVER_PORT="${2:-}"
      [[ -n "$SERVER_PORT" ]] || fail "--port requires a value"
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

start_ai_server() {
  log "Starting AI Server at http://${SERVER_HOST}:${SERVER_PORT} ..."
  exec python -m uvicorn app.main:app --host "$SERVER_HOST" --port "$SERVER_PORT"
}

main() {
  ensure_python
  ensure_venv
  install_requirements
  start_ai_server
}

main "$@"
