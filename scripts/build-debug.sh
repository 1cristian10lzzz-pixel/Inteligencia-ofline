#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 "$ROOT/scripts/validate_source.py"
bash "$ROOT/scripts/check-js.sh"
if [[ -x "$ROOT/android/gradlew" ]]; then
  cd "$ROOT/android"
  ./gradlew --no-daemon assembleDebug
elif command -v gradle >/dev/null 2>&1; then
  gradle --no-daemon -p "$ROOT/android" assembleDebug
else
  echo "No se encontró Gradle. Abre android/ en Android Studio o instala Gradle 9.9.1." >&2
  exit 2
fi
printf '\nAPK (si la compilación termina correctamente): %s\n' "$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
