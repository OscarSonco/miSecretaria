package com.oscarsonco.scosecretaria

import android.content.Context
import java.util.Locale

data class WalletRule(val name: String, val packageId: String, val enabled: Boolean)

object WalletConfig {
    private const val PREFS = "wallet_config_v11"
    private const val KEY = "rules"
    private val defaults = listOf(
        WalletRule("YAPE", "", true),
        WalletRule("YOLO", "", true),
        WalletRule("ZAS", "bec.vdb.direct", true),
        WalletRule("YASTA", "", true),
        WalletRule("AlToke", "", true)
    )

    fun rules(context: Context): List<WalletRule> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return defaults
        return raw.split("\\n").mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size == 3) WalletRule(parts[0], parts[1], parts[2] == "1") else null
        }.ifEmpty { defaults }
    }

    fun setEnabled(context: Context, name: String, enabled: Boolean) = save(context, rules(context).map { if (it.name == name) it.copy(enabled = enabled) else it })

    fun add(context: Context, name: String, packageId: String) {
        if (name.isBlank()) return
        val current = rules(context).filterNot { it.name.equals(name.trim(), true) }
        save(context, current + WalletRule(name.trim(), packageId.trim(), true))
    }

    fun detect(context: Context, source: String): String? {
        val normalized = source.lowercase(Locale.ROOT)
        return rules(context).firstOrNull { rule ->
            rule.enabled && ((rule.packageId.isNotBlank() && normalized.contains(rule.packageId.lowercase(Locale.ROOT))) || normalized.contains(rule.name.lowercase(Locale.ROOT)))
        }?.name
    }

    private fun save(context: Context, values: List<WalletRule>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, values.joinToString("\\n") { "${it.name}|${it.packageId}|${if (it.enabled) "1" else "0"}" }).apply()
    }
}
