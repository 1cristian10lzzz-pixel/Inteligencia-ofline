#!/usr/bin/env python3
"""Static checks for the first GameForge AI source drop."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "android"
ASSETS = ANDROID / "app/src/main/assets"
MANIFEST = ANDROID / "app/src/main/AndroidManifest.xml"

errors = []

def require(condition, message):
    if not condition:
        errors.append(message)

try:
    ET.parse(MANIFEST)
except Exception as exc:
    errors.append(f"AndroidManifest.xml no es XML válido: {exc}")

manifest = MANIFEST.read_text(encoding="utf-8")
require("android.permission.INTERNET" not in manifest, "La app no debe declarar INTERNET")
require('android:name=".MainActivity"' in manifest, "Falta MainActivity declarada")

app_gradle = (ANDROID / "app/build.gradle.kts").read_text(encoding="utf-8")
require("compileSdk = 37" in app_gradle, "compileSdk esperado: 37")
require("targetSdk = 36" in app_gradle, "targetSdk esperado: 36")
require('version "9.4.0"' in (ANDROID / "build.gradle.kts").read_text(encoding="utf-8"), "AGP estable 9.4.0 esperado")
require("9.9.1" in (ROOT / "scripts/prepare-gradle-wrapper.sh").read_text(encoding="utf-8"), "Gradle wrapper debe usar 9.9.1")
require("externalNativeBuild" in app_gradle, "Falta compilación nativa CMake")
require("29.0.14206865" in app_gradle, "Falta fijar versión NDK")

main_java = (ANDROID / "app/src/main/java/com/gameforgeai/studio/MainActivity.java").read_text(encoding="utf-8")
for token in ["@JavascriptInterface", "ACTION_CREATE_DOCUMENT", "ACTION_OPEN_DOCUMENT", "templates/", "runtimeLabel", "askAsync", "loadModelAsync"]:
    require(token in main_java, f"Falta integración esperada en MainActivity: {token}")
require("MainActivity.this.createProject" in main_java, "El puente debe delegar createProject a la Activity")

html = (ASSETS / "index.html").read_text(encoding="utf-8")
require("GameForge.createProject" in html, "Falta acción de creación desde la UI")
require("GameForge.exportProject" in html, "Falta acción de exportación de proyecto")
require("modelo gguf" in html.lower(), "La UI debe informar sobre el modelo local")
require("GameForge.askAsync" in html, "El chat debe llamar a la generación asíncrona")
require("window.onGameForgeAnswer" in html, "Falta callback de respuesta del modelo")
cmake = (ANDROID / "app/src/main/cpp/CMakeLists.txt").read_text(encoding="utf-8")
require("ggml-org/llama.cpp" in cmake, "Falta fuente real de llama.cpp")
require("c406f4367e19bea7930e7f74e1984c9acc6d44bf" in cmake, "llama.cpp debe estar fijado a una revisión concreta")
native = (ANDROID / "app/src/main/cpp/native-lib.cpp").read_text(encoding="utf-8")
for token in ["llama_model_load_from_file", "llama_init_from_model", "llama_decode", "llama_sampler_sample"]:
    require(token in native, f"Falta operación de inferencia real: {token}")

for template, expected in {
    "survival_3d": ["project.godot", "main.tscn", "scripts/main.gd", "scripts/player.gd", "README.md"],
    "platformer_2d": ["project.godot", "main.tscn", "scripts/main.gd", "scripts/player.gd", "README.md"],
}.items():
    base = ASSETS / "templates" / template
    for rel in expected:
        require((base / rel).is_file(), f"Falta plantilla {template}/{rel}")
    project_text = (base / "project.godot").read_text(encoding="utf-8")
    require('run/main_scene="res://main.tscn"' in project_text, f"Escena principal mal configurada: {template}")
    scene_text = (base / "main.tscn").read_text(encoding="utf-8")
    for resource_path in re.findall(r'path="res://([^"]+)"', scene_text):
        require((base / resource_path).is_file(), f"Recurso de escena inexistente: {template}/{resource_path}")

# Guard against obviously broken template markers.
for gd in ASSETS.glob("templates/**/*.gd"):
    text = gd.read_text(encoding="utf-8")
    require("TODO_IMPLEMENT" not in text, f"Marcador pendiente en {gd.relative_to(ROOT)}")
    require("extends " in text, f"Falta extends en {gd.relative_to(ROOT)}")

if errors:
    print("VALIDACIÓN: ERROR")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("VALIDACIÓN ESTÁTICA: OK")
print(" - Manifest XML válido")
print(" - Sin permiso INTERNET declarado")
print(" - Configuración de SDK y actividad presente")
print(" - Puente Android/UI con creación, modelos GGUF e inferencia asíncrona")
print(" - JNI llama.cpp enlazado desde CMake y sin permiso de red")
print(" - Plantillas Godot 2D y 3D completas en estructura y con referencias resueltas")
print("Nota: esto no sustituye compilar ni ejecutar Godot en un dispositivo real.")
