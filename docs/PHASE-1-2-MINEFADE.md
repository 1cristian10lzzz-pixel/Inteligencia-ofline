# Fases 1 y 2 — GameForge AI especializada en Minefade

## Objetivo y límites

GameForge AI es un estudio móvil que incluye el modelo de IA y una instantánea local del repositorio Minefade 2.0. Las fases 1 y 2 permiten conversar, inspeccionar fuentes, proponer parches y restaurar tareas sin conexión. La fase 3 —compilación autenticada del APK de Minefade— permanece separada y no se presenta como terminada.

## Fase 1 — Aplicación y runtime

- El APK incorpora Qwen2.5-Coder-0.5B-Instruct Q3_K_M, su manifiesto, SHA-256 y licencia.
- En el primer arranque, la app extrae el snapshot de Minefade y copia el GGUF al almacenamiento privado.
- Antes de reutilizar el modelo ya copiado, verifica longitud y SHA-256; un archivo dañado se reemplaza desde el asset incluido.
- llama.cpp se inicializa en un ejecutor de fondo. Contexto e hilos se ajustan a RAM disponible.
- El estado de IA solo se declara preparado tras confirmar el modelo en el runtime.
- La inferencia funciona localmente; el APK no solicita INTERNET.
- JNI permite detener una generación que se está ejecutando.
- El estado expone RAM, espacio libre, contexto, hilos, versión Android y número de fuentes.
- Las conversaciones se guardan por proyecto en almacenamiento WebView local.
- El rediseño organiza la aplicación en Inicio, Proyectos, Conversaciones, Agente, Código, Tareas, Compilación y Configuración.

### Capacidad del modelo

Qwen 0.5B Q3_K_M es una elección de compromiso para dispositivos móviles. Puede responder y ayudar a localizar problemas, pero puede cometer errores de razonamiento o generar propuestas incompletas. La especialización Minefade procede del contexto del repositorio, las instrucciones, el buscador y los límites transaccionales. Una propuesta nunca se aplica automáticamente.

## Fase 2 — Agente de ingeniería

### Contexto y análisis
- El workspace solo accede a `files/projects/minefade`.
- El índice excluye binarios, builds, dependencias y directorios no necesarios.
- El contexto dirige las consultas a arquitectura, render, mundo, jugador, entidades, mods o LAN según el tema.
- El prompt prohíbe inventar APIs, rutas o resultados de pruebas y protege los formatos MFVOX001/002/003.

### Cambios
- Una tarea puede proponer hasta 12 archivos.
- En archivos existentes se prefieren parches exactos `find/replace`; el texto buscado debe aparecer una sola vez salvo que se indique una ocurrencia concreta.
- Los archivos nuevos requieren carpeta y extensión permitidas.
- La vista previa enseña el antes y el después.
- El agente nunca ejecuta el contenido del modelo como shell o código arbitrario.

### Seguridad y restauración
- Las rutas se canonicalizan y se mantienen dentro de Minefade.
- Se protegen `local.properties`, `.env`, keystores, credenciales, manifiestos internos y workflows.
- Antes de aplicar se comparan hashes con los archivos originales.
- Cada archivo se escribe atómicamente y los backups se almacenan fuera del proyecto.
- Si una escritura falla, se restaura la transacción.
- Para deshacer, se validan primero todos los hashes y backups. Si algún archivo cambió después, se rechaza la operación para no perder trabajo.
- El historial registra resumen, fecha, rutas y archivos afectados.

### Validación
- JSON y XML: análisis estructural.
- C++, Rust, Java, Kotlin y shaders: comprobación ligera de delimitadores.
- La app explica que estas verificaciones no sustituyen CMake, Gradle, NDK, Cargo ni pruebas reales de Minefade.

## Interfaz

- Inicio: proyecto activo, estado de runtime y accesos rápidos.
- Proyectos: Minefade y proyectos secundarios.
- Conversaciones: historial independiente por proyecto, nuevo chat y detención.
- Agente: auditoría integral y diagnósticos por subsistema.
- Código: búsqueda y lectura de archivos.
- Tareas: historial, copias y deshacer.
- Compilación: estado transparente de la fase 3.
- Configuración: modelo, RAM, almacenamiento y estado del proyecto.

En móvil, una barra inferior ofrece Inicio, Proyectos, Chats, Agente y Más; el menú lateral permite acceder a todas las áreas.

## Pendiente — Fase 3

GameForge AI todavía no autentica un servicio de compilación. Para generar el APK del videojuego se necesita un flujo seguro de autorización de GitHub Actions, compilación de Gradle/CMake/NDK/Rust, diagnóstico de logs, pruebas del juego, firma Release persistente y descarga verificada. No se debe almacenar un token personal ni contraseña dentro del APK.

## Pruebas requeridas

La CI debe validar el snapshot, checksum GGUF, build del APK GameForge AI, presencia de assets, arranque en Android 12, dos arranques en frío y una inferencia de autodiagnóstico con contexto largo. Además, quedan pendientes pruebas manuales en el teléfono real y evaluación de calidad del modelo en tareas de ingeniería Minefade.
