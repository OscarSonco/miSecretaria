# miSecretaria — Manual de uso

miSecretaria escucha las notificaciones de tu teléfono, detecta pagos de billeteras móviles
(ZAS, Yasta, Yape, altoke, Bille, Yolo Pago, etc.) y mensajes de apps que elijas (WhatsApp, SMS, etc.),
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
  tiene:
  - **📋 Copiar**: copia el texto de esa notificación al portapapeles (para pegarlo donde
    quieras).
  - **📌 Fijar / 📌 Quitar fijado**: fija hasta 2 notificaciones a la vez para que aparezcan
    siempre primero en la lista, sin importar el filtro. Si ya tienes 2 fijadas, avisa que
    quites una antes de fijar otra.
  - **📝 Nota**: agrega una nota personal a esa notificación (solo la ves tú — no se envía por
    Telegram ni se lee en voz alta).
  - **Eliminar**: borra esa notificación del historial (sin confirmación, como "Quitar" en
    Billeteras/Apps).
  - **🚫 Marcar como publicidad**: silencia mensajes promocionales repetidos de esa misma
    billetera.
  - Botón **"Seleccionar"** (arriba del historial): activa casillas para elegir varias
    notificaciones y borrarlas juntas con **"Eliminar seleccionadas"** (pide confirmación).
  - Botón **"Vaciar historial"**: borra TODO el historial de una vez (pide confirmación —
    no se puede deshacer).
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
cada sucursal, y que tú le mandes avisos a una o todas las sucursales desde tu chat. Los
comandos (`/notificar`, `/renombrar`, `/help`) se procesan casi al instante mientras el
teléfono tenga el servicio de notificaciones activo — no hace falta esperar ni tocar
"Sincronizar ahora" para eso (ese botón sigue sirviendo para el CSV y como respaldo).

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
  (sobre todo Tecno/Infinix/Xiaomi y similares) matan las tareas en segundo plano por defecto.
- Si mandaste el comando bien pero de todos modos nada pasa, revisa que lo hayas escrito TODO
  en un solo mensaje (ver advertencia arriba en "Comandos") — es el error más común.

**Nota:** si además del comando el bot te contesta con un mensaje de error explicando la
sintaxis, quiere decir que sí está funcionando — solo el formato del comando estaba mal.

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

⚠️ **Muy importante: escribe el comando Y el mensaje juntos, en UN SOLO envío** — no manden
"/notificar TODOS" y luego, en otro mensaje aparte, el texto. Si lo mandas en dos mensajes
separados, el bot no reconoce ninguno de los dos y no hace nada (no te avisa del error salvo
que ya tengas v2.15+, que sí te responde con la sintaxis correcta si te equivocas).

- `/help` (o `/start`) — te devuelve la lista completa de comandos.
- `/notificar TODOS <mensaje>` — **solo audio**: lee el mensaje en voz alta en todas las
  sucursales, sin nada en pantalla. Ejemplo: `/notificar TODOS Cerramos a las 8pm hoy`
- `/notificarpantalla TODOS <mensaje>` — **audio + pantalla**: además de leerlo, lo muestra en
  pantalla completa (si tienes esa opción activada) o como aviso flotante — igual que un pago
  recibido, pero sin decir "Pago recibido" ni inventar un monto. Ejemplo:
  `/notificarpantalla TODOS Vino el proveedor, revisen`
- Con cualquiera de los dos, cambia `TODOS` por el nombre o código de una sucursal para
  avisarle solo a esa. Ejemplo: `/notificar MS-7K2F9Q Reunión a las 3pm`
- `/renombrar <código_actual> <nombre_nuevo>` — si una sucursal se quedó con el código
  alfanumérico automático (ej. `MS-7K2F9Q`) y quieres darle un nombre más claro, así se lo
  cambias sin tocar el teléfono. Ejemplo: `/renombrar MS-7K2F9Q Sucursal Centro`

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
