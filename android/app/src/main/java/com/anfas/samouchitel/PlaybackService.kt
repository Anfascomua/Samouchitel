package com.anfas.samouchitel
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.File
class PlaybackService:MediaSessionService(){var player:ExoPlayer?=null;var session:MediaSession?=null
 override fun onCreate(){super.onCreate();player=ExoPlayer.Builder(this).build().apply{repeatMode=ExoPlayer.REPEAT_MODE_ALL};session=MediaSession.Builder(this,player!!).build()}
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{intent?.getStringExtra("dir")?.let{d->val fs=File(d).listFiles()?.sortedBy{it.name}.orEmpty();player?.setMediaItems(fs.map{MediaItem.fromUri(it.toURI().toString())});player?.prepare();player?.play()};return START_STICKY}
 override fun onGetSession(info:MediaSession.ControllerInfo)=session
 override fun onDestroy(){session?.release();player?.release();super.onDestroy()}
}