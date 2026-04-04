#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_ENV_FILE="${ROOT_DIR}/.env.migrations"

TARGET="all"
ACTION="migrate"
DRY_RUN="0"
ENV_FILE=""

usage() {
  cat <<'EOF'
Usage:
  ./scripts/migrate-platform.sh [all|master|operational] [options]

Options:
  --action <migrate|validate|info|repair>   Flyway action to execute (default: migrate)
  --env-file <path>                         Load migration environment variables from file
  --dry-run                                 Print the Flyway commands without executing them
  -h, --help                                Show this help

Environment defaults:
  FERN_DB_USERNAME=fern
  FERN_DB_PASSWORD=fern
  FERN_MASTER_JDBC_URL=jdbc:postgresql://127.0.0.1:55432/fern_master
  FERN_OPERATIONAL_JDBC_URL=jdbc:postgresql://127.0.0.1:55432/fern_operational

Examples:
  ./scripts/migrate-platform.sh all
  ./scripts/migrate-platform.sh master --action validate
EOF
}

log() {
  printf '[migrate] %s\n' "$*"
}

fail() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

load_env_file() {
  local env_file="$1"
  [[ -f "${env_file}" ]] || fail "Env file not found: ${env_file}"
  log "Loading environment from ${env_file}"
  while IFS= read -r raw_line || [[ -n "${raw_line}" ]]; do
    local line="${raw_line}"
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"

    [[ -z "${line}" ]] && continue
    [[ "${line}" == \#* ]] && continue
    [[ "${line}" == *=* ]] || continue

    local key="${line%%=*}"
    local value="${line#*=}"

    key="${key#"${key%%[![:space:]]*}"}"
    key="${key%"${key##*[![:space:]]}"}"
    if [[ "${key}" == export\ * ]]; then
      key="${key#export }"
      key="${key#"${key%%[![:space:]]*}"}"
      key="${key%"${key##*[![:space:]]}"}"
    fi

    export "${key}=${value}"
  done < "${env_file}"
}

require_file() {
  [[ -f "$1" ]] || fail "Required file not found: $1"
}

quote_cmd() {
  printf '%q ' "$@"
  printf '\n'
}

run_cmd() {
  if [[ "${DRY_RUN}" == "1" ]]; then
    quote_cmd "$@"
    return 0
  fi
  "$@"
}

run_flyway() {
  local module="$1"
  local label="$2"
  local url="$3"
  local user="$4"
  local password="$5"
  local locations="$6"
  local default_schema="$7"
  local schemas="$8"
  shift 8
  local connect_retries="${FERN_FLYWAY_CONNECT_RETRIES:-5}"

  local -a cmd=(
    "${ROOT_DIR}/mvnw"
    "-B"
    "-f" "${ROOT_DIR}/${module}/pom.xml"
    "-DskipTests"
    "-Dflyway.url=${url}"
    "-Dflyway.locations=filesystem:${ROOT_DIR}/${locations}"
    "-Dflyway.defaultSchema=${default_schema}"
    "-Dflyway.schemas=${schemas}"
    "-Dflyway.createSchemas=true"
    "-Dflyway.failOnMissingLocations=true"
    "-Dflyway.connectRetries=${connect_retries}"
  )

  while [[ $# -gt 0 ]]; do
    cmd+=("$1")
    shift
  done

  cmd+=("org.flywaydb:flyway-maven-plugin:${FLYWAY_PLUGIN_VERSION}:${ACTION}")

  log "${label}: ${ACTION}"
  if [[ "${DRY_RUN}" == "1" ]]; then
    if [[ -n "${user}" ]]; then
      quote_cmd env "FLYWAY_USER=${user}" "FLYWAY_PASSWORD=***" "${cmd[@]}"
      return 0
    fi
    quote_cmd "${cmd[@]}"
    return 0
  fi

  if [[ -n "${user}" ]]; then
    FLYWAY_USER="${user}" FLYWAY_PASSWORD="${password}" "${cmd[@]}"
    return 0
  fi

  run_cmd "${cmd[@]}"
}

prepare_shared_artifacts() {
  log "Preparing shared platform artifacts"
  run_cmd \
    "${ROOT_DIR}/mvnw" \
    "-B" \
    "-pl" "platform-common,platform-security,platform-observability,platform-test-support" \
    "-am" \
    "-DskipTests" \
    "install"
}

migrate_master() {
  prepare_shared_artifacts
  run_flyway "services/iam-service" "master/iam" "${FERN_MASTER_JDBC_URL}" "${FERN_DB_USERNAME}" "${FERN_DB_PASSWORD}" \
    "services/iam-service/src/main/resources/db/migration" "iam" "iam"
  run_flyway "services/org-service" "master/org" "${FERN_MASTER_JDBC_URL}" "${FERN_DB_USERNAME}" "${FERN_DB_PASSWORD}" \
    "services/org-service/src/main/resources/db/migration" "org" "org"
  run_flyway "services/catalog-service" "master/catalog" "${FERN_MASTER_JDBC_URL}" "${FERN_DB_USERNAME}" "${FERN_DB_PASSWORD}" \
    "services/catalog-service/src/main/resources/db/migration/postgresql/master" "catalog" "catalog"
  run_flyway "services/procurement-service" "master/procurement_master" "${FERN_MASTER_JDBC_URL}" "${FERN_DB_USERNAME}" "${FERN_DB_PASSWORD}" \
    "services/procurement-service/src/main/resources/db/migration/postgresql/master" "procurement_master" "procurement_master"
  run_flyway "services/hr-service" "master/hr_master" "${FERN_MASTER_JDBC_URL}" "${FERN_DB_USERNAME}" "${FERN_DB_PASSWORD}" \
    "services/hr-service/src/main/resources/db/migration/postgresql/master" "hr_master" "hr_master"
  run_flyway "services/finance-service" "master/config" "${FERN_MASTER_JDBC_URL}" "${FERN_DB_USERNAME}" "${FERN_DB_PASSWORD}" \
    "services/finance-service/src/main/resources/db/migration/postgresql/master" "config" "config"
  run_flyway "services/finance-service" "master/finance_projection" "${FERN_MASTER_JDBC_URL}" "${FERN_DB_USERNAME}" "${FERN_DB_PASSWORD}" \
    "services/finance-service/src/main/resources/db/migration/postgresql/master_projection" "finance_projection" "finance_projection"
  run_flyway "services/report-service" "master/reporting" "${FERN_MASTER_JDBC_URL}" "${FERN_DB_USERNAME}" "${FERN_DB_PASSWORD}" \
    "services/report-service/src/main/resources/db/migration/postgresql/reporting" "raw_events" "raw_events,report"
  run_flyway "services/audit-service" "master/audit" "${FERN_MASTER_JDBC_URL}" "${FERN_DB_USERNAME}" "${FERN_DB_PASSWORD}" \
    "services/audit-service/src/main/resources/db/migration/postgresql/reporting" "audit" "audit"
  run_flyway "services/notification-service" "master/notification" "${FERN_MASTER_JDBC_URL}" "${FERN_DB_USERNAME}" "${FERN_DB_PASSWORD}" \
    "services/notification-service/src/main/resources/db/migration/postgresql/master" "notification" "notification"
}

migrate_operational() {
  run_flyway "services/pos-service" "operational/pos" "${FERN_OPERATIONAL_JDBC_URL}" "${FERN_DB_USERNAME}" "${FERN_DB_PASSWORD}" \
    "services/pos-service/src/main/resources/db/migration/postgresql/operational" "pos" "pos"
  run_flyway "services/inventory-service" "operational/inventory" "${FERN_OPERATIONAL_JDBC_URL}" "${FERN_DB_USERNAME}" "${FERN_DB_PASSWORD}" \
    "services/inventory-service/src/main/resources/db/migration/postgresql/operational" "inventory" "inventory"
  run_flyway "services/procurement-service" "operational/procurement" "${FERN_OPERATIONAL_JDBC_URL}" "${FERN_DB_USERNAME}" "${FERN_DB_PASSWORD}" \
    "services/procurement-service/src/main/resources/db/migration/postgresql/operational" "procurement" "procurement"
  run_flyway "services/hr-service" "operational/hr" "${FERN_OPERATIONAL_JDBC_URL}" "${FERN_DB_USERNAME}" "${FERN_DB_PASSWORD}" \
    "services/hr-service/src/main/resources/db/migration/postgresql/operational" "hr" "hr"
  run_flyway "services/finance-service" "operational/finance" "${FERN_OPERATIONAL_JDBC_URL}" "${FERN_DB_USERNAME}" "${FERN_DB_PASSWORD}" \
    "services/finance-service/src/main/resources/db/migration/postgresql/operational" "finance" "finance"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    all|master|operational)
      TARGET="$1"
      shift
      ;;
    --action)
      [[ $# -ge 2 ]] || fail "--action requires a value"
      ACTION="$2"
      shift 2
      ;;
    --env-file)
      [[ $# -ge 2 ]] || fail "--env-file requires a value"
      ENV_FILE="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN="1"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown argument: $1"
      ;;
  esac
done

case "${ACTION}" in
  migrate|validate|info|repair)
    ;;
  *)
    fail "Unsupported action: ${ACTION}"
    ;;
esac

if [[ -n "${ENV_FILE}" ]]; then
  load_env_file "${ENV_FILE}"
elif [[ -f "${DEFAULT_ENV_FILE}" ]]; then
  load_env_file "${DEFAULT_ENV_FILE}"
fi

require_file "${ROOT_DIR}/mvnw"

FLYWAY_PLUGIN_VERSION="${FLYWAY_PLUGIN_VERSION:-11.7.2}"
FERN_DB_USERNAME="${FERN_DB_USERNAME:-fern}"
FERN_DB_PASSWORD="${FERN_DB_PASSWORD:-fern}"
FERN_MASTER_JDBC_URL="${FERN_MASTER_JDBC_URL:-jdbc:postgresql://127.0.0.1:55432/fern_master}"
FERN_OPERATIONAL_JDBC_URL="${FERN_OPERATIONAL_JDBC_URL:-jdbc:postgresql://127.0.0.1:55432/fern_operational}"

log "Target=${TARGET} action=${ACTION}"

case "${TARGET}" in
  all)
    migrate_master
    migrate_operational
    ;;
  master)
    migrate_master
    ;;
  operational)
    migrate_operational
    ;;
  *)
    fail "Unsupported target: ${TARGET}"
    ;;
esac

log "Completed ${ACTION} for ${TARGET}"
