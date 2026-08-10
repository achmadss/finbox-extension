package dev.achmad.finbox.extension.bri

import dev.achmad.finbox.extension.EmailMessage
import dev.achmad.finbox.extension.ParsedTransaction
import dev.achmad.finbox.extension.TransactionSource
import dev.achmad.finbox.extension.TransactionType
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Parser for Bank BRI transaction notification emails.
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
 * The parser is deliberately tolerant: it returns an empty list (email goes
 * to the unrecognized queue) whenever amount or date cannot be extracted.
 */
class BriParser : TransactionSource {

    override val name: String = "Bank BRI"
    override val versionId: Int = 1
    override val id: Long = sourceId(name, versionId)

    override fun isEmailForProvider(email: EmailMessage): Boolean {
        val from = email.from.lowercase()
        val subject = email.subject.lowercase()
        return "bri.co.id" in from || "bri" in subject
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

    companion object {
        /** Deterministic id convention: MD5("name.lowercase()/versionId"), first 8 bytes as positive Long. */
        fun sourceId(name: String, versionId: Int): Long {
            val digest = java.security.MessageDigest.getInstance("MD5")
                .digest("${name.lowercase()}/$versionId".toByteArray())
            var value = 0L
            for (i in 0 until 8) {
                value = (value shl 8) or (digest[i].toLong() and 0xff)
            }
            return value and Long.MAX_VALUE
        }
    }
}
