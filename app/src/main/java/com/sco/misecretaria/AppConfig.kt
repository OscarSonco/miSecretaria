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

    fun remove(context: Context, name: String) = save(context, rules(context).filterNot { it.name.equals(name, true) })

    fun add(context: Context, name: String, packageId: String) {
        if (name.isBlank()) return
        val current = rules(context).filterNot { it.name.equals(name.trim(), true) }
        save(context, current + AppRule(name.trim(), packageId.trim(), true))
    }

    /** Mismo criterio que `WalletConfig.detect`: paquete exacto si hay `packageId`, si no, palabra completa. */
    fun detect(context: Context, packageName: String, title: String, text: String): String? {
        val combinedText = "$title $text".lowercase(Locale.ROOT)
        return rules(context).firstOrNull { rule ->
            if (!rule.enabled) return@firstOrNull false
            if (rule.packageId.isNotBlank()) packageName.equals(rule.packageId, ignoreCase = true)
            else containsWord(combinedText, rule.name)
        }?.name
    }

    private fun containsWord(haystack: String, needle: String): Boolean {
        if (needle.isBlank()) return false
        return Regex("\\b${Regex.escape(needle.lowercase(Locale.ROOT))}\\b").containsMatchIn(haystack)
    }

    private fun save(context: Context, values: List<AppRule>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, values.joinToString("\n") { "${it.name}|${it.packageId}|${if (it.enabled) "1" else "0"}" }).apply()
    }
}
