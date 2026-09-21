package com.sco.misecretaria

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Backup/restauración de configuración: preferencias, billeteras y aplicaciones generales. */
object BackupManager {
    fun exportJson(context: Context): String {
        val root = JSONObject()
        root.put("app", AppInfo.NAME)
        root.put("version", AppInfo.VERSION)
        root.put("exportedAt", WalletNotificationStore.now())

        val prefs = JSONObject()
        prefs.put("speechEnabled", DisplayPreferences.speechEnabled(context))
        prefs.put("alertEnabled", DisplayPreferences.alertEnabled(context))
        prefs.put("fullScreenEnabled", DisplayPreferences.fullScreenEnabled(context))
        prefs.put("serviceEnabled", DisplayPreferences.serviceEnabled(context))
        prefs.put("buttons", DisplayPreferences.buttons(context).name)
        prefs.put("voiceProfile", DisplayPreferences.voiceProfile(context).name)
        prefs.put("speechRate", DisplayPreferences.speechRateMultiplier(context).toDouble())
        root.put("preferences", prefs)

        val wallets = JSONArray()
        WalletConfig.rules(context).forEach { r -> wallets.put(JSONObject().put("name", r.name).put("packageId", r.packageId).put("enabled", r.enabled)) }
        root.put("wallets", wallets)

        val apps = JSONArray()
        AppConfig.rules(context).forEach { r -> apps.put(JSONObject().put("name", r.name).put("packageId", r.packageId).put("enabled", r.enabled)) }
        root.put("apps", apps)

        return root.toString(2)
    }

    fun importJson(context: Context, json: String) {
        val root = JSONObject(json)
        root.optJSONObject("preferences")?.let { p ->
            DisplayPreferences.setSpeechEnabled(context, p.optBoolean("speechEnabled", true))
            DisplayPreferences.setAlertEnabled(context, p.optBoolean("alertEnabled", true))
            DisplayPreferences.setFullScreenEnabled(context, p.optBoolean("fullScreenEnabled", false))
            DisplayPreferences.setServiceEnabled(context, p.optBoolean("serviceEnabled", true))
            runCatching { DisplayPreferences.setButtons(context, AlertButtons.valueOf(p.optString("buttons", AlertButtons.REPEAT_AND_OK.name))) }
            runCatching { DisplayPreferences.setVoiceProfile(context, VoiceProfile.fromKey(p.optString("voiceProfile", VoiceProfile.FEMALE.name))) }
            DisplayPreferences.setSpeechRateMultiplier(context, p.optDouble("speechRate", 1.0).toFloat())
        }
        root.optJSONArray("wallets")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                WalletConfig.add(context, o.optString("name"), o.optString("packageId"))
                WalletConfig.setEnabled(context, o.optString("name"), o.optBoolean("enabled", true))
            }
        }
        root.optJSONArray("apps")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                AppConfig.add(context, o.optString("name"), o.optString("packageId"))
                AppConfig.setEnabled(context, o.optString("name"), o.optBoolean("enabled", true))
            }
        }
    }
}
