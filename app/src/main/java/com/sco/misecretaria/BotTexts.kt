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
    const val CMD_HELP = "help"
    const val CMD_START = "start"

    // Estos dos NO los procesa la app — los escucha `csv_importer.py` en la PC del
    // administrador (ver ese script). Se listan aquí SOLO para que aparezcan en el /help;
    // si se renombran allá, hay que renombrarlos también aquí a mano (no hay una sola fuente
    // de verdad entre Kotlin y Python para estos dos).
    const val CMD_PANEL_ON = "panelon"
    const val CMD_PANEL_OFF = "paneloff"

    const val REMOTE_ALERT_WALLET = "Aviso remoto"

    fun help(deviceLabel: String) =
        "🤖 miSecretaria — comandos del bot:\n" +
            "⚠️ Escribe todo en UN SOLO mensaje, no en varios seguidos.\n\n" +
            "/$CMD_NOTIFY TODOS <mensaje>\nSolo AUDIO — lee el mensaje en voz alta, sin nada en pantalla.\n" +
            "Ejemplo: /$CMD_NOTIFY TODOS Cerramos a las 8pm hoy\n\n" +
            "/$CMD_NOTIFY_SCREEN TODOS <mensaje>\nAUDIO + PANTALLA — además lo muestra en pantalla completa o aviso flotante.\n" +
            "Ejemplo: /$CMD_NOTIFY_SCREEN TODOS Vino el proveedor, revisen\n\n" +
            "Con ambos puedes usar el nombre/código de una sucursal en vez de TODOS, para avisarle solo a esa.\n" +
            "Ejemplo: /$CMD_NOTIFY $deviceLabel Reunión a las 3pm\n\n" +
            "/$CMD_RENAME <código_actual> <nombre_nuevo>\nCambia el nombre de una sucursal (código/nombre debe coincidir exacto).\n" +
            "Ejemplo: /$CMD_RENAME $deviceLabel Sucursal Centro\n\n" +
            "/$CMD_PANEL_ON\nEnciende el panel web (miSecretaria.html) para ver la base de datos — solo funciona si el administrador tiene csv_importer.py corriendo en su PC.\n" +
            "/$CMD_PANEL_OFF\nApaga ese panel web.\n\n" +
            "/$CMD_HELP\nMuestra esta ayuda.\n\n" +
            "Esta sucursal se llama: $deviceLabel"

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
