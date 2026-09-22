package com.anfas.samouchitel

import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.File

class PlaybackService : MediaSessionService() {
    companion object {
        @Volatile private var activeService: PlaybackService? = null

        fun controlActive(action: String): Boolean {
            val service = activeService ?: return false
            service.applyControl(action)
            return true
        }
    }

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var wordStarts: List<Int> = emptyList()

    override fun onCreate() {
        super.onCreate()
        activeService = this
        player = ExoPlayer.Builder(this).build().apply { repeatMode = ExoPlayer.REPEAT_MODE_ALL }
        session = MediaSession.Builder(this, player!!).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null) {
            applyControl(action)
        } else {
            intent?.getStringExtra("dir")?.let { directory ->
                val files = File(directory).listFiles()?.sortedBy { it.name }.orEmpty()
                wordStarts = files.mapIndexedNotNull { index, file -> index.takeIf { file.name.endsWith("-en.wav") } }
                player?.setMediaItems(files.map { MediaItem.fromUri(it.toURI().toString()) })
                player?.prepare()
                player?.play()
            }
        }
        return START_STICKY
    }

    private fun applyControl(action: String) {
        when (action) {
            "pause" -> player?.pause()
            "resume" -> player?.play()
            // Jump by English words, regardless of how many translation variants follow.
            "next" -> seekVoice(1)
            "previous" -> seekVoice(-1)
        }
    }

    private fun seekVoice(direction: Int) {
        val playback = player ?: return
        if (playback.mediaItemCount == 0 || wordStarts.isEmpty()) return
        val current = playback.currentMediaItemIndex.coerceAtLeast(0)
        val target = if (direction > 0) {
            wordStarts.firstOrNull { it > current } ?: wordStarts.first()
        } else {
            wordStarts.lastOrNull { it < current } ?: wordStarts.last()
        }
        playback.seekTo(target, 0)
        playback.play()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    override fun onDestroy() {
        activeService = null
        session?.release()
        player?.release()
        super.onDestroy()
    }
}
