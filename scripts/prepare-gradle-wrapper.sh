#!/usr/bin/env bash
set -euo pipefail
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle no está instalado. Instálalo o abre android/ con Android Studio." >&2
  exit 1
fi
cd "$(dirname "$0")/../android"
gradle wrapper --gradle-version 9.9.1 --distribution-type bin
