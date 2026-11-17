package com.ragnar.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
 private lateinit var status: TextView
 private val requestCode=41
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER
   setPadding(32,48,32,48); setBackgroundColor(0xFF050B14.toInt())
  }
  val title=TextView(this).apply {
   text="J.A.R.V.I.S."; textSize=30f; letterSpacing=.28f
   setTextColor(0xFF64D9FF.toInt()); gravity=Gravity.CENTER
  }
  status=TextView(this).apply {
   text="VOICE SYSTEM STANDBY"; textSize=14f
   setTextColor(0xFFB8D7E8.toInt()); gravity=Gravity.CENTER
   setPadding(0,28,0,28)
  }
  val start=Button(this).apply { text="ACTIVATE JARVIS"; setOnClickListener { requestAndStart() } }
  val stop=Button(this).apply {
   text="STOP LISTENING"
   setOnClickListener { stopService(Intent(this@MainActivity,WakeWordService::class.java)); status.text="VOICE SYSTEM STOPPED" }
  }
  val note=TextView(this).apply {
   text="Activate while this screen is open. Android shows a persistent microphone notification while listening. Say Jarvis to test the wake phrase."
   textSize=13f; setTextColor(0xFF8BA9C1.toInt()); gravity=Gravity.CENTER; setPadding(0,24,0,0)
  }
  root.addView(title); root.addView(status); root.addView(start); root.addView(stop); root.addView(note)
  setContentView(root)
 }
 private fun requestAndStart() {
  val permissions=mutableListOf(Manifest.permission.RECORD_AUDIO)
  if(Build.VERSION.SDK_INT>=33) permissions+=Manifest.permission.POST_NOTIFICATIONS
  val missing=permissions.filter { checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED }
  if(missing.isNotEmpty()) requestPermissions(missing.toTypedArray(),requestCode) else startWakeService()
 }
 private fun startWakeService() {
  val intent=Intent(this,WakeWordService::class.java)
  if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
  status.text="JARVIS IS LISTENING — SAY JARVIS"
 }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
  super.onRequestPermissionsResult(requestCode,permissions,grantResults)
  if(requestCode==this.requestCode) {
   val micGranted=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
   if(micGranted) startWakeService() else status.text="MICROPHONE PERMISSION IS REQUIRED"
  }
 }
}
