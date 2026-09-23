package com.sco.misecretaria

import android.content.Context

enum class AlertButtons { OK_ONLY, REPEAT_AND_OK }

object DisplayPreferences {
    private const val PREFS = "display_preferences"
    private const val SPEECH = "speech"
    private const val ALERT = "alert"
    private const val BUTTONS = "buttons"
    private const val FULLSCREEN = "fullscreen"
    private const val SERVICE_ENABLED = "service_enabled"
    private const val VOICE_PROFILE = "voice_profile"
    private const val SPEECH_RATE = "speech_rate"
    private const val HEARTBEAT = "service_heartbeat"
    private const val DEVICE_LABEL = "device_label"

    /** Nombre de sucursal/dispositivo. Si nunca se personalizó, genera un código alfanumérico
     * aleatorio (ej. "MS-7K2F9Q") la primera vez y lo deja fijo — se puede renombrar después
     * desde la app o desde el bot de Telegram. */
    fun deviceLabel(context: Context): String {
        val current = get(context, DEVICE_LABEL, "")
        if (current.isNotBlank()) return current
        val generated = "MS-" + (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
        put(context, DEVICE_LABEL, generated)
        return generated
    }
    fun setDeviceLabel(context: Context, value: String) = put(context, DEVICE_LABEL, value.trim())

    fun serviceEnabled(context: Context) = get(context, SERVICE_ENABLED, "true") == "true"
    fun setServiceEnabled(context: Context, value: Boolean) = put(context, SERVICE_ENABLED, value.toString())
    fun voiceProfile(context: Context) = VoiceProfile.fromKey(get(context, VOICE_PROFILE, VoiceProfile.FEMALE.name))
    fun setVoiceProfile(context: Context, value: VoiceProfile) = put(context, VOICE_PROFILE, value.name)
    /** Multiplicador de velocidad de lectura, independiente del perfil de voz (0.5x a 2.0x). */
    fun speechRateMultiplier(context: Context) = get(context, SPEECH_RATE, "1.0").toFloatOrNull() ?: 1.0f
    fun setSpeechRateMultiplier(context: Context, value: Float) = put(context, SPEECH_RATE, value.toString())
    /** Última vez que el servicio de notificaciones procesó un evento (para detectar si sigue vivo). */
    fun heartbeat(context: Context) = get(context, HEARTBEAT, "0").toLongOrNull() ?: 0L
    fun touchHeartbeat(context: Context) = put(context, HEARTBEAT, System.currentTimeMillis().toString())

    fun fullScreenEnabled(context: Context) = get(context, FULLSCREEN, "false") == "true"
    fun setFullScreenEnabled(context: Context, value: Boolean) = put(context, FULLSCREEN, value.toString())
    fun speechEnabled(context: Context) = get(context, SPEECH, "true") == "true"
    fun setSpeechEnabled(context: Context, value: Boolean) = put(context, SPEECH, value.toString())
    fun alertEnabled(context: Context) = get(context, ALERT, "true") == "true"
    fun setAlertEnabled(context: Context, value: Boolean) = put(context, ALERT, value.toString())
    fun buttons(context: Context) = if (get(context, BUTTONS, AlertButtons.REPEAT_AND_OK.name) == AlertButtons.OK_ONLY.name) AlertButtons.OK_ONLY else AlertButtons.REPEAT_AND_OK
    fun setButtons(context: Context, value: AlertButtons) = put(context, BUTTONS, value.name)

    private fun get(context: Context, key: String, fallback: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, fallback) ?: fallback
    private fun put(context: Context, key: String, value: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, value).apply()
}
