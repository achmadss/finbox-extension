import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
}

// Bundled into each extension APK, not provided by the app: these are
// heuristics that change with every bank quirk, and pushing them through the
// app's API would orphan every published extension each time.
android {
    namespace = "dev.achmad.finbox.lib.receipt"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

val apiVersion = providers.gradleProperty("finbox.apiVersion").get()

dependencies {
    // Same deal as an extension module: the app provides these at runtime.
    compileOnly("com.github.achmadss.finbox-android:extension-api:$apiVersion")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
    compileOnly("org.jsoup:jsoup:1.18.3")

    testImplementation("com.github.achmadss.finbox-android:extension-api:$apiVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
    testImplementation("org.jsoup:jsoup:1.18.3")
    testImplementation("junit:junit:4.13.2")
}
