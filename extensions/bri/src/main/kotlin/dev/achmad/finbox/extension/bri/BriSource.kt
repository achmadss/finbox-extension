package dev.achmad.finbox.extension.bri

import dev.achmad.finbox.extension.EmailMessage
import dev.achmad.finbox.extension.EmailQuery
import dev.achmad.finbox.extension.ParsedTransaction
import dev.achmad.finbox.extension.Source
import dev.achmad.finbox.extension.TransactionSource
import dev.achmad.finbox.extension.TransactionType
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Source for Bank BRI transaction notification emails.
 *
 * Typical body:
 * ```
 * PEMBERITAHUAN TRANSAKSI
 * KARTU : 5241 08** **** 1234
 * TANGGAL : 26/01/2026 10:30:12
 * TRANSAKSI : PEMBAYARAN TOKOPEDIA
 * JUMLAH : Rp1.000.000,00
 * SALDO : Rp5.000.000,00
 * ```
 *
 * Parsing is deliberately tolerant: it returns an empty list whenever amount
 * or date cannot be extracted, and the app drops the email.
 */
@Source
class BriSource : TransactionSource {

    // Everything BRI sends, narrowed to the sending domain. The app adds the
    // import window; isEmailForProvider throws away the statements, OTPs and
    // promos that come back with the notifications.
    override val emailQuery = EmailQuery(
        from = listOf("bri.co.id"),
    )

    override fun isEmailForProvider(email: EmailMessage): Boolean {
        val from = email.from.lowercase()
        if ("bri.co.id" !in from) return false
        val text = email.bodyText.ifBlank { email.bodyHtml }
        // A transaction mail always states an amount and a date.
        return "jumlah" in text.lowercase() && "tanggal" in text.lowercase()
    }

    override suspend fun parseEmail(email: EmailMessage): List<ParsedTransaction> {
        val text = email.bodyText.ifBlank {
            email.bodyHtml.replace(Regex("<[^>]+>"), "\n")
        }
        if (text.isBlank()) return emptyList()

        val amount = extractAmount(text) ?: return emptyList()
        val date = extractDate(text) ?: return emptyList()
        val type = detectType(text)
        val description = extractField(text, "transaksi")
            ?: extractField(text, "ket", "keterangan")
            ?: email.subject.trim().ifBlank { null }

        return listOf(
            ParsedTransaction(
                date = date,
                amount = amount,
                currency = "IDR",
                type = type,
                merchant = null,
                description = description,
                reference = extractField(text, "referensi", "ref"),
                confidence = 0.9f,
            ),
        )
    }

    private fun detectType(text: String): TransactionType {
        val lower = text.lowercase()
        return when {
            lower.contains("masuk") || lower.contains("diterima") || lower.contains("kredit") ||
                lower.contains("incoming") -> TransactionType.INCOME
            lower.contains("transfer") || lower.contains("rekening") -> TransactionType.TRANSFER
            else -> TransactionType.EXPENSE
        }
    }

    /** Extracts the value of the first line starting with one of [labels] followed by a colon. */
    private fun extractField(text: String, vararg labels: String): String? {
        val pattern = labels.joinToString("|") { Regex.escape(it) }
        val regex = Regex("(?i)(?:^|[\\r\\n])\\s*($pattern)\\s*[:\\.]?\\s*([^\\r\\n]+)")
        val match = regex.find(text) ?: return null
        val value = match.groupValues[2].trim().trimEnd(';').trim()
        if (value.isEmpty() || value.matches(Regex("(?i)^${pattern}$"))) return null
        return value
    }

    private fun extractAmount(text: String): Long? {
        val rupiah = Regex("(?i)(?:Rp|IDR)\\s*([\\d.,]+)").find(text)
            ?.groupValues?.get(1)
        val bare = if (rupiah == null) {
            Regex("(?i)(?:jumlah|amount|nominal)\\s*[:=]?\\s*([\\d.,]+)").find(text)
                ?.groupValues?.get(1)
        } else null
        val raw = rupiah ?: bare ?: return null
        return parseNumber(raw)
    }

    /** Parses "1.000.000,00", "1,000,000.00", "1000000" etc. into whole units. */
    private fun parseNumber(raw: String): Long? {
        val s = raw.trim().replace(" ", "")
        if (s.isEmpty()) return null
        val integerPart: String
        when {
            s.contains(',') && !s.contains('.') -> {
                val i = s.lastIndexOf(',')
                integerPart = s.substring(0, i).replace(",", "")
            }
            s.contains('.') && !s.contains(',') -> {
                integerPart = s.replace(".", "")
            }
            s.contains(',') && s.contains('.') -> {
                val i = if (s.lastIndexOf(',') > s.lastIndexOf('.')) s.lastIndexOf(',') else s.lastIndexOf('.')
                integerPart = s.substring(0, i).replace(".", "").replace(",", "")
            }
            else -> integerPart = s
        }
        return integerPart.toLongOrNull()
    }

    private fun extractDate(text: String): Long? {
        val match = Regex(
            "(?i)(?:tanggal|date)\\s*[:=]?\\s*(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4})(?:\\s+[,-]?\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?))?",
        ).find(text) ?: return null

        val parts = match.groupValues[1].split('/', '.', '-').map { it.toInt() }
        if (parts.size != 3) return null
        val (day, month, yearRaw) = parts
        val year = if (yearRaw < 100) yearRaw + 2000 else yearRaw

        val time = match.groupValues[2]
        val hm = if (time.isNotBlank()) {
            time.split(':').map { it.toInt() }.let { it[0] to it[1] }
        } else 0 to 0

        return try {
            LocalDateTime.of(year, month, day, hm.first, hm.second)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}
