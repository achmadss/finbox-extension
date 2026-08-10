package dev.achmad.finbox.extension

/**
 * A normalized email message handed to parser extensions by the app.
 * The same FQN must exist in the finbox-android `:extension-api` module
 * (compileOnly stubs here; real classes provided by the app at runtime).
 */
data class EmailMessage(
    val id: Long,
    val messageId: String,
    val threadId: String,
    val subject: String,
    val from: String,
    val to: String,
    /** Unix epoch millis. */
    val date: Long,
    val bodyText: String,
    val bodyHtml: String,
)
