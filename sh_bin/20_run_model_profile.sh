#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
TRUSTSTORE_PATH="${TRUSTSTORE_PATH:-${HOME}/.ai-cert/djl-truststore.p12}"
TRUSTSTORE_PASSWORD="${AI_SEARCH_TRUSTSTORE_PASSWORD:-changeit}"

# 로컬 실행 기본값:
# - 별도 지정이 없으면 로컬 Docker Elasticsearch(9210)를 사용한다.
# - k8s port-forward 자동 연결은 로컬 수동 검증에선 혼선을 만들 수 있어 기본 비활성화한다.
AI_SEARCH_ES_URL="${AI_SEARCH_ES_URL:-http://127.0.0.1:9210}"
AI_SEARCH_ES_USERNAME="${AI_SEARCH_ES_USERNAME:-elastic}"
AI_SEARCH_ES_PASSWORD="${AI_SEARCH_ES_PASSWORD:-elastic}"
AI_SEARCH_AUTO_PORT_FORWARD="${AI_SEARCH_AUTO_PORT_FORWARD:-false}"

java_major_version() {
  "$1" -version 2>&1 | sed -n 's/.*version "\(1\.\)\{0,1\}\([0-9][0-9]*\).*/\2/p' | head -n 1
}

resolve_java_home_from_bin() {
  local java_bin_path="${1:-}"
  local java_dir=""
  if [ -z "${java_bin_path}" ]; then
    return 1
  fi

  java_dir=$(cd -P "${java_bin_path%/*}/.." > /dev/null 2>&1 && pwd)
  if [ -n "${java_dir}" ] && [ -x "${java_dir}/bin/java" ]; then
    printf '%s\n' "${java_dir}"
    return 0
  fi
  return 1
}

ensure_java_21() {
  local java_cmd=""
  local java_major=""
  local resolved_java_home=""

  java_cmd=$(command -v java 2>/dev/null || true)
  if [ -n "${java_cmd}" ]; then
    java_major=$(java_major_version "${java_cmd}")
    if [ -n "${java_major}" ] && [ "${java_major}" -ge 21 ]; then
      resolved_java_home=$(resolve_java_home_from_bin "${java_cmd}" || true)
      if [ -n "${resolved_java_home}" ]; then
        export JAVA_HOME="${resolved_java_home}"
        echo "[INFO] JAVA_HOME set from PATH Java (${JAVA_HOME})"
        return 0
      fi
    fi
  fi

  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    resolved_java_home=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
    if [ -n "${resolved_java_home}" ]; then
      export JAVA_HOME="${resolved_java_home}"
      echo "[INFO] JAVA_HOME set from /usr/libexec/java_home (${JAVA_HOME})"
      return 0
    fi
  fi

  echo "[WARN] Java 21 not found automatically; using existing JAVA_HOME=${JAVA_HOME:-<unset>}"
}

MODE="${1:-web}"
MODEL_KEY="${2:-e5-small-ko-v2}"
OPTIONAL_PROFILE="${OPTIONAL_PROFILE:-}"

case "${MODE}" in
  web)
    EXTRA_PROFILE=""
    ;;
  indexing)
    EXTRA_PROFILE=",indexing"
    ;;
  indexing-web)
    EXTRA_PROFILE=",indexing-web"
    ;;
  *)
    echo "[ERROR] unsupported mode: ${MODE}"
    echo "[USAGE] ./sh_bin/20_run_model_profile.sh <web|indexing|indexing-web> <e5-small-ko-v2|kure-v1|bge-m3>"
    exit 1
    ;;
esac

case "${MODEL_KEY}" in
  e5-small-ko-v2)
    PROFILE="model-e5-small-ko-v2"
    DEFAULT_PORT=8091
    ;;
  kure-v1)
    PROFILE="model-kure-v1"
    DEFAULT_PORT=8092
    ;;
  bge-m3)
    PROFILE="model-bge-m3"
    DEFAULT_PORT=8093
    ;;
  *)
    echo "[ERROR] unsupported model key: ${MODEL_KEY}"
    echo "[USAGE] ./sh_bin/20_run_model_profile.sh <web|indexing|indexing-web> <e5-small-ko-v2|kure-v1|bge-m3>"
    exit 1
    ;;
esac

if [ ! -x "${ROOT_DIR}/gradlew" ]; then
  echo "[ERROR] ./gradlew not found or not executable"
  exit 1
fi

ensure_java_21

if [ "${MODE}" != "web" ] && [ ! -f "${TRUSTSTORE_PATH}" ]; then
  echo "[ERROR] truststore not found: ${TRUSTSTORE_PATH}"
  echo "[NEXT] Run: ./sh_bin/10_1_prepare_djl_truststore.sh"
  exit 1
fi

ACTIVE_PROFILES="${PROFILE}${EXTRA_PROFILE}"
if [ -n "${OPTIONAL_PROFILE}" ]; then
  ACTIVE_PROFILES="${ACTIVE_PROFILES},${OPTIONAL_PROFILE}"
fi
SERVER_PORT="${SERVER_PORT:-${DEFAULT_PORT}}"

echo "[INFO] mode=${MODE}"
echo "[INFO] model=${MODEL_KEY}"
echo "[INFO] spring.profiles.active=${ACTIVE_PROFILES}"
echo "[INFO] server.port=${SERVER_PORT}"
echo "[INFO] ai-search.elasticsearch-url=${AI_SEARCH_ES_URL}"
echo "[INFO] ai-search.username=${AI_SEARCH_ES_USERNAME}"
echo "[INFO] ai-search.k8s.auto-port-forward=${AI_SEARCH_AUTO_PORT_FORWARD}"

cd "${ROOT_DIR}"

if [ "${MODE}" = "web" ]; then
  # 웹 전용 실행은 truststore 없이도 동작할 수 있으므로
  # ES 주소와 포트포워드 여부만 명시적으로 고정해 전달한다.
  AI_SEARCH_ES_URL="${AI_SEARCH_ES_URL}" \
  AI_SEARCH_ES_USERNAME="${AI_SEARCH_ES_USERNAME}" \
  AI_SEARCH_ES_PASSWORD="${AI_SEARCH_ES_PASSWORD}" \
  AI_SEARCH_AUTO_PORT_FORWARD="${AI_SEARCH_AUTO_PORT_FORWARD}" \
  SERVER_PORT="${SERVER_PORT}" \
  ./gradlew bootRun --args="--spring.profiles.active=${ACTIVE_PROFILES}"
  exit 0
fi

# 색인 계열 실행은 truststore와 함께 동일한 ES 연결값을 전달한다.
JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=${TRUSTSTORE_PATH} -Djavax.net.ssl.trustStorePassword=${TRUSTSTORE_PASSWORD}" \
AI_SEARCH_ES_URL="${AI_SEARCH_ES_URL}" \
AI_SEARCH_ES_USERNAME="${AI_SEARCH_ES_USERNAME}" \
AI_SEARCH_ES_PASSWORD="${AI_SEARCH_ES_PASSWORD}" \
AI_SEARCH_AUTO_PORT_FORWARD="${AI_SEARCH_AUTO_PORT_FORWARD}" \
SERVER_PORT="${SERVER_PORT}" \
./gradlew bootRun --args="--spring.profiles.active=${ACTIVE_PROFILES}"
