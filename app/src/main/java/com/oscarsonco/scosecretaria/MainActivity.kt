package com.oscarsonco.scosecretaria

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.oscarsonco.scosecretaria.ui.theme.ScoSecretariaTheme
import kotlinx.coroutines.delay

private const val REPORT_MIME = "text/plain"
data class WalletNotification(val id: String, val wallet: String, val title: String, val message: String, val receivedAt: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state); WalletNotificationStore.init(applicationContext); render() }
    private fun render() { setContent { ScoSecretariaTheme { ScoSecretariaApp(this) } } }
    fun share(name: String, text: String) = startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = REPORT_MIME; putExtra(Intent.EXTRA_SUBJECT, name); putExtra(Intent.EXTRA_TEXT, text); clipData = ClipData.newPlainText(name, text) }, "Compartir $name"))
    fun save(name: String) = startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = REPORT_MIME; putExtra(Intent.EXTRA_TITLE, name) }, if (name.endsWith(".log")) 10 else 11)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); if (resultCode == RESULT_OK && data?.data != null) contentResolver.openOutputStream(data.data!!)?.bufferedWriter()?.use { it.write(if (requestCode == 10) ScoSecretariaLogger.read(this) else WalletNotificationStore.exportText()) } }
}

@Composable private fun ScoSecretariaApp(activity: MainActivity) { var settings by remember { mutableStateOf(false) }; if (settings) SettingsScreen({ settings = false }, activity) else HomeScreen({ settings = true }) }

@Composable private fun HomeScreen(openSettings: () -> Unit) {
    val context = LocalContext.current; var pending by remember { mutableStateOf(WalletNotificationStore.pending()) }; var history by remember { mutableStateOf(WalletNotificationStore.history()) }
    LaunchedEffect(Unit) { while (true) { pending = WalletNotificationStore.pending(); history = WalletNotificationStore.history(); delay(700) } }
    val current = pending.firstOrNull()
    if (current != null && DisplayPreferences.alertEnabled(context) && DisplayPreferences.mode(context) == DisplayMode.OLD) AlertDialog(onDismissRequest = {}, title = { Text("Pago recibido: ${current.wallet}") }, text = { Column { Text(AmountFormatter.amount(current.message), color = Color.Red, style = MaterialTheme.typography.headlineLarge); Text(current.message) } }, confirmButton = { Row { if (DisplayPreferences.buttons(context) == AlertButtons.REPEAT_AND_OK) { Button(onClick = { SpeechEngine.speak(context, current) }) { Text("Repetir") }; Spacer(Modifier.width(8.dp)) }; Button(onClick = { WalletNotificationStore.acknowledge(current.id); WalletNotificationNotifier.cancel(context, current.id); pending = WalletNotificationStore.pending() }) { Text("OK") } } })
    Scaffold { p -> Column(Modifier.padding(p).padding(16.dp).fillMaxSize()) { Text("ScoSecretaria V1.4", style = MaterialTheme.typography.headlineSmall); Text("Asistente de notificaciones de billeteras móviles"); Spacer(Modifier.height(12.dp)); Button(onClick = openSettings) { Text("Configuración") }; Spacer(Modifier.height(12.dp)); Text("Historial (${history.size})", style = MaterialTheme.typography.titleMedium); LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(history, key = { it.id }) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("${it.wallet} · ${it.receivedAt}"); Text(it.message) } } } } } }
}

@Composable private fun SettingsScreen(onBack: () -> Unit, activity: MainActivity) {
    val context = LocalContext.current
    var rules by remember { mutableStateOf(WalletConfig.rules(context)) }
    var mode by remember { mutableStateOf(DisplayPreferences.mode(context)) }
    var speech by remember { mutableStateOf(DisplayPreferences.speechEnabled(context)) }
    var alert by remember { mutableStateOf(DisplayPreferences.alertEnabled(context)) }
    var buttons by remember { mutableStateOf(DisplayPreferences.buttons(context)) }
    var name by remember { mutableStateOf("") }; var pkg by remember { mutableStateOf("") }
    val test = { val item = WalletNotification("test-${System.currentTimeMillis()}", "MiPaguito", "Test", "Recibiste un pago por Bs 10", WalletNotificationStore.now()); WalletNotificationStore.add(item); WalletNotificationNotifier.show(context, item); if (alert && mode == DisplayMode.NEW) WalletOverlay.show(context, item); if (speech) SpeechEngine.speak(context, item) }
    Scaffold { p -> Column(Modifier.padding(p).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { TextButton(onClick = onBack) { Text("Volver") }; Text("Configuración", style = MaterialTheme.typography.headlineSmall) }
        PermissionRow("Acceso a notificaciones", isNotificationAccessEnabled(context)) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        PermissionRow("Mostrar sobre otras aplicaciones", Settings.canDrawOverlays(context)) { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { data = android.net.Uri.parse("package:${context.packageName}") }) }
        Spacer(Modifier.height(10.dp)); SettingSwitch("Notificación hablada", speech) { speech = it; DisplayPreferences.setSpeechEnabled(context, it) }
        SettingSwitch("Pantalla de aviso", alert) { alert = it; DisplayPreferences.setAlertEnabled(context, it) }
        if (alert) {
            Text("Tipo de pantalla", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = mode == DisplayMode.NEW, onClick = { mode = DisplayMode.NEW; DisplayPreferences.setMode(context, mode) }, label = { Text("Nueva") }); FilterChip(selected = mode == DisplayMode.OLD, onClick = { mode = DisplayMode.OLD; DisplayPreferences.setMode(context, mode) }, label = { Text("Antigua") }) }
            Text("Botones del aviso", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = buttons == AlertButtons.OK_ONLY, onClick = { buttons = AlertButtons.OK_ONLY; DisplayPreferences.setButtons(context, buttons) }, label = { Text("Solo OK") }); FilterChip(selected = buttons == AlertButtons.REPEAT_AND_OK, onClick = { buttons = AlertButtons.REPEAT_AND_OK; DisplayPreferences.setButtons(context, buttons) }, label = { Text("Repetir y OK") }) }
        }
        Spacer(Modifier.height(8.dp)); Button(onClick = test) { Text("Test") }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextButton(onClick = { activity.save("ScoSecretaria_ReporteV1.4_${WalletNotificationStore.timestampForFile()}.txt") }) { Text("Guardar Historial") }; TextButton(onClick = { activity.share("ScoSecretaria_ReporteV1.4.txt", WalletNotificationStore.exportText()) }) { Text("Compartir Historial") } }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextButton(onClick = { activity.save("ScoSecretariaV1.4.log") }) { Text("Guardar Log") }; TextButton(onClick = { activity.share("ScoSecretariaV1.4.log", ScoSecretariaLogger.read(context)) }) { Text("Compartir Log") } }
        Text("Billeteras autorizadas", style = MaterialTheme.typography.titleMedium)
        rules.forEach { r -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(r.name); Switch(checked = r.enabled, onCheckedChange = { WalletConfig.setEnabled(context, r.name, it); rules = WalletConfig.rules(context) }) } }
        OutlinedTextField(name, { name = it }, label = { Text("Nombre de nueva billetera") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pkg, { pkg = it }, label = { Text("Paquete Android (opcional)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { WalletConfig.add(context, name, pkg); rules = WalletConfig.rules(context); name = ""; pkg = "" }) { Text("Agregar billetera") }
        Spacer(Modifier.height(24.dp))
    } }
}

@Composable private fun SettingSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = if (value) Color(0xFF188038) else Color.Red); Switch(checked = value, onCheckedChange = onChange) } }
@Composable private fun PermissionRow(label: String, enabled: Boolean, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = if (enabled) Color(0xFF188038) else Color.Red); TextButton(onClick = onClick) { Text(if (enabled) "✓" else "Habilitar") } } }
private fun isNotificationAccessEnabled(c: Context): Boolean { val e = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false; val component = ComponentName(c, WalletNotificationListener::class.java).flattenToString(); return e.split(':').any { it.equals(component, true) } }
