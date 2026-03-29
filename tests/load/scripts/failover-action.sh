#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "${SCRIPT_DIR}/common.sh"

ACTION_NAME="${1:?action name is required}"
MODE="${LOAD_TARGET_MODE}"

deployment_name() {
  local key="$1"
  jq -r ".kubernetes.deployments.${key}" "${LOAD_ENV_FILE}"
}

run_custom_hook() {
  local env_name="$1"
  local command="${!env_name:-}"
  if [[ -z "${command}" ]]; then
    fail "Missing custom hook command in ${env_name}"
  fi
  eval "${command}"
}

if [[ "${MODE}" == "kubernetes" ]]; then
  require_cmd kubectl
fi

case "${ACTION_NAME}" in
  rolling_restart_inventory_service)
    if [[ "${MODE}" == "kubernetes" ]]; then
      kubectl -n "${LOAD_K8S_NAMESPACE}" rollout restart deployment/"$(deployment_name inventory_service)"
    elif [[ "${MODE}" == "compose" ]]; then
      (cd "${ROOT_DIR}" && docker compose restart inventory-service)
    fi
    ;;
  rolling_restart_pos_service)
    if [[ "${MODE}" == "kubernetes" ]]; then
      kubectl -n "${LOAD_K8S_NAMESPACE}" rollout restart deployment/"$(deployment_name pos_service)"
    elif [[ "${MODE}" == "compose" ]]; then
      (cd "${ROOT_DIR}" && docker compose restart pos-service)
    fi
    ;;
  pause_projection_consumer)
    if [[ "${MODE}" == "kubernetes" ]]; then
      kubectl -n "${LOAD_K8S_NAMESPACE}" scale deployment/"$(deployment_name report_service)" --replicas=0
    elif [[ "${MODE}" == "compose" ]]; then
      (cd "${ROOT_DIR}" && docker compose stop report-service)
    fi
    ;;
  resume_projection_consumer)
    if [[ "${MODE}" == "kubernetes" ]]; then
      kubectl -n "${LOAD_K8S_NAMESPACE}" scale deployment/"$(deployment_name report_service)" --replicas=1
    elif [[ "${MODE}" == "compose" ]]; then
      (cd "${ROOT_DIR}" && docker compose start report-service)
    fi
    ;;
  inject_org_latency)
    run_custom_hook LOAD_CHAOS_HOOK_INJECT_ORG_LATENCY
    ;;
  inject_inventory_latency)
    run_custom_hook LOAD_CHAOS_HOOK_INJECT_INVENTORY_LATENCY
    ;;
  *)
    fail "Unsupported failover action: ${ACTION_NAME}"
    ;;
esac

log "Executed failover action ${ACTION_NAME}"
