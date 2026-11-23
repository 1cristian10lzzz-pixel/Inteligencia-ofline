# Estado de GameForge AI 0.6.0-alpha

Esta versión reconstruye el estudio y fortalece la ejecución offline y el agente Minefade. Las casillas describen código integrado; las pruebas de CI y dispositivo se marcan por separado.

## Fase 1 — Aplicación e IA local

- [x] Modelo Qwen2.5-Coder-0.5B-Instruct Q3_K_M incluido en el APK.
- [x] Instalación automática del modelo y del snapshot de Minefade.
- [x] Verificación SHA-256 del modelo instalado contra su manifiesto.
- [x] Carga de llama.cpp en segundo plano y configuración automática de contexto/hilos según RAM disponible.
- [x] Diagnósticos de RAM, almacenamiento, runtime, proyecto y estado de carga.
- [x] Cancelación de una generación en curso a través de JNI.
- [x] Interfaz reorganizada en Inicio, Proyectos, Conversaciones, Agente, Código, Tareas, Compilación y Configuración.
- [x] Historial local persistente, separado por proyecto.
- [x] Estado de fase 3 explícito: el centro de compilación no simula una construcción del videojuego.
- [x] Sin permiso INTERNET en el APK.
- [ ] Repetir CI de 0.6.0: descarga, hashes, compilación, contenido del APK y Android 12.
- [ ] Validar memoria, velocidad, temperatura y consumo en teléfono físico.

## Fase 2 — Agente de ingeniería Minefade

- [x] Proyecto Minefade integrado y contexto dirigido por subsistema.
- [x] Explorador de fuentes, búsqueda local y lectura de archivos.
- [x] Instrucciones del runtime nativo neutrales; la especialización Minefade proviene del prompt del agente, no de un sesgo hacia Godot.
- [x] Propuestas multiarchivo de hasta 12 archivos.
- [x] Parches exactos find/replace para archivos existentes, con rechazo de coincidencias ambiguas y límite de parches por archivo.
- [x] Archivos nuevos permitidos solo en ubicaciones y tipos aprobados.
- [x] Vista previa antes/después, validación estructural y aprobación explícita.
- [x] Comprobación de SHA-256 para detectar cambios concurrentes.
- [x] Escrituras atómicas, copias externas al proyecto y rollback si una escritura falla.
- [x] Deshacer con comprobación de todos los archivos y backups antes de restaurar.
- [x] Historial de tareas accesible desde la interfaz.
- [x] Cancelación de generación.
- [ ] Validar calidad con tareas reales de render, chunks, jugador, Rust y Android.
- [ ] Ampliar pruebas de fallos inducidos, cierre inesperado y recuperación del historial.
- [ ] Implementar streaming token a token; el chat actual informa progreso y permite detener, pero la respuesta se entrega al terminar la generación.

## Fase 3 — Pendiente

- [ ] Conectar una compilación remota autenticada a GitHub Actions sin incrustar credenciales.
- [ ] Ejecutar Gradle, CMake, NDK y Rust para Minefade.
- [ ] Repetir compilación después de corregir errores reales.
- [ ] Probar instalación, apertura del mundo, render y guardado en emulador.
- [ ] Gestionar una clave persistente de firma Release y verificar actualizaciones.
- [ ] Entregar APK Minefade, logs, SHA-256 y resultado de pruebas desde GameForge AI.

## Limitaciones conocidas

1. El modelo 0.5B Q3_K_M es ligero y limitado; no sustituye a un modelo grande de programación.
2. La edición se limita a fuentes/texto permitidos. No ejecuta código arbitrario ni da acceso general al teléfono.
3. Los checks estructurales no sustituyen los compiladores ni las pruebas del juego.
4. El APK generado por CI para esta etapa es debug, no Release firmado.
5. El emulador Android 12 no sustituye la prueba en el dispositivo real del usuario.
