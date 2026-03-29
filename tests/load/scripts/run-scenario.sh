#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "${SCRIPT_DIR}/common.sh"

require_cmd jq
require_cmd k6

SCENARIO_NAME="${1:?scenario name is required}"
SCENARIO_FILE="$(scenario_file_path "${SCENARIO_NAME}")"
[[ -f "${SCENARIO_FILE}" ]] || fail "Scenario file not found: ${SCENARIO_FILE}"

BEHAVIOR="$(scenario_behavior "${SCENARIO_NAME}")"
case "${BEHAVIOR}" in
  inventory_event_storm) K6_SCRIPT="${LOAD_DIR}/k6/scenarios/inventory_event_storm.js" ;;
  *) K6_SCRIPT="${LOAD_DIR}/k6/scenarios/stateful_pos.js" ;;
esac

LOAD_OUTPUT_DIR="${OUTPUT_ROOT}/${LOAD_RUN_ID}/${SCENARIO_NAME}"
mkdir -p "${LOAD_OUTPUT_DIR}"
export LOAD_OUTPUT_DIR LOAD_RUN_ID LOAD_ENV_FILE LOAD_SEED_FILE LOAD_SCENARIO_FILE="${SCENARIO_FILE}"
export LOAD_BASE_URL LOAD_IAM_BASE_URL

"${SCRIPT_DIR}/snapshot-metrics.sh" before >/dev/null

CHAOS_PID=""
CHAOS_ACTION="$(jq -r '.chaos_action.name // empty' "${SCENARIO_FILE}")"
if [[ -n "${CHAOS_ACTION}" && "${LOAD_ENABLE_CHAOS:-1}" == "1" ]]; then
  CHAOS_DELAY_RAW="$(jq -r '.chaos_action.delay_from_start // "0s"' "${SCENARIO_FILE}")"
  CHAOS_RESUME_RAW="$(jq -r '.chaos_action.resume_after // "0s"' "${SCENARIO_FILE}")"
  CHAOS_DELAY_SECONDS="$(parse_duration_seconds "${CHAOS_DELAY_RAW}")"
  CHAOS_RESUME_SECONDS="$(parse_duration_seconds "${CHAOS_RESUME_RAW}")"
  (
    sleep "${CHAOS_DELAY_SECONDS}"
    "${SCRIPT_DIR}/failover-action.sh" "${CHAOS_ACTION}"
    if [[ "${CHAOS_RESUME_SECONDS}" -gt 0 ]]; then
      sleep "${CHAOS_RESUME_SECONDS}"
      case "${CHAOS_ACTION}" in
        pause_projection_consumer)
          "${SCRIPT_DIR}/failover-action.sh" resume_projection_consumer
          ;;
      esac
    fi
  ) &
  CHAOS_PID=$!
fi

log "Running ${SCENARIO_NAME} with ${K6_SCRIPT}"
k6 run "${K6_SCRIPT}"

if [[ -n "${CHAOS_PID}" ]]; then
  wait "${CHAOS_PID}" || true
fi

"${SCRIPT_DIR}/snapshot-metrics.sh" after >/dev/null
"${SCRIPT_DIR}/report-summary.sh" "${SCENARIO_NAME}" >/dev/null

log "Scenario ${SCENARIO_NAME} completed. Artifacts: ${LOAD_OUTPUT_DIR}"
