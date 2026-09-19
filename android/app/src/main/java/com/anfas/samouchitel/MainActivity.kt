package com.anfas.samouchitel
import android.os.Bundle
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import android.speech.tts.TextToSpeech
import android.content.Intent
import java.io.File
import java.util.*
class MainActivity:AppCompatActivity(),TextToSpeech.OnInitListener{
 lateinit var web:WebView; lateinit var tts:TextToSpeech; var ready=false
 override fun onCreate(b:Bundle?){super.onCreate(b);tts=TextToSpeech(this,this);web=WebView(this);setContentView(web);web.settings.javaScriptEnabled=true;web.settings.domStorageEnabled=true;web.settings.mediaPlaybackRequiresUserGesture=false;web.addJavascriptInterface(Bridge(),"AndroidAudio");web.webViewClient=WebViewClient();web.loadUrl("https://samouchitel.pages.dev")}
 override fun onInit(s:Int){ready=s==TextToSpeech.SUCCESS;if(ready)tts.language=Locale.US}
 inner class Bridge{@JavascriptInterface fun available()=true
  @JavascriptInterface fun buildAndPlay(json:String){runOnUiThread{if(!ready)return@runOnUiThread;val parts=json.split("\u001f");val dir=File(cacheDir,"drill").apply{deleteRecursively();mkdirs()};synth(parts,0,dir)}}
 }
 fun synth(parts:List<String>,i:Int,dir:File){if(i>=parts.size){startService(Intent(this,PlaybackService::class.java).putExtra("dir",dir.absolutePath));return};val f=File(dir,"%04d.wav".format(i));tts.setOnUtteranceProgressListener(object:android.speech.tts.UtteranceProgressListener(){override fun onStart(id:String){};override fun onError(id:String){synth(parts,i+1,dir)};override fun onDone(id:String){runOnUiThread{synth(parts,i+1,dir)}}});tts.synthesizeToFile(parts[i],Bundle(),f,"u$i")}
 override fun onDestroy(){tts.shutdown();super.onDestroy()}
}