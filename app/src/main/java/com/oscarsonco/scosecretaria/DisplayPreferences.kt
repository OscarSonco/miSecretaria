package com.oscarsonco.scosecretaria

import android.content.Context

enum class DisplayMode { NEW, OLD }
enum class AlertButtons { OK_ONLY, REPEAT_AND_OK }

object DisplayPreferences {
    private const val PREFS = "display_preferences"
    private const val MODE = "mode"
    private const val SPEECH = "speech"
    private const val ALERT = "alert"
    private const val BUTTONS = "buttons"

    fun mode(context: Context) = if (get(context, MODE, DisplayMode.NEW.name) == DisplayMode.OLD.name) DisplayMode.OLD else DisplayMode.NEW
    fun setMode(context: Context, value: DisplayMode) = put(context, MODE, value.name)
    fun speechEnabled(context: Context) = get(context, SPEECH, "true") == "true"
    fun setSpeechEnabled(context: Context, value: Boolean) = put(context, SPEECH, value.toString())
    fun alertEnabled(context: Context) = get(context, ALERT, "true") == "true"
    fun setAlertEnabled(context: Context, value: Boolean) = put(context, ALERT, value.toString())
    fun buttons(context: Context) = if (get(context, BUTTONS, AlertButtons.REPEAT_AND_OK.name) == AlertButtons.OK_ONLY.name) AlertButtons.OK_ONLY else AlertButtons.REPEAT_AND_OK
    fun setButtons(context: Context, value: AlertButtons) = put(context, BUTTONS, value.name)

    private fun get(context: Context, key: String, fallback: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, fallback) ?: fallback
    private fun put(context: Context, key: String, value: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, value).apply()
}
