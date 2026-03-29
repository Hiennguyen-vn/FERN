#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "${SCRIPT_DIR}/common.sh"

require_cmd jq
require_cmd curl

SNAPSHOT_LABEL="${1:-snapshot}"
OUTPUT_DIR="${LOAD_OUTPUT_DIR:-${OUTPUT_ROOT}/${LOAD_RUN_ID}/adhoc}"
mkdir -p "${OUTPUT_DIR}"
OUTPUT_FILE="${OUTPUT_DIR}/${SNAPSHOT_LABEL}-observability.json"

PROM_RESULTS='{}'
if [[ -n "${LOAD_PROMETHEUS_URL}" && "${LOAD_PROMETHEUS_URL}" != "null" ]]; then
  while IFS=$'\t' read -r metric_name promql; do
    encoded_query="$(url_encode "${promql}")"
    response="$(curl -fsS "${LOAD_PROMETHEUS_URL}/api/v1/query?query=${encoded_query}" 2>/dev/null || true)"
    if [[ -n "${response}" ]]; then
      PROM_RESULTS="$(jq -c --arg key "${metric_name}" --argjson value "${response}" '. + {($key): $value}' <<< "${PROM_RESULTS}")"
    fi
  done < <(jq -r 'to_entries[] | [.key, .value] | @tsv' "${LOAD_PROMQL_FILE}")
fi

MASTER_ASSERTIONS='{}'
if [[ -n "${LOAD_PSQL_URI_MASTER}" ]]; then
  MASTER_ASSERTIONS="$(jq -n \
    --arg report_outbox_pending "$(psql_scalar "${LOAD_PSQL_URI_MASTER}" "SELECT COUNT(*) FROM report.outbox_event WHERE status <> '\''PUBLISHED'\'';")" \
    --arg projection_event_rows "$(psql_scalar "${LOAD_PSQL_URI_MASTER}" "SELECT COUNT(*) FROM report.region_daily_event;")" \
    '{
      report_outbox_pending: $report_outbox_pending,
      projection_event_rows: $projection_event_rows
    }')"
fi

OP_ASSERTIONS='{}'
if [[ -n "${LOAD_PSQL_URI_OPERATIONAL}" ]]; then
  OP_ASSERTIONS="$(jq -n \
    --arg pos_outbox_pending "$(psql_scalar "${LOAD_PSQL_URI_OPERATIONAL}" "SELECT COUNT(*) FROM pos.outbox_event WHERE status <> '\''PUBLISHED'\'';")" \
    --arg inventory_outbox_pending "$(psql_scalar "${LOAD_PSQL_URI_OPERATIONAL}" "SELECT COUNT(*) FROM inventory.outbox_event WHERE status <> '\''PUBLISHED'\'';")" \
    --arg procurement_outbox_pending "$(psql_scalar "${LOAD_PSQL_URI_OPERATIONAL}" "SELECT COUNT(*) FROM procurement.outbox_event WHERE status <> '\''PUBLISHED'\'';")" \
    --arg orphan_payments "$(psql_scalar "${LOAD_PSQL_URI_OPERATIONAL}" "SELECT COUNT(*) FROM pos.sale_payment p LEFT JOIN pos.sale_order o ON o.id = p.sale_order_id WHERE o.id IS NULL;")" \
    '{
      pos_outbox_pending: $pos_outbox_pending,
      inventory_outbox_pending: $inventory_outbox_pending,
      procurement_outbox_pending: $procurement_outbox_pending,
      orphan_payments: $orphan_payments
    }')"
fi

jq -n \
  --arg run_id "${LOAD_RUN_ID}" \
  --arg label "${SNAPSHOT_LABEL}" \
  --arg generated_at "$(date -u +%FT%TZ)" \
  --argjson prometheus "${PROM_RESULTS}" \
  --argjson master "${MASTER_ASSERTIONS}" \
  --argjson operational "${OP_ASSERTIONS}" \
  '{
    run_id: $run_id,
    label: $label,
    generated_at: $generated_at,
    prometheus: $prometheus,
    database: {
      master: $master,
      operational: $operational
    }
  }' > "${OUTPUT_FILE}"

log "Wrote observability snapshot to ${OUTPUT_FILE}"
