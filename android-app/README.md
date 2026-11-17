# JARVIS Android companion — v0.3.0

Native Android prototype with on-device **openWakeWord** detection.

## Install and use
1. Download the `jarvis-android-debug` APK from the latest successful GitHub Actions run.
2. Install it on your Android phone.
3. Open JARVIS and tap **ACTIVATE JARVIS**.
4. Grant microphone permission (and notification permission when requested).
5. Say **“Hey Jarvis”**. JARVIS responds “Yes, sir?” using Android text-to-speech.

No Picovoice account, company email, AccessKey, or cloud connection is required for wake-word detection. The wake-word engine processes microphone audio locally on the phone.

## Current limitations
- This version implements offline wake-word detection and a spoken acknowledgement only.
- It does not yet capture a follow-up natural-language command or connect to an AI conversation backend.
- A secure AI service requires a separately provisioned backend and credentials; never embed an AI provider secret in the APK.
- The bundled openWakeWord pre-trained model is licensed CC BY-NC-SA 4.0. The library code is Apache-2.0. Review model licensing before any commercial distribution.
- Android microphone foreground-service restrictions, battery optimisation and Xiaomi background-management policies can affect locked-screen operation. The user must start listening while the app is visible and keep the persistent notification enabled.

## Build
Open this directory in Android Studio or use the repository GitHub Actions workflow. Application ID: `com.ragnar.jarvis`.
