# GameForge AI

Estudio Android de desarrollo de videojuegos con inferencia local mediante llama.cpp.

## Estado
Versión 0.2.0-alpha. El proyecto incluye:
- Aplicación Android nativa sin permiso INTERNET.
- Runtime JNI basado en llama.cpp compilado dentro del APK.
- Importación manual de modelos GGUF.
- Generación de respuestas en el dispositivo cuando se carga un modelo compatible.
- Plantillas locales de Godot para supervivencia 3D y plataformas 2D.
- Exportación de los proyectos Godot como ZIP.
- GitHub Actions para validar y construir un APK debug.

## Limitaciones conocidas
- El modelo GGUF no está incluido en el APK: se importa por separado.
- La app aún no integra el editor Godot ni compila directamente los proyectos de juego a APK.
- La generación de scripts/escenas arbitrarios por el agente y su aplicación automática al proyecto están pendientes.
- El runtime actual usa CPU; la aceleración GPU/NPU no está habilitada.
- Es una versión alpha; requiere CI y pruebas en dispositivos ARM64 reales.

Consulta [BUILD_STATUS.md](BUILD_STATUS.md), [la arquitectura](docs/architecture.md) y [cómo obtener el APK](docs/como-obtener-el-primer-apk.md).
