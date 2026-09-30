package com.sco.misecretaria

import android.content.Context
import java.util.Locale

data class AppRule(
    val name: String,
    val packageId: String,
    val enabled: Boolean,
    /** v2.41: silencia la lectura en voz alta de esta app — el resto (historial, notificación
     * del sistema, adjuntos) sigue funcionando normal. */
    val speechMuted: Boolean = false,
    /** v2.41: si es falso, la lectura en voz alta omite el nombre de la app al principio. */
    val sayName: Boolean = true,
    /** v2.41: si es verdadero, no se anuncian en voz alta las notificaciones de LLAMADA de esta
     * app (`Notification.CATEGORY_CALL` — ej. llamadas de WhatsApp). El resto de notificaciones
     * (mensajes normales) de la misma app se siguen leyendo igual. */
    val callsMuted: Boolean = false,
    /** v2.41: modo "solo título" — pensado para apps de navegación (ej. Google Maps) cuyo
     * título ya es la distancia ("90 m") y cuyo texto es el destino/dirección, que no aporta
     * nada al leerse en voz alta a cada paso. Si es verdadero, se lee SOLO el título (con la
     * unidad expandida a palabra, ver `NotificationSpeech.distanceOnly`), sin nombre de app ni
     * texto del cuerpo. */
    val titleOnly: Boolean = false,
)

/** Aplicaciones "generales" (no billeteras): WhatsApp, Facebook Messenger, SMS, etc. */
object AppConfig {
    private const val PREFS = "app_config_v1"
    private const val KEY = "rules"

    fun rules(context: Context): List<AppRule> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        val lines = if (raw.contains("\\n")) raw.split("\\n") else raw.split("\n")
        return lines.mapNotNull { line ->
            // v2.41: formato extendido a 7 campos; registros guardados ANTES de v2.41 solo
            // tienen 3 — se leen igual, con los cuatro nuevos en su default.
            val parts = line.split("|", limit = 7)
            if (parts.size < 3) return@mapNotNull null
            AppRule(
                name = parts[0],
                packageId = parts[1],
                enabled = parts[2] == "1",
                speechMuted = parts.getOrNull(3) == "1",
                sayName = parts.getOrNull(4)?.let { it == "1" } ?: true,
                callsMuted = parts.getOrNull(5) == "1",
                titleOnly = parts.getOrNull(6) == "1",
            )
        }
    }

    fun setEnabled(context: Context, name: String, enabled: Boolean) = save(context, rules(context).map { if (it.name == name) it.copy(enabled = enabled) else it })

    fun setSpeechMuted(context: Context, name: String, muted: Boolean) = save(context, rules(context).map { if (it.name == name) it.copy(speechMuted = muted) else it })

    fun setSayName(context: Context, name: String, sayName: Boolean) = save(context, rules(context).map { if (it.name == name) it.copy(sayName = sayName) else it })

    fun setCallsMuted(context: Context, name: String, muted: Boolean) = save(context, rules(context).map { if (it.name == name) it.copy(callsMuted = muted) else it })

    fun setTitleOnly(context: Context, name: String, titleOnly: Boolean) = save(context, rules(context).map { if (it.name == name) it.copy(titleOnly = titleOnly) else it })

    fun remove(context: Context, name: String) = save(context, rules(context).filterNot { it.name.equals(name, true) })

    fun add(context: Context, name: String, packageId: String) {
        if (name.isBlank()) return
        val current = rules(context).filterNot { it.name.equals(name.trim(), true) }
        save(context, current + AppRule(name.trim(), packageId.trim(), true))
    }

    /** v2.48: agrega o actualiza UNA regla por nombre (si existe, la reemplaza entera; si no,
     * la agrega) — usado por `WalletAppSync` para sincronizar apps desde el archivo maestro
     * del admin (`miSecretaria_BilleterasAplicacion.txt`) SIN afectar ninguna otra regla que
     * ya exista localmente y no esté mencionada ahí. */
    fun upsert(context: Context, rule: AppRule) {
        val current = rules(context)
        val exists = current.any { it.name.equals(rule.name, true) }
        save(context, if (exists) current.map { if (it.name.equals(rule.name, true)) rule else it } else current + rule)
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
            .putString(KEY, values.joinToString("\n") {
                "${it.name}|${it.packageId}|${if (it.enabled) "1" else "0"}|${if (it.speechMuted) "1" else "0"}|${if (it.sayName) "1" else "0"}|${if (it.callsMuted) "1" else "0"}|${if (it.titleOnly) "1" else "0"}"
            }).apply()
    }
}
