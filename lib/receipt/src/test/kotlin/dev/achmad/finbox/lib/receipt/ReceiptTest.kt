package dev.achmad.finbox.lib.receipt

import dev.achmad.finbox.extension.EmailMessage
import dev.achmad.finbox.extension.TransactionType
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The two receipt layouts this has to cover: BRI puts a label and its value on
 * one line, Jago puts them on two.
 */
class ReceiptTest {

    private val bri = Receipt(
        """
        Nomor Referensi 192779074268
        Tanggal Transaksi 11 Aug 2026, 10:30:27 WIB
        Jenis Transaksi QRIS Bayar
        Nama Merchant Warkop Maharani
        Nominal Rp13.000
        Biaya Admin Rp0
        """.trimIndent().lines(),
    )

    private val jago = Receipt(
        """
        Transaction Summary
        From
        106156509716
        To
        BEl Shop
        9360000801969721893
        Amount
        Rp 2.000
        Transaction Date
        05 August 2026, 13:23 WIB
        Tip Amount
        Rp 0
        """.trimIndent().lines(),
    )

    private fun wib(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.ofHours(7))
        .toInstant()
        .toEpochMilli()

    @Test
    fun `a value on the same line as its label`() {
        assertEquals("192779074268", bri.field("Nomor Referensi", "No. Ref"))
        assertEquals(13_000L, bri.amount("Nominal"))
        assertEquals(0L, bri.amount("Biaya Admin"))
    }

    @Test
    fun `a value on the line after its label`() {
        assertEquals("BEl Shop", jago.field("To"))
        assertEquals(2_000L, jago.amount("Amount"))
    }

    @Test
    fun `an absent field does not take its neighbour's value`() {
        // "Tip Amount" is the last label, so the line after it is nothing.
        assertNull(Receipt(listOf("Amount", "Tip Amount")).field("Tip Amount"))
        assertNull(bri.field("Total Transaksi"))
    }

    @Test
    fun `the receipt's own timestamp wins over the header, in the zone it states`() {
        assertEquals(wib(2026, 8, 11, 10, 30, 27), bri.date("Tanggal Transaksi"))
        assertEquals(wib(2026, 8, 5, 13, 23), jago.date("Transaction Date"))
    }

    @Test
    fun `an unlabelled date is still found, and a missing one reported`() {
        // A BRIZZI top up states the time in the header and has no date row.
        val brizzi = Receipt(listOf("11 Agu 2026, 07:16 WIB", "Total Transaksi", "Rp50.000"))
        assertEquals(wib(2026, 8, 11, 7, 16), brizzi.date("Tanggal Transaksi"))
        assertNull(Receipt(listOf("Nominal Rp13.000")).date("Tanggal"))
    }

    @Test
    fun `a day and a clock on separate rows are one timestamp`() {
        // BNI, hyphenated and headed by a label that is not the date's own.
        val bni = Receipt(
            listOf("Tanggal & waktu transaksi", "Tanggal 03-Agu-2026", "Waktu 06:23:30 WIB"),
        )
        assertEquals(wib(2026, 8, 3, 6, 23, 30), bni.splitDate())

        // Mandiri, which calls the clock something else again.
        val mandiri = Receipt(listOf("Tanggal 27 Jul 2026", "Jam 15:22:45 WIB"))
        assertEquals(wib(2026, 7, 27, 15, 22, 45), mandiri.splitDate())

        assertNull(Receipt(listOf("Nominal Investasi IDR 100.000.000,00")).splitDate())
    }

    @Test
    fun `Indonesian and English month names, with or without the comma`() {
        assertEquals(wib(2026, 8, 13, 9, 16, 10), parseTimestamp("13 Agustus 2026 , 09:16:10 WIB"))
        assertEquals(wib(2026, 8, 11, 19, 56), parseTimestamp("11 August 2026 19:56 WIB"))
        assertEquals(wib(2026, 7, 22, 13, 53), parseTimestamp("22 July 2026 13:53 WIB"))
        // WITA is not WIT: an hour apart.
        assertEquals(
            ZonedDateTime.of(2026, 8, 11, 10, 30, 0, 0, ZoneOffset.ofHours(8)).toInstant().toEpochMilli(),
            parseTimestamp("11 Aug 2026, 10:30 WITA"),
        )
        assertNull(parseTimestamp("Nomor Referensi 192779074268"))
    }

    @Test
    fun `money is read whichever way it is grouped`() {
        assertEquals(1_151_800L, parseAmount("Rp 1.151.800"))
        assertEquals(1_000_000L, parseAmount("Rp1.000.000,00"))
        assertEquals(41_100L, parseAmount("Rp41.100"))
        assertNull(parseAmount("Successful"))
    }

    @Test
    fun `an amount stated in prose needs its currency, an id is not money`() {
        assertEquals(
            33_189L,
            Receipt(
                listOf(
                    "Hello ACHMAD,",
                    "You have recently made a transaction of Rp33.189 using your Jago debit card.",
                ),
            ).statedAmount(),
        )
        assertNull(Receipt(listOf("Terminal ID A01", "Customer PAN 9360054216156509719")).statedAmount())
    }

    @Test
    fun `html is flattened here, one line per row, so a label keeps its value`() {
        val receipt = Receipt.ofHtml(
            """
            <html><head><style>.total{font-weight:700}</style></head><body>
            <table>
              <tr><th>Nomor Referensi</th><td>192779074268</td></tr>
              <tr><th>Tanggal Transaksi</th><td>11 Aug 2026&#44; 10:30:27 WIB</td></tr>
            </table>
            <p>Amount<br>Rp 2.000</p>
            </body></html>
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "Nomor Referensi 192779074268",
                "Tanggal Transaksi 11 Aug 2026, 10:30:27 WIB",
                "Amount",
                "Rp 2.000",
            ),
            receipt.lines,
        )
        // Both layouts, out of the same markup.
        assertEquals("192779074268", receipt.field("Nomor Referensi"))
        assertEquals(2_000L, receipt.amount("Amount"))
    }

    @Test
    fun `html wins over the text the app derived from it`() {
        val email = EmailMessage(
            id = 1L,
            messageId = "<test@example.com>",
            threadId = "t1",
            subject = "",
            from = "bank@example.com",
            to = "me@example.com",
            date = 0L,
            // What a naive flattener upstream would have produced: the label and
            // its value welded into one line.
            bodyText = "Amount Rp 2.000 Tip Amount Rp 0",
            bodyHtml = "<table><tr><td>Amount</td><td>Rp 2.000</td></tr></table>",
        )

        assertEquals(2_000L, Receipt.of(email).amount("Amount"))
    }

    @Test
    fun `a plain text email is still read as it arrived`() {
        val email = EmailMessage(
            id = 1L,
            messageId = "<test@example.com>",
            threadId = "t1",
            subject = "",
            from = "bank@example.com",
            to = "me@example.com",
            date = 0L,
            bodyText = "Nominal Rp13.000",
            bodyHtml = "",
        )

        assertEquals(13_000L, Receipt.of(email).amount("Nominal"))
    }

    @Test
    fun `the type is a guess from wording, and expense unless told otherwise`() {
        assertEquals(TransactionType.EXPENSE, detectType("QRIS Bayar", "Pembelian QRIS Berhasil"))
        assertEquals(TransactionType.EXPENSE, detectType("You have made a payment to BEl Shop"))
        assertEquals(TransactionType.TRANSFER, detectType("Pemindahan Dana Sesama Rekening BRI"))
        assertEquals(TransactionType.TRANSFER, detectType("You have transferred some money"))
        assertEquals(TransactionType.INCOME, detectType("Dana masuk"))
        // "Termasuk" holds "masuk" but is not money coming in.
        assertEquals(TransactionType.EXPENSE, detectType("Biaya Termasuk PPN"))
    }
}
