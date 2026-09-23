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
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

private const val STALE_NOTIFICATION_MS = 2 * 60 * 1000L

class WalletNotificationListener : NotificationListenerService() {
    companion object {
        /** Le pide al sistema que reconecte el listener si Android lo mató (ahorro de batería, etc). */
        fun requestServiceRebind(context: Context) {
            runCatching { requestRebind(ComponentName(context, WalletNotificationListener::class.java)) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        WalletNotificationStore.init(applicationContext)
        SpeechEngine.init(this)
        ScoSecretariaLogger.info(this, "Servicio de notificaciones iniciado")
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

        // Prototipo (solo logging, Paso 2 de CLAUDE.md): si WhatsApp anuncia un medio nuevo
        // (foto/audio/video), buscar en MediaStore SOLO lo que se agregó justo ahora.
        if (WhatsAppMediaScanner.isWhatsApp(packageName) && WhatsAppMediaScanner.looksLikeNewMedia(title, completeText)) {
            if (WhatsAppMediaScanner.hasMediaPermission(this)) {
                val matches = WhatsAppMediaScanner.findNewMedia(this, statusBarNotification.postTime)
                if (matches.isNotEmpty()) {
                    matches.forEach { ScoSecretariaLogger.info(this, "Medio nuevo de WhatsApp: ${it.type} \"${it.displayName}\" (${it.uri})") }
                } else {
                    ScoSecretariaLogger.debug(this, "Notificación de medio de WhatsApp sin match todavía en MediaStore (title=\"$title\")")
                }
            } else {
                ScoSecretariaLogger.debug(this, "Medio de WhatsApp detectado pero falta el permiso de acceso a medios")
            }
        }

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
        val message = listOf(title, completeText).filter { it.isNotBlank() }.distinct().joinToString(": ")
        if (message.isBlank()) return
        val item = WalletNotification(UUID.randomUUID().toString(), label, title, message, WalletNotificationStore.now(), kind, mediaPath = saveThumbnailIfAny(notification))
        WalletNotificationStore.add(item)
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

    override fun onDestroy() { SpeechEngine.shutdown(); ScoSecretariaLogger.info(this, "Servicio detenido"); super.onDestroy() }
}
