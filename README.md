# GameForge AI — Minefade Studio

**Versión en desarrollo:** 0.6.0-alpha. Las fases 1 y 2 están implementadas en esta rama; esta revisión debe completar CI antes de considerarse un APK validado.

GameForge AI es un estudio Android offline dedicado al desarrollo de Minefade. Incluye un modelo GGUF Qwen2.5-Coder-0.5B-Instruct Q3_K_M, un snapshot local del proyecto Minefade 2.0 y herramientas nativas para inspeccionar fuentes y aplicar cambios revisables.

## Experiencia del estudio

- Inicio con estado del modelo, proyecto, archivos indexados y tareas.
- Proyectos y conversaciones separadas por proyecto.
- Chat local con cancelación de generación, historial y propuestas de cambios.
- Agente Minefade con diagnósticos para render, chunks, jugador, entidades, mundo, LAN y rendimiento.
- Explorador de fuentes, búsqueda local y lectura de archivos.
- Historial de tareas, transacciones y restauración.
- Centro de compilación que distingue explícitamente las funciones aún pendientes de la fase 3.
- Configuración del modelo, memoria, espacio disponible y runtime.

## IA incluida y funcionamiento offline

El archivo GGUF viene dentro del APK y se copia automáticamente al almacenamiento privado durante el primer arranque. No necesitas importar ningún modelo para empezar a usar la IA. El motor llama.cpp se carga en segundo plano y el estado de la interfaz solo cambia a «IA local preparada» cuando el runtime confirma que el modelo está disponible.

Modelo incluido: **Qwen2.5-Coder-0.5B-Instruct Q3_K_M** (aprox. 355 MB). Es una variante pequeña para dispositivos móviles; no tiene la capacidad de un modelo grande de ingeniería. La especialización en Minefade también depende del contexto de fuentes, documentación y herramientas nativas. La IA puede equivocarse: revisa las propuestas antes de aplicarlas.

La inferencia no necesita Internet y el manifiesto Android no declara el permiso INTERNET. La descarga de modelos y del snapshot ocurre durante el proceso de construcción del APK en CI, no al abrir la aplicación.

## Trabajo con el código de Minefade

- El snapshot de Minefade 2.0 se incluye dentro del APK y se instala en `files/projects/minefade`.
- El workspace limita el acceso al directorio del proyecto y a una lista de extensiones de texto.
- Los archivos sensibles y la configuración de firma están protegidos.
- El agente puede proponer transacciones de hasta 12 archivos.
- Para modificar código existente se prefieren parches exactos `find/replace`, con rechazo de coincidencias ambiguas.
- Los cambios se muestran antes/después, se validan de forma estructural y requieren aprobación.
- Al aplicar, se comprueban los hashes actuales y se crean copias de seguridad.
- El historial permite deshacer la última transacción completa. Si los archivos cambiaron después, la restauración se rechaza para no perder trabajo.
- Se puede detener una generación sin cerrar la aplicación.

Las comprobaciones locales de JSON/XML y delimitadores son preventivas; no sustituyen los compiladores C++, Rust o Android ni las pruebas del videojuego.

## Compilación y fase 3

El APK de GameForge AI se construye en GitHub Actions para ARM64 y x86_64 e incluye el motor llama.cpp, el GGUF y el snapshot de Minefade.

GameForge AI **todavía no genera por sí misma el APK final del videojuego Minefade**. La fase 3 deberá incorporar una conexión de compilación autenticada, ejecutar Gradle/CMake/NDK/Rust, probar el juego, firmar Release con una clave persistente y devolver el APK y sus diagnósticos. No se incrustan contraseñas ni tokens en la aplicación.

## Validación

CI comprueba:
- Estructura y seguridad de las fuentes.
- Rutas, tipos de archivo y protecciones del agente.
- Parches, comparación, transacciones y restauración.
- Sintaxis JavaScript de las interfaces.
- Presencia y SHA-256 del modelo, CRC del snapshot y contenido del APK.
- Arranque Android 12, carga del GGUF y una inferencia de autodiagnóstico con contexto largo.

La prueba en emulador no sustituye una evaluación del consumo, temperatura y calidad del modelo en el teléfono físico del usuario.

## Licencias

- llama.cpp / ggml: MIT.
- Qwen2.5-Coder-0.5B-Instruct: Apache-2.0 según su tarjeta de modelo; se incluye el archivo de licencia.
- Se conservan los avisos de las dependencias presentes en la instantánea de Minefade.

Consulta `BUILD_STATUS.md`, `docs/PHASE-1-2-MINEFADE.md` y `docs/UX-ARCHITECTURE-0.6.md`.
