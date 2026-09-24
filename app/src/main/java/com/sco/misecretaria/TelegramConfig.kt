package com.sco.misecretaria

import android.content.Context

/** Configuración del bot de Telegram: token, chat destino, intervalo de envío y offsets. */
object TelegramConfig {
    private const val PREFS = "telegram_config_v1"
    private const val TOKEN = "bot_token"
    private const val CHAT_ID = "chat_id"
    private const val INTERVAL_MIN = "interval_min"
    private const val LAST_CSV_SENT_AT = "last_csv_sent_at"
    private const val PROCESSED_UPDATE_IDS = "processed_update_ids"

    // BuildConfig.DEFAULT_BOT_TOKEN/DEFAULT_CHAT_ID solo vienen rellenos en el build "Interna"
    // (`build_interna.sh`, nunca en el build público) — sirven de valor de fábrica hasta que
    // el usuario guarde uno propio en SharedPreferences.
    fun botToken(context: Context) = get(context, TOKEN, "").ifBlank { BuildConfig.DEFAULT_BOT_TOKEN }
    fun setBotToken(context: Context, value: String) = put(context, TOKEN, value.trim())

    fun chatId(context: Context) = get(context, CHAT_ID, "").ifBlank { BuildConfig.DEFAULT_CHAT_ID }
    fun setChatId(context: Context, value: String) = put(context, CHAT_ID, value.trim())

    /** Minutos entre envíos. Android no permite trabajo periódico en segundo plano por
     * debajo de 15 minutos (WorkManager lo redondea solo hacia arriba). */
    fun intervalMinutes(context: Context) = (get(context, INTERVAL_MIN, "30").toLongOrNull() ?: 30L).coerceAtLeast(15L)
    fun setIntervalMinutes(context: Context, value: Long) = put(context, INTERVAL_MIN, value.coerceAtLeast(15L).toString())

    fun lastCsvSentAt(context: Context) = get(context, LAST_CSV_SENT_AT, "")
    fun setLastCsvSentAt(context: Context, value: String) = put(context, LAST_CSV_SENT_AT, value)

    /**
     * Un solo bot compartido por varias sucursales: nunca se le confirma el offset a Telegram
     * (el worker siempre pide `offset=0`), así el servidor sigue entregando el mismo lote de
     * comandos a TODOS los teléfonos que consulten, no solo al primero. Cada dispositivo
     * recuerda LOCALMENTE qué `update_id` ya procesó para no repetir el mismo comando en cada
     * ciclo (Telegram retiene los últimos ~100/24h sin confirmar).
     *
     * `@Synchronized`: desde v2.16 hay DOS consumidores del mismo lote de `updates` — el
     * long-polling en tiempo real (`WalletNotificationListener`) y el respaldo periódico
     * (`TelegramSyncWorker`) — corriendo en hilos distintos. Si "ya lo vi" y "márcalo como
     * visto" fueran dos pasos separados, ambos podían pasar el primer paso casi al mismo
     * tiempo y procesar el MISMO comando dos veces (esto es lo que causó que un `/notificar`
     * se leyera dos veces). Ahora es un solo paso atómico: devuelve `true` únicamente para
     * quien lo marca primero.
     */
    @Synchronized
    fun markUpdateIfNew(context: Context, updateId: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = (prefs.getStringSet(PROCESSED_UPDATE_IDS, emptySet()) ?: emptySet()).toMutableSet()
        if (!current.add(updateId.toString())) return false
        prefs.edit().putStringSet(PROCESSED_UPDATE_IDS, current.toList().takeLast(300).toSet()).apply()
        return true
    }

    fun isConfigured(context: Context) = botToken(context).isNotBlank() && chatId(context).isNotBlank()

    private fun get(context: Context, key: String, fallback: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, fallback) ?: fallback
    private fun put(context: Context, key: String, value: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, value).apply()
}
