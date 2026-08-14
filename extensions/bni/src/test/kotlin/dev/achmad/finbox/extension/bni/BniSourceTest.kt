package dev.achmad.finbox.extension.bni

import dev.achmad.finbox.extension.EmailMessage
import dev.achmad.finbox.extension.TransactionType
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixtures are real wondr by BNI notifications, flattened by the app's
 * html-to-text (one line per table row) and with names and account numbers
 * redacted.
 */
class BniSourceTest {

    private val source = BniSource()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/bni/$name.txt")) { "missing fixture $name" }
            .bufferedReader()
            .readText()

    private fun email(
        body: String,
        subject: String = "Transaksi berhasil!",
        from: String = "wondr by BNI <wondr@bni.co.id>",
        date: Long = 0L,
    ) = EmailMessage(
        id = 1L,
        messageId = "<test@bni.co.id>",
        threadId = "t1",
        subject = subject,
        from = from,
        to = "me@example.com",
        date = date,
        bodyText = body,
        bodyHtml = "",
    )

    // The receipts state WIB, so the instant is fixed wherever the test runs.
    private fun wib(
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
    fun `a transfer books the total, fee included, and its date is hyphenated`() {
        val parsed = runBlocking {
            source.parseEmail(email(fixture("transfer"), subject = "Transfer berhasil!"))
        }.single()

        // Rp20.000.000 sent, Rp2.500 admin: the total is what left the account.
        assertEquals(20_002_500L, parsed.amount)
        assertEquals("IDR", parsed.currency)
        assertEquals(TransactionType.TRANSFER, parsed.type)
        assertEquals("NAMA PENERIMA", parsed.merchant)
        assertEquals(wib(2026, 8, 3, 6, 23, 30), parsed.date)
        assertEquals("20260803062319859497", parsed.reference)
    }

    @Test
    fun `a QRIS payment names the merchant and the kind`() {
        val parsed = runBlocking { source.parseEmail(email(fixture("qris"))) }.single()

        assertEquals(23_500L, parsed.amount)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("QRIS INDOMARET", parsed.merchant)
        assertEquals("QRIS", parsed.description)
        assertEquals(wib(2026, 8, 11, 20, 43, 33), parsed.date)
        assertEquals("202608110843260325", parsed.reference)
    }

    @Test
    fun `a top up has a free fee and no recipient, and shortens the reference label`() {
        val parsed = runBlocking {
            source.parseEmail(email(fixture("topup"), subject = "Top-up berhasil!"))
        }.single()

        assertEquals(500_000L, parsed.amount)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        // The card being topped up is not a payee.
        assertNull(parsed.merchant)
        assertEquals(wib(2026, 8, 5, 10, 53, 18), parsed.date)
        assertEquals("2026080510531086751", parsed.reference)
    }

    @Test
    fun `every notification is claimed, and nothing else from the same sender is`() {
        assertTrue(source.isEmailForProvider(email(fixture("transfer"))))
        assertTrue(source.isEmailForProvider(email(fixture("qris"))))
        assertTrue(source.isEmailForProvider(email(fixture("topup"))))

        // A promotion states an amount, but no total and no reference id.
        assertFalse(
            source.isEmailForProvider(
                email("Kejar cashback Rp50.000 pakai wondr by BNI bulan ini!"),
            ),
        )
        assertFalse(source.isEmailForProvider(email("Kode OTP wondr kamu adalah 123456.")))
        assertFalse(source.isEmailForProvider(email(fixture("qris"), from = "promo@tokopedia.com")))
    }
}
