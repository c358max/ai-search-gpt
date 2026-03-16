#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

QUERIES=(
  "어린이 간식"
  "건강한 간식"
  "생새우"
  "단백질 많은 간편식"
  "채식 위주의 건강식"
)

for query in "${QUERIES[@]}"; do
  "${SCRIPT_DIR}/33_compare_model_search.sh" "${query}"
done

