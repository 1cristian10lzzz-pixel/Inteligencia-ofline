#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT/android/app/src/main/assets"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
MINEFADE_REPOSITORY="1cristian10lzzz-pixel/Minefade-2.0"
MINEFADE_COMMIT="$(printenv MINEFADE_COMMIT || echo 2fd295a78890e6172375528d57963d213270ab01)"
MODEL_REPOSITORY="tensorblock/Qwen2.5-Coder-0.5B-Instruct-GGUF"
MODEL_FILE="Qwen2.5-Coder-0.5B-Instruct-Q3_K_M.gguf"
MODEL_ASSET="$ASSETS/models/qwen2.5-coder-0.5b-instruct-q3_k_m.gguf"
command -v curl >/dev/null
command -v python3 >/dev/null
command -v tar >/dev/null
mkdir -p "$ASSETS/builtin" "$ASSETS/models"

echo "Descargando snapshot Minefade $MINEFADE_COMMIT"
curl --fail --location --retry 3 --retry-delay 2 \
  "https://codeload.github.com/$MINEFADE_REPOSITORY/tar.gz/$MINEFADE_COMMIT" \
  --output "$TMP/minefade.tar.gz"
mkdir -p "$TMP/minefade"
tar -xzf "$TMP/minefade.tar.gz" --strip-components=1 -C "$TMP/minefade"
test -s "$TMP/minefade/README.md"
test -f "$TMP/minefade/app/src/main/cpp/NativeEngine.cpp"

echo "Resolviendo revisión exacta del modelo GGUF"
MODEL_REVISION="$(curl --fail --location --retry 3 \
  "https://huggingface.co/api/models/$MODEL_REPOSITORY" \
  | python3 -c 'import json,sys; value=json.load(sys.stdin); print(value["sha"])')"
test -n "$MODEL_REVISION"

if [[ ! -s "$MODEL_ASSET" ]]; then
  echo "Descargando modelo incluido $MODEL_FILE ($MODEL_REVISION)"
  curl --fail --location --retry 4 --retry-delay 3 \
    "https://huggingface.co/$MODEL_REPOSITORY/resolve/$MODEL_REVISION/$MODEL_FILE?download=true" \
    --output "$MODEL_ASSET"
fi
MODEL_SIZE="$(stat -c '%s' "$MODEL_ASSET")"
if (( MODEL_SIZE < 300000000 || MODEL_SIZE > 400000000 )); then
  echo "Tamaño inesperado del GGUF: $MODEL_SIZE bytes" >&2
  exit 1
fi
MODEL_SHA256="$(sha256sum "$MODEL_ASSET" | awk '{print $1}')"

echo "Preparando manifiesto y licencia del modelo"
curl --fail --location --retry 3 \
  "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct/resolve/main/LICENSE" \
  --output "$ASSETS/models/QWEN-LICENSE.txt"

python3 - "$TMP/minefade" "$ASSETS/builtin/minefade-source.zip" \
  "$MINEFADE_REPOSITORY" "$MINEFADE_COMMIT" <<'PY'
import json, sys, zipfile
from pathlib import Path
source = Path(sys.argv[1])
destination = Path(sys.argv[2])
repository, commit = sys.argv[3], sys.argv[4]
manifest = {
    "repository": "https://github.com/" + repository,
    "commit": commit,
    "version": "Minefade 2.2.0 source snapshot",
    "includedFor": "offline source analysis and controlled editing in GameForge AI"
}
(source / "gameforge-bundle.json").write_text(
    json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
)
skip_dirs = {".git", ".gradle", ".idea", "build", "outputs", "target",
             "node_modules", ".cxx", ".externalNativeBuild"}
written = 0
with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
    for path in sorted(source.rglob("*")):
        if not path.is_file() or path.is_symlink():
            continue
        relative = path.relative_to(source)
        if any(part in skip_dirs or part.startswith(".git") for part in relative.parts):
            continue
        if "local.properties" in relative.name.lower():
            continue
        size = path.stat().st_size
        if size > 16 * 1024 * 1024:
            raise SystemExit(f"Unexpected large source file: {relative} ({size})")
        archive.write(path, relative.as_posix())
        written += 1
if written < 50:
    raise SystemExit(f"Minefade source snapshot seems incomplete: only {written} files")
print(f"Packed {written} Minefade source files into {destination}")
PY

cat > "$ASSETS/models/model-info.json" <<EOF
{
  "name": "Qwen2.5-Coder-0.5B-Instruct",
  "quantization": "Q3_K_M",
  "format": "GGUF",
  "repository": "https://huggingface.co/$MODEL_REPOSITORY",
  "revision": "$MODEL_REVISION",
  "upstreamModel": "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct",
  "license": "Apache-2.0",
  "file": "$MODEL_FILE",
  "sizeBytes": $MODEL_SIZE,
  "sha256": "$MODEL_SHA256",
  "note": "Small offline coding model; specialized Minefade behavior is supplied by the local source index and agent tools."
}
EOF
printf '%s  %s\n' "$MODEL_SHA256" "$MODEL_FILE" > "$ASSETS/models/SHA256SUMS.txt"
python3 - "$ASSETS/builtin/minefade-source.zip" "$ASSETS/models/model-info.json" <<'PY'
import json, sys, zipfile
from pathlib import Path
bundle = Path(sys.argv[1])
model_info = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
with zipfile.ZipFile(bundle) as archive:
    bad = archive.testzip()
    if bad:
        raise SystemExit(f"Minefade bundle CRC failed at {bad}")
    names = set(archive.namelist())
    for required in (
        "README.md", "app/src/main/cpp/NativeEngine.cpp",
        "app/src/main/cpp/engine/Engine.cpp",
        "app/src/main/java/com/minefade/nativeengine/MainActivity.java",
        "rust-core/src/lib.rs", "tests/run-world-tests.sh",
        "docs/ENGINE_ARCHITECTURE.md", "gameforge-bundle.json"
    ):
        if required not in names:
            raise SystemExit(f"Missing Minefade source file: {required}")
if model_info["sizeBytes"] < 300000000 or len(model_info["sha256"]) != 64:
    raise SystemExit("Model manifest validation failed")
print("Bundled source and GGUF manifests: OK")
PY
echo "Minefade source bundle: $(du -h "$ASSETS/builtin/minefade-source.zip" | cut -f1)"
echo "Bundled GGUF: $(du -h "$MODEL_ASSET" | cut -f1)"
echo "GGUF SHA-256: $MODEL_SHA256"
