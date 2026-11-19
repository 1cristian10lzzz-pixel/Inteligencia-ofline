# GameForge AI — Android offline game studio

**Versión de desarrollo:** 0.2.0-alpha. **Estado:** fuente preparada para compilación CI; todavía requiere la primera compilación y prueba de instalación del APK.

GameForge AI es una aplicación Android nativa sin permiso de Internet. Incluye una interfaz local, creación de proyectos Godot 2D/3D a partir de plantillas, almacenamiento privado de proyectos, exportación ZIP, importación de modelos GGUF y un runtime nativo `llama.cpp` preparado para compilar dentro del APK.

## Funciones de esta revisión

- Interfaz Android local servida desde assets; no solicita `android.permission.INTERNET`.
- Creación de proyectos Godot 4.7.x 2D y 3D a partir de plantillas incluidas.
- Proyectos almacenados en el espacio privado de la aplicación y exportables como ZIP.
- Importación de modelos GGUF elegidos por el usuario.
- Runtime `llama.cpp` incorporado con CMake/NDK para `arm64-v8a` y `x86_64`.
- Carga de modelo y generación de texto en hilo de trabajo, sin bloquear la interfaz.
- CMake descarga una revisión fijada de `ggml-org/llama.cpp` durante la compilación; una vez construido, el runtime de la app funciona localmente.
- La app no descarga modelos ni usa servicios de IA en la nube. El usuario aporta el GGUF y debe comprobar su licencia y compatibilidad.

## Límites que deben probarse

- El APK aún no está verificado hasta que la ejecución de GitHub Actions termine y se compruebe el artefacto.
- La inferencia está integrada a nivel C++/JNI, pero necesita probarse con un modelo GGUF compatible en el teléfono objetivo.
- La creación de proyectos usa plantillas. El asistente neuronal puede ayudar con código e instrucciones, pero todavía no aplica cambios arbitrarios generados por el modelo directamente sobre archivos del proyecto.
- La propia app no incluye el editor de Godot ni el exportador de los juegos; exporta los proyectos fuente para abrirlos en Godot/Godot Android Editor.
- El runtime usa CPU portátil como configuración base. La aceleración GPU/NPU requiere una fase posterior específica por chipset.

## Compilación

Requisitos: JDK 17, Android SDK Platform 37, Build Tools 36.0.0, NDK 29.0.14206865, CMake 3.31.6, Gradle 9.9.1 y conexión durante la compilación para obtener llama.cpp en la revisión fijada.

```bash
python3 scripts/validate_source.py
bash scripts/check-js.sh
gradle --no-daemon -p android assembleDebug
```

APK de depuración: `android/app/build/outputs/apk/debug/app-debug.apk`. Para reproducir builds se recomienda ejecutar `scripts/prepare-gradle-wrapper.sh` desde un entorno que ya tenga Gradle instalado.

## Obtener un modelo

Importa manualmente un GGUF instruct compatible desde la sección **IA local y dispositivo**. Empieza con un modelo pequeño cuantizado (aprox. 0.5B–1.5B); el tamaño adecuado depende de RAM disponible, contexto y cuantización. La app no contiene pesos de modelos.

## GitHub Actions

Cada push a `main` ejecuta validación estática, compila la APK con `llama.cpp`, comprueba el paquete y sus bibliotecas nativas, instala y abre el APK en un emulador Android y publica `gameforge-ai-debug-apk` como artefacto. El flujo requiere que GitHub Actions esté habilitado en el repositorio.

## Licencias y seguridad

- GameForge AI: código de este repositorio.
- llama.cpp: proyecto externo bajo licencia MIT; se obtiene en una revisión exacta indicada en CMake.
- Modelos GGUF: no se redistribuyen; el usuario debe comprobar los términos de la ficha del modelo elegido.
- No se guardan tokens, contraseñas o claves privadas en el repositorio.
- La app no ejecuta código descargado desde la red ni solicita permiso de Internet.
