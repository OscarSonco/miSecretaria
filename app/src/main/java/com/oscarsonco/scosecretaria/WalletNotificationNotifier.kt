package com.oscarsonco.scosecretaria

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object WalletNotificationNotifier {
    private const val channelId = "wallet_payments_v14"
    private const val notificationTag = "ScoSecretariaV1.4"

    fun show(context: Context, item: WalletNotification) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Pagos recibidos", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos urgentes de billeteras autorizadas"
                enableVibration(true)
                setShowBadge(true)
            }
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fullScreenIntent = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            Intent(context, PaymentAlertActivity::class.java).apply {
                putExtra(PaymentAlertActivity.EXTRA_ID, item.id)
                putExtra(PaymentAlertActivity.EXTRA_WALLET, item.wallet)
                putExtra(PaymentAlertActivity.EXTRA_MESSAGE, item.message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Pago recibido: ${item.wallet}")
            .setContentText(item.message)
            .setStyle(Notification.BigTextStyle().bigText(item.message))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .build()
        manager.notify(notificationTag, item.id.hashCode(), notification)
    }

    fun cancel(context: Context, id: String) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(notificationTag, id.hashCode())
    }
}
