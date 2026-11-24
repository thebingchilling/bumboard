plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    // snapshot screenshots of the UI, made locally only (latinime: -PlycheeScreens)
    alias(libs.plugins.paparazzi) apply false
}
