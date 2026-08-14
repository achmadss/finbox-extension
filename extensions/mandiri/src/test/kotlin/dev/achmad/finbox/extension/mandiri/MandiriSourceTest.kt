package dev.achmad.finbox.extension.mandiri

import dev.achmad.finbox.extension.EmailMessage
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixtures are real Livin' by Mandiri notifications, flattened by the app's
 * html-to-text (one line per table row) and with names and account numbers
 * redacted.
 */
class MandiriSourceTest {

    private val source = MandiriSource()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/mandiri/$name.txt")) { "missing fixture $name" }
            .bufferedReader()
            .readText()

    private fun email(
        body: String,
        subject: String = "Pembayaran Berhasil!",
        from: String = "Livin' <noreply.livin@bankmandiri.co.id>",
        date: Long = 0L,
    ) = EmailMessage(
        id = 1L,
        messageId = "<test@bankmandiri.co.id>",
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
    fun `a QR payment names the merchant, and the cents are dropped`() {
        val parsed = runBlocking { source.parseEmail(email(fixture("qris"))) }.single()

        // "Nominal Transaksi Rp 41.000,00" — a ledger in rupiah has no cents.
        assertEquals(41_000L, parsed.amount)
        assertEquals("IDR", parsed.currency)
        assertEquals("PAYMENT", parsed.kind?.key)
        assertEquals("APOTEK KAWI JAYA BSD", parsed.merchant)
        assertEquals(wib(2026, 7, 27, 15, 22, 45), parsed.date)
        // The QRIS reference on the line below must not win.
        assertEquals("2607271121582462322", parsed.reference)
    }

    @Test
    fun `a top up names the provider and labels its nominal differently`() {
        val parsed = runBlocking {
            source.parseEmail(email(fixture("topup"), subject = "Top-up e-money Berhasil"))
        }.single()

        assertEquals(200_000L, parsed.amount)
        assertEquals("TOP_UP", parsed.kind?.key)
        assertEquals("e-money", parsed.merchant)
        assertEquals(wib(2026, 7, 28, 7, 57, 27), parsed.date)
        assertEquals("702607280757221223", parsed.reference)
    }

    @Test
    fun `an SBN order states neither date nor reference, so the mail's own stand in`() {
        val arrived = wib(2026, 8, 14, 9, 0, 0)
        val parsed = runBlocking {
            source.parseEmail(
                email(fixture("sbn"), subject = "Pemesanan SBN Berhasil!", date = arrived),
            )
        }.single()

        assertEquals(100_000_000L, parsed.amount)
        assertEquals("SBN", parsed.kind?.key)
        assertEquals(arrived, parsed.date)
        assertNull(parsed.merchant)
        assertNull(parsed.reference)
    }

    @Test
    fun `every notification is claimed, and nothing else from the same sender is`() {
        assertTrue(source.isEmailForProvider(email(fixture("qris"))))
        assertTrue(source.isEmailForProvider(email(fixture("topup"))))
        assertTrue(source.isEmailForProvider(email(fixture("sbn"))))

        // A promotion states an amount in prose, never as a labelled nominal.
        assertFalse(
            source.isEmailForProvider(
                email("Dapatkan diskon Rp50.000 untuk pembayaran di Livin' Sukha!"),
            ),
        )
        assertFalse(source.isEmailForProvider(email("Kode OTP Livin' kamu adalah 123456.")))
        assertFalse(source.isEmailForProvider(email(fixture("qris"), from = "promo@tokopedia.com")))
    }

    @Test
    fun `every kind a receipt parses to is one this source declares`() {
        val declared = source.kinds.map { it.key }.toSet()
        val parsed = runBlocking {
            FIXTURES.flatMap { source.parseEmail(email(fixture(it.first), subject = it.second)) }
        }

        assertEquals(FIXTURES.size, parsed.size)
        parsed.forEach { assertTrue("undeclared kind ${it.kind?.key}", it.kind?.key in declared) }
    }

    private companion object {
        /** Every fixture with the subject its mail actually carries. */
        val FIXTURES = listOf(
            "qris" to "Pembayaran Berhasil!",
            "topup" to "Top-up e-money Berhasil",
            "sbn" to "Pemesanan SBN Berhasil!",
        )
    }
}
