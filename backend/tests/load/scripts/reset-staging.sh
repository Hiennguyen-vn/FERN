#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "${SCRIPT_DIR}/common.sh"

log "Reset is non-destructive by default."
log "Because load fixtures are run-id scoped, the recommended reset strategy is to generate a new LOAD_RUN_ID."

if [[ -n "${LOAD_OUTPUT_ROOT:-}" && -d "${OUTPUT_ROOT}/${LOAD_RUN_ID}" ]]; then
  rm -rf "${OUTPUT_ROOT:?}/${LOAD_RUN_ID}"
  log "Removed output directory ${OUTPUT_ROOT}/${LOAD_RUN_ID}"
fi

if [[ "${LOAD_REMOVE_GENERATED_SEED_FILE:-0}" == "1" && -f "${LOAD_SEED_FILE}" ]]; then
  rm -f "${LOAD_SEED_FILE}"
  log "Removed generated seed file ${LOAD_SEED_FILE}"
fi
