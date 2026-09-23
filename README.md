# miSecretaria — Manual de uso

miSecretaria escucha las notificaciones de tu teléfono, detecta pagos de billeteras móviles
(YAPE, ZAS, YOLO, YASTA, AlToke, etc.) y mensajes de apps que elijas (WhatsApp, SMS, etc.),
los **lee en voz alta**, los guarda en un historial dentro de la app y — si lo configuras —
te avisa por Telegram y te deja mandar avisos remotos a una o varias sucursales.

## Instalación

1. Descarga el APK más reciente (desde el enlace que te compartan, o desde
   Configuración → "Buscar actualización" si ya tienes una versión instalada).
2. Ábrelo e instálalo (Android puede pedirte permitir "instalar apps de fuentes
   desconocidas" la primera vez — es normal, acéptalo).
3. Al abrir la app por primera vez, otorga los permisos que te pida (ver siguiente sección).

## Permisos que pide y para qué sirven

En Configuración vas a ver una fila por cada permiso, en **verde** si ya está concedido y en
**rojo** si falta:

| Permiso | Para qué sirve |
|---|---|
| Acceso a notificaciones | Es el permiso principal — sin él la app no puede leer ninguna notificación. |
| Mostrar sobre otras aplicaciones | Necesario para el aviso flotante ("Pantalla de aviso") cuando llega un pago. |
| Acceso a medios (fotos/audio/video de WhatsApp) | Permite buscar la foto/video/audio real cuando WhatsApp avisa que llegó uno nuevo (función en desarrollo, ver más abajo). |

Además, en Android puede que tengas que ir a Ajustes del sistema → Batería → "Sin
restricciones" para miSecretaria, para que el teléfono no la duerma y deje de escuchar
notificaciones cuando pasa mucho tiempo sin usarla.

## Uso básico

- **Encendido/Apagado**: en la pantalla principal, el botón grande activa o desactiva por
  completo la escucha de notificaciones (útil si quieres pausarla un rato sin desinstalar).
- **Historial**: lista de todo lo detectado, con filtro por billetera/app arriba. Cada tarjeta
  tiene un botón "🚫 Marcar como publicidad" para silenciar mensajes promocionales repetidos
  de esa misma billetera.
- **Leer**: pantalla para pegar o escribir cualquier texto y que la app lo lea en voz alta
  (con pausa/reanudar y elección de voz Varón/Mujer).

## Configuración

### Billeteras autorizadas / Aplicaciones (General)

Cada fila muestra el ícono real de la app, su nombre y su paquete (en rojo si falta —
significa que esa regla no va a detectar nada hasta que la corrijas).

- **Agregar Billetera / Agregar Aplicación**: abre un selector con todas las apps instaladas
  en tu teléfono — tócala y queda agregada con su paquete correcto automáticamente. Ya no hace
  falta escribir el nombre del paquete a mano.
- **Quitar**: borra esa regla por completo (por ejemplo, para eliminar una entrada vieja o
  duplicada).
- El interruptor activa/desactiva la regla sin borrarla.

Si una billetera nunca se detecta, lo más probable es que se agregó con el paquete vacío —
bórrala con "Quitar" y vuelve a agregarla desde el selector.

### Voz y avisos

- **Notificación hablada / Pantalla de aviso / Pantalla completa**: activa o desactiva cada
  forma de avisar cuando llega un pago.
- **Tipo de voz**: Varón o Mujer, más un control de velocidad de lectura.
- **Instalar más voces**: abre el instalador de voces de Google TTS si tu teléfono no trae una
  voz masculina o femenina real en español.

### Telegram (opcional)

Permite que la app te mande por Telegram un CSV periódico con las notificaciones nuevas de
cada sucursal, y que tú le mandes avisos a una o todas las sucursales desde tu chat.

**El Token y el Chat ID están protegidos con PIN** (ver "PIN de administrador" abajo) — solo tú
puedes verlos o cambiarlos; el personal de la sucursal ve el campo tapado ("•••• configurado").

1. Habla con **@BotFather** en Telegram, crea un bot nuevo y copia el **Token** que te da
   (una cadena larga con dos puntos en el medio, por ejemplo
   `123456789:AAExampleTokenAbCdEfGhIjKlMnOpQrStUvWx` — es un ejemplo, no un token real).
   Trátalo como una contraseña: cualquiera que lo tenga puede controlar tu bot.
2. Mándale cualquier mensaje a tu bot (por ejemplo `/start`) para que sepa que existes.
3. Para obtener tu **Chat ID**, abre en el navegador (reemplazando `<TOKEN>` por el token real):
   `https://api.telegram.org/bot<TOKEN>/getUpdates` y busca el número dentro de
   `"chat":{"id": ...}`.
4. En la app, toca 3 veces el logo de la pantalla principal, ingresa tu PIN, y ve a
   Configuración → Telegram: pega el **Token del bot** y el **Chat ID**, ponle un nombre a
   esta sucursal/dispositivo (o deja el código alfanumérico automático) y presiona
   **"Guardar y activar"**.
5. Usa **"Enviar mensaje de prueba"** para confirmar que quedó bien conectado.
6. Usa **"Sincronizar ahora"** para revisar comandos pendientes y mandar el CSV al instante,
   sin esperar el intervalo configurado — útil para probar que todo funciona.

**Si el bot no responde a ningún comando (ni `/help`):**
- Revisa que Token y Chat ID estén realmente guardados (toca "Sincronizar ahora" y espera unos
  segundos).
- Ve a Ajustes del sistema → Batería → miSecretaria → "Sin restricciones". Algunos teléfonos
  (sobre todo Tecno/Infinix/Xiaomi y similares) matan las tareas en segundo plano por defecto,
  y eso incluye la revisión periódica de Telegram.
- El intervalo mínimo entre revisiones automáticas es de 15 minutos — si no quieres esperar,
  usa "Sincronizar ahora".

**Varias sucursales, un solo bot — sin escribir el token en cada teléfono, dos formas:**

- **APK "Interna" (recomendado, para quien compila la app):** existe una build especial,
  `Releases/miSecretariaV(x.x)-debug_Interna.apk`, que ya viene con el Token y Chat ID
  puestos de fábrica — se instala y ya está lista, sin tocar Configuración. Se genera con
  `./build_interna.sh` (lee `secrets.properties`, un archivo local que nunca se sube a
  ningún lado). **Ese APK trae tu token en texto plano — pásalo a mano (USB, Bluetooth) a
  los teléfonos de tus sucursales, nunca por un canal público** (no es el mismo archivo que
  se publica en "Buscar actualización", ese siempre viene sin nada pre-rellenado).
- **Backup/Restauración (alternativa, sirve para cualquier instalación):** en tu teléfono
  maestro, Configuración → "Guardar Backup" — el archivo `.json` incluye el Token y Chat ID
  (además de billeteras/apps) — trátalo como una contraseña. En cada sucursal, "Restaurar
  Backup" con ese archivo. El nombre de sucursal NO se copia, cada teléfono conserva el suyo.

Todas las sucursales mandan su CSV al mismo chat, y puedes avisarles a todas o a una en
particular (ver comandos abajo).

**Comandos que puedes escribirle al bot desde tu Telegram:**
- `/help` (o `/start`) — te devuelve la lista completa de comandos.
- `/notificar TODOS <mensaje>` — el mensaje se anuncia (voz + notificación) en TODAS las
  sucursales conectadas.
- `/notificar <nombre_de_sucursal> <mensaje>` — solo se anuncia en esa sucursal.
- `/renombrar <código_actual> <nombre_nuevo>` — si una sucursal se quedó con el código
  alfanumérico automático (ej. `MS-7K2F9Q`) y quieres darle un nombre más claro, así se lo
  cambias sin tocar el teléfono.

### PIN de administrador

Toca **3 veces seguidas** el logo (junto a "miSecretaria Vx.x" en la pantalla principal) para
que aparezca el diálogo de PIN. El PIN por defecto es **230985**. Al ingresarlo correctamente,
el Token y Chat ID de Telegram quedan visibles y editables en Configuración durante esa
sesión (se vuelve a tapar si cierras y reabres la app). El resto de la Configuración
(billeteras, voz, avisos, etc.) siempre está disponible, con o sin PIN.

### Copia de seguridad

- **Guardar/Restaurar Backup**: exporta o importa en un archivo `.json` toda tu configuración
  (billeteras, apps, preferencias). El backup **no incluye el historial** de notificaciones —
  eso se exporta aparte, en texto o CSV, desde los botones correspondientes.

### Compartir

- **Compartir Historial / CSV / Log**: manda el contenido como archivo adjunto por cualquier
  app (WhatsApp, correo, Bluetooth, etc.), útil para revisar o respaldar fuera del teléfono.
- **Compartir Aplicación**: comparte el propio instalador (APK) de miSecretaria para que otra
  persona lo instale sin necesidad de internet.

## Actualizaciones

Desde Configuración, el botón **"Buscar actualización"** revisa si hay una versión nueva
publicada y, si la hay, la descarga e instala directamente — no necesitas buscarla a mano.

## Funciones en desarrollo (todavía no completas)

- **Medios nuevos de WhatsApp** (fotos/audio/video que te mandan): la app ya detecta cuándo
  WhatsApp anuncia un medio nuevo y busca el archivo real, pero por ahora solo lo registra en
  el log de depuración — todavía no lo reproduce, no guarda copia ni lo reenvía por Telegram.
- La voz "Varón" puede sonar parecida a "Mujer" en teléfonos sin una voz masculina real
  instalada para español (usa "Instalar más voces" en Configuración para revisar qué voces
  trae tu equipo).
