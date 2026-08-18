// Pins the Kotlin compiler used by AGP's built-in Kotlin support. It must match
// the version finbox-android builds :parser-api with, otherwise the published
// AAR's metadata is unreadable here ("was compiled with an incompatible version
// of Kotlin"). Same reason parsers-source pins libs.kotlin.gradle.
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
    // Not applied here; on the classpath so the finbox.plugins.parser plugin
    // can apply it to each parser module. Versions come from settings.
    id("org.jetbrains.kotlin.jvm") apply false
    id("com.google.devtools.ksp") apply false
}
