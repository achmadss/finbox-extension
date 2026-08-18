package dev.achmad.finbox.parser.jago

import dev.achmad.finbox.parser.EmailMessage
import dev.achmad.finbox.parser.EmailQuery
import dev.achmad.finbox.parser.ParsedTransaction
import dev.achmad.finbox.parser.Source
import dev.achmad.finbox.parser.TransactionKind
import dev.achmad.finbox.parser.TransactionSource
import dev.achmad.finbox.parser.TransactionType
import dev.achmad.finbox.lib.receipt.Receipt
import dev.achmad.finbox.lib.receipt.isIncome

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
 * Investment Pocket mails are ignored: a stock buy or sale moves money between
 * the user's own pockets, not out of them.
 *
 * Nothing here carries a reference number — Jago simply does not send one — so
 * the app falls back to the Gmail thread, then the email, for transaction identity.
 */
@Source
class JagoSource : TransactionSource {

    override val kinds = listOf(PAYMENT, TRANSFER, PARTNER, DEBIT_CARD, INCOMING, OTHER)

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
                kind = kindOf(email.subject),
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
        // Money moving between the account and the Investment Pocket is not
        // spending — the stock broker's withdrawal is a buy, the credit back a
        // sale — so both are dropped rather than booked twice.
        lines.any { INVESTMENT.containsMatchIn(it) } -> null
        hasSummary() -> amount(*AMOUNT)
        lines.any { DEBIT_CARD_LINE in it.lowercase() } -> statedAmount()
        else -> null
    }

    private fun Receipt.hasSummary(): Boolean =
        lines.any { it.startsWith(SUMMARY, ignoreCase = true) }

    /**
     * Which kind a transaction is, from the subject — the body never names it.
     *
     * A partner transaction ("via GoPay") is checked before a payment: Jago
     * words some of them as payments too, and the partner is the more specific
     * fact. Stock trades need no kind, since they never reach here.
     */
    private fun kindOf(vararg text: String): TransactionKind {
        val joined = text.joinToString(" ").lowercase()
        return when {
            isIncome(*text) -> INCOMING
            DEBIT_CARD_LINE in joined -> DEBIT_CARD
            " via " in joined || "partner" in joined -> PARTNER
            "transfer" in joined -> TRANSFER
            "payment" in joined || "paid" in joined -> PAYMENT
            else -> OTHER
        }
    }

    private companion object {
        val PAYMENT = TransactionKind("PAYMENT", "Payment", TransactionType.EXPENSE)
        val TRANSFER = TransactionKind("TRANSFER", "Transfer", TransactionType.EXPENSE)
        val PARTNER = TransactionKind("PARTNER", "Partner Transaction", TransactionType.EXPENSE)
        val DEBIT_CARD = TransactionKind("DEBIT_CARD", "Debit Card Purchase", TransactionType.EXPENSE)
        val INCOMING = TransactionKind("INCOMING", "Incoming Transfer", TransactionType.INCOME)
        val OTHER = TransactionKind("OTHER", "Other", TransactionType.EXPENSE)

        const val SUMMARY = "Transaction Summary"
        const val DEBIT_CARD_LINE = "debit card"

        // Not "invest": every Jago footer says "from saving, transacting, to
        // investing", which would drop the lot.
        val INVESTMENT = Regex("investment pocket|stock", RegexOption.IGNORE_CASE)

        val AMOUNT = arrayOf("Amount")
        val DATE = arrayOf("Transaction Date")

        // "To" for a payment or transfer, the partner's name for the rest.
        val MERCHANT = arrayOf("To", "Jago partner")
    }
}
