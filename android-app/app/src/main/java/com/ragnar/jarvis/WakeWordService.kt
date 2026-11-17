package com.ragnar.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioFormat
import android.media.MediaRecorder
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WakeWordService : Service() {
    private var detector: JarvisWakeWordDetector? = null
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var started = false
    private var capturingCommand = false
    private var torchOn = false
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
                        val reason = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Speech service client error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission missing"
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service network unavailable"
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech recognised"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy"
                            SpeechRecognizer.ERROR_SERVER -> "Speech service server error"
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "English speech pack unavailable"
                            else -> "Speech recognition error $error"
                        }
                        updateNotification("$reason — say Hey Jarvis to retry")
                        speak("I could not understand that, sir. $reason.")
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
        if (started) return
        started = true
        updateNotification("Checking microphone access…")
        Thread({
            val diagnostic = testMicrophoneCapture()
            mainHandler.post {
                if (!started) return@post
                if (diagnostic != null) {
                    started = false
                    updateNotification("Microphone test failed: $diagnostic")
                    speak("I cannot access the microphone, sir. $diagnostic")
                    return@post
                }
                updateNotification("Microphone test passed — starting wake-word engine…")
                startWakeWordDetector()
            }
        }, "JarvisMicCheck").start()
    }

    // Verify Android can actually open and read the microphone before starting
    // the wake-word library. Its public API does not report AudioRecord failures.
    private fun testMicrophoneCapture(): String? {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return "Microphone permission is not granted"
        }
        val sampleRate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return "This device rejected 16 kHz microphone capture (code $minBuffer)"
        var recorder: AudioRecord? = null
        return try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, 4096)
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                "Android could not initialise the microphone recorder"
            } else {
                recorder.startRecording()
                if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    "Android did not enter microphone recording mode"
                } else {
                    val samples = ShortArray(1280)
                    val read = recorder.read(samples, 0, samples.size)
                    if (read <= 0) "Microphone read failed (code $read)" else null
                }
            }
        } catch (e: SecurityException) {
            "Android denied microphone access"
        } catch (e: Exception) {
            "Microphone error: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
        } finally {
            try { recorder?.stop() } catch (_: Exception) {}
            try { recorder?.release() } catch (_: Exception) {}
        }
    }

    private fun startWakeWordDetector() {
        try {
            detector?.release()
            detector = JarvisWakeWordDetector.Builder(applicationContext)
                .setModel(JarvisWakeWordDetector.BuiltInModel.HEY_JARVIS)
                .setThreshold(0.35f)
                .setDebounceMs(2500L)
                .build()
            detector?.start({
                mainHandler.post {
                    if (!capturingCommand) {
                        capturingCommand = true
                        updateNotification("JARVIS activated")
                        speak("Yes, sir?")
                        mainHandler.postDelayed({ listenForCommand() }, 650)
                    }
                }
            }, { engineStatus ->
                updateNotification(engineStatus)
                if (engineStatus.startsWith("Engine error:") || engineStatus.contains("could not initialise") || engineStatus.contains("failed")) {
                    speak("Wake word diagnostic. $engineStatus")
                }
            })
            updateNotification("Wake-word engine initialising")
        } catch (e: Exception) {
            started = false
            updateNotification("Wake-word startup failed: ${e.message ?: e.javaClass.simpleName}")
            speak("The wake word engine failed to start, sir.")
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
                // Do not force offline mode: many devices lack the required offline speech pack.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
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
            command.contains("time") -> speak("The time is ${SimpleDateFormat("h:mm a", Locale.UK).format(Date())}, sir.")
            command.contains("date") || command.contains("day is it") -> speak("Today is ${SimpleDateFormat("EEEE, d MMMM", Locale.UK).format(Date())}, sir.")
            command.contains("flashlight") || command.contains("flash light") || command.contains("torch") -> setTorch(!torchOn)
            command.contains("volume up") || command.contains("turn it up") -> changeVolume(AudioManager.ADJUST_RAISE)
            command.contains("volume down") || command.contains("turn it down") -> changeVolume(AudioManager.ADJUST_LOWER)
            command.contains("mute") -> changeVolume(AudioManager.ADJUST_MUTE)
            command.contains("unmute") -> changeVolume(AudioManager.ADJUST_UNMUTE)
            command.startsWith("set an alarm") || command.startsWith("set alarm") -> setAlarm(command)
            command.startsWith("set a timer") || command.startsWith("set timer") -> setTimer(command)
            command.contains("open bluetooth") || command.contains("bluetooth settings") -> openSettingsPanel("android.settings.panel.action.BLUETOOTH")
            command.contains("open internet") || command.contains("wifi settings") || command.contains("wi-fi settings") -> openSettingsPanel("android.settings.panel.action.INTERNET_CONNECTIVITY")
            command.contains("open sound settings") || command.contains("sound settings") -> launch(Intent(Settings.ACTION_SOUND_SETTINGS), "Opening sound settings, sir.")
            command.contains("open display settings") || command.contains("brightness settings") -> launch(Intent(Settings.ACTION_DISPLAY_SETTINGS), "Opening display settings, sir.")
            command.contains("open location settings") || command.contains("gps settings") -> launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), "Opening location settings, sir.")
            command.contains("open app settings") -> launch(Intent(Settings.ACTION_APPLICATION_SETTINGS), "Opening application settings, sir.")
            command.contains("open settings") -> launch(Intent(Settings.ACTION_SETTINGS), "Opening settings, sir.")
            command.startsWith("call ") -> dialNumber(command.removePrefix("call ").filter { it.isDigit() || it == '+' })
            command.startsWith("text ") || command.startsWith("send a text to ") -> composeText(command)
            command.startsWith("navigate to ") || command.startsWith("directions to ") -> navigate(command.substringAfter(" to ").trim())
            command.startsWith("create a calendar event") || command.startsWith("add calendar event") -> createCalendarEvent(command)
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
            command.contains("open youtube") -> openAppOrWeb("com.google.android.youtube", "https://www.youtube.com", "Opening YouTube, sir.")
            command.contains("open maps") || command.contains("open google maps") -> openAppOrWeb("com.google.android.apps.maps", "https://maps.google.com", "Opening Maps, sir.")
            command.contains("open camera") -> launch(Intent("android.media.action.IMAGE_CAPTURE"), "Opening the camera, sir.")
            command.startsWith("open ") -> openNamedApp(command.removePrefix("open ").trim())
            command.contains("stop listening") || command.contains("stand down") -> {
                speak("Standing down, sir.")
                stopSelf()
            }
            else -> speak("I can control the flashlight and volume, set alarms and timers, open apps and settings, prepare calls or texts, create calendar events, navigate, or search the web. What would you like, sir?")
        }
    }

    private fun setTorch(enabled: Boolean) {
        try {
            val manager = getSystemService(CameraManager::class.java)
            val cameraId = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            if (cameraId == null) { speak("This device does not appear to have a camera flash, sir."); return }
            manager.setTorchMode(cameraId, enabled)
            torchOn = enabled
            speak(if (enabled) "Flashlight on, sir." else "Flashlight off, sir.")
        } catch (_: SecurityException) {
            speak("Please grant camera permission to JARVIS to control the flashlight, sir.")
            launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (_: Exception) { speak("I could not change the flashlight, sir.") }
    }

    private fun changeVolume(direction: Int) {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        speak("Volume adjusted, sir.")
    }

    private fun setAlarm(command: String) {
        val match = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(command)
        if (match == null) { speak("Please say the alarm time, for example, set an alarm for seven thirty AM."); return }
        var hour = match.groupValues[1].toIntOrNull() ?: 7
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        when (match.groupValues[3]) {
            "pm" -> if (hour < 12) hour += 12
            "am" -> if (hour == 12) hour = 0
        }
        if (hour !in 0..23 || minute !in 0..59) { speak("That time does not look valid, sir."); return }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute).putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS alarm")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        launch(intent, "Setting the alarm for ${String.format(Locale.UK, "%02d:%02d", hour, minute)}, sir.")
    }

    private fun setTimer(command: String) {
        val amount = Regex("(\\d+)").find(command)?.value?.toIntOrNull()
        if (amount == null || amount <= 0) { speak("Please specify a timer duration in seconds or minutes, sir."); return }
        val seconds = if (command.contains("hour")) amount * 3600 else if (command.contains("second")) amount else amount * 60
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS timer").putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        launch(intent, "Setting a timer for $amount ${if (command.contains("hour")) "hours" else if (command.contains("second")) "seconds" else "minutes"}, sir.")
    }

    private fun dialNumber(number: String) {
        if (number.length < 3) { speak("Please say the telephone number after the word call, sir."); return }
        launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")), "Opening the dialler. Please confirm the call, sir.")
    }

    private fun composeText(command: String) {
        val number = Regex("(\\+?[0-9][0-9 -]{5,})").find(command)?.value?.replace(" ", "")?.replace("-", "")
        val body = command.substringAfter(" saying ", "").ifBlank { command.substringAfter(" message ", "") }
        if (number.isNullOrBlank()) {
            speak("Please include the recipient's phone number. I will open a message draft for you to review.")
            launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING), "Opening your messages, sir.")
        } else {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                if (body.isNotBlank()) putExtra("sms_body", body)
            }
            launch(intent, "Opening a message draft. Please review and send it yourself, sir.")
        }
    }

    private fun navigate(destination: String) {
        if (destination.isBlank()) { speak("Please specify a destination, sir."); return }
        val uri = Uri.parse("google.navigation:q=" + Uri.encode(destination))
        launch(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps"), "Preparing directions to $destination, sir.")
    }

    private fun createCalendarEvent(command: String) {
        val title = command.substringAfter("event", "").trim().ifBlank { "New event" }
        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
        launch(intent, "Opening a new calendar event for you to complete, sir.")
    }

    private fun openSettingsPanel(action: String) {
        if (Build.VERSION.SDK_INT >= 29) launch(Intent(action), "Opening the relevant system controls, sir.")
        else launch(Intent(Settings.ACTION_SETTINGS), "Opening settings, sir.")
    }

    private fun openNamedApp(name: String) {
        val aliases = mapOf(
            "phone" to "com.google.android.dialer", "dialler" to "com.google.android.dialer",
            "messages" to "com.google.android.apps.messaging", "gmail" to "com.google.android.gm",
            "chrome" to "com.android.chrome", "calendar" to "com.google.android.calendar",
            "photos" to "com.google.android.apps.photos", "gallery" to "com.miui.gallery",
            "camera" to "com.android.camera", "spotify" to "com.spotify.music",
            "youtube music" to "com.google.android.apps.youtube.music", "chatgpt" to "com.openai.chatgpt",
            "whatsapp" to "com.whatsapp", "telegram" to "org.telegram.messenger",
            "tiktok" to "com.zhiliaoapp.musically", "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana", "revolut" to "com.revolut.revolut",
            "maps" to "com.google.android.apps.maps", "settings" to "com.android.settings"
        )
        val pkg = aliases[name]
        if (pkg == null) { speak("I do not have an application shortcut for $name yet, sir."); return }
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) speak("$name does not appear to be installed, sir.") else launch(intent, "Opening $name, sir.")
    }

    private fun openAppOrWeb(packageName: String, url: String, response: String) {
        val app = packageManager.getLaunchIntentForPackage(packageName)
        launch(app ?: Intent(Intent.ACTION_VIEW, Uri.parse(url)), response)
    }

    private fun launch(intent: Intent, response: String = "One moment, sir.") {
        speak(response)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(intent) } catch (_: Exception) { speak("I could not open that destination, sir.") }
    }

    private fun resumeWakeWord() {
        mainHandler.postDelayed({ if (started && !capturingCommand) startWakeWord() }, 900)
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
