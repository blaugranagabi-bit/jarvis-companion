# JARVIS Android companion prototype

This native Android project is separate from the GitHub Pages PWA.

## Implemented foundation
- Native launcher activity and microphone permission flow.
- Microphone foreground service with persistent notification and Stop action.
- British English Android text-to-speech.
- Best-effort spoken Jarvis phrase detection through Android SpeechRecognizer.
- One follow-up spoken command, read back by the current prototype.

## Important limitations
This is not yet a production always-on assistant. SpeechRecognizer is a general speech-to-text API, not a dedicated low-power wake-word detector. It may use a network recognition provider, stop after timeouts, consume battery, or be unavailable while another app uses the microphone.

Android requires microphone permission and a user-visible foreground service. On Android 14+, microphone foreground services have while-in-use restrictions; start the service from the visible activity after the user grants permission. Android 15 blocks starting microphone foreground services from BOOT_COMPLETED. Battery optimisation and manufacturer policies can also stop background work.

The prototype does not include a secure AI backend, custom offline wake-word model, reboot recovery, or default-assistant role integration. Never place an AI provider secret in the APK. For reliable low-power wake-word activation, integrate a dedicated on-device wake-word SDK and its trained model, then implement a compliant Android voice-interaction design.

## Build
Open the android-app directory in current Android Studio, allow Gradle sync, then build the app debug APK. Install it on an Android 15 device. Open JARVIS, tap ACTIVATE JARVIS, and grant microphone and notification permissions. A persistent notification indicates listening; its Stop action stops the service.

Application ID: com.ragnar.jarvis
Version: 0.1.0
