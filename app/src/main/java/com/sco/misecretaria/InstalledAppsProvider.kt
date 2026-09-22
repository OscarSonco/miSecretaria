package com.sco.misecretaria

import android.content.Context
import android.content.Intent

data class InstalledAppInfo(val label: String, val packageName: String)

/** Lista las apps instaladas con ícono de lanzador (excluye la propia app y duplicados). */
object InstalledAppsProvider {
    fun list(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map { InstalledAppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .sortedBy { it.label.lowercase() }
    }
}
