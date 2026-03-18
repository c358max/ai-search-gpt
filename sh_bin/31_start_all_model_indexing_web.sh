#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/ai-search-model-logs}"
PID_DIR="${PID_DIR:-/tmp/ai-search-model-pids}"
NAMESPACE="${NAMESPACE:-ai-search}"
ALIAS_WAIT_SECONDS="${ALIAS_WAIT_SECONDS:-240}"

MODELS=(
  "e5-small-ko-v2"
  "kure-v1"
  "bge-m3"
)

mkdir -p "${LOG_DIR}" "${PID_DIR}"

alias_for_model() {
  case "$1" in
    e5-small-ko-v2) echo "food-products-e5-small-ko-v2-read" ;;
    kure-v1) echo "food-products-kure-v1-read" ;;
    bge-m3) echo "food-products-bge-m3-read" ;;
    *) return 1 ;;
  esac
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ERROR] required command not found: $1"
    exit 1
  fi
}

require_command kubectl
require_command curl

PASSWORD=$(kubectl get secret ai-search-es-es-elastic-user -n "${NAMESPACE}" -o go-template='{{.data.elastic | base64decode}}')

wait_for_alias() {
  local model="$1"
  local alias_name="$2"
  local log_file="$3"
  local waited=0

  while [ "${waited}" -lt "${ALIAS_WAIT_SECONDS}" ]; do
    if ! kill -0 "$(cat "${PID_DIR}/${model}.pid")" >/dev/null 2>&1; then
      echo "[ERROR] ${model} process exited before alias creation"
      echo "[ERROR] check log: ${log_file}"
      tail -n 40 "${log_file}" || true
      exit 1
    fi

    if curl -fsS -u "elastic:${PASSWORD}" "http://localhost:9200/_alias/${alias_name}" >/dev/null 2>&1; then
      echo "[OK] ${model} alias ready: ${alias_name}"
      return 0
    fi

    sleep 5
    waited=$((waited + 5))
  done

  echo "[ERROR] timed out waiting for alias ${alias_name} for model ${model}"
  echo "[ERROR] check log: ${log_file}"
  tail -n 40 "${log_file}" || true
  exit 1
}

wait_for_rollout_complete() {
  local model="$1"
  local log_file="$2"
  local waited=0

  while [ "${waited}" -lt "${ALIAS_WAIT_SECONDS}" ]; do
    if ! kill -0 "$(cat "${PID_DIR}/${model}.pid")" >/dev/null 2>&1; then
      echo "[ERROR] ${model} process exited before indexing completed"
      echo "[ERROR] check log: ${log_file}"
      tail -n 40 "${log_file}" || true
      exit 1
    fi

    if grep -q "Index rollout complete" "${log_file}" && \
       grep -q "Indexing finished in indexing-web mode" "${log_file}"; then
      echo "[OK] ${model} indexing rollout completed"
      return 0
    fi

    sleep 5
    waited=$((waited + 5))
  done

  echo "[ERROR] timed out waiting for indexing completion for model ${model}"
  echo "[ERROR] check log: ${log_file}"
  tail -n 40 "${log_file}" || true
  exit 1
}

for model in "${MODELS[@]}"; do
  log_file="${LOG_DIR}/${model}.indexing-web.log"
  pid_file="${PID_DIR}/${model}.pid"
  alias_name="$(alias_for_model "${model}")"

  echo "[INFO] starting vector-only indexing-web for ${model}"
  OPTIONAL_PROFILE="search-vector-only" "${SCRIPT_DIR}/22_run_model_indexing_web.sh" "${model}" > "${log_file}" 2>&1 &
  echo $! > "${pid_file}"
  echo "[INFO] ${model} pid=$(cat "${pid_file}") log=${log_file}"
  echo "[INFO] waiting for alias ${alias_name}"
  wait_for_alias "${model}" "${alias_name}" "${log_file}"
  echo "[INFO] waiting for indexing rollout completion for ${model}"
  wait_for_rollout_complete "${model}" "${log_file}"
done

echo "[OK] all model indexing-web processes started (search-vector-only)"
echo "[NOTE] indexing runs against each model profile and waits until alias swap is completed"
