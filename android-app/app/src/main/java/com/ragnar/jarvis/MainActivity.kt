package com.ragnar.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var accessKey: EditText
    private var pendingKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 40, 32, 40)
            setBackgroundColor(0xFF050B14.toInt())
        }
        val title = TextView(this).apply {
            text = "J.A.R.V.I.S."
            textSize = 30f
            letterSpacing = 0.28f
            setTextColor(0xFF64D9FF.toInt())
            gravity = Gravity.CENTER
        }
        val intro = TextView(this).apply {
            text = "Offline wake-word engine"
            textSize = 15f
            setTextColor(0xFFB8D7E8.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 12)
        }
        accessKey = EditText(this).apply {
            hint = "Paste your Picovoice AccessKey"
            setSingleLine(true)
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF8BA9C1.toInt())
        }
        status = TextView(this).apply {
            text = "READY — ACCESS KEY REQUIRED"
            textSize = 13f
            setTextColor(0xFFB8D7E8.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }
        val start = Button(this).apply {
            text = "ACTIVATE JARVIS"
            setOnClickListener {
                pendingKey = accessKey.text.toString().trim()
                if (pendingKey.isBlank()) {
                    status.text = "Enter your Picovoice AccessKey first"
                } else requestAndStart()
            }
        }
        val stop = Button(this).apply {
            text = "STOP LISTENING"
            setOnClickListener {
                stopService(Intent(this@MainActivity, WakeWordService::class.java))
                status.text = "VOICE SYSTEM STOPPED"
            }
        }
        val help = TextView(this).apply {
            text = "Get an AccessKey at console.picovoice.ai. The key is passed to the service for this session and is not saved by this app. Wake-word detection runs on-device after initial key validation."
            textSize = 12f
            setTextColor(0xFF8BA9C1.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 22, 0, 0)
        }
        root.addView(title)
        root.addView(intro)
        root.addView(accessKey)
        root.addView(status)
        root.addView(start)
        root.addView(stop)
        root.addView(help)
        setContentView(root)
    }

    private fun requestAndStart() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 41) else startWakeService()
    }

    private fun startWakeService() {
        val intent = Intent(this, WakeWordService::class.java)
            .putExtra(WakeWordService.EXTRA_ACCESS_KEY, pendingKey)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        status.text = "STARTING OFFLINE JARVIS DETECTION…"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startWakeService()
        } else status.text = "MICROPHONE PERMISSION IS REQUIRED"
    }
}
