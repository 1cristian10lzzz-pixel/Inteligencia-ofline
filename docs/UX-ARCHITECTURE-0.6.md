# Arquitectura UX — GameForge AI 0.6.0

## Principios

1. La interfaz representa un estudio de IA, no una página larga de ajustes.
2. El proyecto Minefade permanece visible como contexto activo.
3. El estado del runtime es explícito: cargando, listo, detenido o error.
4. Todas las áreas comparten jerarquía, espaciado, componentes, estados y navegación.
5. El usuario siempre conserva el control sobre los cambios: previsualizar, aprobar, descartar o deshacer.
6. Las capacidades aún no integradas se muestran como pendientes y nunca como botones que simulen una acción.

## Navegación de escritorio

Barra lateral:
- Inicio
- Proyectos
- Conversaciones
- Agente Minefade
- Código y archivos
- Tareas y cambios
- Compilación
- Configuración

La barra inferior de móvil tiene Inicio, Proyectos, Chats, Agente y Más. El botón Más abre Código, Tareas, Compilación y Configuración. El botón de menú lateral da acceso directo al resto de secciones.

## Páginas

- Inicio: estado del modelo/proyecto/archivos, resumen de tareas y accesos rápidos.
- Proyectos: Minefade principal y proyectos secundarios.
- Conversaciones: lista local, búsqueda, renombrado y nuevo chat.
- Agente: auditoría general y diagnósticos específicos del motor.
- Código: búsqueda de rutas/contenido, lectura y enviar un archivo al agente.
- Tareas: transacciones aplicadas, rutas afectadas y deshacer última tarea.
- Compilación: estado real de la fase 3 y pasos pendientes.
- Configuración: modelo Q3, runtime, contexto, RAM, almacenamiento y estado Android.

## Interacciones

- El chat mantiene historial por proyecto en almacenamiento local.
- La generación puede detenerse desde la interfaz.
- El progreso distingue preparar contexto de generación local.
- Los cambios llegan como propuestas multiarchivo.
- La vista previa compara antes/después; aprobar aplica una sola transacción.
- La pantalla de tareas recupera el historial real del workspace, no datos ficticios.
- La aplicación comprueba el estado del runtime de forma periódica y al terminar la instalación del modelo.

## Accesibilidad y móviles

- Diseño adaptable de una columna para móviles y barra lateral en pantallas amplias.
- Controles grandes y con etiquetas legibles.
- Los botones deshabilitados tienen estado claro.
- Los textos y errores se muestran dentro de la página y no dependen solo de un toast.
- El área de chat respeta el espacio del teclado y la navegación inferior.

## Límites

La generación de respuestas no se transmite token a token todavía. El chat muestra un indicador de progreso y permite cancelar; el texto se presenta cuando termina el ciclo nativo. La compilación del APK Minefade no forma parte de esta interfaz hasta integrar el servicio autenticado de la fase 3.
