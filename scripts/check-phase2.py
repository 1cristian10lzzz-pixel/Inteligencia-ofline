#!/usr/bin/env python3
"""Static safety and integration checks for GameForge AI phase 2 tools."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "android/app/src/main/java/com/gameforgeai/studio"
WORKSPACE = (JAVA / "ProjectWorkspace.java").read_text(encoding="utf-8")
ACTIVITY = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
HTML = (ROOT / "android/app/src/main/assets/index.html").read_text(encoding="utf-8")
MANIFEST = (ROOT / "android/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

checks = {
    "project file listing": "listFiles(String projectId)" in WORKSPACE,
    "bounded file read": "MAX_FILE_BYTES = 128 * 1024" in WORKSPACE,
    "canonical path containment": "getCanonicalFile()" in WORKSPACE and "startsWith(base)" in WORKSPACE,
    "extension allow-list": "isTextExtension(String path)" in WORKSPACE,
    "preview without writing": "previewEdit(String projectId, String proposalJson)" in WORKSPACE,
    "stale-file hash check": "sha256(current).equals(edit.oldHash)" in WORKSPACE,
    "atomic project writes": "StandardCopyOption.ATOMIC_MOVE" in WORKSPACE,
    "backups outside project": 'new File(filesDir, "project-backups")' in WORKSPACE,
    "undo support": "undoLast(String projectId)" in WORKSPACE,
    "separate model and generation queues": "modelExecutor" in ACTIVITY and "generationExecutor" in ACTIVITY,
    "chat history in prompt": "buildAgentPrompt(String prompt, String historyJson, String projectId)" in ACTIVITY,
    "structured edit proposal": "<gameforge-edit>" in ACTIVITY and "editProposal" in ACTIVITY,
    "bridge exposes preview/apply/undo": all(name in ACTIVITY for name in (
        "previewProjectEdit", "applyProjectEdit", "cancelProjectEdit", "undoLastProjectEdit")),
    "chat saved locally": "localStorage.setItem(CHAT_STORAGE_KEY" in HTML,
    "chat history sent to model": "JSON.stringify(history)" in HTML,
    "user approves before saving": "Aplicar cambio" in HTML and "previewProjectProposal" in HTML,
    "file explorer visible": "projectFileContent" in HTML and "loadProjectFile()" in HTML,
    "launcher icon configured": 'android:icon="@mipmap/ic_launcher"' in MANIFEST,
    "adaptive icon resource": (ROOT / "android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").is_file(),
    "icon foreground vector": (ROOT / "android/app/src/main/res/drawable/ic_launcher_foreground.xml").is_file(),
}

failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(("PASS" if passed else "FAIL") + "  " + name)
if failed:
    raise SystemExit(f"PHASE 2 CHECKS FAILED: {len(failed)}")
print(f"PHASE 2 CHECKS: OK ({len(checks)}/{len(checks)})")
