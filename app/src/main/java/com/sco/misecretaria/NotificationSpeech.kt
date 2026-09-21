package com.sco.misecretaria

/** Texto hablado para notificaciones de aplicaciones generales (no billeteras). */
object NotificationSpeech {
    private val URL_REGEX = Regex("(?i)\\b((https?://|www\\.)\\S+)")

    /** Reemplaza cualquier URL/link por "hay un link" para no leerlo en voz alta. */
    fun sanitize(message: String): String = URL_REGEX.replace(message) { "hay un link" }

    fun general(appLabel: String, message: String): String = "$appLabel, ${sanitize(message)}"
}
