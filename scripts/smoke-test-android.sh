#!/usr/bin/env bash
set -Eeuo pipefail
PACKAGE="com.gameforgeai.studio"
ACTIVITY="$PACKAGE/.MainActivity"
APK="android/app/build/outputs/apk/debug/app-debug.apk"
RESULT_FILE="startup-result.txt"
BOOT_ID_BEFORE=""

capture_diagnostics() {
  adb logcat -d -b all -v threadtime > startup-logcat.txt 2>/dev/null || true
  printf '\n===== CRASH BUFFER =====\n' >> startup-logcat.txt
  adb logcat -d -b crash -v threadtime >> startup-logcat.txt 2>/dev/null || true
  adb shell dumpsys activity activities > activity-dump.txt 2>&1 || true
  adb shell dumpsys activity exit-info "$PACKAGE" > exit-info.txt 2>&1 || true
  adb shell dumpsys meminfo "$PACKAGE" > startup-meminfo.txt 2>&1 || true
  adb exec-out screencap -p > startup-screen.png 2>/dev/null || true
}
fail() {
  echo "FAIL: $1"
  printf 'FAIL: %s\n' "$1" > "$RESULT_FILE"
  capture_diagnostics
  exit 1
}
check_emulator_unchanged() {
  local current_boot
  current_boot="$(adb shell cat /proc/sys/kernel/random/boot_id 2>/dev/null | tr -d '\r' || true)"
  [[ -n "$current_boot" ]] || fail "Android dejó de responder durante la prueba."
  [[ "$current_boot" == "$BOOT_ID_BEFORE" ]] || fail "El propio Android del emulador se reinició durante la prueba."
  [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] || fail "Android no terminó de arrancar."
}
wait_for_gameforge_ui() {
  local phase="$1" pid="" ready="" attempt
  for ((attempt=0; attempt<90; attempt++)); do
    check_emulator_unchanged
    pid="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
    if [[ -n "$pid" ]] && adb logcat -d -s GameForgeAI:I 2>/dev/null | grep -q "UI_READY"; then
      ready="yes"; break
    fi
    sleep 1
  done
  [[ -n "$pid" ]] || fail "GameForge AI cerró su proceso durante $phase."
  [[ "$ready" == "yes" ]] || fail "WebView no terminó de cargar la interfaz durante $phase."
  if adb logcat -d -b crash -v threadtime 2>/dev/null | grep -q "Process: $PACKAGE"; then
    fail "Android registró una excepción fatal dentro de GameForge AI durante $phase."
  fi
}
trap capture_diagnostics EXIT
test -s "$APK"
adb wait-for-device
for ((attempt=0; attempt<180; attempt++)); do
  [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] && break
  sleep 1
done
[[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] || fail "El emulador no terminó el arranque."
BOOT_ID_BEFORE="$(adb shell cat /proc/sys/kernel/random/boot_id 2>/dev/null | tr -d '\r' || true)"
[[ -n "$BOOT_ID_BEFORE" ]] || fail "No se pudo verificar la identidad de arranque de Android."
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb shell input keyevent 82 >/dev/null 2>&1 || true
adb install -r "$APK" || fail "No se pudo instalar el APK en Android 12."
adb logcat -c
adb shell am force-stop "$PACKAGE"
echo "Cold start: $ACTIVITY"
START_OUTPUT="$(adb shell am start -W -n "$ACTIVITY" 2>&1)" || fail "Android rechazó MainActivity: $START_OUTPUT"
printf '%s\n' "$START_OUTPUT"
wait_for_gameforge_ui "el primer arranque"
echo "Second cold start"
adb shell am force-stop "$PACKAGE"
sleep 2
adb logcat -c
START_OUTPUT="$(adb shell am start -W -n "$ACTIVITY" 2>&1)" || fail "Android rechazó el segundo inicio: $START_OUTPUT"
printf '%s\n' "$START_OUTPUT"
wait_for_gameforge_ui "el segundo arranque"
sleep 15
check_emulator_unchanged
FINAL_PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$FINAL_PID" ]] || fail "La aplicación se cerró durante los 15 segundos de estabilidad."
if adb logcat -d -b crash -v threadtime 2>/dev/null | grep -q "Process: $PACKAGE"; then
  fail "Se detectó una excepción fatal durante la comprobación final."
fi
printf 'PASS: instalación, dos arranques en frío, interfaz UI_READY y proceso estable (PID %s).\n' "$FINAL_PID" | tee "$RESULT_FILE"
