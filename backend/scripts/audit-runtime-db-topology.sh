#!/usr/bin/env bash

set -euo pipefail

STRICT=false
if [[ "${1:-}" == "--strict" ]]; then
  STRICT=true
fi

PGHOST="${FERN_PGHOST:-127.0.0.1}"
PGPORT="${FERN_PGPORT:-55432}"
PGUSER="${FERN_DB_USERNAME:-fern}"
export PGPASSWORD="${FERN_DB_PASSWORD:-fern}"

EXPECTED_DATABASES=(
  "fern_master"
  "fern_operational"
)

psql_query() {
  local database="$1"
  local sql="$2"
  psql -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d "${database}" -Atqc "${sql}"
}

sort_unique() {
  awk 'NF' | sort -u
}

set_diff() {
  local left="$1"
  local right="$2"
  comm -23 <(printf '%s\n' "${left}" | sort_unique) <(printf '%s\n' "${right}" | sort_unique)
}

expected_schemas_for_db() {
  case "$1" in
    fern_master)
      cat <<'EOF'
audit
catalog
config
finance_projection
gateway
hr_master
iam
notification
org
procurement_master
raw_events
report
EOF
      ;;
    fern_operational)
      cat <<'EOF'
finance
hr
inventory
pos
procurement
EOF
      ;;
    *)
      return 1
      ;;
  esac
}

list_databases() {
  psql_query "postgres" "SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname;"
}

list_user_schemas() {
  local database="$1"
  psql_query "${database}" "
    SELECT schema_name
    FROM information_schema.schemata
    WHERE schema_name NOT IN ('information_schema', 'public')
      AND schema_name NOT LIKE 'pg_%'
    ORDER BY schema_name;
  "
}

database_exists() {
  local database="$1"
  local actual_databases="$2"
  printf '%s\n' "${actual_databases}" | grep -qx "${database}"
}

print_block() {
  local title="$1"
  local content="$2"
  echo "${title}"
  if [[ -n "$(printf '%s\n' "${content}" | awk 'NF')" ]]; then
    printf '%s\n' "${content}" | awk 'NF { print "  - " $0 }'
  else
    echo "  - none"
  fi
}

main() {
  local actual_databases
  local expected_databases
  local app_databases
  local extra_databases
  local missing_databases
  local drift_found=false

  actual_databases="$(list_databases)"
  expected_databases="$(printf '%s\n' "${EXPECTED_DATABASES[@]}")"
  app_databases="$(printf '%s\n' "${actual_databases}" | grep '^fern_' || true)"
  extra_databases="$(set_diff "${app_databases}" "${expected_databases}")"
  missing_databases="$(set_diff "${expected_databases}" "${app_databases}")"

  echo "FERN runtime database topology audit"
  echo "Connection: ${PGUSER}@${PGHOST}:${PGPORT}"
  echo

  print_block "Expected application databases" "${expected_databases}"
  print_block "Actual application databases" "${app_databases}"
  print_block "Extra databases not referenced by current runtime topology" "${extra_databases}"
  print_block "Missing databases required by current runtime topology" "${missing_databases}"
  echo

  if [[ -n "$(printf '%s\n' "${extra_databases}" | awk 'NF')" || -n "$(printf '%s\n' "${missing_databases}" | awk 'NF')" ]]; then
    drift_found=true
  fi

  for database in "${EXPECTED_DATABASES[@]}"; do
    echo "[${database}]"
    if ! database_exists "${database}" "${actual_databases}"; then
      echo "  - database missing"
      echo
      continue
    fi

    local expected_schemas
    local actual_schemas
    local extra_schemas
    local missing_schemas

    expected_schemas="$(expected_schemas_for_db "${database}")"
    actual_schemas="$(list_user_schemas "${database}")"
    extra_schemas="$(set_diff "${actual_schemas}" "${expected_schemas}")"
    missing_schemas="$(set_diff "${expected_schemas}" "${actual_schemas}")"

    print_block "  expected schemas" "${expected_schemas}"
    print_block "  actual schemas" "${actual_schemas}"
    print_block "  extra schemas" "${extra_schemas}"
    print_block "  missing schemas" "${missing_schemas}"
    echo

    if [[ -n "$(printf '%s\n' "${extra_schemas}" | awk 'NF')" || -n "$(printf '%s\n' "${missing_schemas}" | awk 'NF')" ]]; then
      drift_found=true
    fi
  done

  if [[ "${drift_found}" == "true" ]]; then
    echo "Audit result: DRIFT DETECTED"
    if [[ "${STRICT}" == "true" ]]; then
      exit 1
    fi
  else
    echo "Audit result: CLEAN"
  fi
}

main
