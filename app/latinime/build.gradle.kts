plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    // LatinIME keeps its Java package (the native code registers JNI methods by
    // class name); only the installed application id is Lychee's.
    namespace = "com.android.inputmethod.latin"
    compileSdk = 37
    ndkVersion = "28.0.13004108"

    defaultConfig {
        applicationId = "app.lychee"
        minSdk = 24
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = System.getenv("BUILD_VERSION_NAME") ?: "0.1.0-dev"
        // the Google handwriting companion's size (build.yml reads it off its release)
        resValue("string", "lychee_companion_size", System.getenv("COMPANION_SIZE") ?: "about 20 MB")
        vectorDrawables.useSupportLibrary = false
        externalNativeBuild {
            cmake {
                arguments += "-DLYCHEE_PREBUILT_DIR=${rootDir.path}/prebuilt"
                // the native code built small and optimised in every build, the debug ones
                // too (they are the ones published)
                arguments += "-DCMAKE_BUILD_TYPE=MinSizeRel"
                cppFlags += listOf("-ffunction-sections", "-fdata-sections")
                arguments += "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--gc-sections"
            }
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("java/AndroidManifest.xml")
            java.srcDir("java/src")
            java.srcDir("common/src")
            res.srcDir("java/res")
            // Lychee's own code and resources, kept apart from LatinIME's.
            kotlin.srcDir("lychee/kotlin")
            res.srcDir("lychee/res")
            // Rime data and readings, built by lychee/data/build_data.py.
            assets.srcDir("../data/out/assets")
            // Lychee's own assets (the pack catalogue)
            assets.srcDir("lychee/assets")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.31.6"
        }
    }

    androidResources {
        // Dictionaries are mmapped by the native code, so they must stay uncompressed (the
        // handwriting models are read into memory, so they are compressed like the rest).
        noCompress += listOf("dict")
    }

    packaging {
        // native libraries compressed in the APK (unpacked when installed), not stored as they
        // are for loading straight from it: a much smaller download
        jniLibs {
            useLegacyPackaging = true
            // phones only: the emulators' x86 libraries some dependencies bring (ONNX Runtime's
            // were 30 MB of the universal APK) are left out
            excludes += listOf("lib/x86/**", "lib/x86_64/**")
        }
    }

    // phones only: 64-bit and 32-bit ARM (no x86, which only emulators and a few old tablets
    // use). Three APKs from each build: one for each (each with only its own native libraries,
    // much smaller) and a universal one with both
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    signingConfigs {
        // A fixed debug key kept in the repository, so every CI build installs over the
        // previous one (each runner would otherwise sign with a fresh random debug key).
        // Test builds only: a release key must come from a secret.
        getByName("debug") {
            storeFile = file("lychee/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "java/proguard.flags")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.viewpager)
    implementation(libs.androidx.recyclerview)
    // Custom Tabs: pack pages open in the browser, which does the downloading
    implementation(libs.androidx.browser)
    implementation(libs.androidx.annotation)
    // runs the handwriting recognisers (PP-OCRv5, scripts/fetch-ocr.sh)
    implementation(libs.onnxruntime.android)
    compileOnly(libs.jsr305)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    testImplementation(libs.junit)
}

// Screenshots of the UI, made locally to look at before committing UI changes (Paparazzi draws
// the real screens on the computer, no emulator or phone): ./gradlew :latinime:recordPaparazziDebug
// -PlycheeScreens. The snapshot tests live in screens/ and are left out of every other build.
if (project.hasProperty("lycheeScreens")) {
    apply(plugin = "app.cash.paparazzi")
    android.sourceSets.getByName("test").kotlin.srcDir("screens/kotlin")
    // each snapshot test in a fresh process: Lychee's helpers keep the first context they're given
    tasks.withType<Test>().configureEach { forkEvery = 1 }
}
