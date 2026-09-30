package com.sco.misecretaria

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sincroniza "Publicidad bloqueada" desde un archivo público en Firebase Hosting — mismo
 * hosting, mismo dominio, mismo patrón que `UpdateManager` usa para `update.json`.
 *
 * Pedido explícito del usuario: mantener un solo archivo maestro en su PC
 * (`miSecretaria_PublicidadBloqueada.txt`, ver `csv_importer.py`) y que se propague solo a
 * TODOS los teléfonos de sus empleados, sin marcar cada frase a mano en cada sucursal.
 *
 * v2.45 intentó esto mandando un comando por el bot de Telegram (`/publicidadsync`) — se
 * descartó: `getUpdates` (lo que usan los teléfonos para "escuchar" comandos) solo devuelve
 * mensajes que llegan AL bot desde una cuenta de usuario real, nunca los que el bot mismo
 * envía con `sendMessage` — confirmado en vivo que el teléfono nunca veía el comando aunque
 * el script "lo mandara" con éxito. Firebase Hosting evita el problema por completo: no pasa
 * por la cola de updates de Telegram en absoluto.
 */
object AdFilterSync {
    private const val MANIFEST_URL = "https://misecretaria-67c62.web.app/publicidad.json"

    /** Reemplaza la "Publicidad bloqueada" local por lo que haya en Firebase Hosting en este
     * momento — si el admin quitó una frase vieja de su archivo maestro, también se quita
     * aquí, no solo se agregan las nuevas. Si la descarga falla (sin internet, archivo
     * todavía no publicado, etc.) no toca nada local — se reintenta en el próximo ciclo. */
    suspend fun syncFromRemote(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000; requestMethod = "GET"
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val array = JSONArray(text)
            val phrases = (0 until array.length()).mapNotNull { i ->
                val line = array.optString(i, "")
                val parts = line.split("|", limit = 2)
                if (parts.size == 2 && parts[1].isNotBlank()) BlockedPhrase(parts[0].trim(), parts[1].trim()) else null
            }
            AdFilterConfig.replaceAll(context, phrases)
            ScoSecretariaLogger.info(context, "Publicidad bloqueada sincronizada desde Firebase Hosting (${phrases.size} frases)")
        }.onFailure {
            ScoSecretariaLogger.debug(context, "No se pudo sincronizar publicidad bloqueada (sin internet o aún no publicada)")
        }
    }
}
