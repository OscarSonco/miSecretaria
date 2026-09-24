package com.sco.misecretaria

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

/**
 * Respaldo periódico: envía el CSV del historial y revisa comandos por si el polling en tiempo
 * real de `WalletNotificationListener` se cortó (por ejemplo, si Android mató el servicio).
 * Los comandos en tiempo real ya no dependen de este worker — ver `TelegramCommandHandler` y
 * el loop de long-polling en `WalletNotificationListener`.
 */
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
     * `TelegramConfig.isUpdateProcessed`). El filtro de repetidos es local, por dispositivo, y
     * es el mismo que usa el polling en tiempo real, así que no hay doble procesamiento.
     */
    private suspend fun processIncomingCommands(token: String, chatId: String) {
        // Mismo resguardo que en WalletNotificationListener: si `getUpdates` se queda colgado
        // (confirmado en vivo que puede pasar), que este ciclo del respaldo se rinda a los 20s
        // en vez de bloquear el worker indefinidamente.
        val updates = runCatching { withTimeout(20_000L) { TelegramClient.getUpdates(token, offset = 0) } }.getOrDefault(emptyList())
        val deviceLabel = DisplayPreferences.deviceLabel(applicationContext)
        for (update in updates) {
            if (!TelegramConfig.markUpdateIfNew(applicationContext, update.updateId)) continue
            if (update.chatId != chatId) continue
            TelegramCommandHandler.handle(applicationContext, update.text.trim(), deviceLabel)
        }
    }
}
