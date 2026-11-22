# GameForge AI — Fases 1 y 2 orientadas a Minefade

## Alcance

Estas fases convierten GameForge AI en una aplicación Android offline con modelo incluido y un agente de código especializado en el repositorio Minefade 2.0. No intentan compilar el APK del videojuego desde el teléfono: esa integración pertenece a la fase 3. La compilación del propio APK de GameForge AI se realiza en GitHub Actions.

## Fase 1 — Aplicación preparada e IA local incluida

- La interfaz de inicio utiliza el estudio local studio.html, con pantallas independientes: Inicio, Proyectos, Chat, Archivos, Reparación y Ajustes.
- El historial de conversaciones se mantiene localmente y separado por proyecto.
- El paquete CI incorpora un snapshot del repositorio 1cristian10lzzz-pixel/Minefade-2.0 fijado a un commit.
- El snapshot se instala automáticamente en el primer arranque en files/projects/minefade. Una instalación incompleta no sustituye ni borra una carpeta existente.
- El paquete incorpora un GGUF Qwen2.5-Coder-0.5B-Instruct Q3_K_M. Se copia automáticamente al almacenamiento privado y se carga por llama.cpp, sin selector de archivos.
- No se añade permiso INTERNET a la aplicación. El APK final incluye el motor C++ y el GGUF; una vez instalado, la inferencia no necesita conexión.
- La app mantiene un estado explícito de carga/errores; no enseña “IA lista” antes de que llama.cpp confirme el modelo.
- La prueba de CI comprueba arranque, pantalla, carga del GGUF e inferencia de una frase de autodiagnóstico en emulador Android 12.

### Límite honesto del modelo inicial

El modelo base es de 0.5B parámetros y ronda 400 MB como archivo Q3_K_M. Está incluido para que el usuario no tenga que importar nada, pero no equivale a un modelo grande de ingeniería. La especialización en Minefade procede también del índice del código, documentación y herramientas locales. Los cambios complejos deben pasar revisión y validación. Un teléfono con poca RAM o almacenamiento puede rechazar la carga; el error debe presentarse sin cerrar la aplicación.

## Fase 2 — Agente programador de Minefade

- El proyecto Minefade aparece de fábrica en Proyectos.
- El workspace distingue el proyecto nativo Minefade de las plantillas Godot que siguen disponibles para proyectos secundarios.
- El índice enumera archivos de código y documentación permitidos, excluyendo carpetas build, dependencias descargadas y binarios.
- El agente recibe el mapa de arquitectura y fragmentos de archivos relacionados con la tarea del usuario.
- El explorador permite abrir un archivo y buscar texto en los archivos locales.
- La IA puede proponer una transacción de hasta ocho archivos mediante un bloque estructurado.
- La vista previa muestra el contenido anterior y el nuevo, indica archivos creados y advierte sobre validaciones ligeras.
- Antes de aplicar, se comprueban de nuevo las rutas, la existencia y el SHA-256 de todos los archivos.
- Las modificaciones se escriben de forma atómica, con copias fuera del proyecto y rollback de la transacción si falla una escritura.
- El deshacer restaura la última tarea completa, pero se niega a sobrescribir archivos que hayan cambiado después.
- Las rutas están limitadas a la carpeta Minefade y a extensiones de texto permitidas. No hay acceso general al almacenamiento, shell ni ejecución de código proporcionado por el modelo.
- Los archivos de firma y secretos (local.properties, .jks, .keystore, .env, credenciales) están protegidos.
- La validación local cubre formato JSON/XML y una comprobación ligera de delimitadores en código. No se presenta como compilador C++/Rust/Android: esas pruebas siguen siendo las autoridades para confirmar que el motor compila.

## Cómo se mantiene el proyecto de Minefade

El snapshot se fija a MINEFADE_COMMIT en scripts/prepare-bundled-assets.sh. Los usuarios pueden trabajar sobre la copia privada instalada incluso sin Internet. Una sincronización a una revisión nueva debe ser una operación explícita que compare cambios y preserve archivos editados; nunca se debe sobrescribir silenciosamente el proyecto activo.

## Fase 3 — Fuera del alcance de esta entrega

- Ejecutar Gradle/CMake/NDK/Rust desde el teléfono.
- Compilar el APK de Minefade directamente dentro de GameForge AI.
- Firmar el APK de Minefade con una clave persistente.
- Instalar y verificar el APK de Minefade desde la propia aplicación.
- Validar visualmente Minefade en el dispositivo físico del usuario.

Hasta que la fase 3 exista, la pantalla debe explicar que el agente puede editar y preparar cambios, pero no anunciar que ya compila el videojuego desde el móvil.
