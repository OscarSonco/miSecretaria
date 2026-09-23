package com.sco.misecretaria

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Fase 1 (prototipo, solo logging) del Paso 2 de CLAUDE.md — opción b decidida por el
 * usuario el 2026-09-23: cuando llega una notificación de WhatsApp que anuncia un medio
 * nuevo (foto/audio/video), busca en MediaStore solo los archivos agregados justo
 * alrededor de ese instante. NUNCA recorre el histórico completo.
 *
 * Riesgos sin verificar (ver CLAUDE.md Paso 2): depende de que WhatsApp indexe el archivo
 * en MediaStore (descarga automática activada) antes de que se acabe la ventana de
 * reintento, y de que el `RELATIVE_PATH`/`DATA` contenga "WhatsApp".
 */
object WhatsAppMediaScanner {
    private const val PREFS = "whatsapp_media_scanner_v1"
    private const val SEEN = "seen_media_ids"
    private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

    // Frases típicas de notificación de medio nuevo, en español e inglés.
    private val MEDIA_KEYWORDS = listOf(
        "foto", "photo", "imagen", "picture", "video",
        "audio", "mensaje de voz", "voice message", "nota de voz"
    )

    // Por pedido explícito del usuario: los stickers (y GIFs) no cuentan como medio a detectar.
    private val EXCLUDED_KEYWORDS = listOf("sticker", "gif")

    fun isWhatsApp(packageName: String) = packageName in WHATSAPP_PACKAGES

    fun looksLikeNewMedia(title: String, text: String): Boolean {
        val combined = "$title $text".lowercase(Locale.ROOT)
        if (EXCLUDED_KEYWORDS.any { combined.contains(it) }) return false
        return MEDIA_KEYWORDS.any { combined.contains(it) }
    }

    fun hasMediaPermission(context: Context): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    data class MediaMatch(val uri: Uri, val displayName: String, val type: String, val dateAddedSec: Long)

    /**
     * Busca SOLO archivos agregados a MediaStore en la ventana [postTimeMs - windowBeforeMs,
     * postTimeMs + windowAfterMs] cuya ruta contenga "WhatsApp". Ya procesados (por URI) no
     * se repiten entre llamadas.
     */
    fun findNewMedia(context: Context, postTimeMs: Long, windowBeforeMs: Long = 30_000L, windowAfterMs: Long = 15_000L): List<MediaMatch> {
        val fromSec = (postTimeMs - windowBeforeMs) / 1000
        val toSec = (postTimeMs + windowAfterMs) / 1000
        val all = mutableListOf<MediaMatch>()
        all += queryCollection(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image", fromSec, toSec)
        all += queryCollection(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video", fromSec, toSec)
        all += queryCollection(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "audio", fromSec, toSec)
        return all.filter { markIfNew(context, it.uri.toString()) }
    }

    private fun pathColumn(): String =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA

    private fun queryCollection(context: Context, collection: Uri, type: String, fromSec: Long, toSec: Long): List<MediaMatch> {
        val pathCol = pathColumn()
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED, pathCol)
        val selection = "${MediaStore.MediaColumns.DATE_ADDED} >= ? AND ${MediaStore.MediaColumns.DATE_ADDED} <= ?"
        val args = arrayOf(fromSec.toString(), toSec.toString())
        val matches = mutableListOf<MediaMatch>()
        runCatching {
            context.contentResolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val pathIdx = cursor.getColumnIndexOrThrow(pathCol)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathIdx) ?: ""
                    if (!path.contains("WhatsApp", ignoreCase = true)) continue
                    val id = cursor.getLong(idCol)
                    matches += MediaMatch(ContentUris.withAppendedId(collection, id), cursor.getString(nameCol) ?: "", type, cursor.getLong(dateCol))
                }
            }
        }
        return matches
    }

    private fun markIfNew(context: Context, key: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = (prefs.getStringSet(SEEN, emptySet()) ?: emptySet()).toMutableSet()
        if (!seen.add(key)) return false
        prefs.edit().putStringSet(SEEN, seen.toList().takeLast(200).toSet()).apply()
        return true
    }
}
