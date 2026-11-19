# Estado de GameForge AI 0.2.0-alpha — 19 de noviembre de 2026

## Implementado en el código fuente

- [x] Android Gradle Plugin 9.4.0 y Gradle 9.9.1, con JDK 17.
- [x] Android SDK Platform 37, NDK 29 y CMake 3.31.6 fijados en CI.
- [x] Aplicación Android sin permiso `INTERNET`.
- [x] Runtime llama.cpp compilado como biblioteca JNI, con la revisión upstream fijada.
- [x] Carga de modelos GGUF desde el almacenamiento privado.
- [x] Tokenización, plantilla conversacional, decodificación y muestreo en el dispositivo.
- [x] Carga e inferencia fuera del hilo principal.
- [x] Plantillas de proyecto Godot 2D y 3D, y exportación ZIP de sus fuentes.
- [x] Interfaz que diferencia el motor nativo disponible de un modelo realmente cargado.
- [x] CI para validar, compilar el APK, revisar bibliotecas JNI e instalar en emulador.

## Verificaciones ya realizadas localmente

- [x] `scripts/validate_source.py`.
- [x] `scripts/check-js.sh`.
- [x] `bash -n scripts/*.sh`.
- [x] `python3 -m py_compile scripts/validate_source.py`.
- [ ] Compilación real `assembleDebug` en GitHub Actions.
- [ ] Prueba de arranque en emulador Android.
- [ ] Instalación y prueba en Android físico ARM64.
- [ ] Inferencia con un GGUF compatible y validación de uso de RAM.

## Límites de esta alfa

1. El modelo GGUF no se incluye en el APK; debe importarse por separado y sus términos de uso deben verificarse.
2. El runtime usa CPU como base portátil. GPU/NPU no están habilitadas en esta versión.
3. El agente no aplica todavía ediciones libres del modelo a los archivos Godot mediante herramientas seguras.
4. La aplicación exporta proyectos Godot fuente; todavía no incluye el editor Godot ni construye por sí sola el APK del videojuego.
5. El APK debug que produzca CI será instalable para pruebas, no una versión firmada de distribución.

No declarar terminado el APK hasta que CI genere el artefacto y se compruebe su instalación. No declarar terminada la inferencia hasta probar un modelo real en el dispositivo objetivo.
