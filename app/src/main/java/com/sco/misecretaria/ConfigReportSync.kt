package com.sco.misecretaria

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * v2.49 (etapa 2 de "Billeteras/Aplicaciones centralizadas"): manda un reporte periódico de
 * la configuración ACTUAL de Billeteras/Aplicaciones de este teléfono al chat de Telegram —
 * mismo mecanismo que ya usan el CSV y los medios (`sendDocument`, Telegram Desktop lo
 * descarga solo, `csv_importer.py` lo recoge). Pedido explícito del usuario: quería que
 * `/listado` pudiera mostrar qué billeteras/apps tiene cada sucursal, para detectar a tiempo
 * si un empleado nuevo configuró algo mal (antes de perder facturas/reportes/balances).
 *
 * Solo se manda cuando la configuración REALMENTE cambió desde el último envío (comparando
 * un hash guardado localmente) — evita acumular archivos idénticos en el chat/carpeta de
 * descargas en cada ciclo si nada cambió.
 */
object ConfigReportSync {
    private const val PREFS = "config_report_v1"
    private const val KEY_LAST_HASH = "last_hash"

    fun sendIfChanged(context: Context, token: String, chatId: String) {
        val deviceLabel = DisplayPreferences.deviceLabel(context)
        val json = buildConfigJson(context, deviceLabel)
        val texto = json.toString()
        val hash = texto.hashCode().toString()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_HASH, null) == hash) return
        val fileName = "${deviceLabel}_config_${WalletNotificationStore.timestampForFile()}.json"
        val sent = TelegramClient.sendDocument(token, chatId, fileName, texto.toByteArray(Charsets.UTF_8), "⚙️ Configuración actual de $deviceLabel", mimeType = "application/json")
        if (sent) {
            prefs.edit().putString(KEY_LAST_HASH, hash).apply()
            ScoSecretariaLogger.info(context, "Reporte de configuración enviado a Telegram ($fileName)")
        }
    }

    private fun buildConfigJson(context: Context, deviceLabel: String): JSONObject {
        val obj = JSONObject()
        obj.put("sucursal", deviceLabel)
        obj.put("generado_en", WalletNotificationStore.now())
        val billeteras = JSONArray()
        WalletConfig.rules(context).forEach { r ->
            billeteras.put(JSONObject().apply {
                put("nombre", r.name)
                put("packageId", r.packageId)
                put("enabled", r.enabled)
                put("speechMuted", r.speechMuted)
                put("sayName", r.sayName)
            })
        }
        obj.put("billeteras", billeteras)
        val apps = JSONArray()
        AppConfig.rules(context).forEach { r ->
            apps.put(JSONObject().apply {
                put("nombre", r.name)
                put("packageId", r.packageId)
                put("enabled", r.enabled)
                put("speechMuted", r.speechMuted)
                put("sayName", r.sayName)
                put("callsMuted", r.callsMuted)
                put("titleOnly", r.titleOnly)
            })
        }
        obj.put("apps", apps)
        return obj
    }
}
