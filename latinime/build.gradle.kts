plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    // LatinIME keeps its Java package (the native code registers JNI methods by
    // class name); only the installed application id is BumBoard's.
    namespace = "com.android.inputmethod.latin"
    compileSdk = 37
    ndkVersion = "28.0.13004108"

    defaultConfig {
        applicationId = "app.bumboard"
        minSdk = 24
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = System.getenv("BUILD_VERSION_NAME") ?: "0.1.0-dev"
        vectorDrawables.useSupportLibrary = false
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DBUMBOARD_PREBUILT_DIR=${rootDir.path}/prebuilt"
            }
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("java/AndroidManifest.xml")
            java.srcDir("java/src")
            java.srcDir("common/src")
            res.srcDir("java/res")
            // BumBoard's own code and resources, kept apart from LatinIME's.
            kotlin.srcDir("bumboard/kotlin")
            res.srcDir("bumboard/res")
            // Rime data and readings, built by bumboard/data/build_data.py.
            assets.srcDir("../data/out/assets")
            // BumBoard's own assets (the pack catalogue)
            assets.srcDir("bumboard/assets")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.31.6"
        }
    }

    androidResources {
        // Dictionaries are mmapped by the native code, so they must stay uncompressed.
        noCompress += "dict"
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
    compileOnly(libs.jsr305)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
}
