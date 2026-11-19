# Estado de GameForge AI 0.3.0-alpha — fase 1 de estabilidad

## Fase 1 — Implementado en el código

- [x] Inicialización diferida de la biblioteca llama.cpp: no se carga al abrir la pantalla.
- [x] Carga nativa sincronizada y bajo demanda al solicitar un modelo GGUF.
- [x] Pantalla nativa de diagnóstico si falla el inicio de WebView o el documento local.
- [x] Recuperación controlada de una caída del renderer de WebView.
- [x] Señal Logcat UI_READY al cargar el panel principal de la interfaz.
- [x] Comprobación estática de seguridad del arranque.
- [x] GitHub Actions comprueba proceso activo y carga de la interfaz en emulador Android 12 (API 31).
- [x] GitHub Actions conserva logcat de arranque como artefacto de diagnóstico.
- [ ] Instalación y prueba en el teléfono Android físico del usuario.
- [ ] Prueba de inferencia con un GGUF real en ese teléfono y medición de RAM/temperatura.

## Verificaciones heredadas de la versión anterior

- [x] Android Gradle Plugin 9.4.0 y Gradle 9.9.1, con JDK 17.
- [x] Android SDK Platform 36, Build Tools 36.0.0, NDK 29 y CMake 3.31.6.
- [x] Aplicación Android sin permiso INTERNET.
- [x] Runtime llama.cpp y bibliotecas JNI para ARM64 y x86_64.
- [x] Carga de GGUF, creación de proyectos Godot 2D/3D y exportación de sus fuentes.
- [x] Pruebas estáticas y compilación de APK en GitHub Actions.

## Límites que siguen vigentes

1. El modelo GGUF no se incluye dentro del APK; el usuario debe importarlo.
2. La inferencia sigue en CPU; GPU/Vulkan/NPU no se han activado.
3. La IA todavía no puede aplicar de forma autónoma cambios generales al código de un proyecto.
4. La aplicación todavía no integra el editor/runtime exportador de Godot y no genera APK de videojuegos.
5. El APK de esta fase sigue siendo debug; la firma release se aborda en la fase 3.

## Próximas fases

- **Fase 2:** agente de programación local, memoria de chat, herramientas seguras para archivos, diffs, restauración, validación GDScript y administrador de modelos.
- **Fase 3:** integración real del motor Godot, recursos y mecánicas completas, pruebas y exportación de APK Android de los videojuegos.

Consulta docs/ROADMAP-3-PHASES.md para objetivos y criterios de salida de las tres fases.
