# Avisos de terceros

## llama.cpp / ggml

Proyecto upstream: https://github.com/ggml-org/llama.cpp

Revisión fijada: c406f4367e19bea7930e7f74e1984c9acc6d44bf

Licencia: MIT. El código fuente se obtiene durante la compilación mediante CMake FetchContent y se enlaza dentro del runtime Android. Conserva los avisos de esa revisión al distribuir binarios.

## Qwen2.5-Coder-0.5B-Instruct GGUF

Modelo base: https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct

GGUF cuantizado: https://huggingface.co/tensorblock/Qwen2.5-Coder-0.5B-Instruct-GGUF

Archivo integrado: Qwen2.5-Coder-0.5B-Instruct-Q3_K_M.gguf. La tarjeta del repositorio GGUF declara Apache-2.0. El workflow descarga también el archivo LICENSE del modelo upstream y lo empaqueta junto al GGUF como models/QWEN-LICENSE.txt.

El modelo se descarga en CI, se fija a la revisión informada por el API del repositorio y se verifica mediante SHA-256 antes de compilar el APK. No se debe actualizar la revisión sin volver a validar el archivo y su licencia.
