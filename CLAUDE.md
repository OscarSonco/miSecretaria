# CLAUDE.md — miSecretaria

Estado del proyecto para continuar el desarrollo desde otra sesión/cuenta de Claude.

## ESTADO ACTUAL (actualizado 2026-09-25)

**2.19 a 2.29 SÍ se publicaron** — el usuario le pidió explícitamente a Claude que corriera
`release.sh` (2026-09-24/25, estando de viaje, sin acceso fácil al Debian) — ya no es un paso
que solo hace el usuario a mano, aunque sigue siendo la norma salvo que él lo pida así de
nuevo. **Desde 2.27, con el teléfono en casa conectado por USB, Claude también instala directo
por ADB** (regla nueva, ver más abajo) — 2.27 a 2.30 ya quedaron instaladas así. **2.24 se
probó en vivo y SÍ funcionó** (fotos reales con la ruta `accounts/1009/Media/...`). **v2.29 se
probó en vivo y confirmó que el robo de medios entre mensajes ya no pasa** (ver "Tanda v2.29").
**v2.30 agrega documentos (PDF/Word/Excel) — pedido explícito del usuario para uso real de
negocio: respaldo de facturas que un empleado de sucursal podría borrar por error o a
propósito. v2.30 SÍ se publicó** (el usuario pidió correr `release.sh`, tag `v2.30`/commit
`2952e90` en GitHub). **v2.31 a v2.33 corrigen tres bugs reales encontrados probando v2.30 en
vivo** (robo de medios por notificación-resumen de grupo, audio compartido en carpeta
distinta a las notas de voz, y `.db`/`.log` descartados por un filtro de "temporales" — ver
sus tandas más abajo) **más tarjetas verdes para notificaciones con archivo adjunto (v2.32)**.
Código en disco = v2.33 (`versionCode=2033`, ver "Tanda v2.33" — **AÚN NO publicada**, falta
correr `release.sh` otra vez para v2.31-2.33). **2.23 en particular sube el
perfil de permisos de la app para TODAS
las sucursales** (pide "Acceso a todos los archivos", no un permiso normal) — conviene que el
usuario avise al personal antes de que la reciban. **Importante:** la tanda v2.19 completa
(borrar/fijar/copiar/nota + centralización de textos) salió a producción SIN haberse probado en
vivo — la próxima sesión debe priorizar confirmar con el usuario que se ve/funciona bien en el
teléfono real, no asumir que "recién compilado" significa "sin probar aún" como en tandas
anteriores. Lo mismo aplica a 2.20 (cambia el comportamiento de "eliminar" en el Historial, ya
no borra, mueve a Papelera) y sobre todo a 2.21 (el fix del long-poll colgado — el usuario ya
tuvo que forzar el cierre de la app una vez por esto en v2.19, así que instalar 2.21+ pronto es
recomendable, no solo cosmético). Reglas de trabajo con
el usuario:
- **Regla de instalación actualizada (2026-09-25, pedido explícito del usuario) — reemplaza la
  regla anterior de "nunca instalar por ADB":** cuando el teléfono del usuario esté conectado
  por USB a este Debian (`adb devices` lo muestra), Claude SÍ instala la app directo por ADB en
  cada versión nueva — ya no hace falta que el usuario use "Buscar actualización" mientras está
  en casa. Cuando el usuario esté de viaje o en una sucursal (teléfono no conectado a este
  Debian), sigue instalando él mismo vía "Buscar actualización", como siempre. ADB para
  diagnóstico (logcat, `dumpsys`, `run-as`, `content query`) sigue disponible siempre, esa parte
  no cambió.
- `CLAUDE.md` (memoria técnica) y `README.md` (manual de uso) se actualizan en cada cambio, no
  solo al cerrar una tanda.
- **Toda versión nueva (código listo + versionCode/versionName subidos) debe ir acompañada de
  `./build_interna.sh`** (2026-09-23, pedido explícito del usuario) — no solo cuando lo pida:
  genera `Releases/miSecretariaV(x.x)-debug_Interna.apk` con el token/Chat ID reales
  pre-rellenados, para que el usuario la tenga lista y la reparta a mano a sus sucursales.
  Después de correrlo, volver a compilar `assembleDebug` normal (sin el flag) para que el
  build por defecto quede sin secretos otra vez.
- **Toda tanda de código, aunque sea chica, necesita su propio bump de versión** — ver
  [[feedback-always-bump-version]] (lección del bug de "Sincronizar ahora" invisible: un fix
  sin subir versión es indistinguible del build ya publicado).

### Tanda v2.24 (2026-09-25) — el escaneo directo de v2.23 buscaba en la ruta equivocada

🐛→✅ **Probado en vivo por el usuario (de viaje, capturas + log reales) apenas se publicó
2.23:** mandaron dos fotos por WhatsApp y ninguna se detectó — 5 intentos en ~2 minutos, todos
"sin encontrar el archivo". Antes (con `MediaStore`), una foto se encontraba en <20s, así que
2 minutos sin éxito no es "todavía no terminó de descargar", es la ruta mal.
- **Causa:** `waBases()` de v2.23 asumía `.../WhatsApp/Media/<subcarpeta>` directo. Pero ya
  estaba documentado desde hace meses (ver limitaciones conocidas) que en este teléfono WhatsApp
  guarda los medios con una carpeta de CUENTA en medio:
  `.../WhatsApp/accounts/<id>/Media/<subcarpeta>` (WhatsApp soporta varias cuentas vinculadas
  en un mismo teléfono desde hace un tiempo, y mete esa carpeta cuando aplica). v2.23 nunca
  miraba ahí.
- ✅ **Arreglo:** `waRoots()` ahora da la raíz SIN el `/Media` final, y `mediaDirsFor(subdir)`
  revisa las DOS formas — `<raíz>/Media/<subdir>` (clásica) y `<raíz>/accounts/<id>/Media/<subdir>`
  (con cuenta, para cualquier `<id>` que exista, listando la carpeta `accounts/`). Se aplica a
  las tres carpetas (`WhatsApp Images`/`WhatsApp Video`/`WhatsApp Voice Notes`) por igual.
- ✅ **Diagnóstico agregado:** si `findNewMedia` no encuentra NINGUNA carpeta accesible en
  ninguna de las rutas conocidas (ni clásica ni con cuenta), ahora loguea un DEBUG explícito
  ("ninguna carpeta de medios de WhatsApp accesible... puede que este teléfono use otra ruta")
  — para la próxima vez que esto falle, distinguir de una vez "la ruta está mal" de "el archivo
  todavía no llegó" sin tener que razonarlo desde cero.
- Se corrigió de paso un mensaje de log que había quedado con texto de la versión anterior
  ("...sin match todavía en MediaStore" en `WalletNotificationListener.kt`, aunque ya no se usa
  MediaStore desde v2.23 — solo texto, no afectaba la función).
- **✅ CONFIRMADO en vivo (2026-09-25):** el usuario instaló 2.24, mandó dos fotos reales y el
  log mostró `Medio nuevo de WhatsApp: image "IMG-20260925-WA0004.jpg"` (y otra más) con la
  ruta exacta `/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/accounts/1009/Media/WhatsApp Images/...`
  — la cuenta de este teléfono es la `1009`. El fix de ruta funcionó a la primera. (Audio
  todavía no se probó con esta versión — solo llegaron fotos en esta prueba — pero usa
  exactamente el mismo código de `mediaDirsFor()`, así que debería funcionar igual.)
  ⚠️ El usuario reportó inicialmente "no funcionó" al ver el mismo log — confusión de UX, no un
  bug: esta función sigue en fase 1 (solo logging), así que no hay NADA visible en el Historial
  que confirme la detección — hay que revisar el log interno para verlo. Vale la pena tenerlo
  presente para la próxima fase (2d-2h): en cuanto haya algo visible en pantalla, esta duda se
  acaba.
- **Publicada por Claude a pedido explícito del usuario** (2026-09-25, "hay un desktop para
  subir la última versión, ejecutar eso" — misma autorización que ya se usó para 2.19-2.23).

### Tanda v2.37 (2026-09-25) — video/foto con texto propio no se detectaban + misterio sin resolver (doc/imagen sin rastro en el log)

El usuario probó de nuevo con un envío detallado y cronometrado: audio de voz (llegó), mp3
(llegó), 3 videos (17:30 llegó, 17:31 y 17:32 NO), un `.doc` (no llegó), una imagen/screenshot
(no llegó), dos `.7z` (no llegaron), un pdf (no llegó). Diagnóstico completo con
`shared_prefs` real (no solo el log).

- 🐛→✅ **Causa CONFIRMADA para los videos #2 y #3 (17:31/17:32): un video enviado CON TEXTO
  PROPIO ya no trae la frase fija.** WhatsApp normalmente pone "🎥 Envió un video. (0:16)"
  (sin texto del usuario) — pero si la persona escribe algo junto con el video, WhatsApp
  reemplaza la frase por ESE texto, dejando el emoji 🎥 + la duración como única señal fija
  (confirmado en los datos reales: `"🎥 Ahora envio monociclo con motociclista junto con
  texto (0:15)"` — nada de "Envió un video."). Como `MEDIA_PHRASE_TYPES` solo buscaba la frase
  EXACTA, un video con texto propio nunca disparaba el escaneo — ni un intento, ni un
  "sin encontrar", nada.
  ✅ **Arreglo:** `EMOJI_TYPES` (nuevo) — mismo principio que ya se usaba para documentos desde
  v2.30 (preferir el emoji, que sobrevive el texto libre, sobre la frase exacta, que no). Se
  agregó como señal PRIMARIA para los 4 tipos: 🎥 video, 📷 foto, 🎤 nota de voz, 🎵 audio
  compartido (además de 📄 documento, que ya estaba) — la frase exacta queda de respaldo, por
  si algún caso no trae emoji. Verificado con los textos reales de esta prueba: el video con
  texto propio ahora sí calcula tipo "video" correctamente.
- 🩺 **Misterio SIN resolver todavía: el `.doc`, los dos `.7z`, el `.pdf` y la imagen
  (screenshot) no dejaron NINGÚN rastro en el log** — ni "Medio nuevo", ni "sin encontrar el
  archivo todavía", ni "no se encontró tras reintentos". Esto es extraño porque los 4 textos
  guardados SÍ contienen el emoji correspondiente (confirmado letra por letra, con el código
  de punto Unicode exacto: `📄` = U+1F4C4, sin variantes ni caracteres invisibles de por
  medio) — con la lógica actual (y la de antes de este fix), `expectedType()` debería haber
  devuelto `"document"` para los tres archivos de documento, disparando el escaneo igual.
  Se probó la lógica exacta en una simulación aparte (Python, replicando el código línea por
  línea) y SÍ devuelve `"document"` para los tres textos reales — la lógica en sí no tiene un
  bug evidente. La imagen (screenshot) es un caso aparte: no se encontró ninguna entrada en el
  Historial con emoji 📷 en absoluto para ese envío — solo el texto de narración
  ("Ahora e enviado una imagen") sin la notificación real de la foto. Teoría más probable, sin
  confirmar: cuando la persona manda el archivo Y CASI AL INSTANTE manda un mensaje de texto
  aparte narrando lo que acaba de mandar (justo el patrón de esta prueba, pensado para que
  Claude pueda diagnosticar), WhatsApp podría estar fusionando ambos en la MISMA notificación
  actualizada antes de que `onNotificationPosted` llegue a procesarla, y `latestMessageText()`
  extrae el texto de narración en vez del de la notificación real del archivo — pero esto NO
  explica por qué el texto GUARDADO en el Historial (que sale de la MISMA extracción) sí
  muestra el emoji correcto. Sigue sin explicación sólida.
  ✅ **Diagnóstico agregado para la próxima vez:** nuevo log DEBUG en
  `WalletNotificationListener.onNotificationPosted` — para CADA notificación de WhatsApp
  aceptada, registra el texto (primeros 60 caracteres) y el tipo que `expectedType()`
  calculó (o "ninguno"). La próxima vez que un documento/imagen no llegue, este log va a decir
  de una si el problema es la detección (tipo = "ninguno" a pesar del emoji, lo que sería un
  bug real distinto al que ya se descartó) o si el problema está en otro lado (tipo correcto
  pero igual sin archivo — ahí sí habría que mirar `findNewMedia`/permisos/carpetas).
- **Sin confirmar todavía en el teléfono** — recién se instaló v2.37 por ADB. Falta repetir un
  envío de video con texto propio (para confirmar el fix) y, sobre todo, un documento/imagen
  con el nuevo log de diagnóstico activo para resolver el misterio de una vez.

### Tanda v2.36 (2026-09-25) — el límite de 100 del Historial podía perder datos ANTES de llegar a Telegram

El usuario notó "el historial siempre muestra 100" y pidió verificarlo. Confirmado: es un
límite real y a propósito (`history.take(100)` en `WalletNotificationStore.add()`, para que
`SharedPreferences` no crezca sin límite) — pero revisando más a fondo se encontró que ese
MISMO límite también afectaba el CSV y el reenvío de medios a Telegram (v2.34), que leían
directo de `history()` con una marca de tiempo (`lastCsvSentAt`/`lastMediaSentAt`). Si a una
sucursal le llegaban más de 100 notificaciones entre un envío periódico y el siguiente (día
ocupado, o el burst de prueba de la Tanda v2.35), las más viejas se perdían del Historial
**antes** de que el worker llegara a mandarlas — ni quedaban visibles, ni llegaban nunca al
CSV ni a Telegram. Dado el caso de uso real del usuario (respaldo completo para caja chica),
esto era un riesgo de pérdida de datos real, no solo un detalle de la interfaz. Se le preguntó
al usuario cómo prefería resolverlo (subir el límite vs. una cola separada sin límite) —
eligió la cola separada.

- ✅ **Dos colas de exportación nuevas, SIN el límite de 100** —
  `WalletNotificationStore.exportCsvQueue()`/`exportMediaQueue()` (persistidas en
  `SharedPreferences` bajo `export_csv_queue`/`export_media_queue`, con un tope de seguridad
  de 20.000 — no un límite operativo real, solo para no crecer de verdad sin fin si Telegram
  queda caído por meses). `WalletNotificationStore.add()` ahora agrega CADA notificación
  nueva a las dos colas, además del `HISTORY` capado en 100 de siempre (que sigue igual, sin
  cambios, para lo que se ve en pantalla). Las colas son independientes de lo que se ve en
  pantalla: un archivo puede desaparecer del Historial visible (por el límite de 100, o
  porque el usuario lo movió a la Papelera) y de todas formas seguir pendiente de exportar —
  es justo el comportamiento que se busca para un respaldo: sobrevivir aunque se "borre"
  localmente, ni la Papelera ni el límite de 100 tocan estas colas.
- ✅ **`TelegramSyncWorker` reescrito para consumir de las colas, no de `history()` con marca
  de tiempo** — `sendPendingCsv()`/`sendPendingMedia()` ya no dependen de
  `TelegramConfig.lastCsvSentAt`/`lastMediaSentAt` (ambas funciones se BORRARON de
  `TelegramConfig.kt`, sin usos que las necesiten más). Cada ítem de la cola de medios se
  quita en cuanto se resuelve (mandado, descartado por tamaño, o sin archivo real) — si el
  envío de uno falla a mitad de camino, se detiene el ciclo ahí, dejando ESE y los siguientes
  en la cola para el próximo intento, en vez de perderlos o saltárselos. El CSV toma una foto
  de la cola completa, manda todo junto en un solo archivo, y solo si el envío fue exitoso
  quita esos ids — lo que haya llegado MIENTRAS se mandaba queda para el próximo ciclo.
- ✅ **`WalletNotificationStore.setMedia()` (el reintento de `WhatsAppMediaScanner`) ahora
  también actualiza la copia en `exportMediaQueue()`** — si no, cuando el archivo se
  encontraba recién en el reintento (4s/10s después), la copia YA guardada en la cola de
  exportación seguía viendo `mediaPath = null` para siempre y nunca se reenviaba.
- ⚠️ **No hay migración retroactiva:** las 100 notificaciones que ya estaban en el Historial
  antes de instalar v2.36 NO se agregaron a las colas nuevas (se crean vacías, se llenan solo
  con notificaciones NUEVAS desde ahora). No se hizo a propósito — mandar de golpe un backlog
  de 100 ítems viejos al bot habría sido más ruido que ayuda.
- **Sin confirmar todavía en el teléfono** — recién se instaló v2.36 por ADB. Falta un ciclo
  real del worker (o "Sincronizar ahora") con notificaciones nuevas para confirmar que las
  colas se vacían correctamente.

### Tanda v2.35 (2026-09-25) — burst de 9 archivos: medios cruzados entre tipos + mensajes repetidos

El usuario probó a fondo mandando 3 videos + 3 imágenes + 1 nota de voz + 1 mp3 + 1 pdf desde
otro WhatsApp, casi todos juntos. Reportó: "solo figuran unos pocos" y "a veces repite
mensajes anteriores". Diagnóstico completo leyendo `shared_prefs/scosecretaria_v01.xml` real
(no solo el log, que además estaba inundado de ruido de VLC "Escanear archivos multimedia" —
mismo problema de rotación que ya pasó antes con OSMAnd, ver limitaciones conocidas).

- 🐛→✅ **Causa real #1 — el escaneo no sabía distinguir TIPOS entre sí mismos.**
  `findNewMedia()` buscaba en las 4 carpetas (imagen/video/audio/documento) y devolvía TODO lo
  que encontraba en la ventana de tiempo, sin importar qué tipo anunciaba la notificación en
  cuestión — el primer resultado (`matches.firstOrNull()`) ganaba sin más criterio que el
  orden de recorrido de carpetas. Confirmado en los datos reales: una notificación
  `"TIGO 01 OSC: 🎥 Envió un video. (5:47)"` terminó con una FOTO adjunta (`mediaType: image`),
  y una `"TIGO 01 OSC: 🎤 Mensaje de voz (0:02)"` terminó con un VIDEO adjunto — ambas porque,
  con 9 archivos aterrizando casi juntos, siempre había ALGO de otro tipo disponible en la
  misma ventana que "ganaba" por casualidad de orden.
  ✅ **Arreglo:** `WhatsAppMediaScanner.expectedType(title, text)` (nuevo, reemplaza el interior
  de `looksLikeNewMedia()`, que ahora es un simple `expectedType(...) != null`) — cada frase de
  `MEDIA_PHRASE_TYPES` ahora sabe a qué tipo pertenece ("envió un video" → `"video"`, "mensaje
  de voz" → `"audio"`, etc.), y el emoji 📄 sigue mapeando a `"document"`. `findNewMedia()`
  ganó un parámetro `preferredType` — sigue devolviendo TODOS los archivos nuevos encontrados
  (para no perder los que llegan de más, que se guardan como notificaciones aparte), pero
  ahora los ORDENA: primero los del tipo que la notificación realmente anuncia, y dentro de
  cada tipo, el archivo cuya fecha de modificación esté más CERCA del `postTimeMs` de la
  notificación (antes no había ningún criterio de cercanía, solo "lo que sea que aparezca").
  Esto no es 100% infalible si llegan DOS archivos del MISMO tipo casi al mismo instante (ej.
  dos videos con 1 segundo de diferencia) — sigue habiendo margen de error ahí, pero ya no se
  cruzan tipos distintos (video↔foto, voz↔video) como pasaba antes.
- 🐛→✅ **Causa real #2 — reposteos duplicados no detectados por el dedupe existente.**
  Confirmado en los datos reales: la notificación-resumen del grupo "RedSonco 😎😎😎 (7
  mensajes)" (la misma de la Tanda v2.31) apareció **7 veces** en 11 minutos con el TEXTO
  IDÉNTICO cada vez, y una nota de voz real (`"TIGO 01 OSC: 🎤 Mensaje de voz (0:02)"`) y un
  video real (`"TIGO 01 OSC: 🎥 Envió un video. (0:11)"`) también se repitieron, cada uno dos
  veces con el mismo título+texto exacto, segundos aparte. El dedupe existente
  (`statusBarNotification.key` + título + texto) no los agarraba — el `key` de Android cambia
  entre reposteos aunque el contenido sea idéntico, así que cada repost pasaba como "nuevo".
  Esto es justo el "a veces repite mensajes anteriores" que reportó el usuario.
  ✅ **Arreglo:** nuevo filtro de reposteo en `WalletNotificationListener` — un mapa en memoria
  (`recentMessages`, título+texto → hora del último aceptado) descarta cualquier notificación
  cuyo título+texto EXACTO ya se aceptó hace menos de `REPOST_WINDOW_MS` (5 segundos). Se limpia
  solo (las entradas más viejas que la ventana se descartan cada vez que se revisa), nunca
  crece sin límite. 5 segundos es corto a propósito — no debería filtrar dos mensajes
  genuinamente distintos que coincidan de texto por casualidad, solo reposteos literales del
  mismo instante.
- **Sin confirmar todavía en el teléfono** — recién se instaló v2.35 por ADB. Falta que el
  usuario repita un envío parecido (varios archivos casi juntos) para confirmar que ya no hay
  cruces de tipo ni repetidos, y seguir de cerca si algún archivo TODAVÍA se pierde del todo
  (ej. el PDF de esta prueba nunca apareció ni como "no encontrado" en el log — puede ser que
  su notificación ni siquiera haya llegado a `onNotificationPosted`, algo a investigar en la
  próxima prueba si se repite).

### Tanda v2.34 (2026-09-25) — reenvío periódico de fotos/videos/audios/documentos al bot (Paso 2f)

Pedido explícito del usuario: "así como de cada App envía cada X tiempo los .csv, que también
se envíen al BOT los Videos/Audios/Documentos/Archivos". Es el **Paso 2f** que quedaba
pendiente en el plan de CLAUDE.md desde hace varias tandas.

- ✅ **`TelegramSyncWorker.sendPendingMedia()` (nueva)** — mismo patrón exacto que
  `sendPendingCsv()`: un marcador "hasta dónde ya se mandó" (`TelegramConfig.lastMediaSentAt`,
  nuevo, mismo mecanismo que `lastCsvSentAt`) en vez de una lista de pendientes aparte. Cada
  ciclo del worker periódico (mismo intervalo que el CSV, mínimo 15 min) busca en
  `WalletNotificationStore.history()` las notificaciones con `mediaPath != null` y
  `receivedAt` posterior al marcador, las ordena por fecha, y las manda una por una con
  `TelegramClient.sendDocument()` — reutilizando el mismo método que ya mandaba el CSV (el
  multipart ya era genérico, solo mandaba el `Content-Type` fijo en `"text/csv"`).
  - Si el archivo pesa más de 50 MB (límite real de la API de Telegram para bots), se salta
    (no tiene sentido intentarlo, Telegram lo rechazaría) y el marcador SÍ avanza para ese
    ítem — no se reintenta para siempre algo que nunca va a poder mandarse.
  - Si el envío de un archivo falla (red cortada, etc.), se detiene el ciclo ahí — el marcador
    NO avanza más allá del último que sí se mandó, así ese archivo se reintenta en el próximo
    ciclo en vez de perderse o saltarse.
  - El pie de foto (`caption`) incluye un emoji según el tipo (🎥 video, 🎤 audio, 📄
    documento, 📷 foto/otro), la sucursal, la billetera/app de origen y el mensaje original
    (recortado a 200 caracteres).
- ✅ **`TelegramClient.sendDocument()` generalizado** — ganó un parámetro `mimeType` (antes
  tenía `"text/csv"` fijo en el código, sin importar qué se mandara). El envío del CSV ahora
  pasa `"text/csv"` explícito (mismo comportamiento que antes, sin regresión); el de medios
  adivina el tipo real con `MimeTypeMap.getMimeTypeFromExtension()` según la extensión del
  archivo, así Telegram puede mostrarlo mejor (reproductor de video/audio en vez de un ícono
  de archivo genérico) — aunque Telegram acepta el archivo igual sin importar si el MIME es
  exacto. También se subió el `readTimeout` de 20s a 60s (un video puede pesar varios MB, un
  CSV nunca).
- **Sin probar todavía en el teléfono** — recién se instaló v2.34 por ADB. El usuario ya tiene
  medios reales guardados en el historial de antes (fotos/videos/audios/PDF de las pruebas de
  v2.31-2.33), así que en el próximo ciclo automático (o tocando "Sincronizar ahora" en
  Configuración) debería empezar a mandarlos al bot — a confirmar que realmente llegan al chat
  de Telegram y que la próxima corrida no los repite (marcador avanzando bien).

### Tanda v2.33 (2026-09-25) — bug latente: `.db`/`.log` se ignoraban SIEMPRE, aunque el usuario los necesita de verdad

El usuario preguntó qué pasaría si un empleado le manda un `.log` o un `.db` — los pide para
analizar caja chica/cuentas de las sucursales. Revisando el código ANTES de que pasara de
verdad (no fue reportado como bug en vivo, se encontró por revisión preventiva):

- 🐛→✅ **`IGNORED_EXTENSIONS` (en `WhatsAppMediaScanner.kt`) incluía `"db"` y `"log"`** —
  esta lista se copió tal cual de `SoncoBot/WhatsAppWatcher.kt` (otro proyecto, revisado en
  v2.23 para resolver audio/video) para descartar archivos temporales/internos de WhatsApp
  (`.nomedia`, `.tmp`, etc.) dentro de sus carpetas de medios. El problema: un `.db`/`.log`
  real que un empleado mande como documento por WhatsApp (📄 + nombre de archivo, cae en
  `WhatsApp Documents` igual que cualquier otro documento) quedaría **descartado en silencio**
  por este filtro — ni se copiaba, ni aparecía en el Historial con su archivo, exactamente
  como si nunca hubiera llegado. `SoncoBot` nunca tuvo un caso de negocio que necesitara esas
  dos extensiones como documentos legítimos; miSecretaria sí.
- ✅ **Arreglo:** se quitaron `"db"` y `"log"` de `IGNORED_EXTENSIONS` — ahora sí se detectan,
  copian y muestran como cualquier otro documento (botón "📄 Abrir documento"). El resto de la
  lista (`nomedia`/`tmp`/`dat`/`journal`/`ini`) se dejó igual — no hay evidencia de que
  también bloqueen algo que el usuario necesite; si en el futuro pasa lo mismo con otra
  extensión, mismo patrón de arreglo.
- ⚠️ **Nota aparte, no es un bug:** el botón "Abrir documento" adivina el tipo de archivo por
  la extensión (`MimeTypeMap`) para elegir con qué app abrirlo — para `.db`/`.log` no hay una
  app "obvia" instalada típicamente, así que Android va a mostrar el selector genérico "Abrir
  con" (o avisar que no hay ninguna app que lo abra) en vez de abrirlo automático. El archivo
  de todas formas queda respaldado y se puede compartir/copiar a la PC para analizarlo ahí
  (ej. con un visor de SQLite para el `.db`) — el respaldo es lo importante, no la vista previa
  en el teléfono.
- **Sin confirmar todavía en el teléfono** — recién se instaló v2.33 por ADB. No hubo caso
  real reportado (el usuario preguntó "qué pasaría", no que ya le hubiera fallado) — falta que
  algún empleado le mande un `.db`/`.log` real para confirmar que ahora sí queda guardado.

### Tanda v2.32 (2026-09-25) — audio COMPARTIDO (no nota de voz) nunca se encontraba + tarjetas verdes para lo que trae archivo

Reportado por el usuario tras instalar v2.31: 2 fotos + 2 videos + 1 pdf salieron bien, pero
un `.mp3` recibido como audio (no una nota de voz grabada en el chat) "no figura en el
historial" (sin adjunto). También pidió, para que el Historial se vea mejor: (1) las tarjetas
que traen un archivo adjunto (video/audio/documento/lo que sea) se vean con fondo VERDE, para
distinguirlas de un vistazo entre el resto de mensajes mezclados de otras conversaciones.
También reportó que el Historial se ve "desordenado y mezclado con otros chats".

- 🐛→✅ **Causa real, confirmada con `adb shell ls` directo sobre la carpeta real:** WhatsApp
  guarda los audios en DOS carpetas distintas según el origen — `WhatsApp Voice Notes` (notas
  de voz grabadas en el chat, `.opus`) y **`WhatsApp Audio`** (archivos de audio COMPARTIDOS,
  ej. un `.mp3` enviado como adjunto — confirmado: `AUD-20260925-WA0077.mp3` apareció ahí,
  modificado exactamente a la hora de la notificación). El código (`WA_SUBDIRS`) solo miraba
  la primera — un comentario viejo en el archivo decía textualmente que "WhatsApp Audio" "se
  agrega igual por si acaso", pero nunca se agregó de verdad al mapa (comentario desactualizado
  que no coincidía con el código real). La notificación del mp3 SÍ decía "🎵 Mensaje de voz
  (1:12)" (mismo texto que una nota de voz, solo con emoji distinto) y SÍ disparaba el
  escaneo — el escaneo simplemente nunca miraba en la carpeta correcta.
- ✅ **Arreglo:** `WA_SUBDIRS` cambió de `Map<String, String>` a `Map<String, List<String>>` —
  el tipo "audio" ahora revisa AMBAS carpetas (`WhatsApp Voice Notes` y `WhatsApp Audio`).
  `findNewMedia()` itera la lista de subcarpetas por tipo en vez de una sola. El resto de tipos
  (imagen/video/documento) quedan con una sola carpeta cada uno, sin cambios de comportamiento.
- ✅ **Tarjetas verdes para notificaciones con archivo adjunto:** `MediaCardGreen` (nuevo,
  `ui/theme/Color.kt`, `#E1F3E5` — verde CLARO de fondo, no el verde fuerte de los botones,
  para que el texto siga leyéndose normal) — el `Card` de cada notificación en el Historial
  (`MainActivity.kt`) usa ese color de fondo si `item.mediaPath != null` (cualquier tipo:
  imagen/video/audio/documento), y el color por defecto si no. Aplica pareja a todos los
  tipos, no solo a los que pidió explícitamente el usuario (video/audio/documento) — una foto
  con miniatura también cuenta como "trae archivo adjunto".
- 🩺 **"Desordenado y mezclado con otros chats" — revisado el código, NO es un bug de orden:**
  `WalletNotificationStore.add()` siempre inserta al PRINCIPIO (`history.add(0, ...)`, más
  nuevo primero) y `HomeScreen` no reordena por su cuenta (`ordered = pinned + rest`, sin
  volver a ordenar por fecha) — el orden cronológico real se respeta. La sensación de
  "mezclado" es el diseño de la app: captura TODAS las conversaciones de WhatsApp en una sola
  lista, así que si mandas 5 archivos seguidos, los mensajes de OTRAS conversaciones que
  lleguen en el medio aparecen igual, intercalados, en el Historial — no hay una vista
  separada "solo lo que yo mandé". Las tarjetas verdes de este mismo cambio deberían ayudar
  mucho a que los archivos destaquen a simple vista aunque sigan intercalados. Si el usuario
  quiere además un filtro "solo con archivo adjunto", es un pedido nuevo, no implementado
  todavía.
- **Sin confirmar todavía en el teléfono** — recién se instaló v2.32 por ADB. Falta mandar un
  audio compartido (no nota de voz) y confirmar que aparece con su reproductor, y ver las
  tarjetas verdes en pantalla.

### Tanda v2.31 (2026-09-25) — el audio y el PDF SÍ se detectaban, pero se los "robaba" una notificación resumen de grupo vieja

🐛→✅ **Reportado por el usuario:** mandó 2 fotos + 2 videos + 1 audio + 1 catálogo en PDF por
WhatsApp — las 4 primeras (fotos/videos) se registraron bien, pero el audio y el PDF "solo
registró texto". Diagnóstico completo con el teléfono conectado por ADB (log +
`shared_prefs/scosecretaria_v01.xml` real, no solo el log):

- **El log SÍ mostraba** `"Medio nuevo de WhatsApp: audio \"PTT-...opus\" copiado a ..."` y
  `"...document \"CATALOGO SIERRAS...pdf\" copiado a ..."` — es decir, `WhatsAppMediaScanner`
  SÍ encontró y copió los dos archivos reales. El problema no era la detección/copia (eso ya
  estaba resuelto desde v2.23-v2.30); era que terminaban adjuntos a la notificación
  EQUIVOCADA.
- **Causa real, confirmada comparando mensaje vs. medio adjunto en `shared_prefs` real:** hay
  un grupo de WhatsApp ("RedSonco 😎😎😎") cuya notificación-resumen de varios mensajes sin
  leer (título `"RedSonco 😎😎😎 (7 mensajes)"`) se repitió varias veces durante la prueba —
  y el "último mensaje" que `latestMessageText()` le extrae es uno VIEJO, del 13.09.2026 (12
  días antes), que menciona un PDF distinto ("13.09.2026.FacturaPDF.pdf"). Como ese texto
  viejo contiene el emoji 📄, cada vez que esa notificación-resumen se reenviaba,
  `looksLikeNewMedia()` la tomaba por "esto parece un documento nuevo" y disparaba un escaneo
  — que, como busca "lo que sea que haya en la carpeta ahora mismo", terminó agarrando el
  audio real (15:18:45) y más tarde el PDF real del catálogo (15:19:22) que SÍ acababan de
  llegar por OTRAS conversaciones. Confirmado en los datos guardados: la notificación real
  `"TIGO 01 OSC: 🎤 Mensaje de voz (0:11)"` quedó con `mediaPath: null`, mientras que
  `"RedSonco 😎😎😎 (7 mensajes): TIGO 01 OSC: 📄 13.09.2026.FacturaPDF.pdf"` (texto viejo, sin
  relación) quedó con el `.opus` real adjunto; mismo patrón exacto con el PDF del catálogo
  unos segundos después.
- ✅ **Arreglo:** `WhatsAppMediaScanner.looksLikeNewMedia()` ahora excluye por completo
  cualquier notificación cuyo TÍTULO calce con el patrón `"(N mensajes)"`/`"(N messages)"`
  (`GROUP_SUMMARY_TITLE`, nueva regex) — sin importar lo que diga el texto, no hay forma de
  confiar en que el "último mensaje" de un resumen de grupo sea genuinamente nuevo. No se
  tocó `WalletNotificationListener.kt` (el filtro de `FLAG_GROUP_SUMMARY` ahí sigue igual;
  confirmado que esta notificación en particular NO trae ese flag en este teléfono, por eso
  se necesitó un filtro adicional en el scanner).
- 🔍 **Hallazgo relacionado, NO corregido en esta tanda (fuera de lo que pidió el usuario):**
  el mismo log mostró el texto `"Sonco Perú: 🎥 Envió un video. (0:04)"` repetido 6 veces en
  ~2 minutos, cada vez con un archivo distinto adjunto (a veces una imagen en vez de un video,
  a veces sin nada) — parece ser la MISMA notificación de un video real que WhatsApp
  reenvía/actualiza varias veces (progreso de descarga, cambios de estado), y cada reenvío
  vuelve a disparar su propio escaneo, con riesgo de agarrar el archivo equivocado si hay
  varios llegando cerca en el tiempo. Como en esta prueba las 4 fotos/videos igual se vieron
  "bien" a simple vista (el usuario no reportó esto como problema), se deja documentado para
  el futuro en vez de tocarlo ahora — mismo patrón de "no arreglar lo que no se pidió".
- **Sin confirmar todavía que el fix resuelva el problema en la práctica** — recién se
  instaló v2.31 por ADB; falta que llegue un audio y un documento nuevos (idealmente sin que
  ese grupo con la notificación-resumen vieja se reactive al mismo tiempo) para confirmar que
  ahora sí quedan adjuntos a su propia notificación.

### Tanda v2.30 (2026-09-25) — documentos (PDF/Word/Excel), pedido con caso de uso de negocio real

Pedido explícito del usuario, con el motivo dicho claramente: *"Necesito esto para mis
sucursales, para que cuando alguno de mis empleados borre alguna factura yo tener respaldo"* —
o sea, el mismo propósito de todo el Paso 2 (copiar a almacenamiento propio para sobrevivir un
borrado), aplicado ahora a documentos además de foto/audio/video.

- ✅ **Detección distinta a los demás tipos:** foto/video/nota de voz tienen una FRASE fija que
  genera WhatsApp ("Envió una foto.", etc. — ver v2.29). Los documentos NO — WhatsApp pone el
  emoji 📄 seguido del **nombre real del archivo** (confirmado en vivo:
  `"📄 HOJA DE VIDA - LUIS GUSTAVO BECERRA_2026.docx"`), que cambia siempre. Por eso
  `looksLikeNewMedia()` ahora también revisa la presencia del emoji `📄` (`DOCUMENT_EMOJI`) en
  vez de una frase — mismo principio de "usar la señal más específica posible" de v2.29, solo
  que aquí la señal específica es un emoji en vez de una frase.
- ✅ **`WA_SUBDIRS` gana `"document" to "WhatsApp Documents"`** — misma carpeta real
  confirmada por `adb shell ls` (`.../Media/WhatsApp Documents/`, archivos sueltos, sin
  subcarpetas por fecha como las notas de voz — igual que foto/video). Reutiliza toda la
  infraestructura ya existente (`mediaDirsFor`, `filesIn`, reintentos, dedupe) sin cambios.
- ✅ **`DocumentOpenButton` (nuevo, `MainActivity.kt`)** — botón "📄 Abrir documento" que abre
  el archivo copiado con la app que el usuario tenga instalada para ese tipo (PDF/Word/Excel/
  lo que sea), adivinando el tipo MIME por la extensión (`MimeTypeMap`) y usando `FileProvider`
  (igual que `VideoOpenButton`) — nunca se expone la ruta cruda a otra app.
- **Sin probar en el teléfono todavía** — recién se instaló por ADB. Falta que llegue un
  documento nuevo y confirmar que aparece con el botón "Abrir documento" y que se abre bien
  con la app correcta (PDF con lector de PDF, .docx con Word/lo que tenga, etc.).

### Tanda v2.29 (2026-09-25) — v2.28 sí encontró el audio, pero empezó a robar medios ajenos

🐛→✅ **El usuario probó v2.28 y reportó "está peor"** — sin más detalle en el mensaje, así que
se investigó de nuevo con acceso directo al teléfono (`shared_prefs` + capturas de pantalla)
antes de asumir nada:
- ✅ **El fix de v2.28 SÍ funcionó** — se confirmó una nota de voz real
  (`"Sonco Perú: 🎤 Mensaje de voz (0:07)"`) con `mediaType: "audio"` y su archivo `.opus`
  copiado correctamente. El problema no era que el audio siguiera sin encontrarse.
- 🐛 **El problema real, encontrado comparando mensaje vs. medio adjunto en el historial:**
  `"Sonco Perú: Este es el audio de doña Marta"` (un mensaje de TEXTO normal, la persona
  simplemente contando que había un audio) apareció con `mediaType: "image"` — ¡le habían
  adjuntado una FOTO que no tenía nada que ver! Causa: `MEDIA_KEYWORDS` tenía palabras sueltas
  ("foto", "audio", "video") — cualquier mensaje de texto normal que solo MENCIONE esas
  palabras (muy común en una conversación real, ej. "te mando el audio", "esa es la foto que
  pediste") disparaba `looksLikeNewMedia() == true` y arrancaba un escaneo de las carpetas de
  WhatsApp igual que si fuera la notificación real de un medio nuevo. En esta prueba estaban
  llegando varias fotos/videos/audios reales casi al mismo tiempo (mensajes de prueba
  seguidos) — el escaneo de ese mensaje de texto encontró la foto de OTRO mensaje real
  (llegada dentro de la misma ventana de ±30s/+15s) y, como el dedupe es por ruta de archivo
  (una vez "visto" no se repite), esa foto quedó apropiada por el mensaje equivocado.
- ✅ **Arreglo:** `MEDIA_KEYWORDS` pasó de palabras sueltas a las **frases exactas** que
  WhatsApp realmente genera en su propia notificación: `"envió una foto"`, `"envió un video"`,
  `"mensaje de voz"` (+ equivalentes en inglés). Es muy poco probable que una persona escriba
  esas frases exactas conversando normal — a diferencia de la palabra suelta "foto"/"audio",
  que sí es común. Esto reduce drásticamente (sin eliminar del todo — sigue siendo texto libre
  de otra persona) el riesgo de falso positivo.
- **Lección para el futuro:** al ajustar heurísticas de detección basadas en texto libre de
  terceros (acá, lo que WhatsApp pone en la notificación), preferir SIEMPRE la frase más
  específica posible sobre la palabra suelta más genérica — la ganancia en "atrapar más casos"
  casi nunca compensa el riesgo de falsos positivos con datos que no controlamos.
- **Sin confirmar todavía si esto elimina el problema del todo** — recién se instaló (por ADB).
  Si vuelve a pasar con las nuevas frases (más raro, pero no imposible en una conversación que
  sí mencione "mensaje de voz" al hablar de otra cosa), habría que agregar una validación extra
  (ej. exigir que el mensaje sea CORTO y esté compuesto casi solo por la frase, no una oración
  larga que la contenga).

### Tanda v2.28 (2026-09-25) — el audio SIEMPRE fallaba: notas de voz en subcarpetas por semana

🐛→✅ **Diagnóstico completo con acceso directo al teléfono** (usuario pidió revisar "la lista
de útiles no sale" + "el audio/video no sale aunque se detecta"). Se leyó `shared_prefs/
scosecretaria_v01.xml` directo del teléfono (`run-as`) y se comparó contra el log — mucho más
concluyente que solo el log:
- **Fotos y video:** SÍ funcionan la mayoría de las veces (confirmado con varios `mediaType:
  "image"`/`"video"` reales en el historial, incluyendo capturas de pantalla mostrando el botón
  "▶ Reproducir video" ya renderizado bien). Hay misses ocasionales (ej. "TIGO 01 OSC: 📷 Envió
  una foto." de las 12:21:18 sin medio) — se acepta como el resto de tiempo/timing normal, no
  se investigó cada caso individual.
- **Audio: 100% de fallos** — se encontraron **9 notas de voz distintas** en el historial
  (`"🎤 Mensaje de voz (0:12)"`, `(0:09)`, `(0:14)`, etc., de varios contactos, en distintos
  momentos, con y sin los reintentos de v2.27), TODAS con `mediaType: null`. Ni una sola vez
  funcionó, con o sin reintento — esto ya no es un problema de tiempo/timing (el reintento del
  v2.27 ya lo cubría), es una carpeta que el código nunca miraba.
- **Causa real, confirmada con `adb shell ls` directo sobre la carpeta real
  (`.../WhatsApp/accounts/1009/Media/WhatsApp Voice Notes/`):** a diferencia de "WhatsApp
  Images"/"WhatsApp Video" (que guardan los archivos sueltos, directo en la carpeta), "WhatsApp
  Voice Notes" los agrupa en subcarpetas por semana (`202635`, `202636`... `202639` = año 2026,
  semana 39 — los `.opus` reales viven DENTRO de esas). `findNewMedia()` hacía
  `dir.listFiles()` y filtraba `f.isFile` — como el contenido de "WhatsApp Voice Notes" son
  puras CARPETAS (las semanas), nunca pasaba el filtro, nunca se encontraba ni un solo audio.
- ✅ **Arreglo: `filesIn(dir, extraDepth = 1)` (nueva)** reemplaza el `dir.listFiles()` directo
  — junta los archivos sueltos de la carpeta MÁS los de sus subcarpetas inmediatas (cubre las
  semanas de "Voice Notes" y de paso "Private"/"Sent", que existen en varios tipos). Se aplica
  igual a los tres tipos (imagen/video/audio) — no hace falta tratar el audio como caso
  especial, la misma función sirve para todos.
- **Sin confirmar todavía si esto arregla el audio de verdad** — recién se instaló (por ADB,
  regla nueva de esta misma tanda) en el teléfono del usuario. Falta que llegue una nota de voz
  nueva y revisar que esta vez sí aparezca con su botón de reproducir.

### Tanda v2.27 (2026-09-25) — reintento del escaneo de medios + regla nueva de instalación

🐛→✅ **Confirmado en vivo tras publicar 2.26:** el usuario mandó 3 fotos, solo 1 apareció en
el Historial con la imagen real. El log del teléfono (`run-as ... cat
files/miSecretaria_debug.log`) mostró el patrón: muchísimos "sin encontrar el archivo todavía"
y un solo "Medio nuevo de WhatsApp... copiado a..." — el escaneo se intentaba UNA vez, justo al
llegar la notificación, y si WhatsApp todavía no había terminado de escribir el archivo en ese
instante exacto, se perdía para siempre (no había ningún reintento).
- ✅ **`scheduleMediaRetry()` (nueva, en `WalletNotificationListener.kt`):** si el primer
  intento no encuentra nada, lanza una corrutina en `serviceScope` (el mismo que ya usa el
  long-poll de Telegram) que reintenta a los 4s y a los 10s, con una ventana de búsqueda cada
  vez más ancha. Si encuentra el archivo en el reintento, llama a
  `WalletNotificationStore.setMedia(id, path, type)` (nueva) para actualizar la notificación
  YA guardada — el Historial la refresca solo, porque ya sondea el store cada 700ms. Si tras
  los dos reintentos sigue sin nada, se deja así (no reintenta para siempre).
- **Simplificación a propósito:** si en el reintento aparecen VARIOS archivos nuevos a la vez,
  solo se usa el primero (a diferencia del intento inicial, que sí genera notificaciones extra
  para cada archivo adicional) — caso raro dentro de un caso ya raro, no vale la pena la
  complejidad completa ahí.
- ✅ **Regla de instalación cambiada (pedido explícito del usuario):** "como está el celular
  conectado a esta Debian, lo instalarás vía ADB, cuando yo esté fuera o en mis sucursales lo
  instalaré vía actualización" — reemplaza la regla anterior de "ADB nunca instala". Ver la
  regla actualizada al principio de este documento. **2.27 ya se instaló así**
  (`adb install -r`), confirmado que preserva los datos de la app (Token/Chat ID de Telegram
  seguían ahí después).
- **Sin confirmar todavía si el reintento resuelve el problema** — recién se instaló, falta que
  lleguen más fotos/audios para ver si ahora sí aparecen todas en el Historial.

### Tanda v2.26 (2026-09-25) — Paso 2 avanza a fase 2: copia real + mostrar en el Historial

Pedido explícito del usuario tras confirmar que la detección (fase 1) funcionaba: "en el
historial solo muestra el texto... allí debe mostrarse la copia de esa foto/imagen/audio". Ya
no es solo logging — ahora la fase 1 (detectar) se conecta con 2d (copiar) y una versión ligera
de 2e (mostrar/reproducir), saltándose por ahora la cola de reproducción con TTS y el reenvío a
Telegram (eso sigue pendiente, ver tabla de Paso 2 más abajo).

- ✅ **`WalletNotificationListener.onNotificationPosted` reordenado:** antes el escaneo de
  medios corría ANTES de saber si esta notificación específica se iba a guardar (podía
  consumir/marcar un archivo como "ya visto" sin nunca asociarlo a nada, si `label` salía
  null). Ahora el escaneo corre DESPUÉS de confirmar wallet/app-match + dedupe + mensaje no
  vacío — recién ahí, si hay coincidencia, se llama `copyMediaToAppStorage()` (nueva función,
  `File.copyTo()` — copia, no mueve, el original de WhatsApp queda intacto) y el resultado se
  guarda en el `mediaPath`/`mediaType` de la `WalletNotification` real, no en un log suelto.
- ✅ **`mediaType` nuevo** en `WalletNotification` (`"image"`/`"video"`/`"audio"`, o `null`
  para las miniaturas viejas de `EXTRA_PICTURE` — que a propósito se tratan como `"image"` por
  compatibilidad) — persistido en `WalletNotificationStore` igual que `note`/`mediaPath`
  (`optString`/`JSONObject.NULL`, historiales viejos sin el campo cargan igual con `null`).
- ✅ **Varios medios en la misma ventana:** si `findNewMedia` devuelve más de un archivo nuevo
  (ej. dos fotos seguidas), el primero se asocia a la notificación principal y **el resto ya no
  se pierde** — cada uno de los demás genera su propia `WalletNotification` extra (mismo
  wallet/kind, mensaje genérico `"<tipo> adjunto: <nombre>"`) con su propia copia. Antes
  (v2.23-2.25) esos archivos adicionales se marcaban como "vistos" en el log y se perdían para
  siempre (nunca se iban a reintentar, pero tampoco se guardaban).
- ✅ **UI del Historial (`HomeScreen`, `MainActivity.kt`):** la tarjeta ahora decide qué mostrar
  según `item.mediaType`:
  - `"image"` (o `null`, legado) → `ThumbnailImage` de siempre — ahora con la foto REAL
    completa en vez de la miniatura de baja resolución de la notificación.
  - `"audio"` → **`AudioPlayer` (nuevo)**: botón "▶ Reproducir audio"/"⏸ Detener audio" con
    `android.media.MediaPlayer` directo sobre el archivo copiado — sin barra de progreso ni
    pausa real (innecesario para una nota de voz corta), se detiene solo al terminar
    (`setOnCompletionListener`). Se libera el `MediaPlayer` en `DisposableEffect` al salir de
    pantalla, para no dejarlo fugado.
  - `"video"` → **`VideoOpenButton` (nuevo)**: botón "▶ Reproducir video" que abre el archivo
    con el reproductor de video que el usuario tenga instalado, vía `Intent.ACTION_VIEW` +
    `FileProvider` (nunca se expone la ruta `file://` cruda a otra app). Requirió agregar
    `<files-path name="media" path="media/" />` a `res/xml/file_paths.xml` — antes el
    `FileProvider` solo cubría `external-files-path`/`cache-path`, no `filesDir/media/` (donde
    viven estas copias y las miniaturas viejas).
- ✅ **Limpieza al vaciar la papelera:** `WalletNotificationStore.emptyTrash()` ahora borra del
  disco el archivo de `mediaPath` de cada notificación antes de vaciar — para no acumular fotos
  y videos huérfanos para siempre. (Mover a la papelera o recortar el historial a 100 SIGUEN sin
  borrar el archivo — quedan como gaps conocidos, ver Paso 2h en la tabla de abajo; se priorizó
  el caso de borrado permanente real, que es el más importante.)
- **Sin probar en el teléfono todavía** — toda esta tanda es nueva: ni la copia real, ni
  `AudioPlayer`, ni `VideoOpenButton`, ni la limpieza al vaciar papelera. Cuando se pruebe,
  confirmar en particular que el ícono/imagen que se ve en el Historial para una foto de
  WhatsApp ya es la foto real (más nítida que antes) y no la miniatura de baja resolución.

### Tanda v2.25 (2026-09-25) — redundancia de escaneo genérico (pedido explícito del usuario)

El usuario, tras confirmar que 2.24 sí funcionaba, pidió agregar la red de seguridad que
`SoncoBot` tiene para esto ("había un botón de escáner, y eso escaneaba todas las variantes")
para que funcione igual **"en diferentes marcas/modelos de celulares"**, sin depender de que la
ruta fija (clásica o con cuenta) siga siendo válida en otros teléfonos/versiones de WhatsApp.

- ✅ **`genericWhatsAppBases()` + `findDirsNamed()`** (nuevas) — mismo espíritu que
  `findWhatsAppDirs()` de `SoncoBot/WhatsAppWatcher.kt`: en vez de asumir una estructura fija,
  busca cualquier carpeta cuyo nombre contenga "whatsapp" bajo `Android/media/` (cubre
  `com.whatsapp`, `com.whatsapp.w4b`, clones como GBWhatsApp) o directamente en la raíz del
  almacenamiento (`/sdcard/WhatsApp`, instalaciones viejas), y dentro de esas, busca
  recursivamente (tope `GENERIC_SCAN_MAX_DEPTH = 4` niveles, a propósito acotado) una carpeta
  llamada EXACTAMENTE "WhatsApp Images"/"WhatsApp Video"/"WhatsApp Voice Notes" — sin asumir en
  qué nivel de anidamiento está (cubre tanto la ruta clásica como la de cuenta, y cualquier otra
  variante razonable).
- ✅ **`mediaDirsFor()` ahora combina las dos estrategias, con las rutas conocidas como
  principal**: primero intenta las rutas fijas (clásica + `accounts/<id>/`, rápido, sin
  recorrer nada); **solo si eso no encuentra NADA**, cae al escaneo genérico de respaldo. Es
  exactamente la redundancia pedida — no se reemplazó lo que ya se demostró que funciona, se le
  agregó un plan B para cuando no aplique (otro fabricante, otra versión de WhatsApp, un clon).
- El mensaje de diagnóstico ("ninguna carpeta accesible") ahora es más fuerte — solo aparece si
  FALLAN los dos caminos, ruta conocida y escaneo genérico.
- **Sin probar en el teléfono todavía** — el camino de rutas conocidas ya está confirmado
  (Tanda v2.24); el camino genérico de respaldo, al no haberse necesitado en el teléfono de
  prueba actual, no se ha ejercitado en vivo. Se podría forzar una prueba renombrando
  temporalmente la carpeta de rutas conocidas, pero no se hizo (no vale la pena arriesgar el
  WhatsApp real del usuario para probarlo).

### Tanda v2.23 (2026-09-24) — medios de WhatsApp: lectura directa de archivo, ya no MediaStore

Pedido explícito del usuario: revisar cómo lo resolvió su otro proyecto,
`~/Documents/SoncoBot` (app Android de monitoreo remoto más amplio — ubicación, contactos,
cámara, etc.; NO se copió nada de eso, solo la pieza de medios de WhatsApp) y aplicar la misma
solución aquí. El usuario confirmó explícitamente que acepta el permiso más fuerte que esto
requiere.

- ✅ **`WhatsAppMediaScanner.kt` reescrito** — ya NO consulta `MediaStore.Images/Video/Audio`
  (que confirmamos no indexa audio de forma confiable, ver arriba). Ahora lee directo las
  carpetas reales de WhatsApp con `java.io.File.listFiles()`, mismo patrón que
  `SoncoBot/app/.../WhatsAppWatcher.kt`:
  - `Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images|WhatsApp Video|WhatsApp Voice Notes`
  - `Android/media/com.whatsapp.w4b/WhatsApp Business/Media/...` (WhatsApp Business)
  - `WhatsApp/Media/...` (ruta legacy, versiones viejas de Android/WhatsApp)
  - Sigue filtrando por ventana de tiempo alrededor del `postTime` de la notificación (no
    recorre el histórico) y dedupea por ruta ya vista (`SharedPreferences`, tope 200) — mismo
    diseño de siempre, solo cambió CÓMO se busca el archivo, no cuándo ni cuánto.
  - `looksLikeNewMedia()` (las palabras clave "foto"/"audio"/"mensaje de voz"/etc.) se mantiene
    igual — sigue decidiendo CUÁNDO vale la pena escanear las carpetas (evita escanear en cada
    notificación de WhatsApp, la mayoría texto normal).
  - `MediaMatch` cambió de `uri: Uri` (content://) a `path: String` (ruta real de archivo) —
    ajustado el único call site en `WalletNotificationListener.onNotificationPosted`.
- ✅ **Permiso nuevo: `MANAGE_EXTERNAL_STORAGE`** ("Acceso a todos los archivos",
  `AndroidManifest.xml`, con `tools:ignore="ScopedStorage"` igual que SoncoBot) — mucho más
  fuerte que los `READ_MEDIA_*` anteriores (que se dejan igual, como respaldo en Android <11).
  No se otorga con un diálogo normal: `hasMediaPermission()` ahora chequea
  `Environment.isExternalStorageManager()` en Android 11+ (`Build.VERSION_CODES.R`), y el botón
  "Habilitar" de la fila de permisos en Configuración manda a
  `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` (pantalla especial de Ajustes) en
  vez del selector de permisos de siempre — en Android <11 sigue usando el flujo viejo
  (`requestMediaPermissions()`).
  ⚠️ **Impacto en todas las sucursales, no solo en el teléfono del usuario:** cualquier
  teléfono que actualice a 2.23+ va a ver esta pantalla de "Acceso a todos los archivos" al
  intentar habilitar el permiso — es más llamativa que los permisos anteriores. El usuario ya
  aceptó este trade-off explícitamente (2026-09-24) a cambio de que fotos/video/audio de
  WhatsApp se detecten de forma confiable, los tres por igual.
- **Sin probar en el teléfono todavía** — ni el permiso nuevo (la pantalla especial de Ajustes,
  el flujo `isExternalStorageManager()`), ni que la detección de audio ahora sí encuentre el
  archivo real. Cuando se pruebe, sería bueno confirmar con un log igual de claro al que ya
  sirvió para diagnosticar esto la vez pasada (mandar una foto y un audio casi al mismo tiempo,
  revisar que ambos salgan como "Medio nuevo de WhatsApp" en el log).

### Tanda v2.22 (2026-09-24) — `/panelon`/`/paneloff` faltaban en el `/help`

Pedido del usuario tras notar que el `/help` del bot no mencionaba los comandos nuevos del
panel web. `BotTexts.kt` ganó `CMD_PANEL_ON = "panelon"`/`CMD_PANEL_OFF = "paneloff"`
(**solo para texto** — la app NO los procesa, los sigue escuchando exclusivamente
`csv_importer.py` en la PC) y se agregaron dos líneas al `help()`. **Importante:** estos dos
nombres viven duplicados a mano en dos lenguajes (`BotTexts.kt` en Kotlin y las constantes
`CMD_PANEL_ON`/`CMD_PANEL_OFF` al principio de `csv_importer.py` en Python) — no hay una sola
fuente de verdad entre la app y el script de la PC; si alguna vez se renombran, hay que
cambiarlos en los dos lados (ambos archivos ya tienen un comentario apuntando al otro).

### Tanda v2.21 (2026-09-24) — bug real: el bot dejó de responder comandos ~13h, sin crashear

🐛→✅ **Reportado por el usuario:** "presioné /help en el bot pero no responde". Diagnóstico
hecho leyendo directamente los `getUpdates` pendientes en Telegram (solo lectura, `offset=0`,
sin confirmar nada — no interfiere con nadie) y comparando contra `processed_update_ids` del
teléfono (`shared_prefs/telegram_config_v1.xml` vía `run-as`):
- El teléfono NO había marcado ningún update como procesado desde las **23:40 del 9/23** —
  justo antes de actualizar a v2.19 (`lastUpdateTime=2026-09-24 00:12:29`, ~32 min después).
  Cuatro mensajes de HOY (`/menu`, `/start`, `/help`, `/help`, 13:24) seguían sin leerse, casi
  10 minutos después de enviados.
- `dumpsys activity services` mostró que el proceso de la app NO se había reiniciado ni
  crasheado en esas ~13h (`createTime` estable, coincide con la hora de instalación) — es decir,
  el bucle de long-polling de `WalletNotificationListener.telegramLongPollLoop()` se congeló
  silenciosamente en una llamada de red (`TelegramClient.getUpdates`) que nunca volvió ni hizo
  timeout, sin tumbar la app ni el servicio. Al no haber ninguna protección adicional, ese loop
  se queda ahí para siempre — la única forma de destrabarlo era forzar el cierre de la app.
- **Se descartó como causa** el nuevo polling de `/panelon`/`/paneloff` agregado hoy en
  `csv_importer.py` (PC): la congelación empezó ~13h antes de que ese código existiera, así
  que no es la causa — pero confirma que hacer `getUpdates` desde varios lugares a la vez
  (teléfonos + ahora la PC) no rompe nada, ya que Telegram no devolvió ningún error de
  conflicto en la consulta de diagnóstico.
- ✅ **Arreglo de fondo:** `WalletNotificationListener.telegramLongPollLoop()` y
  `TelegramSyncWorker.processIncomingCommands()` ahora envuelven la llamada a `getUpdates` en
  `withTimeout(...)` (40s y 20s respectivamente) además de los timeouts propios de
  `HttpURLConnection` (que evidentemente no bastan solos en algún escenario de red raro —
  posible fallo de resolución DNS colgada, un problema conocido de Java/Android que
  `connectTimeout` no siempre cubre). `withTimeout` no puede interrumpir la llamada bloqueante
  en sí (no es una función `suspend` real), pero sí evita que el LOOP se quede esperando para
  siempre — si se agota, se descarta el resultado de esa vuelta y se reintenta en la próxima,
  autorrecuperable sin depender de que el usuario reinicie la app.
- **Arreglo inmediato para el usuario mientras tanto:** forzar el cierre de la app y volver a
  abrirla — eso reinicia el loop y procesa los comandos pendientes casi al instante.
- **Sin confirmar todavía si esto se repite tras el fix** — es la primera vez que se documenta
  este bug; si vuelve a colgarse con `withTimeout` puesto, hay que investigar más a fondo (ej.
  loguear explícitamente cuando se agota el timeout, algo que hoy no hace).

### Tanda v2.20 (2026-09-24) — Papelera de notificaciones + colores de Configuración + botón Volver

Pedido explícito del usuario: (1) que "eliminar" en el Historial NO borre de una vez, sino que
mande a una Papelera desde la que se pueda restaurar 1/varias/todas, o vaciarla; (2) los
botones de Configuración que salían café (sin el `AccentBlue` de tandas anteriores) pasan a
azul; (3) el botón "Volver" pasa a verde con letra blanca.

- ✅ **Papelera** (`WalletNotificationStore.kt`): se reemplazaron los antiguos `remove`/
  `removeAll`/`clearHistory` (borrado permanente, sin confirmación real de recuperación) por
  `moveToTrash`/`moveManyToTrash`/`moveAllToTrash` (mueven de `HISTORY` a una lista nueva
  `TRASH` en `SharedPreferences`, limpiando también `PENDING`/`PINNED` igual que antes) y
  `trash()`/`restore()`/`restoreMany()`/`restoreEverything()`/`emptyTrash()` para el camino de
  vuelta. `restore*` reinserta en `HISTORY` y reordena por `receivedAt` (no simplemente
  antepone, para no adelantar una notificación vieja restaurada por delante de otras más
  nuevas). **Nueva pantalla `TrashScreen`** en `MainActivity.kt` (accesible desde un botón
  "🗑️ Papelera (n)" en Home, junto a "Vaciar historial"): mismo patrón de selección múltiple
  que el Historial (casillas + "Restaurar seleccionadas"), más "Restaurar todas" y "Vaciar
  papelera" (esta última SÍ con confirmación, porque ahí sí es borrado permanente). Los textos
  de los diálogos de confirmación de "Eliminar seleccionadas"/"Vaciar historial" se
  reescribieron para decir "se moverán a la Papelera" en vez de "no se puede deshacer" (porque
  ya no es cierto — ahora sí se puede deshacer, restaurando).
- ✅ **Botones café → azul en Configuración**: se detectaron los `Button()` de
  `SettingsScreen` que NO tenían `colors = blue` (quedaban con el color por defecto de Material
  You, café/crema según el fondo del usuario — mismo problema ya resuelto para otros botones en
  v2.13): "Buscar actualización"/"Buscando...", "Actualizar ahora", "Compartir Aplicación",
  "Agregar Billetera", "Agregar Aplicación", "Guardar y activar". La variable `val blue =
  ButtonDefaults.buttonColors(containerColor = AccentBlue)` se subió al principio de
  `SettingsScreen` (antes se declaraba a mitad de la función, después de los botones que la
  necesitaban antes) para poder reusarla en todos.
- ✅ **Botón "Volver" → verde con texto blanco**: nuevo composable `BackButton()` en
  `MainActivity.kt` (`Button` con `AccentGreen` de fondo y `Color.White` de texto), reemplaza el
  `TextButton` plano que había en `InstalledAppsScreen`, `ReadScreen` y `SettingsScreen` (y se
  usa también en la nueva `TrashScreen`). `AccentGreen = Color(0xFF188038)` nuevo en
  `ui/theme/Color.kt` (mismo verde que ya se usaba suelto para "Activado"/estado del servicio,
  ahora con nombre).
- **Sin probar en el teléfono todavía** — toda esta tanda es nueva. Compila limpio, versión
  subida a `versionCode=2020`/`"2.20"`, build Interna generada
  (`Releases/miSecretariaV2.20-debug_Interna.apk`).

### Colector de CSV a `miSecretaria.db` (2026-09-24, fuera de la app Android)

Pedido del usuario: acumular en una base de datos local, dentro de la carpeta del proyecto, los
CSV que cada sucursal manda por Telegram (ver `TelegramSyncWorker.sendPendingCsv`) — hoy esos
CSV solo se ven sueltos en el chat de Telegram del usuario, sin quedar juntos en ningún lado.

- **Por qué no se reutiliza el mecanismo de comandos (`getUpdates`) para esto:** los CSV los
  manda el propio bot (`sendDocument`) al chat del usuario — son mensajes SALIENTES del bot, y
  `getUpdates` de la API de bots solo entrega mensajes ENTRANTES (lo que el usuario le escribe
  al bot). Por diseño de Telegram, un bot no puede "leerse a sí mismo" los archivos que ya
  envió. Se evaluaron 4 alternativas (Telethon con la cuenta personal, Firestore como
  intermediario, un receptor HTTP propio con túnel, y Telegram Desktop con descarga automática
  + carpeta vigilada) — el usuario eligió la última (**Opción C**) porque ya tenía **Telegram
  Desktop 7.2.9** corriendo en este Debian (`/home/beelinkser5max/Telegram/Telegram`, sesión
  propia) y confirmado que YA descarga solo los CSV a
  `~/Descargas/Telegram Desktop/` — no hizo falta configurar nada de Telegram, solo escribir el
  importador.
- ✅ **`csv_importer.py`** (nuevo, en la raíz del proyecto, fuera de `app/` — es una herramienta
  de escritorio, no parte de la app Android): vigila `~/Descargas/Telegram Desktop/` cada 30s,
  valida que el encabezado del CSV sea exactamente `Sucursal,Fecha,Origen,Tipo,Mensaje` (para no
  tragarse por error un CSV de otro chat que caiga en la misma carpeta de descargas), e inserta
  cada fila en la tabla `notificaciones` de `miSecretaria.db` (SQLite, en la raíz del proyecto,
  **no se sube a git** — agregado a `.gitignore`). Cada archivo ya importado se registra en la
  tabla `archivos_importados` (clave = nombre de archivo) para no duplicar si Telegram lo vuelve
  a tocar. Sin dependencias nuevas — solo librería estándar de Python (`sqlite3`, `csv`).
- ✅ **Sin systemd ni cron, a propósito** — mismo criterio que el usuario ya fijó explícitamente
  para su otro proyecto (`CotizacionDelDolar`, ver su `CLAUDE.md`: *"no quiere auto-arranque ni
  auto-reinicio de ningún tipo"*). Se inicia a mano con **`miSecretaria_ImportarCSV.desktop`**
  (doble clic → terminal, mismo patrón que `miSecretaria_Update.desktop`) y se detiene cerrando
  la ventana o con Ctrl+C. Queda corriendo en primer plano mientras el usuario lo quiera activo.
- ✅ **Probado en vivo (2026-09-24):** una corrida real importó correctamente las 9 CSV que ya
  estaban en la carpeta de descargas → 23 filas reales en `notificaciones` (pagos y mensajes de
  "Localizador"/WhatsApp de la sucursal `OSC_TecnoPovaNeo2`). Confirmado con
  `sqlite3 miSecretaria.db "SELECT ..."`.
- ✅ **`miSecretaria.log` (2026-09-24):** logging real vía el módulo `logging` de Python (no
  `print()`) — `RotatingFileHandler` en modo `DEBUG` (2 MB × 3 respaldos, no crece sin límite,
  a diferencia de `miSecretaria.db`). En `.gitignore` (`/miSecretaria.log*`, cubre también las
  rotaciones `.log.1`/`.log.2`/`.log.3`).
- ✅ **Panel en vivo con `rich` (2026-09-24, pedido explícito del usuario: "más estético, más
  pro, con métricas en tiempo real"):** la consola ya no es texto que va scrolleando — es un
  panel (`rich.live.Live`, se redibuja en el mismo lugar) con: rutas vigiladas, cuenta regresiva
  hasta la próxima revisión, contadores en vivo (archivos importados, filas importadas, último
  archivo, errores, tiempo en marcha) y una sub-caja "Últimos eventos" con las últimas 8 líneas.
  Dependencia nueva: `rich` (ya estaba instalada globalmente en esta máquina, `pip3 list` la
  confirma; se agregó `requirements.txt` con `rich` para que quede documentada). **Garantía
  importante:** el panel de eventos se alimenta de un `logging.Handler` (`PanelHandler`) que
  recibe EXACTAMENTE los mismos registros que ya van al archivo — no hay un texto "solo en
  pantalla" que no haya pasado por `miSecretaria.log`; la única diferencia es de nivel (el panel
  filtra a INFO+, el archivo se queda con todo, incluido DEBUG). Los mensajes de inicio con
  rutas completas se bajaron a DEBUG a propósito porque rompían el ancho fijo del panel de
  eventos (quedan en el archivo igual, solo no se ven en pantalla — la tabla de métricas ya
  muestra esas rutas de forma resumida). Probado en una pseudo-terminal real (`script`): el
  panel se ve limpio, sin envolver líneas, con colores (verde=INFO, amarillo=WARNING,
  rojo=ERROR).
- **Pendiente/decisiones futuras, no resueltas todavía:** (1) `miSecretaria.db` no tiene límite
  de crecimiento ni purga — igual que `CotizacionDelDolar.db`, se deja así a propósito salvo que
  el usuario pida lo contrario; (2) no hay backup automático de esta base (ver el patrón
  `sqlite3 ".backup"` de `CotizacionDelDolar/Backup_Proyecto.sh` si se quiere algo similar más
  adelante); (3) no se filtra ni deduplica por contenido, solo por nombre de archivo — si algún
  día una sucursal reenvía manualmente un CSV viejo con otro nombre, se importaría de nuevo.

### Tanda v2.19 (2026-09-24) — textos centralizados (renombrado seguro) + gestión de historial (borrar/fijar/copiar/nota)

Pedido explícito del usuario: (1) poder cambiar CUALQUIER texto visible de la app o del bot
(títulos, botones, ayuda de Telegram, incluso el nombre de un comando como `/renombrar`) editando
un solo lugar, sin arriesgar romper la lógica; (2) poder eliminar 1, varias o todas las
notificaciones del historial; (3) copiar el texto de una notificación al portapapeles; (4) fijar
("pinear") hasta 2 notificaciones para que queden siempre arriba; (5) poder añadirle una nota
personal a cualquier notificación.

- ✅ **Centralización de texto — arquitectura nueva, dos piezas:**
  - `app/src/main/res/values/strings.xml` (nuevo): TODO el texto visible de la UI (Compose) —
    `MainActivity.kt` completo, y también `WalletNotificationNotifier.kt`/`WalletOverlay.kt`/
    `PaymentAlertActivity.kt` (que antes repetían "Pago recibido: X"/"Repetir"/"OK" cada uno por
    su lado) — ahora leen el mismo string (`R.string.notif_title_payment`, etc.). Es el mecanismo
    NATIVO de Android para esto: cambiar una palabra es editar un `<string>` en este XML y
    recompilar, sin tocar ningún `.kt`. Los textos con partes variables usan `%1$s`/`%1$d`
    (ej. `history_title_filtered` = "Historial (%1$d de %2$d)").
  - `BotTexts.kt` (nuevo): todo el texto Y los nombres de comando del bot de Telegram
    (`/notificar`, `/notificarpantalla`, `/renombrar`, `/help`, `/start`) como constantes
    (`CMD_NOTIFY`, `CMD_RENAME`, etc.) y funciones que arman los mensajes (`help()`,
    `notifyUsageError()`, `renamed()`). `TelegramCommandHandler.kt` ya NO tiene ningún texto ni
    nombre de comando hardcodeado — construye sus `Regex` a partir de `BotTexts.CMD_*`. Cambiar
    `/renombrar` por `/rebautizar`, por ejemplo, es editar una sola constante en `BotTexts.kt`
    (`CMD_RENAME = "rebautizar"`) y el `/help` y la detección del comando quedan consistentes
    automáticamente — no hace falta tocar `TelegramCommandHandler.kt`.
  - No se centralizaron los mensajes internos de `ScoSecretariaLogger` (log de depuración) — son
    diagnóstico técnico, no texto de marca/UX, y son decenas repartidos en muchos archivos; fuera
    de alcance de este pedido.
- ✅ **Borrar notificaciones del historial** (`HomeScreen`): cada tarjeta tiene ahora un botón
  "Eliminar" (borrado inmediato, sin confirmación — mismo criterio que "Quitar" en
  billeteras/apps). Para varias a la vez: botón "Seleccionar" arriba del historial activa
  casillas de verificación por tarjeta; con 1+ seleccionadas aparece "Eliminar seleccionadas (n)"
  (SÍ pide confirmación, por ser una acción sobre varios elementos a la vez). Para todo: botón
  "Vaciar historial" (visible si hay algo en el historial) con confirmación explícita antes de
  borrar. `WalletNotificationStore.remove(id)`/`removeAll(ids)` (nuevas) también limpian el
  registro si estaba en `pending` o `pinned`, para no dejar referencias colgando a una
  notificación que ya no existe.
- ✅ **Copiar al portapapeles**: botón "📋 Copiar" en cada tarjeta — copia `item.message`
  (el texto de la notificación) vía `ClipboardManager` nativo de Android, con un Toast de
  confirmación. Sin permisos nuevos, no toca la nube.
- ✅ **Fijar hasta 2 notificaciones** ("pin"): botón "📌 Fijar"/"📌 Quitar fijado" por tarjeta.
  `WalletNotificationStore.pinnedIds()`/`togglePin(id)` (nuevas) guardan un set de ids en
  `SharedPreferences` (`MAX_PINNED = 2`, constante fácil de subir si el usuario pide más).
  `togglePin` devuelve `PinToggleResult.LIMIT_REACHED` si ya hay 2 y se intenta fijar una
  tercera — la UI muestra un Toast avisando en vez de fallar en silencio. Las notificaciones
  fijadas se muestran siempre primero en el historial (con un 📌 antes del nombre de billetera),
  sin importar el filtro de billetera/app activo.
- ✅ **Nota personal por notificación**: campo `note: String?` nuevo en `WalletNotification`
  (persistido en el JSON de `WalletNotificationStore`, con `optString`/`?:JSONObject.NULL` igual
  que `mediaPath`, así que backups/históricos viejos sin ese campo cargan igual con `note=null`).
  Botón "📝 Nota" por tarjeta abre un campo de texto inline (Guardar/Quitar/Cancelar) vía
  `WalletNotificationStore.setNote(id, texto)`. La nota es SOLO local — no se envía a Telegram
  ni se lee en voz alta, es un recordatorio personal (ej. "ya facturé esto").
- **Sin probar en el teléfono todavía** — toda esta tanda es nueva, ninguna parte se ha visto en
  vivo (ni el borrado, ni el pin, ni copiar, ni la nota, ni que los textos movidos a `strings.xml`
  se vean igual que antes). Compila limpio (`./gradlew assembleDebug`) y build Interna 2.19
  generada (`Releases/miSecretariaV2.19-debug_Interna.apk`).

### Tanda v2.18 (2026-09-23, noche) — `/notificarpantalla` no mostraba nada en pantalla: bug real, confirmado en log

- 🐛→✅ **Confirmado en el log real del teléfono** (2.17 instalada y probada):
  ```
  [INFO] Aviso remoto (audio + pantalla) recibido vía Telegram
  [ERROR] No se pudo mostrar aviso superpuesto Can't create handler inside thread
          Thread[DefaultDispatcher-worker-2,5,main] that has not called Looper.prepare()
  ```
  Causa: `WalletOverlay.show()` crea `View`s de Android (`LinearLayout`/`TextView`/`Button`) y
  llama `WindowManager.addView()` — eso SOLO se puede hacer en el hilo principal (el único con
  `Looper` preparado). Antes de v2.16 esto nunca fallaba porque `WalletOverlay.show()` siempre
  se llamaba desde `onNotificationPosted` (el framework de Android lo invoca en el hilo
  principal). Desde que `/notificarpantalla` llega por el long-polling de Telegram
  (`Dispatchers.IO`, un hilo de fondo), la llamada directa reventaba en silencio (solo logueaba
  el error, no crasheaba la app) y el aviso nunca aparecía — ni la pantalla completa
  (`fullScreenEnabled`) ni el aviso flotante, exactamente lo que reportó el usuario. El usuario
  tenía "Pantalla de aviso" activado (no "Pantalla completa"), así que cayó en la rama que
  usa `WalletOverlay`, la que fallaba.
- ✅ **Corregido:** `WalletOverlay.show()`/`remove()` ahora revisan en qué hilo están
  (`Looper.myLooper() == Looper.getMainLooper()`) y si no es el principal, saltan ahí con
  `Handler(Looper.getMainLooper()).post { ... }` antes de tocar cualquier `View`. Funciona
  igual sin importar desde qué hilo se llame (notificación normal en el hilo principal, o
  `/notificarpantalla` desde el polling en un hilo de fondo).
- **Lección para el futuro:** cualquier código nuevo que dispare UI (`WalletOverlay`,
  `PaymentAlertActivity`, cualquier `View`/`WindowManager` directo) debe asumir que puede ser
  llamado desde un hilo de fondo ahora que existe el long-polling de Telegram — no asumir que
  siempre viene de `onNotificationPosted`.
- **Sin confirmar todavía si `/notificarpantalla` ya funciona de punta a punta** — el fix
  corrige la causa exacta del error de log, pero falta que el usuario lo pruebe en el teléfono
  tras instalar 2.18.

### Tanda v2.17 (2026-09-23, tarde) — race condition del dedupe + `/notificarpantalla`

- 🐛→✅ **Causa real de "el /notificar se leyó dos veces":** `isUpdateProcessed`/
  `markUpdateProcessed` eran DOS pasos separados (no atómicos). Desde 2.16 hay DOS
  consumidores del mismo lote de `updates` corriendo en paralelo — el long-polling en tiempo
  real (`WalletNotificationListener`) y el respaldo periódico (`TelegramSyncWorker`) — así que
  ambos podían "ver" el mismo `update_id` como no-procesado casi al mismo tiempo y ejecutar el
  comando dos veces. Corregido: `TelegramConfig.markUpdateIfNew` (un solo método `@Synchronized`,
  devuelve `true` solo para quien lo marca primero) reemplaza a los dos anteriores. (No se
  pudo confirmar 100% viendo el log del teléfono — el spam de OSMAnd, "Sin coincidencia" cada
  ~6s durante viajes, rota el log de depuración en menos de 1h30 y se comió el rastro del
  evento real — pero la causa por diseño es sólida y el fix es correcto de todos modos.)
- ✅ **`/notificar` ahora es SOLO AUDIO** (pedido explícito del usuario) — ya no llama a
  `WalletNotificationNotifier.show`/`WalletOverlay`, solo lee el mensaje y lo guarda en el
  historial.
- ✅ **Nuevo comando `/notificarpantalla TODOS|<sucursal> <mensaje>` = AUDIO + PANTALLA**:
  igual que `/notificar` pero además dispara pantalla completa (si "Pantalla completa" está
  activado) o aviso flotante (si "Pantalla de aviso" está activado) — mismo mecanismo que un
  pago recibido, pero SIN el encabezado "Pago recibido" ni un monto inventado (antes
  `PaymentAlertActivity`/`WalletOverlay` mostraban eso siempre, sin importar el tipo).
  - Nuevo valor de enum `NotificationKind.ALERT` (además de `PAYMENT`/`GENERAL`) — activa el
    fullscreen/overlay igual que `PAYMENT`, pero con el header genérico (solo `item.wallet`,
    sin "Pago recibido:" ni monto).
  - `PaymentAlertActivity` ahora recibe `EXTRA_KIND` en el Intent y decide qué encabezado
    mostrar.
  - Los prefijos de comando se revisan con límite de palabra (`^/notificar\b` vs.
    `^/notificarpantalla\b`) para que no se confundan entre sí (`/notificarpantalla` SÍ
    empieza con el texto "/notificar", pero no con espacio inmediatamente después).
- ✅ **Probado en el teléfono (2026-09-23, noche):** el audio de `/notificarpantalla` sonó
  bien (ver log: "Aviso remoto (audio + pantalla) recibido vía Telegram", dos veces para dos
  pruebas distintas — sin señales de doble-lectura del mismo mensaje, el fix del dedupe parece
  estar funcionando). ❌ La parte de PANTALLA no apareció — bug real encontrado y corregido en
  v2.18, ver esa sección.

### Tanda v2.15/v2.16 (2026-09-23, tarde) — comandos de Telegram: diagnóstico de uso + tiempo real

- 🩺 **Diagnóstico: `/notificar TODOS` "no hace nada".** El usuario mandó `/notificar TODOS` y
  el mensaje ("Hola mundo") como DOS mensajes de Telegram separados. El regex
  (`^/notificar\s+(\S+)\s+(.+)$`) exige todo en un solo mensaje — si no calza, antes se
  ignoraba en silencio. Confirmado leyendo el backlog real de `getUpdates` contra
  `processed_update_ids`.
- ✅ **v2.15 — el bot ya responde si el formato está mal**, en vez de quedarse callado:
  `handleNotifyCommand`/`handleRenameCommand` ahora revisan si el texto empieza con
  `/notificar`/`/renombrar` y, si no matchea el patrón completo, contestan con la sintaxis
  correcta y un ejemplo. El `/help` también se reescribió con ejemplos concretos y una
  advertencia en mayúsculas sobre mandar todo junto.
- ✅ **v2.16 — comandos de Telegram en (casi) tiempo real, ya no hay que esperar el intervalo
  ni tocar "Sincronizar ahora"** (pedido explícito del usuario). Rediseño:
  - Lógica de comandos extraída a `TelegramCommandHandler.kt` (objeto compartido, recibe
    `context` explícito) — antes vivía dentro de `TelegramSyncWorker`.
  - `TelegramClient.getUpdates` ahora acepta `timeoutSeconds` (long-polling real de Telegram:
    la conexión queda abierta hasta 25s esperando un mensaje nuevo, en vez de responder al
    toque). `readTimeout` del cliente HTTP se ajusta con margen (`timeoutSeconds + 10`) para no
    cortar la conexión antes de que Telegram conteste.
  - `WalletNotificationListener` (el servicio que YA corre siempre mientras hay permiso de
    notificaciones) ahora tiene un `CoroutineScope` propio y lanza un loop infinito
    (`telegramLongPollLoop`) en `onCreate()`, cancelado en `onDestroy()`: pide `getUpdates` con
    `timeout=25s`, procesa lo que llegue con `TelegramCommandHandler`, y repite. Si Telegram no
    está configurado, espera 30s entre intentos sin martillar la red.
  - `TelegramSyncWorker` (el trabajo periódico, cada 30 min por defecto) queda como
    **respaldo**: sigue revisando comandos además de mandar el CSV, por si el loop en tiempo
    real se corta (ej. Android mata el servicio). Mismo dedupe
    (`TelegramConfig.isUpdateProcessed`) para los dos caminos, así que no hay riesgo de
    procesar un comando dos veces.
  - **Sin probar en el teléfono todavía** — falta confirmar que el loop responde de verdad casi
    al instante, y que no se corta solo tras un rato con la pantalla apagada (este Tecno no
    está en la whitelist de batería, aunque el `WorkManager` sí lo estaba — un
    `CoroutineScope` dentro de un servicio normal podría comportarse distinto, hay que
    verificar en vivo).
- ✅ **"Aviso remoto" ya no se anuncia en voz alta al leer un `/notificar`** (pedido explícito
  del usuario). Antes `SpeechEngine.speak(item)` anteponía "Aviso remoto, " (mismo prefijo que
  usan las apps generales tipo WhatsApp). Ahora se usa `SpeechEngine.speakText(context,
  message)` — lee el mensaje tal cual, como en la pantalla "Leer". El historial SÍ sigue
  guardando/mostrando "Aviso remoto" como categoría (no se tocó esa parte, la duda era sobre lo
  que se lee en voz alta).

### Tanda v2.13 (pedida por el usuario 2026-09-23) — instalada y probada en el teléfono

El usuario instaló 2.13 (no quedó claro si vía "Buscar actualización" o a mano; `lastUpdateTime`
del paquete confirma 2026-09-23 13:26) y **borró los datos de la app para empezar de cero**.
Verificado EN VIVO tras eso:
- ✅ Botones azules, PIN de administrador y `/help` confirmados en pantalla (captura de
  Configuración con "Compartir Log" en azul y las 6 billeteras con "Quitar").
- ✅ **El bot de Telegram SÍ funciona** (independientemente del bug de abajo). El usuario
  reportó "solo un momento funcionó" — diagnóstico (comparando `getUpdates` del bot vs.
  `processed_update_ids` guardados en el teléfono): NO es un bug de conectividad. El trabajo
  periódico de WorkManager está bien agendado (`dumpsys jobscheduler`: `Doze whitelisted:
  true`, sin restricciones — la preocupación anterior sobre batería en este Tecno no aplicó) y
  procesó correctamente varios `/start`/`/help` reales del usuario entre las 12:42 y las 13:30.
  Sus últimos 4 mensajes (13:32-13:34) quedaron pendientes simplemente porque el siguiente
  ciclo automático (intervalo 30 min) todavía no llegaba — de ahí la sensación de "se cortó".
- 🐛→✅ **Bug real encontrado (2026-09-23, tarde): "Sincronizar ahora" NO se veía.** El usuario
  mandó captura de Configuración → Telegram mostrando solo "Guardar y activar" y "Enviar
  mensaje de prueba" — el botón nuevo no estaba. Causa: se agregó como TERCER botón a un `Row`
  que ya no entraba en el ancho de pantalla (labels largos) — Compose no envuelve `Row` por
  defecto, así que quedaba recortado fuera de vista, invisible e inalcanzable (no era un
  problema de que "faltara" el botón: se publicó igual en el commit "Release v2.13", pero
  nadie podía verlo ni tocarlo). Corregido en `MainActivity.kt`: el primer `Row`
  (Guardar/Enviar) ahora tiene `horizontalScroll` de respaldo, y "Sincronizar ahora" pasó a su
  propia línea debajo, siempre visible. **Requirió subir a `versionCode=2014`/`"2.14"`** — el
  fix anterior (agregar el botón) se había hecho SIN subir versión, así que quedó indistinguible
  del build ya publicado y el usuario nunca pudo recibirlo por su cuenta.
- ✅ **`WalletConfig.defaults` actualizado con paquetes reales** (2026-09-23, tarde): el usuario
  re-agregó las 6 billeteras desde el selector de apps tras borrar datos, revelando los
  paquetes reales de su teléfono — ya no hace falta adivinar ni dejar vacío:
  `ZAS→bec.vdb.direct`, `Yasta→com.busa.wallet`, `Yape→com.bcp.bo.wallet`,
  `altoke→com.bancosol.altoke`, `Bille→com.walletapp.mobile`, `Yolo Pago→bo.com.yolopago`.
  Reemplaza los defaults viejos (YAPE/YASTA/AlToke con `packageId=""`, nunca detectaban nada).
  Solo afecta instalaciones NUEVAS sin config guardada — el teléfono del usuario ya tiene estas
  mismas 6 guardadas a mano, no por los defaults.
- ✅ Botones "Configuración"/"Leer" en Home, y "Compartir Historial/CSV/Log" en Configuración
  → ahora en azul (`AccentBlue` en `ui/theme/Color.kt`). Antes salían café/crema porque
  `ScoSecretariaTheme` usa `dynamicColor = true` (Material You, colores del wallpaper del
  usuario en Android 12+) — se sobreescribió el color de esos botones puntuales, no el tema
  entero.
- ✅ Se quitaron los botones "Guardar Historial"/"Guardar CSV"/"Guardar Log" (redundantes con
  "Compartir") — ahora solo queda un botón "Compartir" por cada uno. La función `save()` de
  `MainActivity` sigue existiendo (la sigue usando "Guardar Backup").
- ✅ `WalletConfig.defaults`: se quitó la entrada muerta `WalletRule("YOLO", "", true)` — ya
  cubierta por "Yolo Pago" (agregada por el usuario desde el selector de apps, con paquete
  real). Solo afecta instalaciones NUEVAS/sin config guardada; en el teléfono del usuario, si
  el "YOLO" viejo sigue en su lista, lo puede quitar con el botón "Quitar" (ya en v2.12).
- ✅ **PIN de administrador** (`AdminAccess.kt`, PIN fijo `230985`, sin UI para cambiarlo por
  ahora): en Home, tocar 3 veces seguidas el logo (ícono `ic_launcher_foreground`) junto al
  título abre un diálogo de PIN. Correcto → `adminUnlocked=true` para el resto de la sesión
  (no se persiste; se resetea al reabrir la app). En Configuración, con `adminUnlocked=false`
  el Token y Chat ID de Telegram se muestran enmascarados ("•••• configurado" /
  "no configurado") y NO son editables; con `true` se ven los campos normales de siempre.
  El nombre de sucursal, intervalo y botones de guardar/probar quedan visibles siempre (no son
  secretos). Estado se maneja en `ScoSecretariaApp` (arriba de `HomeScreen`/`SettingsScreen`).
- ⚠️→✅ **Pedido inicial rechazado por seguridad, resuelto con una build separada.** El usuario
  pidió dejar SU token real y Chat ID como valor por defecto ya rellenado en el código. Se
  rechazó hacerlo en el build PÚBLICO: ese código se compila en el APK que se instala en CADA
  sucursal y se distribuye públicamente (GitHub Releases + actualización in-app) — cualquiera
  que decompile el APK vería el token en texto plano. En su lugar, a propuesta del usuario, se
  implementó una **build "Interna" separada** (`build_interna.sh`):
  - `secrets.properties` (nuevo, en `.gitignore`, NUNCA se commitea) guarda `botToken`/`chatId`
    reales en texto plano, solo en el Debian del usuario. Ya se creó con sus datos reales.
  - `app/build.gradle.kts`: lee ese archivo y define `BuildConfig.DEFAULT_BOT_TOKEN`/
    `DEFAULT_CHAT_ID`, pero **solo si se compila con `-PincludeSecrets=true`** — el build
    normal (`assembleDebug` sin flags, que es lo que usa `release.sh`) siempre los deja vacíos,
    sin importar si `secrets.properties` existe en disco. Verificado con builds de prueba
    alternando el flag: sin fuga de caché entre uno y otro.
  - `TelegramConfig.botToken()`/`chatId()`: si el usuario no guardó nada en SharedPreferences,
    caen a `BuildConfig.DEFAULT_BOT_TOKEN`/`DEFAULT_CHAT_ID` (vacíos en el build público).
  - `build_interna.sh`: corre `./gradlew assembleDebug -PincludeSecrets=true` y copia el
    resultado a `Releases/miSecretariaV(x.x)-debug_Interna.apk` (probado, genera el APK
    correctamente). Ese archivo **nunca se sube a GitHub Releases ni a Firebase** — el usuario
    lo pasa a mano (USB/Bluetooth) a los teléfonos de sucursal.
  - `secrets.properties.example` (sí commiteado): plantilla vacía para que una sesión futura
    sepa el formato sin exponer nada.
  Complementario: **Backup/Restauración también incluye el token+Chat ID+intervalo**
  (`BackupManager.exportJson`/`importJson`, sección `"telegram"` — a propósito NO incluye el
  nombre de sucursal, que debe quedar distinto por teléfono) — sirve para actualizar el token
  en una instalación YA existente sin recompilar. El usuario puede configurar Telegram UNA
  vez en su teléfono maestro (detrás del PIN), exporta un Backup, y restaura ese mismo archivo
  en cada teléfono de sucursal — el secreto nunca toca el repo ni el código fuente. ⚠️ El
  archivo de backup exportado ahora contiene el token — tratarlo como una contraseña (no
  compartirlo por canales inseguros).
- ✅ **Comando `/help` (y `/start`) en el bot de Telegram** (`TelegramSyncWorker.handleHelpCommand`):
  responde con la lista de comandos (`/notificar`, `/renombrar`, `/help`) y el nombre de la
  sucursal que contesta. ⚠️ Responde CADA dispositivo que comparte el bot (no hay "un solo
  respondedor" sin servidor propio) — con pocas sucursales es aceptable, revisar si escala mal
  con muchas.
- ✅ **Botón "Sincronizar ahora"** en Configuración → Telegram: llama a
  `TelegramSyncWorker.runOnce(context)` (ya existía en el código, nunca se llamaba desde
  ningún lado — se agregó el botón que faltaba). Sirve para probar comandos/CSV al instante,
  sin esperar el intervalo periódico (mínimo 15 min) ni depender de que WorkManager decida
  correr pronto.
- **Diagnóstico: "el bot no responde ni /help ni nada" (reportado por el usuario 2026-09-23).**
  Antes de asumir un bug, dos causas mucho más probables en ESTE momento:
  1. El build instalado en el teléfono (de ~11:17, intermedio) es de ANTES de todo lo de hoy —
     no tiene `/help`, ni el rediseño `offset=0`, ni "Sincronizar ahora". Hay que instalar 2.13
     (o la build Interna) para probar cualquiera de estas cosas.
  2. El worker periódico de Telegram depende de WorkManager, y este teléfono (TECNO LG6n,
     Transsion/HiOS) **no está en la whitelist de batería del sistema** (confirmado con
     `adb shell dumpsys deviceidle whitelist`) — el OEM puede demorar o matar el trabajo en
     segundo plano. Recomendar al usuario: Ajustes → Batería → miSecretaria → "Sin
     restricciones", y usar el nuevo botón "Sincronizar ahora" para no depender del scheduler
     mientras se prueba.
  Si tras instalar 2.13, guardar token/Chat ID (o usar la build Interna) y presionar
  "Sincronizar ahora" el bot SIGUE sin responder, ahí sí hay que revisar `TelegramClient`/
  `TelegramSyncWorker` con el log real del teléfono.
- **Sin probar en el teléfono todavía:** el diálogo de PIN, el enmascarado, el botón "Quitar"
  de YOLO, el `/help` del bot, "Sincronizar ahora", y la build Interna — el usuario los verá
  cuando instale 2.13 vía "Buscar actualización" (o la build Interna a mano).

Esta tanda (Paso 1 + Paso 2 fase 1) SÍ se compiló (`./gradlew assembleDebug` exitoso) y se
instaló/probó una vez en el teléfono del usuario (única instalación por ADB, ya no se repetirá).
Verificado EN VIVO vía el log interno del teléfono (`run-as ... cat files/miSecretaria_debug.log`):
- ✅ **Paso 1 confirmado funcionando de verdad:** tras la corrección, el log muestra
  `Notificación aceptada de WhatsApp (GENERAL)` para mensajes reales (11:33:52 y 11:46:05),
  algo que antes de la corrección no pasaba. Hubo una racha de "Sin coincidencia: pkg=com.whatsapp"
  entre las 09:48 y las 11:24 (incluyendo una ya con el código nuevo instalado, a las 11:24:48)
  que dejó de repetirse justo cuando el usuario terminó de editar Billeteras/Apps en
  Configuración — no se pudo aislar una causa de código (la lógica de `detect()` no depende del
  contenido del mensaje cuando la regla tiene `packageId`), así que se trata como un estado
  transitorio ya superado, no como un bug pendiente. El grupo de WhatsApp usado en la prueba
  ("[ 🤖 El Desconocido 🤣 ]") es la SEGUNDA cuenta del usuario dentro de la misma instalación de
  WhatsApp (función nativa de WhatsApp de dos números en una app) — mismo `packageName`
  (`com.whatsapp`), así que la regla los cubre a ambos por igual.
- ✅ **Fila nueva "Acceso a medios" en Configuración probada en pantalla** — se detectó y
  corrigió un bug real de layout: la etiqueta larga no dejaba espacio al botón "Habilitar"
  (ya arreglado con `Modifier.weight(1f)` en el `Text` de `PermissionRow`).
- ⚠️ **Pendiente de probar:** la detección real de medios de WhatsApp (Paso 2, 2a–2c) necesita
  el permiso de medios concedido (no se concedió aún — la app instalada en el teléfono es de
  antes del arreglo de layout) y que llegue una foto/audio/video real por WhatsApp.
- 🔍 **Riesgo del Paso 2 validado por consulta directa a MediaStore (sin tocar la app):**
  fotos y videos de WhatsApp SÍ están indexados y recientes (última foto: hoy 07:09, último
  video: ayer 10:47) — la ruta real en este teléfono es
  `Android/media/com.whatsapp/WhatsApp/accounts/<id>/Media/...` (contiene "WhatsApp" igual, el
  filtro de ruta sigue sirviendo) y también aparece contenido en `Movies/WhatsApp/` para algunos
  videos. ⚠️ El **audio de nota de voz NO tiene entradas recientes** en `MediaStore.Audio` (la
  última real es del 14 de septiembre, 9 días antes de esta prueba) — puede ser que el usuario
  simplemente no haya recibido audios nuevos, o que WhatsApp/este Android no indexe notas de voz
  en el "Audio" de MediaStore de forma confiable. **Falta confirmar mandando una nota de voz de
  prueba ahora mismo y volviendo a consultar MediaStore** — esto decide si vale la pena construir
  2d-2h (cola de reproducción) para audio, o si hay que buscar una vía alternativa solo para ese tipo.
  **✅ CONFIRMADO 2026-09-24 (ver "Tanda v2.22+" / sección de WhatsApp media más abajo): esto
  era exactamente el problema.** El detector de palabra clave funciona perfecto para audio
  (WhatsApp manda "🎤 Mensaje de voz (0:16)", que sí matchea), pero `MediaStore.Audio` no
  indexa el archivo — se confirmó con una foto y un audio llegados casi al mismo segundo: la
  foto se encontró en `MediaStore.Images`, el audio no se encontró en `MediaStore.Audio`. No es
  un bug de código, es una limitación real de Android/WhatsApp con archivos `.opus` — ver "Qué
  hacer con el audio (decisión pendiente)" en la sección de limitaciones conocidas.

| Pedido del usuario | Estado |
|---|---|
| Nombre de sucursal aleatorio si no se personaliza | ✅ en código (`DisplayPreferences.deviceLabel`) |
| Renombrar sucursal desde el bot (`/renombrar`) | ✅ en código (`TelegramSyncWorker`) |
| Mensajes desde el bot a TODOS / sucursal (`/notificar`) | ✅ en código |
| Botón Atrás de Android en Configuración | ✅ `BackHandler` + `enableOnBackInvokedCallback` en manifest (sin probar en equipo) |
| Listas con ícono + nombre + packageId; sin cuadros de texto manuales | ✅ en código (sin probar en equipo) |
| Monto `Bs 2,392.69` mal leído | ✅ código corregido en `AmountSpeech`; sin probar |
| Ícono nuevo `miSecretaria.jpg` | ✅ en código (ítem 27): adaptativo + legacy regenerados sin tablero; falta ver en el teléfono tras compilar |
| YASTA y "Bille" no procesan notificaciones | ✅ resuelto por el usuario: re-agregó las billeteras desde el selector de apps instaladas (ahora con `packageId` real). Quedó un rastro "YOLO" viejo (vacío) duplicado con el nuevo "Yolo Pago" — ver fila siguiente |
| No había forma de borrar una billetera/app mal agregada, solo activar/desactivar | ✅ en código: `WalletConfig.remove`/`AppConfig.remove` + botón "Quitar" junto al switch de cada regla en Configuración (2026-09-23, sin probar en equipo aún) |
| Audio de WhatsApp en cola / video como audio | 🔄 Paso 2 fase 1 (2a-2c) ✅ en código y compilado: permisos de medios + fila en Configuración, detección de notificación de medio nuevo de WhatsApp, búsqueda en `MediaStore` solo de archivos recién agregados (ventana de tiempo, sin recorrer histórico), solo LOGUEA lo encontrado (`WhatsAppMediaScanner.kt`). Falta probar en el teléfono con un medio real y luego 2d-2h (cola de reproducción, copia antes de borrado, reenvío a Telegram, checklist) |
| Guardar copia de medios borrados + reenviar al bot + checklist de tipos | 🔄 **v2.26: la copia (2d) y mostrar/reproducir en el Historial (versión ligera de 2e) YA están en código** (`copyMediaToAppStorage`, `AudioPlayer`, `VideoOpenButton`). **v2.34: el reenvío al bot de Telegram (2f) también YA está en código** (`sendPendingMedia`, mismo ciclo periódico que el CSV) — sin probar en el teléfono todavía. Sigue faltando: la cola de reproducción coordinada con TTS (2e completo) y el checklist de qué tipos guardar/reenviar (2g) |
| Subir versión (2.12) | ✅ hecho en `app/build.gradle.kts` (2026-09-23); falta que el usuario corra el release (`.desktop`/`release.sh`) |
| Limpieza del repo (scripts/archivos huérfanos) | ✅ hecho 2026-09-23: ver "Limpieza del repo" más abajo |
| Configurar bot de Telegram | 🔄 el usuario creó el bot (`t.me/miSecretariaPerfecta_bot`) y obtuvo el token con BotFather; Claude obtuvo el Chat ID (`8159568738`) consultando `getUpdates` una sola vez (uso puntual, no guardado en ningún archivo). **El token NO se guarda en CLAUDE.md/README/repo por seguridad** — el usuario debe pegarlo él mismo en Configuración → Telegram → "Token del bot", junto con el Chat ID. Falta que el usuario guarde y pruebe "Enviar mensaje de prueba" |
| Un solo bot para todas las sucursales (decisión del usuario, no uno por sucursal) | ⏳ decisión tomada 2026-09-23; falta implementar el rediseño del Paso 3 (ver abajo) para que `/notificar TODOS` funcione con varios teléfonos sin que el primero que consulte "se coma" los mensajes de los demás |

Próximo paso: seguir el plan de la sección **"PASOS SIGUIENTES"** (justo debajo). Ya hecho:
✔ ícono adaptativo. Decidido: ✔ medios de WhatsApp por la opción b (ver Paso 2).
Compilar SIEMPRE desde el Debian (no desde `Z:\`).

## PASOS SIGUIENTES (plan acordado el 2026-09-23 — LEER PRIMERO si se corta la sesión)

**Decisión del usuario (2026-09-23):** para audio/video/fotos de WhatsApp se elige la
**opción b — leer la carpeta/base de medios de WhatsApp**, y SOLO de archivos **nuevos**,
disparada por las notificaciones nuevas que lleguen. NO se escanean ni importan medios
antiguos. (Las opciones descartadas por ahora: a) Accessibility Service, c) limitar el
alcance a la miniatura.)

Leyenda: ⬜ pendiente · 🔄 en curso · ✅ hecho. **Actualizar este bloque al terminar cada paso.**

✅ **Paso 0 — Red de seguridad.** El WIP de la tanda 2.11 (Telegram/ícono/BackHandler/etc.)
seguía sin commitear en `~/Documents/miSecretaria`; se replicó también a este worktree y
ambas copias en disco coinciden. Sigue sin commitear a propósito — el usuario decide cuándo
(ver flujo de release normal).

✅ **Paso 1 — Corregir la detección (Claude).** Hecho y compilado.
- `WalletConfig.detect`/`AppConfig.detect` ahora reciben `packageName`, `title`, `text` por
  separado (ya no un `source` concatenado). Si la regla tiene `packageId` → coincidencia
  EXACTA de paquete (nada de substring). Si no tiene `packageId` → coincide el *nombre* como
  palabra completa (regex `\bnombre\b`) en título+texto, así "pizzas" ya NO coincide con "ZAS".
  Único call site actualizado en `WalletNotificationListener.onNotificationPosted`.
- `WalletConfig.defaults` (YAPE/YOLO/YASTA/AlToke con `packageId=""`) se deja igual a
  propósito — sigue pendiente que el usuario las re-agregue con el selector de apps.

🔄 **Paso 2 — Medios de WhatsApp, opción b, fase 1: imagen/audio/video NUEVOS (Claude).**
Fase 1 (2a–2c, solo LOGUEA lo que encuentra) hecha y compilada; **falta probarla en el
teléfono con un medio real** antes de construir cola/reenvío (2d–2h).
- 2a. ✅ Permisos en `AndroidManifest.xml`: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`,
  `READ_MEDIA_AUDIO` (Android 13+) y `READ_EXTERNAL_STORAGE` con `maxSdkVersion="32"`.
  Fila nueva "Acceso a medios (fotos/audio/video de WhatsApp)" en Configuración
  (`MainActivity.requestMediaPermissions()`, vía `ActivityResultContracts.RequestMultiplePermissions`).
  Bug encontrado y corregido en el mismo paso: la etiqueta larga de esa fila no dejaba
  espacio al botón "Habilitar" (`PermissionRow` sin `weight` en el `Text`) — ya arreglado.
- 2b. ✅ Disparo en `WalletNotificationListener.onNotificationPosted`: si el paquete es
  WhatsApp (`com.whatsapp`/`com.whatsapp.w4b`) y `WhatsAppMediaScanner.looksLikeNewMedia`
  detecta palabras de medio (foto/photo/imagen/video/audio/mensaje de voz/voice message/nota
  de voz, en español e inglés; excluye a propósito "sticker"/"gif" — pedido explícito del
  usuario de no tratarlos como medio), se dispara la búsqueda con `statusBarNotification.postTime`.
- 2c. ✅ `WhatsAppMediaScanner.findNewMedia`: consulta `MediaStore` (Images/Video/Audio) con
  `DATE_ADDED` dentro de una ventana de ±30s/15s alrededor de `postTime`, filtra por
  `RELATIVE_PATH` (Android 10+) o `DATA` (Android 9-) que contenga "WhatsApp", y descarta
  archivos ya vistos (`SharedPreferences` con set de URIs, tope 200) para no repetir ni
  recorrer nunca el histórico completo. Por ahora solo llama a `ScoSecretariaLogger` con lo
  que encuentra — no reproduce ni copia ni reenvía nada todavía.
- 2d. Copia inmediata a almacenamiento propio (`filesDir/media/`), para conservarla si el
  remitente la borra ("eliminar para todos").
- 2e. Reproducción en cola: audio (.opus) con `MediaPlayer`/Media3 por orden de llegada;
  video "como audio" (solo la pista de audio); coordinar con el TTS para no pisar la voz que
  lee el texto; botones Detener/Saltar.
- 2f. ✅ **Hecho en v2.34 (2026-09-25)** — `TelegramSyncWorker.sendPendingMedia()`, mismo
  patrón que el CSV (marcador `lastMediaSentAt`), generalizando `TelegramClient.sendDocument`
  (no se agregaron `sendVoice/sendAudio/sendVideo/sendPhoto` separados — `sendDocument` ya
  acepta cualquier archivo, solo se le agregó el parámetro `mimeType`). Sí respeta el límite
  de 50 MB por archivo (se salta, no se reintenta). Sin probar en el teléfono todavía.
- 2g. Checklist en Configuración de qué tipos guardar/reenviar/reproducir (imagen, audio,
  video, documento): nuevo `MediaConfig.kt` o claves en `DisplayPreferences`.
- 2h. Modelo/Store: `WalletNotification.mediaPath` hoy solo guarda la miniatura; agregar
  `mediaType` de forma compatible (`optString` en `WalletNotificationStore.load`) y borrar
  los archivos de `filesDir/media` al recortar el historial (100) y en `clearHistory`
  (hoy nunca se borran).
- Fuera de la fase 1: documentos (PDF, etc.) — probablemente requieren acceso amplio a
  almacenamiento; evaluarlo después.
- **RIESGOS:** (1) ✅ **validado por consulta directa a `content query` sobre MediaStore
  (2026-09-23, sin tocar la app):** fotos y videos de WhatsApp SÍ están indexados y recientes
  en este teléfono (ruta real:
  `Android/media/com.whatsapp/WhatsApp/accounts/<id>/Media/...`, también `Movies/WhatsApp/`
  para algunos videos — ambas contienen "WhatsApp", el filtro de ruta sigue sirviendo).
  ⚠️ **Audio (notas de voz) SIN validar todavía:** la última nota de voz indexada en
  `MediaStore.Audio` es de 9 días antes de la prueba — puede ser que el usuario no haya
  recibido audios nuevos, o que no se indexen de forma confiable. Falta mandar una nota de voz
  de prueba y volver a consultar antes de invertir en 2e (cola de reproducción de audio).
  (2) Si WhatsApp tiene la descarga automática desactivada, el archivo no existe hasta que el
  usuario lo abra — sin validar. (3) Plan B de la opción b (sin verificar):
  `ACTION_OPEN_DOCUMENT_TREE` sobre `Android/media/com.whatsapp/WhatsApp/Media` con permiso
  persistente, solo si (1)/(2) fallan para algún tipo de archivo.

✅ **Paso 3 — Telegram con varios teléfonos (2026-09-23): decisión del usuario = UN SOLO bot
para todas las sucursales** (no uno por sucursal, "sería problemático"). Rediseño hecho y
compilado: `TelegramSyncWorker`/`TelegramConfig` ya NO confirman el offset ante Telegram
(antes `getUpdates` con offset por dispositivo, pero la cola de un bot es única: el primer
teléfono que consultaba "se comía" los mensajes y los demás nunca veían `/notificar TODOS` ni
`/renombrar`). Ahora cada ciclo pide `offset=0` (nunca se confirma nada) y cada dispositivo
filtra LOCALMENTE los `update_id` ya procesados (`TelegramConfig.isUpdateProcessed`/
`markUpdateProcessed`, tope 300) — así todos los teléfonos ven el mismo lote de comandos.
Falta probar con el bot real del usuario (`t.me/miSecretariaPerfecta_bot`, Chat ID
`8159568738` — token conocido solo por el usuario, no guardado aquí) una vez que guarde y
presione "Enviar mensaje de prueba" en Configuración. Alternativa descartada por el usuario:
un bot (token) por sucursal ("sería problemático").

🔄 **Paso 4 — Cerrar la tanda (Claude + usuario).** 2.11/2.12 nunca se publicaron por separado
(absorbidas). **2.13 y 2.14 SÍ se publicaron** (usuario corrió el release, GitHub Releases +
`update.json` + Firebase confirmados). Después de publicar 2.14, se encontraron más cosas en
vivo (bot que no respondía a `/notificar` mal formado, comandos que tardaban hasta 30 min) —
arregladas en 2.15/2.16, **compiladas y con build Interna generada, pero AÚN sin publicar**.
Ya NO se crean scripts `-instalar.sh` por versión (ver "Cómo compilar e instalar"). Falta:
1. El usuario prueba en el teléfono lo que sigue sin verificar en vivo: permiso de medios +
   detección real de WhatsApp (Paso 2, todavía no probado), el long-polling de Telegram en
   tiempo real (2.16, recién hecho, sin probar), y los defaults de billeteras reales (solo
   aplican a instalaciones nuevas, no a este teléfono que ya tiene su propia config guardada).
2. Checklist rápido: Atrás físico, íconos/paquetes, `Bs 2,392.69`, ícono nuevo, "Quitar",
   `/renombrar`, `/notificar` (todo en un solo mensaje), `/help`, triple-tap al logo + PIN
   `230985`, "Sincronizar ahora" (ahora en su propia línea, visible), y que `/notificar` llegue
   casi al instante sin tocar "Sincronizar ahora".
3. El usuario presiona `miSecretaria_Update.desktop` (o corre `./release.sh`) para publicar
   2.14 — **recordar SIEMPRE subir versionCode/versionName al hacer un cambio de código,
   aunque sea chico**, para que "Buscar actualización" pueda distinguirlo (la lección de este
   bug: un fix sin subir versión es indistinguible del build ya publicado).

### Hallazgos de la verificación del 2026-09-23 (lectura estática del código)
- **Confirmado en código:** `versionCode=2011`; sucursal aleatoria (`MS-XXXXXX`);
  `/notificar` y `/renombrar`; `BackHandler` en SETTINGS/READ/PICK + flag en el manifest;
  listas con `AppIcon` + nombre + `packageId` (rojo si vacío) sin campos manuales;
  `AmountSpeech.parseAmount` (`2,392.69` → 2392 Bs + 69 ctvs); ícono adaptativo →
  `@mipmap/ic_launcher_foreground`, fondo `#F6E7B4`, PNG en las 5 densidades, sin referencias
  colgando al avatar viejo. Medios: solo miniatura (`EXTRA_PICTURE`); no hay Accessibility
  Service, ni permisos de medios, ni `sendPhoto/sendAudio/sendVideo`.
- **Estado de compilación:** el último APK en `app/build/outputs` es 2.10 (`versionCode` 2010)
  y el último commit del log es "Release v2.10".
- **Discrepancias con lo que decía este .md antes:** los `packageId` vacíos no son solo YASTA
  (ver Paso 1); falsos positivos por nombre (Paso 1); limitación de Telegram con varios
  teléfonos (Paso 3); miniaturas nunca borradas (Paso 2h).
- **Archivos NO leídos en esa verificación:** `SpeechEngine`, `PaymentMessageDetector`,
  `AdFilterConfig`, `BackupManager`, `UpdateManager`, `WalletOverlay`,
  `WalletNotificationNotifier`, `PaymentAlertActivity`.
- El `.git` tiene `worktrees` (`oscarsonco-*`) y `refs/copilot`: otras sesiones/herramientas
  pudieron tocar el repo; mirar `git status` antes de seguir.

## Qué es

App Android nativa (Kotlin + Jetpack Compose) que escucha las notificaciones del teléfono
(vía `NotificationListenerService`), detecta pagos de billeteras móviles bolivianas (YAPE,
ZAS, YOLO, YASTA, AlToke, MiPaguito, etc.) y de otras apps configuradas como "generales"
(WhatsApp, SMS, etc.), y **lee en voz alta** el contenido con texto-a-voz (TTS), además de
guardar todo en un historial dentro de la app.

## Datos clave del proyecto

- **Carpeta raíz (Debian):** `~/Documents/miSecretaria`
- **Acceso alterno desde Windows:** `Z:\Documents\miSecretaria` (unidad de red SMB al mismo
  Debian). ⚠️ Ver sección "Gotchas de entorno" abajo — NO compilar desde Windows/SMB.
- **applicationId / namespace:** `com.sco.misecretaria`
- **Paquete Kotlin:** `com.sco.misecretaria` (en `app/src/main/java/com/sco/misecretaria/`)
- **Versión actual:** `versionCode=2037`, `versionName="2.37"` (ver `app/build.gradle.kts`,
  subida 2026-09-25). Compila limpio, build Interna generada, **YA INSTALADA por ADB en el
  teléfono del usuario** (`adb install -r`, confirmado `lastUpdateTime=2026-09-25 17:47:10`).
  **v2.30 SÍ se publicó** (el usuario pidió correr `release.sh`, ver más abajo — el tag
  `v2.30` y el commit `2952e90` quedaron en GitHub) — **v2.31 a v2.37 aún no**, falta
  correr `release.sh` de nuevo. **v2.24 ya se confirmó en vivo** (fotos reales); **v2.29 ya se
  confirmó en vivo** (el robo de medios entre mensajes por palabra suelta ya no pasa). **v2.30
  (documentos) confirmado en vivo, con varios bugs reales encontrados y corregidos en
  v2.31-v2.36 — ver esas tandas más abajo:** (v2.31) el PDF SÍ se detectaba/copiaba, pero
  terminaba adjunto a la notificación equivocada por una notificación-resumen de grupo vieja;
  (v2.32) un audio COMPARTIDO (`.mp3`, no nota de voz) nunca se encontraba porque solo se
  miraba la carpeta de notas de voz, no la de audios compartidos; (v2.33) `.db`/`.log` (que el
  usuario SÍ necesita recibir, para caja chica) se descartaban en silencio por un filtro de
  "archivos temporales" copiado de otro proyecto sin ese caso de uso — encontrado por revisión
  preventiva, antes de que fallara en la práctica; (v2.34) reenvío periódico de medios al bot
  de Telegram (Paso 2f), mismo patrón que el CSV; (v2.35) probado a fondo con un burst de 9
  archivos (3 videos, 3 imágenes, voz, mp3, pdf) casi simultáneos — encontró y corrigió dos
  bugs más: medios de un tipo terminaban adjuntos a notificaciones de OTRO tipo (video↔foto,
  voz↔video), y notificaciones repostadas por WhatsApp/Android con contenido idéntico se
  guardaban como duplicadas (el "a veces repite mensajes anteriores" que reportó el usuario);
  (v2.36) el límite de 100 del Historial (por diseño, para no crecer sin fin) también estaba
  limitando el CSV/reenvío de medios a Telegram — se separó en dos colas de exportación sin
  ese límite, decisión tomada con el usuario (eligió "cola separada" sobre "solo subir el
  número"); (v2.37) video/foto enviados CON TEXTO PROPIO no traían la frase fija y nunca se
  detectaban — corregido usando el emoji (🎥/📷/🎤/🎵) como señal primaria, mismo principio
  que documentos desde v2.30. **Misterio sin resolver:** un `.doc`, dos `.7z`, un `.pdf` y una
  imagen no dejaron NINGÚN rastro en el log a pesar de que el texto guardado sí tenía el emoji
  correcto — se agregó un log de diagnóstico nuevo para la próxima vez (ver "Tanda v2.37").
  **Pendiente de verificar en vivo:** el fix de video/foto con texto propio, y sobre todo el
  misterio de arriba con el log de diagnóstico activo. También sigue pendiente el
  escaneo genérico de respaldo de v2.25 (nunca se ejercitó, porque las rutas conocidas ya
  encuentran todo en este teléfono). También pendiente: guardar/probar el
  bot de Telegram en Configuración (Paso 3), toda la tanda v2.19 (borrar/fijar/copiar/nota +
  centralización de textos — ya en producción, sin probar), toda la tanda v2.20 (Papelera,
  colores de Configuración, botón Volver verde — sin publicar, sin probar) y el fix de v2.21
  (`withTimeout` en el long-poll de Telegram — corrige un bug real ya confirmado en vivo: el
  bot dejó de responder comandos por ~13h sin crashear, ver esa sección más abajo).
- **Esquema de versionCode:** `major*1000 + minor` (ej. 2.1 → 2001, 2.700 → 2700), para poder
  hacer muchos builds de prueba (2.1, 2.2, ... 2.700) antes de saltar a la siguiente versión
  entera (3.0) cuando quede estable.
- **minSdk 26 / targetSdk 37**
- **JAVA_HOME de build:** `/home/beelinkser5max/Descargas/android-studio/jbr`

## Cómo compilar e instalar

**Descontinuado (2026-09-23):** ya NO se usan scripts `miSecretariaV(x.x)-instalar.sh` por
versión (compilaban e instalaban por ADB). Se borraron todos los `.sh` viejos (V2.2 a V2.11)
del repo; `release.sh` nunca dependió de ellos.

**Instalación (actualizado 2026-09-25, ver regla al principio del documento):** si el teléfono
del usuario está conectado por USB a este Debian, Claude instala directo por ADB
(`adb install -r ...`) en cada versión nueva. Si el usuario está de viaje o en una sucursal
(sin el teléfono conectado aquí), instala él mismo desde la app ("Buscar actualización").

Para compilar y solo verificar que el código anda bien (sin instalar nada):

```bash
cd ~/Documents/miSecretaria
export JAVA_HOME=/home/beelinkser5max/Descargas/android-studio/jbr
./gradlew assembleDebug --no-configuration-cache
```

Si algo se comporta raro (build fantasma, permission denied persistente):
`pkill -f gradle`, borrar `.gradle`/`app/build`/`.kotlin`, reintentar con
`--no-configuration-cache --rerun-tasks`.

Al crear una versión nueva: solo hay que subir `versionCode`/`versionName` en
`app/build.gradle.kts` (esquema arriba). **No hace falta tocar strings de versión a mano en el
código** — todo pasa por `AppInfo.kt`, que lee `BuildConfig.VERSION_NAME`/`VERSION_CODE`. El
resto (compilar, publicar en GitHub Releases, actualizar `update.json`, desplegar Firebase
Hosting, commit+push) lo hace `release.sh` / `miSecretaria_Update.desktop` — ver "Flujo de
release" más abajo.

**Build "Interna" (2026-09-23, con Token/Chat ID de Telegram pre-rellenados) — CORRER EN
CADA VERSIÓN NUEVA, no solo si el usuario lo pide (pedido explícito 2026-09-23):**
```bash
cd ~/Documents/miSecretaria
cp secrets.properties.example secrets.properties   # solo la primera vez
# editar secrets.properties con botToken/chatId reales (archivo en .gitignore, no se commitea)
./build_interna.sh
./gradlew assembleDebug   # sin flags, para que el build por defecto vuelva a quedar sin secretos
```
Genera `Releases/miSecretariaV(x.x)-debug_Interna.apk`. **Nunca lo suba release.sh ni se sube a
GitHub** — se pasa a mano (USB/Bluetooth) a los teléfonos de sucursal. Ver detalle técnico en
la sección v2.13 más arriba. Ya generada para 2.13 y 2.14.

## Dashboard web de `miSecretaria.db` (`web_server.py`, 2026-09-24)

Pedido explícito del usuario: poder ver localmente lo que hay en `miSecretaria.db` desde un
`miSecretaria.html`, y que ese "servidor" se pueda encender/apagar con un comando de Telegram
(no a mano en la PC).

- ✅ **`web_server.py`** (nuevo, raíz del proyecto) — Flask, puerto **8766** (el 8765 ya lo usa
  `CotizacionDelDolar/web_server.py` en esta misma máquina — puertos distintos para no
  chocar). Una sola ruta `/`, abre `miSecretaria.db` en modo **`?mode=ro`** (nunca escribe —
  mismo patrón, y mismo motivo, que el `web_server.py` de `CotizacionDelDolar`: puede correr al
  mismo tiempo que `csv_importer.py` sin arriesgar bloquear/corromper la base). Muestra tarjetas
  con el total y el desglose por sucursal, y una tabla con las últimas 200 notificaciones.
- ✅ **`miSecretaria.html`** (nuevo, **en la raíz del proyecto** — se movió ahí desde
  `templates/miSecretaria.html` el 2026-09-24 a pedido explícito del usuario: quería el archivo
  visible junto a `miSecretaria.db`/`miSecretaria.log`, exista o no el servidor prendido, en
  vez de escondido en una subcarpeta por convención de Flask. `web_server.py` ahora usa
  `Flask(__name__, template_folder=str(BASE_DIR))` — la raíz del proyecto sirve de
  `template_folder`). Diseño propio (no Bootstrap/CDN — todo el CSS es inline, cero
  dependencias externas): tarjetas de métricas en azul (`AccentBlue`, para que combine con la
  app) y una tabla con scroll propio. Colores por tipo: `PAYMENT` en verde, `ALERT` en rojo.
  **Ojo:** el archivo sigue siendo una plantilla Jinja2 (`{{ total }}`, etc.) — abrirlo directo
  con doble clic (sin pasar por `web_server.py`) muestra las llaves sin rellenar, no datos
  reales; solo se ve bien visitando `http://localhost:8766` (o la IP de la PC) mientras el
  servidor está encendido.
- ✅ **Redirección automática al abrir el archivo directo (2026-09-25, pedido explícito del
  usuario: "sino, no tendría sentido haber creado ese html")** — `miSecretaria.html` ahora
  tiene un `<script>` al principio del `<head>` que revisa `window.location.protocol`: si es
  `"file:"` (doble clic), redirige solo a `http://localhost:8766/`; si es `http:`/`https:`
  (servido por Flask), no hace nada y se ve la plantilla ya rellenada. Si el servidor está
  apagado en ese momento, el navegador muestra el error normal de "no se puede conectar" en
  vez de las llaves vacías — más claro, pero requiere que el importador esté corriendo y el
  panel encendido (`/panelon`) para que la redirección sirva de algo.
  🐛→✅ **Bug propio encontrado al probar esto:** el comentario del script original decía
  textualmente "...se ven las llaves `{{ }}` sin rellenar" — Jinja2 intenta parsear ESE mismo
  `{{ }}` como una expresión de plantilla (no distingue "esto es un comentario de JS" de
  "esto es código Jinja"), tirando `TemplateSyntaxError` y una página 500 en cuanto Flask
  recompilaba la plantilla (no se notó al momento de escribirlo porque el proceso de
  `web_server.py` ya estaba corriendo desde antes con la plantilla vieja cacheada en memoria —
  Jinja2 con `debug=False` no recarga plantillas solas; recién se manifestó al reiniciar el
  servidor). Corregido reescribiendo el comentario sin usar `{{ }}` literal. **Lección para el
  futuro:** cualquier texto (comentario, string, lo que sea) dentro de `miSecretaria.html` que
  necesite mencionar la sintaxis `{{ }}`/`{% %}` de Jinja2 tiene que evitar escribirla literal,
  o Jinja la va a interpretar como código real.
- ✅ **Filtros por sucursal y por aplicación (2026-09-25, pedido explícito del usuario)** —
  filtrado del lado del servidor (no solo sobre las 200 filas ya cargadas, para no perder
  resultados de una sucursal/app poco frecuente que quedaría fuera de las últimas 200
  globales):
  - `web_server.py`: `query_db(sucursal, origen)` ahora arma la consulta SQL con `WHERE`
    dinámico (parámetros vía `?`, nunca concatenados directo — sin riesgo de inyección) según
    los query params `?sucursal=...`/`?origen=...` de la URL (`request.args`). Las tarjetas por
    sucursal y las listas de opciones de los `<select>` (`sucursales`/`origenes`) siempre
    reflejan TODA la base sin importar el filtro activo, para que el usuario vea todas las
    opciones disponibles; `total`/`recientes` sí quedan filtrados.
  - `miSecretaria.html`: dos `<select>` (Sucursal/Aplicación) dentro de un `<form method="get">`
    que se auto-envía con `onchange="this.form.submit()"` (funciona sin JavaScript también,
    con el botón nativo de envío del `<select>`). Cada tarjeta de sucursal es ahora un botón
    dentro de su propio mini-`<form>` (en vez de un `<div>`) — un clic filtra directo a esa
    sucursal sin tocar el desplegable, preservando el filtro de aplicación activo si había uno
    (vía un `<input type="hidden">`). Aparece un enlace "✕ Quitar filtros" (vuelve a `/`, sin
    query params) solo cuando hay algún filtro activo. Tarjeta activa resaltada con
    `.tarjeta-activa` (borde azul).
  - **Probado en vivo (2026-09-25) desde el navegador integrado de Claude:** filtro por
    sucursal solo (`OSC_TecnoPovaNeo2` → 88 de 88), combinado con aplicación (`+ ZAS` → 2 de
    2, las dos coincidían), "Quitar filtros" resetea ambos `<select>` a "Todas", y clic directo
    en la tarjeta "VIC_PocoX5" filtra a esa sucursal (116 de 116) sin pasar por el desplegable.
    Confirmado también que el bug de Jinja de arriba está resuelto (la página ya no tira 500).
- 🐛→✅ **Bug real encontrado (2026-09-25): `/panelon` no hacía nada — `getUpdates(offset=0)`
  no es confiable, se queda pegado devolviendo solo el mensaje más viejo.** El usuario mandó
  `/panelon` varias veces (13:24, 13:57, 14:00, 14:48, 14:49, 14:52 — confirmado con doble
  check ✓✓ de "entregado" en su Telegram) y `csv_importer.py` nunca respondió ni encendió el
  panel, incluso después de reiniciarlo. Diagnóstico completo contra la API real de Telegram
  (sin tocar nada de la app, solo lectura):
  - `getWebhookInfo` mostró `pending_update_count: 15` — Telegram SÍ tenía 15 mensajes sin
    confirmar en cola.
  - Pero `getUpdates?offset=0` (exactamente lo que hace `telegram_get_updates()` en
    `csv_importer.py`) devolvía **un solo resultado**, siempre el mismo: un `/help` de un día
    antes (24/09 18:07) — nunca los `/panelon` de hoy. Repetido varias veces, con y sin
    `limit=100`/`allowed_updates` reseteado: mismo resultado, estable y reproducible.
  - `getUpdates?offset=-15` (el modo "dame las últimas N de la cola" que documenta la API de
    Telegram, alternativa a "offset=0 = dame todo lo no confirmado") **sí devolvió las 15
    completas** — incluyendo los 6 `/panelon` de hoy y el `/help` de las 15:00. Confirmado
    2 veces seguidas, estable.
  - Se descartó que otro proceso de esta máquina "se coma" las actualizaciones: se buscó el
    token del bot en todo `~/Documents/` y solo aparece en la carpeta de miSecretaria.
    También se descartó un webhook activo (`getWebhookInfo.url` vacío).
  - Dato adicional, no contradictorio: el teléfono conectado (`TECNO_LG6n`, v2.30) tenía en su
    `shared_prefs/telegram_config_v1.xml` un `processed_update_ids` con exactamente ese mismo
    rango de ids (530 a 594) — es decir, en algún momento SÍ logró ver el lote completo con su
    propio `offset=0` (la app Android usa el mismo diseño). El fallo de `offset=0` parece ser
    intermitente/dependiente de qué réplica del backend de Telegram responde, no un error de
    sintaxis del lado de acá — por eso conviene el offset negativo, que fue estable las dos
    veces que se probó.
  - ✅ **Arreglo:** `telegram_get_updates()` en `csv_importer.py` cambió de `offset=0` a
    `offset=-100` — sigue sin confirmar nada ante Telegram (mismo diseño de dedupe local vía
    `panel_updates_procesados`), solo cambia CÓMO se pide la cola para no depender del
    comportamiento poco confiable de `offset=0`. **Aplica solo a `csv_importer.py` (Python,
    PC) por ahora** — no se tocó `TelegramClient.kt` (Android) porque el teléfono probado SÍ
    tenía el lote completo en su set local, sin evidencia de que le esté pasando lo mismo; si
    algún device de sucursal reporta que `/notificar`/`/renombrar` "no llegan" de forma
    persistente (no solo lenta), vale la pena aplicar el mismo cambio ahí.
  - **✅ Confirmado en vivo (2026-09-25):** el usuario reinició `csv_importer.py` y mandó
    `/panelon` de nuevo — "Ya encendió después de mucho tiempo..." (tardó, pero encendió; el
    offset negativo sí trae los mensajes que antes se perdían con `offset=0`).
- ✅ **Fusionar y eliminar sucursales desde `miSecretaria.html` (2026-09-25, pedido explícito
  del usuario)** — motivo real: el usuario renombra el `deviceLabel` de un teléfono cuando
  cambia de empleado/sucursal (ej. `MS-YCFNS9` → `VIC_PocoX5` → `VIC_RedmiNote13Pro`, el mismo
  teléfono con 3 nombres distintos con el tiempo), pero como `sucursal` en `miSecretaria.db`
  es el nombre TAL CUAL llegó en cada CSV histórico, el historial viejo queda repartido bajo
  nombres distintos en vez de junto. Nuevo panel colapsable `<details>` "⚙️ Administrar
  sucursales" debajo de los filtros, con una tabla de checkboxes (una fila por sucursal, con
  su conteo) y dos acciones sobre un mismo formulario (`<form method="post"
  action="/administrar">`, dos `<button name="accion" value="fusionar|eliminar">`):
  - **Fusionar:** marca 1+ sucursales de origen, escribe (o elige de un `<datalist>` con las
    ya existentes) el nombre destino → `UPDATE notificaciones SET sucursal = ? WHERE sucursal
    IN (...)`. Si el destino es un nombre nuevo que no existía, simplemente aparece; si ya
    existía, sus filas se suman.
  - **Eliminar:** marca 1+ sucursales → `DELETE FROM notificaciones WHERE sucursal IN (...)`,
    con confirmación `confirm()` de JavaScript antes de enviar (deja claro que es permanente,
    "no hay papelera acá" — a diferencia del Historial de la app Android, que sí tiene
    Papelera desde v2.20). Pensado para limpiar sucursales de prueba o duplicados ya
    fusionados a otro nombre.
  - `web_server.py`: `get_write_conn()` (nueva) abre una conexión de escritura de corta
    duración (se cierra apenas termina la función que la usa, nunca queda viva entre
    requests) con `PRAGMA busy_timeout = 5000` — si `csv_importer.py` está insertando un CSV
    justo en ese milisegundo, esta conexión ESPERA hasta 5s en vez de fallar con "database is
    locked", en vez de competir a ciegas. La conexión de LECTURA de siempre
    (`query_db`, modo `?mode=ro`) no cambió — sigue siendo de solo lectura.
  - Ruta nueva `POST /administrar`: lee `accion`/`sucursales`/`destino` del formulario,
    ejecuta `fusionar_sucursales()`/`eliminar_sucursales()`, y redirige a `/?msg=...` con un
    resumen de cuántas filas se movieron/borraron — la plantilla muestra ese mensaje en una
    cajita azul arriba de la tabla si `msg` viene en la URL.
  - **Probado (2026-09-25) SOLO sobre una copia descartable de la base** (`cp` a `/tmp`,
    nunca la real) — confirmado que fusionar 12 filas de `MS-YCFNS9` a `VIC_RedmiNote13Pro`
    y eliminar las 5 de `MS-4ERLNN` funcionan como se espera (conteos correctos antes/después,
    total final consistente). **A propósito NO se probó contra `miSecretaria.db` real** — es
    una operación que muta/borra datos reales de verdad, así que la primera vez que se use
    contra la base real del usuario debe ser el usuario decidiendo qué fusionar/eliminar
    desde el navegador, no Claude ejecutándolo por su cuenta.
- ✅ **Control por Telegram (`/panelon`, `/paneloff`)** — vive DENTRO de `csv_importer.py`, no
  en un script aparte: como `web_server.py` no puede escucharse a sí mismo para "encenderse"
  (si está apagado no hay nada corriendo que reciba el comando), el que escucha tiene que ser un
  proceso que YA esté siempre corriendo — y ese es `csv_importer.py`. Cada 10s (aparte del ciclo
  de 30s de los CSV) hace su propio `getUpdates(offset=0)` (nunca confirma ante Telegram, igual
  que hace la app Android) y dedupea localmente contra una tabla nueva en la misma base,
  `panel_updates_procesados(update_id)` — un dedupe totalmente independiente del que usan los
  teléfonos (cada uno el suyo), así que no hay riesgo de interferencia. Si el texto no es
  exactamente `/panelon` o `/paneloff`, se ignora en silencio (igual que la app Android ignora
  cualquier texto que no matchee sus propios comandos) — **estos dos comandos NO están en el
  `/help` de la app** a propósito, porque la app no los procesa, son exclusivos de la PC.
  - `iniciar_servidor_web()`/`detener_servidor_web()`: lanzan/matan `web_server.py` con
    `subprocess.Popen(..., start_new_session=True)`. **Chequeo de "¿ya está encendido?" por
    conexión real al puerto 8766** (`socket.connect`), no por PID ni `pgrep` — misma lección ya
    documentada en `CotizacionDelDolar/CLAUDE.md` ("detectar un proceso con pgrep da falsos
    positivos"). Si el puerto está ocupado por algo que este script no lanzó, avisa por Telegram
    en vez de intentar matarlo a ciegas.
  - Requiere `secrets.properties` (mismo archivo que usa `build_interna.sh`) — si no existe o
    está vacío, el importador de CSV sigue funcionando igual, solo que `/panelon`/`/paneloff`
    quedan sin efecto (avisado con un `log.warning` al iniciar).
  - El panel en vivo de la terminal (`csv_importer.py`) ahora muestra una fila más: "Panel web
    (/panelon, /paneloff): 🟢 encendido (puerto 8766)" / "🔴 apagado".
- ✅ **Probado en vivo (2026-09-24):** `web_server.py` corrido a mano, `curl` a
  `http://127.0.0.1:8766/` devolvió el HTML completo con datos reales (50 notificaciones, 2
  sucursales). El polling de Telegram dentro de `csv_importer.py` también se probó en vivo (sin
  disparar `/panelon`/`/paneloff` de verdad, para no mandar un mensaje real sin permiso) — no
  hubo errores, y quedó una fila en `panel_updates_procesados` confirmando que el dedupe local
  funciona.
- ⚠️ **El proceso de `csv_importer.py` que el usuario ya tenía corriendo (desde las 10:51, antes
  de este cambio) sigue con el código VIEJO en memoria** — Python no recarga en caliente. Para
  que `/panelon`/`/paneloff` funcionen, el usuario tiene que cerrar esa ventana (Ctrl+C o
  cerrarla) y volver a abrir `miSecretaria_ImportarCSV.desktop`.
- Dependencia nueva: `flask` (ya estaba instalada globalmente en esta máquina — la usa
  `CotizacionDelDolar/web_server.py` — se agregó a `requirements.txt`).

## Backup portable del proyecto (`backup_proyecto.sh`, 2026-09-24)

Pedido explícito del usuario: poder migrar todo el proyecto a otra computadora "como un
Firefox portable" (código + sesión/credenciales viajando juntos), guardando el resultado
dentro de `/Archivo` (carpeta que el usuario ya tenía creada en la raíz del proyecto, mismo
patrón que su otro proyecto `CotizacionDelDolar`).

- ✅ **`backup_proyecto.sh`** (nuevo, raíz del proyecto) + **`miSecretaria_backup.desktop`**
  (doble clic, `Terminal=true`, mismo estilo que `miSecretaria_Update.desktop`) — empaqueta el
  proyecto en `Archivo/miSecretaria_Backup_<fecha_hora>.tar.gz`. Adaptado del
  `Backup_Proyecto.sh` de `CotizacionDelDolar` (mismo patrón: `rsync` con exclusiones + snapshot
  de SQLite vía `sqlite3 ".backup"` + `tar -czf`), con una diferencia deliberada:
  - **Incluye `secrets.properties`** (el Token/Chat ID real) y `miSecretaria.db`/
    `miSecretaria.log*` — es justo la "sesión" que debe viajar con el backup para no tener que
    reconfigurar nada a mano en la máquina nueva (la analogía de "Firefox portable" que pidió
    el usuario).
  - **Excluye `.git`** (a propósito, igual que `CotizacionDelDolar`): el repo ya está en
    GitHub, así que en la máquina nueva es más simple `git clone` + copiar encima los 3
    archivos no versionados del backup, que arrastrar los ~150 MB de historial git (que además
    tiene entradas de `worktrees` con rutas absolutas de ESTA máquina — copiarlas tal cual
    podría dejar referencias de worktree rotas en la máquina nueva).
  - Excluye también `.claude` (datos de sesión de Claude Code, no es parte del proyecto),
    `app/build`/`.gradle` (se regeneran solos al compilar) y `Releases/` (esos APK ya están en
    GitHub Releases, se regeneran con `build_interna.sh`/`release.sh`).
  - El propio script imprime, al terminar, los pasos exactos para restaurar en la máquina
    nueva (incluye el flag `--break-system-packages` para `pip3 install`, necesario en Debian
    por PEP 668 — mismo detalle que ya resolvió `CotizacionDelDolar`).
- ✅ **Probado en vivo (2026-09-24):** una corrida real generó
  `Archivo/miSecretaria_Backup_2026-09-24_10-45-59.tar.gz` (1.3 MB — mucho más liviano que el
  del otro proyecto porque no hay sesión de WhatsApp/Chromium de por medio). Verificado con
  `tar -tzf`: contiene `secrets.properties` y `miSecretaria.db`, no contiene nada de `.git`,
  `.claude`, `app/build` ni `Releases`.
- Agregado a `.gitignore`: `/Archivo` (backups + el `miSecretaria.png` que el usuario ya tenía
  ahí a mano) y `*.tar.gz`.

## Colector de CSV a `miSecretaria.db` (herramienta de escritorio, fuera de la app)

Acumula en una base SQLite local todos los CSV que las sucursales mandan por Telegram (ver
"Colector de CSV a `miSecretaria.db`" más arriba para el porqué de este diseño). No es parte de
la app Android — vive en la raíz del proyecto, se corre en este Debian.

```bash
cd ~/Documents/miSecretaria
python3 csv_importer.py          # corre en primer plano; Ctrl+C para detener
# o doble clic en miSecretaria_ImportarCSV.desktop
```

- Requiere que **Telegram Desktop esté abierto con sesión** en esta máquina y que ese chat
  tenga la descarga automática de archivos activada (ya confirmado que funciona por defecto).
- Escribe en `miSecretaria.db` (raíz del proyecto, en `.gitignore`, no se sube a git).
- Log de depuración en `miSecretaria.log` (rotación automática, también en `.gitignore`).
- Sin systemd/cron a propósito — se inicia y detiene a mano.

## Backup portable del proyecto (uso)

```bash
cd ~/Documents/miSecretaria
bash backup_proyecto.sh          # o doble clic en miSecretaria_backup.desktop
```
Genera `Archivo/miSecretaria_Backup_<fecha_hora>.tar.gz` (código + `secrets.properties` +
`miSecretaria.db`/`.log`, sin `.git`/`.claude`/`app/build`/`Releases`). Ver "Backup portable del
proyecto (`backup_proyecto.sh`, 2026-09-24)" más arriba para el detalle completo y el porqué de
cada exclusión.

## Mapa de archivos (app/src/main/java/com/sco/misecretaria/)

- `MainActivity.kt` — UI Compose completa: navegación (Home/Configuración/Leer/selector de
  apps), historial con filtro por billetera/app y miniaturas (`ThumbnailImage`) cuando hay
  `mediaPath`, botón Encendido/Apagado, indicador de estado del servicio, guardar/compartir
  (historial .txt, .csv, log, backup .json), compartir APK, pantalla "Leer" (texto libre →
  voz, con Pausa/Reanudar y selector Varón/Mujer). Todas las pantallas menos Home tienen
  `BackHandler` para que el botón atrás de Android funcione igual que "Volver". Las listas
  de Billeteras/Apps muestran ícono real (`AppIcon`, vía `packageManager.getApplicationIcon`)
  + nombre + `packageId` (en rojo si está vacío — eso fue justo el bug de YASTA, ver abajo).
  Ya NO hay cuadros de texto manuales para agregar billetera/app: el único camino es el
  botón "Agregar Billetera"/"Agregar Aplicación" → abre `InstalledAppsScreen`.
  **Desde v2.19: TODO el texto visible de este archivo viene de `res/values/strings.xml`**
  (`stringResource(R.string.xxx)`) — no quedan literales Spanish hardcodeados en los
  `Composable`. Cada tarjeta del historial en `HomeScreen` tiene ahora: "📋 Copiar" (copia
  `item.message` al portapapeles vía `copyToClipboard()`, función top-level nueva que usa
  `ClipboardManager`), "📌 Fijar"/"📌 Quitar fijado" (máx. 2, ver `WalletNotificationStore`),
  "📝 Nota" (abre un campo de texto inline para `item.note`, ver `setNote`), y "Eliminar"
  (**desde v2.20: ya NO borra de una vez, mueve a la Papelera** —
  `WalletNotificationStore.moveToTrash`). Arriba del historial hay un botón "Seleccionar" que
  activa casillas de verificación por tarjeta para mover varias a la Papelera a la vez
  ("Eliminar seleccionadas (n)", con confirmación) y un botón "Vaciar historial" (con
  confirmación) que también mueve todo a la Papelera, no lo borra. Un botón nuevo
  "🗑️ Papelera (n)" junto a esos dos abre `TrashScreen` (nueva, v2.20): desde ahí se restaura
  1/varias/todas (`restore`/`restoreMany`/`restoreEverything`) o se vacía la papelera de verdad
  (`emptyTrash`, con confirmación — ese sí es el único borrado permanente que queda en toda la
  pantalla). Las notificaciones fijadas se reordenan siempre al principio de la lista mostrada
  (`ordered = pinned + rest`), sin importar el filtro activo. **Desde v2.20:** el botón "Volver"
  de `InstalledAppsScreen`/`ReadScreen`/`SettingsScreen`/`TrashScreen` usa el composable nuevo
  `BackButton()` (verde `AccentGreen`, texto blanco) en vez de un `TextButton` plano; los
  `Button()` de `SettingsScreen` que quedaban con el color café por defecto de Material You ya
  usan `colors = blue` (`AccentBlue`) igual que el resto. **v2.26:** la tarjeta del Historial
  ahora rama por `item.mediaType` — `"image"`/`null` sigue usando `ThumbnailImage` (ahora con
  la foto real, no la miniatura), `"audio"` usa el composable nuevo `AudioPlayer`
  (`android.media.MediaPlayer`, botón reproducir/detener), `"video"` usa `VideoOpenButton`
  (abre con el reproductor externo vía `FileProvider`). **v2.32:** cada `Card` del Historial
  usa `MediaCardGreen` (`ui/theme/Color.kt`, verde claro `#E1F3E5`) como fondo si
  `item.mediaPath != null` (cualquier tipo — imagen/video/audio/documento), para distinguir a
  simple vista las notificaciones con archivo adjunto entre el resto de mensajes de otras
  conversaciones (pedido explícito del usuario).
- `WalletNotificationListener.kt` — `NotificationListenerService`: detecta pagos/apps
  generales, arma el `WalletNotification`, dispara notificación/overlay/pantalla
  completa/voz según corresponda. Filtra notificaciones-resumen de grupo (`FLAG_GROUP_SUMMARY`,
  el "3 mensajes nuevos" de WhatsApp) y extrae el último mensaje real vía `MessagingStyle`.
  **Ignora notificaciones "revividas"** al reconectar (más de 2 min de antigüedad según
  `statusBarNotification.postTime` → se descartan sin registrar nada; antes esto causaba
  un chorro de "notificaciones viejas" al reiniciar el servicio). Intenta guardar una
  **miniatura de baja resolución** (`saveThumbnailIfAny`, vía `Notification.EXTRA_PICTURE`)
  cuando la notificación trae una y no se encontró el archivo real de WhatsApp — desde v2.23
  SÍ se puede obtener el archivo original de foto/video/audio de WhatsApp específicamente (ver
  `WhatsAppMediaScanner.kt`), esta limitación de "solo miniatura" sigue aplicando para
  cualquier OTRA app (ver limitaciones abajo). **v2.26: `copyMediaToAppStorage()` (nueva)**
  copia el archivo que encontró `WhatsAppMediaScanner` a `filesDir/media/` — el orden de
  `onNotificationPosted` se cambió para que esto corra DESPUÉS de confirmar wallet/app-match +
  dedupe (antes podía consumir un archivo sin nunca asociarlo a nada). Si `findNewMedia`
  devuelve más de un archivo nuevo, el resto genera notificaciones extra propias (antes se
  perdían). **v2.27:** si el primer intento no encuentra nada, `scheduleMediaRetry()` reintenta
  a los 4s/10s en `serviceScope` y actualiza la notificación ya guardada con
  `WalletNotificationStore.setMedia()` si lo encuentra después (confirmado en vivo que sin esto
  se perdían fotos que WhatsApp tardaba en terminar de escribir). **v2.35:** `expectedType`
  (de `WhatsAppMediaScanner`) se calcula ANTES de escanear y se pasa como `preferredType` a
  `findNewMedia()`/`scheduleMediaRetry()` — evita que un medio de un tipo termine adjunto a la
  notificación de otro tipo. También nuevo: `recentMessages` (mapa en memoria, título+texto →
  hora) descarta notificaciones repostadas por WhatsApp/Android con contenido IDÉNTICO dentro
  de `REPOST_WINDOW_MS` (5s) — el dedupe existente por `statusBarNotification.key` no las
  agarraba porque ese `key` cambia entre reposteos aunque el contenido no cambie. Actualiza el "heartbeat"
  (`DisplayPreferences.touchHeartbeat`) en cada evento, para
  que la Home pueda mostrar si el servicio sigue vivo. Expone `requestServiceRebind(context)`
  (llamado desde `MainActivity.onResume`) para pedirle al sistema que reconecte el listener
  si Android lo mató. **Desde v2.16, también corre el long-polling de Telegram en tiempo
  real:** tiene su propio `CoroutineScope` (`serviceScope`), lanzado en `onCreate` y cancelado
  en `onDestroy`, con un loop infinito (`telegramLongPollLoop`) que pide `getUpdates` con
  `timeout=25s` y delega en `TelegramCommandHandler` — se aprovecha que este servicio YA corre
  todo el tiempo que la app tenga permiso de notificaciones, sin necesitar un mecanismo nuevo.
  **v2.21:** la llamada a `getUpdates` queda envuelta en `withTimeout(40_000)` — confirmado en
  vivo que sin esto el loop puede quedarse colgado para siempre en una llamada de red que nunca
  vuelve, sin crashear (ver "Tanda v2.21" arriba para el diagnóstico completo).
- `WalletNotificationNotifier.kt` — notificación del sistema (top deslizable) + `fullScreenIntent`
  (solo para pagos, si "Pantalla completa" está activado). Desde v2.19, el título/nombre/
  descripción del canal salen de `strings.xml` (`context.getString(...)`) en vez de estar
  hardcodeados.
- `WalletOverlay.kt` — el aviso flotante ("Pantalla de aviso"), vistas nativas de Android
  (no Compose). Botones Repetir (azul/blanco) y OK (amarillo/rojo). Desde v2.17: el encabezado
  "Pago recibido: X" y el monto solo se muestran si `item.kind == PAYMENT`; para
  `NotificationKind.ALERT` (`/notificarpantalla`) solo se ve el nombre + el mensaje, sin monto.
  **v2.18:** `show()`/`remove()` saltan al hilo principal con `Handler(Looper.getMainLooper())`
  si se llaman desde otro hilo — crear `View`s fuera del hilo principal tiraba
  "Can't create handler inside thread ... Looper.prepare()" y el aviso nunca se mostraba
  (pasaba con `/notificarpantalla`, que llega por el polling de Telegram en `Dispatchers.IO`).
  **v2.19:** los textos "Pago recibido: X"/"Repetir"/"OK" ahora salen de `strings.xml`
  (`context.getString(R.string.notif_title_payment, ...)`, etc.), mismo string que usan
  `WalletNotificationNotifier` y `PaymentAlertActivity` — cambiar la frase en un solo lugar
  la cambia en los tres.
- `PaymentAlertActivity.kt` — pantalla completa de pago (cuando "Pantalla completa" está ON).
  Mismos colores de botones que el overlay. Desde v2.17: recibe `EXTRA_KIND` en el Intent — con
  `PAYMENT` muestra "Pago recibido: X" + monto; con cualquier otro kind (`ALERT` de
  `/notificarpantalla`) solo el nombre + mensaje. Desde v2.19, esos textos vienen de
  `strings.xml` vía `stringResource(...)` (mismo string que `WalletOverlay`).
- `WalletConfig.kt` / `AppConfig.kt` — registros de billeteras y "aplicaciones generales"
  respectivamente (mismo patrón, `SharedPreferences` con formato `nombre|paquete|enabled`).
  **`detect()` corregido (Paso 1, 2026-09-23):** recibe `packageName`, `title`, `text` por
  separado; si la regla tiene `packageId` exige coincidencia EXACTA de paquete, y solo si
  está vacío cae a buscar el *nombre* como palabra completa (evita falsos positivos como
  "pizzas" conteniendo "zas").
  ✅ **Resuelto por el usuario (2026-09-23):** re-agregó YAPE/YOLO/YASTA/AlToke/Bille desde el
  selector de apps instaladas, ahora con `packageId` real cada una. Quedó un registro viejo
  "YOLO" (vacío, del default original) duplicado con el nuevo "Yolo Pago" — se puede quitar con
  el botón nuevo "Quitar" (ver abajo).
  **`remove()` nuevo (2026-09-23):** `WalletConfig.remove(context, name)` / `AppConfig.remove`
  — antes solo se podía activar/desactivar una regla, no borrarla. Botón "Quitar" junto al
  switch de cada fila en Configuración.
- `WhatsAppMediaScanner.kt` (nuevo, Paso 2 fase 1, 2026-09-23; **reescrito v2.23, ruta
  corregida v2.24, escaneo genérico de respaldo v2.25, subcarpetas por semana v2.28,
  exclusión de resúmenes de grupo v2.31, dos carpetas de audio v2.32, extensiones legítimas
  v2.33, tipos priorizados v2.35, emoji como señal primaria v2.37**) — **v2.37:**
  `EMOJI_TYPES` (nuevo) — 🎥/📷/🎤/🎵 (además de 📄, que ya estaba) como señal PRIMARIA de
  `expectedType()`, antes de caer a la frase exacta — un video/foto enviado CON TEXTO PROPIO
  ya no trae la frase fija ("Envió un video."), solo el emoji + duración sobreviven.
  **v2.35:** `expectedType(title, text)` (nuevo,
  `looksLikeNewMedia()` pasó a ser solo `expectedType(...) != null`) — cada frase de
  `MEDIA_PHRASE_TYPES` sabe a qué tipo pertenece; `findNewMedia()` ganó `preferredType` y
  ahora ORDENA los resultados (tipo esperado primero, luego cercanía de `lastModifiedMs` al
  `postTimeMs`) en vez de devolver "lo que sea que encuentre primero" — corrige medios que se
  cruzaban entre tipos (video↔foto, voz↔video) confirmado con un burst de 9 archivos reales.
  **v2.33:** `IGNORED_EXTENSIONS` ya NO incluye `"db"`/`"log"` — el usuario
  necesita recibir justo esas extensiones (reportes/bases de caja chica de sus empleados) y
  antes se descartaban en silencio, mismo tratamiento que un archivo temporal de WhatsApp.
  **v2.32:**
  `WA_SUBDIRS` pasó de `Map<String, String>` a `Map<String, List<String>>` — el tipo "audio"
  ahora mira TANTO `WhatsApp Voice Notes` (notas de voz grabadas) COMO `WhatsApp Audio`
  (archivos de audio compartidos, ej. un `.mp3` mandado como adjunto — confirmado con
  `adb shell ls` que viven en carpetas distintas). **v2.31:** `GROUP_SUMMARY_TITLE` (regex
  `"(N mensajes)"`) excluye notificaciones-resumen de grupo de `looksLikeNewMedia()` —
  confirmado en vivo que le podían "robar" a otra conversación un archivo real recién llegado.
  **v2.28:** `filesIn(dir, extraDepth=1)` reemplaza `dir.listFiles()` directo — "WhatsApp Voice
  Notes" agrupa los `.opus` en subcarpetas por semana (`202639`, etc.), a diferencia de
  imagen/video que los dejan sueltos; sin esto el audio fallaba el 100% de las veces
  (confirmado con `adb shell ls` directo sobre la carpeta real). Detección de medios NUEVOS de
  WhatsApp: `isWhatsApp`/`looksLikeNewMedia` (con exclusión explícita de sticker/GIF) para
  decidir si vale la pena buscar, `hasMediaPermission` (`Environment.isExternalStorageManager()`
  en Android 11+, permiso clásico en versiones viejas) y `findNewMedia`. **Desde v2.23,
  `findNewMedia` ya NO usa `MediaStore`** (confirmado que `MediaStore.Audio` no indexa notas de
  voz de forma confiable) — lee directo con `java.io.File.listFiles()` las carpetas reales de
  WhatsApp, mismo patrón que `SoncoBot/WhatsAppWatcher.kt`. **`mediaDirsFor()` combina dos
  estrategias:** primero las rutas conocidas — `<raíz>/Media/<subdir>` (clásica) y
  `<raíz>/accounts/<id>/Media/<subdir>` (con cuenta, WhatsApp multi-cuenta; **v2.24, confirmada
  en vivo** — era la que faltaba y por eso v2.23 no encontraba nada) —, y **solo si esas no
  encuentran nada**, cae al escaneo genérico de respaldo (**v2.25**,
  `genericWhatsAppBases()`/`findDirsNamed()`): busca por nombre de carpeta dentro de cualquier
  directorio relacionado con WhatsApp, hasta 4 niveles de profundidad, para cubrir otras
  marcas/modelos sin estructura conocida (pedido explícito del usuario, mismo patrón que
  `findWhatsAppDirs()` de `SoncoBot`). Si ninguna carpeta resulta accesible por NINGÚN camino,
  loguea un DEBUG explícito para diferenciar "ruta mal" de "archivo todavía no llega". Sigue
  filtrando por ventana de tiempo alrededor del `postTime` y recordando rutas ya vistas (no
  `MediaStore` URIs) para no repetir ni recorrer el histórico.
  `MediaMatch.uri` pasó a ser `MediaMatch.path` (`String`, ruta real del archivo). Llamado
  desde `WalletNotificationListener.onNotificationPosted`, que desde v2.26 copia lo
  encontrado a `filesDir/media/` y lo adjunta a la `WalletNotification` real (ver esa clase
  más abajo) — ya no solo loguea. **v2.29:** `MEDIA_KEYWORDS` pasó de palabras sueltas
  ("foto"/"audio"/"video") a las frases EXACTAS que genera WhatsApp — una palabra suelta
  producía falsos positivos con mensajes de texto normal que solo la mencionaban. **v2.30:**
  detección de documentos por el emoji fijo `📄` (`DOCUMENT_EMOJI`), ya que a diferencia de
  foto/video/audio no hay una frase fija (WhatsApp pone el nombre real del archivo). **v2.31:**
  `GROUP_SUMMARY_TITLE` (regex `"(N mensajes)"`/`"(N messages)"`) excluye por completo las
  notificaciones-resumen de un grupo con varios mensajes sin leer — confirmado en vivo que su
  "último mensaje" extraído puede ser uno VIEJO (días atrás) que igual dispara
  `looksLikeNewMedia()` si menciona un documento/foto/etc., robándole a otra conversación el
  archivo real que sí acababa de llegar (pasó con un audio y un PDF genuinos).
- `TelegramClient.kt` — cliente mínimo (sin librerías) de la API HTTP de Telegram Bot:
  `sendMessage`, `sendDocument` (multipart, para el CSV) y `getUpdates` (con `timeoutSeconds`
  opcional desde v2.16 para long-polling real — conexión abierta hasta que llega un mensaje o
  se agota el tiempo). Todo con `HttpURLConnection` + `org.json`.
- `BotTexts.kt` (nuevo v2.19) — TODO el texto del bot de Telegram y los nombres de sus
  comandos (`CMD_NOTIFY`, `CMD_NOTIFY_SCREEN`, `CMD_RENAME`, `CMD_HELP`, `CMD_START`) en un
  solo lugar. `TelegramCommandHandler.kt` construye sus `Regex` a partir de estas constantes
  (`"^/${BotTexts.CMD_NOTIFY}\\b"`), así que renombrar un comando (ej. `/renombrar` →
  `/rebautizar`) es cambiar una constante aquí — la detección y el `/help` quedan consistentes
  solos, sin tocar `TelegramCommandHandler.kt`.
- `TelegramCommandHandler.kt` (nuevo v2.16, ampliado v2.17, sin texto propio desde v2.19) —
  lógica de `/help`, `/notificar` (solo audio), `/notificarpantalla` (audio + pantalla,
  `NotificationKind.ALERT`), `/renombrar` — objeto compartido (recibe `context` explícito),
  usado tanto por el long-polling en tiempo real (`WalletNotificationListener`) como por el
  respaldo periódico (`TelegramSyncWorker`). Si un comando no calza con el patrón completo (ej.
  el usuario mandó el comando y el mensaje en dos envíos separados de Telegram), responde con
  la sintaxis correcta y un ejemplo (texto en `BotTexts.kt`) en vez de quedarse callado. Los
  prefijos usan límite de palabra (`^/notificar\b`) para no confundir `/notificar` con
  `/notificarpantalla`.
- `TelegramConfig.kt` — guarda token del bot / chat id en `SharedPreferences`.
  **Rediseño Paso 3 (2026-09-23, un solo bot para todas las sucursales, decisión del usuario):**
  se cambió `lastUpdateId` (un offset que se le confirmaba a Telegram) por
  `isUpdateProcessed`/`markUpdateProcessed` (un set de `update_id` ya vistos, local por
  dispositivo, tope 300). Motivo: al confirmar el offset ante Telegram, el servidor deja de
  entregar esos mensajes a CUALQUIER otro teléfono que use el mismo bot — el primero que
  consultaba "se comía" los comandos y los demás nunca los veían. **v2.36:** se borraron
  `lastCsvSentAt`/`setLastCsvSentAt`/`lastMediaSentAt`/`setLastMediaSentAt` — el envío
  periódico ya no marca "hasta dónde mandé" con una fecha, consume de las colas nuevas de
  `WalletNotificationStore` (ver esa clase).
- `TelegramSyncWorker.kt` — `Worker` de WorkManager que corre periódicamente (cada
  `intervalMinutes`, mínimo 15, default 30): envía el CSV del historial y revisa comandos como
  **respaldo** del long-polling en tiempo real (ver `WalletNotificationListener`, v2.16) — por
  si Android mata el servicio y se corta el loop. **Pide siempre `offset=0`** (nunca confirma
  nada ante Telegram) y filtra los repetidos con `TelegramConfig.markUpdateIfNew` — mismo
  dedupe atómico que el loop en tiempo real, así que no hay riesgo de procesar un comando dos
  veces. La lógica de los comandos en sí vive en `TelegramCommandHandler.kt`. Se arranca desde
  `MainActivity.onCreate`. Dependencia `androidx.work:work-runtime-ktx:2.11.2` agregada en
  `app/build.gradle.kts`. **v2.21:** `processIncomingCommands` también envuelve su `getUpdates`
  en `withTimeout(20_000)`, mismo motivo que en `WalletNotificationListener`. **v2.34:**
  `sendPendingMedia()` (nueva) reenvía fotos/videos/audios/documentos guardados, generalizando
  `TelegramClient.sendDocument` (ganó parámetro `mimeType`). **v2.36:** tanto
  `sendPendingCsv()` como `sendPendingMedia()` dejaron de leer `WalletNotificationStore.
  history()` (capada en 100) con una marca de tiempo — ahora consumen
  `exportCsvQueue()`/`exportMediaQueue()` (sin ese límite), quitando cada ítem de la cola solo
  cuando se resuelve de verdad (mandado, o descartado por no tener archivo/pesar demasiado).
- `DisplayPreferences.kt` — todas las preferencias del usuario (switches, perfil de voz,
  velocidad, heartbeat, `deviceLabel` —nombre de sucursal, se genera un código alfanumérico
  aleatorio tipo `MS-7K2F9Q` si no fue personalizado, renombrable por Telegram con
  `/renombrar`—, etc.).
- `VoiceProfile.kt` — solo **Varón** y **Mujer** (se quitaron Niño/Niña/Anciano/Anciana por
  pedido explícito). Ver "Pendiente / limitaciones conocidas" sobre la voz de Varón.
- `SpeechEngine.kt` — motor TTS. Intenta usar una voz real del teléfono marcada
  masculina/femenina (`tts.voices`, busca `"male"`/`"female"` en el nombre); si no hay,
  usa pitch/rate de `VoiceProfile` como respaldo. `speakText()` lee texto libre troceando
  por el límite de caracteres del motor (pantalla "Leer"). Soporta `pause()`/`resume()` a
  nivel de trozo (Android TTS no tiene pausa real nativa, así que no es exacto palabra por
  palabra, pero funciona bien para textos largos).
- `AmountSpeech.kt` — parseo de montos ("Bs3.29", "Bs1.2", "Bs. 1.20"...) a frase hablada
  ("3 Bolivianos con 29 centavos"). Regla importante: un solo dígito decimal se rellena a la
  derecha (`.2` → 20 centavos, no 2). Usa "Un" en vez de "1" para concordancia de género.
  **Corregido:** montos con separador de miles (ej. `Bs 2,392.69`) se leían mal como "2 con
  39 centavos" — ahora `parseAmount()` distingue separador de miles vs. decimal por la
  posición del último `.`/`,` y la cantidad de dígitos que le siguen (1-2 dígitos = decimal,
  0 o 3+ = miles), así que `2,392.69` se lee correctamente "Dos mil trecientos noventa y dos
  Bolivianos con sesenta y nueve centavos".
- `NotificationSpeech.kt` — frase hablada para apps "generales" (no billeteras);
  reemplaza URLs por "hay un link".
- `PaymentMessageDetector.kt` — distingue pago real de publicidad de la billetera
  (heurística: requiere monto + frase típica de pago como "recibiste"/"envió"/"yapeo").
  La publicidad se guarda en Historial y en el log, pero no se lee ni dispara overlay,
  pantalla completa, ni la notificación superior del sistema.
- `AdFilterConfig.kt` — filtro de publicidad **manual** (v2.10), complementa al automático
  de arriba: desde una tarjeta del Historial, el usuario marca "🚫 Marcar como publicidad"
  con una frase (editable, pre-rellenada con los primeros 60 caracteres del mensaje).
  Guarda `(billetera, frase)` y cualquier mensaje futuro de esa misma billetera que
  contenga esa frase queda silenciado igual que la publicidad automática. Se administra
  (ver/quitar) en Configuración → "Publicidad bloqueada".
- `InstalledAppsProvider.kt` — lista las apps instaladas con ícono de lanzador (usa el
  `<queries>` del manifest para verlas todas en Android 11+, sin permisos especiales).
  Alimenta la pantalla "Elegir desde apps instaladas" (botón en Billeteras y en Apps
  Generales dentro de Configuración) para agregar billeteras/apps sin escribir el
  nombre de paquete a mano.
- `UpdateManager.kt` — actualización remota: consulta `update.json` (Firebase Hosting),
  descarga con `DownloadManager` e instala. Ver sección "Firebase / GitHub" abajo.
- `WalletNotificationStore.kt` — historial persistido (JSON en `SharedPreferences`),
  export a texto plano y CSV. **Desde v2.19:** `remove(id)`/`removeAll(ids)` borran de
  `HISTORY`/`PENDING`/`PINNED` a la vez (para no dejar ids colgando); `clearHistory()` también
  limpia `PINNED`. `pinnedIds()`/`togglePin(id)` (devuelve `PinToggleResult`:
  `PINNED`/`UNPINNED`/`LIMIT_REACHED`) manejan el set de hasta `MAX_PINNED=2` notificaciones
  fijadas, en `SharedPreferences` bajo la clave `"pinned"`. `setNote(id, texto)` actualiza el
  campo `note` (nuevo en `WalletNotification`, persistido igual que `mediaPath` con
  `optString`/`JSONObject.NULL` para compatibilidad con historiales viejos sin ese campo).
  **v2.36:** `add()` ahora TAMBIÉN agrega cada notificación a `exportCsvQueue()`/
  `exportMediaQueue()` (nuevas, SIN el límite de 100 de `HISTORY` — solo un tope de seguridad
  de 20.000) — `TelegramSyncWorker` las consume y las va vaciando con
  `removeFromCsvQueue(ids)`/`removeFromMediaQueue(id)`. Motivo: el CSV/reenvío de medios
  dependía antes de `history()`, así que si llegaban más de 100 notificaciones entre un envío
  periódico y el siguiente, las más viejas se perdían del Historial ANTES de llegar a
  Telegram — con estas colas independientes, ya no importa cuánto tarde el envío ni cuántas
  notificaciones lleguen mientras tanto. `setMedia()` también actualiza la copia en
  `exportMediaQueue()` para que un reintento exitoso (4s/10s después) no quede "invisible"
  para el reenvío.
- `BackupManager.kt` — backup/restauración en JSON de: preferencias, billeteras, apps
  generales, **y desde v2.13 también token+Chat ID+intervalo de Telegram** (sección
  `"telegram"`, a propósito SIN el nombre de sucursal — cada teléfono conserva el suyo). Es la
  vía pensada para llevar el bot de Telegram a otras sucursales sin escribir el token a mano ni
  guardarlo en el código (ver [[feedback-no-secrets-in-repo]]). **No incluye el historial**
  (decisión de alcance, se exporta aparte). ⚠️ El archivo exportado ahora contiene un secreto
  real si Telegram está configurado — tratarlo como una contraseña.
- `AdminAccess.kt` (nuevo v2.13) — PIN de administrador (`230985`, fijo, sin UI para
  cambiarlo). Triple-tap al logo en Home → diálogo de PIN → si es correcto, revela/permite
  editar el token y Chat ID de Telegram en Configuración durante esa sesión (no se persiste el
  desbloqueo). Pensado para que el personal de una sucursal no pueda ver ni tocar el token.
- `ScoSecretariaLogger.kt` — log de depuración, **solo activo en builds DEBUG**
  (`BuildConfig.DEBUG`), con rotación automática (mantiene las últimas ~800 líneas).
- `AppInfo.kt` — nombre/versión centralizados vía `BuildConfig`.
- `NotificationKind.kt` (enum dentro de `MainActivity.kt`) — `PAYMENT` vs `GENERAL`. Pantalla
  completa/overlay de pago son exclusivos de `PAYMENT`; `GENERAL` solo notifica + habla + historial.

## Funcionalidad implementada (resumen cronológico)

1. Toggle "Pantalla completa" (payment-only) vs "Pantalla de aviso" (overlay), eliminando el
   `AlertDialog` viejo y el selector Nueva/Antigua.
2. Ícono de la app actualizado (avatar con "SCO" en el sombrero).
3. Lectura de montos en bolivianos/centavos con reglas de pronunciación correctas.
4. Renombre completo: proyecto, carpeta, `applicationId`/paquete
   (`com.oscarsonco.scosecretaria` → `com.sco.misecretaria`).
5. Botón Encendido/Apagado del servicio, junto a Configuración.
6. Soporte de "aplicaciones generales" (WhatsApp, SMS, etc.) además de billeteras.
7. Selector de tipo de voz (Varón/Mujer) + control de velocidad de lectura.
8. Botones Repetir (azul/blanco) y OK (amarillo/rojo).
9. Filtro de "N mensajes nuevos" (notificación resumen de WhatsApp) — se ignora y se
   extrae el último mensaje real.
10. "Compartir Aplicación" (comparte el APK instalado vía cualquier app, incluido Bluetooth).
11. Botón "Leer" — pantalla para pegar/escribir texto libre y que se lea en voz alta.
12. Rotación de log de depuración.
13. Heartbeat + indicador de estado del servicio + auto-reconexión (`requestServiceRebind`).
14. Exportar historial a CSV (además de .txt).
15. Backup/restauración de configuración en JSON.
16. Filtro del historial por billetera/aplicación en la pantalla principal.
17. **Actualización remota sin cuentas** (v2.7): botón "Buscar actualización" en
    Configuración. Consulta `https://misecretaria-67c62.web.app/update.json` (Firebase
    Hosting, gratis) y si hay versión nueva, descarga el APK con `DownloadManager` y lanza
    la instalación. El **APK se aloja en GitHub Releases**
    (`https://github.com/OscarSonco/miSecretaria/releases`), NO en Firebase Hosting: el plan
    gratuito (Spark) bloquea por completo archivos `.apk/.exe/.dll/.ipa` sin importar
    permisos (ver `UpdateManager.kt`).
18. **Selector de apps instaladas** (v2.9): botón "Elegir desde apps instaladas" en las
    secciones de Billeteras y Apps Generales — evita escribir el nombre de paquete a mano.
19. **Filtro de publicidad de billeteras** (v2.9): ver `PaymentMessageDetector.kt` arriba.
20. **Compartir como archivo real** (v2.9): "Compartir Historial/CSV/Log" ahora manda un
    adjunto vía `FileProvider`, no un bloque de texto pegado en el chat.
21. **Pausa/Reanudar en pantalla "Leer"** (v2.9), más selector de voz Varón/Mujer ahí mismo
    (usa la misma preferencia global de voz).
22. **Publicación de releases automatizada**: `release.sh` + lanzador
    `miSecretaria_Update.desktop` (doble clic) hacen todo el flujo de una sola vez: compila,
    crea el GitHub Release con el APK adjunto (vía `gh` CLI, sin navegador), actualiza
    `update.json`, despliega a Firebase Hosting, y hace commit+push. Ver sección siguiente.
23. **Filtro de publicidad manual** (v2.10): botón "🚫 Marcar como publicidad" en cada
    tarjeta del Historial, complementa al filtro automático (`AdFilterConfig.kt` arriba).
24. **Integración con Telegram** (sin publicar aún, ver arriba): comandos `/notificar` y
    `/renombrar` vía polling (`TelegramSyncWorker` + WorkManager), envío periódico del CSV
    del historial al bot. Ver `TelegramClient.kt`/`TelegramConfig.kt`/`TelegramSyncWorker.kt`.
25. **Nombre de sucursal aleatorio**: si el `deviceLabel` no fue personalizado, se genera un
    código alfanumérico tipo `MS-7K2F9Q` (en vez de quedar vacío o genérico), renombrable
    después desde Telegram con `/renombrar`.
26. **Botón Atrás físico de Android corregido**: el `BackHandler` de Compose en Configuración/
    Leer/selector de apps ya funcionaba en código, pero le faltaba
    `android:enableOnBackInvokedCallback="true"` en el manifest (requerido desde Android 13+
    para que el botón/gesto físico del sistema le llegue a Compose) — agregado y confirmado.
27. **Ícono de la app → `miSecretaria.jpg`** ✅ hecho en código (2026-09-23), pendiente de
    verlo en el teléfono tras compilar. Historia: una primera pasada (`gen_icons.ps1`)
    generó PNG por densidad pero (a) recortaba el JPG en cuadrado central, cortando sombrero
    y base, (b) el JPG trae un **tablero gris/blanco "quemado"** de falsa transparencia, y
    (c) el ícono adaptativo (`mipmap-anydpi/ic_launcher.xml`, el que usa Android 8+) seguía
    apuntando al avatar viejo "SCO". Solución final con **`gen_icons_v2.ps1`**: quita el
    tablero por relleno desde los bordes (pixeles casi grises y claros; el contorno negro y
    el aro dorado frenan el relleno; incluye compilación C# inline con `Add-Type`), recorta
    al personaje completo y genera en las 5 densidades: `ic_launcher_foreground.png`
    (transparente, personaje al 64% del alto = dentro de la zona segura), `ic_launcher.png`
    (cuadrado, fondo crema) e `ic_launcher_round.png`. Cambios de recursos:
    `ic_launcher.xml`/`ic_launcher_round.xml` ahora usan `@mipmap/ic_launcher_foreground`;
    se quitó `<monochrome>` (con imagen a color solo daba una silueta);
    `drawable/ic_launcher_background.xml` = crema `#F6E7B4` (cambiar también `$bgHex` en el
    script si se quiere otro color); se borraron `drawable/ic_launcher_foreground.xml` y
    `drawable-nodpi/scosecretaria_avatar.png` (verificado sin referencias colgando). Para
    regenerar: `powershell -ExecutionPolicy Bypass -File gen_icons_v2.ps1` (escribe en
    `app/src/main/res`; correrlo desde Windows está bien, solo son imágenes).
    `gen_icons.ps1` y `check_dims.ps1` quedan obsoletos.
28. **Corrección de lectura de montos con separador de miles** (ej. `Bs 2,392.69`): código
    escrito en `AmountSpeech.parseAmount()` (revisado por lectura: `2,392.69` → 2392 Bs +
    69 ctvs). ⚠️ **No compilado ni probado en el equipo aún.** Nota: la frase hablada usa
    dígitos ("2392 Bolivianos"); el "dos mil trescientos noventa y dos" depende del motor TTS.

## Firebase / GitHub — datos del proyecto

- **Proyecto Firebase:** `misecretaria-67c62` (cuenta `oscarorlandosonco@gmail.com`)
- **google-services.json:** en `app/google-services.json` (ya commiteado)
- **Firebase Hosting:** carpeta `public/` → sirve `update.json` únicamente (el APK NO va
  acá, ver arriba). Los archivos en `public/` deben quedar con permisos `644` (no
  ejecutables) o Firebase Hosting rechaza el deploy incluso para archivos permitidos.
- **Firebase App Distribution:** configurado (`appDistributionUploadDebug`) pero **NO es
  el canal para usuarios finales** — requiere que cada tester tenga cuenta de Google y
  acepte una invitación. Sirve solo para testers internos/beta, no para el público general.
- **Repositorio GitHub:** `https://github.com/OscarSonco/miSecretaria` (renombrado desde
  `ScoSecretaria`). Autenticación por HTTPS requiere un **Personal Access Token** (no la
  password de la cuenta, GitHub la bloqueó en 2021) — ya configurado con
  `git config --global credential.helper store`.
- **GitHub CLI (`gh`):** instalado y autenticado (`gh auth login`, cuenta `OscarSonco`).
  Se usa en `release.sh` para crear el Release + subir el APK sin pasar por el navegador.

## Flujo de release — el camino normal (recomendado)

1. Se sube `versionCode`/`versionName` en `app/build.gradle.kts` y se hacen los cambios
   de código de la nueva versión (esto lo hace Claude).
2. El usuario hace **doble clic en `miSecretaria_Update.desktop`** (o corre
   `./release.sh` a mano). Ese script:
   - Lee versionName/versionCode directo de `app/build.gradle.kts` (no hace falta pasarle nada).
   - Compila (`./gradlew assembleDebug`) y copia el APK a `Releases/`.
   - Crea el GitHub Release (tag `v(x.x)`) con el APK adjunto, vía `gh release create`.
   - Regenera `public/update.json` con la URL del asset recién publicado.
   - Corre `firebase deploy --only hosting`.
   - Hace `git add -A && git commit && git push`.
3. Listo — cualquier usuario que abra la app y toque "Buscar actualización" ve la versión nueva.

⚠️ **Nota de entorno:** los lanzadores `.desktop` no cargan `.bashrc`/`nvm`, así que
`release.sh` fuerza el `PATH` a mano con las rutas reales de `firebase` y `gh` en esta
máquina (`/home/beelinkser5max/.nvm/versions/node/v22.23.2/bin` y `/usr/bin`). Si el
usuario reinstala Node/nvm o cambia de versión, hay que actualizar esa línea en `release.sh`.

## Flujo de release — manual (si `release.sh` falla o se quiere paso a paso)

```bash
cd ~/Documents/miSecretaria
./gradlew assembleDebug
mkdir -p Releases public
cp app/build/outputs/apk/debug/app-debug.apk Releases/miSecretariaV(x.x)-debug.apk
gh release create v(x.x) Releases/miSecretariaV(x.x)-debug.apk --repo OscarSonco/miSecretaria --title "miSecretaria v(x.x)" --notes "..."
# actualizar public/update.json a mano con versionCode/versionName/apkUrl/notes
chmod 644 public/update.json
firebase deploy --only hosting
git add -A && git commit -m "Release v(x.x)" && git push
```

## Limpieza del repo (2026-09-23)

A pedido del usuario ("revisa todos los archivos... lo que no es funcional, elimínalo"), se
quitaron del repo (recuperables del historial de git si hiciera falta):
- Los 8 scripts `miSecretariaV2.2` a `V2.11`-`instalar.sh` — huérfanos, `release.sh` nunca los
  llamó; el flujo real siempre fue compilar+copiar a `Releases/` directo.
- `Releases/*.apk` (13 APKs históricos, ~150 MB) — ya no aportan nada: la distribución real es
  GitHub Releases, no la copia local en el repo. `Releases/` queda en `.gitignore`;
  `release.sh` la vuelve a crear sola en cada publicación.
- `ScoSecretaria.png` — logo del nombre viejo del proyecto, cero referencias en el código.
- `gen_icons.ps1` / `check_dims.ps1` — ya marcados obsoletos en este mismo archivo, superados
  por `gen_icons_v2.ps1` (que sí sigue en uso).
- `.firebase/hosting.cHVibGlj.cache` — caché local de la CLI de Firebase, comiteado por error;
  ahora en `.gitignore`, se regenera solo.
- ⚠️ Nota sobre el tamaño del repo: quitar `Releases/*.apk` del *tracking* no reduce el tamaño
  del historial de git ya existente (~150 MB siguen en commits viejos) — eso requeriría
  reescribir el historial (`git filter-repo`/BFG + force-push), una operación destructiva que
  no se hizo; solo se pidió si el usuario lo pide explícitamente en el futuro.

## Pendiente / limitaciones conocidas

- **YASTA sin packageId real:** `WalletConfig.defaults` trae `YASTA` con `packageId=""`
  desde el código original (pre-Claude) — nunca se detecta porque busca literalmente la
  palabra "yasta" en el mensaje (que no aparece en notificaciones reales). No se corrigió
  con un paquete adivinado (se sospechaba `com.busa.wallet`, sin confirmar con certeza) —
  queda resuelto cuando el usuario la vuelva a agregar desde el selector de apps instaladas
  (autocompleta el paquete correcto; si queda vacío, ahora se ve en rojo en Configuración).
  Lo mismo puede pasar con "Bille" u otras billeteras agregadas sin paquete.
- **Audio/video de WhatsApp — reproducción automática en cola y copia de medios borrados:** ✅ **DECIDIDO 2026-09-23: opción b (solo archivos NUEVOS) — plan en "PASOS SIGUIENTES", Paso 2.**
  pedido explícitamente por el usuario, **no implementado**, y tiene una limitación real de
  Android de fondo que hay que resolver antes de programar nada:
  `NotificationListenerService` (la única vía que usa la app para leer WhatsApp sin rootear
  el teléfono) **no expone el archivo multimedia original** de otra app — solo puede acceder
  a lo que la notificación trae embebido, que en el mejor caso es una miniatura de baja
  resolución (`Notification.EXTRA_PICTURE`, ya usado en `saveThumbnailIfAny()` de
  `WalletNotificationListener.kt`). No hay audio, ni video, ni documento real accesible por
  esta vía, y por lo tanto tampoco es posible "reproducir en cola" ni "guardar una copia
  antes de que el remitente lo borre" para esos tipos de archivo tal como se pidió. Caminos
  alternativos discutidos pendientes de decidir con el usuario antes de tocar código:
  a) Accessibility Service (más invasivo, más frágil ante actualizaciones de WhatsApp, pero
     sí puede acceder a la UI/contenido real reproducido en pantalla);
  b) leer directo la base de datos/carpeta de medios de WhatsApp (no soportado oficialmente,
     roto por Scoped Storage en versiones recientes de Android);
  c) limitar el alcance a lo que sí es viable hoy (miniatura de imagen ya implementada) y
     dejar audio/video fuera de esta función.
  Tampoco está implementado el checklist de "qué tipo de archivo nuevo guardar" ni el
  reenvío automático de esos medios al bot de Telegram — depende de resolver lo anterior.
  **Actualización 2026-09-24 — estado real confirmado con uso en vivo (no solo teoría):** la
  opción b (`WhatsAppMediaScanner.kt`) SÍ está implementada (fase 1, solo logging) y **SÍ
  funciona para fotos y video** — confirmado con capturas de pantalla del usuario y el log del
  teléfono: dos fotos reales detectadas y encontradas en `MediaStore.Images` con su ruta y
  nombre correctos. **Para audio (notas de voz), NO funciona — confirmado, no es una duda ya**:
  la notificación de WhatsApp para un audio SÍ se detecta bien por palabra clave ("🎤 Mensaje de
  voz (0:16)" matchea con `MEDIA_KEYWORDS`), pero el archivo `.opus` nunca aparece en
  `MediaStore.Audio` — probado con una foto y un audio llegados casi al mismo segundo (mismo
  chat, mismo minuto): la foto sí se encontró, el audio no. Es una limitación real de cómo
  Android indexa (o no indexa) archivos de audio de apps de terceros, no un bug de
  `WhatsAppMediaScanner`/`WalletNotificationListener`. **✅ RESUELTO en v2.23 (ver "Tanda
  v2.23" más abajo):** en vez de perseguir MediaStore, se lee directo el archivo del disco —
  igual que ya lo tenía resuelto el proyecto hermano `SoncoBot`
  (`~/Documents/SoncoBot/app/.../WhatsAppWatcher.kt`, revisado a pedido del usuario). Esto
  aplica a fotos, video Y audio por igual — ya no hace falta distinguir "esto sí, esto no".
- **Voz de Varón:** puede seguir sonando parecida a mujer en teléfonos cuyo motor TTS no
  tenga una voz masculina real instalada para español — en ese caso cae al respaldo de
  pitch bajo (0.48), que tiene un límite físico de cuánto puede "engrosar" una voz sintética
  femenina. Si sigue sin convencer, la solución robusta sería que el usuario instale/verifique
  qué voces trae Google TTS en Ajustes → Accesibilidad → Texto a voz → Motor de Google → Instalar
  datos de voz, y confirmar si hay alguna voz masculina en español disponible en ese equipo.
- **Estilo iOS:** no implementado. Jetpack Compose puede imitar visualmente el look de iOS
  (tipografía, bordes redondeados, back-swipe, etc.) pero seguirá siendo una app Android
  (esto no la convierte en una app de iPhone real). Para una app nativa de iPhone habría que
  reescribirla en Swift/SwiftUI — proyecto aparte, no un simple reskin.
- **Actualización remota (OTA):** no implementada. Opciones discutidas con el usuario:
  a) chequeo manual de versión contra un JSON/API propio + descarga de APK actualizado
     (com o Google no lo permite vía Play Store porque la app no está publicada ahí);
  b) Firebase App Distribution para testers;
  c) publicar en Play Store (Internal Testing) para tener updates automáticos reales.
  Ver la conversación completa para el detalle de pros/contras de cada opción.
- El "heartbeat" de estado del servicio es un heurístico: se actualiza con cualquier
  notificación que llegue al teléfono (de cualquier app), no solo las relevantes. Si el
  teléfono pasa muchas horas sin notificaciones de ningún tipo, puede mostrar "sin actividad
  reciente" aunque el servicio siga vivo. Umbral actual: 6 horas.
- El backup no incluye el historial de notificaciones (se exporta aparte en .txt/.csv).

## Gotchas de entorno (importantes, ya debuggeados en la conversación)

- **NO compilar desde Windows vía `Z:\` (SMB).** Ya causó builds fantasma con
  `UP-TO-DATE` falso por desincronización de metadatos entre SMB y el filesystem nativo
  Linux. Compilar **siempre** desde una terminal nativa en el Debian
  (`cd ~/Documents/miSecretaria && ./gradlew ...`).
- Si un build se cuelga sin salida o dice "permission denied" de forma persistente:
  `pkill -f gradle`, borrar `.gradle`/`app/build`/`.kotlin`, y reintentar con
  `--no-configuration-cache --rerun-tasks`.
- `git diff`/`git log` sin `--no-pager` se queda esperando en `less` (aparece `(END)`),
  hay que presionar `q`. Se puede evitar con `git config --global core.pager cat`.
- El `applicationId` se cambió de `com.oscarsonco.scosecretaria` a `com.sco.misecretaria`:
  para Android son apps distintas. Si aparece una instalada con el paquete viejo, es un
  resto y se puede desinstalar. El permiso "Acceso a notificaciones" se otorga por
  `applicationId`, así que cualquier reinstalación con un paquete nuevo requiere volver
  a otorgarlo en Ajustes.

## Historial de conversación

Este archivo resume el estado técnico. El detalle completo de decisiones, diagnósticos de
bugs (permission denied, configuration cache, WhatsApp "N mensajes nuevos", etc.) y el
razonamiento detrás de cada elección de diseño está en la conversación de Claude donde se
construyó esto — puede ser útil pegarle este archivo a Claude al empezar una sesión nueva
como contexto inicial, y contarle en qué versión/feature seguir.
