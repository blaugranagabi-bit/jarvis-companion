plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ragnar.jarvis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ragnar.jarvis"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }
}

dependencies {
    implementation("ai.picovoice:porcupine-android:4.0.2")
}
