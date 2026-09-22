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
            // The playlist alternates: voice, 3-second silence, voice…
            // Skip controls must always land on a voice item, not on silence.
            "next" -> seekVoice(1)
            "previous" -> seekVoice(-1)
        }
    }

    private fun seekVoice(direction: Int) {
        val playback = player ?: return
        val count = playback.mediaItemCount
        if (count == 0) return
        val current = playback.currentMediaItemIndex.coerceAtLeast(0)
        val voiceIndex = if (current % 2 == 0) current else current + direction
        var target = voiceIndex + direction * 2
        target = ((target % count) + count) % count
        if (target % 2 != 0) target = (target + direction + count) % count
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
