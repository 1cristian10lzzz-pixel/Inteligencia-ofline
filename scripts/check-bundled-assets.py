#!/usr/bin/env python3
"""Checks the exact model and Minefade snapshot prepared for the Android APK."""
from pathlib import Path
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "android/app/src/main/assets"
MODEL = ASSETS / "models/qwen2.5-coder-0.5b-instruct-q3_k_m.gguf"
INFO = ASSETS / "models/model-info.json"
BUNDLE = ASSETS / "builtin/minefade-source.zip"

def require(condition, message):
    if not condition:
        raise SystemExit("BUNDLED ASSETS: FAIL — " + message)

require(MODEL.is_file(), "no se incluyó el modelo GGUF")
require(INFO.is_file(), "falta model-info.json")
require(BUNDLE.is_file(), "falta la instantánea del código Minefade")
info = json.loads(INFO.read_text(encoding="utf-8"))
size = MODEL.stat().st_size
require(300_000_000 <= size <= 400_000_000, f"tamaño inesperado del modelo: {size}")
require(size == info.get("sizeBytes"), "el tamaño no coincide con el manifiesto")
digest = hashlib.sha256()
with MODEL.open("rb") as source:
    for chunk in iter(lambda: source.read(1024 * 1024), b""):
        digest.update(chunk)
sha = digest.hexdigest()
require(sha == info.get("sha256"), "el SHA-256 del GGUF no coincide")
require(info.get("license") == "Apache-2.0", "falta la licencia declarada del modelo")
require((ASSETS / "models/QWEN-LICENSE.txt").is_file(), "falta la licencia de Qwen")
with zipfile.ZipFile(BUNDLE) as archive:
    require(archive.testzip() is None, "el ZIP de Minefade está dañado")
    names = set(archive.namelist())
    for path in (
        "README.md", "app/src/main/cpp/NativeEngine.cpp",
        "app/src/main/cpp/world/VoxelWorld.cpp",
        "app/src/main/java/com/minefade/nativeengine/MainActivity.java",
        "rust-core/src/lib.rs", "docs/ENGINE_ARCHITECTURE.md",
        "gameforge-bundle.json",
    ):
        require(path in names, f"falta {path} en el proyecto Minefade")
print(f"BUNDLED ASSETS: OK — GGUF {size:,} bytes, Minefade snapshot {BUNDLE.stat().st_size:,} bytes")
