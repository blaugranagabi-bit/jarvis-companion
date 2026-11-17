package com.ragnar.jarvis

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import java.util.Locale

class WakeWordService : Service() {
    private var porcupine: PorcupineManager? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Initialising JARVIS wake-word engine"))
        tts = TextToSpeech(this) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.UK
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val accessKey = intent?.getStringExtra(EXTRA_ACCESS_KEY).orEmpty()
        if (accessKey.isBlank()) {
            updateNotification("Picovoice AccessKey required — open JARVIS")
            speak("Sir, a Picovoice access key is required to activate the wake word.")
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            porcupine?.delete()
            porcupine = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                .build(applicationContext) {
                    updateNotification("JARVIS detected — listening for your command")
                    speak("Yes, sir?")
                }
            porcupine?.start()
            updateNotification("Offline wake-word detection active — say Jarvis")
        } catch (e: Exception) {
            updateNotification("Wake engine error: check AccessKey")
            speak("I could not initialise the wake word engine, sir.")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-voice")
    }

    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
            else Notification.Builder(this)
        return builder.setContentTitle("JARVIS voice assistant")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(message))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "JARVIS voice listening", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try { porcupine?.stop() } catch (_: Exception) {}
        porcupine?.delete()
        porcupine = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_ACCESS_KEY = "com.ragnar.jarvis.ACCESS_KEY"
        private const val CHANNEL_ID = "jarvis_voice"
        private const val NOTIFICATION_ID = 7001
        private const val ACTION_STOP = "com.ragnar.jarvis.STOP"
    }
}
