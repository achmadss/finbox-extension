pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.library") version "8.12.1"
        id("com.android.application") version "8.12.1"
        id("org.jetbrains.kotlin.android") version "2.2.10"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "finbox-extension"

includeBuild("build-logic")

include(":core")
include(":extensions:bri")
