#!/bin/sh
set -eu
GRADLE_VERSION=8.13
CACHE_DIR="${HOME}/.gradle/data-bottle-bootstrap"
GRADLE_HOME="${CACHE_DIR}/gradle-${GRADLE_VERSION}"
ZIP_FILE="${CACHE_DIR}/gradle-${GRADLE_VERSION}-bin.zip"

if [ ! -x "${GRADLE_HOME}/bin/gradle" ]; then
  mkdir -p "${CACHE_DIR}"
  echo "[DATA BOTTLE] Downloading Gradle ${GRADLE_VERSION}..."
  if command -v curl >/dev/null 2>&1; then
    curl -L "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "${ZIP_FILE}"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "${ZIP_FILE}" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  else
    echo "curl or wget is required" >&2
    exit 1
  fi
  unzip -q -o "${ZIP_FILE}" -d "${CACHE_DIR}"
fi
exec "${GRADLE_HOME}/bin/gradle" "$@"
