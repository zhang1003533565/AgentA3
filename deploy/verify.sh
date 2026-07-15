#!/usr/bin/env bash
set -euo pipefail

backend_url="${BACKEND_BASE_URL:-http://localhost:8080}"
ai_url="${AI_BASE_URL:-http://localhost:8081}"
web_url="${WEB_BASE_URL:-http://localhost:3000}"

check_url() {
  local label="$1"
  local url="$2"
  if ! curl --fail --silent --show-error --max-time 10 "$url" >/dev/null; then
    echo "[FAIL] ${label}: ${url}" >&2
    return 1
  fi
  echo "[PASS] ${label}: ${url}"
}

command -v curl >/dev/null 2>&1 || {
  echo "[FAIL] curl is required for deployment verification" >&2
  exit 1
}

check_url "backend" "${backend_url}/actuator/health"
check_url "ai-server" "${ai_url}/healthz"
check_url "web" "${web_url}"

echo "All submission services are reachable."
