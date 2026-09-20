package com.oscarsonco.scosecretaria

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oscarsonco.scosecretaria.ui.theme.ScoSecretariaTheme

class PaymentAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)
        val id=intent.getStringExtra(EXTRA_ID).orEmpty(); val wallet=intent.getStringExtra(EXTRA_WALLET).orEmpty(); val message=intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        setContent { ScoSecretariaTheme { Column(Modifier.padding(24.dp).fillMaxWidth(), verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("Pago recibido: $wallet", style=MaterialTheme.typography.headlineSmall)
            Text(AmountFormatter.amount(message), color=Color.Red, style=MaterialTheme.typography.headlineLarge)
            Text(message, style=MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                if (DisplayPreferences.buttons(this@PaymentAlertActivity) == AlertButtons.REPEAT_AND_OK) {
                    Button(onClick={ SpeechEngine.speak(this@PaymentAlertActivity, WalletNotification(id,wallet,"",message,"")) }) { Text("Repetir") }
                }
                Button(onClick={ acknowledge(id) }) { Text("OK") }
            }
        } } }
    }
    private fun acknowledge(id:String){ WalletNotificationStore.acknowledge(id); WalletNotificationNotifier.cancel(this,id); WalletOverlay.remove(); setResult(Activity.RESULT_OK); finish() }
    companion object { const val EXTRA_ID="notification_id"; const val EXTRA_WALLET="wallet"; const val EXTRA_MESSAGE="message" }
}
object AmountFormatter { private val regex=Regex("(?i)(Bs\\.?\\s*[0-9]+(?:[.,][0-9]{1,2})?)"); fun amount(message:String)=regex.find(message)?.value ?: "Bs. --" }
