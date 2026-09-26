package com.sco.misecretaria

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

private const val STALE_NOTIFICATION_MS = 2 * 60 * 1000L
private const val TELEGRAM_LONGPOLL_TIMEOUT_SEC = 25L

/** v2.35: ventana para descartar reposteos — confirmado en vivo (envío de 9 archivos casi
 * simultáneos) que WhatsApp/Android puede volver a publicar la MISMA notificación (título y
 * texto idénticos, incluida la duración de un video/audio) varias veces en pocos segundos sin
 * que haya nada nuevo de verdad — el dedupe existente (`statusBarNotification.key` + título +
 * texto) no lo agarra porque el `key` cambia entre reposteos aunque el contenido sea idéntico.
 * Esto producía tarjetas duplicadas en el Historial ("a veces repite mensajes anteriores"). */
private const val REPOST_WINDOW_MS = 5_000L

class WalletNotificationListener : NotificationListenerService() {
    companion object {
        /** Le pide al sistema que reconecte el listener si Android lo mató (ahorro de batería, etc). */
        fun requestServiceRebind(context: Context) {
            runCatching { requestRebind(ComponentName(context, WalletNotificationListener::class.java)) }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** "título|texto" -> hora del último aceptado con ese mismo contenido — ver
     * `REPOST_WINDOW_MS`. Se limpia solo (entradas viejas se descartan al revisar), nunca
     * crece sin límite. */
    private val recentMessages = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        WalletNotificationStore.init(applicationContext)
        SpeechEngine.init(this)
        ScoSecretariaLogger.info(this, "Servicio de notificaciones iniciado")
        serviceScope.launch { telegramLongPollLoop() }
    }

    /**
     * Comandos de Telegram casi en tiempo real mientras este servicio esté vivo: usa
     * long-polling (`timeout=25s` en `getUpdates`, la conexión queda abierta esperando un
     * mensaje nuevo en vez de consultar a cada rato) para no depender del intervalo de
     * `TelegramSyncWorker` (que igual sigue como respaldo si este loop se corta, ej. si
     * Android mata el servicio). Mismo dedupe (`TelegramConfig.isUpdateProcessed`) que el
     * respaldo, así que no hay riesgo de procesar un comando dos veces.
     */
    private suspend fun telegramLongPollLoop() {
        while (serviceScope.isActive) {
            val token = TelegramConfig.botToken(applicationContext)
            val chatId = TelegramConfig.chatId(applicationContext)
            if (token.isBlank() || chatId.isBlank()) {
                delay(30_000L)
                continue
            }
            val deviceLabel = DisplayPreferences.deviceLabel(applicationContext)
            // `getUpdates` es una llamada bloqueante (HttpURLConnection, no una función
            // suspend real) — sus propios connectTimeout/readTimeout no siempre alcanzan a
            // cortar una conexión colgada en condiciones de red raras (confirmado en vivo:
            // el loop quedó congelado ~13h sin respuesta tras un update de la app, sin volver
            // a intentar nada, hasta que se forzó a cerrar la app). `withTimeout` no puede
            // interrumpir la llamada bloqueante en sí, pero sí evita que ESTE loop se quede
            // esperando para siempre: si se agota, se descarta el resultado y se reintenta en
            // la próxima vuelta — autorecuperable, sin depender de que el usuario reinicie la app.
            val updates = runCatching {
                withTimeout((TELEGRAM_LONGPOLL_TIMEOUT_SEC + 15) * 1000L) {
                    TelegramClient.getUpdates(token, offset = 0, timeoutSeconds = TELEGRAM_LONGPOLL_TIMEOUT_SEC)
                }
            }.getOrDefault(emptyList())
            for (update in updates) {
                if (!TelegramConfig.markUpdateIfNew(applicationContext, update.updateId)) continue
                if (update.chatId != chatId) continue
                TelegramCommandHandler.handle(applicationContext, update.text.trim(), deviceLabel)
            }
            // Si no hubo nada (o falló la conexión) esperar un poco antes de reintentar, para
            // no martillar la red si Telegram/la red están caídos.
            if (updates.isEmpty()) delay(1_000L)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        DisplayPreferences.touchHeartbeat(this)
        ScoSecretariaLogger.info(this, "Listener conectado")
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification) {
        DisplayPreferences.touchHeartbeat(this)
        if (statusBarNotification.packageName == packageName) return
        if (!DisplayPreferences.serviceEnabled(this)) return
        // Al reconectar, Android puede reenviar notificaciones que ya estaban en la bandeja
        // desde antes (no son nuevas de verdad) — se ignoran por completo, sin registrar nada.
        if (System.currentTimeMillis() - statusBarNotification.postTime > STALE_NOTIFICATION_MS) return
        val notification = statusBarNotification.notification

        // Apps de mensajería (WhatsApp, Messenger, etc.) publican una notificación "resumen"
        // por grupo cuando hay varios mensajes sin leer (ej. "3 mensajes nuevos"). Esa
        // notificación no trae contenido real, así que se ignora por completo.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val title = notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = notification.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val fallbackText = listOf(text, bigText).filter { it.isNotBlank() }.distinct().joinToString(" ")
        // Para notificaciones de chat con varios mensajes acumulados, el texto "colapsado"
        // (EXTRA_TEXT) suele ser "N mensajes nuevos". Se extrae el último mensaje real en
        // su lugar, desde el estilo de mensajería o las líneas expandidas de la notificación.
        val completeText = latestMessageText(notification) ?: fallbackText
        val packageName = statusBarNotification.packageName

        val walletMatch = WalletConfig.detect(this, packageName, title, completeText)
        val appMatch = if (walletMatch == null) AppConfig.detect(this, packageName, title, completeText) else null
        val label = walletMatch ?: appMatch
        if (label == null) {
            ScoSecretariaLogger.debug(this, "Sin coincidencia: pkg=${statusBarNotification.packageName} title=\"$title\"")
            return
        }
        val kind = if (walletMatch != null) NotificationKind.PAYMENT else NotificationKind.GENERAL

        val dedupeKey = "${statusBarNotification.key}|$title|$completeText"
        if (!WalletNotificationStore.markIfNew(dedupeKey)) return

        // v2.35: descarta reposteos del mismo contenido dentro de REPOST_WINDOW_MS (ver esa
        // constante) — distinto del dedupeKey de arriba, que no los agarra porque el `key` de
        // Android cambia entre reposteos aunque el contenido sea idéntico.
        val now = System.currentTimeMillis()
        recentMessages.entries.removeAll { now - it.value > REPOST_WINDOW_MS }
        val repostKey = "$title|$completeText"
        if (recentMessages.containsKey(repostKey)) return
        recentMessages[repostKey] = now

        val message = listOf(title, completeText).filter { it.isNotBlank() }.distinct().joinToString(": ")
        if (message.isBlank()) return

        // Paso 2 de CLAUDE.md: si WhatsApp anuncia un medio nuevo (foto/audio/video), busca el
        // archivo real y se queda con una COPIA propia (sobrevive si el remitente lo borra).
        // Solo se busca/consume DESPUÉS de confirmar que esta notificación sí se va a guardar
        // (arriba) — así no se marca un archivo como "ya visto" sin nunca mostrarlo.
        var mediaPath: String? = null
        var mediaType: String? = null
        var extraMediaMatches: List<WhatsAppMediaScanner.MediaMatch> = emptyList()
        val expectedMediaType = if (WhatsAppMediaScanner.isWhatsApp(packageName)) WhatsAppMediaScanner.expectedType(title, completeText) else null
        val looksLikeMedia = expectedMediaType != null
        // v2.37: diagnóstico — confirmado en vivo que un video/documento/foto puede quedar sin
        // NINGÚN rastro en el log (ni "Medio nuevo" ni "sin encontrar") cuando `expectedType`
        // devuelve null para un mensaje que a simple vista sí parecía un medio. Este log
        // (DEBUG, solo para WhatsApp) deja constancia de qué tipo se calculó para CADA
        // notificación de WhatsApp aceptada, así la próxima vez que pase se puede confirmar de
        // una si el problema es la detección (expectedType da null) o algo posterior.
        if (WhatsAppMediaScanner.isWhatsApp(packageName)) {
            ScoSecretariaLogger.debug(this, "WhatsApp texto=\"${completeText.take(60)}\" tipo detectado=${expectedMediaType ?: "ninguno"}")
        }
        var retryMedia = false
        if (looksLikeMedia) {
            if (WhatsAppMediaScanner.hasMediaPermission(this)) {
                val matches = WhatsAppMediaScanner.findNewMedia(this, statusBarNotification.postTime, preferredType = expectedMediaType)
                val first = matches.firstOrNull()
                if (first != null) {
                    mediaPath = copyMediaToAppStorage(first)
                    mediaType = first.type
                    if (mediaPath != null) ScoSecretariaLogger.info(this, "Medio nuevo de WhatsApp: ${first.type} \"${first.displayName}\" copiado a $mediaPath")
                    extraMediaMatches = matches.drop(1)
                } else {
                    // El archivo puede no estar listo todavía en este instante exacto (WhatsApp
                    // sigue guardándolo) — se reintenta más tarde en vez de darlo por perdido
                    // (confirmado en vivo: pasaba seguido con esta sola pasada).
                    ScoSecretariaLogger.debug(this, "Notificación de medio de WhatsApp sin encontrar el archivo todavía en las carpetas de WhatsApp (title=\"$title\")")
                    retryMedia = true
                }
            } else {
                ScoSecretariaLogger.debug(this, "Medio de WhatsApp detectado pero falta el permiso de acceso a medios")
            }
        }
        if (mediaPath == null) {
            mediaPath = saveThumbnailIfAny(notification)
            if (mediaPath != null) mediaType = "image"
        }
        val item = WalletNotification(UUID.randomUUID().toString(), label, title, message, WalletNotificationStore.now(), kind, mediaPath = mediaPath, mediaType = mediaType)
        WalletNotificationStore.add(item)
        if (retryMedia) scheduleMediaRetry(item.id, statusBarNotification.postTime, expectedMediaType)

        // Si en la misma ventana llegó más de un archivo nuevo (ej. varias fotos seguidas),
        // el resto se guarda como notificaciones aparte — no se pierden, cada una con su copia.
        extraMediaMatches.forEach { match ->
            val extraPath = copyMediaToAppStorage(match) ?: return@forEach
            ScoSecretariaLogger.info(this, "Medio nuevo de WhatsApp: ${match.type} \"${match.displayName}\" copiado a $extraPath")
            WalletNotificationStore.add(
                WalletNotification(UUID.randomUUID().toString(), label, title, "${match.type} adjunto: ${match.displayName}", WalletNotificationStore.now(), kind, mediaPath = extraPath, mediaType = match.type)
            )
        }
        val isPromo = (kind == NotificationKind.PAYMENT && !PaymentMessageDetector.looksLikePayment(message)) ||
            AdFilterConfig.isBlocked(this, label, message)
        ScoSecretariaLogger.info(this, "Notificación aceptada de $label (${kind.name}${if (isPromo) ", publicidad: silenciada" else ""})")
        if (!isPromo) {
            WalletNotificationNotifier.show(this, item)
            if (kind == NotificationKind.PAYMENT && DisplayPreferences.alertEnabled(this) && !DisplayPreferences.fullScreenEnabled(this)) WalletOverlay.show(this, item)
            if (DisplayPreferences.speechEnabled(this)) SpeechEngine.speak(this, item)
        }
    }

    /**
     * Guarda una copia local de la miniatura adjunta a la notificación, si trae una
     * (ej. fotos de WhatsApp con BigPictureStyle). NO es el archivo original ni garantiza
     * buena calidad — es lo único accesible vía NotificationListenerService; Android no
     * expone el audio/video/documento real de otra app por esta vía.
     */
    private fun saveThumbnailIfAny(notification: Notification): String? {
        val bitmap = runCatching {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= 33) notification.extras?.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
            else notification.extras?.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap
        }.getOrNull() ?: return null
        return runCatching {
            val dir = File(filesDir, "media").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out) }
            file.absolutePath
        }.getOrNull()
    }

    /**
     * Paso 2d de CLAUDE.md: copia el archivo real de WhatsApp (que `WhatsAppMediaScanner`
     * encontró en sus carpetas) a almacenamiento propio de la app — así la copia sobrevive
     * aunque el remitente use "eliminar para todos" o WhatsApp borre el original. Se copia
     * (no se mueve): el archivo de WhatsApp se deja intacto.
     */
    private fun copyMediaToAppStorage(match: WhatsAppMediaScanner.MediaMatch): String? = runCatching {
        val source = File(match.path)
        val dir = File(filesDir, "media").apply { mkdirs() }
        val ext = source.extension.ifBlank { "bin" }
        val dest = File(dir, "${UUID.randomUUID()}.$ext")
        source.copyTo(dest, overwrite = true)
        dest.absolutePath
    }.getOrNull()

    /**
     * Reintento del escaneo de medios (confirmado en vivo, 2026-09-25: si se busca una sola
     * vez justo al llegar la notificación, WhatsApp muchas veces todavía no terminó de guardar
     * el archivo — "sin encontrar el archivo todavía" en el log, y se pierde para siempre sin
     * esto). Reintenta a los 4s y a los 10s con una ventana más ancha cada vez; si encuentra
     * algo, actualiza la notificación YA guardada (`WalletNotificationStore.setMedia`) — el
     * Historial la refresca solo (ya sondea cada 700ms). Si sigue sin nada tras los dos
     * intentos, se deja así (no reintenta para siempre).
     */
    /**
     * v2.38: la lista de reintentos pasó de `[4s, 10s]` (14s en total) a `[4s, 10s, 30s, 60s,
     * 120s]` (~3.7 minutos en total) — confirmado en vivo con archivos reales (un `.7z` de
     * ~25 MB, otro de ~46 MB, un pdf de ~71 MB) que WhatsApp puede tardar MINUTOS en terminar
     * de descargar un documento grande, muy por encima de los 14s que cubría antes. Un audio o
     * foto normal (pocos MB) sigue encontrándose en los primeros intentos igual que siempre —
     * esto solo alarga cuánto se espera ANTES de rendirse, no afecta la velocidad de los casos
     * que ya funcionaban. `elapsedMs` (nuevo) acumula el tiempo real transcurrido, para que la
     * ventana de búsqueda (`windowAfterMs`) y el mensaje de log reflejen el tiempo total desde
     * que llegó la notificación, no solo el último paso de espera.
     */
    private fun scheduleMediaRetry(notificationId: String, postTimeMs: Long, preferredType: String?) {
        serviceScope.launch {
            var elapsedMs = 0L
            for (stepMs in listOf(4_000L, 10_000L, 30_000L, 60_000L, 120_000L)) {
                delay(stepMs)
                elapsedMs += stepMs
                val matches = runCatching {
                    WhatsAppMediaScanner.findNewMedia(applicationContext, postTimeMs, windowAfterMs = elapsedMs + 15_000L, preferredType = preferredType)
                }.getOrDefault(emptyList())
                val first = matches.firstOrNull() ?: continue
                val path = copyMediaToAppStorage(first) ?: continue
                WalletNotificationStore.setMedia(notificationId, path, first.type)
                ScoSecretariaLogger.info(applicationContext, "Medio nuevo de WhatsApp (reintento a los ${elapsedMs / 1000}s): ${first.type} \"${first.displayName}\" copiado a $path")
                return@launch
            }
            ScoSecretariaLogger.debug(applicationContext, "WhatsAppMediaScanner: no se encontró el archivo tras reintentos (${elapsedMs / 1000}s)")
        }
    }

    /** Último mensaje real de una notificación de chat (MessagingStyle o líneas expandidas). */
    private fun latestMessageText(notification: Notification): String? {
        runCatching {
            val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            val last = style?.messages?.lastOrNull()?.text?.toString()
            if (!last.isNullOrBlank()) return last
        }
        val lines = notification.extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        val lastLine = lines?.lastOrNull()?.toString()
        if (!lastLine.isNullOrBlank()) return lastLine
        return null
    }

    override fun onDestroy() { serviceScope.cancel(); SpeechEngine.shutdown(); ScoSecretariaLogger.info(this, "Servicio detenido"); super.onDestroy() }
}
