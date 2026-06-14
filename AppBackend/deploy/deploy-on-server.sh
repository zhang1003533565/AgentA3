#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-master}"
ENV_FILE="${ENV_FILE:-AppBackend/deploy/.env}"
COMPOSE_FILE="${COMPOSE_FILE:-AppBackend/docker-compose.yml}"

cd "$REPO_DIR"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[deploy] Missing $ENV_FILE. Copy AppBackend/deploy/.env.example to AppBackend/deploy/.env and fill real values." >&2
  exit 1
fi

git fetch origin "$DEPLOY_BRANCH"
git checkout "$DEPLOY_BRANCH"
git pull --ff-only origin "$DEPLOY_BRANCH"

docker compose --profile deploy --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build --remove-orphans
