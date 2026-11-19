# Estado de GameForge AI 0.2.0-alpha — 19 de noviembre de 2026

## Implementado y compilado

- [x] Android Gradle Plugin 9.4.0 y Gradle 9.9.1, con JDK 17.
- [x] Android SDK Platform 36, Build Tools 36.0.0, NDK 29 y CMake 3.31.6.
- [x] Aplicación Android sin permiso `INTERNET`.
- [x] Runtime llama.cpp compilado como biblioteca JNI.
- [x] Carga de modelos GGUF desde almacenamiento local.
- [x] Tokenización, plantilla conversacional, decodificación y muestreo en el dispositivo.
- [x] Inferencia y carga fuera del hilo principal.
- [x] Bibliotecas nativas de llama.cpp incluidas para ARM64 y x86_64.
- [x] Plantillas Godot 2D y 3D; exportación ZIP de los proyectos fuente.
- [x] GitHub Actions compila y verifica el APK.
- [x] Artefacto `gameforge-ai-debug-apk` generado y descargable (19-11-2026).

## Verificaciones

- [x] `scripts/validate_source.py`.
- [x] `scripts/check-js.sh`.
- [x] Compilación real `assembleDebug` en GitHub Actions.
- [x] Identificador de paquete verificado: `com.gameforgeai.studio`.
- [x] Verificadas las bibliotecas `libllama.so`, `libggml*.so` y `libgameforge_llama.so` para ARM64 y x86_64.
- [ ] Prueba de arranque en emulador Android (se ejecuta después de publicar el artefacto).
- [ ] Instalación y prueba en Android físico ARM64.
- [ ] Inferencia con un GGUF concreto y validación de RAM/rendimiento en el teléfono objetivo.

## Límites de esta alfa

1. El modelo GGUF no se incluye en el APK; se importa por separado. La IA solo responde después de que el usuario cargue un modelo compatible.
2. El runtime usa CPU como base portátil. GPU/NPU no están habilitadas en esta versión.
3. El agente todavía no puede aplicar ediciones arbitrarias generadas por el modelo a los archivos Godot mediante herramientas seguras.
4. La aplicación exporta proyectos Godot fuente; todavía no incorpora el editor Godot ni produce automáticamente el APK del videojuego.
5. El artefacto actual es un APK debug, útil para instalar y probar. No es una versión firmada para distribución comercial.

No declarar terminada la inferencia hasta probar un GGUF real en el dispositivo de destino, ni declarar integrado el flujo de creación completa de videojuegos hasta implementar la edición automática y exportación Android del juego.
