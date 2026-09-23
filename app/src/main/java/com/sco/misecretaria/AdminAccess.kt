package com.sco.misecretaria

/**
 * PIN de administrador (triple-tap al logo en Home) para ver/editar el token y Chat ID de
 * Telegram — pensado para que el personal de una sucursal no pueda verlos ni cambiarlos.
 */
object AdminAccess {
    private const val DEFAULT_PIN = "230985"

    fun verify(pin: String): Boolean = pin == DEFAULT_PIN
}
