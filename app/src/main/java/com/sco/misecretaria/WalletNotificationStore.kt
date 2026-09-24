package com.sco.misecretaria

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PinToggleResult { PINNED, UNPINNED, LIMIT_REACHED }

object WalletNotificationStore {
    private const val PREFS = "scosecretaria_v01"
    private const val PENDING = "pending"
    private const val HISTORY = "history"
    private const val SEEN = "seen"
    private const val PINNED = "pinned"
    const val MAX_PINNED = 2
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

    /** Nota personal del usuario sobre una notificación (no viene de la app/billetera de
     * origen). `note = null` o vacía la quita. */
    @Synchronized
    fun setNote(id: String, note: String?) {
        val trimmed = note?.trim()?.takeIf { it.isNotBlank() }
        save(HISTORY, history().map { if (it.id == id) it.copy(note = trimmed) else it })
        save(PENDING, pending().map { if (it.id == id) it.copy(note = trimmed) else it })
    }

    @Synchronized
    fun remove(id: String) {
        save(HISTORY, history().filterNot { it.id == id })
        save(PENDING, pending().filterNot { it.id == id })
        savePinned(pinnedIds() - id)
    }

    @Synchronized
    fun removeAll(ids: Set<String>) {
        if (ids.isEmpty()) return
        save(HISTORY, history().filterNot { it.id in ids })
        save(PENDING, pending().filterNot { it.id in ids })
        savePinned(pinnedIds() - ids)
    }

    @Synchronized
    fun clearHistory() {
        save(HISTORY, emptyList())
        savePinned(emptySet())
    }

    /** Notificaciones fijadas por el usuario (máximo [MAX_PINNED]) — siempre se muestran
     * primero en el Historial, sin importar el filtro de billetera/app activo. */
    @Synchronized
    fun pinnedIds(): Set<String> {
        if (!::context.isInitialized) return emptySet()
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(PINNED, emptySet()) ?: emptySet()
    }

    @Synchronized
    fun togglePin(id: String): PinToggleResult {
        val current = pinnedIds().toMutableSet()
        if (current.remove(id)) {
            savePinned(current)
            return PinToggleResult.UNPINNED
        }
        if (current.size >= MAX_PINNED) return PinToggleResult.LIMIT_REACHED
        current.add(id)
        savePinned(current)
        return PinToggleResult.PINNED
    }

    private fun savePinned(ids: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(PINNED, ids).apply()
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

    /** CSV para el envío periódico por Telegram: incluye la sucursal/dispositivo de origen. */
    fun exportCsvFor(items: List<WalletNotification>, deviceLabel: String): String = buildString {
        appendLine("Sucursal,Fecha,Origen,Tipo,Mensaje")
        items.forEach {
            val fields = listOf(deviceLabel, it.receivedAt, it.wallet, it.kind.name, it.message)
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
                    .put("mediaPath", it.mediaPath ?: JSONObject.NULL)
                    .put("note", it.note ?: JSONObject.NULL)
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
                            kind = runCatching { NotificationKind.valueOf(item.optString("kind", NotificationKind.PAYMENT.name)) }.getOrDefault(NotificationKind.PAYMENT),
                            mediaPath = item.optString("mediaPath", null)?.takeIf { it.isNotBlank() && it != "null" },
                            note = item.optString("note", null)?.takeIf { it.isNotBlank() && it != "null" }
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
