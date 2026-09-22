package com.sco.misecretaria

import java.util.Locale

/**
 * Distingue una notificación de pago real de publicidad/promociones que mandan las
 * billeteras (que a veces también mencionan un monto en Bs, como en "Gana Bs 5 con la Yapa").
 * Heurística: debe tener un monto EN COMBINACIÓN con una frase típica de confirmación de pago.
 */
object PaymentMessageDetector {
    private val PAYMENT_KEYWORDS = listOf("recibiste", "envió", "envio", "pago recibido", "yapeo")
    private val AMOUNT_REGEX = Regex("(?i)Bs\\.?\\s*[0-9]+(?:[.,][0-9]{1,2})?")

    fun looksLikePayment(message: String): Boolean {
        val lower = message.lowercase(Locale.ROOT)
        val hasAmount = AMOUNT_REGEX.containsMatchIn(message)
        val hasKeyword = PAYMENT_KEYWORDS.any { lower.contains(it) }
        return hasAmount && hasKeyword
    }
}
