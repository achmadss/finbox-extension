package dev.achmad.finbox.extension.bri

import dev.achmad.finbox.extension.EmailMessage
import dev.achmad.finbox.extension.EmailQuery
import dev.achmad.finbox.extension.ParsedTransaction
import dev.achmad.finbox.extension.Source
import dev.achmad.finbox.extension.TransactionSource
import dev.achmad.finbox.lib.receipt.Receipt
import dev.achmad.finbox.lib.receipt.detectType

/**
 * Source for BRImo transaction receipts.
 *
 * They arrive as html tables, which the app flattens to one line per row:
 * ```
 * Nomor Referensi 192779074268
 * Tanggal Transaksi 11 Aug 2026, 10:30:27 WIB
 * Jenis Transaksi QRIS Bayar
 * Nama Merchant Warkop Maharani
 * Nominal Rp13.000
 * Biaya Admin Rp0
 * ```
 * Every field is read by its label, because the layout differs per transaction
 * type — QRIS, transfer and BRIZZI receipts share no common template, and BRI
 * adds types without warning.
 *
 * Parsing is deliberately tolerant: it returns an empty list whenever the
 * amount cannot be extracted, and the app drops the email.
 */
@Source
class BriSource : TransactionSource {

    // Every BRImo notification comes from this one address, whatever the
    // transaction is. Subjects differ per type, so they are matched below.
    override val emailQuery = EmailQuery.from("BankBRI@bri.co.id")

    override fun isEmailForProvider(email: EmailMessage): Boolean {
        if ("bri.co.id" !in email.from.lowercase()) return false
        val receipt = Receipt.of(email)
        // A receipt states a reference number and what was charged. Statements,
        // OTPs and promotions from the same address state neither.
        return receipt.field(*REFERENCE) != null &&
            (receipt.field(*AMOUNT) != null || receipt.field(*TOTAL) != null)
    }

    override suspend fun parseEmail(email: EmailMessage): List<ParsedTransaction> {
        val receipt = Receipt.of(email)

        // Nominal is what was spent, Total is that plus the admin fee — the
        // ledger wants what actually left the account.
        val nominal = receipt.amount(*AMOUNT)
        val fee = receipt.amount(*FEE) ?: 0L
        val amount = nominal?.plus(fee) ?: receipt.amount(*TOTAL) ?: return emptyList()

        val kind = receipt.field(*TYPE).orEmpty()

        return listOf(
            ParsedTransaction(
                // The body's timestamp is when BRI booked it; internalDate is
                // when the mail arrived, which is close but not the same.
                date = receipt.date(*DATE) ?: email.date,
                amount = amount,
                currency = "IDR",
                type = detectType(kind, email.subject),
                merchant = receipt.field(*MERCHANT),
                description = kind.ifBlank { email.subject.trim() }.ifBlank { null },
                reference = receipt.field(*REFERENCE),
            ),
        )
    }

    private companion object {
        val AMOUNT = arrayOf("Nominal")
        val FEE = arrayOf("Biaya Admin")
        val TOTAL = arrayOf("Total Transaksi", "Total")
        val REFERENCE = arrayOf("Nomor Referensi", "No. Ref", "No Ref")
        val DATE = arrayOf("Tanggal Transaksi", "Tanggal")
        val TYPE = arrayOf("Jenis Transaksi")
        val MERCHANT = arrayOf("Nama Merchant", "Nama Tujuan")
    }
}
