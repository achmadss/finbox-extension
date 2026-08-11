pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.library") version "9.3.1"
        id("com.android.application") version "9.3.1"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // The parser API published from finbox-android. mavenLocal() first so a
        // locally published build (./gradlew :extension-api:publishToMavenLocal
        // over there) wins while iterating on the API.
        mavenLocal()
        maven("https://jitpack.io")
    }
}

rootProject.name = "finbox-extension"

includeBuild("build-logic")

include(":extensions:bri")
