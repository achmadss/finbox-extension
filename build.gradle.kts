// Pins the Kotlin compiler used by AGP's built-in Kotlin support. It must match
// the version finbox-android builds :extension-api with, otherwise the published
// AAR's metadata is unreadable here ("was compiled with an incompatible version
// of Kotlin"). Same reason extensions-source pins libs.kotlin.gradle.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
    id("com.android.library") version "9.3.1" apply false
}
