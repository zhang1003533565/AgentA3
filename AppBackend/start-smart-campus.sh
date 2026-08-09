#!/usr/bin/env bash
set -euo pipefail

PROJECT_NAME="smart-campus"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$SCRIPT_DIR"

log() {
  printf '[%s] %s\n' "$PROJECT_NAME" "$*"
}

fail() {
  printf '[%s] ERROR: %s\n' "$PROJECT_NAME" "$*" >&2
  exit 1
}

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

load_dotenv() {
  local path="$1"
  if [[ ! -f "$path" ]]; then
    log "No .env file found at '${path}'. Using existing process environment."
    return
  fi

  local loaded_keys=()
  local raw_line line name value joined key
  while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
    line="$(trim "$raw_line")"
    [[ -z "$line" || "${line:0:1}" == "#" ]] && continue

    if [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)[[:space:]]*=(.*)$ ]]; then
      name="${BASH_REMATCH[1]}"
      value="$(trim "${BASH_REMATCH[2]}")"
      if [[ ${#value} -ge 2 ]]; then
        if [[ "${value:0:1}" == "\"" && "${value: -1}" == "\"" ]]; then
          value="${value:1:${#value}-2}"
        elif [[ "${value:0:1}" == "'" && "${value: -1}" == "'" ]]; then
          value="${value:1:${#value}-2}"
        fi
      fi

      if [[ -z "${!name+x}" ]]; then
        export "${name}=${value}"
        loaded_keys+=("$name")
      fi
    fi
  done < "$path"

  if ((${#loaded_keys[@]} > 0)); then
    joined=""
    for key in "${loaded_keys[@]}"; do
      if [[ -n "$joined" ]]; then
        joined+=", "
      fi
      joined+="$key"
    done
    log "Loaded .env keys: ${joined}"
  else
    log ".env found, but no new process environment keys were loaded."
  fi
}

env_flag_enabled() {
  case "${1:-}" in
    1|true|TRUE|True|yes|YES|Yes|on|ON|On) return 0 ;;
    *) return 1 ;;
  esac
}

load_dotenv "${PROJECT_ROOT}/.env"
load_dotenv "${SCRIPT_DIR}/.env"

MYSQL_SERVICE="mysql"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"
MYSQL_DATABASE="${MYSQL_DATABASE:-smart-campus}"
MYSQL_CHARSET="${MYSQL_CHARSET:-utf8mb4}"
MYSQL_COLLATION="${MYSQL_COLLATION:-utf8mb4_unicode_ci}"
MYSQL_WAIT_SECONDS="${MYSQL_WAIT_SECONDS:-90}"
ADMINER_PORT="${ADMINER_PORT:-}"
BACKEND_PORT="${SERVER_PORT:-8080}"
# Host port mapped from container's 3306 (see docker-compose.yml). Defaults to 3306.
MYSQL_HOST_PORT="${MYSQL_HOST_PORT:-3306}"
NEO4J_ENABLED="${NEO4J_ENABLED:-false}"
NEO4J_WAIT_SECONDS="${NEO4J_WAIT_SECONDS:-${MYSQL_WAIT_SECONDS}}"
IMPORT_DATA_SQL="${IMPORT_DATA_SQL:-}"
DATA_SQL_PATH="${SCRIPT_DIR}/src/main/resources/data.sql"

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
    if "${COMPOSE[@]}" exec -T -e "MYSQL_PWD=${MYSQL_ROOT_PASSWORD}" "$MYSQL_SERVICE" \
      mysqladmin ping -uroot --silent >/dev/null 2>&1; then
      return
    fi
    sleep 1
  done

  fail "MySQL did not become ready within ${MYSQL_WAIT_SECONDS}s."
}

ensure_database() {
  log "Ensuring database '${MYSQL_DATABASE}' exists..."
  "${COMPOSE[@]}" exec -T -e "MYSQL_PWD=${MYSQL_ROOT_PASSWORD}" "$MYSQL_SERVICE" \
    mysql -uroot \
    -e "CREATE DATABASE IF NOT EXISTS \`${MYSQL_DATABASE}\` DEFAULT CHARACTER SET ${MYSQL_CHARSET} COLLATE ${MYSQL_COLLATION};" \
    || fail "Failed to create or verify database '${MYSQL_DATABASE}'."
}

import_data_sql_if_requested() {
  if ! env_flag_enabled "$IMPORT_DATA_SQL"; then
    log "Skipping data.sql import. Set IMPORT_DATA_SQL=1 only when you need to reset/seed local data."
    return
  fi

  [[ -f "$DATA_SQL_PATH" ]] || fail "data.sql was not found at '${DATA_SQL_PATH}'."

  log "IMPORT_DATA_SQL=1 detected. Importing data.sql into '${MYSQL_DATABASE}'."
  log "Warning: data.sql contains TRUNCATE statements and may reset local seed data."
  "${COMPOSE[@]}" exec -T -e "MYSQL_PWD=${MYSQL_ROOT_PASSWORD}" "$MYSQL_SERVICE" \
    mysql --default-character-set=utf8mb4 -uroot "$MYSQL_DATABASE" < "$DATA_SQL_PATH" \
    || fail "Failed to import data.sql."
  log "data.sql import completed."
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

wait_for_neo4j() {
  if ! env_flag_enabled "$NEO4J_ENABLED"; then
    log "Skipping Neo4j readiness check. Set NEO4J_ENABLED=true to enable knowledge graph storage."
    return
  fi

  log "Waiting for Neo4j container..."
  for _ in $(seq 1 "$NEO4J_WAIT_SECONDS"); do
    local status
    status="$(docker inspect -f "{{.State.Health.Status}}" smart-campus-neo4j 2>/dev/null || true)"
    if [[ "$status" == "healthy" ]]; then
      return
    fi
    sleep 1
  done

  fail "Neo4j did not become healthy within ${NEO4J_WAIT_SECONDS}s."
}

ensure_backend_tools() {
  if ! command -v java >/dev/null 2>&1; then
    fail "Java is not available. This backend requires JDK 21. On macOS: brew install openjdk@21"
  fi

  if ! command -v mvn >/dev/null 2>&1; then
    fail "Maven is not available. This backend requires Maven 3.9+. On macOS: brew install maven"
  fi
}

show_cos_config_status() {
  local required_keys=(
    TENCENT_COS_SECRET_ID
    TENCENT_COS_SECRET_KEY
    TENCENT_COS_REGION
    TENCENT_COS_BUCKET
    TENCENT_COS_DOMAIN
  )
  local missing_keys=()
  local key missing_text

  for key in "${required_keys[@]}"; do
    if [[ -z "${!key:-}" ]]; then
      missing_keys+=("$key")
    fi
  done

  if ((${#missing_keys[@]} > 0)); then
    local IFS=", "
    missing_text="${missing_keys[*]}"
    log "Warning: COS config is incomplete. Image upload may fail. Missing: ${missing_text}"
    log "Create '${SCRIPT_DIR}/.env' or set these environment variables before starting the backend."
    return
  fi

  log "COS config loaded. Bucket: ${TENCENT_COS_BUCKET}; Region: ${TENCENT_COS_REGION}; Domain: ${TENCENT_COS_DOMAIN}"
}

ensure_datasource_url() {
  # Host MySQL port is mapped from container's 3306 via docker-compose.yml.
  # characterEncoding must use the Java charset name "UTF-8" (NOT "utf8mb4" --
  # MySQL Connector/J rejects MySQL charset names here with UnsupportedEncodingException).
  # Connector/J 8.0.26+ automatically uses utf8mb4 on the server side when UTF-8 is given.
  # URL must NOT contain connectionCollation (triggers MySQL error 1059 "Identifier too long").
  if [[ -n "${SPRING_DATASOURCE_URL:-}" ]]; then
    log "Using configured SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL}"
  else
    export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:${MYSQL_HOST_PORT}/${MYSQL_DATABASE}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
    log "Using default SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL}"
    log "Tip: Override by setting SPRING_DATASOURCE_URL in .env or your shell environment."
  fi

  if [[ -z "${SPRING_DATASOURCE_USERNAME:-}" ]]; then
    export SPRING_DATASOURCE_USERNAME="root"
    log "Using default SPRING_DATASOURCE_USERNAME: root"
  fi

  if [[ -z "${SPRING_DATASOURCE_PASSWORD:-}" ]]; then
    export SPRING_DATASOURCE_PASSWORD="$MYSQL_ROOT_PASSWORD"
    log "Using MYSQL_ROOT_PASSWORD as SPRING_DATASOURCE_PASSWORD."
  fi
}

start_backend() {
  log "Backend API: http://localhost:${BACKEND_PORT}"
  log "Swagger UI: http://localhost:${BACKEND_PORT}/swagger-ui.html"
  log "Adminer: http://localhost:${ADMINER_PORT}"
  log "Starting Spring Boot backend..."
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
  wait_for_neo4j
  ensure_database
  import_data_sql_if_requested
  ensure_backend_tools
  show_cos_config_status
  ensure_datasource_url
  start_backend
}

main "$@"
