package com.sco.misecretaria

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

/**
 * Actualización remota sin cuentas ni tiendas de apps: consulta un manifiesto JSON público
 * (hosteado en Firebase Hosting) y, si hay versión nueva, descarga e instala el APK.
 */
object UpdateManager {
    // Cambiar por la URL real una vez desplegado en Firebase Hosting.
    private const val MANIFEST_URL = "https://misecretaria-67c62.web.app/update.json"

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000; requestMethod = "GET"
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val json = JSONObject(text)
            UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = json.getString("apkUrl"),
                notes = json.optString("notes", "")
            )
        }.getOrNull()?.takeIf { it.versionCode > AppInfo.VERSION_CODE }
    }

    fun canInstallPackages(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun downloadAndInstall(context: Context, info: UpdateInfo) {
        val appContext = context.applicationContext
        val fileName = "${AppInfo.NAME}V${info.versionName}.apk"
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("Actualizando ${AppInfo.NAME}")
            .setDescription("Descargando versión ${info.versionName}")
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = manager.enqueue(request)
        pollAndInstall(appContext, manager, id, fileName)
    }

    private fun pollAndInstall(context: Context, manager: DownloadManager, id: Long, fileName: String) {
        Thread {
            var downloading = true
            while (downloading) {
                manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    if (cursor.moveToFirst()) {
                        when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                            DownloadManager.STATUS_SUCCESSFUL -> { downloading = false; installApk(context, fileName) }
                            DownloadManager.STATUS_FAILED -> { downloading = false; ScoSecretariaLogger.error(context, "Descarga de actualización falló") }
                        }
                    }
                }
                if (downloading) Thread.sleep(800)
            }
        }.start()
    }

    private fun installApk(context: Context, fileName: String) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
