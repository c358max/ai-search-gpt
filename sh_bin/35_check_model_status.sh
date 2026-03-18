#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-ai-search}"

MODELS=(
  "e5-small-ko-v2:8091:food-products-e5-small-ko-v2-read"
  "kure-v1:8092:food-products-kure-v1-read"
  "bge-m3:8093:food-products-bge-m3-read"
)

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ERROR] required command not found: $1"
    exit 1
  fi
}

require_command kubectl
require_command curl

PASSWORD=$(kubectl get secret ai-search-es-es-elastic-user -n "${NAMESPACE}" -o go-template='{{.data.elastic | base64decode}}')

for entry in "${MODELS[@]}"; do
  model="${entry%%:*}"
  rest="${entry#*:}"
  port="${rest%%:*}"
  alias_name="${rest#*:}"

  echo
  echo "=================================================="
  echo "${model}"
  echo "=================================================="

  if lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "[PORT] ${port} LISTEN"
  else
    echo "[PORT] ${port} NOT LISTENING"
  fi

  if curl -fsS -u "elastic:${PASSWORD}" "http://localhost:9200/_alias/${alias_name}" >/dev/null 2>&1; then
    echo "[ALIAS] ${alias_name} READY"
  else
    echo "[ALIAS] ${alias_name} NOT FOUND"
  fi
done
