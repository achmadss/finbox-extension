package dev.achmad.finbox.extension.bri

import dev.achmad.finbox.extension.EmailMessage
import dev.achmad.finbox.extension.TransactionType
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BriSourceTest {

    private val source = BriSource()

    private fun email(
        body: String = "",
        html: String = "",
        subject: String = "Notifikasi Transaksi BRI",
        from: String = "noreply@bri.co.id",
    ) = EmailMessage(
        id = 1L,
        messageId = "<test@bri.co.id>",
        threadId = "t1",
        subject = subject,
        from = from,
        to = "me@example.com",
        date = 0L,
        bodyText = body,
        bodyHtml = html,
    )

    private val notification = """
        PEMBERITAHUAN TRANSAKSI
        KARTU : 5241 08** **** 1234
        TANGGAL : 26/01/2026 10:30:12
        TRANSAKSI : PEMBAYARAN TOKOPEDIA
        JUMLAH : Rp1.000.000,00
        SALDO : Rp5.000.000,00
    """.trimIndent()

    private fun parse(body: String) = runBlocking { source.parseEmail(email(body = body)) }

    @Test
    fun `asks gmail for bri mail only`() {
        val query = source.emailQuery

        assertFalse(query.isEmpty)
        assertEquals(listOf("bri.co.id"), query.from)
    }

    @Test
    fun `confirms only transaction mail from the bri domain`() {
        // The query already restricts the sender; this rejects what BRI sends
        // from the same address that isn't a transaction.
        assertTrue(source.isEmailForProvider(email(from = "noreply@bri.co.id", body = notification)))
        assertFalse(
            source.isEmailForProvider(
                email(from = "noreply@bri.co.id", subject = "Promo BRI", body = "Diskon 50% untuk Anda"),
            ),
        )
        assertFalse(source.isEmailForProvider(email(from = "noreply@bca.co.id", body = notification)))
    }

    @Test
    fun `parses a notification email`() {
        val tx = parse(notification).single()

        assertEquals(1_000_000L, tx.amount)
        assertEquals("IDR", tx.currency)
        assertEquals("PEMBAYARAN TOKOPEDIA", tx.description)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertNull(tx.reference)
    }

    @Test
    fun `reads the date as day-month-year in the local zone`() {
        val tx = parse(notification).single()

        // 26/01/2026 is the 26th of January, not the 1st of the 26th month.
        val parsed = Instant.ofEpochMilli(tx.date!!).atZone(ZoneId.systemDefault())
        assertEquals(2026, parsed.year)
        assertEquals(1, parsed.monthValue)
        assertEquals(26, parsed.dayOfMonth)
        assertEquals(10, parsed.hour)
        assertEquals(30, parsed.minute)
    }

    @Test
    fun `accepts a two digit year and a date without a time`() {
        val tx = parse(
            """
            TANGGAL : 05/03/26
            JUMLAH : Rp250.000
            """.trimIndent(),
        ).single()

        val parsed = Instant.ofEpochMilli(tx.date!!).atZone(ZoneId.systemDefault())
        assertEquals(2026, parsed.year)
        assertEquals(3, parsed.monthValue)
        assertEquals(5, parsed.dayOfMonth)
        assertEquals(0, parsed.hour)
        assertEquals(250_000L, tx.amount)
    }

    @Test
    fun `reads both thousand separator conventions and bare digits`() {
        fun amountOf(jumlah: String) = parse("TANGGAL : 26/01/2026\nJUMLAH : $jumlah").single().amount

        assertEquals(1_000_000L, amountOf("Rp1.000.000,00"))
        assertEquals(1_000_000L, amountOf("Rp1,000,000.00"))
        assertEquals(1_000_000L, amountOf("Rp1000000"))
        assertEquals(1_000_000L, amountOf("IDR 1.000.000"))
        // No currency marker: the JUMLAH label carries it.
        assertEquals(1_000_000L, amountOf("1.000.000"))
    }

    @Test
    fun `classifies income and transfers`() {
        fun typeOf(line: String) =
            parse("TANGGAL : 26/01/2026\nJUMLAH : Rp10.000\n$line").single().type

        assertEquals(TransactionType.INCOME, typeOf("TRANSAKSI : DANA MASUK"))
        assertEquals(TransactionType.INCOME, typeOf("TRANSAKSI : KREDIT GAJI"))
        assertEquals(TransactionType.TRANSFER, typeOf("TRANSAKSI : TRANSFER KE 1234"))
        assertEquals(TransactionType.EXPENSE, typeOf("TRANSAKSI : PEMBAYARAN TOKOPEDIA"))
    }

    @Test
    fun `picks up a reference when present`() {
        val tx = parse("$notification\nREFERENSI : 987654321").single()

        assertEquals("987654321", tx.reference)
    }

    @Test
    fun `falls back to the html body when there is no plain text`() {
        val html = "<html><body><p>TANGGAL : 26/01/2026 10:30:12</p>" +
            "<p>JUMLAH : Rp1.000.000,00</p></body></html>"
        val tx = runBlocking { source.parseEmail(email(html = html)) }.single()

        assertEquals(1_000_000L, tx.amount)
    }

    @Test
    fun `yields nothing when the essentials are missing`() {
        // The app routes an unparsed email elsewhere; returning a half-built
        // transaction would put a wrong amount in the ledger.
        assertTrue(parse("").isEmpty())
        assertTrue(parse("TANGGAL : 26/01/2026").isEmpty())
        assertTrue(parse("JUMLAH : Rp1.000.000,00").isEmpty())
        assertTrue(parse("TANGGAL : 32/13/2026\nJUMLAH : Rp1.000.000,00").isEmpty())
    }
}
