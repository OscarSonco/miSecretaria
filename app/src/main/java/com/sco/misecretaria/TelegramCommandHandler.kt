package com.sco.misecretaria

import android.content.Context
import java.util.UUID

/**
 * Lógica de comandos del bot (/help, /notificar, /renombrar), compartida entre el polling en
 * tiempo real de `WalletNotificationListener` y el respaldo periódico de `TelegramSyncWorker`
 * (mismo dedupe vía `TelegramConfig.isUpdateProcessed`, así que no hay riesgo de duplicar).
 */
object TelegramCommandHandler {

    fun handle(context: Context, text: String, deviceLabel: String) {
        handleHelpCommand(context, text, deviceLabel)
        handleNotifyCommand(context, text, deviceLabel)
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
            "/notificar TODOS <mensaje>\nAvisa (voz + notificación) a TODAS las sucursales.\n" +
            "Ejemplo: /notificar TODOS Cerramos a las 8pm hoy\n\n" +
            "/notificar <sucursal> <mensaje>\nAvisa solo a esa sucursal (nombre o código, ej. MS-7K2F9Q).\n" +
            "Ejemplo: /notificar $deviceLabel Reunión a las 3pm\n\n" +
            "/renombrar <código_actual> <nombre_nuevo>\nCambia el nombre de una sucursal (código/nombre debe coincidir exacto).\n" +
            "Ejemplo: /renombrar $deviceLabel Sucursal Centro\n\n" +
            "/help\nMuestra esta ayuda.\n\n" +
            "Esta sucursal se llama: $deviceLabel"
        replyUsage(context, help)
    }

    /**
     * /notificar TODOS|<sucursal> <mensaje> — avisa a un dispositivo específico o a todos.
     * Debe mandarse TODO en un solo mensaje de Telegram (no como dos mensajes seguidos) — si
     * el usuario manda "/notificar TODOS" solo y el texto en otro mensaje aparte, no calza con
     * el patrón; en vez de quedarse callado, el bot responde con la sintaxis correcta.
     */
    private fun handleNotifyCommand(context: Context, text: String, deviceLabel: String) {
        if (!text.trim().startsWith("/notificar", ignoreCase = true)) return
        val notifyMatch = Regex("(?is)^/notificar\\s+(\\S+)\\s+(.+)$").find(text)
        if (notifyMatch == null) {
            replyUsage(context, "⚠️ Formato incorrecto. Todo en UN SOLO mensaje:\n/notificar TODOS <mensaje>\n/notificar <sucursal> <mensaje>\n\nEjemplo: /notificar TODOS Hola a todos")
            return
        }
        val target = notifyMatch.groupValues[1]
        val message = notifyMatch.groupValues[2].trim()
        if (!target.equals("TODOS", true) && !target.equals(deviceLabel, true)) return
        val item = WalletNotification(UUID.randomUUID().toString(), "Aviso remoto", "Aviso remoto", message, WalletNotificationStore.now(), NotificationKind.GENERAL)
        WalletNotificationStore.add(item)
        ScoSecretariaLogger.info(context, "Aviso remoto recibido vía Telegram")
        WalletNotificationNotifier.show(context, item)
        // A propósito NO se usa SpeechEngine.speak(item) — eso antepondría "Aviso remoto, " al
        // leerlo (mismo prefijo de app que usan las notificaciones generales). Aquí se lee el
        // mensaje tal cual, como en la pantalla "Leer".
        if (DisplayPreferences.speechEnabled(context)) SpeechEngine.speakText(context, message)
    }

    /** /renombrar <codigo_actual> <nombre_nuevo> — solo el dispositivo cuyo nombre/código
     * actual coincide (case-insensitive) se renombra; el resto ignora el comando. Sirve tanto
     * para renombrar el código alfanumérico autogenerado como para volver a renombrar después.
     * Igual que /notificar: debe mandarse todo en un solo mensaje. */
    private fun handleRenameCommand(context: Context, text: String, deviceLabel: String) {
        if (!text.trim().startsWith("/renombrar", ignoreCase = true)) return
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
