package com.anfas.samouchitel

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.File
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
            tts.setSpeechRate(rate.coerceIn(0.5f, 1.5f))
            synthesize(words, 0, directory)
        }
        @JavascriptInterface fun stop() = runOnUiThread {
            tts.stop()
            stopService(Intent(this@MainActivity, PlaybackService::class.java))
        }
    }

    private fun synthesize(parts: List<String>, index: Int, directory: File) {
        if (index >= parts.size) {
            startForegroundService(Intent(this, PlaybackService::class.java).putExtra("dir", directory.absolutePath))
            return
        }
        val file = File(directory, "%04d.wav".format(index))
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String) = Unit
            override fun onError(id: String) = runOnUiThread { synthesize(parts, index + 1, directory) }
            override fun onDone(id: String) = runOnUiThread { synthesize(parts, index + 1, directory) }
        })
        tts.synthesizeToFile(parts[index], Bundle(), file, "drill-$index")
    }

    override fun onDestroy() { tts.shutdown(); super.onDestroy() }
}
