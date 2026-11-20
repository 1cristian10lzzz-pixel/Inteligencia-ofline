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

require("onRenderProcessGone" in ACTIVITY,
        "MainActivity debe recuperar una caída del proceso WebView")
require("request.isForMainFrame()" in ACTIVITY and "onReceivedError" in ACTIVITY,
        "MainActivity debe mostrar un diagnóstico si falla la página principal local")
require("UI_READY" in ACTIVITY and "document.getElementById('messages')" in ACTIVITY,
        "La Activity debe registrar que la interfaz local terminó de cargar")
require('android:hardwareAccelerated="true"' in MANIFEST,
        "La aplicación debe habilitar aceleración hardware para WebView")

require("api-level: 31" in WORKFLOW,
        "La prueba de arranque debe cubrir Android 12 (API 31)")
require('ANDROID_I_WANT_MY_TCG: "yes"' in WORKFLOW,
        "El emulador sin aceleración debe habilitar TCG por software")
require("emulator-boot-timeout: 1200" in WORKFLOW,
        "La prueba debe permitir más tiempo al emulador de software")
require("pidof com.gameforgeai.studio" in WORKFLOW and "UI_READY" in WORKFLOW,
        "CI debe comprobar proceso activo y carga real de la interfaz")
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
print(" - CI exige proceso activo y carga de la interfaz en Android 12")
