# Runtime neuronal Android — llama.cpp integrado

Esta versión contiene una integración C++/JNI basada en la API pública de `llama.cpp`. La revisión se fija en `c406f4367e19bea7930e7f74e1984c9acc6d44bf` en `android/app/src/main/cpp/CMakeLists.txt`.

## Operaciones incluidas

- `llama_backend_init()` inicializa el backend.
- `llama_model_load_from_file()` abre el GGUF seleccionado desde el almacenamiento privado.
- `llama_init_from_model()` crea un contexto acotado a 2048 tokens por defecto.
- Tokenización, aplicación de plantilla conversacional GGUF, decodificación y muestreo con `llama.cpp`.
- El modelo y el contexto se liberan al reemplazar o descargar el modelo.
- JNI expone versión, carga, estado, generación y descarga.

## Compilación

Android Gradle Plugin 9.4.0, Gradle 9.9.1, JDK 17, NDK 29.0.14206865 y CMake 3.31.6. Se compila para `arm64-v8a` y `x86_64`. La configuración usa CPU portátil y desactiva OpenMP, llamafile, OpenSSL, servidor y ejemplos.

## Limitaciones conocidas

- La revisión inicial genera una respuesta por petición y no conserva todavía historial conversacional completo.
- No hay cancelación token-a-token en esta alfa.
- Para el primer arranque se recomienda un GGUF pequeño; modelos mayores pueden exceder memoria disponible.
- La compilación CMake descarga fuentes upstream al construir; el APK final no necesita esa conexión para inferir.

No mostrar `IA lista` hasta que `nativeIsModelLoaded()` confirme un modelo y contexto activos.
