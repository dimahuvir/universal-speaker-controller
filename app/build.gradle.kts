plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.usc.speakercontroller"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.usc.speakercontroller"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
}
