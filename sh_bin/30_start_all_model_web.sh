#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/ai-search-model-logs}"
PID_DIR="${PID_DIR:-/tmp/ai-search-model-pids}"

MODELS=(
  "e5-small-ko-v2"
  "e5-small-ko"
  "minilm-l12"
  "minilm-l6"
)

mkdir -p "${LOG_DIR}" "${PID_DIR}"

for model in "${MODELS[@]}"; do
  log_file="${LOG_DIR}/${model}.web.log"
  pid_file="${PID_DIR}/${model}.pid"

  echo "[INFO] starting web server for ${model}"
  "${SCRIPT_DIR}/21_run_model_web.sh" "${model}" > "${log_file}" 2>&1 &
  echo $! > "${pid_file}"
  echo "[INFO] ${model} pid=$(cat "${pid_file}") log=${log_file}"
  sleep 2
done

echo "[OK] all model web servers started"

