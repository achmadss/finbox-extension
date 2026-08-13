package dev.achmad.finbox.extension.jago

import dev.achmad.finbox.extension.EmailMessage
import dev.achmad.finbox.extension.EmailQuery
import dev.achmad.finbox.extension.ParsedTransaction
import dev.achmad.finbox.extension.Source
import dev.achmad.finbox.extension.TransactionSource
import dev.achmad.finbox.lib.receipt.Receipt
import dev.achmad.finbox.lib.receipt.detectType

/**
 * Source for Bank Jago notifications.
 *
 * Jago writes a "Transaction Summary" with the label on its own line and the
 * value on the next:
 * ```
 * Transaction Summary
 * To
 * BEl Shop
 * Amount
 * Rp 2.000
 * Transaction Date
 * 05 August 2026, 13:23 WIB
 * ```
 * Labels gain a colon in the Jago Partner variant ("Amount:"), which changes
 * nothing — [Receipt.field] strips it.
 *
 * A debit card purchase is the odd one out: no summary at all, just a sentence
 * stating the amount. It carries no date and no merchant either, so it is read
 * against the mail's own arrival time.
 *
 * Nothing here carries a reference number — Jago simply does not send one — so
 * transactions are identified by their email alone.
 */
@Source
class JagoSource : TransactionSource {

    override val emailQuery = EmailQuery.from("noreply@jago.com")

    override fun isEmailForProvider(email: EmailMessage): Boolean {
        if ("jago.com" !in email.from.lowercase()) return false
        return Receipt.of(email).transactionAmount() != null
    }

    override suspend fun parseEmail(email: EmailMessage): List<ParsedTransaction> {
        val receipt = Receipt.of(email)
        val amount = receipt.transactionAmount() ?: return emptyList()
        val summarised = receipt.hasSummary()

        return listOf(
            ParsedTransaction(
                // Only the summary states a time of its own; a debit card email
                // has none, so the mail's own arrival has to stand in.
                date = receipt.date(*DATE).takeIf { summarised } ?: email.date,
                amount = amount,
                currency = "IDR",
                // The body says nothing about the kind of transaction, but the
                // subject names it: "made a payment to", "made a transfer".
                type = detectType(email.subject),
                merchant = receipt.field(*MERCHANT)?.takeIf { summarised },
                description = email.subject.trim().ifBlank { null },
                reference = null,
            ),
        )
    }

    /**
     * What was spent, or null when the email is not a transaction at all.
     *
     * The two shapes are kept apart on purpose: a promotion from the same
     * address states amounts too ("Rp50.000 cashback"), so a bare currency
     * figure is only trusted in the one sentence that is known to be a receipt.
     */
    private fun Receipt.transactionAmount(): Long? = when {
        hasSummary() -> amount(*AMOUNT)
        lines.any { DEBIT_CARD in it.lowercase() } -> statedAmount()
        else -> null
    }

    private fun Receipt.hasSummary(): Boolean =
        lines.any { it.startsWith(SUMMARY, ignoreCase = true) }

    private companion object {
        const val SUMMARY = "Transaction Summary"
        const val DEBIT_CARD = "debit card"

        val AMOUNT = arrayOf("Amount")
        val DATE = arrayOf("Transaction Date")

        // "To" for a payment or transfer, the partner's name for the rest.
        val MERCHANT = arrayOf("To", "Jago partner")
    }
}
