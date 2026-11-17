package com.ragnar.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.openwakeword.OpenWakeWord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WakeWordService : Service() {
    private var detector: OpenWakeWord? = null
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var started = false
    private var capturingCommand = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Starting offline wake-word engine"))
        tts = TextToSpeech(this) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.UK
                tts?.setSpeechRate(0.88f)
                tts?.setPitch(0.92f)
            }
        }
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) { updateNotification("Listening for your command…") }
                    override fun onBeginningOfSpeech() { updateNotification("Hearing you…") }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { updateNotification("Processing command…") }
                    override fun onError(error: Int) {
                        capturingCommand = false
                        speak("I beg your pardon, sir. I did not catch that.")
                        resumeWakeWord()
                    }
                    override fun onResults(results: android.os.Bundle?) {
                        capturingCommand = false
                        val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if (phrase.isBlank()) speak("I did not catch that, sir.") else handleCommand(phrase)
                        resumeWakeWord()
                    }
                    override fun onPartialResults(partialResults: android.os.Bundle?) {}
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (started) return START_STICKY
        startWakeWord()
        return START_STICKY
    }

    private fun startWakeWord() {
        try {
            detector?.release()
            detector = OpenWakeWord.Builder(applicationContext)
                .setModel(OpenWakeWord.BuiltInModel.HEY_JARVIS)
                .setThreshold(0.5f)
                .setDebounceMs(2500L)
                .build()
            detector?.start { score ->
                mainHandler.post {
                    if (!capturingCommand) {
                        capturingCommand = true
                        updateNotification("JARVIS activated")
                        speak("Yes, sir?")
                        mainHandler.postDelayed({ listenForCommand() }, 650)
                    }
                }
            }
            started = true
            updateNotification("Listening offline — say Hey Jarvis")
        } catch (e: Exception) {
            updateNotification("Could not start wake-word engine")
            speak("I could not start the wake word engine, sir.")
            stopSelf()
        }
    }

    private fun listenForCommand() {
        try {
            detector?.stop()
            if (recognizer == null) {
                speak("Voice recognition is unavailable on this device, sir.")
                resumeWakeWord()
                return
            }
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-GB")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            recognizer?.startListening(request)
        } catch (e: Exception) {
            capturingCommand = false
            speak("Voice recognition could not be started, sir.")
            resumeWakeWord()
        }
    }

    private fun handleCommand(raw: String) {
        val command = raw.lowercase(Locale.UK).trim()
        when {
            command.contains("time") -> {
                val time = SimpleDateFormat("h:mm a", Locale.UK).format(Date())
                speak("The time is $time, sir.")
            }
            command.contains("date") || command.contains("day is it") -> {
                val date = SimpleDateFormat("EEEE, d MMMM", Locale.UK).format(Date())
                speak("Today is $date, sir.")
            }
            command.contains("open youtube") -> openAppOrWeb("com.google.android.youtube", "https://www.youtube.com", "Opening YouTube, sir.")
            command.contains("open maps") || command.contains("open google maps") -> openAppOrWeb("com.google.android.apps.maps", "https://maps.google.com", "Opening Maps, sir.")
            command.contains("open settings") -> {
                speak("Opening settings, sir.")
                launch(Intent(android.provider.Settings.ACTION_SETTINGS))
            }
            command.startsWith("search for ") || command.startsWith("google ") -> {
                val query = command.removePrefix("search for ").removePrefix("google ").trim()
                speak("Searching the web for $query, sir.")
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
            }
            command.startsWith("ask chatgpt ") || command.startsWith("ask chat gpt ") -> {
                val prompt = command.removePrefix("ask chatgpt ").removePrefix("ask chat gpt ").trim()
                speak("Preparing that for ChatGPT, sir.")
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/?q=" + Uri.encode(prompt))))
            }
            command.contains("stop listening") || command.contains("stand down") -> {
                speak("Standing down, sir.")
                stopSelf()
            }
            else -> {
                speak("I can tell you the time or date, open YouTube, Maps or settings, search the web, or prepare a question for ChatGPT. What would you like, sir?")
            }
        }
    }

    private fun openAppOrWeb(packageName: String, url: String, response: String) {
        speak(response)
        val app = packageManager.getLaunchIntentForPackage(packageName)
        launch(app ?: Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(intent) } catch (_: Exception) { speak("I could not open that destination, sir.") }
    }

    private fun resumeWakeWord() {
        mainHandler.postDelayed({
            if (started && !capturingCommand) startWakeWord()
        }, 900)
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-voice")
    }

    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("JARVIS voice assistant").setContentText(message)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
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
        started = false
        try { detector?.stop() } catch (_: Exception) {}
        detector?.release()
        detector = null
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "jarvis_voice"
        private const val NOTIFICATION_ID = 7001
        private const val ACTION_STOP = "com.ragnar.jarvis.STOP"
    }
}
