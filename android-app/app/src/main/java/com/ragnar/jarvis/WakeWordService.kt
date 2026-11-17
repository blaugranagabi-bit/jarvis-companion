package com.ragnar.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Proof-of-concept phrase listener. Android SpeechRecognizer is general ASR,
 * not a low-power always-on hotword DSP. Availability and background behaviour
 * depend on the recognition provider, device and power-management policy.
 */
class WakeWordService : Service(), RecognitionListener {
 private var recognizer:SpeechRecognizer?=null
 private var tts:TextToSpeech?=null
 private var ttsReady=false
 private var awaitingCommand=false
 private var restarting=false

 override fun onCreate() {
  super.onCreate()
  createChannel()
  startForeground(NOTIFICATION_ID,notification("Waiting for Jarvis"))
  tts=TextToSpeech(this) { result ->
   ttsReady=result==TextToSpeech.SUCCESS
   if(ttsReady) tts?.language=Locale.UK
  }
  if(!SpeechRecognizer.isRecognitionAvailable(this)) {
   speak("Speech recognition is unavailable on this device."); stopSelf(); return
  }
  recognizer=if(Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this))
   SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
  else SpeechRecognizer.createSpeechRecognizer(this)
  recognizer?.setRecognitionListener(this)
  listen()
 }

 private fun listen() {
  if(restarting) return
  val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
   putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
   putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-GB")
   putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"en-GB")
   putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3)
   putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false)
  }
  try { recognizer?.startListening(intent) } catch(_:Exception) { restart(1200) }
 }

 override fun onResults(results:Bundle?) {
  val heard=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase(Locale.UK).orEmpty()
  if(awaitingCommand) {
   awaitingCommand=false
   val reply=if(heard.isBlank()) "I didn't catch that, sir. Please try again."
    else "I heard: $heard. My secure AI connection has not been configured yet."
   speak(reply); updateNotification("Waiting for Jarvis")
  } else if(heard.contains("jarvis")) {
   awaitingCommand=true; speak("Yes, sir?")
   updateNotification("Jarvis activated — listening for your command")
  }
  restart(if(awaitingCommand) 650 else 300)
 }
 override fun onError(error:Int) { restart(1200) }
 override fun onReadyForSpeech(params:Bundle?) { updateNotification(if(awaitingCommand) "Listening for command" else "Waiting for Jarvis") }
 override fun onBeginningOfSpeech() {}
 override fun onEndOfSpeech() {}
 override fun onRmsChanged(rmsdB:Float) {}
 override fun onBufferReceived(buffer:ByteArray?) {}
 override fun onPartialResults(partialResults:Bundle?) {}
 override fun onEvent(eventType:Int,params:Bundle?) {}

 private fun restart(delay:Long) {
  restarting=true
  Handler(mainLooper).postDelayed({ restarting=false; listen() },delay)
 }
 private fun speak(text:String) { if(ttsReady) tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"jarvis-voice") }

 private fun notification(message:String):Notification {
  val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val stop=PendingIntent.getService(this,1,Intent(this,WakeWordService::class.java).setAction(ACTION_STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val builder=if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,CHANNEL_ID) else Notification.Builder(this)
  return builder.setContentTitle("JARVIS voice assistant").setContentText(message)
   .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(open).setOngoing(true)
   .addAction(Notification.Action.Builder(null,"Stop",stop).build()).build()
 }
 private fun updateNotification(message:String) {
  getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,notification(message))
 }
 private fun createChannel() {
  if(Build.VERSION.SDK_INT>=26) {
   val channel=NotificationChannel(CHANNEL_ID,"JARVIS voice listening",NotificationManager.IMPORTANCE_LOW)
   getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action==ACTION_STOP) stopSelf()
  return START_NOT_STICKY
 }
 override fun onDestroy() {
  recognizer?.destroy(); recognizer=null
  tts?.stop(); tts?.shutdown(); tts=null
  super.onDestroy()
 }
 override fun onBind(intent:Intent?):IBinder?=null
 companion object {
  private const val CHANNEL_ID="jarvis_voice"
  private const val NOTIFICATION_ID=7001
  private const val ACTION_STOP="com.ragnar.jarvis.STOP"
 }
}
