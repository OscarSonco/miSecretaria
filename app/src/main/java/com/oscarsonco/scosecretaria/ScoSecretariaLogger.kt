package com.oscarsonco.scosecretaria

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScoSecretariaLogger {
    private const val TAG = "ScoSecretariaV0.1"
    private const val FILE_NAME = "ScoSecretariaV0.1.log"

    fun info(context: Context, message: String) {
        write(context, "INFO", message)
    }

    fun read(context: Context): String = File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readText() ?: "Sin eventos registrados.\n"

    fun error(context: Context, message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        write(context, "ERROR", "$message ${throwable?.message.orEmpty()}")
    }

    private fun write(context: Context, level: String, message: String) {
        Log.d(TAG, message)
        runCatching {
            val timestamp = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.getDefault()
            ).format(Date())

            File(context.filesDir, FILE_NAME).appendText(
                "$timestamp [$level] $message\n"
            )
        }
    }
}
