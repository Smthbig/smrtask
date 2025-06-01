pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        kotlin("android") version "1.9.0" // Make sure this matches your Kotlin version
        id("com.android.application") version "8.2.1" // Latest stable Android Gradle Plugin
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS) // Prefer centralized repo settings
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "smrtask"
include(":app")