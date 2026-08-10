package dev.achmad.finbox.extension

/**
 * Allows a single extension APK to expose multiple [TransactionSource]s.
 * The app instantiates this class (via `finbox.extension.class` metadata)
 * when it implements [SourceFactory] instead of [TransactionSource].
 */
interface SourceFactory {
    fun createSources(): List<TransactionSource>
}
