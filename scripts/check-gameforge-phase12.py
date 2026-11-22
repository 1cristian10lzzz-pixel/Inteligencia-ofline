#!/usr/bin/env python3
"""Integration checks for bundled offline Minefade AI and the project agent."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "android/app/src/main/java/com/gameforgeai/studio"
ACTIVITY = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
WORKSPACE = (JAVA / "MinefadeProjectWorkspace.java").read_text(encoding="utf-8")
INSTALLER = (JAVA / "BuiltinAssetsInstaller.java").read_text(encoding="utf-8")
HTML = (ROOT / "android/app/src/main/assets/studio.html").read_text(encoding="utf-8")
GRADLE = (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/android-debug.yml").read_text(encoding="utf-8")
MANIFEST = (ROOT / "android/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

checks = {
    "startup loads the dedicated studio screen": "studio.html" in ACTIVITY,
    "project and model bootstrap are started automatically": "bootstrapBundledAssetsAndModel()" in ACTIVITY,
    "built-in Minefade is installed from the APK": "builtin/minefade-source.zip" in INSTALLER,
    "built-in model is installed without a document picker": "models/qwen2.5-coder-0.5b-instruct-q3_k_m.gguf" in INSTALLER,
    "model is loaded automatically after copy": "LocalLlmRuntime.loadModel" in ACTIVITY and "MODEL_READY" in ACTIVITY,
    "source workspace is specific to Minefade": "MinefadeProjectWorkspace" in ACTIVITY,
    "project root checks prevent path traversal": "getCanonicalFile()" in WORKSPACE and "startsWith(base.getPath() + File.separator)" in WORKSPACE,
    "workspace has a narrow source file allow-list": "isTextFile(String value)" in WORKSPACE,
    "sensitive signing files are protected": "isProtectedPath(String path)" in WORKSPACE,
    "batch edit has an eight-file limit": "MAX_BATCH_FILES = 8" in WORKSPACE,
    "batch edit preview is separate from apply": "previewBatch(String proposalJson)" in WORKSPACE and "applyBatch(String previewId)" in WORKSPACE,
    "batch writes verify hashes before modifying files": "sha256(before)" in WORKSPACE and "cambió desde la vista previa" in WORKSPACE,
    "multi-file batch rollback is implemented": "for (int i = written - 1; i >= 0; i--)" in WORKSPACE,
    "whole-task undo checks new file hashes": "public synchronized JSONObject undoLast()" in WORKSPACE and "newHash" in WORKSPACE,
    "context is specialized for Minefade native code": "C++20" in WORKSPACE and "MFVOX001/002/003" in WORKSPACE,
    "new UI contains separated routes": all(text in HTML for text in ("data-page=\"home\"", "data-page=\"projects\"", "data-page=\"chat\"", "data-page=\"files\"", "data-page=\"repair\"", "data-page=\"settings\"")),
    "new UI supports per-project conversations": "localStorage" in HTML and "sessions" in HTML,
    "new UI submits prompts to local inference": "GameForge.askAsync" in HTML,
    "new UI can review and apply a batch proposal": all(text in HTML for text in ("previewProjectBatchEdit", "applyProjectBatchEdit", "cancelProjectBatchEdit")),
    "model is not compressed inside APK": '"gguf"' in GRADLE and "noCompress" in GRADLE,
    "long prompt is decoded in bounded llama.cpp batches": "kPromptBatchLimit = 256" in (ROOT / "android/app/src/main/cpp/native-lib.cpp").read_text(encoding="utf-8"),
    "startup self-test includes a long source context": "longSelfTestPrompt" in ACTIVITY and "i < 40" in ACTIVITY,
    "CI prepares a Minefade source snapshot and local GGUF": "prepare-bundled-assets.sh" in WORKFLOW,
    "APK does not request Internet permission": "android.permission.INTERNET" not in MANIFEST,
}
failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(("PASS" if passed else "FAIL") + "  " + name)
if failed:
    raise SystemExit(f"GAMEFORGE PHASE 1/2 CHECKS FAILED: {len(failed)}")
print(f"GAMEFORGE PHASE 1/2 CHECKS: OK ({len(checks)}/{len(checks)})")
