#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-master}"
ENV_FILE="${ENV_FILE:-deploy/.env}"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/compose.submission.yml}"
DEPLOY_PRUNE_DOCKER="${DEPLOY_PRUNE_DOCKER:-true}"
DEPLOY_FORCE_RELEASE_PORTS="${DEPLOY_FORCE_RELEASE_PORTS:-}"
DEPLOY_RELEASE_PORTS="${DEPLOY_RELEASE_PORTS:-}"
BACKEND_PORT="${BACKEND_PORT:-}"
AI_PORT="${AI_PORT:-}"
WEB_PORT="${WEB_PORT:-}"

# BACKEND_IMAGE, AI_SERVER_IMAGE, WEB_IMAGE, MYSQL_IMAGE, REDIS_IMAGE,
# IMAGE_TAG, BACKEND_PORT, AI_PORT, JWT_SECRET, AI_INTERNAL_TOKEN and
# MYSQL_ROOT_PASSWORD may come from CI. Docker Compose gives those process
# values precedence; otherwise it reads deploy/.env.

cd "$REPO_DIR"

release_port() {
  local port="$1"
  if [[ ! "$port" =~ ^[0-9]+$ ]]; then
    return
  fi
  echo "[deploy] Releasing host port ${port}."
  local container_ids
  container_ids="$(docker ps -q --filter "publish=${port}" || true)"
  if [[ -n "$container_ids" ]]; then
    docker rm -f $container_ids
  fi
  if command -v fuser >/dev/null 2>&1; then
    fuser -k -n tcp "$port" >/dev/null 2>&1 || true
  elif command -v lsof >/dev/null 2>&1; then
    local pids
    pids="$(lsof -ti "tcp:${port}" || true)"
    if [[ -n "$pids" ]]; then
      kill -9 $pids || true
    fi
  fi
}

release_configured_ports() {
  if [[ "$DEPLOY_FORCE_RELEASE_PORTS" != "true" ]]; then
    echo "[deploy] Forced host port release skipped because DEPLOY_FORCE_RELEASE_PORTS=${DEPLOY_FORCE_RELEASE_PORTS}."
    return
  fi
  local seen=" "
  local port
  for port in $DEPLOY_RELEASE_PORTS; do
    if [[ "$seen" == *" ${port} "* ]]; then
      continue
    fi
    seen="${seen}${port} "
    release_port "$port"
  done
}

dump_deploy_diagnostics() {
  echo "[deploy] Compose service status:" >&2
  "${compose[@]}" ps -a >&2 || true
  local service
  for service in backend mysql redis ai-server web config-guard; do
    echo "[deploy] Last logs for ${service}:" >&2
    "${compose[@]}" logs --no-color --tail=200 "$service" >&2 || true
  done
  echo "[deploy] Docker containers publishing deployment ports:" >&2
  docker ps --format 'table {{.ID}}\t{{.Names}}\t{{.Status}}\t{{.Ports}}' >&2 || true
}

prune_docker_artifacts() {
  local phase="$1"
  if [[ "$DEPLOY_PRUNE_DOCKER" == "true" ]]; then
    echo "[deploy] Pruning all unused Docker images and build cache ${phase}; volumes are preserved."
    docker image prune -af
    docker builder prune -af
  else
    echo "[deploy] Docker prune ${phase} skipped because DEPLOY_PRUNE_DOCKER=${DEPLOY_PRUNE_DOCKER}."
  fi
}

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[deploy] Missing $ENV_FILE. Copy deploy/.env.example to deploy/.env and fill real values." >&2
  exit 1
fi

env_file_value() {
  awk -F= -v key="$1" '
    $1 == key {
      value = substr($0, length(key) + 2)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      print value
      exit
    }
  ' "$ENV_FILE"
}

BACKEND_PORT="${BACKEND_PORT:-$(env_file_value BACKEND_PORT)}"
AI_PORT="${AI_PORT:-$(env_file_value AI_PORT)}"
WEB_PORT="${WEB_PORT:-$(env_file_value WEB_PORT)}"
DEPLOY_FORCE_RELEASE_PORTS="${DEPLOY_FORCE_RELEASE_PORTS:-$(env_file_value DEPLOY_FORCE_RELEASE_PORTS)}"
DEPLOY_FORCE_RELEASE_PORTS="${DEPLOY_FORCE_RELEASE_PORTS:-true}"
DEPLOY_RELEASE_PORTS="${DEPLOY_RELEASE_PORTS:-$(env_file_value DEPLOY_RELEASE_PORTS)}"
DEPLOY_RELEASE_PORTS="${DEPLOY_RELEASE_PORTS:-8080 8081 18080 18081 ${BACKEND_PORT:-} ${AI_PORT:-} ${WEB_PORT:-3000}}"

git fetch origin "$DEPLOY_BRANCH"
git checkout -f -B "$DEPLOY_BRANCH" "origin/$DEPLOY_BRANCH"
git reset --hard "origin/$DEPLOY_BRANCH"

missing_required=()
for name in MYSQL_ROOT_PASSWORD JWT_SECRET AI_INTERNAL_TOKEN; do
  if [[ -z "${!name:-}" ]]; then
    file_value="$(env_file_value "$name")"
    if [[ -z "$file_value" || "$file_value" == CHANGE_ME* ]]; then
      missing_required+=("$name")
    fi
  fi
done
if [[ "${#missing_required[@]}" -gt 0 ]]; then
  printf '[deploy] Missing required deployment secrets: %s\n' "${missing_required[*]}" >&2
  printf '[deploy] Set them as GitHub Actions secrets or fill real values in %s on the server.\n' "$ENV_FILE" >&2
  exit 1
fi

compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
"${compose[@]}" config --quiet
prune_docker_artifacts "before image pull"
"${compose[@]}" pull config-guard mysql redis backend ai-server web
"${compose[@]}" run --rm --no-deps config-guard
"${compose[@]}" down --remove-orphans
release_configured_ports
if ! "${compose[@]}" up -d --remove-orphans; then
  dump_deploy_diagnostics
  exit 1
fi

if ! BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://127.0.0.1:${BACKEND_PORT:-18080}}" \
  AI_BASE_URL="${AI_BASE_URL:-http://127.0.0.1:${AI_PORT:-18081}}" \
  WEB_BASE_URL="${WEB_BASE_URL:-http://127.0.0.1:${WEB_PORT:-3000}}" \
  AI_INTERNAL_TOKEN="${AI_INTERNAL_TOKEN:-}" \
  bash deploy/verify.sh; then
  dump_deploy_diagnostics
  exit 1
fi

prune_docker_artifacts "after successful deployment"
