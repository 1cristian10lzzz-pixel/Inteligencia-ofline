#!/usr/bin/env bash
set -Eeuo pipefail

PACKAGE="com.gameforgeai.studio"
ACTIVITY="$PACKAGE/.MainActivity"
APK="android/app/build/outputs/apk/debug/app-debug.apk"
LOG_FILE="startup-logcat.txt"
SCREEN_FILE="startup-screen.png"

capture_diagnostics() {
  adb logcat -d -b all -v threadtime > "$LOG_FILE" 2>/dev/null || true
  printf '\n===== CRASH BUFFER =====\n' >> "$LOG_FILE"
  adb logcat -d -b crash -v threadtime >> "$LOG_FILE" 2>/dev/null || true
  adb shell dumpsys activity activities > activity-dump.txt 2>&1 || true
  adb shell dumpsys activity exit-info "$PACKAGE" > exit-info.txt 2>&1 || true
  adb exec-out screencap -p > "$SCREEN_FILE" 2>/dev/null || true
}

trap capture_diagnostics EXIT

test -s "$APK"
adb wait-for-device
adb install -r "$APK"
adb logcat -c
adb shell am force-stop "$PACKAGE"

echo "Starting $ACTIVITY"
START_OUTPUT="$(adb shell am start -W -n "$ACTIVITY" 2>&1)" || {
  printf '%s\n' "$START_OUTPUT"
  echo "FAIL: Android no pudo iniciar MainActivity."
  exit 1
}
printf '%s\n' "$START_OUTPUT"

APP_PID=""
for ((attempt=0; attempt<30; attempt++)); do
  APP_PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "$APP_PID" ]]; then
    break
  fi
  sleep 1
done

if [[ -z "$APP_PID" ]]; then
  echo "FAIL: GameForge AI no mantuvo un proceso activo después de iniciar."
  capture_diagnostics
  exit 1
fi

UI_READY=""
for ((attempt=0; attempt<30; attempt++)); do
  if adb logcat -d -s GameForgeAI:I GameForgeAI:E 2>/dev/null | grep -q "UI_READY"; then
    UI_READY="yes"
    break
  fi
  sleep 1
done

if [[ -z "$UI_READY" ]]; then
  echo "FAIL: MainActivity vive, pero WebView no confirmó UI_READY."
  capture_diagnostics
  exit 1
fi

sleep 3
FINAL_PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
if [[ -z "$FINAL_PID" ]]; then
  echo "FAIL: la aplicación se cerró después de mostrar la interfaz."
  capture_diagnostics
  exit 1
fi

echo "PASS: GameForge AI está abierta (PID $FINAL_PID) y la interfaz local confirmó UI_READY."
