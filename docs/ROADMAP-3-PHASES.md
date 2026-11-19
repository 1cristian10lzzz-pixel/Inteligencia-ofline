# GameForge AI — plan integral en 3 fases

Este plan agrupa las mejoras de la aplicación Android offline. La fase 1 es la única incluida en esta entrega; las fases 2 y 3 quedan definidas para mantener el trabajo ordenado y evitar declarar funciones que todavía no existen.

## Fase 1 — Arranque, estabilidad y base fiable

**Objetivo:** que GameForge AI abra de forma consistente y que sus errores puedan diagnosticarse.

- [x] Cargar llama.cpp de forma diferida: abrir la pantalla principal ya no carga bibliotecas JNI de gran tamaño.
- [x] Inicializar el runtime bajo demanda desde la operación de carga de un GGUF y proteger la inicialización contra llamadas duplicadas.
- [x] Mantener una pantalla nativa de diagnóstico si WebView no puede iniciar o cargar index.html.
- [x] Recuperar una caída del proceso WebView e intentar una recreación controlada.
- [x] Registrar en Logcat la señal UI_READY cuando la interfaz local esté realmente cargada.
- [x] Hacer obligatoria en CI la prueba de instalación, proceso activo y UI_READY en Android 12 (API 31).
- [x] Guardar los registros de arranque de Android como artefacto de diagnóstico.
- [ ] Probar la nueva APK en el teléfono físico del usuario y verificar estabilidad, memoria y ABI real.

**Criterio de salida:** APK compilada, instalada y con interfaz visible en el emulador Android 12; después, confirmación en el teléfono real.

## Fase 2 — IA programadora autónoma y gestión avanzada

**Objetivo:** que la IA pueda trabajar sobre los proyectos existentes, no limitarse a contestar o copiar plantillas.

- Añadir memoria de conversación real y enviar el historial relevante al modelo.
- Incorporar respuesta progresiva por tokens, cancelación y control de tareas largas.
- Separar colas de importación, carga de modelos, generación y exportación para que una operación no bloquee a las demás.
- Crear herramientas locales restringidas para listar, leer y modificar archivos del proyecto.
- Mostrar diffs antes de aplicar cambios, crear copias de seguridad y ofrecer deshacer/restaurar.
- Implementar un agente por pasos: analizar petición, inspeccionar proyecto, planificar, editar, validar y reparar.
- Validar sintaxis GDScript y referencias de escenas antes de guardar cambios.
- Incorporar administrador de modelos GGUF: metadatos, compatibilidad, RAM estimada, reemplazar/eliminar modelos y mensajes útiles.
- Mejorar el rendimiento con límites adaptativos de contexto/hilos y evaluar aceleración GPU/Vulkan solo cuando sea compatible con el dispositivo.

**Criterio de salida:** describir una modificación y verla aplicada de forma segura a un proyecto real, con diff, validación y opción de deshacer, todo sin Internet.

## Fase 3 — Motor de videojuegos, creación completa y APK final

**Objetivo:** convertir un proyecto generado en un videojuego comprobable y exportable desde Android.

- Integrar una ruta real de ejecución de Godot compatible con Android; no confundir exportar fuentes ZIP con compilar un juego.
- Ampliar las bases 2D y 3D con escenas, controles configurables, cámara, inventario, interacción, enemigos, NPC, guardado, niveles y progresión.
- Añadir generación y organización de recursos: texturas, sprites, modelos, animaciones, audio y sus referencias dentro del proyecto.
- Construir herramientas para ejecutar pruebas, capturar errores y pedir a la IA que repare el proyecto.
- Añadir exportación Android del videojuego, gestión de plantillas de exportación, firma de release y verificación del APK producido.
- Optimizar consumo de RAM, almacenamiento, batería y rendimiento en móviles modestos.
- Rediseñar la interfaz para mostrar el estado de cada tarea, los archivos modificados, las pruebas y los APK generados.

**Criterio de salida:** crear o modificar un juego, ejecutarlo, reparar errores y producir un APK instalable del videojuego directamente desde GameForge AI, sin servicios de nube.

## Reglas de calidad

- No marcar una capacidad como terminada solo porque compila: debe tener una prueba que demuestre su comportamiento.
- Mantener el funcionamiento offline y no añadir permiso INTERNET sin una decisión explícita.
- No borrar proyectos ni modelos del usuario durante actualizaciones.
- Entregar una APK de la aplicación GameForge AI distinta del APK del videojuego que se exportará en la fase 3.
