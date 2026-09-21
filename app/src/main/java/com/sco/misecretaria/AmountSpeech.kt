package com.sco.misecretaria

object AmountSpeech {
    // Bs3.29 | Bs1.2 | Bs. 1.20 | Bs 10 | Bs0.4
    private val AMOUNT_REGEX = Regex("(?i)Bs\\.?\\s*([0-9]+)(?:[.,]([0-9]{1,2}))?")
    // "...de ARACELI OLIVERA CARRILLO en la cuenta..."
    private val NAME_REGEX_ACCOUNT = Regex("(?i)\\bde\\s+(.+?)\\s+en la cuenta")
    // "QR DE SONCO CHOQUE OSCAR ORLANDO te envió..."
    private val NAME_REGEX_QR = Regex("(?i)QR DE\\s+(.+?)\\s+te envi[oó]")

    fun buildSpeechText(wallet: String, message: String): String {
        val amountPhrase = amountPhrase(message)
        val name = senderName(message)
        return buildString {
            append(wallet); append(", Recibiste ")
            append(amountPhrase ?: "un pago")
            if (!name.isNullOrBlank()) { append(" de "); append(name.trim()) }
        }
    }

    private fun senderName(message: String): String? =
        NAME_REGEX_ACCOUNT.find(message)?.groupValues?.get(1)
            ?: NAME_REGEX_QR.find(message)?.groupValues?.get(1)

    private fun amountPhrase(message: String): String? {
        val m = AMOUNT_REGEX.find(message) ?: return null
        val bolivianos = m.groupValues[1].toIntOrNull() ?: 0
        val fracRaw = m.groupValues[2]
        // Un solo dígito decimal representa decenas de centavo: "1.2" -> 20 centavos, no 2.
        val centavos = if (fracRaw.isNotEmpty()) fracRaw.padEnd(2, '0').toInt() else 0
        return phrase(bolivianos, centavos)
    }

    private fun phrase(bolivianos: Int, centavos: Int): String {
        // "Un/Uno": el numeral "1" leído por TTS suena "uno"; para que concuerde en género
        // con "Boliviano"/"centavo" se usa la palabra "Un" en vez del dígito.
        val bsPart = when {
            bolivianos <= 0 -> null
            bolivianos == 1 -> "Un Boliviano"
            else -> "$bolivianos Bolivianos"
        }
        val ctPart = when {
            centavos <= 0 -> null
            centavos == 1 -> "Un centavo"
            else -> "$centavos centavos"
        }
        return when {
            bsPart != null && ctPart != null -> "$bsPart con $ctPart"
            bsPart != null -> bsPart
            ctPart != null -> ctPart
            else -> "un pago"
        }
    }
}
