package dev.achmad.finbox.gradle

/**
 * DSL block for a finbox parser extension module:
 *
 * ```kotlin
 * finbox {
 *     name = "Bank BRI"
 *     provider = "bri"
 *     versionCode = 1
 * }
 * ```
 *
 * The parser class is not named here: annotate it with `@Source` and the
 * `:compiler` KSP processor generates the entry point the manifest points at.
 */
open class FinboxExtension {
    var name: String = ""
    var provider: String = ""
    var versionCode: Int = 1

    /**
     * `<apiVersion>.<versionCode>`, e.g. 1.0.3. The app falls back to the part
     * before the last dot when an APK omits its `finbox.extension.lib`
     * metadata, so the two can never disagree.
     */
    fun versionName(apiVersion: String): String = "$apiVersion.$versionCode"
}
