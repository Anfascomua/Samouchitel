package com.anfas.samouchitel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds a deterministic local playlist: English audio, silence, Russian audio, silence. */
class MainActivity : AppCompatActivity() {
    private data class Clip(val text: String, val lang: String, val kind: String, val pauseAfterMs: Int)
    private var clips: List<Clip> = emptyList()
    private var speechRate = 0.82f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    private fun receive(intent: Intent?) {
        val raw = intent?.data?.getQueryParameter("words") ?: return
        clips = raw.split('\u001f').flatMap { item ->
            val pair = item.split('\u001e', limit = 2)
            val english = pair.getOrNull(0)?.trim().orEmpty()
            val translations = pair.getOrNull(1).orEmpty().split(';').map { it.trim() }.filter { it.isNotBlank() }
            buildList {
                if (english.isNotBlank()) add(Clip(english, "en", "en", 2000))
                translations.forEachIndexed { index, translation ->
                    add(Clip(translation, "ru", "ru", if (index == translations.lastIndex) 2000 else 1000))
                }
            }
        }.take(200)
        speechRate = intent.data?.getQueryParameter("rate")?.toFloatOrNull()?.coerceIn(0.5f, 1.2f) ?: 0.82f
        buildFiles()
    }

    private fun buildFiles() {
        if (clips.isEmpty()) return
        stopService(Intent(this, PlaybackService::class.java))
        val directory = File(cacheDir, "drill").apply { deleteRecursively(); mkdirs() }
        downloadClip(directory, 0)
    }

    private fun downloadClip(directory: File, index: Int) {
        if (index >= clips.size) {
            startForegroundService(Intent(this, PlaybackService::class.java).putExtra("dir", directory.absolutePath).putExtra("rate", speechRate))
            finish()
            return
        }
        val clip = clips[index]
        val audioFile = File(directory, "%04d-%s.mp3".format(index * 2, clip.kind))
        Thread {
            try {
                val text = URLEncoder.encode(clip.text, Charsets.UTF_8.name()).replace("+", "%20")
                URL("https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=${clip.lang}&q=$text").openConnection().apply {
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    connectTimeout = 15000
                    readTimeout = 30000
                }.getInputStream().use { input -> audioFile.outputStream().use { output -> input.copyTo(output) } }
            } catch (_: Exception) {
                // Keep positions stable even if one network clip failed; the surrounding clips still play.
                writeSilence(audioFile, 250)
            }
            writeSilence(File(directory, "%04d-gap.wav".format(index * 2 + 1)), clip.pauseAfterMs)
            runOnUiThread { downloadClip(directory, index + 1) }
        }.start()
    }

    private fun writeSilence(file: File, durationMs: Int) {
        val sampleRate = 8_000
        val dataSize = sampleRate * 2 * durationMs / 1_000
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray()).put("fmt ".toByteArray())
        header.putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(dataSize)
        FileOutputStream(file).use { it.write(header.array()); it.write(ByteArray(dataSize)) }
    }
}
