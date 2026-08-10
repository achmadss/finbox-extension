package dev.achmad.finbox.gradle

/**
 * DSL block for a finbox parser extension module:
 *
 * ```kotlin
 * finbox {
 *     name = "Bank BRI"
 *     provider = "bri"
 *     versionCode = 1
 *     className = "dev.achmad.finbox.extension.bri.BriParser"
 * }
 * ```
 */
open class FinboxExtension {
    var name: String = ""
    var provider: String = ""
    var versionCode: Int = 1
    var className: String = ""

    fun versionName(): String = "1.0.$versionCode"
}
