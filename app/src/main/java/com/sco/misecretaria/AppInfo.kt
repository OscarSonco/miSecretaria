package com.sco.misecretaria

/**
 * Punto único de nombre/versión de la app. Usa BuildConfig (generado a partir de
 * versionName/versionCode en build.gradle.kts) para que los nombres de archivo exportados
 * y los textos en pantalla siempre coincidan con la versión real compilada, sin tener que
 * tocar estos literales a mano en cada bump de versión.
 */
object AppInfo {
    const val NAME = "miSecretaria"
    val VERSION: String get() = BuildConfig.VERSION_NAME
    val VERSION_CODE: Int get() = BuildConfig.VERSION_CODE
    val DISPLAY: String get() = "$NAME V$VERSION"
    val REPORT_BASENAME: String get() = "${NAME}_ReporteV$VERSION"
    val LOG_EXPORT_NAME: String get() = "${NAME}V$VERSION.log"
}
