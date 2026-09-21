package com.anfas.samouchitel

import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.File

class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply { repeatMode = ExoPlayer.REPEAT_MODE_ALL }
        session = MediaSession.Builder(this, player!!).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "pause" -> player?.pause()
            "resume" -> player?.play()
            "next" -> player?.seekToNextMediaItem()
            "previous" -> player?.seekToPreviousMediaItem()
            else -> intent?.getStringExtra("dir")?.let { directory ->
                val files = File(directory).listFiles()?.sortedBy { it.name }.orEmpty()
                player?.setMediaItems(files.map { MediaItem.fromUri(it.toURI().toString()) })
                player?.prepare()
                player?.play()
            }
        }
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    override fun onDestroy() { session?.release(); player?.release(); super.onDestroy() }
}
