package com.sco.misecretaria

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
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

        /** Límite real de la API de Telegram Bot para `sendDocument`. Un archivo más grande
         * simplemente no se reenvía (queda igual respaldado localmente en `filesDir/media/`,
         * solo no viaja a Telegram) — no tiene sentido intentarlo, Telegram lo rechazaría de
         * todas formas. */
        private const val MAX_TELEGRAM_FILE_BYTES = 50L * 1024 * 1024

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
            // v2.45/v2.48: independientes de Telegram a propósito — corren siempre que este
            // worker corra, aunque más abajo el resto del ciclo se salte por falta de
            // token/chatId.
            AdFilterSync.syncFromRemote(applicationContext)
            WalletAppSync.syncFromRemote(applicationContext)
            val token = TelegramConfig.botToken(applicationContext)
            val chatId = TelegramConfig.chatId(applicationContext)
            if (token.isBlank() || chatId.isBlank()) return@withContext Result.success()

            sendPendingCsv(token, chatId)
            sendPendingMedia(token, chatId)
            // v2.49 (etapa 2): reporte de Billeteras/Apps actuales, solo si cambió desde el
            // último envío — ver `ConfigReportSync` para el porqué (permite que `/listado`
            // detecte configuraciones mal hechas en un teléfono nuevo).
            ConfigReportSync.sendIfChanged(applicationContext, token, chatId)
            processIncomingCommands(token, chatId)
        }
        Result.success()
    }

    /**
     * v2.36: ya NO usa una marca de tiempo contra `history()` (que solo guarda las últimas
     * 100) — consume directo de `WalletNotificationStore.exportCsvQueue()`, una cola SIN ese
     * límite (ver esa función). Se toma una foto de la cola (`queued`), se manda todo junto en
     * un solo CSV, y solo si el envío fue exitoso se quitan ESOS ids de la cola — así lo que
     * haya llegado MIENTRAS se mandaba no se pierde (queda para el próximo ciclo).
     */
    private fun sendPendingCsv(token: String, chatId: String) {
        val deviceLabel = DisplayPreferences.deviceLabel(applicationContext)
        val queued = WalletNotificationStore.exportCsvQueue()
        if (queued.isEmpty()) return
        val csv = WalletNotificationStore.exportCsvFor(queued, deviceLabel)
        val fileName = "${deviceLabel}_${WalletNotificationStore.timestampForFile()}.csv"
        val sent = TelegramClient.sendDocument(token, chatId, fileName, csv.toByteArray(Charsets.UTF_8), "📋 $deviceLabel — ${queued.size} nuevas", mimeType = "text/csv")
        if (sent) WalletNotificationStore.removeFromCsvQueue(queued.map { it.id }.toSet())
    }

    /**
     * v2.36: mismo cambio que `sendPendingCsv` — consume de `exportMediaQueue()` (sin límite
     * de 100) en vez de `history()` con marca de tiempo. Pedido explícito del usuario: "así
     * como cada X tiempo se envían los CSV, que también se envíen los videos/audios/
     * documentos/archivos". Se procesan en orden cronológico; cada ítem se quita de la cola
     * en cuanto se resuelve (mandado, descartado por tamaño, o sin archivo real) — si el envío
     * de uno falla (red cortada, etc.) se detiene el ciclo ahí, dejando ESE y los siguientes
     * en la cola para el próximo intento, sin perder nada.
     */
    private fun sendPendingMedia(token: String, chatId: String) {
        val deviceLabel = DisplayPreferences.deviceLabel(applicationContext)
        val queued = WalletNotificationStore.exportMediaQueue().sortedBy { it.receivedAt }
        for (item in queued) {
            val mediaPath = item.mediaPath
            if (mediaPath == null) {
                // A esta altura (mínimo 15 min después de creada) ya pasó cualquier reintento
                // de `WhatsAppMediaScanner` — si sigue sin archivo, nunca lo va a tener.
                WalletNotificationStore.removeFromMediaQueue(item.id)
                continue
            }
            val file = File(mediaPath)
            if (!file.exists()) {
                WalletNotificationStore.removeFromMediaQueue(item.id)
                continue
            }
            if (file.length() > MAX_TELEGRAM_FILE_BYTES) {
                ScoSecretariaLogger.debug(applicationContext, "Medio demasiado grande para reenviar por Telegram (${file.length()} bytes): ${file.name}")
                WalletNotificationStore.removeFromMediaQueue(item.id)
                continue
            }
            val emoji = when (item.mediaType) { "video" -> "🎥"; "audio" -> "🎤"; "document" -> "📄"; else -> "📷" }
            val caption = "$emoji $deviceLabel — ${item.wallet}: ${item.message.take(200)}"
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
            val sent = TelegramClient.sendDocument(token, chatId, file.name, file.readBytes(), caption, mimeType)
            if (!sent) break
            WalletNotificationStore.removeFromMediaQueue(item.id)
        }
    }

    /**
     * Offset negativo a propósito ("las últimas N de la cola"): NO confirma nada ante
     * Telegram, así el mismo lote de comandos sigue disponible para todos los demás teléfonos
     * que comparten este bot (ver `TelegramConfig.isUpdateProcessed`). El filtro de repetidos
     * es local, por dispositivo, y es el mismo que usa el polling en tiempo real, así que no
     * hay doble procesamiento.
     *
     * v2.44: antes usaba `offset=0` — confirmado en vivo (2026-09-30) que con 2+ updates
     * pendientes en la cola real, `offset=0` devolvía SIEMPRE el más viejo, nunca llegaba al
     * más nuevo (un `/help` se quedó sin responder mientras un `/panelon` anterior, ya
     * procesado, seguía "tapando" la cola). Mismo bug ya visto y corregido en
     * `csv_importer.py` (Python/PC) — ver `TELEGRAM_UPDATES_OFFSET` en `TelegramClient.kt`
     * para el diagnóstico completo.
     */
    private suspend fun processIncomingCommands(token: String, chatId: String) {
        // Mismo resguardo que en WalletNotificationListener: si `getUpdates` se queda colgado
        // (confirmado en vivo que puede pasar), que este ciclo del respaldo se rinda a los 20s
        // en vez de bloquear el worker indefinidamente.
        val updates = runCatching { withTimeout(20_000L) { TelegramClient.getUpdates(token, offset = TELEGRAM_UPDATES_OFFSET) } }.getOrDefault(emptyList())
        val deviceLabel = DisplayPreferences.deviceLabel(applicationContext)
        for (update in updates) {
            if (!TelegramConfig.markUpdateIfNew(applicationContext, update.updateId)) continue
            if (update.chatId != chatId) continue
            TelegramCommandHandler.handle(applicationContext, update.text.trim(), deviceLabel)
        }
    }
}
