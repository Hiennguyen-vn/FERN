#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_ENV_FILE="${ROOT_DIR}/.env.migrations"

TARGET="all"
ACTION="migrate"
DRY_RUN="0"
SKIP_SNOWFLAKE_BOOTSTRAP="0"
ENV_FILE=""

usage() {
  cat <<'EOF'
Usage:
  ./scripts/migrate-platform.sh [all|master|operational|snowflake] [options]

Options:
  --action <migrate|validate|info|repair>   Flyway action to execute (default: migrate)
  --env-file <path>                         Load migration environment variables from file
  --dry-run                                 Print the Flyway commands without executing them
  --skip-snowflake-bootstrap                Skip Snowflake database/warehouse bootstrap step
  -h, --help                                Show this help

Environment defaults:
  FERN_DB_USERNAME=fern
  FERN_DB_PASSWORD=fern
  FERN_MASTER_JDBC_URL=jdbc:postgresql://127.0.0.1:55432/fern_master
  FERN_OPERATIONAL_JDBC_URL=jdbc:postgresql://127.0.0.1:55432/fern_operational
  FERN_SNOWFLAKE_DATABASE=FERN_REPORTING
  FERN_SNOWFLAKE_BOOTSTRAP_DATABASE=SNOWFLAKE
  FERN_SNOWFLAKE_WAREHOUSE=FERN_INGEST_WH
  FERN_SNOWFLAKE_BI_WAREHOUSE=FERN_BI_WH
  FERN_SNOWFLAKE_ROLE=SYSADMIN
  FERN_SNOWFLAKE_JDBC_OPTIONS=&JDBC_QUERY_RESULT_FORMAT=JSON

Examples:
  ./scripts/migrate-platform.sh all
  ./scripts/migrate-platform.sh master --action validate
  ./scripts/migrate-platform.sh snowflake --env-file .env.migrations
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

build_snowflake_url() {
  local database="$1"
  local schema="$2"
  local url
  url="jdbc:snowflake://${FERN_SNOWFLAKE_ACCOUNT}.snowflakecomputing.com/?db=${database}&warehouse=${FERN_SNOWFLAKE_WAREHOUSE}&role=${FERN_SNOWFLAKE_ROLE}"
  if [[ -n "${schema}" ]]; then
    url="${url}&schema=${schema}"
  fi
  if [[ -n "${FERN_SNOWFLAKE_AUTHENTICATOR:-}" ]]; then
    url="${url}&authenticator=${FERN_SNOWFLAKE_AUTHENTICATOR}"
  fi
  url="${url}${FERN_SNOWFLAKE_JDBC_OPTIONS}"
  printf '%s' "${url}"
}

require_snowflake_env() {
  [[ -n "${FERN_SNOWFLAKE_ACCOUNT:-}" ]] || fail "FERN_SNOWFLAKE_ACCOUNT is required for Snowflake migrations"
  [[ -n "${FERN_SNOWFLAKE_USER:-}" ]] || fail "FERN_SNOWFLAKE_USER is required for Snowflake migrations"
}

run_snowflake_bootstrap() {
  local sql_file="${ROOT_DIR}/infrastructure/snowflake/reporting/bootstrap/V1__bootstrap_reporting_database.sql"
  require_file "${sql_file}"

  local -a cmd=(
    "${ROOT_DIR}/mvnw"
    "-B"
    "-f" "${ROOT_DIR}/services/report-service/pom.xml"
    "-DskipTests"
    "-Dexec.mainClass=com.fern.reportservice.tools.SnowflakeBootstrapCommand"
    "-Dexec.args=${sql_file}"
    "compile"
    "org.codehaus.mojo:exec-maven-plugin:3.5.0:java"
  )

  log "snowflake/bootstrap: bootstrap"
  if [[ "${DRY_RUN}" == "1" ]]; then
    quote_cmd env \
      "FERN_SNOWFLAKE_ACCOUNT=${FERN_SNOWFLAKE_ACCOUNT}" \
      "FERN_SNOWFLAKE_USER=${FERN_SNOWFLAKE_USER}" \
      "FERN_SNOWFLAKE_PASSWORD=***" \
      "FERN_SNOWFLAKE_ROLE=${FERN_SNOWFLAKE_ROLE}" \
      "FERN_SNOWFLAKE_WAREHOUSE=${FERN_SNOWFLAKE_WAREHOUSE}" \
      "FERN_SNOWFLAKE_JDBC_OPTIONS=${FERN_SNOWFLAKE_JDBC_OPTIONS}" \
      "FERN_SNOWFLAKE_AUTHENTICATOR=${FERN_SNOWFLAKE_AUTHENTICATOR:-}" \
      "FERN_SNOWFLAKE_DATABASE=${FERN_SNOWFLAKE_DATABASE}" \
      "FERN_SNOWFLAKE_BI_WAREHOUSE=${FERN_SNOWFLAKE_BI_WAREHOUSE}" \
      "${cmd[@]}"
    return 0
  fi

  "${cmd[@]}"
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

  if [[ "${url}" == jdbc:snowflake:* ]]; then
    connect_retries="${FERN_SNOWFLAKE_CONNECT_RETRIES:-0}"
  fi

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

migrate_snowflake() {
  require_snowflake_env

  if [[ "${SKIP_SNOWFLAKE_BOOTSTRAP}" != "1" ]]; then
    run_snowflake_bootstrap
  fi

  run_flyway "services/report-service" "snowflake/report" \
    "$(build_snowflake_url "${FERN_SNOWFLAKE_DATABASE}" "RAW_EVENTS")" \
    "${FERN_SNOWFLAKE_USER}" "${FERN_SNOWFLAKE_PASSWORD}" \
    "services/report-service/src/main/resources/db/migration/snowflake" "RAW_EVENTS" "RAW_EVENTS,REPORT"
  run_flyway "services/finance-service" "snowflake/finance_projection" \
    "$(build_snowflake_url "${FERN_SNOWFLAKE_DATABASE}" "FINANCE_PROJECTION")" \
    "${FERN_SNOWFLAKE_USER}" "${FERN_SNOWFLAKE_PASSWORD}" \
    "services/finance-service/src/main/resources/db/migration/snowflake" "FINANCE_PROJECTION" "FINANCE_PROJECTION"
  run_flyway "services/audit-service" "snowflake/audit" \
    "$(build_snowflake_url "${FERN_SNOWFLAKE_DATABASE}" "AUDIT")" \
    "${FERN_SNOWFLAKE_USER}" "${FERN_SNOWFLAKE_PASSWORD}" \
    "services/audit-service/src/main/resources/db/migration/snowflake" "AUDIT" "AUDIT"
  run_flyway "services/notification-service" "snowflake/notification" \
    "$(build_snowflake_url "${FERN_SNOWFLAKE_DATABASE}" "NOTIFICATION")" \
    "${FERN_SNOWFLAKE_USER}" "${FERN_SNOWFLAKE_PASSWORD}" \
    "services/notification-service/src/main/resources/db/migration/snowflake" "NOTIFICATION" "NOTIFICATION"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    all|master|operational|snowflake)
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
    --skip-snowflake-bootstrap)
      SKIP_SNOWFLAKE_BOOTSTRAP="1"
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
FERN_SNOWFLAKE_DATABASE="${FERN_SNOWFLAKE_DATABASE:-FERN_REPORTING}"
FERN_SNOWFLAKE_BOOTSTRAP_DATABASE="${FERN_SNOWFLAKE_BOOTSTRAP_DATABASE:-SNOWFLAKE}"
FERN_SNOWFLAKE_WAREHOUSE="${FERN_SNOWFLAKE_WAREHOUSE:-FERN_INGEST_WH}"
FERN_SNOWFLAKE_BI_WAREHOUSE="${FERN_SNOWFLAKE_BI_WAREHOUSE:-FERN_BI_WH}"
FERN_SNOWFLAKE_ROLE="${FERN_SNOWFLAKE_ROLE:-SYSADMIN}"
FERN_SNOWFLAKE_PASSWORD="${FERN_SNOWFLAKE_PASSWORD:-}"
FERN_SNOWFLAKE_JDBC_OPTIONS="${FERN_SNOWFLAKE_JDBC_OPTIONS:-&JDBC_QUERY_RESULT_FORMAT=JSON}"

log "Target=${TARGET} action=${ACTION}"

case "${TARGET}" in
  all)
    migrate_master
    migrate_operational
    migrate_snowflake
    ;;
  master)
    migrate_master
    ;;
  operational)
    migrate_operational
    ;;
  snowflake)
    migrate_snowflake
    ;;
  *)
    fail "Unsupported target: ${TARGET}"
    ;;
esac

log "Completed ${ACTION} for ${TARGET}"
