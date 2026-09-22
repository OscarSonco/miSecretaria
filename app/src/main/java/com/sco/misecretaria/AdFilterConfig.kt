package com.sco.misecretaria

import android.content.Context
import java.util.Locale

data class BlockedPhrase(val wallet: String, val phrase: String)

/** Frases marcadas manualmente como publicidad desde el Historial, por billetera/app. */
object AdFilterConfig {
    private const val PREFS = "ad_filter_v1"
    private const val KEY = "phrases"

    fun list(context: Context): List<BlockedPhrase> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("|", limit = 2)
            if (parts.size == 2 && parts[1].isNotBlank()) BlockedPhrase(parts[0], parts[1]) else null
        }
    }

    fun add(context: Context, wallet: String, phrase: String) {
        val clean = phrase.trim()
        if (clean.isBlank()) return
        val current = list(context)
        if (current.any { it.wallet.equals(wallet, true) && it.phrase.equals(clean, true) }) return
        save(context, current + BlockedPhrase(wallet, clean))
    }

    fun remove(context: Context, wallet: String, phrase: String) {
        save(context, list(context).filterNot { it.wallet.equals(wallet, true) && it.phrase.equals(phrase, true) })
    }

    fun isBlocked(context: Context, wallet: String, message: String): Boolean {
        val lowerMsg = message.lowercase(Locale.ROOT)
        return list(context).any { it.wallet.equals(wallet, true) && lowerMsg.contains(it.phrase.lowercase(Locale.ROOT)) }
    }

    private fun save(context: Context, values: List<BlockedPhrase>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, values.joinToString("\n") { "${it.wallet}|${it.phrase}" }).apply()
    }
}
