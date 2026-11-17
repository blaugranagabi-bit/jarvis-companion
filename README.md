# JARVIS Companion

A JARVIS-inspired Android voice assistant and companion interface.

## Native Android app

The Android project is in `android-app/`. It targets Android 8.0+ and uses an offline openWakeWord “Hey Jarvis” detector. No Picovoice account or access key is required.

### Current phone actions

After saying “Hey Jarvis”, the assistant can:
- Tell the time and date
- Open supported apps (YouTube, Maps, Camera, Chrome, Gmail, Calendar, Photos, Spotify, WhatsApp, Telegram, TikTok, Instagram, Revolut and ChatGPT, where installed)
- Open Android settings and Bluetooth / internet / sound / display / location panels
- Adjust media volume and toggle the flashlight
- Prepare an alarm or timer in the clock app
- Open the dialler with a number ready
- Prepare an SMS draft for review
- Open navigation directions in Google Maps
- Open a new calendar event draft
- Search the web or prepare a question for ChatGPT

### Build

The GitHub Actions workflow builds a debug APK on pushes to `android-app/**` and uploads it as the `jarvis-android-debug` artifact.

### Important limitations and privacy

Wake-word detection is local. Speech command recognition currently uses the Android speech-recognition service; offline recognition depends on an installed offline language pack and device support. The ChatGPT command opens ChatGPT with a prepared query; it does not have silent access to a user's ChatGPT account or send messages in the background.

Calls and texts open the system dialler or messaging app for user confirmation. Calendar events open an editable draft. Protected Android settings are opened as system panels because ordinary apps cannot directly change every protected setting. Microphone use requires the foreground notification to remain active. Android and manufacturer battery controls may restrict background listening.

The spoken voice uses Android's UK English text-to-speech engine with a slower pace and slightly lower pitch. The exact voice depends on the speech engine and voices installed on the device; this project does not redistribute an unauthorised actor voice clone.

## Web companion

The repository also contains the browser-based companion interface. It is a separate interface prototype and does not have direct access to the native Android app's permissions or device controls.
