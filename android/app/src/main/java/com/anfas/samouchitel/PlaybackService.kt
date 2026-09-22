package com.anfas.samouchitel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/** Keeps the currently selected drill list playing after the phone screen turns off. */
class PlaybackService : Service() {
    companion object {
        private const val CHANNEL_ID = "drill_audio"
        private const val NOTIFICATION_ID = 41
    }

    private var player: ExoPlayer? = null
    private var wordStarts: List<Int> = emptyList()

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Зубрёжка в фоне", NotificationManager.IMPORTANCE_LOW)
        )
        player = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = refreshNotification()
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "pause" -> player?.pause()
            "resume" -> player?.play()
            "next" -> seekWord(1)
            "previous" -> seekWord(-1)
            "stop" -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            else -> intent?.getStringExtra("dir")?.let(::startPlaylist)
        }
        return START_STICKY
    }

    private fun startPlaylist(directory: String) {
        val files = File(directory).listFiles()?.sortedBy { it.name }.orEmpty()
        wordStarts = files.mapIndexedNotNull { index, file -> index.takeIf { file.name.contains("-en.") } }
        if (files.isEmpty()) { stopSelf(); return }
        startForeground(NOTIFICATION_ID, notification())
        player?.setMediaItems(files.map { MediaItem.fromUri(it.toURI().toString()) })
        player?.prepare()
        player?.play()
        refreshNotification()
    }

    private fun seekWord(direction: Int) {
        val playback = player ?: return
        if (playback.mediaItemCount == 0 || wordStarts.isEmpty()) return
        val current = playback.currentMediaItemIndex.coerceAtLeast(0)
        val target = if (direction > 0) wordStarts.firstOrNull { it > current } ?: wordStarts.first()
        else wordStarts.lastOrNull { it < current } ?: wordStarts.last()
        playback.seekTo(target, 0)
        playback.play()
    }

    private fun refreshNotification() { startForeground(NOTIFICATION_ID, notification()) }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle("Самоучитель: зубрёжка")
        .setContentText(if (player?.isPlaying == true) "Воспроизведение в фоне" else "Пауза")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .addAction(android.R.drawable.ic_media_previous, "Предыдущее", controlIntent("previous"))
        .addAction(if (player?.isPlaying == true) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (player?.isPlaying == true) "Пауза" else "Продолжить", controlIntent(if (player?.isPlaying == true) "pause" else "resume"))
        .addAction(android.R.drawable.ic_media_next, "Следующее", controlIntent("next"))
        .build()

    private fun controlIntent(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(), Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { player?.release(); super.onDestroy() }
}
