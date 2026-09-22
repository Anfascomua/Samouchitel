package com.anfas.samouchitel

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var web: WebView
    private lateinit var tts: TextToSpeech
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        web.addJavascriptInterface(Bridge(), "AndroidAudio")
        web.webViewClient = WebViewClient()
        web.loadUrl("https://samouchitel.pages.dev")
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) tts.language = Locale.US
    }

    inner class Bridge {
        @JavascriptInterface fun available() = ready
        @JavascriptInterface fun buildAndPlay(sequence: String, rate: Float) = runOnUiThread {
            if (!ready) return@runOnUiThread
            val words = sequence.split("\u001f").filter { it.isNotBlank() }
            val directory = File(cacheDir, "drill").apply { deleteRecursively(); mkdirs() }
            tts.language = Locale.US
            tts.setSpeechRate((rate * 0.8f).coerceIn(0.5f, 1.2f))
            synthesize(words, 0, directory)
        }
        @JavascriptInterface fun stop() = runOnUiThread {
            tts.stop()
            stopService(Intent(this@MainActivity, PlaybackService::class.java))
        }
        @JavascriptInterface fun speak(text: String, rate: Float) = runOnUiThread {
            if (!ready) return@runOnUiThread
            tts.stop()
            tts.language = Locale.US
            tts.setSpeechRate(rate.coerceIn(0.5f, 1.2f))
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "page-speech")
        }
        @JavascriptInterface fun stopSpeech() = runOnUiThread { tts.stop() }
        @JavascriptInterface fun control(action: String) = runOnUiThread {
            // The service is already running while a drill is playing. Calling it
            // directly keeps controls responsive and avoids starting a second service.
            if (!PlaybackService.controlActive(action)) {
                startService(Intent(this@MainActivity, PlaybackService::class.java).setAction(action))
            }
        }
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
            override fun onDone(id: String) = runOnUiThread { writeSilence(File(directory, "%04d-gap.wav".format(index * 2 + 1))); synthesize(parts, index + 1, directory) }
        })
        tts.synthesizeToFile(parts[index], Bundle(), file, "drill-$index")
    }

    private fun writeSilence(file: File) {
        val sampleRate = 8_000
        val dataSize = sampleRate * 3_000 / 1_000 * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray()).put("fmt ".toByteArray())
        header.putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(dataSize)
        FileOutputStream(file).use { it.write(header.array()); it.write(ByteArray(dataSize)) }
    }

    override fun onDestroy() { tts.shutdown(); super.onDestroy() }
}
