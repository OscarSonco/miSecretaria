package com.sco.misecretaria

import android.Manifest
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.sco.misecretaria.ui.theme.AccentBlue
import com.sco.misecretaria.ui.theme.ScoSecretariaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val REPORT_MIME = "text/plain"
enum class NotificationKind { PAYMENT, GENERAL }
data class WalletNotification(val id: String, val wallet: String, val title: String, val message: String, val receivedAt: String, val kind: NotificationKind = NotificationKind.PAYMENT, val mediaPath: String? = null)

class MainActivity : ComponentActivity() {
    companion object { private const val REQ_SAVE = 100; private const val REQ_RESTORE = 101 }
    private var pendingSaveText: String = ""
    private val mediaPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    fun requestMediaPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        mediaPermissionLauncher.launch(perms)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        WalletNotificationStore.init(applicationContext)
        if (TelegramConfig.isConfigured(this)) TelegramSyncWorker.schedule(this)
        render()
    }
    override fun onResume() { super.onResume(); WalletNotificationListener.requestServiceRebind(this) }
    private fun render() { setContent { ScoSecretariaTheme { ScoSecretariaApp(this) } } }
    fun share(name: String, text: String, mime: String = REPORT_MIME) {
        runCatching {
            val file = File(cacheDir, name)
            file.writeText(text)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Compartir $name"))
        }.onFailure { ScoSecretariaLogger.error(this, "No se pudo compartir $name", it) }
    }
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

private enum class Screen { HOME, SETTINGS, READ, PICK_WALLET, PICK_APP }

@Composable private fun ScoSecretariaApp(activity: MainActivity) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var adminUnlocked by remember { mutableStateOf(false) }
    when (screen) {
        Screen.HOME -> HomeScreen(openSettings = { screen = Screen.SETTINGS }, openRead = { screen = Screen.READ }, onAdminUnlocked = { adminUnlocked = true })
        Screen.SETTINGS -> SettingsScreen(
            onBack = { screen = Screen.HOME },
            activity = activity,
            onPickWallet = { screen = Screen.PICK_WALLET },
            onPickApp = { screen = Screen.PICK_APP },
            adminUnlocked = adminUnlocked
        )
        Screen.READ -> ReadScreen({ screen = Screen.HOME })
        Screen.PICK_WALLET -> InstalledAppsScreen(target = PickerTarget.WALLET, onDone = { screen = Screen.SETTINGS })
        Screen.PICK_APP -> InstalledAppsScreen(target = PickerTarget.APP, onDone = { screen = Screen.SETTINGS })
    }
}

enum class PickerTarget { WALLET, APP }

@Composable private fun InstalledAppsScreen(target: PickerTarget, onDone: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { InstalledAppsProvider.list(context) }
        loading = false
    }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    }
    BackHandler(onBack = onDone)
    Scaffold { p -> Column(Modifier.padding(p).padding(16.dp).fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { TextButton(onClick = onDone) { Text("Volver") }; Text(if (target == PickerTarget.WALLET) "Elegir billetera" else "Elegir aplicación", style = MaterialTheme.typography.headlineSmall) }
        OutlinedTextField(query, { query = it }, label = { Text("Buscar") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        if (loading) Text("Cargando aplicaciones instaladas...")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered, key = { it.packageName }) { app ->
                Card(Modifier.fillMaxWidth().clickable {
                    if (target == PickerTarget.WALLET) WalletConfig.add(context, app.label, app.packageName)
                    else AppConfig.add(context, app.label, app.packageName)
                    onDone()
                }) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(app.packageName)
                        Column {
                            Text(app.label, style = MaterialTheme.typography.titleSmall)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    } }
}

@Composable private fun HomeScreen(openSettings: () -> Unit, openRead: () -> Unit, onAdminUnlocked: () -> Unit) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(WalletNotificationStore.history()) }
    var serviceOn by remember { mutableStateOf(DisplayPreferences.serviceEnabled(context)) }
    var filter by remember { mutableStateOf<String?>(null) }
    var serviceAlive by remember { mutableStateOf(true) }
    var editingAdId by remember { mutableStateOf<String?>(null) }
    var draftPhrase by remember { mutableStateOf("") }
    var logoTapCount by remember { mutableStateOf(0) }
    var lastLogoTapAt by remember { mutableStateOf(0L) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(36.dp).clickable {
                    val now = System.currentTimeMillis()
                    if (now - lastLogoTapAt > 1200) logoTapCount = 0
                    logoTapCount++
                    lastLogoTapAt = now
                    if (logoTapCount >= 3) {
                        logoTapCount = 0
                        pinInput = ""
                        pinError = false
                        showPinDialog = true
                    }
                }
            )
            Text(AppInfo.DISPLAY, style = MaterialTheme.typography.headlineSmall)
        }
        Text("Asistente de notificaciones de billeteras móviles")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = openSettings, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) { Text("Configuración") }
            Button(onClick = openRead, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) { Text("Leer") }
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(filtered, key = { it.id }) { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text("${item.wallet} · ${item.receivedAt}"); Text(item.message)
            item.mediaPath?.let { path -> ThumbnailImage(path) }
            if (editingAdId == item.id) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(draftPhrase, { draftPhrase = it }, label = { Text("Frase para bloquear futuros similares") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { AdFilterConfig.add(context, item.wallet, draftPhrase); editingAdId = null }) { Text("Bloquear") }
                    TextButton(onClick = { editingAdId = null }) { Text("Cancelar") }
                }
            } else {
                TextButton(onClick = { editingAdId = item.id; draftPhrase = item.message.take(60) }) { Text("🚫 Marcar como publicidad") }
            }
        } } } }
    } }
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("PIN de administrador") },
            text = {
                Column {
                    Text("Solo para editar el token y Chat ID de Telegram.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        pinInput,
                        { pinInput = it.filter(Char::isDigit) },
                        label = { Text("PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError) Text("PIN incorrecto", color = Color.Red, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (AdminAccess.verify(pinInput)) {
                        showPinDialog = false
                        onAdminUnlocked()
                    } else pinError = true
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text("Cancelar") } }
        )
    }
}

@Composable private fun ReadScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var voiceProfile by remember { mutableStateOf(DisplayPreferences.voiceProfile(context)) }
    var isPaused by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Scaffold { p -> Column(Modifier.padding(p).padding(16.dp).fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { TextButton(onClick = onBack) { Text("Volver") }; Text("Leer", style = MaterialTheme.typography.headlineSmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoiceProfile.entries.forEach { profile ->
                FilterChip(selected = voiceProfile == profile, onClick = { voiceProfile = profile; DisplayPreferences.setVoiceProfile(context, profile) }, label = { Text(profile.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Pega o escribe el texto a leer") }, modifier = Modifier.fillMaxWidth().weight(1f))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { isPaused = false; SpeechEngine.speakText(context, text) }, enabled = text.isNotBlank()) { Text("Leer en voz alta") }
            OutlinedButton(onClick = {
                if (isPaused) { SpeechEngine.resume(context); isPaused = false } else { SpeechEngine.pause(); isPaused = true }
            }) { Text(if (isPaused) "Reanudar" else "Pausa") }
            OutlinedButton(onClick = { SpeechEngine.stop(); isPaused = false }) { Text("Detener") }
        }
    } }
}

@Composable private fun SettingsScreen(onBack: () -> Unit, activity: MainActivity, onPickWallet: () -> Unit, onPickApp: () -> Unit, adminUnlocked: Boolean) {
    val context = LocalContext.current
    var rules by remember { mutableStateOf(WalletConfig.rules(context)) }
    var appRules by remember { mutableStateOf(AppConfig.rules(context)) }
    var fullScreen by remember { mutableStateOf(DisplayPreferences.fullScreenEnabled(context)) }
    var speech by remember { mutableStateOf(DisplayPreferences.speechEnabled(context)) }
    var alert by remember { mutableStateOf(DisplayPreferences.alertEnabled(context)) }
    var buttons by remember { mutableStateOf(DisplayPreferences.buttons(context)) }
    var voiceProfile by remember { mutableStateOf(DisplayPreferences.voiceProfile(context)) }
    var speechRate by remember { mutableStateOf(DisplayPreferences.speechRateMultiplier(context)) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateChecked by remember { mutableStateOf(false) }
    var blockedPhrases by remember { mutableStateOf(AdFilterConfig.list(context)) }
    var deviceLabel by remember { mutableStateOf(DisplayPreferences.deviceLabel(context)) }
    var tgToken by remember { mutableStateOf(TelegramConfig.botToken(context)) }
    var tgChatId by remember { mutableStateOf(TelegramConfig.chatId(context)) }
    var tgInterval by remember { mutableStateOf(TelegramConfig.intervalMinutes(context).toString()) }
    var tgStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)
    Scaffold { p -> Column(Modifier.padding(p).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { TextButton(onClick = onBack) { Text("Volver") }; Text("Configuración", style = MaterialTheme.typography.headlineSmall) }
        PermissionRow("Acceso a notificaciones", isNotificationAccessEnabled(context)) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        PermissionRow("Mostrar sobre otras aplicaciones", Settings.canDrawOverlays(context)) { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { data = android.net.Uri.parse("package:${context.packageName}") }) }
        PermissionRow("Acceso a medios (fotos/audio/video de WhatsApp)", WhatsAppMediaScanner.hasMediaPermission(context)) { activity.requestMediaPermissions() }
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
        val blue = ButtonDefaults.buttonColors(containerColor = AccentBlue)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { activity.share("${AppInfo.REPORT_BASENAME}.txt", WalletNotificationStore.exportText()) }, colors = blue) { Text("Compartir Historial") }
            Button(onClick = { activity.share("${AppInfo.REPORT_BASENAME}.csv", WalletNotificationStore.exportCsv(), "text/csv") }, colors = blue) { Text("Compartir CSV") }
        }
        Button(onClick = { activity.share(AppInfo.LOG_EXPORT_NAME, ScoSecretariaLogger.read(context)) }, colors = blue) { Text("Compartir Log") }
        Button(onClick = { activity.shareApk() }) { Text("Compartir Aplicación") }
        Spacer(Modifier.height(12.dp))
        Text("Copia de seguridad (config., billeteras, apps)", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { activity.save("${AppInfo.NAME}_backup_${WalletNotificationStore.timestampForFile()}.json", BackupManager.exportJson(context), "application/json") }) { Text("Guardar Backup") }
            TextButton(onClick = { activity.restoreBackup() }) { Text("Restaurar Backup") }
        }
        Text("Billeteras autorizadas", style = MaterialTheme.typography.titleMedium)
        rules.forEach { r -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(r.packageId)
                Column { Text(r.name); Text(r.packageId.ifBlank { "(sin paquete)" }, style = MaterialTheme.typography.bodySmall, color = if (r.packageId.isBlank()) Color.Red else Color.Gray) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = r.enabled, onCheckedChange = { WalletConfig.setEnabled(context, r.name, it); rules = WalletConfig.rules(context) })
                TextButton(onClick = { WalletConfig.remove(context, r.name); rules = WalletConfig.rules(context) }) { Text("Quitar") }
            }
        } }
        Button(onClick = onPickWallet) { Text("Agregar Billetera") }
        Spacer(Modifier.height(20.dp))
        Text("Aplicaciones (General)", style = MaterialTheme.typography.titleMedium)
        Text("WhatsApp, Messenger, SMS u otras: lee el título de la app + el mensaje completo.", style = MaterialTheme.typography.bodySmall)
        appRules.forEach { r -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(r.packageId)
                Column { Text(r.name); Text(r.packageId.ifBlank { "(sin paquete)" }, style = MaterialTheme.typography.bodySmall, color = if (r.packageId.isBlank()) Color.Red else Color.Gray) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = r.enabled, onCheckedChange = { AppConfig.setEnabled(context, r.name, it); appRules = AppConfig.rules(context) })
                TextButton(onClick = { AppConfig.remove(context, r.name); appRules = AppConfig.rules(context) }) { Text("Quitar") }
            }
        } }
        Button(onClick = onPickApp) { Text("Agregar Aplicación") }
        Spacer(Modifier.height(20.dp))
        Text("Telegram", style = MaterialTheme.typography.titleMedium)
        Text("Envía por Telegram un CSV con las notificaciones nuevas cada cierto tiempo, y recibe avisos remotos vía /notificar TODOS|sucursal mensaje.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(deviceLabel, { deviceLabel = it }, label = { Text("Nombre de sucursal/dispositivo") }, modifier = Modifier.fillMaxWidth())
        if (adminUnlocked) {
            OutlinedTextField(tgToken, { tgToken = it }, label = { Text("Token del bot") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tgChatId, { tgChatId = it }, label = { Text("Chat ID") }, modifier = Modifier.fillMaxWidth())
        } else {
            Text("Token: " + if (tgToken.isBlank()) "(no configurado)" else "•••• configurado", style = MaterialTheme.typography.bodySmall)
            Text("Chat ID: " + if (tgChatId.isBlank()) "(no configurado)" else "•••• configurado", style = MaterialTheme.typography.bodySmall)
            Text("Toca 3 veces el logo de la pantalla principal para editarlos.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        OutlinedTextField(tgInterval, { tgInterval = it.filter { c -> c.isDigit() } }, label = { Text("Intervalo (minutos, mínimo 15)") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                DisplayPreferences.setDeviceLabel(context, deviceLabel)
                TelegramConfig.setBotToken(context, tgToken)
                TelegramConfig.setChatId(context, tgChatId)
                TelegramConfig.setIntervalMinutes(context, tgInterval.toLongOrNull() ?: 30L)
                if (TelegramConfig.isConfigured(context)) TelegramSyncWorker.schedule(context) else TelegramSyncWorker.cancel(context)
                tgStatus = "Guardado."
            }) { Text("Guardar y activar") }
            OutlinedButton(onClick = {
                tgStatus = "Enviando..."
                scope.launch {
                    val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { TelegramClient.sendMessage(tgToken, tgChatId, "✅ Prueba de conexión desde $deviceLabel") }
                    tgStatus = if (ok) "Mensaje de prueba enviado." else "No se pudo enviar — revisa token/chat id."
                }
            }) { Text("Enviar mensaje de prueba") }
            OutlinedButton(onClick = {
                TelegramSyncWorker.runOnce(context)
                tgStatus = "Sincronizando ahora (revisa comandos y envía el CSV pendiente, sin esperar el intervalo)..."
            }) { Text("Sincronizar ahora") }
        }
        if (tgStatus != null) Text(tgStatus!!, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))
        Text("Publicidad bloqueada", style = MaterialTheme.typography.titleMedium)
        if (blockedPhrases.isEmpty()) {
            Text("Ninguna todavía — desde el Historial puedes marcar un mensaje como publicidad.", style = MaterialTheme.typography.bodySmall)
        } else {
            blockedPhrases.forEach { bp -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text(bp.wallet, style = MaterialTheme.typography.bodySmall); Text(bp.phrase, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { AdFilterConfig.remove(context, bp.wallet, bp.phrase); blockedPhrases = AdFilterConfig.list(context) }) { Text("Quitar") }
            } }
        }
        Spacer(Modifier.height(24.dp))
    } }
}

@Composable private fun SettingSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = if (value) Color(0xFF188038) else Color.Red); Switch(checked = value, onCheckedChange = onChange) } }
@Composable private fun PermissionRow(label: String, enabled: Boolean, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = if (enabled) Color(0xFF188038) else Color.Red, modifier = Modifier.weight(1f)); TextButton(onClick = onClick) { Text(if (enabled) "✓" else "Habilitar") } } }
@Composable private fun AppIcon(packageId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageId) {
        runCatching { context.packageManager.getApplicationIcon(packageId).toBitmap().asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(36.dp))
    else Box(modifier.size(36.dp))
}
@Composable private fun ThumbnailImage(path: String) {
    val bitmap = remember(path) {
        runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Spacer(Modifier.height(6.dp))
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp))
    }
}
private fun isNotificationAccessEnabled(c: Context): Boolean { val e = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false; val component = ComponentName(c, WalletNotificationListener::class.java).flattenToString(); return e.split(':').any { it.equals(component, true) } }
