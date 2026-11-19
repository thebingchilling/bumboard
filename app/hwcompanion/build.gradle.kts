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
        versionCode = 8
        versionName = "1.7"
        // one APK for every phone: 64-bit and 32-bit ARM (ML Kit's engine is a native library
        // for each kind of processor; the emulators' x86 ones are left out)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    packaging {
        // the native libraries compressed in the APK (unpacked when installed): a much smaller download
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        // what is shipped: built whole, as the first companion that worked was. R8's shrinking
        // (code and resources) broke ML Kit, which finds its classes and resources by name, so
        // nothing is stripped; signed with Lychee's key like the debug build
        release {
            isMinifyEnabled = false
            isShrinkResources = false
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
