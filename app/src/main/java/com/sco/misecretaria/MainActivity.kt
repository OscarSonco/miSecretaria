package com.sco.misecretaria

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.sco.misecretaria.ui.theme.ScoSecretariaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val REPORT_MIME = "text/plain"
enum class NotificationKind { PAYMENT, GENERAL }
data class WalletNotification(val id: String, val wallet: String, val title: String, val message: String, val receivedAt: String, val kind: NotificationKind = NotificationKind.PAYMENT)

class MainActivity : ComponentActivity() {
    companion object { private const val REQ_SAVE = 100; private const val REQ_RESTORE = 101 }
    private var pendingSaveText: String = ""

    override fun onCreate(state: Bundle?) { super.onCreate(state); WalletNotificationStore.init(applicationContext); render() }
    override fun onResume() { super.onResume(); WalletNotificationListener.requestServiceRebind(this) }
    private fun render() { setContent { ScoSecretariaTheme { ScoSecretariaApp(this) } } }
    fun share(name: String, text: String, mime: String = REPORT_MIME) = startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_SUBJECT, name); putExtra(Intent.EXTRA_TEXT, text); clipData = ClipData.newPlainText(name, text) }, "Compartir $name"))
    fun save(name: String, content: String, mime: String = REPORT_MIME) {
        pendingSaveText = content
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = mime; putExtra(Intent.EXTRA_TITLE, name) }, REQ_SAVE)
    }
    fun restoreBackup() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, REQ_RESTORE)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        when (requestCode) {
            REQ_SAVE -> runCatching { contentResolver.openOutputStream(data.data!!)?.bufferedWriter()?.use { it.write(pendingSaveText) } }
            REQ_RESTORE -> runCatching {
                val text = contentResolver.openInputStream(data.data!!)?.bufferedReader()?.use { it.readText() } ?: return
                BackupManager.importJson(this, text)
                android.widget.Toast.makeText(this, "Backup restaurado", android.widget.Toast.LENGTH_LONG).show()
                recreate()
            }.onFailure {
                ScoSecretariaLogger.error(this, "Fallo al restaurar backup", it)
                android.widget.Toast.makeText(this, "No se pudo restaurar el backup", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    fun shareApk() {
        runCatching {
            val sourceApk = File(applicationInfo.sourceDir)
            val outDir = getExternalFilesDir(null) ?: filesDir
            val outFile = File(outDir, "${AppInfo.NAME}V${AppInfo.VERSION}.apk")
            sourceApk.copyTo(outFile, overwrite = true)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", outFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Compartir ${AppInfo.NAME}"))
        }.onFailure { ScoSecretariaLogger.error(this, "No se pudo compartir el APK", it) }
    }
}

private enum class Screen { HOME, SETTINGS, READ }

@Composable private fun ScoSecretariaApp(activity: MainActivity) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    when (screen) {
        Screen.HOME -> HomeScreen(openSettings = { screen = Screen.SETTINGS }, openRead = { screen = Screen.READ })
        Screen.SETTINGS -> SettingsScreen({ screen = Screen.HOME }, activity)
        Screen.READ -> ReadScreen({ screen = Screen.HOME })
    }
}

@Composable private fun HomeScreen(openSettings: () -> Unit, openRead: () -> Unit) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(WalletNotificationStore.history()) }
    var serviceOn by remember { mutableStateOf(DisplayPreferences.serviceEnabled(context)) }
    var filter by remember { mutableStateOf<String?>(null) }
    var serviceAlive by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        WalletNotificationListener.requestServiceRebind(context)
        while (true) {
            history = WalletNotificationStore.history()
            val hb = DisplayPreferences.heartbeat(context)
            serviceAlive = hb != 0L && (System.currentTimeMillis() - hb) < 6 * 60 * 60 * 1000L
            delay(700)
        }
    }
    val sources = remember(history) { history.map { it.wallet }.distinct().sorted() }
    val filtered = remember(history, filter) { if (filter == null) history else history.filter { it.wallet == filter } }
    Scaffold { p -> Column(Modifier.padding(p).padding(16.dp).fillMaxSize()) {
        Text(AppInfo.DISPLAY, style = MaterialTheme.typography.headlineSmall)
        Text("Asistente de notificaciones de billeteras móviles")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = openSettings) { Text("Configuración") }
            Button(onClick = openRead) { Text("Leer") }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { serviceOn = !serviceOn; DisplayPreferences.setServiceEnabled(context, serviceOn) }, colors = ButtonDefaults.buttonColors(containerColor = if (serviceOn) Color(0xFF188038) else Color(0xFFB00020))) { Text(if (serviceOn) "${AppInfo.NAME}: Activado" else "${AppInfo.NAME}: Desactivado") }
        Text(if (serviceAlive) "Servicio: escuchando ✓" else "Servicio: sin actividad reciente ⚠", color = if (serviceAlive) Color(0xFF188038) else Color(0xFFB00020), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Text("Historial (${filtered.size}${if (filter != null) " de ${history.size}" else ""})", style = MaterialTheme.typography.titleMedium)
        if (sources.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Todas") })
                sources.forEach { s -> FilterChip(selected = filter == s, onClick = { filter = s }, label = { Text(s) }) }
            }
            Spacer(Modifier.height(6.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(filtered, key = { it.id }) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("${it.wallet} · ${it.receivedAt}"); Text(it.message) } } } }
    } }
}

@Composable private fun ReadScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    Scaffold { p -> Column(Modifier.padding(p).padding(16.dp).fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { TextButton(onClick = onBack) { Text("Volver") }; Text("Leer", style = MaterialTheme.typography.headlineSmall) }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Pega o escribe el texto a leer") }, modifier = Modifier.fillMaxWidth().weight(1f))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { SpeechEngine.speakText(context, text) }, enabled = text.isNotBlank()) { Text("Leer en voz alta") }
            OutlinedButton(onClick = { SpeechEngine.stop() }) { Text("Detener") }
        }
    } }
}

@Composable private fun SettingsScreen(onBack: () -> Unit, activity: MainActivity) {
    val context = LocalContext.current
    var rules by remember { mutableStateOf(WalletConfig.rules(context)) }
    var appRules by remember { mutableStateOf(AppConfig.rules(context)) }
    var fullScreen by remember { mutableStateOf(DisplayPreferences.fullScreenEnabled(context)) }
    var speech by remember { mutableStateOf(DisplayPreferences.speechEnabled(context)) }
    var alert by remember { mutableStateOf(DisplayPreferences.alertEnabled(context)) }
    var buttons by remember { mutableStateOf(DisplayPreferences.buttons(context)) }
    var voiceProfile by remember { mutableStateOf(DisplayPreferences.voiceProfile(context)) }
    var speechRate by remember { mutableStateOf(DisplayPreferences.speechRateMultiplier(context)) }
    var walletName by remember { mutableStateOf("") }; var walletPkg by remember { mutableStateOf("") }
    var appName by remember { mutableStateOf("") }; var appPkg by remember { mutableStateOf("") }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateChecked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold { p -> Column(Modifier.padding(p).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { TextButton(onClick = onBack) { Text("Volver") }; Text("Configuración", style = MaterialTheme.typography.headlineSmall) }
        PermissionRow("Acceso a notificaciones", isNotificationAccessEnabled(context)) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        PermissionRow("Mostrar sobre otras aplicaciones", Settings.canDrawOverlays(context)) { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { data = android.net.Uri.parse("package:${context.packageName}") }) }
        Spacer(Modifier.height(10.dp))
        Text("Versión instalada: ${AppInfo.VERSION}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                checkingUpdate = true; updateChecked = false
                scope.launch { updateInfo = UpdateManager.checkForUpdate(); checkingUpdate = false; updateChecked = true }
            }, enabled = !checkingUpdate) { Text(if (checkingUpdate) "Buscando..." else "Buscar actualización") }
        }
        if (updateChecked) {
            val info = updateInfo
            if (info == null) {
                Text("Estás en la última versión.", style = MaterialTheme.typography.bodySmall)
            } else {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text("Nueva versión disponible: ${info.versionName}", style = MaterialTheme.typography.titleMedium)
                    if (info.notes.isNotBlank()) Text(info.notes, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (!UpdateManager.canInstallPackages(context)) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}")))
                        } else {
                            UpdateManager.downloadAndInstall(context, info)
                        }
                    }) { Text("Actualizar ahora") }
                } }
            }
        }
        Spacer(Modifier.height(10.dp)); SettingSwitch("Notificación hablada", speech) { speech = it; DisplayPreferences.setSpeechEnabled(context, it) }
        SettingSwitch("Pantalla de aviso", alert) { alert = it; DisplayPreferences.setAlertEnabled(context, it) }
        SettingSwitch("Pantalla completa", fullScreen) { fullScreen = it; DisplayPreferences.setFullScreenEnabled(context, it) }
        if (alert) {
            Text("Botones del aviso", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = buttons == AlertButtons.OK_ONLY, onClick = { buttons = AlertButtons.OK_ONLY; DisplayPreferences.setButtons(context, buttons) }, label = { Text("Solo OK") }); FilterChip(selected = buttons == AlertButtons.REPEAT_AND_OK, onClick = { buttons = AlertButtons.REPEAT_AND_OK; DisplayPreferences.setButtons(context, buttons) }, label = { Text("Repetir y OK") }) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Tipo de voz", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoiceProfile.entries.forEach { profile ->
                FilterChip(selected = voiceProfile == profile, onClick = { voiceProfile = profile; DisplayPreferences.setVoiceProfile(context, profile) }, label = { Text(profile.label) })
            }
        }
        Text("Velocidad de lectura: ${"%.1f".format(speechRate)}x", style = MaterialTheme.typography.titleMedium)
        Slider(value = speechRate, onValueChange = { speechRate = it; DisplayPreferences.setSpeechRateMultiplier(context, it) }, valueRange = 0.5f..2.0f, steps = 14)
        TextButton(onClick = { runCatching { context.startActivity(Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) } }) { Text("Instalar más voces") }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextButton(onClick = { activity.save("${AppInfo.REPORT_BASENAME}_${WalletNotificationStore.timestampForFile()}.txt", WalletNotificationStore.exportText()) }) { Text("Guardar Historial") }; TextButton(onClick = { activity.share("${AppInfo.REPORT_BASENAME}.txt", WalletNotificationStore.exportText()) }) { Text("Compartir Historial") } }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextButton(onClick = { activity.save("${AppInfo.REPORT_BASENAME}_${WalletNotificationStore.timestampForFile()}.csv", WalletNotificationStore.exportCsv(), "text/csv") }) { Text("Guardar CSV") }; TextButton(onClick = { activity.share("${AppInfo.REPORT_BASENAME}.csv", WalletNotificationStore.exportCsv(), "text/csv") }) { Text("Compartir CSV") } }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextButton(onClick = { activity.save(AppInfo.LOG_EXPORT_NAME, ScoSecretariaLogger.read(context)) }) { Text("Guardar Log") }; TextButton(onClick = { activity.share(AppInfo.LOG_EXPORT_NAME, ScoSecretariaLogger.read(context)) }) { Text("Compartir Log") } }
        Button(onClick = { activity.shareApk() }) { Text("Compartir Aplicación") }
        Spacer(Modifier.height(12.dp))
        Text("Copia de seguridad (config., billeteras, apps)", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { activity.save("${AppInfo.NAME}_backup_${WalletNotificationStore.timestampForFile()}.json", BackupManager.exportJson(context), "application/json") }) { Text("Guardar Backup") }
            TextButton(onClick = { activity.restoreBackup() }) { Text("Restaurar Backup") }
        }
        Text("Billeteras autorizadas", style = MaterialTheme.typography.titleMedium)
        rules.forEach { r -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(r.name); Switch(checked = r.enabled, onCheckedChange = { WalletConfig.setEnabled(context, r.name, it); rules = WalletConfig.rules(context) }) } }
        OutlinedTextField(walletName, { walletName = it }, label = { Text("Nombre de nueva billetera") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(walletPkg, { walletPkg = it }, label = { Text("Paquete Android (opcional)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { WalletConfig.add(context, walletName, walletPkg); rules = WalletConfig.rules(context); walletName = ""; walletPkg = "" }) { Text("Agregar billetera") }
        Spacer(Modifier.height(20.dp))
        Text("Aplicaciones (General)", style = MaterialTheme.typography.titleMedium)
        Text("WhatsApp, Messenger, SMS u otras: lee el título de la app + el mensaje completo.", style = MaterialTheme.typography.bodySmall)
        appRules.forEach { r -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(r.name); Switch(checked = r.enabled, onCheckedChange = { AppConfig.setEnabled(context, r.name, it); appRules = AppConfig.rules(context) }) } }
        OutlinedTextField(appName, { appName = it }, label = { Text("Nombre de la aplicación") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(appPkg, { appPkg = it }, label = { Text("Paquete Android (opcional)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { AppConfig.add(context, appName, appPkg); appRules = AppConfig.rules(context); appName = ""; appPkg = "" }) { Text("Agregar aplicación") }
        Spacer(Modifier.height(24.dp))
    } }
}

@Composable private fun SettingSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = if (value) Color(0xFF188038) else Color.Red); Switch(checked = value, onCheckedChange = onChange) } }
@Composable private fun PermissionRow(label: String, enabled: Boolean, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = if (enabled) Color(0xFF188038) else Color.Red); TextButton(onClick = onClick) { Text(if (enabled) "✓" else "Habilitar") } } }
private fun isNotificationAccessEnabled(c: Context): Boolean { val e = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false; val component = ComponentName(c, WalletNotificationListener::class.java).flattenToString(); return e.split(':').any { it.equals(component, true) } }
