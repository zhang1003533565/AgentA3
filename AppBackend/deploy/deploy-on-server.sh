#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-master}"
ENV_FILE="${ENV_FILE:-deploy/.env}"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/compose.submission.yml}"
DEPLOY_PRUNE_DOCKER="${DEPLOY_PRUNE_DOCKER:-true}"
DEPLOY_PRUNE_UNTIL="${DEPLOY_PRUNE_UNTIL:-24h}"

# BACKEND_IMAGE, AI_SERVER_IMAGE, WEB_IMAGE, IMAGE_TAG, JWT_SECRET,
# AI_INTERNAL_TOKEN and MYSQL_ROOT_PASSWORD may come from CI. Docker Compose
# gives those process values precedence; otherwise it reads deploy/.env.

cd "$REPO_DIR"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[deploy] Missing $ENV_FILE. Copy deploy/.env.example to deploy/.env and fill real values." >&2
  exit 1
fi

git fetch origin "$DEPLOY_BRANCH"
git checkout "$DEPLOY_BRANCH"
git pull --ff-only origin "$DEPLOY_BRANCH"

compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
"${compose[@]}" config --quiet
"${compose[@]}" pull config-guard mysql redis backend ai-server web
"${compose[@]}" run --rm --no-deps config-guard
"${compose[@]}" up -d --remove-orphans

BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://127.0.0.1:${BACKEND_PORT:-8080}}" \
AI_BASE_URL="${AI_BASE_URL:-http://127.0.0.1:${AI_PORT:-8081}}" \
WEB_BASE_URL="${WEB_BASE_URL:-http://127.0.0.1:${WEB_PORT:-3000}}" \
AI_INTERNAL_TOKEN="${AI_INTERNAL_TOKEN:-}" \
bash deploy/verify.sh

if [[ "$DEPLOY_PRUNE_DOCKER" == "true" ]]; then
  echo "[deploy] Pruning unused Docker images and build cache older than ${DEPLOY_PRUNE_UNTIL}; volumes are preserved."
  docker image prune -af --filter "until=${DEPLOY_PRUNE_UNTIL}"
  docker builder prune -af --filter "until=${DEPLOY_PRUNE_UNTIL}"
else
  echo "[deploy] Docker prune skipped because DEPLOY_PRUNE_DOCKER=${DEPLOY_PRUNE_DOCKER}."
fi
