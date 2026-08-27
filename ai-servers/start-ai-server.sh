#!/usr/bin/env bash
set -euo pipefail
shopt -s extglob

PROJECT_NAME="smart-campus-ai"
SERVER_HOST="127.0.0.1"
SERVER_PORT="8081"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_ENV_FILE="$(cd "$SCRIPT_DIR/.." && pwd)/.env"
cd "$SCRIPT_DIR"
export UV_LINK_MODE="${UV_LINK_MODE:-copy}"

log() {
  printf '[%s] %s\n' "$PROJECT_NAME" "$*"
}

fail() {
  printf '[%s] ERROR: %s\n' "$PROJECT_NAME" "$*" >&2
  exit 1
}

load_root_env() {
  if [[ ! -f "$ROOT_ENV_FILE" ]]; then
    log "No root .env found at '$ROOT_ENV_FILE'. Using existing process environment."
    return
  fi

  local loaded=()
  local raw_line line name value
  while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
    line="${raw_line%$'\r'}"
    line="${line##+([[:space:]])}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    if [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
      name="${BASH_REMATCH[1]}"
      value="${BASH_REMATCH[2]}"
      if [[ "$value" == '"'*'"' || "$value" == "'"*"'" ]]; then
        value="${value:1:${#value}-2}"
      fi
      if [[ "$name" == LLM_* || -z "${!name+x}" ]]; then
        export "$name=$value"
        loaded+=("$name")
      fi
    fi
  done < "$ROOT_ENV_FILE"

  if (( ${#loaded[@]} > 0 )); then
    log "Loaded root .env keys: ${loaded[*]}"
  else
    log "Root .env found, but no environment keys were loaded."
  fi
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

ensure_uv() {
  command -v uv >/dev/null 2>&1 || fail "uv is not available. Install uv first: https://docs.astral.sh/uv/getting-started/installation/"
}

sync_dependencies() {
  log "Syncing Python dependencies with uv..."
  uv sync
}

start_ai_server() {
  log "Starting AI Server at http://${SERVER_HOST}:${SERVER_PORT} ..."
  exec uv run python -m uvicorn app.main:app --host "$SERVER_HOST" --port "$SERVER_PORT"
}

main() {
  load_root_env
  ensure_uv
  sync_dependencies
  start_ai_server
}

main "$@"
