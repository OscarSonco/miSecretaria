package com.sco.misecretaria

import android.content.Context
import java.util.Locale

data class AppRule(val name: String, val packageId: String, val enabled: Boolean)

/** Aplicaciones "generales" (no billeteras): WhatsApp, Facebook Messenger, SMS, etc. */
object AppConfig {
    private const val PREFS = "app_config_v1"
    private const val KEY = "rules"

    fun rules(context: Context): List<AppRule> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        val lines = if (raw.contains("\\n")) raw.split("\\n") else raw.split("\n")
        return lines.mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size == 3) AppRule(parts[0], parts[1], parts[2] == "1") else null
        }
    }

    fun setEnabled(context: Context, name: String, enabled: Boolean) = save(context, rules(context).map { if (it.name == name) it.copy(enabled = enabled) else it })

    fun add(context: Context, name: String, packageId: String) {
        if (name.isBlank()) return
        val current = rules(context).filterNot { it.name.equals(name.trim(), true) }
        save(context, current + AppRule(name.trim(), packageId.trim(), true))
    }

    fun detect(context: Context, source: String): String? {
        val normalized = source.lowercase(Locale.ROOT)
        return rules(context).firstOrNull { rule ->
            rule.enabled && ((rule.packageId.isNotBlank() && normalized.contains(rule.packageId.lowercase(Locale.ROOT))) || normalized.contains(rule.name.lowercase(Locale.ROOT)))
        }?.name
    }

    private fun save(context: Context, values: List<AppRule>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, values.joinToString("\n") { "${it.name}|${it.packageId}|${if (it.enabled) "1" else "0"}" }).apply()
    }
}
