# JARVIS Android companion — v0.2.0

Native Android prototype with an on-device Porcupine wake-word engine.

## Wake-word setup
1. Install the APK built by GitHub Actions.
2. Create a Picovoice Console account at https://console.picovoice.ai/ and copy your AccessKey.
3. Open JARVIS, paste the key, and tap ACTIVATE JARVIS.
4. Grant microphone and notification permissions.
5. The app uses Porcupine's built-in English JARVIS keyword and replies "Yes, sir?" using Android text-to-speech.

Porcupine requires a valid AccessKey. The key is supplied locally at runtime, not committed to source code or saved by this prototype. Initial key validation requires internet access; wake-word inference runs on-device.

## Current limitations
- This version implements offline wake-word detection and a spoken acknowledgement only.
- It does not yet capture a follow-up natural-language command or connect to an AI conversation backend.
- A secure AI service requires a separately provisioned backend and credentials; never embed an AI provider secret in the APK.
- Android microphone foreground-service restrictions, battery optimisation and Xiaomi background-management policies can affect locked-screen operation. The user must start listening while the app is visible and keep the persistent notification enabled.
- A future release should add the Android voice-interaction role, command capture, secure backend, and Xiaomi-specific battery/background setup guidance.

## Build
Open this directory in Android Studio or use the repository GitHub Actions workflow. Application ID: `com.ragnar.jarvis`.
