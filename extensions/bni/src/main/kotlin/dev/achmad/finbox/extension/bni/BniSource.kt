package dev.achmad.finbox.extension.bni

import dev.achmad.finbox.extension.EmailMessage
import dev.achmad.finbox.extension.EmailQuery
import dev.achmad.finbox.extension.ParsedTransaction
import dev.achmad.finbox.extension.Source
import dev.achmad.finbox.extension.TransactionSource
import dev.achmad.finbox.lib.receipt.Receipt
import dev.achmad.finbox.lib.receipt.detectType

/**
 * Source for wondr by BNI receipts.
 *
 * They are html tables grouped under section headings, which flatten to a label
 * and its value on one line:
 * ```
 * Penerima
 * QRIS INDOMARET
 * Tanggal & waktu transaksi
 * Tanggal 11 Agu 2026
 * Waktu 20:43:33 WIB
 * Detail pembayaran
 * Nominal Rp23.500
 * Total Rp23.500
 * Jenis transaksi QRIS
 * Reference ID 202608110843260325
 * ```
 * The headings are why the amount is read as Total and the date by shape: a
 * section is titled "Nominal transaksi" and another "Tanggal & waktu transaksi",
 * so a lookup of "Nominal" or "Tanggal" answers with the heading it hits first.
 * Total is the honest figure anyway — it is the nominal plus the admin fee, and
 * so what actually left the account.
 */
@Source
class BniSource : TransactionSource {

    // wondr notifies from this one address; the subject names the kind
    // ("Transfer berhasil!", "Top-up berhasil!", "Transaksi berhasil!").
    override val emailQuery = EmailQuery.from("wondr@bni.co.id")

    override fun isEmailForProvider(email: EmailMessage): Boolean {
        if ("bni.co.id" !in email.from.lowercase()) return false
        val receipt = Receipt.of(email)
        // A receipt states a reference id and a total. A promotion or an OTP
        // from the same address states neither.
        return receipt.field(*REFERENCE) != null && receipt.amount(*TOTAL) != null
    }

    override suspend fun parseEmail(email: EmailMessage): List<ParsedTransaction> {
        val receipt = Receipt.of(email)
        val amount = receipt.amount(*TOTAL) ?: return emptyList()
        val kind = receipt.field(*TYPE).orEmpty()

        return listOf(
            ParsedTransaction(
                // Stated as two rows, a day and a clock, hence splitDate.
                date = receipt.splitDate() ?: email.date,
                amount = amount,
                currency = "IDR",
                type = detectType(kind, email.subject),
                // Absent on a top up, which names the topped-up card instead.
                // A transfer puts the recipient's alias in the same cell, so an
                // unnamed one leaves a dash hanging off the end of the name.
                merchant = receipt.field(*MERCHANT)?.trim(' ', '-')?.ifBlank { null },
                description = kind.ifBlank { email.subject.trim() }.ifBlank { null },
                reference = receipt.field(*REFERENCE),
            ),
        )
    }

    private companion object {
        val TOTAL = arrayOf("Total")
        val REFERENCE = arrayOf("Reference ID", "Ref ID")
        val TYPE = arrayOf("Jenis transaksi")
        val MERCHANT = arrayOf("Penerima")
    }
}
