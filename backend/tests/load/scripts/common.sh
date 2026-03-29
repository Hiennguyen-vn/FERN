#!/usr/bin/env bash

if [[ -n "${FERN_LOAD_COMMON_LOADED:-}" ]]; then
  return 0
fi
FERN_LOAD_COMMON_LOADED=1

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
LOAD_DIR="${ROOT_DIR}/tests/load"
OUTPUT_ROOT="${LOAD_OUTPUT_ROOT:-${ROOT_DIR}/.tmp/load}"
mkdir -p "${OUTPUT_ROOT}"

LOAD_ENV_FILE="${LOAD_ENV_FILE:-${LOAD_DIR}/config/environments/staging.json}"
LOAD_SEED_FILE="${LOAD_SEED_FILE:-${LOAD_DIR}/config/seeds/generated-staging.json}"
LOAD_PROMQL_FILE="${LOAD_PROMQL_FILE:-${LOAD_DIR}/config/metrics/promql.json}"
LOAD_BASE_URL="${LOAD_BASE_URL:-$(jq -r '.base_urls.gateway' "${LOAD_ENV_FILE}")}"
LOAD_IAM_BASE_URL="${LOAD_IAM_BASE_URL:-$(jq -r '.base_urls.iam' "${LOAD_ENV_FILE}")}"
LOAD_PROMETHEUS_URL="${LOAD_PROMETHEUS_URL:-$(jq -r '.base_urls.prometheus' "${LOAD_ENV_FILE}")}"
LOAD_TARGET_MODE="${LOAD_TARGET_MODE:-$(jq -r '.mode' "${LOAD_ENV_FILE}")}"
LOAD_K8S_NAMESPACE="${LOAD_K8S_NAMESPACE:-$(jq -r '.kubernetes.namespace // "default"' "${LOAD_ENV_FILE}")}"

LOAD_RUN_ID="${LOAD_RUN_ID:-load-$(date +%Y%m%d%H%M%S)}"
LOAD_BOOTSTRAP_USERNAME="${LOAD_BOOTSTRAP_USERNAME:-bootstrap-admin}"
LOAD_BOOTSTRAP_PASSWORD="${LOAD_BOOTSTRAP_PASSWORD:-Admin123!}"
LOAD_USER_PASSWORD="${LOAD_USER_PASSWORD:-Load123!}"

LOAD_PSQL_URI_MASTER="${LOAD_PSQL_URI_MASTER:-}"
LOAD_PSQL_URI_OPERATIONAL="${LOAD_PSQL_URI_OPERATIONAL:-}"

log() {
  printf '[load] %s\n' "$*"
}

fail() {
  printf '[load] %s\n' "$*" >&2
  exit 1
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "Missing required command: $1"
  fi
}

json_get() {
  jq -r "$1"
}

url_encode() {
  jq -nr --arg value "$1" '$value|@uri'
}

login_access_token() {
  local username="$1"
  local password="$2"
  local response
  response="$(curl -fsS \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${username}\",\"password\":\"${password}\"}" \
    "${LOAD_IAM_BASE_URL}/auth/login")" || fail "Unable to login as ${username}"
  printf '%s' "${response}" | jq -r '.accessToken'
}

bootstrap_token() {
  login_access_token "${LOAD_BOOTSTRAP_USERNAME}" "${LOAD_BOOTSTRAP_PASSWORD}"
}

api_json() {
  local token="$1"
  local method="$2"
  local url="$3"
  local body="${4:-}"
  shift 4 || true

  local -a args=(-fsS -X "${method}" -H "Authorization: Bearer ${token}" -H 'Accept: application/json')
  if [[ -n "${body}" ]]; then
    args+=(-H 'Content-Type: application/json' -d "${body}")
  fi
  while [[ $# -gt 0 ]]; do
    args+=(-H "$1")
    shift
  done
  curl "${args[@]}" "${url}"
}

parse_duration_seconds() {
  local raw="$1"
  case "${raw}" in
    *h) echo $(( ${raw%h} * 3600 )) ;;
    *m) echo $(( ${raw%m} * 60 )) ;;
    *s) echo "${raw%s}" ;;
    *) echo "${raw}" ;;
  esac
}

psql_scalar() {
  local uri="$1"
  local sql="$2"
  psql "${uri}" -Atqc "${sql}" 2>/dev/null || true
}

scenario_file_path() {
  local scenario_name="$1"
  printf '%s/config/scenarios/%s.json' "${LOAD_DIR}" "${scenario_name}"
}

scenario_behavior() {
  local scenario_name="$1"
  jq -r '.behavior' "$(scenario_file_path "${scenario_name}")"
}
