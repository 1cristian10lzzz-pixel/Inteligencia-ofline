#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
python3 - "$ROOT/android/app/src/main/assets/index.html" "$ROOT/android/app/src/main/assets/studio.html" "$TMP" <<'PY'
import re, sys
from pathlib import Path
for index, source in enumerate(sys.argv[1:3]):
    html = Path(source).read_text(encoding="utf-8")
    scripts = re.findall(r"<script>(.*?)</script>", html, flags=re.S)
    if not scripts:
        raise SystemExit(f"No se encontró JavaScript inline en {source}")
    Path(sys.argv[3], f"screen-{index}.js").write_text("\n".join(scripts), encoding="utf-8")
PY
node --check "$TMP/screen-0.js"
node --check "$TMP/screen-1.js"
echo "JavaScript syntax (legacy + studio UI): OK"
