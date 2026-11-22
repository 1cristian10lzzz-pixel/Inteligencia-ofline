# Estado de GameForge AI 0.5.0-alpha — fases 1 y 2 Minefade

Esta es la rama de implementación. Las casillas de código indican que la capacidad está integrada en la fuente; las casillas de validación no se marcan hasta que CI y/o un dispositivo real lo demuestren.

## Fase 1 — App preparada y modelo incluido

- [x] Nueva interfaz de estudio con pantallas Inicio, Proyectos, Chat, Archivos, Reparación y Configuración.
- [x] Historial local de conversaciones separado por proyecto.
- [x] Snapshot Minefade fijado a commit y empaquetado durante CI.
- [x] Instalación segura del snapshot en almacenamiento privado en primer arranque.
- [x] GGUF Qwen2.5-Coder-0.5B-Instruct Q3_K_M preparado para incluirse en el APK.
- [x] Copia automática del GGUF al almacenamiento privado; no se necesita selector ni importación manual.
- [x] Carga automática llama.cpp en un ejecutor de trabajo, con contexto e hilos configurados por memoria.
- [x] Diagnóstico explícito para errores de extracción, espacio, memoria o carga del modelo.
- [x] INTERNET sigue ausente del AndroidManifest.
- [ ] Confirmar descarga del modelo y snapshot en el runner CI.
- [ ] Compilar el APK con los dos assets incluidos.
- [ ] Probar dos arranques en frío, carga GGUF e inferencia real en Android 12 emulator.
- [ ] Probar instalación, memoria, velocidad y calentamiento en teléfono físico.

## Fase 2 — Agente programador especializado en Minefade

- [x] Proyecto Minefade 2.0 como proyecto predeterminado de la aplicación.
- [x] Workspace nativo dedicado; no confunde Minefade con plantillas Godot.
- [x] Mapa de arquitectura y contexto dirigido por tema.
- [x] Listado, lectura y búsqueda de fuentes de Minefade.
- [x] Limitar rutas canónicas y tipos de archivo editables.
- [x] Proteger credenciales, keystores, manifiestos de proyecto y workflows.
- [x] Propuestas multiarchivo de hasta ocho archivos en una sola transacción.
- [x] Comparación antes/después y aprobación explícita.
- [x] Comprobación de hashes antes de aplicar para evitar sobrescribir ediciones concurrentes.
- [x] Escrituras atómicas, copias de seguridad fuera del proyecto y rollback si falla una operación.
- [x] Deshacer la última tarea completa sin sobrescribir archivos que cambiaron después.
- [x] Comprobación estructural ligera de JSON/XML y delimitadores.
- [ ] Confirmar CI de integración, compilación y prueba de arranque.
- [ ] Probar con tareas reales de reparación de C++, Rust y Android y revisar la calidad de las propuestas.

## Límites de esta entrega

1. El modelo 0.5B Q3_K_M incluido (aprox. 355 MB) es una versión ligera para móviles; no sustituye un modelo grande de ingeniería. Debe evaluarse en Minefade antes de confiarle tareas complejas.
2. El agente puede inspeccionar y modificar una tarea multiarchivo con aprobación, pero no ejecuta código arbitrario ni tiene acceso general al teléfono.
3. Los checks locales son estructurales; los compiladores, pruebas C++/Rust y build Android siguen siendo autoridad.
4. No se compila el APK del videojuego Minefade desde GameForge AI (fase 3).
5. El APK GameForge AI de CI es debug; la firma Release persistente se define por separado.
