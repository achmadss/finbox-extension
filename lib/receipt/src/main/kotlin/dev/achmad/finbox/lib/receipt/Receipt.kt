package dev.achmad.finbox.lib.receipt

import dev.achmad.finbox.parser.EmailMessage
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.jsoup.Jsoup

/**
 * A bank receipt, flattened to one line per row.
 *
 * Every bank lays its receipt out differently, but they all state fields as a
 * label and a value — either on one line ("Nominal Rp13.000", BRI) or on two
 * ("Amount" / "Rp 2.000", Jago). That, the money format and the date format are
 * all a parser really shares, so that is all this holds.
 */
class Receipt(val lines: List<String>) {

    /**
     * The value that follows one of [labels], on the same line or the next one.
     *
     * The next-line form is why the following line is rejected when it is
     * itself a label: an absent field would otherwise take its neighbour's
     * value.
     */
    fun field(vararg labels: String): String? {
        for ((index, line) in lines.withIndex()) {
            val label = labels.firstOrNull { line.startsWith(it, ignoreCase = true) } ?: continue
            val value = line.substring(label.length).trimStart(':', ' ', '\t').trim()
            if (value.isNotEmpty()) return value
            return lines.getOrNull(index + 1)
                ?.takeIf { next -> labels.none { next.startsWith(it, ignoreCase = true) } }
        }
        return null
    }

    /** [field], read as money. */
    fun amount(vararg labels: String): Long? = field(*labels)?.let(::parseAmount)

    /**
     * The transaction's own timestamp.
     *
     * Labelled first; some receipts (a BRIZZI top up, say) state the time in
     * the header and nowhere else, so any parseable line will do as a fallback.
     * Returns null when the receipt states no date at all — the caller should
     * fall back to [EmailMessage.date].
     */
    fun date(vararg labels: String): Long? =
        field(*labels)?.let(::parseTimestamp) ?: lines.firstNotNullOfOrNull(::parseTimestamp)

    /**
     * The timestamp of a receipt that states the day and the clock as two rows:
     * "Tanggal 03-Agu-2026" then "Waktu 06:23:30 WIB" (BNI), "Tanggal 27 Jul
     * 2026" then "Jam 15:22:45 WIB" (Mandiri).
     *
     * Found by shape rather than by label, because both banks head the section
     * with a label of its own — "Tanggal & waktu transaksi" is what [field]
     * would answer with. The clock is taken from the day's own line or below it,
     * never above, so an unrelated time earlier in the mail cannot win. The zone
     * travels with the clock, hence the two parsed as one string.
     */
    fun splitDate(): Long? {
        val day = lines.indexOfFirst { parseTimestamp(it) != null }.takeIf { it >= 0 } ?: return null
        val clock = lines.drop(day).firstNotNullOfOrNull { CLOCK.find(it)?.value }.orEmpty()
        return parseTimestamp("${lines[day]} $clock")
    }

    /** The first `Rp …` anywhere, for receipts that state the amount in prose. */
    fun statedAmount(): Long? = lines.firstNotNullOfOrNull(::findAmount)

    companion object {
        /**
         * The body, flattened here rather than taken as the app flattened it.
         *
         * html wins when there is any, because the layout is what pairs a label
         * with its value and this is where that decision belongs: a bank whose
         * markup flattens badly is then a fix in this repo, not a release of
         * the app.
         */
        fun of(email: EmailMessage): Receipt =
            if (email.bodyHtml.isNotBlank()) ofHtml(email.bodyHtml) else Receipt(email.bodyText.toLines())

        /**
         * Flattens html to one line per row.
         *
         * `Jsoup.text()` alone returns a single line, which loses what pairs a
         * label with its value — these mails put both in one table row and
         * nothing else marks where a field ends.
         */
        fun ofHtml(html: String): Receipt {
            val document = Jsoup.parse(html)
            document.outputSettings().prettyPrint(false)
            document.select("style, script, head").remove()
            // A literal marker, not a newline: text() collapses real whitespace,
            // so the break has to survive as characters and be put back after.
            document.select("br, tr, p, div, li, h1, h2, h3, h4, table").before(MARKER)
            return Receipt(document.text().replace(MARKER, "\n").toLines())
        }

        private const val MARKER = "\\n"

        private fun String.toLines(): List<String> =
            lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    }
}

/**
 * "Rp13.000", "Rp 2.000", "Rp1.000.000,00" — dots group, a comma is cents, and
 * the cents are dropped since a ledger in rupiah has none worth keeping.
 *
 * Lenient by design: this is for a value already found by its label, so
 * whatever digits are in it are the amount.
 *
 * ponytail: Indonesian grouping only. A bank that writes "1,000,000.00" needs
 * the separators sniffed rather than assumed; none of them do.
 */
fun parseAmount(raw: String): Long? {
    val digits = Regex("[\\d.,]+").find(raw)?.value ?: return null
    return digits.substringBeforeLast(',').replace(".", "").replace(",", "").toLongOrNull()
}

/**
 * The first amount in a line of prose, e.g. "a transaction of Rp33.189 using
 * your debit card".
 *
 * Unlike [parseAmount] this insists on a currency marker, because an unlabelled
 * line is as likely to hold a card number or a terminal id as a price.
 */
fun findAmount(text: String): Long? =
    CURRENCY.find(text)?.groupValues?.get(1)?.let(::parseAmount)

/**
 * "11 Aug 2026, 10:30:27 WIB", "05 August 2026, 13:23 WIB",
 * "11 August 2026 19:56 WIB", "13 Agustus 2026 , 09:16:10", "03-Agu-2026".
 *
 * The stated zone wins when there is one: a receipt saying 13:23 WIB means the
 * same instant wherever the phone happens to be. [fallbackZone] covers the rest.
 */
fun parseTimestamp(text: String, fallbackZone: ZoneId = ZoneId.systemDefault()): Long? {
    val match = TIMESTAMP.find(text) ?: return null
    val (day, monthName, year, hour, minute, second) = match.destructured
    val month = MONTHS[monthName.lowercase().take(3)] ?: return null
    val zone = ZONE.find(text)?.let { ZONES[it.value.lowercase()] } ?: fallbackZone
    return try {
        LocalDateTime.of(
            year.toInt(),
            month,
            day.toInt(),
            hour.toIntOrNull() ?: 0,
            minute.toIntOrNull() ?: 0,
            second.toIntOrNull() ?: 0,
        ).atZone(zone).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }
}

/**
 * What kind of transaction [text] describes — a subject and the receipt's own
 * "Jenis Transaksi" are the usual inputs.
 *
 * Expense unless something says otherwise, because that is what the great
 * majority of these emails are.
 */
/**
 * Whether the text reads as money arriving rather than leaving.
 *
 * Only this much is shared. What a provider calls a transaction is the
 * provider's own vocabulary and belongs in its source, which declares the kinds
 * it can produce and maps its own wording onto them — a lookup table here would
 * have to know every bank's phrasing, and would quietly mis-file the first one
 * it didn't.
 *
 * Money in is the exception because every provider says it the same handful of
 * ways, and getting it wrong flips the sign on the transaction.
 */
fun isIncome(vararg text: String): Boolean = INCOME.containsMatchIn(text.joinToString(" "))

private val CURRENCY = Regex("(?:Rp|IDR)\\s*([\\d.,]+)", RegexOption.IGNORE_CASE)

/**
 * `11 Aug 2026, 10:30:27` — the time is optional, the seconds too, and a hyphen
 * groups the date as readily as a space ("03-Agu-2026", BNI).
 */
private val TIMESTAMP = Regex(
    "(\\d{1,2})[\\s-]+([A-Za-z]+)[\\s-]+(\\d{4})(?:\\s*,?\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?",
)

/** A clock, with the zone that qualifies it: "06:23:30 WIB", "10:53". */
private val CLOCK = Regex("\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*(?:WIB|WITA|WIT))?", RegexOption.IGNORE_CASE)

// Longest first: WITA would otherwise be read as WIT.
private val ZONE = Regex("\\b(WITA|WIB|WIT)\\b", RegexOption.IGNORE_CASE)

private val ZONES = mapOf(
    "wib" to ZoneOffset.ofHours(7),
    "wita" to ZoneOffset.ofHours(8),
    "wit" to ZoneOffset.ofHours(9),
)

/** Indonesian and English month names, by their first three letters. */
private val MONTHS = mapOf(
    "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
    "mei" to 5, "may" to 5, "jun" to 6, "jul" to 7,
    "agu" to 8, "aug" to 8, "sep" to 9, "okt" to 10, "oct" to 10,
    "nov" to 11, "des" to 12, "dec" to 12,
)

// Matched at a word start only, or "Biaya Termasuk PPN" on a BRI receipt would
// read as money coming in. No trailing boundary, so "transferred" still counts.
private fun words(vararg words: String) =
    Regex("\\b(${words.joinToString("|")})", RegexOption.IGNORE_CASE)

private val INCOME = words("masuk", "diterima", "kredit", "refund", "received", "incoming", "cashback")


