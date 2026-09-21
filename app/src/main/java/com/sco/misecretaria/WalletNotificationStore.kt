package com.sco.misecretaria

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WalletNotificationStore {
    private const val PREFS = "scosecretaria_v01"
    private const val PENDING = "pending"
    private const val HISTORY = "history"
    private const val SEEN = "seen"
    private lateinit var context: Context

    fun init(appContext: Context) {
        context = appContext.applicationContext
    }

    @Synchronized
    fun add(notification: WalletNotification) {
        val pending = pending().toMutableList()
        val history = history().toMutableList()
        pending.add(notification)
        history.add(0, notification)

        save(PENDING, pending)
        save(HISTORY, history.take(100))
    }

    @Synchronized
    fun pending(): List<WalletNotification> = load(PENDING)

    @Synchronized
    fun history(): List<WalletNotification> = load(HISTORY)

    @Synchronized
    fun acknowledge(id: String) {
        save(PENDING, pending().filterNot { it.id == id })
    }

    @Synchronized
    fun markIfNew(key: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = (prefs.getStringSet(SEEN, emptySet()) ?: emptySet()).toMutableSet()
        if (!seen.add(key)) return false
        prefs.edit().putStringSet(SEEN, seen.toList().takeLast(100).toSet()).apply()
        return true
    }

    @Synchronized
    fun clearHistory() {
        save(HISTORY, emptyList())
    }

    fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    fun timestampForFile(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

    fun exportText(): String = buildString {
        appendLine("${AppInfo.DISPLAY} - Historial de notificaciones")
        appendLine()
        history().forEach {
            appendLine("[${it.receivedAt}] ${it.wallet}")
            appendLine(it.message)
            appendLine()
        }
    }

    fun exportCsv(): String = buildString {
        appendLine("Fecha,Origen,Tipo,Mensaje")
        history().forEach {
            val fields = listOf(it.receivedAt, it.wallet, it.kind.name, it.message)
            appendLine(fields.joinToString(",") { field -> "\"${field.replace("\"", "\"\"")}\"" })
        }
    }

    private fun save(key: String, values: List<WalletNotification>) {
        val array = JSONArray()
        values.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("wallet", it.wallet)
                    .put("title", it.title)
                    .put("message", it.message)
                    .put("receivedAt", it.receivedAt)
                    .put("kind", it.kind.name)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key, array.toString())
            .apply()
    }

    private fun load(key: String): List<WalletNotification> {
        if (!::context.isInitialized) return emptyList()

        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, "[]") ?: "[]"

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        WalletNotification(
                            id = item.getString("id"),
                            wallet = item.getString("wallet"),
                            title = item.getString("title"),
                            message = item.getString("message"),
                            receivedAt = item.getString("receivedAt"),
                            kind = runCatching { NotificationKind.valueOf(item.optString("kind", NotificationKind.PAYMENT.name)) }.getOrDefault(NotificationKind.PAYMENT)
                        )
                    )
                }
            }
        }.getOrElse {
            ScoSecretariaLogger.error(context, "No se pudo leer $key", it)
            emptyList()
        }
    }
}
