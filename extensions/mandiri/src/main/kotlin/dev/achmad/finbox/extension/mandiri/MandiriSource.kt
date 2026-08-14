package dev.achmad.finbox.extension.mandiri

import dev.achmad.finbox.extension.EmailMessage
import dev.achmad.finbox.extension.EmailQuery
import dev.achmad.finbox.extension.ParsedTransaction
import dev.achmad.finbox.extension.Source
import dev.achmad.finbox.extension.TransactionSource
import dev.achmad.finbox.lib.receipt.Receipt
import dev.achmad.finbox.lib.receipt.detectType

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
                type = detectType(email.subject),
                merchant = receipt.field(*MERCHANT),
                description = email.subject.trim().ifBlank { null },
                reference = receipt.field(*REFERENCE),
            ),
        )
    }

    private companion object {
        val AMOUNT = arrayOf("Nominal")
        val REFERENCE = arrayOf("No. Referensi", "Nomor Referensi")

        // "Penerima" for a QR payment, the provider's name for a top up.
        val MERCHANT = arrayOf("Penerima", "Penyedia Jasa")
    }
}
