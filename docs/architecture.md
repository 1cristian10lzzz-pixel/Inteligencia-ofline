# Arquitectura de GameForge AI

## Capas

1. **Interfaz offline:** WebView que carga únicamente `assets/index.html` y no usa recursos remotos.
2. **Aplicación Android:** `MainActivity` administra almacenamiento privado, proyectos Godot, importación de GGUF y exportación ZIP.
3. **Puente JNI:** `LocalLlmRuntime` carga la biblioteca `gameforge_llama` y expone carga/descarga del modelo, estado y generación.
4. **Runtime neuronal:** `llama.cpp` se compila con CMake/NDK para `arm64-v8a` y `x86_64`; los pesos GGUF se importan al dispositivo.
5. **Plantillas Godot:** plantillas 2D y 3D se copian al sandbox de la app; los proyectos se pueden exportar como ZIP.

## Flujo de inferencia local

1. El usuario selecciona un archivo `.gguf` mediante el selector de documentos de Android.
2. GameForge copia el archivo a su directorio privado `files/models`.
3. El usuario selecciona el modelo y lo carga en un ejecutor de trabajo.
4. JNI inicializa el backend llama.cpp, abre el GGUF y crea el contexto y sampler.
5. Las preguntas se convierten a la plantilla conversacional del modelo cuando está disponible, se tokenizan y se procesan localmente.
6. La respuesta se devuelve a la interfaz mediante una llamada asíncrona.

No se declara permiso `android.permission.INTERNET`. La compilación de la aplicación sí necesita obtener la fuente upstream de llama.cpp; una vez compilado, la inferencia no requiere red. Para funcionamiento desconectado desde el primer momento, el instalador debe traer la biblioteca nativa y el usuario debe haber importado el GGUF.

## Seguridad y aislamiento

- Los GGUF se importan a almacenamiento privado de la app.
- Los proyectos se guardan en `files/projects` y se exportan mediante el selector de documentos Android.
- No se ejecuta shell ni código recibido desde el chat.
- Las plantillas y los archivos de proyecto no se cargan desde sitios remotos.
- No se guardan credenciales, tokens ni claves de firma en el repositorio.

## Pendiente para el agente de creación completo

La siguiente fase debe incorporar herramientas de acceso restringido a archivos (`list/read/write` dentro del proyecto), diffs revisables, copias antes de modificar, validación de GDScript y exportación a APK de juegos mediante Godot/GABE. No se debe dar al modelo acceso general al sistema de archivos ni ejecutar código arbitrario sin sandbox.
