package com.sco.misecretaria

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class TelegramUpdate(val updateId: Long, val chatId: String, val text: String)

/**
 * v2.44: offset a pasarle a `getUpdates` para pedir "las últimas N de la cola" en vez de
 * `offset=0` ("todo lo no confirmado desde el principio") — confirmado en vivo (2026-09-30)
 * que `offset=0` puede quedarse devolviendo SIEMPRE el update más viejo de la cola cuando hay
 * 2+ pendientes, sin llegar nunca al más nuevo (un `/help` real nunca se vio mientras un
 * `/panelon` anterior, ya procesado, seguía "tapando" la cola) — mismo bug ya visto y
 * corregido antes en `csv_importer.py` (Python/PC, Tanda de 2026-09-25). 100 es generoso
 * (normalmente hay 0-2 pendientes) sin costo real. Usado por `WalletNotificationListener`
 * (long-poll en tiempo real) y `TelegramSyncWorker` (respaldo periódico) por igual.
 */
const val TELEGRAM_UPDATES_OFFSET = -100L

/** Cliente mínimo de la API de Telegram Bot (sendMessage/sendDocument/getUpdates), sin librerías. */
object TelegramClient {
    private fun base(token: String) = "https://api.telegram.org/bot$token"

    fun sendMessage(token: String, chatId: String, text: String): Boolean = runCatching {
        val url = URL("${base(token)}/sendMessage")
        val body = "chat_id=${URLEncoder.encode(chatId, "UTF-8")}&text=${URLEncoder.encode(text, "UTF-8")}"
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10000; readTimeout = 10000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val ok = conn.responseCode in 200..299
        conn.disconnect()
        ok
    }.getOrDefault(false)

    /**
     * `mimeType` genérico (`application/octet-stream`) por defecto — sirve para cualquier
     * archivo real (foto/video/audio/documento, ver `TelegramSyncWorker.sendPendingMedia`).
     * El envío periódico del CSV pasa `"text/csv"` explícito. Telegram no exige que el MIME
     * sea exacto para aceptar el archivo, pero ayuda a que el cliente de Telegram lo muestre
     * mejor (ej. reproductor de video/audio en vez de un ícono de archivo genérico).
     * Timeouts más generosos que `sendMessage` porque un video puede pesar varios MB.
     */
    fun sendDocument(token: String, chatId: String, fileName: String, bytes: ByteArray, caption: String, mimeType: String = "application/octet-stream"): Boolean = runCatching {
        val boundary = "----miSecretaria${System.currentTimeMillis()}"
        val url = URL("${base(token)}/sendDocument")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000; readTimeout = 60000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        conn.outputStream.use { out ->
            fun writeField(name: String, value: String) {
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                out.write("$value\r\n".toByteArray())
            }
            writeField("chat_id", chatId)
            writeField("caption", caption)
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\"document\"; filename=\"$fileName\"\r\n".toByteArray())
            out.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
            out.write(bytes)
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val ok = conn.responseCode in 200..299
        conn.disconnect()
        ok
    }.getOrDefault(false)

    /**
     * `timeoutSeconds` > 0 activa "long polling" de Telegram: la conexión queda abierta hasta
     * que llega un mensaje nuevo o se agota el tiempo, lo que permita respuestas casi
     * instantáneas sin tener que pedir cada pocos segundos. El timeout HTTP del cliente se deja
     * con margen extra para no cortar la conexión antes de que Telegram responda.
     */
    fun getUpdates(token: String, offset: Long, timeoutSeconds: Long = 0): List<TelegramUpdate> = runCatching {
        val url = URL("${base(token)}/getUpdates?offset=$offset&timeout=$timeoutSeconds")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = ((timeoutSeconds + 10) * 1000).toInt()
        }
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val root = JSONObject(text)
        val result = root.optJSONArray("result") ?: return@runCatching emptyList()
        buildList {
            for (i in 0 until result.length()) {
                val update = result.getJSONObject(i)
                val message = update.optJSONObject("message") ?: continue
                val chat = message.optJSONObject("chat") ?: continue
                add(
                    TelegramUpdate(
                        updateId = update.optLong("update_id"),
                        chatId = chat.optLong("id").toString(),
                        text = message.optString("text", "")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}
