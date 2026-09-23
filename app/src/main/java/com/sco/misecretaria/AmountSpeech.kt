package com.sco.misecretaria

object AmountSpeech {
    // Bs3.29 | Bs1.2 | Bs. 1.20 | Bs 10 | Bs0.4 | Bs 2,392.69 | 0,8 Bs (orden invertido)
    private val AMOUNT_REGEX = Regex("(?i)(?:Bs\\.?\\s*([0-9][0-9.,]*)|([0-9][0-9.,]*)\\s*Bs\\.?)")
    // "...de ARACELI OLIVERA CARRILLO en la cuenta..."
    private val NAME_REGEX_ACCOUNT = Regex("(?i)\\bde\\s+(.+?)\\s+en la cuenta")
    // "QR DE SONCO CHOQUE OSCAR ORLANDO te envió..."
    private val NAME_REGEX_QR = Regex("(?i)QR DE\\s+(.+?)\\s+te envi[oó]")
    // "SONCO CHOQUE OSCAR ORLANDO te ha enviado 0,8 Bs" (estilo YASTA/Bille)
    private val NAME_REGEX_TE_HA_ENVIADO = Regex("(?i)^(.+?)\\s+te ha enviado")

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
            ?: NAME_REGEX_TE_HA_ENVIADO.find(message)?.groupValues?.get(1)

    private fun amountPhrase(message: String): String? {
        val m = AMOUNT_REGEX.find(message) ?: return null
        val raw = m.groupValues[1].ifBlank { m.groupValues[2] }
        val (bolivianos, centavos) = parseAmount(raw)
        return phrase(bolivianos, centavos)
    }

    /**
     * Parsea un monto que puede traer separador de miles (coma o punto) y/o decimales.
     * Regla: el ÚLTIMO separador se considera decimal SOLO si tiene 1-2 dígitos después;
     * cualquier separador anterior (o uno con 3+ dígitos después) se trata como miles.
     * Un solo dígito decimal representa decenas de centavo: "1.2" -> 20 centavos, no 2.
     * Ej: "2,392.69" -> 2392 con 69 centavos (no "2 con 39").
     */
    private fun parseAmount(rawInput: String): Pair<Int, Int> {
        val raw = rawInput.trim().trim('.', ',')
        val lastDot = raw.lastIndexOf('.')
        val lastComma = raw.lastIndexOf(',')
        val decimalSepIndex = maxOf(lastDot, lastComma)
        if (decimalSepIndex == -1) {
            return (raw.replace(Regex("[.,]"), "").toIntOrNull() ?: 0) to 0
        }
        val fracPart = raw.substring(decimalSepIndex + 1)
        if (fracPart.length !in 1..2) {
            // No es un decimal válido (0 o 3+ dígitos): todo era separador de miles.
            return (raw.replace(Regex("[.,]"), "").toIntOrNull() ?: 0) to 0
        }
        val bolivianos = raw.substring(0, decimalSepIndex).replace(Regex("[.,]"), "").toIntOrNull() ?: 0
        val centavos = fracPart.padEnd(2, '0').toIntOrNull() ?: 0
        return bolivianos to centavos
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
