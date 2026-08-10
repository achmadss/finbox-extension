package dev.achmad.finbox.gradle

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Builds a finbox parser extension APK:
 * - Android application module, no launcher activity
 * - Manifest declares the `dev.achmad.finbox.extension` feature plus
 *   `finbox.extension.lib` / `finbox.extension.class` metadata
 * - `compileOnly` against the shared parser API (`:core`) so the app
 *   provides the real classes at runtime
 * - Copies the release APK to `repo/apk/finbox-<provider>-<version>.apk`
 */
class FinboxExtensionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")

            val finbox = extensions.create<FinboxExtension>("finbox")

            configure<ApplicationExtension> {
                namespace = "dev.achmad.finbox.extension"
                compileSdk = 36
                defaultConfig {
                    minSdk = 26
                    // project.name is available at plugin-apply time; the finbox {} DSL
                    // runs after AGP eagerly reads defaultConfig, so version data lives
                    // in manifest metadata (finbox.extension.version_code) instead.
                    applicationId = "dev.achmad.finbox.extension.${project.name}"
                    versionCode = 1
                    versionName = "1.0.1"
                }
                buildTypes {
                    release {
                        isMinifyEnabled = false
                        signingConfig = signingConfigs.getByName("debug")
                    }
                }
                sourceSets.getByName("main").manifest.srcFile(
                    layout.buildDirectory.file("generated/manifest/AndroidManifest.xml"),
                )
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_11
                    targetCompatibility = JavaVersion.VERSION_11
                }
            }

            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }

            val generateManifest = tasks.register("generateFinboxManifest") {
                val manifestFile = layout.buildDirectory.file("generated/manifest/AndroidManifest.xml")
                inputs.property("provider", providers.provider { finbox.provider })
                inputs.property("className", providers.provider { finbox.className })
                inputs.property("name", providers.provider { finbox.name })
                outputs.file(manifestFile)
                doLast {
                    val file = manifestFile.get().asFile
                    file.parentFile.mkdirs()
                    file.writeText(manifest(finbox))
                }
            }
            tasks.named("preBuild").configure { dependsOn(generateManifest) }

            val copyApk = tasks.register("copyReleaseApk") {
                val apkFile = layout.buildDirectory.file("outputs/apk/release/${project.name}-release.apk")
                val outFile = rootProject.layout.projectDirectory
                    .dir("repo/apk")
                    .file(providers.provider { "finbox-${finbox.provider}-${finbox.versionName()}.apk" })
                inputs.file(apkFile)
                outputs.file(outFile)
                doLast {
                    outFile.get().asFile.parentFile.mkdirs()
                    apkFile.get().asFile.copyTo(outFile.get().asFile, overwrite = true)
                }
            }
            afterEvaluate {
                if (finbox.name.isBlank()) throw GradleException("finbox { name } must be set")
                if (finbox.provider.isBlank()) throw GradleException("finbox { provider } must be set")
                if (finbox.className.isBlank()) throw GradleException("finbox { className } must be set")
                if (finbox.versionCode < 1) throw GradleException("finbox { versionCode } must be >= 1")
                tasks.named("assembleRelease").configure { finalizedBy(copyApk) }
            }

            val deps = dependencies
            deps.add("compileOnly", project(":core"))
            deps.add("compileOnly", "com.squareup.okhttp3:okhttp:5.1.0")
            deps.add("compileOnly", "org.jsoup:jsoup:1.18.3")
            deps.add("compileOnly", "org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            deps.add("compileOnly", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
    }

    private fun manifest(finbox: FinboxExtension): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <uses-feature
                android:name="dev.achmad.finbox.extension"
                android:required="true" />
            <application>
                <meta-data
                    android:name="finbox.extension.lib"
                    android:value="1.0" />
                <meta-data
                    android:name="finbox.extension.class"
                    android:value="${finbox.className}" />
                <meta-data
                    android:name="finbox.extension.provider"
                    android:value="${finbox.provider}" />
                <meta-data
                    android:name="finbox.extension.name"
                    android:value="${finbox.name}" />
                <meta-data
                    android:name="finbox.extension.version_code"
                    android:value="${finbox.versionCode}" />
            </application>
        </manifest>
    """.trimIndent()
}
