package com.sco.misecretaria

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sco.misecretaria.ui.theme.ScoSecretariaTheme

class PaymentAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)
        val id=intent.getStringExtra(EXTRA_ID).orEmpty(); val wallet=intent.getStringExtra(EXTRA_WALLET).orEmpty(); val message=intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        val isPayment = runCatching { NotificationKind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: NotificationKind.PAYMENT.name) }.getOrDefault(NotificationKind.PAYMENT) == NotificationKind.PAYMENT
        setContent { ScoSecretariaTheme { Column(Modifier.padding(24.dp).fillMaxWidth(), verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if (isPayment) {
                Text(stringResource(R.string.notif_title_payment, wallet), style=MaterialTheme.typography.headlineSmall)
                Text(AmountFormatter.amount(message), color=Color.Red, style=MaterialTheme.typography.headlineLarge)
            } else {
                Text(wallet, style=MaterialTheme.typography.headlineSmall)
            }
            Text(message, style=MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                if (DisplayPreferences.buttons(this@PaymentAlertActivity) == AlertButtons.REPEAT_AND_OK) {
                    Button(onClick={ SpeechEngine.speak(this@PaymentAlertActivity, WalletNotification(id,wallet,"",message,"")) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0), contentColor = Color.White)) { Text(stringResource(R.string.action_repeat)) }
                }
                Button(onClick={ acknowledge(id) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEB3B), contentColor = Color.Red)) { Text(stringResource(R.string.action_ok)) }
            }
        } } }
    }
    private fun acknowledge(id:String){ WalletNotificationStore.acknowledge(id); WalletNotificationNotifier.cancel(this,id); WalletOverlay.remove(); setResult(Activity.RESULT_OK); finish() }
    companion object { const val EXTRA_ID="notification_id"; const val EXTRA_WALLET="wallet"; const val EXTRA_MESSAGE="message"; const val EXTRA_KIND="kind" }
}
object AmountFormatter { private val regex=Regex("(?i)(Bs\\.?\\s*[0-9]+(?:[.,][0-9]{1,2})?)"); fun amount(message:String)=regex.find(message)?.value ?: "Bs. --" }
