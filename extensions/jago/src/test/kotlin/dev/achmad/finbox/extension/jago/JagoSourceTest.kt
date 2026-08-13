package dev.achmad.finbox.extension.jago

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
 * The fixtures are real Jago notifications, flattened by the app's html-to-text
 * (one line per table row) and with names and account numbers redacted.
 */
class JagoSourceTest {

    private val source = JagoSource()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/jago/$name.txt")) { "missing fixture $name" }
            .bufferedReader()
            .readText()

    private fun email(
        body: String,
        subject: String = "You have made a payment to BEl Shop",
        from: String = "Jago <noreply@jago.com>",
        date: Long = 0L,
    ) = EmailMessage(
        id = 1L,
        messageId = "<test@jago.com>",
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
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.ofHours(7))
        .toInstant()
        .toEpochMilli()

    @Test
    fun `a payment is an expense, with the merchant it went to`() {
        val parsed = runBlocking { source.parseEmail(email(fixture("payment"))) }.single()

        assertEquals(2_000L, parsed.amount)
        assertEquals("IDR", parsed.currency)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("BEl Shop", parsed.merchant)
        assertEquals(millis(2026, 8, 5, 13, 23), parsed.date)
        // Jago sends no reference number of any kind.
        assertNull(parsed.reference)
    }

    @Test
    fun `a transfer names the recipient, and the date has no comma`() {
        val parsed = runBlocking {
            source.parseEmail(
                email(fixture("transfer"), subject = "You have made a transfer"),
            )
        }.single()

        assertEquals(34_000L, parsed.amount)
        assertEquals(TransactionType.TRANSFER, parsed.type)
        assertEquals("NAMA PENERIMA", parsed.merchant)
        assertEquals(millis(2026, 7, 22, 13, 53), parsed.date)
    }

    @Test
    fun `a partner transaction labels with colons and names the partner`() {
        val parsed = runBlocking {
            source.parseEmail(
                email(fixture("partner"), subject = "You have made a transaction via GoPay"),
            )
        }.single()

        assertEquals(41_100L, parsed.amount)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("GoPay", parsed.merchant)
        assertEquals(millis(2026, 8, 11, 19, 56), parsed.date)
    }

    @Test
    fun `a debit card purchase states only its amount, so the mail's date is used`() {
        val arrived = millis(2026, 8, 6, 2, 49)
        val parsed = runBlocking {
            source.parseEmail(
                email(
                    fixture("debitcard"),
                    subject = "You have made a transaction using your debit card",
                    date = arrived,
                ),
            )
        }.single()

        assertEquals(33_189L, parsed.amount)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(arrived, parsed.date)
        // Nothing but an amount: no summary means no merchant to name.
        assertNull(parsed.merchant)
    }

    @Test
    fun `every notification is claimed, and nothing else from the same sender is`() {
        assertTrue(source.isEmailForProvider(email(fixture("payment"))))
        assertTrue(source.isEmailForProvider(email(fixture("transfer"))))
        assertTrue(source.isEmailForProvider(email(fixture("partner"))))
        assertTrue(source.isEmailForProvider(email(fixture("debitcard"))))

        // A promotion states an amount too, but no summary and no card.
        assertFalse(
            source.isEmailForProvider(
                email("Get Rp50.000 cashback when you pay with Jago this month!"),
            ),
        )
        assertFalse(source.isEmailForProvider(email("Your Jago OTP is 123456.")))
        assertFalse(
            source.isEmailForProvider(email(fixture("payment"), from = "promo@tokopedia.com")),
        )
    }
}
