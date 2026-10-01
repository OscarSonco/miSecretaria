# miSecretaria — Manual de uso

miSecretaria escucha las notificaciones de tu teléfono, detecta pagos de billeteras móviles
(ZAS, Yasta, Yape, altoke, Bille, Yolo Pago, etc.) y mensajes de apps que elijas (WhatsApp, SMS, etc.),
los **lee en voz alta**, los guarda en un historial dentro de la app y — si el administrador lo
configuró — avisa por Telegram y permite mandar avisos remotos a una o varias sucursales.

Este manual tiene dos partes:

- **[📱 Guía para usuarios de sucursal](#-guía-para-usuarios-de-sucursal)** — instalar la app,
  permisos, uso diario (Historial, Leer, Configuración básica). Para el personal que usa el
  teléfono en el día a día.
- **[🛠️ Guía para el administrador](#️-guía-para-el-administrador)** — configurar el bot de
  Telegram, repartir la app a varias sucursales (incluida la build "Interna"), publicar
  actualizaciones, y las herramientas de respaldo. Para quien gestiona todo desde su
  computadora (dueño del negocio / del bot).

---

# 📱 Guía para usuarios de sucursal

## Instalación

Hay dos formas de recibir la app — cualquiera de las dos funciona igual para el uso diario:

1. **Un APK que te pasó el administrador** (por USB, Bluetooth, o un enlace) — puede ser el
   público o la build **"Interna"** (que ya viene con el bot de Telegram configurado de
   fábrica, sin que tengas que tocar nada). Ábrelo e instálalo.
2. **Desde Configuración → "Buscar actualización"**, si ya tenías una versión instalada — solo
   funciona para el APK público (ver [Actualizaciones](#actualizaciones) más abajo).

Android puede pedirte permitir "instalar apps de fuentes desconocidas" la primera vez — es
normal, acéptalo. Al abrir la app por primera vez, otorga los permisos que te pida (ver
siguiente sección).

## Permisos que pide y para qué sirven

En Configuración vas a ver una fila por cada permiso, en **verde** si ya está concedido y en
**rojo** si falta:

| Permiso | Para qué sirve |
|---|---|
| Acceso a notificaciones | Es el permiso principal — sin él la app no puede leer ninguna notificación. |
| Mostrar sobre otras aplicaciones | Necesario para el aviso flotante ("Pantalla de aviso") cuando llega un pago. |
| Acceso a todos los archivos (fotos/audio/video/documentos de WhatsApp) | Permite buscar la foto/video/audio/documento real cuando WhatsApp avisa que llegó uno nuevo (función en desarrollo, ver más abajo). Este permiso abre una pantalla especial de Android ("Acceso a todos los archivos") en vez del diálogo normal — es más fuerte que un permiso típico porque necesita leer las carpetas de otra app directamente. |

Además, en Android puede que tengas que ir a Ajustes del sistema → Batería → "Sin
restricciones" para miSecretaria, para que el teléfono no la duerma y deje de escuchar
notificaciones cuando pasa mucho tiempo sin usarla.

## Uso básico

- **Encendido/Apagado**: en la pantalla principal, el botón grande activa o desactiva por
  completo la escucha de notificaciones (útil si quieres pausarla un rato sin desinstalar).
- **Historial**: lista de todo lo detectado, con filtro por billetera/app arriba. Si un mensaje
  de WhatsApp traía una foto, audio, video o documento (PDF, Word, Excel, etc.) nuevo, la app
  busca el archivo real y lo muestra ahí mismo — la foto se ve completa, el audio tiene un
  botón para reproducirlo, el video un botón para abrirlo con tu reproductor de video, y el
  documento un botón "📄 Abrir documento" para abrirlo con la app correspondiente (lector de
  PDF, Word, etc.). Útil, por ejemplo, para que una sucursal siempre tenga una copia de una
  factura enviada por WhatsApp aunque el remitente la borre después. Las tarjetas con archivo
  adjunto se ven con **fondo verde**, para distinguirlas fácil del resto de mensajes de otras
  conversaciones. Requiere el permiso "Acceso a todos los archivos" (ver tabla de permisos
  arriba). **La lista solo muestra las últimas 100** (para que la app no se ponga lenta con
  miles de notificaciones acumuladas) — las más viejas se van cayendo de la vista a medida que
  llegan nuevas, pero eso NO afecta lo que se manda por Telegram (CSV y medios): ese envío usa
  su propia cola interna, sin ese límite, así que nada se pierde ahí aunque ya no lo veas en
  pantalla. Cada tarjeta tiene:
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
- **📎 Adjuntos**: botón junto a "Papelera" en la pantalla principal — junta TODOS los
  archivos adjuntos del Historial en una sola vista, agrupados por tipo (🎥 Videos, 🎤
  Audios, 📄 Documentos, 📷 Archivos en general), en vez de mezclados entre el resto de
  mensajes de otras conversaciones. Es solo una forma distinta de ver lo mismo que ya está en
  el Historial — no cambia qué se guarda ni qué se manda por Telegram.
- **Leer**: pantalla para pegar o escribir cualquier texto y que la app lo lea en voz alta
  (con pausa/reanudar y elección de voz Varón/Mujer).

El logo de la pantalla principal, si lo tocas 3 veces seguidas, pide un PIN — eso es solo para
el administrador (ver más abajo); si no lo conoces, simplemente cierra ese diálogo, no afecta
nada de lo que ya usas.

## Configuración que puedes ajustar tú

### Billeteras autorizadas / Aplicaciones (General)

Cada fila muestra el ícono real de la app, su nombre y su paquete (en rojo si falta —
significa que esa regla no va a detectar nada hasta que la corrijas).

- **Agregar Billetera / Agregar Aplicación**: abre un selector con todas las apps instaladas
  en tu teléfono — tócala y queda agregada con su paquete correcto automáticamente. Ya no hace
  falta escribir el nombre del paquete a mano.
- **Quitar**: borra esa regla por completo (por ejemplo, para eliminar una entrada vieja o
  duplicada).
- El interruptor activa/desactiva la regla sin borrarla.
- **🔊/🔇**: silencia la LECTURA EN VOZ ALTA de esa billetera/app en particular — el resto
  (Historial, notificación del sistema, adjuntos) sigue funcionando igual, solo deja de
  escucharse.
- **🏷️ Con nombre / Sin nombre**: si está en "Sin nombre", la lectura en voz alta ya no
  antepone el nombre de la billetera/app (ej. dice "Recibiste 50 Bolivianos" en vez de "ZAS,
  Recibiste 50 Bolivianos").
- **📞 Llamadas / Sin llamadas**: con "Sin llamadas", las notificaciones de LLAMADA (ej. una
  llamada de WhatsApp) no se anuncian en voz alta — los mensajes normales de esa misma app se
  siguen leyendo igual. **Desde v2.51, este botón solo aparece en WhatsApp, WhatsApp
  Business, Telegram, Messenger y "Teléfono"** — en el resto de Aplicaciones no se muestra,
  porque no tiene sentido (nunca van a mostrar una notificación de llamada).
- **📍 Solo distancia / Mensaje completo** — con "Solo distancia", se lee ÚNICAMENTE la
  distancia que trae el título de la notificación ("90 metros"), sin el nombre de la app ni
  el destino/dirección del cuerpo — útil para no escuchar "Maps, 90 metros, Matheus Pub,
  edificio Atahualpa..." a cada paso mientras vas navegando. **Desde v2.50, este botón solo
  aparece en apps de navegación** (Google Maps, "Localizador"/Find My Device, OsmAnd) — en
  el resto de Aplicaciones no se muestra, para no llenar la fila de botones que no aplican.

Si una billetera nunca se detecta, lo más probable es que se agregó con el paquete vacío —
bórrala con "Quitar" y vuelve a agregarla desde el selector.

### Voz y avisos

- **Notificación hablada / Pantalla de aviso / Pantalla completa**: activa o desactiva cada
  forma de avisar cuando llega un pago.
- **Tipo de voz**: Varón o Mujer, más un control de velocidad de lectura.
- **Instalar más voces**: abre el instalador de voces de Google TTS si tu teléfono no trae una
  voz masculina o femenina real en español.

### Compartir

- **Compartir Aplicación**: comparte el propio instalador (APK) de miSecretaria para que otra
  persona lo instale sin necesidad de internet — sirve para pasarla a otro teléfono de la
  misma sucursal, por ejemplo.

**Compartir Historial / CSV / Log** (desde v2.43) manda el contenido como archivo adjunto por
cualquier app (WhatsApp, correo, Bluetooth, etc.) — como sí contienen datos reales de
notificaciones/pagos, viven en el **panel de Admin** (ver la guía del administrador más abajo),
no aquí.

## Actualizaciones

Desde Configuración, el botón **"Buscar actualización"** revisa si hay una versión nueva
publicada y, si la hay, la descarga e instala directamente — no necesitas buscarla a mano.
**Solo funciona si tu app es la build pública** (la que se instaló desde un enlace de
"Buscar actualización" o del repositorio). Si tu teléfono tiene la build **"Interna"** (te la
entregó el administrador ya configurada), las actualizaciones te las pasa él directamente en
un APK nuevo — este botón no encontrará nada porque son builds distintas.

## Medios de WhatsApp — respaldo confirmado (2026-09-30)

Probado a fondo con 21 tipos de envío distintos (fotos, videos, notas de voz, archivos de
video `.mkv`/`.mp4`/`.avi`, archivos de audio `.flac`/`.wav`/`.mp3`, y documentos `.pdf`/
`.doc`/`.docx`/`.txt`/`.db`/`.sql`/`.log`): **18 de 21 se detectan, copian y muestran bien**
en el Historial (fondo **verde** en la tarjeta para distinguirlos a simple vista), sin
importar la extensión real del archivo. También se reenvían solos al bot de Telegram junto
con el CSV periódico (mismo intervalo, sin acción manual) — un archivo de más de 50 MB no
viaja al bot (límite de Telegram), pero igual queda respaldado en el teléfono.

⚠️ **"Ver una vez"** (foto/video/audio marcados con el ícono ① en WhatsApp): confirmado que
WhatsApp nunca guarda ese archivo en ninguna carpeta del teléfono a la que la app pueda
acceder, a propósito (es la misma función la que evita que se pueda "hacer trampa" y guardar
algo que se supone se ve una sola vez).
- **Foto y video "Ver una vez": excepción permanente, sin solución posible.** WhatsApp
  protege esa pantalla con `FLAG_SECURE` de Android — la misma protección que usan las apps
  bancarias contra capturas de pantalla — y eso bloquea CUALQUIER forma de capturarlo
  (captura de pantalla, grabación de pantalla, o cualquier otro método). No es un error de la
  app, es una limitación de Android/WhatsApp; si necesitas respaldar algo así, pídeselo a
  quien te lo mande como un archivo normal, no "Ver una vez".
- **Audio "Ver una vez": función experimental nueva (v2.40), para uso interno con
  empleados.** A diferencia de foto/video, el audio SÍ se puede capturar (la protección de
  arriba es solo visual). Toca 3 veces el logo de la pantalla principal, ingresa el PIN, y en
  el panel de Admin que se abre busca "🎙️ Audio 'Ver una vez'" — hay un botón para **armar
  la grabación**: pide permiso de micrófono y, después, el diálogo
  del sistema de "grabación de pantalla" (aunque solo se use para audio — es el mismo permiso
  que exige Android para este tipo de captura). Mientras está armada, si alguien reproduce un
  mensaje de voz "Ver una vez" de WhatsApp, la app intenta grabarlo y guardarlo junto a la
  notificación correspondiente en el Historial. **Limitaciones a tener en cuenta:** hay que
  volver a armarla a mano cada vez que cierras la app o reinicias el teléfono (Android no
  permite dejarlo activo para siempre); es la primera versión, sin calibrar con uso real
  todavía. Pensada para arqueos/balances de caja que los empleados mandan así por seguridad —
  si la usas, considera avisarles que el respaldo existe.

Falta: un checklist en Configuración de qué tipos guardar/reenviar/reproducir.

## Otras limitaciones conocidas

- La voz "Varón" puede sonar parecida a "Mujer" en teléfonos sin una voz masculina real
  instalada para español (usa "Instalar más voces" en Configuración para revisar qué voces
  trae tu equipo).

---

# 🛠️ Guía para el administrador

Todo lo de la guía de usuario aplica también a tu teléfono. Esta sección es lo adicional que
solo tú necesitas: configurar el bot de Telegram, repartir la app a varias sucursales, publicar
actualizaciones, y las herramientas que corren en tu computadora (no en los teléfonos).

## PIN de administrador

Toca **3 veces seguidas** el logo (junto a "miSecretaria Vx.x" en la pantalla principal) para
que aparezca el diálogo de PIN (con teclado numérico, desde v2.42). El PIN por defecto es
**230985**. Al ingresarlo correctamente se abre el **panel de Admin**, con TODO lo sensible en
un solo lugar (desde v2.43): nombre de sucursal/dispositivo, Token y Chat ID de Telegram,
intervalo, "Guardar y activar"/"Enviar mensaje de prueba"/"Sincronizar ahora", "Audio 'Ver una
vez'", y "Compartir Historial/CSV/Log". Nada de esto aparece en Configuración bajo ninguna
condición — el resto de la Configuración (billeteras, voz, avisos, "Compartir Aplicación",
etc.) siempre está disponible, con o sin PIN.

## Configurar el bot de Telegram

Permite que la app te mande por Telegram un CSV periódico con las notificaciones nuevas de
cada sucursal, y que tú le mandes avisos a una o todas las sucursales desde tu chat. Los
comandos `/notificar` y `/renombrar` se procesan casi al instante mientras el teléfono tenga
el servicio de notificaciones activo — no hace falta esperar ni tocar "Sincronizar ahora"
para eso (ese botón sigue sirviendo para el CSV y como respaldo). **`/help` es distinto desde
v2.47:** lo responde tu PC (`csv_importer.py`), no los teléfonos — ver más abajo.

1. Habla con **@BotFather** en Telegram, crea un bot nuevo y copia el **Token** que te da
   (una cadena larga con dos puntos en el medio, por ejemplo
   `123456789:AAExampleTokenAbCdEfGhIjKlMnOpQrStUvWx` — es un ejemplo, no un token real).
   Trátalo como una contraseña: cualquiera que lo tenga puede controlar tu bot.
2. Mándale cualquier mensaje a tu bot (por ejemplo `/start`) para que sepa que existes.
3. Para obtener tu **Chat ID**, abre en el navegador (reemplazando `<TOKEN>` por el token real):
   `https://api.telegram.org/bot<TOKEN>/getUpdates` y busca el número dentro de
   `"chat":{"id": ...}`.
4. En la app, toca 3 veces el logo de la pantalla principal, ingresa tu PIN. En el panel de
   Admin que se abre, pega el **Token del bot** y el **Chat ID**, ponle un nombre a esta
   sucursal/dispositivo (o deja el código alfanumérico automático), ajusta el intervalo si
   quieres, y presiona **"Guardar y activar"** — un solo botón guarda las cuatro cosas.
5. Usa **"Enviar mensaje de prueba"** para confirmar que quedó bien conectado.
6. Usa **"Sincronizar ahora"** para revisar comandos pendientes y mandar el CSV al instante,
   sin esperar el intervalo configurado — útil para probar que todo funciona.

**Si el bot no responde a ningún comando:**
- Si es `/help` específicamente: revisa que `csv_importer.py` esté corriendo en tu PC — desde
  v2.47 es el ÚNICO que responde ese comando (antes respondía cada teléfono por separado, lo
  que generaba una respuesta repetida por cada sucursal).
- Para los demás comandos: revisa que Token y Chat ID estén realmente guardados (toca
  "Sincronizar ahora" y espera unos segundos).
- Ve a Ajustes del sistema → Batería → miSecretaria → "Sin restricciones". Algunos teléfonos
  (sobre todo Tecno/Infinix/Xiaomi y similares) matan las tareas en segundo plano por defecto.
- Si mandaste el comando bien pero de todos modos nada pasa, revisa que lo hayas escrito TODO
  en un solo mensaje (ver advertencia abajo en "Comandos") — es el error más común.

**Nota:** si además del comando el bot te contesta con un mensaje de error explicando la
sintaxis, quiere decir que sí está funcionando — solo el formato del comando estaba mal.

### Comandos que puedes escribirle al bot desde tu Telegram

⚠️ **Muy importante: escribe el comando Y el mensaje juntos, en UN SOLO envío** — no manden
"/notificar TODOS" y luego, en otro mensaje aparte, el texto. Si lo mandas en dos mensajes
separados, el bot no reconoce ninguno de los dos y no hace nada (no te avisa del error salvo
que ya tengas v2.15+, que sí te responde con la sintaxis correcta si te equivocas).

- `/help` (o `/start`) — te devuelve la lista completa de comandos, con un teclado de botones
  persistente para los que quieras (algunos, como `/notificar`, necesitan que completes el
  mensaje a mano después de tocar el botón — Telegram no permite que un bot autocomplete el
  campo de texto). **Este comando lo procesa tu PC (`csv_importer.py`), no los teléfonos** —
  tiene que estar corriendo ahí para que responda (ver "Configurar el bot de Telegram"
  arriba). Los demás comandos de esta lista SÍ los procesan los teléfonos, normal.
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
  cambias sin tocar el teléfono. Ejemplo: `/renombrar MS-7K2F9Q Sucursal Centro`. El cambio
  queda GUARDADO en ese teléfono (no es algo que la PC solo "recuerde") — desde ese momento
  usa el nombre nuevo para todo, incluso después de cerrar la app o reiniciar el equipo. Si
  ya usaste el nombre viejo en algún lado (ej. en `destino` de
  `miSecretaria_BilleterasAplicacion.txt`, ver más abajo), acuérdate de actualizarlo ahí
  también — ese teléfono deja de responder a su nombre anterior.
- `/listado` — lista todas las sucursales conocidas (de tu base de datos local) y hace cuánto
  se vieron activas (🟢 si mandaron algo en las últimas 48h, ⚪ si no), y además un resumen de
  su Billeteras/Apps: si alguna está ❌ apagada o 🔇 sin voz te lo dice explícito (para que
  puedas detectar a tiempo si un empleado nuevo configuró algo mal, antes de perder
  facturas/reportes/balances); si todo está normal, dice "✅ todas activas y con voz" en vez
  de listarlas una por una. **Lo procesa tu PC**, igual que `/help` — ningún teléfono puede
  responder esto por su cuenta, porque cada uno solo conoce su propia configuración. Útil
  también para saber qué nombres exactos usar en `destino` (ver siguiente sección).
- `/panelon` / `/paneloff` — enciende/apaga el panel web (`miSecretaria.html`) para ver la base
  de datos local. Estos dos (y `/help`/`/listado`) **no los procesan los teléfonos** — los
  escucha tu computadora (`csv_importer.py` tiene que estar corriendo ahí). Ver "Ver el
  historial en un panel web" más abajo.

## Publicidad bloqueada centralizada (`miSecretaria_PublicidadBloqueada.txt`)

Cada teléfono puede marcar publicidad como bloqueada a mano (ver "🚫 Marcar como publicidad"
en la guía de usuario), pero si quieres que la MISMA lista aplique en todas tus sucursales
sin repetir el trabajo en cada teléfono:

1. Abre `miSecretaria_PublicidadBloqueada.txt` en la carpeta del proyecto (tiene el formato
   explicado en sus propios comentarios: una línea `Billetera|Frase` por entrada).
2. Agrega, edita o borra líneas y guarda el archivo.
3. Con `csv_importer.py` corriendo (ver más abajo), en menos de un minuto lo publica
   automáticamente en Firebase Hosting. Cada teléfono lo descarga solo en su próximo ciclo
   periódico (o tocando "Sincronizar ahora" en el panel de Admin) y reemplaza su lista local
   completa — si quitaste una frase del archivo, también se quita en los teléfonos, no solo
   se agregan las nuevas.

No hace falta reiniciar nada en los teléfonos ni en el script para que tome efecto — solo
guardar el archivo.

## Billeteras/Aplicaciones centralizadas (`miSecretaria_BilleterasAplicacion.txt`)

Para agregar una billetera/app nueva (ej. una app de un proveedor que quieras que todos tus
empleados instalen) o ajustar On/Off, voz, nombre, llamadas o modo — sin entrar a cada
teléfono uno por uno. **A diferencia de "Publicidad bloqueada", esto NUNCA borra ni pisa una
billetera/app que ya tengas configurada y que el archivo no mencione** — cada línea se
APLICA puntualmente (agregar, modificar o quitar UNA regla), nunca reemplaza todo.

1. Manda `/listado` a tu bot para ver los nombres exactos de tus sucursales.
2. Abre `miSecretaria_BilleterasAplicacion.txt` en la carpeta del proyecto — el formato
   completo (10 campos) está explicado en sus propios comentarios, con dos ejemplos reales.
3. Escribe una línea por cambio. La columna `destino` decide a quién le llega: `TODOS`, o uno
   o varios nombres de sucursal separados por coma (los mismos que te dio `/listado`) — así
   puedes darle una billetera/app solo a ciertos empleados (ej. un Jefe) sin tocar al resto.
4. Guarda el archivo — con `csv_importer.py` corriendo, en menos de un minuto se publica y
   cada teléfono de destino la aplica en su próximo ciclo (o con "Sincronizar ahora").

**Ejemplo:** para agregar la app de un proveedor a todos:
```
app|BEES Bolivia|TODOS|definir|com.abinbev.android.tapwiser.beesBolivia|on|on|on|on|normal
```

## ¿Puedo vaciar el chat del bot en Telegram?

**Recomendado: no.** Aunque los CSV ya están a salvo en `miSecretaria.db` una vez importados,
la multimedia (fotos/videos/audios/documentos) NO se guarda en ninguna base de datos — el
chat de Telegram es su ÚNICA copia fuera del teléfono. Si la vacías, el único respaldo que
queda es el del propio teléfono (justo el que se puede perder si un empleado borra la app,
resetea el equipo, o se le daña — el mismo riesgo que esta función existe para cubrir). Dejar
todo en Telegram, sin borrar nada, mantiene tu respaldo completo sin ocupar espacio en tu PC —
el chat de Telegram es el archivo histórico; tu base de datos local solo necesita lo
necesario para el día a día.

## Repartir la app a varias sucursales

Todas las sucursales comparten **un solo bot** (un solo Token) — no se crea un bot por
sucursal. Hay dos formas de que cada teléfono quede con el Token/Chat ID sin escribirlo a mano
en cada uno:

- **APK "Interna" (recomendado):** una build especial que ya viene con el Token y Chat ID
  puestos de fábrica — se instala y ya está lista, sin tocar Configuración. Se genera en tu
  computadora (ver [Generar la build "Interna"](#generar-la-build-interna-apk) abajo) y se
  reparte a mano (USB, Bluetooth) a los teléfonos de sucursal — **nunca por un canal público**,
  porque trae tu token en texto plano.
- **Backup/Restauración (alternativa, sirve para cualquier instalación ya hecha):** en tu
  teléfono maestro, Configuración → "Guardar Backup" — el archivo `.json` incluye el Token y
  Chat ID (además de billeteras/apps) — trátalo como una contraseña. En cada sucursal,
  "Restaurar Backup" con ese archivo. El nombre de sucursal NO se copia, cada teléfono conserva
  el suyo.

Todas las sucursales mandan su CSV al mismo chat, y puedes avisarles a todas o a una en
particular con los comandos de arriba.

### Copia de seguridad de la configuración (por teléfono)

- **Guardar/Restaurar Backup**: exporta o importa en un archivo `.json` toda la configuración
  de ESE teléfono (billeteras, apps, preferencias, y el Token/Chat ID si está configurado). El
  backup **no incluye el historial** de notificaciones — eso se exporta aparte, en texto o CSV.

---

## Herramientas de administrador (se corren en tu computadora, no en los teléfonos)

Todo lo siguiente vive en la carpeta del proyecto (`~/Documents/miSecretaria` en el Debian del
administrador) — no es parte de la app Android, son scripts de escritorio.

### Publicar una versión nueva

Doble clic en `miSecretaria_Update.desktop` (o `./release.sh` a mano): compila, publica el
Release en GitHub con el APK adjunto, actualiza el manifiesto de "Buscar actualización", y
hace commit+push. Es lo que hace que el botón "Buscar actualización" de los teléfonos vea la
versión nueva.

### Generar la build "Interna" (APK)

```bash
cd ~/Documents/miSecretaria
cp secrets.properties.example secrets.properties   # solo la primera vez
# editar secrets.properties con tu botToken/chatId reales
./build_interna.sh
```
Genera `Releases/miSecretariaV(x.x)-debug_Interna.apk` — el APK con el Token/Chat ID
pre-rellenados que se reparte a mano a las sucursales (ver arriba). **Nunca se sube a GitHub ni
a ningún canal público.**

### Base de datos local de todos los CSV (`miSecretaria.db`)

Si quieres tener juntos, en un solo lugar consultable, todos los CSV que las sucursales te
mandan por Telegram (en vez de verlos sueltos en el chat):

1. Asegúrate de tener **Telegram Desktop abierto con tu sesión** en tu computadora, en el chat
   donde llegan los CSV, con la descarga automática de archivos activada para ese chat.
2. Corre `python3 csv_importer.py` (o doble clic en `miSecretaria_ImportarCSV.desktop`) desde
   la carpeta del proyecto. Muestra un panel en vivo con métricas (archivos/filas importadas,
   próxima revisión, errores) y los últimos eventos, mientras vigila tu carpeta de descargas y
   guarda cada CSV nuevo en `miSecretaria.db` (SQLite) y `miSecretaria.log` (log detallado, en
   modo debug, con rotación automática).
3. Cierra la ventana o presiona Ctrl+C cuando quieras detenerlo — no se inicia solo ni queda
   como servicio permanente.

### Ver el historial en un panel web (`miSecretaria.html`)

Mientras `csv_importer.py` esté corriendo (ver arriba), puedes encender un dashboard local con
todo lo que hay en `miSecretaria.db` — tarjetas con el total y el desglose por sucursal, una
tabla con las últimas 200 notificaciones, y (ver más abajo) herramientas para fusionar o
eliminar sucursales completas.

- **Desde Telegram (recomendado):** manda `/panelon` al bot para encenderlo y `/paneloff` para
  apagarlo. Estos dos comandos son distintos de los que usan las sucursales (`/notificar`,
  etc.) — los procesa tu computadora, no los teléfonos, así que solo funcionan si
  `csv_importer.py` está corriendo en tu PC y tienes `secrets.properties` configurado.
- **A mano, sin Telegram:** `python3 web_server.py` desde la carpeta del proyecto.
- Una vez encendido, ábrelo en el navegador: `http://localhost:8766` (o
  `http://<IP-de-tu-PC>:8766` desde otro dispositivo de tu misma red). También puedes abrir el
  archivo `miSecretaria.html` directo (doble clic) — te redirige solo a esa misma dirección.
- **Filtros:** arriba de las tarjetas hay dos desplegables — "Sucursal" y "Aplicación" (Yape,
  WhatsApp, ZAS, etc.) — se combinan entre sí y filtran la tabla y el total (sobre TODA la
  base, no solo lo que se ve en pantalla). También puedes hacer clic directo en cualquier
  tarjeta de sucursal para filtrar por esa sucursal al instante. "✕ Quitar filtros" los
  resetea a todos.
- **⚙️ Administrar sucursales (fusionar o eliminar):** desplegable debajo de los filtros.
  Útil cuando le cambias el nombre a un teléfono (ej. de sucursal o de empleado) y el
  historial viejo queda repartido bajo el nombre anterior:
  - **Fusionar:** marca las sucursales viejas con las casillas, escribe (o elige de la lista)
    el nombre final, y presiona "🔀 Fusionar seleccionadas" — todo ese historial pasa a tener
    el mismo nombre de sucursal.
  - **Eliminar:** marca las sucursales que quieras borrar y presiona "🗑️ Eliminar
    seleccionadas" — pide confirmación, y es **permanente** (a diferencia del Historial de la
    app en el teléfono, este panel no tiene Papelera). Útil para limpiar sucursales de prueba
    o duplicados que ya fusionaste a otro nombre.

### Backup portable del proyecto completo (migrar a otra computadora)

Doble clic en `miSecretaria_backup.desktop` (o `bash backup_proyecto.sh`): empaqueta el
proyecto completo — código, `secrets.properties` (tu Token/Chat ID real), `miSecretaria.db` y
`miSecretaria.log` — en un único `.tar.gz` dentro de `Archivo/`, listo para copiar a otra
computadora y descomprimir ahí. Es como un "Firefox portable": la sesión (tu Token/Chat ID, y
todo el historial acumulado en `miSecretaria.db`) viaja junto con el código, sin tener que
reconfigurar nada a mano en la máquina nueva. No incluye `.git` ni los APK de `Releases/`
(ya están en GitHub) para que el archivo quede liviano. El script mismo imprime los pasos
exactos para restaurarlo al terminar.

⚠️ El `.tar.gz` generado contiene tu token real — trátalo como una contraseña, guárdalo en un
lugar privado (no lo subas a un canal público ni a una nube sin cifrar).
