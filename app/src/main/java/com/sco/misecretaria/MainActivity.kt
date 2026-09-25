package com.sco.misecretaria

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.sco.misecretaria.ui.theme.AccentBlue
import com.sco.misecretaria.ui.theme.AccentGreen
import com.sco.misecretaria.ui.theme.ScoSecretariaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val REPORT_MIME = "text/plain"
// ALERT = aviso remoto de /notificarpantalla: usa la misma pantalla completa/aviso flotante
// que un pago, pero sin el encabezado "Pago recibido" ni el monto (no es un pago real).
enum class NotificationKind { PAYMENT, GENERAL, ALERT }
data class WalletNotification(val id: String, val wallet: String, val title: String, val message: String, val receivedAt: String, val kind: NotificationKind = NotificationKind.PAYMENT, val mediaPath: String? = null, val note: String? = null)

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
            startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title, name)))
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
                android.widget.Toast.makeText(this, getString(R.string.toast_backup_restored), android.widget.Toast.LENGTH_LONG).show()
                recreate()
            }.onFailure {
                ScoSecretariaLogger.error(this, "Fallo al restaurar backup", it)
                android.widget.Toast.makeText(this, getString(R.string.toast_backup_restore_failed), android.widget.Toast.LENGTH_LONG).show()
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
            startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title, AppInfo.NAME)))
        }.onFailure { ScoSecretariaLogger.error(this, "No se pudo compartir el APK", it) }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(AppInfo.NAME, text))
    android.widget.Toast.makeText(context, context.getString(R.string.toast_copied), android.widget.Toast.LENGTH_SHORT).show()
}

private enum class Screen { HOME, SETTINGS, READ, PICK_WALLET, PICK_APP, TRASH }

@Composable private fun ScoSecretariaApp(activity: MainActivity) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var adminUnlocked by remember { mutableStateOf(false) }
    when (screen) {
        Screen.HOME -> HomeScreen(openSettings = { screen = Screen.SETTINGS }, openRead = { screen = Screen.READ }, openTrash = { screen = Screen.TRASH }, onAdminUnlocked = { adminUnlocked = true })
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
        Screen.TRASH -> TrashScreen(onBack = { screen = Screen.HOME })
    }
}

/** Botón "Volver" — verde con texto blanco (pedido explícito del usuario, 2026-09-24), usado
 * en todas las pantallas secundarias en vez del TextButton plano de antes. */
@Composable private fun BackButton(onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.White)) {
        Text(stringResource(R.string.action_back))
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { BackButton(onDone); Text(stringResource(if (target == PickerTarget.WALLET) R.string.picker_title_wallet else R.string.picker_title_app), style = MaterialTheme.typography.headlineSmall) }
        OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.label_search)) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        if (loading) Text(stringResource(R.string.installed_apps_loading))
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

@Composable private fun HomeScreen(openSettings: () -> Unit, openRead: () -> Unit, openTrash: () -> Unit, onAdminUnlocked: () -> Unit) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(WalletNotificationStore.history()) }
    var pinnedIds by remember { mutableStateOf(WalletNotificationStore.pinnedIds()) }
    var trashCount by remember { mutableStateOf(WalletNotificationStore.trash().size) }
    var serviceOn by remember { mutableStateOf(DisplayPreferences.serviceEnabled(context)) }
    var filter by remember { mutableStateOf<String?>(null) }
    var serviceAlive by remember { mutableStateOf(true) }
    var editingAdId by remember { mutableStateOf<String?>(null) }
    var draftPhrase by remember { mutableStateOf("") }
    var editingNoteId by remember { mutableStateOf<String?>(null) }
    var draftNote by remember { mutableStateOf("") }
    var logoTapCount by remember { mutableStateOf(0) }
    var lastLogoTapAt by remember { mutableStateOf(0L) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        WalletNotificationListener.requestServiceRebind(context)
        while (true) {
            history = WalletNotificationStore.history()
            trashCount = WalletNotificationStore.trash().size
            val hb = DisplayPreferences.heartbeat(context)
            serviceAlive = hb != 0L && (System.currentTimeMillis() - hb) < 6 * 60 * 60 * 1000L
            delay(700)
        }
    }
    val sources = remember(history) { history.map { it.wallet }.distinct().sorted() }
    val filtered = remember(history, filter) { if (filter == null) history else history.filter { it.wallet == filter } }
    val ordered = remember(filtered, pinnedIds) {
        val (pinned, rest) = filtered.partition { it.id in pinnedIds }
        pinned + rest
    }
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
        Text(stringResource(R.string.home_subtitle))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = openSettings, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) { Text(stringResource(R.string.action_settings)) }
            Button(onClick = openRead, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) { Text(stringResource(R.string.action_read)) }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { serviceOn = !serviceOn; DisplayPreferences.setServiceEnabled(context, serviceOn) }, colors = ButtonDefaults.buttonColors(containerColor = if (serviceOn) Color(0xFF188038) else Color(0xFFB00020))) { Text(stringResource(if (serviceOn) R.string.service_toggle_on else R.string.service_toggle_off, AppInfo.NAME)) }
        Text(stringResource(if (serviceAlive) R.string.service_status_alive else R.string.service_status_dead), color = if (serviceAlive) Color(0xFF188038) else Color(0xFFB00020), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (filter != null) stringResource(R.string.history_title_filtered, filtered.size, history.size) else stringResource(R.string.history_title, filtered.size),
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = { selectionMode = !selectionMode; selectedIds = emptySet() }) { Text(stringResource(if (selectionMode) R.string.action_select_done else R.string.action_select)) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectionMode && selectedIds.isNotEmpty()) {
                TextButton(onClick = { showDeleteSelectedConfirm = true }) { Text(stringResource(R.string.action_delete_selected, selectedIds.size)) }
            }
            if (history.isNotEmpty()) {
                TextButton(onClick = { showClearAllConfirm = true }) { Text(stringResource(R.string.action_clear_history)) }
            }
            TextButton(onClick = openTrash) { Text(stringResource(R.string.action_trash, trashCount)) }
        }
        if (sources.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.filter_all)) })
                sources.forEach { s -> FilterChip(selected = filter == s, onClick = { filter = s }, label = { Text(s) }) }
            }
            Spacer(Modifier.height(6.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(ordered, key = { it.id }) { item ->
            val isPinned = item.id in pinnedIds
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectionMode) Checkbox(checked = item.id in selectedIds, onCheckedChange = { checked ->
                        selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id
                    })
                    Column(Modifier.weight(1f)) {
                        Text((if (isPinned) "📌 " else "") + "${item.wallet} · ${item.receivedAt}")
                        Text(item.message)
                    }
                }
                item.mediaPath?.let { path -> ThumbnailImage(path) }
                if (editingNoteId != item.id && !item.note.isNullOrBlank()) {
                    Text("📝 ${item.note}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { copyToClipboard(context, item.message) }) { Text(stringResource(R.string.action_copy)) }
                    TextButton(onClick = {
                        when (WalletNotificationStore.togglePin(item.id)) {
                            PinToggleResult.LIMIT_REACHED -> android.widget.Toast.makeText(context, context.getString(R.string.pin_limit_reached, WalletNotificationStore.MAX_PINNED), android.widget.Toast.LENGTH_SHORT).show()
                            else -> {}
                        }
                        pinnedIds = WalletNotificationStore.pinnedIds()
                    }) { Text(stringResource(if (isPinned) R.string.action_unpin else R.string.action_pin)) }
                    if (!selectionMode) {
                        TextButton(onClick = { editingNoteId = item.id; draftNote = item.note.orEmpty() }) { Text(stringResource(R.string.action_add_note)) }
                        TextButton(onClick = {
                            WalletNotificationStore.moveToTrash(item.id)
                            history = WalletNotificationStore.history()
                            pinnedIds = WalletNotificationStore.pinnedIds()
                            trashCount = WalletNotificationStore.trash().size
                        }) { Text(stringResource(R.string.action_delete)) }
                    }
                }
                if (editingNoteId == item.id) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(draftNote, { draftNote = it }, label = { Text(stringResource(R.string.note_field_label)) }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { WalletNotificationStore.setNote(item.id, draftNote); history = WalletNotificationStore.history(); editingNoteId = null }) { Text(stringResource(R.string.action_save_note)) }
                        if (!item.note.isNullOrBlank()) TextButton(onClick = { WalletNotificationStore.setNote(item.id, null); history = WalletNotificationStore.history(); editingNoteId = null }) { Text(stringResource(R.string.action_remove)) }
                        TextButton(onClick = { editingNoteId = null }) { Text(stringResource(R.string.action_cancel)) }
                    }
                }
                if (editingAdId == item.id) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(draftPhrase, { draftPhrase = it }, label = { Text(stringResource(R.string.ad_block_phrase_label)) }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { AdFilterConfig.add(context, item.wallet, draftPhrase); editingAdId = null }) { Text(stringResource(R.string.action_block)) }
                        TextButton(onClick = { editingAdId = null }) { Text(stringResource(R.string.action_cancel)) }
                    }
                } else if (!selectionMode) {
                    TextButton(onClick = { editingAdId = item.id; draftPhrase = item.message.take(60) }) { Text(stringResource(R.string.action_mark_ad)) }
                }
            } }
        } }
    } }
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(stringResource(R.string.pin_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.pin_dialog_body), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        pinInput,
                        { pinInput = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.pin_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError) Text(stringResource(R.string.pin_error), color = Color.Red, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (AdminAccess.verify(pinInput)) {
                        showPinDialog = false
                        onAdminUnlocked()
                    } else pinError = true
                }) { Text(stringResource(R.string.action_accept)) }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
    if (showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text(stringResource(R.string.confirm_delete_selected_title, selectedIds.size)) },
            text = { Text(stringResource(R.string.confirm_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    WalletNotificationStore.moveManyToTrash(selectedIds)
                    history = WalletNotificationStore.history()
                    pinnedIds = WalletNotificationStore.pinnedIds()
                    trashCount = WalletNotificationStore.trash().size
                    selectedIds = emptySet()
                    selectionMode = false
                    showDeleteSelectedConfirm = false
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteSelectedConfirm = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(stringResource(R.string.confirm_clear_history_title)) },
            text = { Text(stringResource(R.string.confirm_clear_history_body, history.size)) },
            confirmButton = {
                TextButton(onClick = {
                    WalletNotificationStore.moveAllToTrash()
                    history = WalletNotificationStore.history()
                    pinnedIds = WalletNotificationStore.pinnedIds()
                    trashCount = WalletNotificationStore.trash().size
                    selectedIds = emptySet()
                    selectionMode = false
                    showClearAllConfirm = false
                }) { Text(stringResource(R.string.action_clear_history)) }
            },
            dismissButton = { TextButton(onClick = { showClearAllConfirm = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable private fun TrashScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var trash by remember { mutableStateOf(WalletNotificationStore.trash()) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showEmptyConfirm by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Scaffold { p -> Column(Modifier.padding(p).padding(16.dp).fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { BackButton(onBack); Text(stringResource(R.string.trash_title), style = MaterialTheme.typography.headlineSmall) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (trash.isNotEmpty()) {
                TextButton(onClick = { selectionMode = !selectionMode; selectedIds = emptySet() }) { Text(stringResource(if (selectionMode) R.string.action_select_done else R.string.action_select)) }
                if (selectionMode && selectedIds.isNotEmpty()) {
                    TextButton(onClick = {
                        WalletNotificationStore.restoreMany(selectedIds)
                        trash = WalletNotificationStore.trash()
                        selectedIds = emptySet()
                        selectionMode = false
                    }) { Text(stringResource(R.string.action_restore_selected, selectedIds.size)) }
                }
                if (!selectionMode) {
                    TextButton(onClick = { WalletNotificationStore.restoreEverything(); trash = WalletNotificationStore.trash() }) { Text(stringResource(R.string.action_restore_all)) }
                    TextButton(onClick = { showEmptyConfirm = true }) { Text(stringResource(R.string.action_empty_trash)) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (trash.isEmpty()) {
            Text(stringResource(R.string.trash_empty_message), style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(trash, key = { it.id }) { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectionMode) Checkbox(checked = item.id in selectedIds, onCheckedChange = { checked ->
                    selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id
                })
                Column(Modifier.weight(1f)) {
                    Text("${item.wallet} · ${item.receivedAt}")
                    Text(item.message)
                }
            }
            if (!selectionMode) {
                TextButton(onClick = {
                    WalletNotificationStore.restore(item.id)
                    trash = WalletNotificationStore.trash()
                }) { Text(stringResource(R.string.action_restore)) }
            }
        } } } }
    } }
    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = { Text(stringResource(R.string.confirm_empty_trash_title)) },
            text = { Text(stringResource(R.string.confirm_empty_trash_body, trash.size)) },
            confirmButton = {
                TextButton(onClick = {
                    WalletNotificationStore.emptyTrash()
                    trash = WalletNotificationStore.trash()
                    selectedIds = emptySet()
                    selectionMode = false
                    showEmptyConfirm = false
                }) { Text(stringResource(R.string.action_empty_trash)) }
            },
            dismissButton = { TextButton(onClick = { showEmptyConfirm = false }) { Text(stringResource(R.string.action_cancel)) } }
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { BackButton(onBack); Text(stringResource(R.string.action_read), style = MaterialTheme.typography.headlineSmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoiceProfile.entries.forEach { profile ->
                FilterChip(selected = voiceProfile == profile, onClick = { voiceProfile = profile; DisplayPreferences.setVoiceProfile(context, profile) }, label = { Text(profile.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.read_placeholder)) }, modifier = Modifier.fillMaxWidth().weight(1f))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { isPaused = false; SpeechEngine.speakText(context, text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.action_read_aloud)) }
            OutlinedButton(onClick = {
                if (isPaused) { SpeechEngine.resume(context); isPaused = false } else { SpeechEngine.pause(); isPaused = true }
            }) { Text(stringResource(if (isPaused) R.string.action_resume else R.string.action_pause)) }
            OutlinedButton(onClick = { SpeechEngine.stop(); isPaused = false }) { Text(stringResource(R.string.action_stop)) }
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
    val blue = ButtonDefaults.buttonColors(containerColor = AccentBlue)
    BackHandler(onBack = onBack)
    Scaffold { p -> Column(Modifier.padding(p).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { BackButton(onBack); Text(stringResource(R.string.action_settings), style = MaterialTheme.typography.headlineSmall) }
        PermissionRow(stringResource(R.string.perm_notifications), isNotificationAccessEnabled(context)) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        PermissionRow(stringResource(R.string.perm_overlay), Settings.canDrawOverlays(context)) { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { data = android.net.Uri.parse("package:${context.packageName}") }) }
        PermissionRow(stringResource(R.string.perm_media), WhatsAppMediaScanner.hasMediaPermission(context)) {
            // Desde v2.23: en Android 11+ hace falta el permiso especial "Acceso a todos los
            // archivos" (MANAGE_EXTERNAL_STORAGE) — no un diálogo normal, es una pantalla propia
            // de Ajustes. En versiones más viejas, el permiso clásico de medios sigue sirviendo.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, android.net.Uri.parse("package:${context.packageName}")))
            } else {
                activity.requestMediaPermissions()
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.installed_version, AppInfo.VERSION), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                checkingUpdate = true; updateChecked = false
                scope.launch { updateInfo = UpdateManager.checkForUpdate(); checkingUpdate = false; updateChecked = true }
            }, enabled = !checkingUpdate, colors = blue) { Text(stringResource(if (checkingUpdate) R.string.checking_update else R.string.action_check_update)) }
        }
        if (updateChecked) {
            val info = updateInfo
            if (info == null) {
                Text(stringResource(R.string.up_to_date), style = MaterialTheme.typography.bodySmall)
            } else {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.update_available, info.versionName), style = MaterialTheme.typography.titleMedium)
                    if (info.notes.isNotBlank()) Text(info.notes, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (!UpdateManager.canInstallPackages(context)) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}")))
                        } else {
                            UpdateManager.downloadAndInstall(context, info)
                        }
                    }, colors = blue) { Text(stringResource(R.string.action_update_now)) }
                } }
            }
        }
        Spacer(Modifier.height(10.dp)); SettingSwitch(stringResource(R.string.setting_speech), speech) { speech = it; DisplayPreferences.setSpeechEnabled(context, it) }
        SettingSwitch(stringResource(R.string.setting_alert_overlay), alert) { alert = it; DisplayPreferences.setAlertEnabled(context, it) }
        SettingSwitch(stringResource(R.string.setting_fullscreen), fullScreen) { fullScreen = it; DisplayPreferences.setFullScreenEnabled(context, it) }
        if (alert) {
            Text(stringResource(R.string.alert_buttons_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = buttons == AlertButtons.OK_ONLY, onClick = { buttons = AlertButtons.OK_ONLY; DisplayPreferences.setButtons(context, buttons) }, label = { Text(stringResource(R.string.alert_buttons_ok_only)) }); FilterChip(selected = buttons == AlertButtons.REPEAT_AND_OK, onClick = { buttons = AlertButtons.REPEAT_AND_OK; DisplayPreferences.setButtons(context, buttons) }, label = { Text(stringResource(R.string.alert_buttons_repeat_ok)) }) }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.voice_type_title), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoiceProfile.entries.forEach { profile ->
                FilterChip(selected = voiceProfile == profile, onClick = { voiceProfile = profile; DisplayPreferences.setVoiceProfile(context, profile) }, label = { Text(profile.label) })
            }
        }
        Text(stringResource(R.string.speech_rate_title, "%.1f".format(speechRate)), style = MaterialTheme.typography.titleMedium)
        Slider(value = speechRate, onValueChange = { speechRate = it; DisplayPreferences.setSpeechRateMultiplier(context, it) }, valueRange = 0.5f..2.0f, steps = 14)
        TextButton(onClick = { runCatching { context.startActivity(Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) } }) { Text(stringResource(R.string.action_install_voices)) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { activity.share("${AppInfo.REPORT_BASENAME}.txt", WalletNotificationStore.exportText()) }, colors = blue) { Text(stringResource(R.string.action_share_history)) }
            Button(onClick = { activity.share("${AppInfo.REPORT_BASENAME}.csv", WalletNotificationStore.exportCsv(), "text/csv") }, colors = blue) { Text(stringResource(R.string.action_share_csv)) }
        }
        Button(onClick = { activity.share(AppInfo.LOG_EXPORT_NAME, ScoSecretariaLogger.read(context)) }, colors = blue) { Text(stringResource(R.string.action_share_log)) }
        Button(onClick = { activity.shareApk() }, colors = blue) { Text(stringResource(R.string.action_share_apk)) }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.backup_section_title), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { activity.save("${AppInfo.NAME}_backup_${WalletNotificationStore.timestampForFile()}.json", BackupManager.exportJson(context), "application/json") }) { Text(stringResource(R.string.action_save_backup)) }
            TextButton(onClick = { activity.restoreBackup() }) { Text(stringResource(R.string.action_restore_backup)) }
        }
        Text(stringResource(R.string.wallets_section_title), style = MaterialTheme.typography.titleMedium)
        rules.forEach { r -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(r.packageId)
                Column { Text(r.name); Text(r.packageId.ifBlank { stringResource(R.string.no_package) }, style = MaterialTheme.typography.bodySmall, color = if (r.packageId.isBlank()) Color.Red else Color.Gray) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = r.enabled, onCheckedChange = { WalletConfig.setEnabled(context, r.name, it); rules = WalletConfig.rules(context) })
                TextButton(onClick = { WalletConfig.remove(context, r.name); rules = WalletConfig.rules(context) }) { Text(stringResource(R.string.action_remove)) }
            }
        } }
        Button(onClick = onPickWallet, colors = blue) { Text(stringResource(R.string.action_add_wallet)) }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.apps_section_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.apps_section_hint), style = MaterialTheme.typography.bodySmall)
        appRules.forEach { r -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(r.packageId)
                Column { Text(r.name); Text(r.packageId.ifBlank { stringResource(R.string.no_package) }, style = MaterialTheme.typography.bodySmall, color = if (r.packageId.isBlank()) Color.Red else Color.Gray) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = r.enabled, onCheckedChange = { AppConfig.setEnabled(context, r.name, it); appRules = AppConfig.rules(context) })
                TextButton(onClick = { AppConfig.remove(context, r.name); appRules = AppConfig.rules(context) }) { Text(stringResource(R.string.action_remove)) }
            }
        } }
        Button(onClick = onPickApp, colors = blue) { Text(stringResource(R.string.action_add_app)) }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.telegram_section_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.telegram_section_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(deviceLabel, { deviceLabel = it }, label = { Text(stringResource(R.string.device_label_field)) }, modifier = Modifier.fillMaxWidth())
        if (adminUnlocked) {
            OutlinedTextField(tgToken, { tgToken = it }, label = { Text(stringResource(R.string.telegram_token_field)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tgChatId, { tgChatId = it }, label = { Text(stringResource(R.string.telegram_chatid_field)) }, modifier = Modifier.fillMaxWidth())
        } else {
            Text(stringResource(R.string.telegram_token_status, if (tgToken.isBlank()) stringResource(R.string.status_not_configured) else stringResource(R.string.status_configured_masked)), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.telegram_chatid_status, if (tgChatId.isBlank()) stringResource(R.string.status_not_configured) else stringResource(R.string.status_configured_masked)), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.telegram_masked_hint), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        OutlinedTextField(tgInterval, { tgInterval = it.filter { c -> c.isDigit() } }, label = { Text(stringResource(R.string.telegram_interval_field)) }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                DisplayPreferences.setDeviceLabel(context, deviceLabel)
                TelegramConfig.setBotToken(context, tgToken)
                TelegramConfig.setChatId(context, tgChatId)
                TelegramConfig.setIntervalMinutes(context, tgInterval.toLongOrNull() ?: 30L)
                if (TelegramConfig.isConfigured(context)) TelegramSyncWorker.schedule(context) else TelegramSyncWorker.cancel(context)
                tgStatus = context.getString(R.string.telegram_status_saved)
            }, colors = blue) { Text(stringResource(R.string.action_save_activate)) }
            OutlinedButton(onClick = {
                tgStatus = context.getString(R.string.telegram_status_sending)
                scope.launch {
                    val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { TelegramClient.sendMessage(tgToken, tgChatId, context.getString(R.string.telegram_test_message, deviceLabel)) }
                    tgStatus = if (ok) context.getString(R.string.telegram_status_sent) else context.getString(R.string.telegram_status_send_failed)
                }
            }) { Text(stringResource(R.string.action_send_test)) }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = {
            TelegramSyncWorker.runOnce(context)
            tgStatus = context.getString(R.string.telegram_status_syncing)
        }) { Text(stringResource(R.string.action_sync_now)) }
        if (tgStatus != null) Text(tgStatus!!, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.ads_section_title), style = MaterialTheme.typography.titleMedium)
        if (blockedPhrases.isEmpty()) {
            Text(stringResource(R.string.ads_section_empty), style = MaterialTheme.typography.bodySmall)
        } else {
            blockedPhrases.forEach { bp -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text(bp.wallet, style = MaterialTheme.typography.bodySmall); Text(bp.phrase, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { AdFilterConfig.remove(context, bp.wallet, bp.phrase); blockedPhrases = AdFilterConfig.list(context) }) { Text(stringResource(R.string.action_remove)) }
            } }
        }
        Spacer(Modifier.height(24.dp))
    } }
}

@Composable private fun SettingSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = if (value) Color(0xFF188038) else Color.Red); Switch(checked = value, onCheckedChange = onChange) } }
@Composable private fun PermissionRow(label: String, enabled: Boolean, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = if (enabled) Color(0xFF188038) else Color.Red, modifier = Modifier.weight(1f)); TextButton(onClick = onClick) { Text(stringResource(if (enabled) R.string.permission_ok else R.string.permission_enable)) } } }
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
