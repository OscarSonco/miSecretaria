package com.sco.misecretaria

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScoSecretariaLogger {
    // Nombre interno estable (no depende de la versión); el nombre EXPORTADO al usuario
    // sí se versiona dinámicamente, ver AppInfo.LOG_EXPORT_NAME usado en MainActivity.
    private const val TAG = "miSecretaria"
    private const val FILE_NAME = "miSecretaria_debug.log"
    private const val MAX_LINES = 800
    private var linesSinceRotationCheck = 0

    fun info(context: Context, message: String) {
        write(context, "INFO", message)
    }

    /** Trazas verbosas (ej. notificación recibida pero no coincide con ninguna regla). */
    fun debug(context: Context, message: String) {
        write(context, "DEBUG", message)
    }

    fun read(context: Context): String = File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readText() ?: "Sin eventos registrados.\n"

    fun error(context: Context, message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        write(context, "ERROR", "$message ${throwable?.message.orEmpty()}")
    }

    private fun write(context: Context, level: String, message: String) {
        // Solo se recopila en builds DEBUG (BuildConfig.DEBUG), evitando volcar datos en release.
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, message)
        runCatching {
            val timestamp = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.getDefault()
            ).format(Date())

            File(context.filesDir, FILE_NAME).appendText(
                "$timestamp [$level] $message\n"
            )
            rotateIfNeeded(context)
        }
    }

    /** Recorta el log a las últimas MAX_LINES líneas cada ~50 escrituras, para que no crezca sin límite. */
    private fun rotateIfNeeded(context: Context) {
        linesSinceRotationCheck++
        if (linesSinceRotationCheck < 50) return
        linesSinceRotationCheck = 0
        val file = File(context.filesDir, FILE_NAME)
        val lines = file.readLines()
        if (lines.size > MAX_LINES) {
            file.writeText(lines.takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
        }
    }
}
