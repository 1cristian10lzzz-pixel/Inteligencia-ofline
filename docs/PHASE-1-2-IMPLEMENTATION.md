# Implementación — fases 1 y 2 de GameForge AI

## Fase 1: arranque y estabilidad

El motor llama.cpp ya no se inicializa al cargar la clase Java. La biblioteca nativa se carga cuando el usuario pide cargar un GGUF, en el ejecutor de modelos. La Activity mantiene una pantalla de diagnóstico nativa si WebView falla al crearse o no puede cargar index.html, y trata de reconstruir el renderer una vez si este termina.

La prueba automatizada espera el proceso Android y la señal UI_READY de la interfaz. La primera ejecución de esta prueba no llegó a instalar la aplicación: el log reveló que el runner inició el emulador con -accel off, pero el emulador rechazó la emulación por software. La configuración del siguiente intento habilita ANDROID_I_WANT_MY_TCG=yes y permite 20 minutos de arranque. Por eso el fallo anterior no demuestra un cierre de GameForge AI.

## Fase 2: agente de archivos local y revisable

- El historial reciente del chat se guarda localmente en WebView y se envían hasta ocho mensajes anteriores por solicitud.
- Al seleccionar un proyecto, la IA recibe una lista y una muestra limitada de archivos relevantes, con límites de tamaño.
- El explorador permite enumerar y leer archivos de texto dentro del directorio privado del proyecto.
- La IA puede devolver un bloque estructurado de edición para un solo archivo. El bloque solo se convierte en una vista previa.
- El usuario ve el contenido actual y el propuesto y debe pulsar «Aplicar cambio».
- Antes de escribir, la aplicación vuelve a comprobar que el archivo no haya cambiado desde la vista previa.
- Se restringen las rutas a la carpeta del proyecto, se bloquea '..', se usa una lista blanca de extensiones y se limita cada archivo a 128 KB.
- Las copias se guardan fuera del proyecto y se conserva un historial limitado. «Deshacer último cambio» restaura el archivo previo o elimina el archivo nuevo.

## Lo que todavía no está terminado

La fase 2 no es todavía un agente de reparación autónomo de varios pasos. No ejecuta Godot ni comprueba el proyecto con el compilador GDScript; tampoco genera modelos 3D o texturas ni modifica varios archivos automáticamente. Esas capacidades necesitan validación adicional y la fase 3.

La app no descarga modelos. Para utilizar edición con IA se debe importar y cargar un GGUF compatible. El chat local de reglas funciona sin modelo, pero no puede generar ediciones inteligentes.

## Icono de aplicación

Se añade un icono adaptativo para Android con un cubo voxel verde sobre un yunque tecnológico azul, con detalles de energía cian y violeta. El icono usa recursos vectoriales para mostrarse nítido en diferentes densidades y máscaras de launcher.
