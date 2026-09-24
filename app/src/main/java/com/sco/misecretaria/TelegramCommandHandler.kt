package com.sco.misecretaria

import android.content.Context
import java.util.UUID

/**
 * Lógica de comandos del bot (/help, /notificar, /notificarpantalla, /renombrar), compartida
 * entre el polling en tiempo real de `WalletNotificationListener` y el respaldo periódico de
 * `TelegramSyncWorker` (mismo dedupe atómico vía `TelegramConfig.markUpdateIfNew`, así que no
 * hay riesgo de duplicar aunque los dos corran casi al mismo tiempo).
 */
object TelegramCommandHandler {

    fun handle(context: Context, text: String, deviceLabel: String) {
        handleHelpCommand(context, text, deviceLabel)
        handleNotifyCommand(context, text, deviceLabel)
        handleNotifyPantallaCommand(context, text, deviceLabel)
        handleRenameCommand(context, text, deviceLabel)
    }

    /**
     * /help o /start — lista los comandos disponibles. Responde CADA dispositivo que comparte
     * el bot (no hay forma de elegir "un solo respondedor" sin un servidor propio) — con pocas
     * sucursales es aceptable; si llega a haber muchas, revisar si conviene limitarlo.
     */
    private fun handleHelpCommand(context: Context, text: String, deviceLabel: String) {
        val normalized = text.trim().lowercase()
        if (normalized != "/help" && normalized != "/start") return
        val help = "🤖 miSecretaria — comandos del bot:\n" +
            "⚠️ Escribe todo en UN SOLO mensaje, no en varios seguidos.\n\n" +
            "/notificar TODOS <mensaje>\nSolo AUDIO — lee el mensaje en voz alta, sin nada en pantalla.\n" +
            "Ejemplo: /notificar TODOS Cerramos a las 8pm hoy\n\n" +
            "/notificarpantalla TODOS <mensaje>\nAUDIO + PANTALLA — además lo muestra en pantalla completa o aviso flotante.\n" +
            "Ejemplo: /notificarpantalla TODOS Vino el proveedor, revisen\n\n" +
            "Con ambos puedes usar el nombre/código de una sucursal en vez de TODOS, para avisarle solo a esa.\n" +
            "Ejemplo: /notificar $deviceLabel Reunión a las 3pm\n\n" +
            "/renombrar <código_actual> <nombre_nuevo>\nCambia el nombre de una sucursal (código/nombre debe coincidir exacto).\n" +
            "Ejemplo: /renombrar $deviceLabel Sucursal Centro\n\n" +
            "/help\nMuestra esta ayuda.\n\n" +
            "Esta sucursal se llama: $deviceLabel"
        replyUsage(context, help)
    }

    /**
     * /notificar TODOS|<sucursal> <mensaje> — SOLO AUDIO: lee el mensaje en voz alta y lo
     * guarda en el historial, sin notificación visible ni aviso en pantalla. El prefijo se
     * revisa con límite de palabra (`\b`) para no confundirse con `/notificarpantalla`.
     */
    private fun handleNotifyCommand(context: Context, text: String, deviceLabel: String) {
        if (!Regex("(?is)^/notificar\\b").containsMatchIn(text.trim())) return
        val match = Regex("(?is)^/notificar\\s+(\\S+)\\s+(.+)$").find(text)
        if (match == null) {
            replyUsage(context, "⚠️ Formato incorrecto. Todo en UN SOLO mensaje:\n/notificar TODOS <mensaje>\n/notificar <sucursal> <mensaje>\n\nEjemplo: /notificar TODOS Hola a todos")
            return
        }
        val (target, message) = resolveTargetAndMessage(match) ?: return
        if (!targetMatches(target, deviceLabel)) return
        saveAndSpeak(context, message)
        ScoSecretariaLogger.info(context, "Aviso remoto (solo audio) recibido vía Telegram")
        // A propósito NO se llama a WalletNotificationNotifier/WalletOverlay aquí: este
        // comando es solo audio, sin nada en pantalla — para eso está /notificarpantalla.
    }

    /**
     * /notificarpantalla TODOS|<sucursal> <mensaje> — AUDIO + PANTALLA: además de leerlo,
     * lo muestra en pantalla completa (si "Pantalla completa" está activado) o como aviso
     * flotante (si "Pantalla de aviso" está activado), igual que un pago recibido pero sin el
     * encabezado "Pago recibido" ni un monto inventado.
     */
    private fun handleNotifyPantallaCommand(context: Context, text: String, deviceLabel: String) {
        if (!Regex("(?is)^/notificarpantalla\\b").containsMatchIn(text.trim())) return
        val match = Regex("(?is)^/notificarpantalla\\s+(\\S+)\\s+(.+)$").find(text)
        if (match == null) {
            replyUsage(context, "⚠️ Formato incorrecto. Todo en UN SOLO mensaje:\n/notificarpantalla TODOS <mensaje>\n/notificarpantalla <sucursal> <mensaje>\n\nEjemplo: /notificarpantalla TODOS Hola a todos")
            return
        }
        val (target, message) = resolveTargetAndMessage(match) ?: return
        if (!targetMatches(target, deviceLabel)) return
        val item = saveAndSpeak(context, message, kind = NotificationKind.ALERT)
        ScoSecretariaLogger.info(context, "Aviso remoto (audio + pantalla) recibido vía Telegram")
        WalletNotificationNotifier.show(context, item)
        if (!DisplayPreferences.fullScreenEnabled(context) && DisplayPreferences.alertEnabled(context)) {
            WalletOverlay.show(context, item)
        }
    }

    private fun resolveTargetAndMessage(match: MatchResult): Pair<String, String>? {
        val target = match.groupValues[1]
        val message = match.groupValues[2].trim()
        if (message.isBlank()) return null
        return target to message
    }

    private fun targetMatches(target: String, deviceLabel: String) =
        target.equals("TODOS", true) || target.equals(deviceLabel, true)

    private fun saveAndSpeak(context: Context, message: String, kind: NotificationKind = NotificationKind.GENERAL): WalletNotification {
        val item = WalletNotification(UUID.randomUUID().toString(), "Aviso remoto", "Aviso remoto", message, WalletNotificationStore.now(), kind)
        WalletNotificationStore.add(item)
        // A propósito NO se usa SpeechEngine.speak(item) — eso antepondría "Aviso remoto, " al
        // leerlo (mismo prefijo de app que usan las notificaciones generales). Aquí se lee el
        // mensaje tal cual, como en la pantalla "Leer".
        if (DisplayPreferences.speechEnabled(context)) SpeechEngine.speakText(context, message)
        return item
    }

    /** /renombrar <codigo_actual> <nombre_nuevo> — solo el dispositivo cuyo nombre/código
     * actual coincide (case-insensitive) se renombra; el resto ignora el comando. Sirve tanto
     * para renombrar el código alfanumérico autogenerado como para volver a renombrar después.
     * Igual que /notificar: debe mandarse todo en un solo mensaje. */
    private fun handleRenameCommand(context: Context, text: String, deviceLabel: String) {
        if (!Regex("(?is)^/renombrar\\b").containsMatchIn(text.trim())) return
        val renameMatch = Regex("(?is)^/renombrar\\s+(\\S+)\\s+(.+)$").find(text)
        if (renameMatch == null) {
            replyUsage(context, "⚠️ Formato incorrecto. Todo en UN SOLO mensaje:\n/renombrar <código_actual> <nombre_nuevo>\n\nEjemplo: /renombrar $deviceLabel Sucursal Centro")
            return
        }
        val currentCode = renameMatch.groupValues[1]
        val newName = renameMatch.groupValues[2].trim()
        if (!currentCode.equals(deviceLabel, true) || newName.isBlank()) return
        DisplayPreferences.setDeviceLabel(context, newName)
        ScoSecretariaLogger.info(context, "Dispositivo renombrado de '$deviceLabel' a '$newName' vía Telegram")
        replyUsage(context, "✅ '$deviceLabel' ahora se llama '$newName'")
    }

    private fun replyUsage(context: Context, text: String) {
        TelegramClient.sendMessage(TelegramConfig.botToken(context), TelegramConfig.chatId(context), text)
    }
}
