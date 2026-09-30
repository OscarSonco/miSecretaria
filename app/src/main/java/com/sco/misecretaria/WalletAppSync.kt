package com.sco.misecretaria

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sincroniza Billeteras/Aplicaciones desde un archivo público en Firebase Hosting — mismo
 * patrón que `AdFilterSync` usa para "Publicidad bloqueada", pero con una diferencia
 * DELIBERADA e importante: esto APLICA cambios puntuales (agregar/quitar/modificar UNA regla
 * por nombre), no REEMPLAZA la lista completa. Si se reemplazara entera como la publicidad,
 * cualquier billetera/app que una sucursal haya agregado por su cuenta y no esté en el
 * archivo maestro del admin se borraría en el próximo ciclo — un riesgo real de romper la
 * detección de pagos en esa sucursal. Con "aplicar cambios puntuales", una regla que el
 * archivo maestro no menciona simplemente queda intacta.
 *
 * También respeta los campos que el admin deja SIN especificar en una entrada: si una entrada
 * para una billetera/app que YA existe localmente omite, por ejemplo, `sayName`, el valor
 * local existente se conserva tal cual (no se pisa con un default) — así el admin puede
 * sincronizar solo el campo que le interesa (ej. solo `enabled`) sin resetear los demás
 * ajustes que una sucursal ya haya personalizado.
 *
 * Pedido explícito del usuario: poder agregar una app nueva (ej. la del proveedor de
 * bebidas) o una billetera nueva a todas las sucursales de una vez, o ajustar
 * On/Off/voz/nombre/llamadas/modo, sin tener que entrar a cada teléfono uno por uno.
 *
 * v2.48 (etapa 1): cada entrada trae un `destino` (`"TODOS"` o una lista de nombres de
 * sucursal) — pedido explícito del usuario, porque no todos los empleados/sucursales deben
 * recibir el mismo cambio (ej. un Jefe puede necesitar billeteras que un cajero no). Cada
 * teléfono se filtra a sí mismo comparando `destino` contra su propio
 * `DisplayPreferences.deviceLabel()` — mismo criterio ya usado y probado en
 * `TelegramCommandHandler.targetMatches()` para `/notificar TODOS|<sucursal>`. El admin
 * consulta `/listado` (lo responde `csv_importer.py`, PC) para ver los nombres exactos de
 * sucursal que puede usar en `destino`.
 */
object WalletAppSync {
    private const val MANIFEST_URL = "https://misecretaria-67c62.web.app/billeteras_aplicaciones.json"
    private const val TIPO_BILLETERA = "billetera"
    private const val TIPO_APP = "app"
    private const val DESTINO_TODOS = "TODOS"

    suspend fun syncFromRemote(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000; requestMethod = "GET"
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val array = JSONArray(text)
            val deviceLabel = DisplayPreferences.deviceLabel(context)
            var aplicados = 0
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (!destinoIncluyeEsteDispositivo(obj, deviceLabel)) continue
                if (aplicarEntrada(context, obj)) aplicados++
            }
            if (aplicados > 0) ScoSecretariaLogger.info(context, "Billeteras/Apps: $aplicados regla(s) sincronizada(s) desde Firebase Hosting")
        }.onFailure {
            ScoSecretariaLogger.debug(context, "No se pudo sincronizar Billeteras/Apps (sin internet o aún no publicado)")
        }
    }

    /** `destino` ausente se trata como `TODOS` (respaldo seguro, para no ignorar en
     * silencio una entrada mal formada) — en la práctica `csv_importer.py` siempre lo manda. */
    private fun destinoIncluyeEsteDispositivo(obj: JSONObject, deviceLabel: String): Boolean {
        val destino = obj.optJSONArray("destino") ?: return true
        for (i in 0 until destino.length()) {
            val valor = destino.optString(i, "")
            if (valor.equals(DESTINO_TODOS, ignoreCase = true) || valor.equals(deviceLabel, ignoreCase = true)) return true
        }
        return false
    }

    private fun aplicarEntrada(context: Context, obj: JSONObject): Boolean {
        val tipo = obj.optString("tipo", "").trim().lowercase()
        val nombre = obj.optString("nombre", "").trim()
        if (nombre.isBlank() || (tipo != TIPO_BILLETERA && tipo != TIPO_APP)) return false
        val quitar = obj.optBoolean("quitar", false)
        if (tipo == TIPO_BILLETERA) {
            if (quitar) {
                WalletConfig.remove(context, nombre)
            } else {
                val existente = WalletConfig.rules(context).firstOrNull { it.name.equals(nombre, true) }
                WalletConfig.upsert(context, WalletRule(
                    name = nombre,
                    packageId = if (obj.has("packageId")) obj.optString("packageId", "") else existente?.packageId ?: "",
                    enabled = if (obj.has("enabled")) obj.optBoolean("enabled") else existente?.enabled ?: true,
                    speechMuted = if (obj.has("speechMuted")) obj.optBoolean("speechMuted") else existente?.speechMuted ?: false,
                    sayName = if (obj.has("sayName")) obj.optBoolean("sayName") else existente?.sayName ?: true,
                ))
            }
        } else {
            if (quitar) {
                AppConfig.remove(context, nombre)
            } else {
                val existente = AppConfig.rules(context).firstOrNull { it.name.equals(nombre, true) }
                AppConfig.upsert(context, AppRule(
                    name = nombre,
                    packageId = if (obj.has("packageId")) obj.optString("packageId", "") else existente?.packageId ?: "",
                    enabled = if (obj.has("enabled")) obj.optBoolean("enabled") else existente?.enabled ?: true,
                    speechMuted = if (obj.has("speechMuted")) obj.optBoolean("speechMuted") else existente?.speechMuted ?: false,
                    sayName = if (obj.has("sayName")) obj.optBoolean("sayName") else existente?.sayName ?: true,
                    callsMuted = if (obj.has("callsMuted")) obj.optBoolean("callsMuted") else existente?.callsMuted ?: false,
                    titleOnly = if (obj.has("titleOnly")) obj.optBoolean("titleOnly") else existente?.titleOnly ?: false,
                ))
            }
        }
        return true
    }
}
