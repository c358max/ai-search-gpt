#!/usr/bin/env bash
set -euo pipefail

PID_DIR="${PID_DIR:-/tmp/ai-search-model-pids}"

MODELS=(
  "e5-small-ko-v2"
  "kure-v1"
  "bge-m3"
)

port_for_model() {
  case "$1" in
    e5-small-ko-v2) echo "8091" ;;
    kure-v1) echo "8092" ;;
    bge-m3) echo "8093" ;;
    *) return 1 ;;
  esac
}

stop_pid_if_running() {
  local pid="$1"
  local label="$2"

  if ! kill -0 "${pid}" >/dev/null 2>&1; then
    return 0
  fi

  echo "[INFO] stopping ${label} pid=${pid}"
  kill "${pid}" >/dev/null 2>&1 || true
  sleep 1

  if kill -0 "${pid}" >/dev/null 2>&1; then
    echo "[INFO] force stopping ${label} pid=${pid}"
    kill -9 "${pid}" >/dev/null 2>&1 || true
  fi
}

stop_port_listener() {
  local port="$1"
  local pids=""

  pids=$(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true)
  if [ -z "${pids}" ]; then
    echo "[INFO] no listener found on port ${port}"
    return 0
  fi

  for pid in ${pids}; do
    stop_pid_if_running "${pid}" "port ${port} listener"
  done
}

for model in "${MODELS[@]}"; do
  pid_file="${PID_DIR}/${model}.pid"
  port="$(port_for_model "${model}")"

  if [ ! -f "${pid_file}" ]; then
    echo "[INFO] no pid file for ${model}"
  else
    pid=$(cat "${pid_file}")
    if kill -0 "${pid}" >/dev/null 2>&1; then
      stop_pid_if_running "${pid}" "${model}"
    else
      echo "[INFO] stale pid file for ${model} pid=${pid}"
    fi
    rm -f "${pid_file}"
  fi

  stop_port_listener "${port}"
done

echo "[OK] stop request sent for all models"
