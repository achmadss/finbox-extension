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
        // over there) wins while iterating on the API; otherwise it comes from
        // GitHub Packages, and this repo can be worked on on its own.
        mavenLocal()
        maven {
            url = uri("https://maven.pkg.github.com/achmadss/finbox-android")
            // GitHub Packages authenticates reads, even of a public package.
            // Put a token with `read:packages` in ~/.gradle/gradle.properties as
            // gpr.user / gpr.key, or export GITHUB_ACTOR / GITHUB_TOKEN.
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

rootProject.name = "finbox-extension"

includeBuild("build-logic")

include(":compiler")
include(":lib:receipt")
include(":extensions:bri")
include(":extensions:jago")
