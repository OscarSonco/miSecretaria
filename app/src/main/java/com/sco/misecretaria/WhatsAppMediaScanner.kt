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
 *
 * **v2.24:** se agregó la variante de ruta con cuenta (`.../WhatsApp/accounts/<id>/Media/...`
 * — WhatsApp multi-cuenta), confirmada en vivo con capturas reales del usuario.
 *
 * **v2.25:** redundancia pedida explícitamente por el usuario ("diferentes marcas/modelos de
 * celulares") — si las rutas conocidas (clásica y con cuenta) no encuentran nada, cae a un
 * escaneo genérico por nombre de carpeta (ver `genericWhatsAppBases()`/`findDirsNamed()`),
 * mismo espíritu que `SoncoBot/WhatsAppWatcher.kt`. Las rutas conocidas siguen siendo el
 * camino principal — el genérico es solo respaldo.
 *
 * **v2.28:** "WhatsApp Voice Notes" agrupa los `.opus` en subcarpetas por semana (a diferencia
 * de imagen/video, que los dejan sueltos) — `filesIn()` entra un nivel para encontrarlos.
 *
 * **v2.29:** `MEDIA_KEYWORDS` pasó de palabras sueltas ("foto", "audio", "video") a las
 * FRASES exactas que WhatsApp genera — confirmado en vivo que una palabra suelta produce
 * falsos positivos graves: un mensaje de TEXTO que solo menciona la palabra "audio" en una
 * frase normal disparaba el escaneo y podía robarle a otra conversación una foto/video/audio
 * ajeno que llegó casi al mismo tiempo (dedupe por ruta = solo un dueño posible por archivo).
 */
object WhatsAppMediaScanner {
    private const val PREFS = "whatsapp_media_scanner_v1"
    private const val SEEN = "seen_media_paths"
    private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

    // v2.29: FRASES exactas que genera WhatsApp mismo para avisar de un medio nuevo — no
    // palabras sueltas. Confirmado en vivo que "foto"/"audio"/"video" como palabra suelta
    // producía falsos positivos graves: un mensaje de TEXTO diciendo "Este es el audio de
    // doña Marta" (una persona hablando de un audio, no la notificación real de WhatsApp)
    // disparaba el escaneo y terminaba robándole una FOTO ajena a otra conversación que
    // llegó casi al mismo tiempo. Las frases de abajo son las que WhatsApp realmente pone en
    // la notificación ("📷 Envió una foto.", "🎥 Envió un video. (0:06)", "🎤 Mensaje de voz
    // (0:07)") — muy poco probable que alguien las escriba tal cual en una conversación normal.
    private val MEDIA_KEYWORDS = listOf(
        "envió una foto", "sent a photo", "envió una imagen",
        "envió un video", "sent a video",
        "mensaje de voz", "voice message"
    )

    // Por pedido explícito del usuario: los stickers (y GIFs) no cuentan como medio a detectar.
    private val EXCLUDED_KEYWORDS = listOf("sticker", "gif")

    private val IGNORED_EXTENSIONS = setOf("nomedia", "tmp", "dat", "db", "journal", "ini", "log")

    private const val GENERIC_SCAN_MAX_DEPTH = 4

    // Carpetas reales de WhatsApp por tipo de medio (mismo mapeo que SoncoBot/WhatsAppWatcher.kt).
    // "WhatsApp Audio" son audios compartidos (no notas de voz); se agrega igual por si acaso.
    private val WA_SUBDIRS = linkedMapOf(
        "image" to "WhatsApp Images",
        "video" to "WhatsApp Video",
        "audio" to "WhatsApp Voice Notes",
    )

    // Raíces de WhatsApp SIN el "/Media" final — porque desde que WhatsApp soporta varias
    // cuentas vinculadas en el mismo teléfono, algunos instalan meten una carpeta de cuenta
    // ENTRE la raíz y "Media" (confirmado en vivo, 2026-09-23: la ruta real en un teléfono de
    // prueba fue `.../WhatsApp/accounts/<id>/Media/...`, no `.../WhatsApp/Media/...` directo).
    // `mediaDirsFor()` revisa las dos formas.
    private fun waRoots(): List<String> {
        val sd = Environment.getExternalStorageDirectory().absolutePath
        return listOf(
            "$sd/Android/media/com.whatsapp/WhatsApp",
            "$sd/Android/media/com.whatsapp.w4b/WhatsApp Business",
            "$sd/WhatsApp",
        )
    }

    /** Para un subdirectorio tipo "WhatsApp Images", devuelve todas las carpetas reales que
     * podrían contenerlo — la forma clásica (`<raíz>/Media/<subdir>`) y la forma con cuenta
     * (`<raíz>/accounts/<id>/Media/<subdir>`, cualquier `<id>` que exista).
     *
     * v2.25: si ninguna de las rutas conocidas existe, cae a un escaneo genérico (mismo
     * espíritu que `findWhatsAppDirs()` de `SoncoBot/WhatsAppWatcher.kt`) — busca la carpeta
     * por NOMBRE dentro de cualquier directorio relacionado con WhatsApp, sin asumir una
     * estructura fija. Es la redundancia pedida explícitamente por el usuario para que
     * funcione en marcas/modelos de celular con una organización de carpetas distinta a la ya
     * confirmada en este teléfono (`accounts/<id>/Media/...`). Las rutas conocidas siguen
     * siendo el camino principal (más rápido, sin recorrer nada) — el genérico es solo
     * respaldo cuando las conocidas no encuentran nada. */
    private fun mediaDirsFor(subdir: String): List<File> {
        val found = mutableListOf<File>()
        for (root in waRoots()) {
            val rootDir = File(root)
            File(rootDir, "Media/$subdir").takeIf { it.isDirectory }?.let { found += it }
            runCatching { File(rootDir, "accounts").listFiles() }.getOrNull()?.forEach { accountDir ->
                if (accountDir.isDirectory) {
                    File(accountDir, "Media/$subdir").takeIf { it.isDirectory }?.let { found += it }
                }
            }
        }
        if (found.isEmpty()) {
            for (base in genericWhatsAppBases()) {
                found += findDirsNamed(base, subdir, maxDepth = GENERIC_SCAN_MAX_DEPTH)
            }
        }
        return found.distinctBy { it.absolutePath }
    }

    /** Cualquier carpeta cuyo nombre contenga "whatsapp" bajo `Android/media/` (ahí viven las
     * apps con Scoped Storage, com.whatsapp/com.whatsapp.w4b/clones tipo GBWhatsApp) o
     * directamente en la raíz del almacenamiento (`/sdcard/WhatsApp`, instalaciones viejas). */
    private fun genericWhatsAppBases(): List<File> {
        val sd = Environment.getExternalStorageDirectory()
        val bases = mutableListOf<File>()
        runCatching { File(sd, "Android/media").listFiles() }.getOrNull()?.forEach { appDir ->
            if (appDir.isDirectory && "whatsapp" in appDir.name.lowercase(Locale.ROOT)) bases += appDir
        }
        runCatching { sd.listFiles() }.getOrNull()?.forEach { dir ->
            if (dir.isDirectory && "whatsapp" in dir.name.lowercase(Locale.ROOT)) bases += dir
        }
        return bases
    }

    /** Busca recursivamente (hasta `maxDepth` niveles) una carpeta llamada exactamente
     * `targetName` dentro de `root`. Acotado a propósito — es un respaldo que solo corre
     * cuando las rutas conocidas fallan, no algo que se recorra en cada notificación. */
    private fun findDirsNamed(root: File, targetName: String, maxDepth: Int): List<File> {
        if (maxDepth < 0) return emptyList()
        val found = mutableListOf<File>()
        val children = runCatching { root.listFiles() }.getOrNull() ?: return found
        for (child in children) {
            if (!child.isDirectory) continue
            if (child.name.equals(targetName, ignoreCase = true)) {
                found += child
            } else if (maxDepth > 0) {
                found += findDirsNamed(child, targetName, maxDepth - 1)
            }
        }
        return found
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
        var dirsFound = 0
        for ((type, subdir) in WA_SUBDIRS) {
            val dirs = mediaDirsFor(subdir)
            dirsFound += dirs.size
            for (dir in dirs) {
                for (f in filesIn(dir, extraDepth = 1)) {
                    if (f.extension.lowercase(Locale.ROOT) in IGNORED_EXTENSIONS) continue
                    val mtime = f.lastModified()
                    if (mtime < fromMs || mtime > toMs) continue
                    matches += MediaMatch(f.absolutePath, f.name, type, mtime)
                }
            }
        }
        // Diagnóstico: si ni las rutas conocidas NI el escaneo genérico de respaldo
        // encontraron ninguna carpeta, es un problema de fondo (¿falta el permiso? ¿WhatsApp
        // guarda los medios en un lugar que ni el escaneo genérico cubre?) — distinto de "la
        // carpeta es correcta pero el archivo todavía no aparece" (problema de tiempo, se
        // resuelve solo reintentando).
        if (dirsFound == 0) {
            ScoSecretariaLogger.debug(context, "WhatsAppMediaScanner: ninguna carpeta de medios de WhatsApp accesible ni por ruta conocida ni por escaneo genérico")
        }
        return matches.filter { markIfNew(context, it.path) }
    }

    /**
     * Archivos directos en `dir`, más los de sus subcarpetas hasta `extraDepth` niveles.
     * **v2.28, confirmado en vivo:** "WhatsApp Voice Notes" (a diferencia de "WhatsApp
     * Images"/"WhatsApp Video", que guardan los archivos sueltos) los agrupa en subcarpetas
     * por semana (ej. `202639/PTT-...opus`) — con solo `dir.listFiles()` nunca se veía ni un
     * solo archivo de audio, solo las carpetas de semana (por eso el audio fallaba siempre,
     * a diferencia de foto/video que sí funcionaban la mayoría de las veces). Con
     * `extraDepth=1` se cubre ese caso (y "Private"/"Sent", que existen en más de un tipo) sin
     * recorrer indefinidamente.
     */
    private fun filesIn(dir: File, extraDepth: Int): List<File> {
        val entries = runCatching { dir.listFiles() }.getOrNull() ?: return emptyList()
        val files = entries.filter { it.isFile }
        if (extraDepth <= 0) return files
        val nested = entries.filter { it.isDirectory }.flatMap { filesIn(it, extraDepth - 1) }
        return files + nested
    }

    private fun markIfNew(context: Context, key: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = (prefs.getStringSet(SEEN, emptySet()) ?: emptySet()).toMutableSet()
        if (!seen.add(key)) return false
        prefs.edit().putStringSet(SEEN, seen.toList().takeLast(200).toSet()).apply()
        return true
    }
}
