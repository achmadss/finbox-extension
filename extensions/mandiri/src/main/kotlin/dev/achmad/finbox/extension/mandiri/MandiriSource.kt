package dev.achmad.finbox.extension.mandiri

import dev.achmad.finbox.extension.EmailMessage
import dev.achmad.finbox.extension.EmailQuery
import dev.achmad.finbox.extension.ParsedTransaction
import dev.achmad.finbox.extension.Source
import dev.achmad.finbox.extension.TransactionKind
import dev.achmad.finbox.extension.TransactionSource
import dev.achmad.finbox.extension.TransactionType
import dev.achmad.finbox.lib.receipt.Receipt
import dev.achmad.finbox.lib.receipt.isIncome

/**
 * Source for Livin' by Mandiri receipts.
 *
 * A label and its value share one flattened row, and the day and the clock are
 * two rows of their own:
 * ```
 * Penerima
 * APOTEK KAWI JAYA BSD
 * Tanggal 27 Jul 2026
 * Jam 15:22:45 WIB
 * Nominal Transaksi Rp 41.000,00
 * No. Referensi 2607271121582462322
 * ```
 * Livin' renames the amount per transaction — "Nominal Transaksi", "Nominal
 * Top-up", "Nominal Investasi" — so it is read by the word all three begin
 * with; what follows it is never digits, and the figure is found regardless.
 *
 * No fee is ever stated of its own: the amount shown is what left the account.
 */
@Source
class MandiriSource : TransactionSource {

    override val kinds = listOf(PAYMENT, TOP_UP, SBN, TRANSFER, INCOMING, OTHER)

    override val emailQuery = EmailQuery.from("noreply.livin@bankmandiri.co.id")

    override fun isEmailForProvider(email: EmailMessage): Boolean {
        if ("bankmandiri.co.id" !in email.from.lowercase()) return false
        // A labelled nominal is what separates a receipt from a promotion, which
        // states its "Rp50.000" in prose. Not the reference number: an SBN order
        // is a real payment and carries none.
        return Receipt.of(email).amount(*AMOUNT) != null
    }

    override suspend fun parseEmail(email: EmailMessage): List<ParsedTransaction> {
        val receipt = Receipt.of(email)
        val amount = receipt.amount(*AMOUNT) ?: return emptyList()

        return listOf(
            ParsedTransaction(
                // Stated as two rows, a day and a clock, hence splitDate.
                date = receipt.splitDate() ?: email.date,
                amount = amount,
                currency = "IDR",
                // The body never names the kind, the subject always does:
                // "Pembayaran Berhasil!", "Top-up e-money Berhasil".
                kind = kindOf(email.subject),
                merchant = receipt.field(*MERCHANT),
                description = email.subject.trim().ifBlank { null },
                reference = receipt.field(*REFERENCE),
            ),
        )
    }

    /**
     * Which kind a receipt is, from the subject — the body never names it.
     *
     * A QRIS payment arrives under the same "Pembayaran Berhasil!" as any other,
     * so there is no QRIS kind to declare: nothing in the mail distinguishes
     * one, and a switch that cannot be honoured is worse than no switch.
     */
    private fun kindOf(vararg text: String): TransactionKind {
        val joined = text.joinToString(" ").lowercase()
        return when {
            isIncome(*text) -> INCOMING
            "sbn" in joined || "investasi" in joined -> SBN
            "top-up" in joined || "top up" in joined -> TOP_UP
            "transfer" in joined || "pemindahan" in joined -> TRANSFER
            "pembayaran" in joined -> PAYMENT
            else -> OTHER
        }
    }

    private companion object {
        val PAYMENT = TransactionKind("PAYMENT", "Payment", TransactionType.EXPENSE)
        val TOP_UP = TransactionKind("TOP_UP", "E-Money Top Up", TransactionType.EXPENSE)
        val SBN = TransactionKind("SBN", "Government Bond Order", TransactionType.EXPENSE)
        val TRANSFER = TransactionKind("TRANSFER", "Transfer", TransactionType.EXPENSE)
        val INCOMING = TransactionKind("INCOMING", "Incoming Transfer", TransactionType.INCOME)
        val OTHER = TransactionKind("OTHER", "Other", TransactionType.EXPENSE)

        val AMOUNT = arrayOf("Nominal")
        val REFERENCE = arrayOf("No. Referensi", "Nomor Referensi")

        // "Penerima" for a QR payment, the provider's name for a top up.
        val MERCHANT = arrayOf("Penerima", "Penyedia Jasa")
    }
}
