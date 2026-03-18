#!/usr/bin/env bash
set -euo pipefail

QUERY="${1:-어린이 간식}"
SIZE="${SIZE:-5}"
PAGE="${PAGE:-1}"

MODELS=(
  "e5-small-ko-v2:8091"
  "kure-v1:8092"
  "bge-m3:8093"
)

urlencode() {
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1]))' "$1"
}

print_header() {
  echo
  echo "=================================================="
  echo "$1"
  echo "=================================================="
}

print_results() {
  local model_name="$1"
  local response="$2"

  python3 - "$model_name" "$response" <<'PY'
import json
import sys

model = sys.argv[1]
raw = sys.argv[2]

try:
    data = json.loads(raw)
except json.JSONDecodeError:
    print(f"[{model}] invalid json response")
    print(raw)
    sys.exit(0)

results = data.get("results", [])
print(f"[{model}] totalElements={data.get('totalElements')} count={data.get('count')}")
for idx, item in enumerate(results[:5], start=1):
    source = item.get("source", {})
    print(
        f"  {idx}. score={item.get('score')} "
        f"name={source.get('goods_name')} "
        f"category={source.get('lev3_category_id_name')} "
        f"price={source.get('sale_price')}"
    )
if not results:
    print("  (no results)")
PY
}

print_error() {
  local model_name="$1"
  local response_file="$2"
  local status_code="$3"

  echo "[${model_name}] request failed status=${status_code}"
  python3 - "$model_name" "$response_file" <<'PY'
import json
import pathlib
import sys

model = sys.argv[1]
path = pathlib.Path(sys.argv[2])
raw = path.read_text(encoding="utf-8")
try:
    data = json.loads(raw)
except json.JSONDecodeError:
    print(raw)
    sys.exit(0)

error = data.get("error")
if isinstance(error, dict):
    reason = error.get("reason")
    root = error.get("root_cause") or []
    root_reason = root[0].get("reason") if root and isinstance(root[0], dict) else None
    print(f"[{model}] reason={reason}")
    if root_reason and root_reason != reason:
        print(f"[{model}] root_cause={root_reason}")
else:
    print(raw)
PY
}

ENCODED_QUERY="$(urlencode "${QUERY}")"

print_header "query=${QUERY} page=${PAGE} size=${SIZE}"

for entry in "${MODELS[@]}"; do
  model="${entry%%:*}"
  port="${entry##*:}"
  url="http://localhost:${port}/api/search?q=${ENCODED_QUERY}&page=${PAGE}&size=${SIZE}"
  response_file="$(mktemp)"

  echo "[INFO] requesting ${model} (${url})"
  status_code="$(curl -sS -o "${response_file}" -w "%{http_code}" "${url}" || true)"
  if [ "${status_code}" != "200" ]; then
    print_error "${model}" "${response_file}" "${status_code}"
    rm -f "${response_file}"
    continue
  fi

  response="$(cat "${response_file}")"
  rm -f "${response_file}"
  print_results "${model}" "${response}"
done
