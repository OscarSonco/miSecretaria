package com.sco.misecretaria

import java.util.Locale

/** Texto hablado para notificaciones de aplicaciones generales (no billeteras). */
object NotificationSpeech {
    private val URL_REGEX = Regex("(?i)\\b((https?://|www\\.)\\S+)")

    /** Reemplaza cualquier URL/link por "hay un link" para no leerlo en voz alta. */
    fun sanitize(message: String): String = URL_REGEX.replace(message) { "hay un link" }

    /** v2.41: `sayName=false` omite el nombre de la app al principio (pedido explícito del
     * usuario, aplica a cualquier app general igual que a las billeteras). */
    fun general(appLabel: String, message: String, sayName: Boolean = true): String =
        if (sayName) "$appLabel, ${sanitize(message)}" else sanitize(message)

    private val DISTANCE_REGEX = Regex("(?i)^(\\d+(?:[.,]\\d+)?)\\s*(km|kilometros?|kilómetros?|mi|millas?|ft|pies|m|metros?)\\.?$")

    /**
     * v2.41: para apps de navegación (Google Maps) cuyo TÍTULO de notificación ya es la
     * distancia ("90 m") y el texto es el destino/dirección (que no aporta nada al leerse en
     * voz alta a cada paso, pedido explícito del usuario). Si el título calza con el patrón de
     * distancia, lo lee expandiendo la unidad a palabra ("90 m" -> "90 metros"); si no calza
     * (otra app, u otro formato de Maps), se devuelve el título tal cual como respaldo seguro.
     */
    fun distanceOnly(title: String): String {
        val trimmed = title.trim()
        val match = DISTANCE_REGEX.find(trimmed) ?: return trimmed
        val value = match.groupValues[1]
        val unitWord = when {
            match.groupValues[2].lowercase(Locale.ROOT).startsWith("k") -> "kilómetros"
            match.groupValues[2].lowercase(Locale.ROOT).startsWith("mi") -> "millas"
            match.groupValues[2].lowercase(Locale.ROOT).startsWith("f") || match.groupValues[2].lowercase(Locale.ROOT).startsWith("p") -> "pies"
            else -> "metros"
        }
        return "$value $unitWord"
    }
}
