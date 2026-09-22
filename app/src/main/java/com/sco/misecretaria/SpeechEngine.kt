package com.sco.misecretaria

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

object SpeechEngine {
    private var tts: TextToSpeech? = null
    private var pendingChunks: List<String> = emptyList()
    private var chunkIndex: Int = 0

    fun init(context: Context) {
        if (tts == null) tts = TextToSpeech(context.applicationContext) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale.forLanguageTag("es-BO") }
    }

    fun speak(context: Context, item: WalletNotification) {
        init(context)
        applyVoiceProfile(context)
        tts?.setOnUtteranceProgressListener(null)
        val text = if (item.kind == NotificationKind.GENERAL) NotificationSpeech.general(item.wallet, item.message)
                   else AmountSpeech.buildSpeechText(item.wallet, item.message)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, item.id)
    }

    /** Lee texto libre (pantalla "Leer"), troceando textos largos. Soporta pausa/reanudar por trozo. */
    fun speakText(context: Context, text: String) {
        init(context)
        applyVoiceProfile(context)
        val maxLen = runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000).coerceAtMost(3900)
        pendingChunks = chunkText(text, maxLen)
        chunkIndex = 0
        speakFromCurrentChunk(context)
    }

    /** Detiene la lectura y recuerda en qué trozo iba, para poder continuar con resume(). */
    fun pause() { tts?.stop() }

    fun resume(context: Context) {
        init(context)
        applyVoiceProfile(context)
        speakFromCurrentChunk(context)
    }

    fun stop() { tts?.stop(); pendingChunks = emptyList(); chunkIndex = 0 }

    fun shutdown() { tts?.stop(); tts?.shutdown(); tts = null }

    private fun speakFromCurrentChunk(context: Context) {
        val engine = tts ?: return
        if (chunkIndex >= pendingChunks.size) return
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                chunkIndex++
                if (chunkIndex < pendingChunks.size) speakFromCurrentChunk(context)
            }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) {}
        })
        engine.speak(pendingChunks[chunkIndex], TextToSpeech.QUEUE_FLUSH, null, "leer-$chunkIndex")
    }

    private fun applyVoiceProfile(context: Context) {
        val engine = tts ?: return
        val profile = DisplayPreferences.voiceProfile(context)
        val rateMultiplier = DisplayPreferences.speechRateMultiplier(context)
        engine.setPitch(profile.pitch)
        engine.setSpeechRate(profile.rate * rateMultiplier)
        findVoice(engine, wantMale = profile == VoiceProfile.MALE)?.let { runCatching { engine.voice = it } }
    }

    private fun findVoice(engine: TextToSpeech, wantMale: Boolean): Voice? {
        val voices = runCatching { engine.voices }.getOrNull().orEmpty()
        return voices.firstOrNull { v ->
            val name = v.name.lowercase(Locale.ROOT)
            v.locale.language.equals("es", ignoreCase = true) &&
                if (wantMale) name.contains("male") && !name.contains("female") else name.contains("female")
        }
    }

    private fun chunkText(text: String, maxLen: Int): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= maxLen) return listOf(trimmed)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < trimmed.length) {
            var end = (start + maxLen).coerceAtMost(trimmed.length)
            if (end < trimmed.length) {
                val lastSpace = trimmed.lastIndexOf(' ', end)
                if (lastSpace > start) end = lastSpace
            }
            chunks.add(trimmed.substring(start, end).trim())
            start = end
        }
        return chunks.filter { it.isNotBlank() }
    }
}
