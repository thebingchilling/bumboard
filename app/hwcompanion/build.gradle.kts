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
        versionCode = 3
        versionName = "1.2"
        // 64-bit ARM only, every phone of recent years: ML Kit's engine is a native library for
        // each kind of processor, so each one more makes the download bigger (32-bit-only phones
        // keep Lychee's built-in engine)
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    packaging {
        // the native libraries compressed in the APK (unpacked when installed): a much smaller download
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        // what is shipped: the unused code of Google's libraries stripped out (their own keep
        // rules come with them), signed with Lychee's key like the debug build
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
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
