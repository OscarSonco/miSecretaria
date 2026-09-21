package com.sco.misecretaria

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.HorizontalScrollView

object WalletOverlay {
    private var windowManager: WindowManager? = null
    private var view: View? = null
    private var currentId: String? = null

    fun show(context: Context, item: WalletNotification) {
        if (!Settings.canDrawOverlays(context)) {
            ScoSecretariaLogger.error(context, "Permiso de mostrar sobre otras aplicaciones no concedido")
            return
        }
        val manager = context.getSystemService(WindowManager::class.java)
        val overlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 24f
                setStroke(2, Color.rgb(25, 118, 210))
            }
            elevation = 16f
        }
        overlay.addView(TextView(context).apply {
            text = "Pago recibido: ${item.wallet}"
            textSize = 21f
            setTextColor(Color.rgb(20, 20, 20))
        })
        overlay.addView(TextView(context).apply {
            text = AmountFormatter.amount(item.message)
            textSize = 30f
            setTextColor(Color.RED)
            setPadding(0, 12, 0, 4)
        })
        overlay.addView(TextView(context).apply {
            text = item.message
            textSize = 17f
            setTextColor(Color.rgb(40, 40, 40))
            setPadding(0, 4, 0, 20)
        })
        val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        if (DisplayPreferences.buttons(context) == AlertButtons.REPEAT_AND_OK) buttons.addView(Button(context).apply {
            text = "Repetir"
            setBackgroundColor(Color.rgb(21, 101, 192))
            setTextColor(Color.WHITE)
            setOnClickListener { SpeechEngine.speak(context, item) }
        })
        buttons.addView(Button(context).apply {
            text = "OK"
            setBackgroundColor(Color.rgb(255, 235, 59))
            setTextColor(Color.RED)
            setOnClickListener {
                WalletNotificationStore.acknowledge(item.id)
                WalletNotificationNotifier.cancel(context, item.id)
                remove()
            }
        })
        overlay.addView(buttons)

        remove()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        try {
            manager.addView(overlay, params)
            windowManager = manager
            view = overlay
            currentId = item.id
            ScoSecretariaLogger.info(context, "Aviso visible sobre otras aplicaciones")
        } catch (error: RuntimeException) {
            ScoSecretariaLogger.error(context, "No se pudo mostrar aviso superpuesto", error)
        }
    }

    fun remove() {
        val existing = view ?: return
        runCatching { windowManager?.removeView(existing) }
        view = null
        currentId = null
    }
}
