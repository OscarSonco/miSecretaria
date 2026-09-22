# CLAUDE.md — miSecretaria

Estado del proyecto para continuar el desarrollo desde otra sesión/cuenta de Claude.

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
- **Versión actual:** `versionCode=2004`, `versionName="2.4"` (ver `app/build.gradle.kts`)
- **Esquema de versionCode:** `major*1000 + minor` (ej. 2.1 → 2001, 2.700 → 2700), para poder
  hacer muchos builds de prueba (2.1, 2.2, ... 2.700) antes de saltar a la siguiente versión
  entera (3.0) cuando quede estable.
- **minSdk 26 / targetSdk 37**
- **JAVA_HOME de build:** `/home/beelinkser5max/Descargas/android-studio/jbr`

## Cómo compilar e instalar

Cada versión tiene su propio script `miSecretariaV(x.x)-instalar.sh` en la raíz del proyecto,
que compila, copia el APK a `Releases/miSecretariaV(x.x)-debug.apk` y lo instala por ADB:

```bash
cd ~/Documents/miSecretaria
chmod +x miSecretariaV2.4-instalar.sh
./gradlew clean --no-configuration-cache
rm -rf app/build .kotlin
./gradlew assembleDebug --stacktrace --no-configuration-cache --rerun-tasks
./miSecretariaV2.4-instalar.sh
adb shell dumpsys package com.sco.misecretaria | grep -E "versionName|versionCode"
```

Al crear una versión nueva: subir `versionCode`/`versionName` en `app/build.gradle.kts`, y
crear el siguiente `miSecretariaV(x.x)-instalar.sh` (copiar el anterior y cambiar el número).
**No hace falta tocar strings de versión a mano en el código** — todo pasa por `AppInfo.kt`,
que lee `BuildConfig.VERSION_NAME`/`VERSION_CODE`.

## Mapa de archivos (app/src/main/java/com/sco/misecretaria/)

- `MainActivity.kt` — UI Compose completa: navegación (Home/Configuración/Leer), historial
  con filtro por billetera/app, botón Encendido/Apagado, indicador de estado del servicio,
  guardar/compartir (historial .txt, .csv, log, backup .json), compartir APK, pantalla "Leer"
  (texto libre → voz).
- `WalletNotificationListener.kt` — `NotificationListenerService`: detecta pagos/apps
  generales, arma el `WalletNotification`, dispara notificación/overlay/pantalla
  completa/voz según corresponda. Filtra notificaciones-resumen de grupo (`FLAG_GROUP_SUMMARY`,
  el "3 mensajes nuevos" de WhatsApp) y extrae el último mensaje real vía `MessagingStyle`.
  Actualiza el "heartbeat" (`DisplayPreferences.touchHeartbeat`) en cada evento, para que la
  Home pueda mostrar si el servicio sigue vivo. Expone `requestServiceRebind(context)` (llamado
  desde `MainActivity.onResume`) para pedirle al sistema que reconecte el listener si Android
  lo mató.
- `WalletNotificationNotifier.kt` — notificación del sistema (top deslizable) + `fullScreenIntent`
  (solo para pagos, si "Pantalla completa" está activado).
- `WalletOverlay.kt` — el aviso flotante ("Pantalla de aviso"), vistas nativas de Android
  (no Compose). Botones Repetir (azul/blanco) y OK (amarillo/rojo).
- `PaymentAlertActivity.kt` — pantalla completa de pago (cuando "Pantalla completa" está ON).
  Mismos colores de botones que el overlay.
- `WalletConfig.kt` / `AppConfig.kt` — registros de billeteras y "aplicaciones generales"
  respectivamente (mismo patrón, `SharedPreferences` con formato `nombre|paquete|enabled`).
- `DisplayPreferences.kt` — todas las preferencias del usuario (switches, perfil de voz,
  velocidad, heartbeat, etc.).
- `VoiceProfile.kt` — solo **Varón** y **Mujer** (se quitaron Niño/Niña/Anciano/Anciana por
  pedido explícito). Ver "Pendiente / limitaciones conocidas" sobre la voz de Varón.
- `SpeechEngine.kt` — motor TTS. Intenta usar una voz real del teléfono marcada
  masculina/femenina (`tts.voices`, busca `"male"`/`"female"` en el nombre); si no hay,
  usa pitch/rate de `VoiceProfile` como respaldo. `speakText()` lee texto libre troceando
  por el límite de caracteres del motor (pantalla "Leer").
- `AmountSpeech.kt` — parseo de montos ("Bs3.29", "Bs1.2", "Bs. 1.20"...) a frase hablada
  ("3 Bolivianos con 29 centavos"). Regla importante: un solo dígito decimal se rellena a la
  derecha (`.2` → 20 centavos, no 2). Usa "Un" en vez de "1" para concordancia de género.
- `NotificationSpeech.kt` — frase hablada para apps "generales" (no billeteras);
  reemplaza URLs por "hay un link".
- `WalletNotificationStore.kt` — historial persistido (JSON en `SharedPreferences`),
  export a texto plano y CSV.
- `BackupManager.kt` — backup/restauración en JSON de: preferencias, billeteras, apps
  generales. **No incluye el historial** (decisión de alcance, se exporta aparte).
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

## Firebase / GitHub — datos del proyecto

- **Proyecto Firebase:** `misecretaria-67c62` (cuenta `oscarorlandosonco@gmail.com`)
- **google-services.json:** en `app/google-services.json` (ya commiteado)
- **Firebase Hosting:** carpeta `public/` → sirve `update.json` únicamente (el APK NO va
  acá, ver arriba). Desplegar con `firebase deploy --only hosting`. Los archivos en
  `public/` deben quedar con permisos `644` (no ejecutables) o Firebase Hosting rechaza el
  deploy incluso para archivos permitidos.
- **Firebase App Distribution:** configurado (`appDistributionUploadDebug`) pero **NO es
  el canal para usuarios finales** — requiere que cada tester tenga cuenta de Google y
  acepte una invitación. Sirve solo para testers internos/beta, no para el público general.
  Login vía `firebase login --no-localhost` (headless, pega la URL en cualquier navegador).
- **Repositorio GitHub:** `https://github.com/OscarSonco/miSecretaria` (renombrado desde
  `ScoSecretaria`). Autenticación por HTTPS requiere un **Personal Access Token** (no la
  password de la cuenta, GitHub la bloqueó en 2021) — generar en
  https://github.com/settings/tokens con permiso `repo`, y usarlo como "password" al hacer
  `git push`. Con `git config --global credential.helper store` se guarda tras la primera vez.
- **Flujo de release completo** (cada versión nueva):
  1. Compilar: `./gradlew assembleDebug` → copiar a `Releases/miSecretariaV(x.x)-debug.apk`.
  2. Subir el APK como asset de un nuevo **GitHub Release** (tag `v(x.x)`) — al crear el
     release en la web, adjuntar el archivo en la zona **"Attach binaries"** (no en el
     cuadro de texto de notas, que solo acepta imágenes/documentos).
  3. Actualizar `public/update.json` (versionCode, versionName, apkUrl al asset de GitHub,
     notas) y `chmod 644 public/update.json`.
  4. `firebase deploy --only hosting`.
  5. `git add -A && git commit && git push`.


## Pendiente / limitaciones conocidas

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
