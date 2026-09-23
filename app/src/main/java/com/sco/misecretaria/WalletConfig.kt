package com.sco.misecretaria

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
        val lines = if (raw.contains("\\n")) raw.split("\\n") else raw.split("\n")
        return lines.mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size == 3) WalletRule(parts[0], parts[1], parts[2] == "1") else null
        }.ifEmpty { defaults }
    }

    fun setEnabled(context: Context, name: String, enabled: Boolean) = save(context, rules(context).map { if (it.name == name) it.copy(enabled = enabled) else it })

    fun remove(context: Context, name: String) = save(context, rules(context).filterNot { it.name.equals(name, true) })

    fun add(context: Context, name: String, packageId: String) {
        if (name.isBlank()) return
        val current = rules(context).filterNot { it.name.equals(name.trim(), true) }
        save(context, current + WalletRule(name.trim(), packageId.trim(), true))
    }

    /**
     * Si la regla tiene `packageId`, exige coincidencia EXACTA de paquete (no substring de
     * título/texto: evita falsos positivos como "pizzas" conteniendo "zas"). Solo si el
     * `packageId` está vacío, cae a buscar el nombre como palabra completa en título/texto.
     */
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

    private fun save(context: Context, values: List<WalletRule>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, values.joinToString("\n") { "${it.name}|${it.packageId}|${if (it.enabled) "1" else "0"}" }).apply()
    }
}
