// Pure Kotlin logic shared by the keyboard: romanisation, reading/meaning selection.
// No Android dependencies, so it is unit-tested on the JVM.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
    // tests read the real readings.db when lychee/data/build_data.py has built it
    testImplementation(libs.sqlite.jdbc)
}

tasks.test {
    systemProperty("lychee.readingsDb",
        rootDir.resolve("data/out/assets/lychee/readings.db").canonicalPath)
}
