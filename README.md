# GameForge AI — estudio offline especializado en Minefade

**Versión en desarrollo:** 0.5.1-alpha. Esta rama implementa las fases 1 y 2 y todavía necesita superar CI y pruebas en un teléfono Android real.

GameForge AI es una aplicación Android para trabajar con el código fuente de Minefade. Incluye un modelo GGUF de programación, una copia inicial del repositorio Minefade 2.0, memoria de conversaciones y un agente capaz de inspeccionar y proponer cambios coordinados en varios archivos. La inferencia es local y el APK no declara el permiso Android INTERNET.

## Qué incluye la rama de fases 1 y 2

- Modelo Qwen2.5-Coder-0.5B-Instruct GGUF Q3_K_M incluido dentro del APK.
- Instalación y carga automáticas del modelo al primer arranque. No requiere importar ni seleccionar un GGUF.
- Snapshot de Minefade 2.0 instalado como proyecto inicial en el almacenamiento privado de GameForge AI.
- Interfaz independiente por pantallas: Inicio, Proyectos, Chat, Archivos, Reparación y Configuración.
- Conversaciones separadas por proyecto e historial local.
- Índice de archivos C++, Rust, Java, Kotlin, JNI, Gradle, CMake, documentación y pruebas de Minefade.
- Búsqueda local y lectura de archivos del proyecto.
- Propuestas de hasta ocho archivos por tarea, vista previa antes/después, confirmación del usuario, copias de seguridad y deshacer transaccional.
- Validación local ligera de JSON/XML y delimitadores; la compilación y los tests reales siguen siendo necesarios para confirmar los cambios.
- Runtime llama.cpp compilado para arm64-v8a y x86_64.

## Primer arranque

1. Instala el APK.
2. En el primer arranque la aplicación extrae el snapshot de Minefade y copia el GGUF incluido al almacenamiento privado.
3. llama.cpp carga automáticamente el modelo. El estado pasa a «IA local lista» únicamente cuando el runtime confirma el modelo.
4. Abre el proyecto Minefade y comienza un chat. No necesitas conexión a Internet ni importar archivos para conversar con la IA una vez instalada la app.

La instalación del modelo requiere aproximadamente 355 MB para el GGUF Q3_K_M, además del espacio que ocupan los archivos privados y el propio APK. Si falta almacenamiento o RAM, la app debe mostrar un diagnóstico en vez de cerrarse.

## Límites que se mantienen explícitos

- El modelo 0.5B es pequeño para caber mejor en móviles. No tiene la capacidad de un modelo grande de ingeniería: puede equivocarse, omitir contexto o generar cambios defectuosos. La especialización también depende del contexto del repositorio y de las herramientas locales.
- La aplicación no compila aún el APK de Minefade desde el teléfono. Eso pertenece a la fase 3; en esta rama el agente edita y organiza el código, pero no debe anunciar que ejecutó una compilación que no se haya realizado.
- La inferencia inicial utiliza CPU; no se afirma aceleración GPU/NPU.
- El snapshot de Minefade queda local y editable. Actualizarlo desde GitHub debe ser una acción explícita y con comparación de cambios; no debe sobrescribir trabajos locales.
- El APK generado por CI es de depuración. No es todavía un paquete Release firmado con una clave permanente.
- La prueba del emulador no sustituye la instalación y evaluación del APK en el teléfono físico del usuario.

## Compilación y validación

El workflow prepara el modelo y el snapshot fijando sus revisiones, comprueba tamaños, manifiestos, SHA-256 y CRC del ZIP, compila la aplicación y realiza una prueba de arranque en Android 12. La prueba también espera la carga del GGUF y una inferencia de autodiagnóstico real.

El paquete GameForge AI y el APK del videojuego Minefade son entregables distintos. La automatización de la compilación del videojuego es la fase 3.

## Licencias

- llama.cpp / ggml: MIT.
- Qwen2.5-Coder-0.5B-Instruct: Apache-2.0 según la tarjeta del modelo.
- Se incluye el archivo de licencia del modelo junto al asset GGUF.
- La copia del código Minefade conserva los avisos y referencias de las dependencias que aparecen en el repositorio fuente.

Consulta BUILD_STATUS.md, docs/architecture.md y docs/PHASE-1-2-MINEFADE.md.
