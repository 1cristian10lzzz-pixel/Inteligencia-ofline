#!/usr/bin/env python3
"""Regression checks for launch-time Android stability in GameForge AI."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "android/app/src/main/java/com/gameforgeai/studio"
RUNTIME = (JAVA / "LocalLlmRuntime.java").read_text(encoding="utf-8")
ACTIVITY = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
MANIFEST = (ROOT / "android/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/android-debug.yml").read_text(encoding="utf-8")
SMOKE_TEST = (ROOT / "scripts/smoke-test-android.sh").read_text(encoding="utf-8")

errors = []

def require(condition, message):
    if not condition:
        errors.append(message)

static_blocks = re.findall(r"\bstatic\s*\{([\s\S]*?)\n\s*\}", RUNTIME)
require(not any("System.loadLibrary(" in block for block in static_blocks),
        "llama.cpp no debe cargarse durante la inicialización estática de la clase")
load_method = re.search(r"public static boolean loadModel\([\s\S]*?\n    \}", RUNTIME)
require(load_method is not None and "ensureLibraryLoaded()" in load_method.group(0),
        "loadModel debe inicializar el runtime en el hilo que solicita la carga")
require("private static synchronized boolean ensureLibraryLoaded()" in RUNTIME,
        "La inicialización nativa debe ser diferida y sincronizada")

require(ACTIVITY.index("setContentView(rootLayout);") < ACTIVITY.index("setStatusBar();"),
        "La ventana debe adjuntarse antes de consultar WindowInsetsController")
require("decor.post(() ->" in ACTIVITY and "window.getInsetsController()" in ACTIVITY,
        "La personalización de barras debe ejecutarse después de adjuntar DecorView")

require("setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)" in ACTIVITY,
        "El renderer visible de WebView debe tener prioridad ante presión de memoria")
require("onRenderProcessGone" in ACTIVITY,
        "MainActivity debe recuperar una caída del proceso WebView")
require("request.isForMainFrame()" in ACTIVITY and "onReceivedError" in ACTIVITY,
        "MainActivity debe mostrar un diagnóstico si falla la página principal local")
require("UI_READY" in ACTIVITY and "document.getElementById('appRoot')" in ACTIVITY and "studio.html" in ACTIVITY,
        "La Activity debe registrar que la interfaz local terminó de cargar")
require('android:hardwareAccelerated="true"' in MANIFEST,
        "La aplicación debe habilitar aceleración hardware para WebView")

require("ram-size: 4096M" in WORKFLOW and "cores: 4" in WORKFLOW and "target: google_apis" in WORKFLOW,
        "El emulador Android 12 debe tener recursos y la imagen Google APIs")
require("sudo chmod 666 /dev/kvm" in WORKFLOW and "-accel-check" in WORKFLOW and "-accel on" in WORKFLOW,
        "La prueba Android debe usar aceleración KVM")
require("ANDROID_I_WANT_MY_TCG" not in WORKFLOW,
        "No se debe forzar emulación CPU por software")
require("api-level: 31" in WORKFLOW,
        "La prueba de arranque debe cubrir Android 12 (API 31)")
require("emulator-boot-timeout: 1200" in WORKFLOW,
        "La prueba KVM debe permitir tiempo suficiente para completar el arranque")
require("script: bash scripts/smoke-test-android.sh" in WORKFLOW,
        "El emulador debe ejecutar la prueba como un único script Bash")
require("pidof \"$PACKAGE\"" in SMOKE_TEST and "UI_READY" in SMOKE_TEST,
        "La prueba debe comprobar proceso activo y carga real de la interfaz")
require('adb logcat -d -s GameForgeAI:I 2>/dev/null | grep -q "UI_READY"' in SMOKE_TEST,
        "El detector UI_READY debe aceptar mensajes informativos de éxito")
require("INFERENCE_SELF_TEST_PASS" in SMOKE_TEST and "wait_for_bundled_ai" in SMOKE_TEST,
        "La prueba debe esperar una inferencia local real del modelo incluido")
require("Second cold start" in SMOKE_TEST and "sleep 15" in SMOKE_TEST,
        "La prueba debe verificar dos arranques en frío y estabilidad posterior")
require("random/boot_id" in SMOKE_TEST and "sys.boot_completed" in SMOKE_TEST,
        "La prueba debe detectar reinicios del propio Android invitado")
require("logcat -d -b all" in SMOKE_TEST and "dumpsys activity exit-info" in SMOKE_TEST,
        "La prueba debe guardar registros de errores y diagnóstico de cierre")
require(not re.search(r"continue-on-error:\s*true", WORKFLOW),
        "La prueba de arranque no puede ser opcional")

if errors:
    print("STARTUP SAFETY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("STARTUP SAFETY: OK")
print(" - JNI llama.cpp se carga bajo demanda, no al abrir la pantalla")
print(" - WebView se puede recuperar y los errores de carga se presentan")
print(" - CI ejecuta la prueba de arranque con Bash y recoge diagnósticos del cierre")
