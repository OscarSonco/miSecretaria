package com.oscarsonco.scosecretaria

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

object SpeechEngine {
    private var tts: TextToSpeech? = null
    fun init(context: Context) { if (tts == null) tts = TextToSpeech(context.applicationContext) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale.forLanguageTag("es-BO") } }
    fun speak(context: Context, item: WalletNotification) { init(context); tts?.speak("${item.wallet}. ${item.message}", TextToSpeech.QUEUE_FLUSH, null, item.id) }
    fun shutdown() { tts?.stop(); tts?.shutdown(); tts = null }
}
