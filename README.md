# GameForge AI — estudio de creación de videojuegos Android offline

**Versión:** 0.2.0-alpha. **APK debug:** compilado y publicado por GitHub Actions el 19 de noviembre de 2026.

GameForge AI es una aplicación Android nativa diseñada para trabajar sin servicios de IA en la nube. No declara permiso `android.permission.INTERNET`.

## Funciones incluidas

- Interfaz Android local y almacenamiento privado.
- Motor nativo `llama.cpp` enlazado mediante JNI.
- Importación de modelos GGUF desde el dispositivo.
- Carga del modelo y generación de texto en un hilo de trabajo.
- Bibliotecas nativas incluidas para `arm64-v8a` y `x86_64`.
- Creación de proyectos Godot a partir de plantillas 2D y supervivencia 3D.
- Exportación de proyectos fuente como ZIP.

## Descargas

El APK de prueba está publicado en GitHub Actions, en el artefacto `gameforge-ai-debug-apk` de la ejecución de compilación. El archivo generado es `app-debug.apk`.

## Preparar el funcionamiento offline

1. Instala el APK.
2. Descarga una sola vez un modelo GGUF compatible con el hardware y la RAM del teléfono (por ejemplo, una versión instruct pequeña y cuantizada).
3. Abre la sección **IA local y dispositivo** e importa ese archivo GGUF.
4. Carga el modelo. Una vez guardado localmente, el chat de IA no necesita conexión a internet.

El modelo GGUF no va embebido en el APK para evitar que la instalación pese cientos de MB. Debes revisar la licencia del modelo que elijas. La inferencia de esta versión usa CPU; GPU/NPU todavía no están habilitadas.

## Límites importantes de 0.2.0-alpha

- Esta versión incluye un runtime local real de llama.cpp, pero todavía debe probarse la inferencia con un GGUF concreto en el teléfono de destino.
- El asistente ayuda con código e instrucciones, pero aún no aplica de forma autónoma cambios arbitrarios del modelo a los archivos del proyecto.
- La aplicación crea y exporta proyectos Godot fuente; no contiene el editor Godot ni compila todavía el APK del videojuego dentro de GameForge AI. Para editar/exportar el juego se abre el proyecto en Godot o Godot Android Editor.
- El APK es de depuración firmado con la clave debug de CI. Es instalable para pruebas, pero no es una versión de distribución ni tiene una clave de lanzamiento permanente.

## Compilación reproducible

Requisitos de CI: JDK 17, Android SDK Platform 36, Build Tools 36.0.0, NDK 29.0.14206865, CMake 3.31.6 y Gradle 9.9.1. Durante el build, CMake obtiene la revisión fijada de llama.cpp.

```bash
python3 scripts/validate_source.py
bash scripts/check-js.sh
gradle --no-daemon -p android assembleDebug
```

Salida: `android/app/build/outputs/apk/debug/app-debug.apk`.

## Licencias y seguridad

- llama.cpp se incorpora bajo su licencia MIT.
- Los modelos GGUF no se redistribuyen; revisa las licencias de sus autores.
- No guardes claves privadas ni contraseñas en el repositorio.
- La aplicación no solicita permiso de Internet ni requiere una API key.

Consulta [BUILD_STATUS.md](BUILD_STATUS.md) y [docs/architecture.md](docs/architecture.md).
