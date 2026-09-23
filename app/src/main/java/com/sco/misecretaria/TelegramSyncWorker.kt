package com.sco.misecretaria

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

class TelegramSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        private const val WORK_NAME = "telegram_sync"

        fun schedule(context: Context) {
            val minutes = TelegramConfig.intervalMinutes(context)
            val request = PeriodicWorkRequestBuilder<TelegramSyncWorker>(minutes, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun runOnce(context: Context) {
            androidx.work.OneTimeWorkRequestBuilder<TelegramSyncWorker>().build()
                .also { WorkManager.getInstance(context).enqueue(it) }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            WalletNotificationStore.init(applicationContext)
            val token = TelegramConfig.botToken(applicationContext)
            val chatId = TelegramConfig.chatId(applicationContext)
            if (token.isBlank() || chatId.isBlank()) return@withContext Result.success()

            sendPendingCsv(token, chatId)
            processIncomingCommands(token, chatId)
        }
        Result.success()
    }

    private fun sendPendingCsv(token: String, chatId: String) {
        val deviceLabel = DisplayPreferences.deviceLabel(applicationContext)
        val lastSent = TelegramConfig.lastCsvSentAt(applicationContext)
        val newItems = WalletNotificationStore.history().filter { it.receivedAt > lastSent }
        if (newItems.isEmpty()) return
        val csv = WalletNotificationStore.exportCsvFor(newItems, deviceLabel)
        val fileName = "${deviceLabel}_${WalletNotificationStore.timestampForFile()}.csv"
        val sent = TelegramClient.sendDocument(token, chatId, fileName, csv.toByteArray(Charsets.UTF_8), "📋 $deviceLabel — ${newItems.size} nuevas")
        if (sent) TelegramConfig.setLastCsvSentAt(applicationContext, newItems.maxOf { it.receivedAt })
    }

    /**
     * `offset=0` a propósito: NO confirma nada ante Telegram, así el mismo lote de comandos
     * sigue disponible para todos los demás teléfonos que comparten este bot (ver
     * `TelegramConfig.isUpdateProcessed`). El filtro de repetidos es local, por dispositivo.
     */
    private fun processIncomingCommands(token: String, chatId: String) {
        val updates = TelegramClient.getUpdates(token, offset = 0)
        val deviceLabel = DisplayPreferences.deviceLabel(applicationContext)
        for (update in updates) {
            if (TelegramConfig.isUpdateProcessed(applicationContext, update.updateId)) continue
            TelegramConfig.markUpdateProcessed(applicationContext, update.updateId)
            if (update.chatId != chatId) continue
            handleCommand(update.text.trim(), deviceLabel)
        }
    }

    private fun handleCommand(text: String, deviceLabel: String) {
        handleHelpCommand(text, deviceLabel)
        handleNotifyCommand(text, deviceLabel)
        handleRenameCommand(text, deviceLabel)
    }

    /**
     * /help o /start — lista los comandos disponibles. Responde CADA dispositivo que comparte
     * el bot (no hay forma de elegir "un solo respondedor" sin un servidor propio) — con pocas
     * sucursales es aceptable; si llega a haber muchas, revisar si conviene limitarlo.
     */
    private fun handleHelpCommand(text: String, deviceLabel: String) {
        val normalized = text.trim().lowercase()
        if (normalized != "/help" && normalized != "/start") return
        val help = "🤖 miSecretaria — comandos del bot:\n\n" +
            "/notificar TODOS <mensaje>\nAvisa (voz + notificación) a TODAS las sucursales conectadas.\n\n" +
            "/notificar <sucursal> <mensaje>\nAvisa solo a esa sucursal (usa su nombre o código, ej. MS-7K2F9Q).\n\n" +
            "/renombrar <código_actual> <nombre_nuevo>\nCambia el nombre de una sucursal (el código/nombre debe coincidir exacto).\n\n" +
            "/help\nMuestra esta ayuda.\n\n" +
            "Esta sucursal se llama: $deviceLabel"
        TelegramClient.sendMessage(TelegramConfig.botToken(applicationContext), TelegramConfig.chatId(applicationContext), help)
    }

    /** /notificar TODOS|<sucursal> <mensaje> — avisa a un dispositivo específico o a todos. */
    private fun handleNotifyCommand(text: String, deviceLabel: String) {
        val notifyMatch = Regex("(?is)^/notificar\\s+(\\S+)\\s+(.+)$").find(text) ?: return
        val target = notifyMatch.groupValues[1]
        val message = notifyMatch.groupValues[2].trim()
        if (!target.equals("TODOS", true) && !target.equals(deviceLabel, true)) return
        val item = WalletNotification(UUID.randomUUID().toString(), "Aviso remoto", "Aviso remoto", message, WalletNotificationStore.now(), NotificationKind.GENERAL)
        WalletNotificationStore.add(item)
        ScoSecretariaLogger.info(applicationContext, "Aviso remoto recibido vía Telegram")
        WalletNotificationNotifier.show(applicationContext, item)
        if (DisplayPreferences.speechEnabled(applicationContext)) SpeechEngine.speak(applicationContext, item)
    }

    /** /renombrar <codigo_actual> <nombre_nuevo> — solo el dispositivo cuyo nombre/código
     * actual coincide (case-insensitive) se renombra; el resto ignora el comando. Sirve tanto
     * para renombrar el código alfanumérico autogenerado como para volver a renombrar después. */
    private fun handleRenameCommand(text: String, deviceLabel: String) {
        val renameMatch = Regex("(?is)^/renombrar\\s+(\\S+)\\s+(.+)$").find(text) ?: return
        val currentCode = renameMatch.groupValues[1]
        val newName = renameMatch.groupValues[2].trim()
        if (!currentCode.equals(deviceLabel, true) || newName.isBlank()) return
        DisplayPreferences.setDeviceLabel(applicationContext, newName)
        ScoSecretariaLogger.info(applicationContext, "Dispositivo renombrado de '$deviceLabel' a '$newName' vía Telegram")
        TelegramClient.sendMessage(TelegramConfig.botToken(applicationContext), TelegramConfig.chatId(applicationContext), "✅ '$deviceLabel' ahora se llama '$newName'")
    }
}
