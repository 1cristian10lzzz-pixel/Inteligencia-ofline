#!/usr/bin/env bash
set -euo pipefail
HTML="$(dirname "$0")/../android/app/src/main/assets/index.html"
TMP="$(mktemp --suffix=.js)"
trap 'rm -f "$TMP"' EXIT
python3 - "$HTML" "$TMP" <<'PY'
import re, sys
html = open(sys.argv[1], encoding="utf-8").read()
scripts = re.findall(r"<script>(.*?)</script>", html, flags=re.S)
if not scripts:
    raise SystemExit("No se encontró JavaScript inline")
open(sys.argv[2], "w", encoding="utf-8").write("\n".join(scripts))
PY
node --check "$TMP"
echo "JavaScript syntax: OK"
