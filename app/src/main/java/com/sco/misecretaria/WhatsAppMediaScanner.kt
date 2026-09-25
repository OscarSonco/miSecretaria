package com.sco.misecretaria

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

/**
 * Fase 1 (prototipo, solo logging) del Paso 2 de CLAUDE.md — opción b: cuando llega una
 * notificación de WhatsApp que anuncia un medio nuevo (foto/audio/video), busca el archivo
 * real en las carpetas de medios de WhatsApp.
 *
 * **v2.23 (2026-09-24): lectura directa del archivo (`java.io.File`), no `MediaStore`.**
 * La versión anterior consultaba `MediaStore.Images/Video/Audio` — confirmado en vivo con
 * capturas reales del usuario que `MediaStore.Audio` NO indexa las notas de voz de WhatsApp
 * de forma confiable (una foto y un audio llegados casi al mismo segundo: la foto sí se
 * encontraba, el audio no). Solución adoptada del proyecto hermano `SoncoBot`
 * (`WhatsAppWatcher.kt`): leer directo las carpetas reales de WhatsApp en el almacenamiento,
 * sin pasar por el índice de MediaStore. Esto requiere el permiso especial
 * `MANAGE_EXTERNAL_STORAGE` ("Acceso a todos los archivos") — más fuerte que los permisos de
 * medios anteriores, ver `hasMediaPermission()`.
 */
object WhatsAppMediaScanner {
    private const val PREFS = "whatsapp_media_scanner_v1"
    private const val SEEN = "seen_media_paths"
    private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

    // Frases típicas de notificación de medio nuevo, en español e inglés — deciden CUÁNDO
    // vale la pena escanear las carpetas (no se escanea en cada notificación de WhatsApp).
    private val MEDIA_KEYWORDS = listOf(
        "foto", "photo", "imagen", "picture", "video",
        "audio", "mensaje de voz", "voice message", "nota de voz"
    )

    // Por pedido explícito del usuario: los stickers (y GIFs) no cuentan como medio a detectar.
    private val EXCLUDED_KEYWORDS = listOf("sticker", "gif")

    private val IGNORED_EXTENSIONS = setOf("nomedia", "tmp", "dat", "db", "journal", "ini", "log")

    // Carpetas reales de WhatsApp por tipo de medio (mismo mapeo que SoncoBot/WhatsAppWatcher.kt).
    // "WhatsApp Audio" son audios compartidos (no notas de voz); se agrega igual por si acaso.
    private val WA_SUBDIRS = linkedMapOf(
        "image" to "WhatsApp Images",
        "video" to "WhatsApp Video",
        "audio" to "WhatsApp Voice Notes",
    )

    private fun waBases(): List<String> {
        val sd = Environment.getExternalStorageDirectory().absolutePath
        return listOf(
            "$sd/Android/media/com.whatsapp/WhatsApp/Media",
            "$sd/Android/media/com.whatsapp.w4b/WhatsApp Business/Media",
            "$sd/WhatsApp/Media",
        )
    }

    fun isWhatsApp(packageName: String) = packageName in WHATSAPP_PACKAGES

    fun looksLikeNewMedia(title: String, text: String): Boolean {
        val combined = "$title $text".lowercase(Locale.ROOT)
        if (EXCLUDED_KEYWORDS.any { combined.contains(it) }) return false
        return MEDIA_KEYWORDS.any { combined.contains(it) }
    }

    /**
     * Desde v2.23: en Android 11+ (`R`) hace falta `MANAGE_EXTERNAL_STORAGE`
     * ("Acceso a todos los archivos") para leer las carpetas de otra app de forma confiable —
     * los permisos de medios por tipo (`READ_MEDIA_*`) ya no bastan para esto. En versiones
     * más viejas de Android, el permiso clásico de almacenamiento sigue siendo suficiente.
     */
    fun hasMediaPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    data class MediaMatch(val path: String, val displayName: String, val type: String, val lastModifiedMs: Long)

    /**
     * Busca SOLO archivos modificados en la ventana [postTimeMs - windowBeforeMs,
     * postTimeMs + windowAfterMs] dentro de las carpetas reales de WhatsApp. Ya procesados
     * (por ruta) no se repiten entre llamadas.
     */
    fun findNewMedia(context: Context, postTimeMs: Long, windowBeforeMs: Long = 30_000L, windowAfterMs: Long = 15_000L): List<MediaMatch> {
        val fromMs = postTimeMs - windowBeforeMs
        val toMs = postTimeMs + windowAfterMs
        val matches = mutableListOf<MediaMatch>()
        for ((type, subdir) in WA_SUBDIRS) {
            for (base in waBases()) {
                val files = runCatching { File(base, subdir).listFiles() }.getOrNull() ?: continue
                for (f in files) {
                    if (!f.isFile) continue
                    if (f.extension.lowercase(Locale.ROOT) in IGNORED_EXTENSIONS) continue
                    val mtime = f.lastModified()
                    if (mtime < fromMs || mtime > toMs) continue
                    matches += MediaMatch(f.absolutePath, f.name, type, mtime)
                }
            }
        }
        return matches.filter { markIfNew(context, it.path) }
    }

    private fun markIfNew(context: Context, key: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = (prefs.getStringSet(SEEN, emptySet()) ?: emptySet()).toMutableSet()
        if (!seen.add(key)) return false
        prefs.edit().putStringSet(SEEN, seen.toList().takeLast(200).toSet()).apply()
        return true
    }
}
