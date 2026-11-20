# Estado de GameForge AI 0.4.0-alpha — fases 1 y 2

## Fase 1 — Arranque y estabilidad

- [x] Inicialización diferida de llama.cpp: no carga JNI al abrir la pantalla principal.
- [x] Recuperación controlada de cierres del renderer WebView.
- [x] Pantalla nativa de diagnóstico si la página local no carga.
- [x] Señal Logcat UI_READY.
- [x] Validación estática de arranque.
- [x] Prueba de instalación, proceso activo y UI_READY en Android 12.
- [x] Corrección del emulador de CI: permite TCG por software cuando el runner desactiva la aceleración de CPU.
- [ ] Verificar la apertura en un teléfono Android físico del usuario.

## Fase 2 — IA programadora offline

- [x] Separar los ejecutores de importación/exportación, carga de modelos e inferencia.
- [x] Conservar el historial reciente del chat en el almacenamiento local de WebView.
- [x] Enviar historial limitado y contexto acotado del proyecto seleccionado al modelo.
- [x] Añadir listado y lectura de archivos de texto del proyecto.
- [x] Restringir extensiones, tamaño máximo y rutas canónicas para bloquear traversal.
- [x] Implementar propuestas estructuradas de edición de un archivo por respuesta.
- [x] Mostrar antes/después y pedir aprobación explícita.
- [x] Comprobar que el archivo no haya cambiado desde la vista previa antes de aplicar.
- [x] Crear copias de seguridad fuera de la carpeta del proyecto.
- [x] Añadir deshacer del último cambio, incluso si el archivo era nuevo.
- [x] Añadir comprobaciones estáticas de integración y seguridad para fase 2.
- [ ] Probar generación con un GGUF real en el teléfono y validar archivos Godot reales en uso.

## Límites vigentes

1. El modelo GGUF se importa manualmente y no se incluye en el APK.
2. La inferencia sigue usando CPU; GPU/Vulkan/NPU no están habilitadas.
3. El agente propone un archivo completo por operación, con aprobación manual; todavía no es un agente autónomo de varios pasos.
4. No se ejecuta Godot dentro de GameForge AI ni se exportan APK de videojuegos (fase 3).
5. Esta versión sigue siendo debug, no una versión firmada para distribución comercial.

Consulta docs/PHASE-1-2-IMPLEMENTATION.md y docs/ROADMAP-3-PHASES.md.
