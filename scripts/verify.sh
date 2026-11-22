#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! command -v java >/dev/null 2>&1 && [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JDK 25 is required. Install Temurin 25 and set JAVA_HOME." >&2
  exit 1
fi

select_java25() {
  local candidate
  for candidate in \
    "${JAVA_HOME:-}" \
    "${HOME}/.sdkman/candidates/java/25.0.5.1+2-tem" \
    "/usr/lib/jvm/temurin-25-jdk" \
    "/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home"
  do
    if [[ -n "${candidate}" && -x "${candidate}/bin/java" ]]; then
      local version
      version="$("${candidate}/bin/java" -version 2>&1 || true)"
      if [[ "${version}" == *'"25.'* ]]; then
        export JAVA_HOME="${candidate}"
        export PATH="${JAVA_HOME}/bin:${PATH}"
        return 0
      fi
    fi
  done
  return 1
}

if ! select_java25; then
  echo "JDK 25 LTS was not found. Refusing to build with another major version." >&2
  exit 1
fi

echo "Using ${JAVA_HOME}"
java -version
cd "${ROOT}"
mvn --batch-mode clean verify
