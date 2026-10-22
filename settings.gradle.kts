pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BumBoard"

// The keyboard app: AOSP LatinIME (Apache-2.0), modified for BumBoard.
include(":latinime")
// Pure Kotlin: romanisation and choosing the reading/meaning for a candidate.
include(":core")
