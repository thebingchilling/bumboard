// Lychee's Google handwriting companion: ML Kit's digital ink recognition in a small app of its
// own (ML Kit downloads its handwriting models, so it needs the internet; Lychee never does).
// Lychee hands it the strokes written on its pad through a bound service and gets the guesses
// back. Built once and only rebuilt when this module changes (build.yml).
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.lychee.hwgoogle"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.lychee.hwgoogle"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // Lychee's key: the service only answers apps signed like it (a signature permission)
        getByName("debug") {
            storeFile = file("../latinime/lychee/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.mlkit.digital.ink)
}
