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

    fun versionName(): String = "1.0.$versionCode"
}
