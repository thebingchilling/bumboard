plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

// Snapshot screenshots of the UI (Paparazzi), made locally only: its plugin is loaded only with
// -PlycheeScreens (it needs Java 21; CI's builds never load it). See tools/screens.sh.
buildscript {
    if (project.hasProperty("lycheeScreens")) {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath("app.cash.paparazzi:paparazzi-gradle-plugin:2.0.0-alpha05.1")
        }
    }
}
