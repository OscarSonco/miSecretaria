package com.sco.misecretaria

/**
 * Todo el texto y los nombres de comando del bot de Telegram, en un solo lugar. Cambiar el
 * nombre de un comando (ej. CMD_RENAME) o cualquier frase de aquí actualiza a la vez la
 * detección del comando y el texto de ayuda que lo describe — no hay que tocar
 * `TelegramCommandHandler.kt` para un cambio puramente de texto/nombre de comando.
 */
object BotTexts {
    const val CMD_NOTIFY = "notificar"
    const val CMD_NOTIFY_SCREEN = "notificarpantalla"
    const val CMD_RENAME = "renombrar"

    // v2.47: estos dos (y /help, /start) ya NO los procesa la app en absoluto — los escucha
    // exclusivamente `csv_importer.py` en la PC del administrador (ver ese script, incluido
    // el texto de ayuda equivalente a lo que era `help()` aquí, ya eliminada). No hay una
    // sola fuente de verdad entre Kotlin y Python para estos nombres — si se renombran allá,
    // hay que renombrarlos también aquí a mano.
    const val CMD_PANEL_ON = "panelon"
    const val CMD_PANEL_OFF = "paneloff"

    const val REMOTE_ALERT_WALLET = "Aviso remoto"

    fun notifyUsageError() =
        "⚠️ Formato incorrecto. Todo en UN SOLO mensaje:\n" +
            "/$CMD_NOTIFY TODOS <mensaje>\n/$CMD_NOTIFY <sucursal> <mensaje>\n\n" +
            "Ejemplo: /$CMD_NOTIFY TODOS Hola a todos"

    fun notifyScreenUsageError() =
        "⚠️ Formato incorrecto. Todo en UN SOLO mensaje:\n" +
            "/$CMD_NOTIFY_SCREEN TODOS <mensaje>\n/$CMD_NOTIFY_SCREEN <sucursal> <mensaje>\n\n" +
            "Ejemplo: /$CMD_NOTIFY_SCREEN TODOS Hola a todos"

    fun renameUsageError(deviceLabel: String) =
        "⚠️ Formato incorrecto. Todo en UN SOLO mensaje:\n" +
            "/$CMD_RENAME <código_actual> <nombre_nuevo>\n\n" +
            "Ejemplo: /$CMD_RENAME $deviceLabel Sucursal Centro"

    fun renamed(oldName: String, newName: String) = "✅ '$oldName' ahora se llama '$newName'"
}
