package com.sco.misecretaria

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
    private const val TRASH = "trash"
    private const val EXPORT_CSV_QUEUE = "export_csv_queue"
    private const val EXPORT_MEDIA_QUEUE = "export_media_queue"
    // v2.36: salvavidas contra un crecimiento REALMENTE descontrolado (ej. Telegram caído por
    // meses) — no es un límite operativo normal. En uso normal esta cola se vacía cada ciclo
    // del worker (15-30 min), muy por debajo de esto.
    private const val EXPORT_QUEUE_SAFETY_CAP = 20_000
    const val MAX_PINNED = 2
    private lateinit var context: Context

    fun init(appContext: Context) {
        context = appContext.applicationContext
    }

    /**
     * v2.36: además del Historial (`HISTORY`, capado en 100 — solo para mostrar en pantalla),
     * cada notificación nueva entra también a dos colas de exportación SIN ese límite
     * (`EXPORT_CSV_QUEUE`/`EXPORT_MEDIA_QUEUE`). Motivo: `TelegramSyncWorker` leía directo de
     * `history()` con una marca de tiempo — si llegaban más de 100 notificaciones entre un
     * envío periódico y el siguiente (día ocupado en una sucursal), las más viejas se perdían
     * del `HISTORY` ANTES de que el worker llegara a mandarlas, y nunca se recuperaban ni en
     * el CSV ni en Telegram. Las colas nuevas son independientes de lo que se ve en pantalla:
     * un archivo puede desaparecer del Historial visible (por el límite de 100, o porque el
     * usuario lo movió a la Papelera) y de todas formas seguir pendiente de exportar — es
     * justo el comportamiento que se busca para un respaldo (sobrevivir aunque se "borre"
     * localmente). Cada cola se vacía por su cuenta cuando `TelegramSyncWorker` efectivamente
     * manda ese contenido (`removeFromCsvQueue`/`removeFromMediaQueue`), no por tiempo.
     */
    @Synchronized
    fun add(notification: WalletNotification) {
        val pending = pending().toMutableList()
        val history = history().toMutableList()
        val csvQueue = exportCsvQueue().toMutableList()
        val mediaQueue = exportMediaQueue().toMutableList()
        pending.add(notification)
        history.add(0, notification)
        csvQueue.add(notification)
        mediaQueue.add(notification)

        save(PENDING, pending)
        save(HISTORY, history.take(100))
        save(EXPORT_CSV_QUEUE, csvQueue.takeLast(EXPORT_QUEUE_SAFETY_CAP))
        save(EXPORT_MEDIA_QUEUE, mediaQueue.takeLast(EXPORT_QUEUE_SAFETY_CAP))
    }

    @Synchronized
    fun pending(): List<WalletNotification> = load(PENDING)

    @Synchronized
    fun history(): List<WalletNotification> = load(HISTORY)

    @Synchronized
    fun exportCsvQueue(): List<WalletNotification> = load(EXPORT_CSV_QUEUE)

    @Synchronized
    fun exportMediaQueue(): List<WalletNotification> = load(EXPORT_MEDIA_QUEUE)

    /** Quita de la cola de exportación del CSV los ids que ya viajaron en un envío exitoso. */
    @Synchronized
    fun removeFromCsvQueue(ids: Set<String>) {
        if (ids.isEmpty()) return
        save(EXPORT_CSV_QUEUE, exportCsvQueue().filterNot { it.id in ids })
    }

    /** Quita de la cola de exportación de medios un id ya reenviado a Telegram (o descartado
     * porque nunca tuvo archivo, o porque pesa más de lo que Telegram permite). */
    @Synchronized
    fun removeFromMediaQueue(id: String) {
        save(EXPORT_MEDIA_QUEUE, exportMediaQueue().filterNot { it.id == id })
    }

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

    /** Actualiza el medio (foto/audio/video real) de una notificación ya guardada — usado por
     * el reintento de `WhatsAppMediaScanner`/`WalletNotificationListener` cuando el archivo
     * no estaba listo todavía en el primer intento y se encuentra unos segundos después. */
    @Synchronized
    fun setMedia(id: String, path: String, type: String) {
        save(HISTORY, history().map { if (it.id == id) it.copy(mediaPath = path, mediaType = type) else it })
        save(PENDING, pending().map { if (it.id == id) it.copy(mediaPath = path, mediaType = type) else it })
        // Si el reintento encuentra el archivo DESPUÉS de que la notificación ya entró a la
        // cola de exportación de medios, hay que actualizar esa copia también — si no, cuando
        // el worker la revise seguiría viendo `mediaPath = null` y la descartaría sin enviarla.
        save(EXPORT_MEDIA_QUEUE, exportMediaQueue().map { if (it.id == id) it.copy(mediaPath = path, mediaType = type) else it })
    }

    /** Papelera: "eliminar" desde el Historial no borra de verdad — mueve a esta lista, de
     * donde se puede restaurar o vaciar (borrado permanente) más tarde. */
    @Synchronized
    fun trash(): List<WalletNotification> = load(TRASH)

    @Synchronized
    fun moveToTrash(id: String) = moveManyToTrash(setOf(id))

    @Synchronized
    fun moveManyToTrash(ids: Set<String>) {
        if (ids.isEmpty()) return
        val movidos = history().filter { it.id in ids }
        if (movidos.isEmpty()) return
        save(HISTORY, history().filterNot { it.id in ids })
        save(PENDING, pending().filterNot { it.id in ids })
        savePinned(pinnedIds() - ids)
        save(TRASH, trash() + movidos)
    }

    @Synchronized
    fun moveAllToTrash() = moveManyToTrash(history().map { it.id }.toSet())

    @Synchronized
    fun restore(id: String) = restoreMany(setOf(id))

    @Synchronized
    fun restoreMany(ids: Set<String>) {
        if (ids.isEmpty()) return
        val recuperados = trash().filter { it.id in ids }
        if (recuperados.isEmpty()) return
        save(TRASH, trash().filterNot { it.id in ids })
        save(HISTORY, (history() + recuperados).sortedByDescending { it.receivedAt })
    }

    @Synchronized
    fun restoreEverything() = restoreMany(trash().map { it.id }.toSet())

    /** Vacía la papelera de verdad (borrado permanente) — a diferencia de mover a la papelera,
     * esto SÍ borra del disco las copias de fotos/audio/video que se hayan guardado
     * (`filesDir/media/`, ver `WalletNotificationListener.copyMediaToAppStorage`), para no
     * acumular archivos huérfanos para siempre. */
    @Synchronized
    fun emptyTrash() {
        trash().forEach { item -> item.mediaPath?.let { runCatching { File(it).delete() } } }
        save(TRASH, emptyList())
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
                    .put("mediaType", it.mediaType ?: JSONObject.NULL)
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
                            note = item.optString("note", null)?.takeIf { it.isNotBlank() && it != "null" },
                            mediaType = item.optString("mediaType", null)?.takeIf { it.isNotBlank() && it != "null" }
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
