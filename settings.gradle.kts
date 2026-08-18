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
        // The parser API from finbox-android, built on demand by JitPack — no
        // account, no token, so this repo can be cloned and built by anyone.
        // Deliberately the only source: no mavenLocal, so what builds here is
        // what a contributor gets, never a jar that exists on one machine.
        maven("https://jitpack.io")
    }
}

rootProject.name = "finbox-parser"

includeBuild("build-logic")

include(":compiler")
include(":lib:receipt")
include(":parsers:bni")
include(":parsers:bri")
include(":parsers:jago")
include(":parsers:mandiri")
