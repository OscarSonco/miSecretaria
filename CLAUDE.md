# CLAUDE.md — miSecretaria

Estado del proyecto para continuar el desarrollo desde otra sesión/cuenta de Claude.

## ESTADO ACTUAL (actualizado 2026-09-24)

Código en disco = v2.19 (`versionCode=2019`) — el usuario ya publicó e instaló hasta 2.17;
2.18 y 2.19 son fixes/features sin publicar todavía (build Interna de 2.19 ya generada). Reglas
de trabajo con el usuario:
- ADB es SOLO para diagnóstico técnico de Claude (logcat, `dumpsys`, `run-as` para leer el log
  interno, `content query` sobre MediaStore) — **nunca para instalar**; el usuario instala
  siempre por su cuenta, vía "Buscar actualización" en la app.
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
| Guardar copia de medios borrados + reenviar al bot + checklist de tipos | ❌ no implementado; depende de validar 2a-2c en el teléfono primero (ver Paso 2, 2d-2h) |
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
- 2f. Reenvío al bot de Telegram: generalizar `TelegramClient.sendDocument` (el multipart ya
  existe) o añadir `sendVoice/sendAudio/sendVideo/sendPhoto`. Límite de 50 MB por archivo para bots.
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
- **Versión actual:** `versionCode=2019`, `versionName="2.19"` (ver `app/build.gradle.kts`,
  subida 2026-09-24). Compila limpio en el Debian. **Aún no publicada** — falta que el usuario
  presione `miSecretaria_Update.desktop` (o corra `./release.sh`) cuando quiera cerrarla. Antes
  de publicar, probar en el teléfono lo que quedó pendiente de verificar en vivo: permiso de
  medios + detección real de foto/audio/video de WhatsApp (Paso 2), guardar/probar el bot de
  Telegram en Configuración (Paso 3), y toda la tanda v2.19 (borrar/fijar/copiar/nota +
  centralización de textos, ver esa sección — nada de eso se ha visto en el teléfono todavía).
- **Esquema de versionCode:** `major*1000 + minor` (ej. 2.1 → 2001, 2.700 → 2700), para poder
  hacer muchos builds de prueba (2.1, 2.2, ... 2.700) antes de saltar a la siguiente versión
  entera (3.0) cuando quede estable.
- **minSdk 26 / targetSdk 37**
- **JAVA_HOME de build:** `/home/beelinkser5max/Descargas/android-studio/jbr`

## Cómo compilar e instalar

**Descontinuado (2026-09-23):** ya NO se usan scripts `miSecretariaV(x.x)-instalar.sh` por
versión (compilaban e instalaban por ADB). El usuario instala siempre desde la app misma
("Buscar actualización"), y ADB queda solo para diagnóstico técnico de Claude — nunca para
instalar (ver [[feedback-workflow-cadence]] en memoria). Se borraron todos los `.sh` viejos
(V2.2 a V2.11) del repo; `release.sh` nunca dependió de ellos.

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
  (borrado inmediato de esa notificación). Arriba del historial hay un botón "Seleccionar" que
  activa casillas de verificación por tarjeta para borrar varias a la vez
  ("Eliminar seleccionadas (n)", con confirmación) y un botón "Vaciar historial" (con
  confirmación) para borrar todo. Las notificaciones fijadas se reordenan siempre al principio
  de la lista mostrada (`ordered = pinned + rest`), sin importar el filtro activo.
- `WalletNotificationListener.kt` — `NotificationListenerService`: detecta pagos/apps
  generales, arma el `WalletNotification`, dispara notificación/overlay/pantalla
  completa/voz según corresponda. Filtra notificaciones-resumen de grupo (`FLAG_GROUP_SUMMARY`,
  el "3 mensajes nuevos" de WhatsApp) y extrae el último mensaje real vía `MessagingStyle`.
  **Ignora notificaciones "revividas"** al reconectar (más de 2 min de antigüedad según
  `statusBarNotification.postTime` → se descartan sin registrar nada; antes esto causaba
  un chorro de "notificaciones viejas" al reiniciar el servicio). Intenta guardar una
  **miniatura de baja resolución** (`saveThumbnailIfAny`, vía `Notification.EXTRA_PICTURE`)
  cuando la notificación trae una — es lo único accesible por esta vía; NO hay forma de
  obtener el archivo original de foto/video/audio/documento de otra app (ver limitaciones
  abajo). Actualiza el "heartbeat" (`DisplayPreferences.touchHeartbeat`) en cada evento, para
  que la Home pueda mostrar si el servicio sigue vivo. Expone `requestServiceRebind(context)`
  (llamado desde `MainActivity.onResume`) para pedirle al sistema que reconecte el listener
  si Android lo mató. **Desde v2.16, también corre el long-polling de Telegram en tiempo
  real:** tiene su propio `CoroutineScope` (`serviceScope`), lanzado en `onCreate` y cancelado
  en `onDestroy`, con un loop infinito (`telegramLongPollLoop`) que pide `getUpdates` con
  `timeout=25s` y delega en `TelegramCommandHandler` — se aprovecha que este servicio YA corre
  todo el tiempo que la app tenga permiso de notificaciones, sin necesitar un mecanismo nuevo.
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
- `WhatsAppMediaScanner.kt` (nuevo, Paso 2 fase 1, 2026-09-23) — detección de medios NUEVOS
  de WhatsApp: `isWhatsApp`/`looksLikeNewMedia` (con exclusión explícita de sticker/GIF) para
  decidir si vale la pena buscar, `hasMediaPermission` (permisos de medios según SDK) y
  `findNewMedia` (consulta `MediaStore` Images/Video/Audio en una ventana de tiempo alrededor
  del `postTime` de la notificación, filtra por ruta que contenga "WhatsApp", y recuerda URIs
  ya vistas para no repetir ni recorrer el histórico). Llamado desde
  `WalletNotificationListener.onNotificationPosted`. Por ahora SOLO loguea lo que encuentra
  (`ScoSecretariaLogger`) — no reproduce, copia ni reenvía nada (eso es 2d-2h, pendiente).
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
  consultaba "se comía" los comandos y los demás nunca los veían.
- `TelegramSyncWorker.kt` — `Worker` de WorkManager que corre periódicamente (cada
  `intervalMinutes`, mínimo 15, default 30): envía el CSV del historial y revisa comandos como
  **respaldo** del long-polling en tiempo real (ver `WalletNotificationListener`, v2.16) — por
  si Android mata el servicio y se corta el loop. **Pide siempre `offset=0`** (nunca confirma
  nada ante Telegram) y filtra los repetidos con `TelegramConfig.isUpdateProcessed` — mismo
  dedupe que el loop en tiempo real, así que no hay riesgo de procesar un comando dos veces.
  La lógica de los comandos en sí vive en `TelegramCommandHandler.kt`. Se arranca desde
  `MainActivity.onCreate`. Dependencia `androidx.work:work-runtime-ktx:2.11.2` agregada en
  `app/build.gradle.kts`.
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
