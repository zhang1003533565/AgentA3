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

check_json_up() {
  local label="$1"
  local url="$2"
  local header="${3:-}"
  local body
  local curl_args=(--fail --silent --show-error --max-time 10)
  if [[ -n "$header" ]]; then
    curl_args+=(-H "$header")
  fi
  if ! body="$(curl "${curl_args[@]}" "$url")"; then
    echo "[FAIL] ${label}: ${url}" >&2
    return 1
  fi
  if [[ ! "$body" =~ \"status\"[[:space:]]*:[[:space:]]*\"UP\" ]]; then
    echo "[FAIL] ${label}: response status is not UP" >&2
    return 1
  fi
  echo "[PASS] ${label}: ${url}"
}

check_business_success() {
  local label="$1"
  local url="$2"
  local header="$3"
  local body
  if ! body="$(curl --fail --silent --show-error --max-time 10 -H "$header" "$url")"; then
    echo "[FAIL] ${label}: ${url}" >&2
    return 1
  fi
  if [[ ! "$body" =~ \"code\"[[:space:]]*:[[:space:]]*200 ]]; then
    echo "[FAIL] ${label}: business response code is not 200" >&2
    return 1
  fi
  echo "[PASS] ${label}: ${url}"
}

check_status_code() {
  local label="$1"
  local url="$2"
  local expected="$3"
  local actual
  actual="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' --max-time 10 "$url")"
  if [[ "$actual" != "$expected" ]]; then
    echo "[FAIL] ${label}: expected HTTP ${expected}, got ${actual}" >&2
    return 1
  fi
  echo "[PASS] ${label}: HTTP ${actual}"
}

command -v curl >/dev/null 2>&1 || {
  echo "[FAIL] curl is required for deployment verification" >&2
  exit 1
}

check_url "backend" "${backend_url}/actuator/health"
check_json_up "backend dependencies" "${backend_url}/actuator/readiness"
check_url "ai-server" "${ai_url}/healthz"
if [[ -n "${AI_INTERNAL_TOKEN:-}" ]]; then
  check_json_up \
    "ai-server Redis readiness" \
    "${ai_url}/internal/readiness" \
    "X-AI-Internal-Token: ${AI_INTERNAL_TOKEN}"
else
  echo "[INFO] direct /internal/readiness probe skipped; backend readiness already verifies the shared Java→Python token"
fi
check_url "web" "${web_url}"

if [[ -n "${SMOKE_TOKEN:-}" ]]; then
  authorization="${SMOKE_TOKEN}"
  if [[ "$authorization" != Bearer\ * ]]; then
    authorization="Bearer ${authorization}"
  fi
  check_business_success \
    "authenticated campus business smoke" \
    "${backend_url}/api/auth/current-user" \
    "Authorization: ${authorization}"
else
  check_status_code \
    "campus authentication boundary smoke" \
    "${backend_url}/api/auth/current-user" \
    "401"
  echo "[INFO] set SMOKE_TOKEN to additionally verify an authenticated campus request"
fi

echo "All submission readiness and minimum business smoke checks passed."
