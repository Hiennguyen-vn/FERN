#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env.migrations"
ENV_TEMPLATE="${ROOT_DIR}/infrastructure/migration.env.example"
KEEP_INFRA_UP="${KEEP_INFRA_UP:-1}"
SKIP_MIGRATE="${SKIP_MIGRATE:-0}"
SKIP_SMOKE="${SKIP_SMOKE:-0}"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/bootstrap-local.sh

Environment:
  KEEP_INFRA_UP=1   Keep Docker infrastructure running after the smoke flow (default)
  KEEP_INFRA_UP=0   Tear down Docker infrastructure after the smoke flow
  SKIP_MIGRATE=1    Skip PostgreSQL migrations
  SKIP_SMOKE=1      Skip the smoke flow after bootstrap

The script will:
1. Ensure .env.migrations exists
2. Start local Docker infrastructure
3. Apply PostgreSQL master and operational migrations
4. Run the end-to-end smoke flow through gateway, IAM, and Org
EOF
}

log() {
  printf '[bootstrap] %s\n' "$*"
}

fail() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "Missing required command: $1"
  fi
}

wait_for_container_health() {
  local container_name="$1"
  local retries="${2:-60}"

  for _ in $(seq 1 "${retries}"); do
    local health_value
    health_value="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${container_name}" 2>/dev/null || true)"
    if [[ "${health_value}" == "healthy" || "${health_value}" == "running" ]]; then
      return 0
    fi
    sleep 2
  done

  fail "Timed out waiting for container ${container_name} to become healthy"
}

for arg in "$@"; do
  case "${arg}" in
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown argument: ${arg}"
      ;;
  esac
done

require_cmd docker
require_cmd curl
require_cmd python3

docker info >/dev/null 2>&1 || fail "Docker daemon is not running"

if [[ ! -f "${ENV_FILE}" ]]; then
  log "Creating ${ENV_FILE} from template"
  cp "${ENV_TEMPLATE}" "${ENV_FILE}"
fi

log "Starting Docker infrastructure"
(cd "${ROOT_DIR}" && docker compose up -d postgres redis kafka >/dev/null)
wait_for_container_health fern-postgres
wait_for_container_health fern-redis
wait_for_container_health fern-kafka 90

if [[ "${SKIP_MIGRATE}" != "1" ]]; then
  log "Applying PostgreSQL migrations"
  (cd "${ROOT_DIR}" && ./scripts/migrate-platform.sh master)
  (cd "${ROOT_DIR}" && ./scripts/migrate-platform.sh operational)
else
  log "Skipping PostgreSQL migrations"
fi

if [[ "${SKIP_SMOKE}" != "1" ]]; then
  log "Running smoke flow"
  (
    cd "${ROOT_DIR}" &&
    KEEP_INFRA_UP="${KEEP_INFRA_UP}" \
    SKIP_INFRA_BOOTSTRAP=1 \
    ./scripts/smoke-local.sh
  )
else
  log "Skipping smoke flow"
  if [[ "${KEEP_INFRA_UP}" != "1" ]]; then
    log "KEEP_INFRA_UP=0 requested, tearing down Docker infrastructure"
    (cd "${ROOT_DIR}" && docker compose down >/dev/null)
  fi
fi

log "Local bootstrap completed"
