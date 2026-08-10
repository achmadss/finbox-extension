package dev.achmad.finbox.extension

/**
 * A parser extension for one financial provider.
 *
 * `id` must be deterministic and stable across releases; the convention is
 * `MD5("${name.lowercase()}/$versionId")` first 8 bytes as a positive Long,
 * which survives provider renames and version bumps.
 */
interface TransactionSource {
    val id: Long
    val name: String
    val versionId: Int

    /** Whether this email belongs to this provider (sender, subject, format). */
    fun isEmailForProvider(email: EmailMessage): Boolean

    /** Convert the email into one or more standardized transactions. */
    suspend fun parseEmail(email: EmailMessage): List<ParsedTransaction>
}
