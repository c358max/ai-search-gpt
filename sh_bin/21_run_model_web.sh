#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODEL_KEY="${1:-e5-small-ko-v2}"
SEARCH_MODE="${2:-hybrid}"

# 비교/수동 검증 시 두 번째 인자로 검색 전략을 선택한다.
# - hybrid: 기본 검색 전략(KnnSearchStrategy)
# - search-vector-only: 벡터 전용 전략(VectorOnlySearchStrategy)
case "${SEARCH_MODE}" in
  hybrid)
    OPTIONAL_PROFILE=""
    ;;
  search-vector-only)
    OPTIONAL_PROFILE="search-vector-only"
    ;;
  *)
    echo "[ERROR] unsupported search mode: ${SEARCH_MODE}"
    echo "[USAGE] ./sh_bin/21_run_model_web.sh <e5-small-ko-v2|kure-v1|bge-m3> <hybrid|search-vector-only>"
    exit 1
    ;;
esac

OPTIONAL_PROFILE="${OPTIONAL_PROFILE}" "${SCRIPT_DIR}/20_run_model_profile.sh" web "${MODEL_KEY}"
