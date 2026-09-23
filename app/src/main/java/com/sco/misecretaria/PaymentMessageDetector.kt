package com.sco.misecretaria

import java.util.Locale

/**
 * Distingue una notificación de pago real de publicidad/promociones que mandan las
 * billeteras (que a veces también mencionan un monto en Bs, como en "Gana Bs 5 con la Yapa").
 * Heurística: debe tener un monto EN COMBINACIÓN con una raíz de palabra típica de
 * confirmación de pago. Se usan raíces (no la palabra exacta) para cubrir conjugaciones:
 * "recib" -> recibiste/recibido/recibió; "envi" -> envió/enviado/envía/te ha enviado, etc.
 */
object PaymentMessageDetector {
    private val PAYMENT_KEYWORDS = listOf("recib", "envi", "yape", "transfer", "deposit", "pagaste", "pagado")
    private val AMOUNT_REGEX = Regex("(?i)(?:Bs\\.?\\s*[0-9][0-9.,]*|[0-9][0-9.,]*\\s*Bs\\.?)")

    fun looksLikePayment(message: String): Boolean {
        val lower = message.lowercase(Locale.ROOT)
        val hasAmount = AMOUNT_REGEX.containsMatchIn(message)
        val hasKeyword = PAYMENT_KEYWORDS.any { lower.contains(it) }
        return hasAmount && hasKeyword
    }
}
