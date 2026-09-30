package com.sco.misecretaria

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import kotlin.concurrent.thread

/**
 * v2.40 — pedido explícito del usuario (2026-09-30): capturar el AUDIO de mensajes de voz
 * "Ver una vez" de WhatsApp para arqueos/balances de caja que sus empleados le mandan en ese
 * formato "por seguridad" (para que no quede en el WhatsApp de ellos) — el usuario necesita un
 * respaldo del lado del administrador por si hay una contingencia o un malentendido.
 *
 * Confirmado en vivo (2026-09-30) que la captura de PANTALLA (foto/video) es imposible:
 * WhatsApp pone `FLAG_SECURE` en el visor de "Ver una vez", que bloquea por igual el
 * screenshot básico, `AccessibilityService.takeScreenshot()` y la grabación de pantalla
 * (`MediaProjection` con video) — probado con `adb shell screencap` mientras el visor estaba
 * abierto: falló por completo (0 bytes), mientras que la MISMA prueba fuera del visor
 * funcionó perfecto. El AUDIO es un subsistema aparte de Android, sin esa restricción —
 * confirmado con `dumpsys audio` en vivo que la sesión de reproducción de WhatsApp NO tenía
 * marcadas las banderas que sí bloquean captura de audio (`FLAG_NO_MEDIA_PROJECTION`/
 * `FLAG_NO_SYSTEM_CAPTURE`) — de ahí que esta clase exista SOLO para audio, no para medios
 * visuales.
 *
 * Requiere Android 10+ (`AudioPlaybackCaptureConfiguration`) y el token de `MediaProjection`
 * que el usuario otorga UNA VEZ desde Configuración → "🎙️ Armar grabación" (protegido con el
 * PIN de administrador, ver `SettingsScreen`) — Android exige ese permiso con un diálogo del
 * sistema por cada sesión nueva, no se puede dejar concedido para siempre en segundo plano;
 * sigue "armado" mientras este servicio en primer plano siga vivo (se cae si se cierra la app
 * o se reinicia el teléfono, hay que volver a armarlo).
 *
 * Detección por energía (no depende de eventos de WhatsApp, que no expone ninguno público
 * para "empezó/terminó de reproducir"): mientras está armado, lee el audio de WhatsApp
 * (filtrado por su propio UID vía `addMatchingUid`, para no mezclar música u otras apps) en
 * fragmentos cortos; cuando detecta sonido después de silencio, abre un `.wav` nuevo; tras un
 * tramo sostenido de silencio, lo cierra e intenta asociarlo a la notificación de "Mensaje de
 * voz" más cercana en el tiempo que todavía no tenga archivo
 * (`WalletNotificationStore.attachCapturedAudio`) — si no encuentra ninguna, lo guarda igual
 * como notificación aparte para no perder el audio.
 *
 * **Primera versión, sin calibrar en la práctica todavía** — el umbral de silencio
 * (`SILENCE_RMS_THRESHOLD`) es un punto de partida razonable para PCM de 16 bits, puede
 * necesitar ajuste si resulta muy sensible (corta música/ruido de fondo como si fuera el
 * mensaje) o muy poco sensible (no detecta notas de voz grabadas bajito).
 */
class ViewOnceAudioCaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        private const val CHANNEL_ID = "view_once_audio_capture"
        private const val NOTIF_ID = 9201
        private const val SAMPLE_RATE = 44100
        private const val SILENCE_RMS_THRESHOLD = 300.0
        private const val SILENCE_HANGOVER_MS = 1500L
        private const val MIN_SEGMENT_MS = 400L

        @Volatile var isRunning: Boolean = false
            private set
    }

    private var audioRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null
    private var captureThread: Thread? = null

    @Volatile private var stopRequested = false

    override fun onCreate() {
        super.onCreate()
        WalletNotificationStore.init(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || resultCode == 0 || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
        runCatching { startCapture(resultCode, resultData) }.onFailure {
            ScoSecretariaLogger.error(this, "No se pudo iniciar la captura de audio 'Ver una vez'", it)
            stopSelf()
        }
        return START_STICKY
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection = manager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            ScoSecretariaLogger.error(this, "MediaProjection nula al armar la captura de audio 'Ver una vez'")
            stopSelf()
            return
        }
        mediaProjection = projection

        // Sin este UID, la captura mezclaría el audio de CUALQUIER app con USAGE_MEDIA/
        // USAGE_UNKNOWN (música, otros mensajeros, etc.) — se acota solo a WhatsApp.
        val whatsappUid = runCatching { packageManager.getApplicationInfo("com.whatsapp", 0).uid }.getOrNull()

        val configBuilder = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
        if (whatsappUid != null) configBuilder.addMatchingUid(whatsappUid)

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(minBufferSize.coerceAtLeast(4096) * 4)
            .setAudioPlaybackCaptureConfig(configBuilder.build())
            .build()

        audioRecord = record
        record.startRecording()
        isRunning = true
        ScoSecretariaLogger.info(this, "Captura de audio 'Ver una vez' armada y escuchando")

        stopRequested = false
        captureThread = thread(name = "ViewOnceAudioCapture") { captureLoop(record) }
    }

    private fun captureLoop(record: AudioRecord) {
        val chunk = ShortArray(1024)
        var currentOut: RandomAccessFile? = null
        var currentFile: File? = null
        var segmentStartedAt = 0L
        var lastSoundAt = 0L
        val mediaDir = File(filesDir, "media").apply { mkdirs() }

        fun closeSegment() {
            val out = currentOut ?: return
            val file = currentFile
            runCatching {
                writeWavHeader(out, SAMPLE_RATE)
                out.close()
            }
            currentOut = null
            currentFile = null
            val durationMs = System.currentTimeMillis() - segmentStartedAt
            if (file != null) {
                if (durationMs >= MIN_SEGMENT_MS) {
                    ScoSecretariaLogger.info(this, "Audio capturado ('Ver una vez'): ${file.name} (${durationMs / 1000}s)")
                    WalletNotificationStore.attachCapturedAudio(file.absolutePath, segmentStartedAt)
                } else {
                    file.delete()
                }
            }
        }

        while (!stopRequested) {
            val read = record.read(chunk, 0, chunk.size)
            if (read <= 0) continue
            var sumSquares = 0.0
            for (i in 0 until read) sumSquares += chunk[i].toDouble() * chunk[i].toDouble()
            val rms = kotlin.math.sqrt(sumSquares / read)
            val now = System.currentTimeMillis()

            if (rms > SILENCE_RMS_THRESHOLD) {
                if (currentOut == null) {
                    val file = File(mediaDir, "${UUID.randomUUID()}.wav")
                    currentFile = file
                    segmentStartedAt = now
                    currentOut = RandomAccessFile(file, "rw").apply {
                        setLength(0)
                        seek(44) // espacio reservado para el encabezado WAV, se completa al cerrar
                    }
                }
                lastSoundAt = now
            }
            currentOut?.let { out ->
                val bytes = ByteArray(read * 2)
                for (i in 0 until read) {
                    val v = chunk[i].toInt()
                    bytes[i * 2] = (v and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                }
                out.write(bytes)
                if (now - lastSoundAt > SILENCE_HANGOVER_MS) closeSegment()
            }
        }
        closeSegment()
    }

    private fun writeWavHeader(out: RandomAccessFile, sampleRate: Int) {
        val dataLength = (out.length() - 44).coerceAtLeast(0)
        out.seek(0)
        val header = ByteArray(44)
        fun putStr(offset: Int, s: String) = s.forEachIndexed { i, c -> header[offset + i] = c.code.toByte() }
        fun putInt(offset: Int, v: Int) {
            header[offset] = (v and 0xFF).toByte()
            header[offset + 1] = ((v shr 8) and 0xFF).toByte()
            header[offset + 2] = ((v shr 16) and 0xFF).toByte()
            header[offset + 3] = ((v shr 24) and 0xFF).toByte()
        }
        fun putShort(offset: Int, v: Int) {
            header[offset] = (v and 0xFF).toByte()
            header[offset + 1] = ((v shr 8) and 0xFF).toByte()
        }
        putStr(0, "RIFF")
        putInt(4, (36 + dataLength).toInt())
        putStr(8, "WAVE")
        putStr(12, "fmt ")
        putInt(16, 16)
        putShort(20, 1) // PCM
        putShort(22, 1) // mono
        putInt(24, sampleRate)
        putInt(28, sampleRate * 2) // byte rate = sampleRate * canales * bytesPorMuestra
        putShort(32, 2) // block align
        putShort(34, 16) // bits por muestra
        putStr(36, "data")
        putInt(40, dataLength.toInt())
        out.write(header)
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, getString(R.string.view_once_capture_channel_name), NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.view_once_capture_notif_title))
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopRequested = true
        runCatching { captureThread?.join(2000) }
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        runCatching { mediaProjection?.stop() }
        audioRecord = null
        mediaProjection = null
        isRunning = false
        ScoSecretariaLogger.info(this, "Captura de audio 'Ver una vez' desarmada")
        super.onDestroy()
    }
}
