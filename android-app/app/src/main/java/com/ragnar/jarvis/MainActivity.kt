package com.ragnar.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private var demoTts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        demoTts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                demoTts?.language = Locale.UK
                demoTts?.setSpeechRate(0.88f)
                demoTts?.setPitch(0.92f)
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 42, 28, 36)
            setBackgroundColor(0xFF050B14.toInt())
        }
        val title = TextView(this).apply {
            text = "J.A.R.V.I.S."
            textSize = 32f
            letterSpacing = 0.28f
            setTextColor(0xFF64D9FF.toInt())
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "PERSONAL VOICE COMPANION  •  v0.5"
            textSize = 11f
            letterSpacing = 0.12f
            setTextColor(0xFF8BA9C1.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 28)
        }
        status = TextView(this).apply {
            text = "SYSTEM STANDBY"
            textSize = 14f
            letterSpacing = 0.08f
            setTextColor(0xFF64D9FF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }
        val start = Button(this).apply {
            text = "ACTIVATE JARVIS"
            setOnClickListener { requestAndStart() }
        }
        val stop = Button(this).apply {
            text = "STAND DOWN"
            setOnClickListener {
                stopService(Intent(this@MainActivity, WakeWordService::class.java))
                status.text = "VOICE SYSTEM STOPPED"
            }
        }
        val testVoice = Button(this).apply {
            text = "TEST JARVIS VOICE"
            setOnClickListener {
                demoTts?.speak("At your service, sir. All systems are standing by.", TextToSpeech.QUEUE_FLUSH, null, "jarvis-demo")
            }
        }
        val guide = TextView(this).apply {
            text = """\nHOW TO USE

1. Tap ACTIVATE JARVIS and grant microphone, notification and camera permissions.
2. Say “Hey Jarvis”.
3. Wait for “Yes, sir?” and then speak your command.

PHONE COMMANDS

• “Turn on/off the flashlight”
• “Volume up”, “volume down”, “mute”
• “Set an alarm for 7 AM”
• “Set a timer for 10 minutes”
• “Call [phone number]” — opens the dialler for confirmation
• “Text [phone number] saying [message]” — opens a draft
• “Open YouTube / WhatsApp / Telegram / Spotify / Chrome / Gmail / TikTok / ChatGPT”
• “Open Bluetooth settings”, “open internet settings”
• “Open sound / display / location settings”
• “Open camera”, “open maps”
• “Navigate to [destination]”
• “Create a calendar event [title]”
• “Search for [something]”
• “Ask ChatGPT [your question]”
• “Stand down”

LIMITATIONS & PRIVACY

Wake-word detection runs locally. Spoken command recognition uses the Android speech service; offline recognition depends on an installed English (UK) offline pack. Calls and messages open the relevant app for you to review and confirm. Calendar events open a draft for you to complete. “Ask ChatGPT” opens ChatGPT; it does not silently access your account. Protected Android controls may open a system panel instead of changing settings directly.

Keep the JARVIS notification active while listening. Android and Xiaomi battery controls may restrict background microphone use."""
            textSize = 13f
            setTextColor(0xFFB8D7E8.toInt())
            setPadding(4, 26, 4, 0)
            setLineSpacing(5f, 1f)
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(status)
        root.addView(start, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(stop, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(testVoice, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(guide)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun requestAndStart() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 41) else startWakeService()
    }

    private fun startWakeService() {
        val intent = Intent(this, WakeWordService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        status.text = "STARTING — SAY “HEY JARVIS”"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startWakeService()
        } else status.text = "MICROPHONE PERMISSION IS REQUIRED"
    }

    override fun onDestroy() {
        demoTts?.stop()
        demoTts?.shutdown()
        demoTts = null
        super.onDestroy()
    }
}
