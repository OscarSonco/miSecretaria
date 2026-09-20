package com.oscarsonco.scosecretaria

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.UUID

class WalletNotificationListener : NotificationListenerService() {
    override fun onCreate() {
        super.onCreate()
        WalletNotificationStore.init(applicationContext)
        SpeechEngine.init(this)
        ScoSecretariaLogger.info(this, "Servicio de notificaciones iniciado")
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification) {
        if (statusBarNotification.packageName == packageName) return
        val notification = statusBarNotification.notification
        val title = notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = notification.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val completeText = listOf(text, bigText).filter { it.isNotBlank() }.distinct().joinToString(" ")
        val source = "${statusBarNotification.packageName} $title $completeText"
        val wallet = WalletConfig.detect(this, source) ?: return
        val dedupeKey = "${statusBarNotification.key}|$title|$completeText"
        if (!WalletNotificationStore.markIfNew(dedupeKey)) return
        val message = listOf(title, completeText).filter { it.isNotBlank() }.distinct().joinToString(": ")
        if (message.isBlank()) return
        val item = WalletNotification(UUID.randomUUID().toString(), wallet, title, message, WalletNotificationStore.now())
        WalletNotificationStore.add(item)
        ScoSecretariaLogger.info(this, "Notificación aceptada de $wallet")
        WalletNotificationNotifier.show(this, item)
        if (DisplayPreferences.alertEnabled(this) && DisplayPreferences.mode(this) == DisplayMode.NEW) WalletOverlay.show(this, item)
        if (DisplayPreferences.speechEnabled(this)) SpeechEngine.speak(this, item)
    }

    override fun onDestroy() { SpeechEngine.shutdown(); ScoSecretariaLogger.info(this, "Servicio detenido"); super.onDestroy() }
}
