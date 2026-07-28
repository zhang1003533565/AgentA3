#!/usr/bin/env bash
set -euo pipefail

PROJECT_NAME="smart-campus"
MYSQL_SERVICE="mysql"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"
MYSQL_DATABASE="${MYSQL_DATABASE:-smart-campus}"
MYSQL_CHARSET="${MYSQL_CHARSET:-utf8mb4}"
MYSQL_COLLATION="${MYSQL_COLLATION:-utf8mb4_unicode_ci}"
MYSQL_WAIT_SECONDS="${MYSQL_WAIT_SECONDS:-90}"
ADMINER_PORT="${ADMINER_PORT:-}"
# Host port mapped from container's 3306 (see docker-compose.yml). Defaults to 3307.
MYSQL_HOST_PORT="${MYSQL_HOST_PORT:-3307}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

log() {
  printf '[%s] %s\n' "$PROJECT_NAME" "$*"
}

fail() {
  printf '[%s] ERROR: %s\n' "$PROJECT_NAME" "$*" >&2
  exit 1
}

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

port_is_available() {
  local port="$1"

  if command -v lsof >/dev/null 2>&1; then
    ! lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
    return
  fi

  if command -v nc >/dev/null 2>&1; then
    ! nc -z 127.0.0.1 "$port" >/dev/null 2>&1
    return
  fi

  return 0
}

choose_adminer_port() {
  if [[ -n "$ADMINER_PORT" ]]; then
    export ADMINER_PORT
    log "Using configured Adminer port: ${ADMINER_PORT}"
    return
  fi

  for port in $(seq 7070 7080); do
    if port_is_available "$port"; then
      ADMINER_PORT="$port"
      export ADMINER_PORT
      log "Using Adminer port: ${ADMINER_PORT}"
      return
    fi
  done

  fail "No available Adminer port found in range 7070-7080. Set ADMINER_PORT and run again."
}

wait_for_mysql() {
  log "Waiting for MySQL container..."
  for _ in $(seq 1 "$MYSQL_WAIT_SECONDS"); do
    if "${COMPOSE[@]}" exec -T "$MYSQL_SERVICE" \
      mysqladmin ping -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent >/dev/null 2>&1; then
      return
    fi
    sleep 1
  done

  fail "MySQL did not become ready within ${MYSQL_WAIT_SECONDS}s."
}

ensure_database() {
  log "Ensuring database '${MYSQL_DATABASE}' exists..."
  "${COMPOSE[@]}" exec -T "$MYSQL_SERVICE" \
    mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" \
    -e "CREATE DATABASE IF NOT EXISTS \`${MYSQL_DATABASE}\` DEFAULT CHARACTER SET ${MYSQL_CHARSET} COLLATE ${MYSQL_COLLATION};" \
    || fail "Failed to create or verify database '${MYSQL_DATABASE}'."
}

wait_for_redis() {
  log "Waiting for Redis container..."
  for _ in $(seq 1 "$MYSQL_WAIT_SECONDS"); do
    if "${COMPOSE[@]}" exec -T redis redis-cli ping >/dev/null 2>&1; then
      return
    fi
    sleep 1
  done

  fail "Redis did not become ready within ${MYSQL_WAIT_SECONDS}s."
}

ensure_backend_tools() {
  if ! command -v java >/dev/null 2>&1; then
    fail "Java is not available. This backend requires JDK 21. On macOS: brew install openjdk@21"
  fi

  if ! command -v mvn >/dev/null 2>&1; then
    fail "Maven is not available. This backend requires Maven 3.9+. On macOS: brew install maven"
  fi
}

ensure_datasource_url() {
  # Host MySQL port (3307) is mapped from container's 3306 via docker-compose.yml.
  # characterEncoding must use the Java charset name "UTF-8" (NOT "utf8mb4" --
  # MySQL Connector/J rejects MySQL charset names here with UnsupportedEncodingException).
  # Connector/J 8.0.26+ automatically uses utf8mb4 on the server side when UTF-8 is given.
  # URL must NOT contain connectionCollation (triggers MySQL error 1059 "Identifier too long").
  if [[ -n "${SPRING_DATASOURCE_URL:-}" ]]; then
    log "Using configured SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL}"
    return
  fi

  export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:${MYSQL_HOST_PORT}/${MYSQL_DATABASE}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
  log "Using default SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL}"
  log "Tip: Override by setting SPRING_DATASOURCE_URL in .env or your shell environment."
}

start_backend() {
  log "Starting Spring Boot backend at http://localhost:8080 ..."
  exec mvn spring-boot:run
}

main() {
  command -v docker >/dev/null 2>&1 || fail "Docker is not installed."
  find_compose
  wait_for_docker
  choose_adminer_port

  log "Starting Docker services..."
  "${COMPOSE[@]}" up -d

  wait_for_mysql
  wait_for_redis
  ensure_database
  ensure_backend_tools
  ensure_datasource_url
  start_backend
}

main "$@"
