package com.sco.misecretaria

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.UUID

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
        val source = "${statusBarNotification.packageName} $title $completeText"

        val walletMatch = WalletConfig.detect(this, source)
        val appMatch = if (walletMatch == null) AppConfig.detect(this, source) else null
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
        val item = WalletNotification(UUID.randomUUID().toString(), label, title, message, WalletNotificationStore.now(), kind)
        WalletNotificationStore.add(item)
        val isPromo = kind == NotificationKind.PAYMENT && !PaymentMessageDetector.looksLikePayment(message)
        ScoSecretariaLogger.info(this, "Notificación aceptada de $label (${kind.name}${if (isPromo) ", publicidad: silenciada" else ""})")
        if (!isPromo) {
            WalletNotificationNotifier.show(this, item)
            if (kind == NotificationKind.PAYMENT && DisplayPreferences.alertEnabled(this) && !DisplayPreferences.fullScreenEnabled(this)) WalletOverlay.show(this, item)
            if (DisplayPreferences.speechEnabled(this)) SpeechEngine.speak(this, item)
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

    override fun onDestroy() { SpeechEngine.shutdown(); ScoSecretariaLogger.info(this, "Servicio detenido"); super.onDestroy() }
}
