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
import java.net.HttpURLConnection
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds a deterministic local playlist: English audio, silence, Russian audio, silence. */
class MainActivity : AppCompatActivity() {
    private data class Clip(val text: String, val lang: String, val kind: String, val pauseAfterMs: Int)
    private var clips: List<Clip> = emptyList()
    private var speechRate = 0.82f
    private var playlistKey = ""
    private var playAfterBuild = false

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
        val mode = intent.data?.getQueryParameter("mode") ?: "play"
        playlistKey = raw
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
        // Match the normal in-app listening speed; do not inherit a deliberately slow English voice setting.
        speechRate = 0.9f
        val directory = File(cacheDir, "drill")
        val savedKey = File(directory, "playlist.key").takeIf { it.exists() }?.readText()
        val alreadyBuilt = savedKey == playlistKey && directory.listFiles()?.any { it.name.endsWith(".mp3") } == true
        if (mode == "play" && alreadyBuilt) {
            startForegroundService(Intent(this, PlaybackService::class.java).putExtra("dir", directory.absolutePath).putExtra("rate", speechRate))
            finish()
            return
        }
        playAfterBuild = mode == "play"
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
            File(directory, "playlist.key").writeText(playlistKey)
            if (playAfterBuild) startForegroundService(Intent(this, PlaybackService::class.java).putExtra("dir", directory.absolutePath).putExtra("rate", speechRate))
            finish()
            return
        }
        val clip = clips[index]
        val audioFile = File(directory, "%04d-%s.mp3".format(index * 2, clip.kind))
        Thread {
            if (!downloadSpeech(clip, audioFile)) writeSilence(audioFile, 250)
            writeSilence(File(directory, "%04d-gap.wav".format(index * 2 + 1)), clip.pauseAfterMs)
            runOnUiThread { downloadClip(directory, index + 1) }
        }.start()
    }

    private fun downloadSpeech(clip: Clip, destination: File): Boolean {
        val text = URLEncoder.encode(clip.text, Charsets.UTF_8.name()).replace("+", "%20")
        repeat(3) { attempt ->
            var connection: HttpURLConnection? = null
            try {
                connection = URL("https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=${clip.lang}&q=$text").openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
                    if (destination.length() > 512) return true
                }
            } catch (_: Exception) {
                // A short retry is more reliable than silently dropping a translation.
            } finally {
                connection?.disconnect()
            }
            destination.delete()
            Thread.sleep(500L * (attempt + 1))
        }
        return false
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
