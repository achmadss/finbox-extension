package dev.achmad.finbox.extension.bri

import dev.achmad.finbox.extension.EmailMessage
import dev.achmad.finbox.extension.TransactionType
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixtures are real BRImo receipts, flattened by the app's html-to-text
 * (one line per table row) and with names and account numbers redacted.
 */
class BriSourceTest {

    private val source = BriSource()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/bri/$name.txt")) { "missing fixture $name" }
            .bufferedReader()
            .readText()

    private fun email(
        body: String,
        subject: String = "Notifikasi Transaksi BRI",
        from: String = "Bank BRI <BankBRI@bri.co.id>",
        date: Long = 0L,
    ) = EmailMessage(
        id = 1L,
        messageId = "<test@bri.co.id>",
        threadId = "t1",
        subject = subject,
        from = from,
        to = "me@example.com",
        date = date,
        bodyText = body,
        bodyHtml = "",
    )

    // The receipts state WIB, so the instant is fixed wherever the test runs.
    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.ofHours(7))
        .toInstant()
        .toEpochMilli()

    @Test
    fun `a QRIS payment is an expense, with its merchant`() {
        val parsed = runBlocking {
            source.parseEmail(email(fixture("qris"), subject = "Pembelian QRIS Berhasil"))
        }.single()

        assertEquals(13_000L, parsed.amount)
        assertEquals("IDR", parsed.currency)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("Warkop Maharani", parsed.merchant)
        assertEquals("192779074268", parsed.reference)
        assertEquals(millis(2026, 8, 11, 10, 30, 27), parsed.date)
    }

    @Test
    fun `a transfer names the recipient and reads the Indonesian month`() {
        val parsed = runBlocking {
            source.parseEmail(
                email(fixture("transfer"), subject = "Pemindahan Dana Sesama Rekening BRI"),
            )
        }.single()

        assertEquals(30_000L, parsed.amount)
        assertEquals(TransactionType.TRANSFER, parsed.type)
        assertEquals("NAMA PENERIMA", parsed.merchant)
        assertEquals("193865864645", parsed.reference)
        assertEquals(millis(2026, 8, 13, 9, 16, 10), parsed.date)
    }

    @Test
    fun `an out-of-bank transfer is charged with its fee`() {
        val parsed = runBlocking {
            source.parseEmail(
                email(fixture("bifast"), subject = "Pemindahan Dana Bank Lain Dalam Negeri"),
            )
        }.single()

        // Nominal Rp1.000.000 plus Biaya Admin Rp2.500, which is the stated Total.
        assertEquals(1_002_500L, parsed.amount)
        assertEquals(TransactionType.TRANSFER, parsed.type)
        assertEquals("NAMA PENERIMA", parsed.merchant)
        assertEquals("180715421169", parsed.reference)
        assertEquals(millis(2026, 7, 21, 16, 40, 32), parsed.date)
    }

    @Test
    fun `a BRIZZI top up has no date row, so the header date is used`() {
        val parsed = runBlocking {
            source.parseEmail(email(fixture("brizzi"), subject = "Top Up BRIZZI Berhasil"))
        }.single()

        assertEquals(50_000L, parsed.amount)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("192685482301", parsed.reference)
        // No seconds in the header, so the minute is as precise as it gets.
        assertEquals(millis(2026, 8, 11, 7, 16, 0), parsed.date)
    }

    @Test
    fun `the admin fee is part of what left the account`() {
        val parsed = runBlocking {
            source.parseEmail(
                email(
                    """
                    Nomor Referensi 192779074268
                    Tanggal Transaksi 11 Aug 2026, 10:30:27 WIB
                    Jenis Transaksi Transfer Antar Bank
                    Nominal Rp30.000
                    Biaya Admin Rp2.500
                    """.trimIndent(),
                ),
            )
        }.single()

        assertEquals(32_500L, parsed.amount)
    }

    @Test
    fun `every receipt is claimed, and nothing else from the same sender is`() {
        assertTrue(source.isEmailForProvider(email(fixture("qris"))))
        assertTrue(source.isEmailForProvider(email(fixture("transfer"))))
        assertTrue(source.isEmailForProvider(email(fixture("brizzi"))))
        assertTrue(source.isEmailForProvider(email(fixture("bifast"))))

        // A bank sends OTPs and promotions from the address the query matches.
        assertFalse(
            source.isEmailForProvider(
                email("Kode OTP kamu adalah 123456. Jangan berikan ke siapa pun."),
            ),
        )
        assertFalse(
            source.isEmailForProvider(email("Promo cashback 50% untuk pengguna BRImo!")),
        )
        assertFalse(
            source.isEmailForProvider(email(fixture("qris"), from = "promo@tokopedia.com")),
        )
    }

    @Test
    fun `an email with no amount is dropped rather than guessed at`() {
        val parsed = runBlocking {
            source.parseEmail(email("Nomor Referensi 192779074268\nJenis Transaksi QRIS Bayar"))
        }

        assertTrue(parsed.isEmpty())
    }
}
