package com.anfas.samouchitel

import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/** Receives selected words from the browser PWA and keeps audio alive in a media service. */
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private var ready = false
    private var queuedWords: List<String> = emptyList()
    private var queuedRate = 0.82f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Самоучитель Аудио\n\nОткройте «Самоучитель» в Chrome, выберите слова и нажмите «В фоне».\n\nВоспроизведение и кнопки находятся в уведомлении."
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(36, 36, 36, 36)
        })
        tts = TextToSpeech(this, this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
        receive(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.US
            startQueuedPlayback()
        }
    }

    private fun receive(intent: Intent?) {
        val raw = intent?.data?.getQueryParameter("words") ?: return
        queuedWords = raw.split('\u001f').map { it.trim() }.filter { it.isNotBlank() }.take(100)
        queuedRate = intent.data?.getQueryParameter("rate")?.toFloatOrNull()?.coerceIn(0.5f, 1.2f) ?: 0.82f
        startQueuedPlayback()
    }

    private fun startQueuedPlayback() {
        if (!ready || queuedWords.isEmpty()) return
        val directory = File(cacheDir, "drill").apply { deleteRecursively(); mkdirs() }
        tts.stop()
        tts.language = Locale.US
        tts.setSpeechRate(queuedRate)
        synthesize(queuedWords, 0, directory)
    }

    private fun synthesize(parts: List<String>, index: Int, directory: File) {
        if (index >= parts.size) {
            startForegroundService(Intent(this, PlaybackService::class.java).putExtra("dir", directory.absolutePath))
            return
        }
        val file = File(directory, "%04d-voice.wav".format(index * 2))
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String) = Unit
            override fun onError(id: String) = runOnUiThread { synthesize(parts, index + 1, directory) }
            override fun onDone(id: String) = runOnUiThread {
                writeSilence(File(directory, "%04d-gap.wav".format(index * 2 + 1)))
                synthesize(parts, index + 1, directory)
            }
        })
        tts.synthesizeToFile(parts[index], Bundle(), file, "drill-$index")
    }

    private fun writeSilence(file: File) {
        val sampleRate = 8_000
        val dataSize = sampleRate * 3 * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray()).put("fmt ".toByteArray())
        header.putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(dataSize)
        FileOutputStream(file).use { it.write(header.array()); it.write(ByteArray(dataSize)) }
    }

    override fun onDestroy() { tts.shutdown(); super.onDestroy() }
}
