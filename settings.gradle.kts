pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.library") version "9.3.1"
        id("com.android.application") version "9.3.1"
        id("org.jetbrains.kotlin.jvm") version "2.4.10"
        id("com.google.devtools.ksp") version "2.3.9"
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

include(":compiler")
include(":lib:receipt")
include(":extensions:bri")
include(":extensions:jago")
