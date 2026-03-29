#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "${SCRIPT_DIR}/common.sh"

require_cmd jq

SCENARIO_NAME="${1:?scenario name is required}"
OUTPUT_DIR="${LOAD_OUTPUT_DIR:?LOAD_OUTPUT_DIR is required}"
SCENARIO_FILE="$(scenario_file_path "${SCENARIO_NAME}")"
SUMMARY_JSON="${OUTPUT_DIR}/k6-summary.json"
BEFORE_JSON="${OUTPUT_DIR}/before-observability.json"
AFTER_JSON="${OUTPUT_DIR}/after-observability.json"
REPORT_MD="${OUTPUT_DIR}/report.md"

jq -n \
  --arg scenario_name "${SCENARIO_NAME}" \
  --arg run_id "${LOAD_RUN_ID}" \
  --arg generated_at "$(date -u +%FT%TZ)" \
  --argjson scenario "$(cat "${SCENARIO_FILE}")" \
  --argjson k6 "$(cat "${SUMMARY_JSON}")" \
  --argjson before "$(cat "${BEFORE_JSON}" 2>/dev/null || echo '{}')" \
  --argjson after "$(cat "${AFTER_JSON}" 2>/dev/null || echo '{}')" \
  '
  {
    scenario_name: $scenario_name,
    run_id: $run_id,
    generated_at: $generated_at,
    scenario: $scenario,
    k6: $k6,
    before: $before,
    after: $after
  }' > "${OUTPUT_DIR}/combined-report.json"

{
  printf '# %s\n\n' "${SCENARIO_NAME}"
  printf '- Run ID: `%s`\n' "${LOAD_RUN_ID}"
  printf '- Generated At: `%s`\n' "$(date -u +%FT%TZ)"
  printf '- Behavior: `%s`\n' "$(jq -r '.behavior' "${SCENARIO_FILE}")"
  printf '\n## Workload Model\n'
  jq -r '.workload_model | to_entries[] | "- \(.key): `\(.value)`"' "${SCENARIO_FILE}"
  printf '\n## Pass/Fail Targets\n'
  jq -r '.pass_fail | to_entries[] | "- \(.key): `\(.value)`"' "${SCENARIO_FILE}"
  printf '\n## Bottleneck Hypotheses\n'
  jq -r '.bottleneck_hypothesis[] | "- \(.)"' "${SCENARIO_FILE}"
  printf '\n## Observability Assertions\n'
  jq -r '.observability_assertions[] | "- \(.)"' "${SCENARIO_FILE}"
  printf '\n## k6 Metrics Snapshot\n'
  jq -r '.metrics | to_entries[] | "- \(.key): `\(.value.type)`"' "${SUMMARY_JSON}"
} > "${REPORT_MD}"

log "Wrote markdown report to ${REPORT_MD}"
