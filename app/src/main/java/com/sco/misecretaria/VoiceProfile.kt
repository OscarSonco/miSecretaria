package com.sco.misecretaria

/**
 * Perfiles de voz. SpeechEngine intenta primero usar una voz real del motor TTS marcada
 * masculina/femenina (si el teléfono la tiene instalada); si no hay ninguna disponible,
 * usa pitch+velocidad como respaldo, que sí funciona en cualquier equipo.
 */
enum class VoiceProfile(val label: String, val pitch: Float, val rate: Float) {
    FEMALE("Mujer", 1.18f, 1.0f),
    MALE("Varón", 0.48f, 0.90f);

    companion object {
        fun fromKey(key: String): VoiceProfile = entries.firstOrNull { it.name == key } ?: FEMALE
    }
}
