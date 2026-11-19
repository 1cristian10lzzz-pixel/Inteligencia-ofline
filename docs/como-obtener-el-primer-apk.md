# Compilar y obtener GameForge AI APK

## GitHub Actions (recomendado)

1. Abre **Actions** en el repositorio.
2. Selecciona **Build GameForge AI APK**.
3. Ejecuta `Run workflow` o realiza un push a `main`.
4. Espera a que terminen `validate` y `build` (incluye CMake/NDK, llama.cpp y prueba en emulador).
5. Descarga el artefacto `gameforge-ai-debug-apk` desde la ejecución completada.
6. Extrae `app-debug.apk`, pásalo a Android y habilita instalación para esa fuente.

El APK es de depuración. Es instalable para pruebas, pero no es una versión firmada de distribución.

## Compilación local

Instala JDK 17, Android SDK Platform 37, Build Tools 36.0.0, NDK 29.0.14206865, CMake 3.31.6 y Gradle 9.9.1. Con conexión durante la primera compilación para descargar las fuentes fijadas de llama.cpp:

```bash
python3 scripts/validate_source.py
bash scripts/check-js.sh
gradle --no-daemon -p android assembleDebug
```

El APK aparecerá en `android/app/build/outputs/apk/debug/app-debug.apk`.

## Primera prueba en el teléfono

1. Instala y abre GameForge AI.
2. Crea un proyecto 2D o 3D y exporta su ZIP de fuentes.
3. Importa un GGUF instruct pequeño y compatible.
4. Cárgalo desde el selector de modelos; espera la confirmación.
5. Envía una pregunta corta y comprueba que el modo del asistente indique `Modelo neuronal local`.
6. Activa modo avión y repite la conversación para verificar funcionamiento offline.

La app no descarga modelos. El modelo se debe transferir/importar manualmente y necesita RAM suficiente.
