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
 *
 * **v2.30:** documentos (PDF, Word, etc.) — pedido explícito del usuario. A diferencia de
 * foto/video/nota de voz, la notificación de un documento NO tiene una frase fija: WhatsApp
 * pone el EMOJI 📄 seguido del nombre real del archivo (confirmado en vivo:
 * "📄 HOJA DE VIDA - LUIS GUSTAVO BECERRA_2026.docx"), así que no hay frase que buscar — se
 * detecta por la presencia del emoji 📄 en vez de una palabra/frase.
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
    // v2.35: cada frase ahora sabe a qué TIPO pertenece (antes era una lista plana sin tipo,
    // usada solo para decidir sí/no) — necesario para que `findNewMedia()` pueda priorizar la
    // carpeta correcta en vez de tomar "lo primero que encuentre" sin importar el tipo real.
    private val MEDIA_PHRASE_TYPES = listOf(
        "envió una foto" to "image", "sent a photo" to "image", "envió una imagen" to "image",
        "envió un video" to "video", "sent a video" to "video",
        "mensaje de voz" to "audio", "voice message" to "audio",
    )
    private val MEDIA_KEYWORDS = MEDIA_PHRASE_TYPES.map { it.first }

    // v2.30: los documentos no tienen frase fija (WhatsApp pone el nombre real del archivo),
    // así que se detectan por el emoji que SÍ es fijo en la notificación.
    private const val DOCUMENT_EMOJI = "📄"

    // v2.37: confirmado en vivo (2026-09-25) que un video/foto ENVIADO CON UN TEXTO PROPIO
    // (ej. "🎥 Ahora envío el monociclo con motociclista (0:15)") ya NO trae la frase fija
    // "Envió un video."/"Envió una foto." — WhatsApp la reemplaza por el texto que la persona
    // escribió, dejando SOLO el emoji (y, para video, la duración) como señal fija. Antes de
    // este cambio, `MEDIA_PHRASE_TYPES` (frase exacta) era la única forma de detectar video/
    // foto — un video con texto propio no disparaba el escaneo en absoluto. Se agrega el
    // emoji como señal PRIMARIA (mismo principio que ya se usa para documentos desde v2.30:
    // preferir la señal más específica que sobreviva un texto libre), con la frase exacta como
    // respaldo. Para audio hay DOS emojis posibles según el origen (🎤 nota de voz grabada,
    // 🎵 audio compartido como archivo — ver v2.32) — se agregan los dos.
    private val EMOJI_TYPES = listOf(
        DOCUMENT_EMOJI to "document",
        "🎥" to "video",
        "📷" to "image",
        "🎤" to "audio",
        "🎵" to "audio",
    )

    // Por pedido explícito del usuario: los stickers (y GIFs) no cuentan como medio a detectar.
    private val EXCLUDED_KEYWORDS = listOf("sticker", "gif")

    // v2.31: notificaciones "resumen" de un grupo con varios mensajes sin leer — WhatsApp les
    // pone un título como "RedSonco 😎😎😎 (7 mensajes)". Confirmado en vivo (2026-09-25) que
    // NO siempre llevan `FLAG_GROUP_SUMMARY` (el filtro de la línea de arriba en
    // WalletNotificationListener no las agarra todas), y que Android las repuebla/reenvía
    // varias veces sin que haya un mensaje genuinamente nuevo — el "último mensaje" que
    // `latestMessageText()` extrae de ellas puede ser uno VIEJO (ej. un PDF de 12 días antes,
    // sentado sin leer en un grupo silenciado). Si ese texto viejo menciona un documento/foto/
    // video/audio, `looksLikeNewMedia()` disparaba un escaneo igual — y como el escaneo busca
    // "lo que sea que haya en la carpeta ahora mismo", terminaba robándole a otra conversación
    // el archivo real que sí acababa de llegar (confirmado: un audio y un PDF genuinos
    // desaparecieron del Historial porque quedaron adjuntos a esta notificación resumen en vez
    // de a la suya). Se excluye por completo de la detección de medios, sin importar lo que
    // diga el texto — no hay forma de confiar en que sea "nuevo" de verdad.
    private val GROUP_SUMMARY_TITLE = Regex("""\(\s*\d+\s+(mensajes|messages)\s*\)""", RegexOption.IGNORE_CASE)

    // v2.33: se quitaron "db" y "log" de esta lista — pedido explícito del usuario, que
    // recibe justo esas extensiones de sus empleados (bases de datos/reportes de caja chica
    // para analizar). Esta lista se copió originalmente de `SoncoBot/WhatsAppWatcher.kt`
    // (otro proyecto, con otro caso de uso) para descartar archivos temporales/internos de
    // WhatsApp — pero ahí nunca hubo un caso real de negocio que necesitara justamente esas
    // dos extensiones como documentos legítimos, y aquí sí. Las demás se dejan igual (no hay
    // evidencia de que también bloqueen algo que el usuario necesite).
    private val IGNORED_EXTENSIONS = setOf("nomedia", "tmp", "dat", "journal", "ini")

    private const val GENERIC_SCAN_MAX_DEPTH = 4

    // Carpetas reales de WhatsApp por tipo de medio (mismo mapeo que SoncoBot/WhatsAppWatcher.kt).
    // v2.32: "audio" ahora revisa DOS carpetas — "WhatsApp Voice Notes" (notas de voz grabadas
    // en el chat) Y "WhatsApp Audio" (archivos de audio COMPARTIDOS, ej. un .mp3 enviado como
    // adjunto). Son carpetas distintas y reales en el teléfono (confirmado con `adb shell ls`:
    // un .mp3 recién enviado apareció en "WhatsApp Audio", nunca en "WhatsApp Voice Notes") —
    // antes de v2.32 solo se miraba la segunda, así que un audio compartido (no nota de voz)
    // nunca se encontraba, aunque su notificación sí decía "Mensaje de voz" y disparaba el
    // escaneo igual.
    private val WA_SUBDIRS = linkedMapOf(
        "image" to listOf("WhatsApp Images"),
        "video" to listOf("WhatsApp Video"),
        "audio" to listOf("WhatsApp Voice Notes", "WhatsApp Audio"),
        "document" to listOf("WhatsApp Documents"),
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

    fun looksLikeNewMedia(title: String, text: String): Boolean = expectedType(title, text) != null

    /**
     * v2.35: qué TIPO de medio anuncia esta notificación en particular ("image"/"video"/
     * "audio"/"document", o `null` si no parece un medio nuevo) — antes solo existía
     * `looksLikeNewMedia()` (sí/no), sin decir de qué tipo. Se necesita para que
     * `findNewMedia()` busque PRIMERO en la carpeta correcta según lo que la notificación
     * realmente anuncia, en vez de tomar el primer archivo que encuentre sin importar el tipo
     * (confirmado en vivo, 2026-09-25, con un envío de 9 archivos casi simultáneos: una
     * notificación de "Envió un video" terminó con una FOTO adjunta, y una de "Mensaje de voz"
     * terminó con un VIDEO — ambas robadas por otro archivo real que llegó en la misma
     * ventana de tiempo, del tipo que se buscaba primero por casualidad de orden, no por
     * coincidir con lo que la notificación decía).
     */
    fun expectedType(title: String, text: String): String? {
        if (GROUP_SUMMARY_TITLE.containsMatchIn(title)) return null
        val raw = "$title $text"
        val combined = raw.lowercase(Locale.ROOT)
        if (EXCLUDED_KEYWORDS.any { combined.contains(it) }) return null
        EMOJI_TYPES.firstOrNull { (emoji, _) -> raw.contains(emoji) }?.let { return it.second }
        return MEDIA_PHRASE_TYPES.firstOrNull { (phrase, _) -> combined.contains(phrase) }?.second
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
     *
     * `preferredType` (v2.35): el tipo que la notificación en cuestión anuncia (ver
     * `expectedType()`). El resultado sigue incluyendo TODOS los archivos nuevos de
     * cualquier tipo encontrados en la ventana (para no perder archivos adicionales que
     * lleguen casi al mismo tiempo — se guardan como notificaciones aparte, ver
     * `WalletNotificationListener`), pero el ORDEN prioriza: primero los del tipo esperado,
     * y dentro de cada tipo, el archivo cuya fecha de modificación esté más CERCA de
     * `postTimeMs` — así el primero de la lista (el que se adjunta a ESTA notificación) es
     * el más probable de ser el correcto, en vez de "lo que sea que haya encontrado primero
     * el recorrido de carpetas" (antes ni siquiera miraba el tipo: una notificación de video
     * podía terminar con una foto adjunta si la foto se encontraba primero).
     */
    fun findNewMedia(context: Context, postTimeMs: Long, windowBeforeMs: Long = 30_000L, windowAfterMs: Long = 15_000L, preferredType: String? = null): List<MediaMatch> {
        val fromMs = postTimeMs - windowBeforeMs
        val toMs = postTimeMs + windowAfterMs
        val matches = mutableListOf<MediaMatch>()
        var dirsFound = 0
        for ((type, subdirs) in WA_SUBDIRS) {
            for (subdir in subdirs) {
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
            .sortedWith(
                compareBy(
                    { if (preferredType != null && it.type == preferredType) 0 else 1 },
                    { kotlin.math.abs(it.lastModifiedMs - postTimeMs) },
                )
            )
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
