package dev.achmad.finbox.extension

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

/**
 * A parsed transaction in a provider-neutral form. All fields are nullable:
 * a parser should return as much as it reliably extracted; missing fields
 * fall back to defaults in the app. `amount` is in whole units of
 * [currency] (e.g. rupiah for IDR). `reference` is the provider's own
 * transaction reference used for duplicate detection when available.
 */
data class ParsedTransaction(
    val date: Long?,
    val amount: Long?,
    val currency: String?,
    val type: TransactionType?,
    val merchant: String?,
    val description: String?,
    val reference: String?,
    val confidence: Float = 1f,
)
