package com.checkpointalerts.app.data

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Tennessee checkpoint source — the official list only.
 *
 * The Tennessee Highway Patrol publishes the full month's checkpoints as a
 * PDF on tn.gov (one file per month, every district, DATE / COUNTY /
 * LOCATION / TYPE rows). That is the ONLY source this app reads for
 * Tennessee. An earlier version also read aggregator sites that republish
 * the same announcements; the two copies worded each location differently,
 * so the same checkpoint showed up twice. One official source means one
 * entry per checkpoint.
 *
 * PDF: https://www.tn.gov/content/dam/tn/safety/documents/thp-checkpoints/
 *      {YYYY}/{MONTH}.pdf  (e.g. 2026/NOVEMBER.pdf)
 * The app fetches the current month and the next month, so checkpoints
 * announced early for next month already show up. A next-month file that
 * does not exist yet is normal and is skipped, not an error.
 *
 * The PDF has no times — only dates — so timeText is "Time not listed".
 * Seat-belt enforcement rows are out of scope and are dropped; sobriety
 * and driver-license rows are kept. It only ever returns what the list
 * shows — never invented data. If every fetch fails, fetch() throws and
 * the app keeps showing the last saved data with a "couldn't refresh"
 * state.
 */
class TennesseeSource : CheckpointSource {

    override val stateCode = "TN"
    override val stateName = "Tennessee"

    private val baseUrl =
        "https://www.tn.gov/content/dam/tn/safety/documents/thp-checkpoints"

    private val userAgent =
        "Mozilla/5.0 (Linux; Android 14; moto g) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    override suspend fun fetch(): List<Checkpoint> = withContext(Dispatchers.IO) {
        val found = mutableListOf<Checkpoint>()
        var anyOk = false
        for (month in listOf(YearMonth.now(), YearMonth.now().plusMonths(1))) {
            val pdf = downloadPdf(month) ?: continue
            val parsed = parsePdfBytes(pdf.bytes, pdf.url)
            if (parsed.isNotEmpty()) anyOk = true
            found += parsed
        }
        if (!anyOk && found.isEmpty()) {
            throw IOException("Could not load the THP checkpoint list")
        }
        found.distinctBy { it.id }
            .sortedWith(compareBy({ it.date }, { it.county }, { it.location }))
    }

    private data class PdfDownload(val bytes: ByteArray, val url: String)

    private fun downloadPdf(month: YearMonth): PdfDownload? {
        val monthName = month.month.name
        val urls = listOf(
            "$baseUrl/${month.year}/$monthName.pdf",
            "$baseUrl/archived-${month.year}/$monthName.pdf"
        )
        for (url in urls) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", userAgent)
                conn.connectTimeout = 25_000
                conn.readTimeout = 25_000
                if (conn.responseCode != HttpURLConnection.HTTP_OK) continue
                val bytes = conn.inputStream.readBytes()
                if (bytes.size > 1000 && isPdf(bytes)) {
                    return PdfDownload(bytes, url)
                }
            } catch (_: Exception) {
                // A missing next-month file is normal; try the next URL.
            }
        }
        return null
    }

    private fun isPdf(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == '%'.code.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'D'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()
    }

    /** Parse a downloaded THP PDF. Exposed for unit tests. */
    fun parsePdfBytes(
        bytes: ByteArray,
        sourceUrl: String,
        today: LocalDate = LocalDate.now()
    ): List<Checkpoint> {
        val text = try {
            PDDocument.load(ByteArrayInputStream(bytes)).use { doc ->
                PDFTextStripper().apply { sortByPosition = true }.getText(doc)
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return parsePdfText(text, sourceUrl, today)
    }

    /** Parse the extracted text of a THP monthly PDF. Exposed for unit tests. */
    fun parsePdfText(
        text: String,
        sourceUrl: String,
        today: LocalDate = LocalDate.now()
    ): List<Checkpoint> {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { !it.contains("CHECKPOINT ENFORCEMENT") }
            .filter { !DISTRICT_HEADER.matches(it) }
            .filter { it !in COLUMN_WORDS }
            .filter { !(it.contains("DATE") && it.contains("COUNTY") && it.contains("LOCATION")) }
            .toList()

        // Group lines into records. Each record starts at a line carrying a
        // date; the location text can wrap onto lines above and below that
        // line. A wrapped fragment belongs to the record below it when the
        // previous record's location reads complete, and to the record above
        // it when that location ends mid-phrase (open paren, trailing "of",
        // "just", ...). That is how the official PDF lays its wrapped rows
        // out — verified against the real November 2026 file.
        val records = mutableListOf<RawRecord>()
        var cur: RawRecord? = null
        val pending = mutableListOf<String>()
        for (line in lines) {
            val dateMatch = DATE_REGEX.find(line)
            if (dateMatch != null) {
                cur?.let { records += it }
                cur = RawRecord(
                    dateText = dateMatch.value,
                    preFragments = pending.toList(),
                    dateLineRest = line.substring(dateMatch.range.last + 1).trim(),
                    postFragments = mutableListOf()
                )
                pending.clear()
            } else {
                val current = cur
                if (current == null) {
                    pending += line
                } else {
                    val locSoFar = (
                        current.preFragments +
                            lineLocation(current.dateLineRest) +
                            current.postFragments
                        ).filter { it.isNotEmpty() }.joinToString(" ")
                    if (locationIncomplete(locSoFar)) {
                        current.postFragments += line
                    } else {
                        pending += line
                    }
                }
            }
        }
        cur?.let { records += it }

        val out = mutableListOf<Checkpoint>()
        for (rec in records) {
            parseRecord(rec, sourceUrl, today)?.let { out += it }
        }
        return out.distinctBy { it.id }
            .sortedWith(compareBy({ it.date }, { it.county }, { it.location }))
    }

    private fun parseRecord(
        rec: RawRecord,
        sourceUrl: String,
        today: LocalDate
    ): Checkpoint? {
        val date = parsePdfDate(rec.dateText) ?: return null
        if (date.isBefore(today)) return null
        val county = matchCounty(rec.dateLineRest) ?: return null
        val fullText = (rec.preFragments + rec.dateLineRest + rec.postFragments)
            .joinToString(" ")
        val lower = fullText.lowercase(Locale.US)
        // Seat-belt enforcement is out of scope for this app.
        if (lower.contains("seat belt") || lower.contains("seatbelt")) return null
        val type = if (LICENSE_REGEX.containsMatchIn(fullText)) {
            CheckpointType.LICENSE
        } else {
            CheckpointType.SOBRIETY
        }
        val location = (rec.preFragments + lineLocation(rec.dateLineRest) + rec.postFragments)
            .map { cleanLocation(it) }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .let { cleanLocation(it) }
            .takeIf { it.length >= 4 } ?: return null
        val normalized = location.lowercase(Locale.US).replace(Regex("\\s+"), " ")
        return Checkpoint(
            id = "TN|${county.first}|$date|${normalized.hashCode()}",
            stateCode = "TN",
            county = county.first,
            city = "",
            location = location,
            date = date,
            timeText = "Time not listed",
            type = type,
            sourceUrl = sourceUrl
        )
    }

    /** A date line's rest (everything after the date) minus its county and
     *  type words — i.e. just the location text on that line, if any. */
    private fun lineLocation(rest: String): String {
        var s = rest
        matchCounty(s)?.let { (_, start, end) -> s = s.removeRange(start, end) }
        s = TYPE_REGEX.replace(s, "")
        return s.replace(Regex("\\s+"), " ").trim().trim('.', ',')
    }

    private fun cleanLocation(raw: String): String {
        var s = TYPE_REGEX.replace(raw, "")
        s = s.replace(Regex("\\s+"), " ").trim()
        s = s.replace("( ", "(").replace(" )", ")")
        s = s.replace(Regex("\\s+"), " ").trim().trim('.', ',')
        return s
    }

    /** True when a location string ends mid-phrase, so the next line is a
     *  continuation of it rather than a new row's text. */
    private fun locationIncomplete(loc: String): Boolean {
        val t = loc.trim()
        if (t.isEmpty()) return false
        if (t.count { it == '(' } > t.count { it == ')' }) return true
        val last = t.split(Regex("\\s+")).last()
            .lowercase(Locale.US).trim('.', ',', ')', ':')
        return last in CONTINUE_WORDS
    }

    /** Earliest whole-word county name in [text], with its span. */
    private fun matchCounty(text: String): Triple<String, Int, Int>? {
        var best: Triple<String, Int, Int>? = null
        for (name in TnCounties.ALL) {
            val m = Regex("\\b${Regex.escape(name)}\\b", RegexOption.IGNORE_CASE)
                .find(text) ?: continue
            if (best == null || m.range.first < best.second) {
                best = Triple(name, m.range.first, m.range.last + 1)
            }
        }
        return best
    }

    private data class RawRecord(
        val dateText: String,
        val preFragments: List<String>,
        val dateLineRest: String,
        val postFragments: MutableList<String>
    )

    companion object {
        private val DATE_REGEX = Regex("(\\d{1,2}/\\d{1,2}/\\d{4})")

        /** Any type word the PDF prints: sobriety (including the PDF's own
         *  "Sobreity" typo), DUI/DWI, driver license / DL, seat belt. */
        private val TYPE_REGEX = Regex(
            "\\b(seat belt|seatbelt|sobreity|sobriety|dui|dwi|driver license|license|dl)\\b",
            RegexOption.IGNORE_CASE
        )

        private val LICENSE_REGEX = Regex(
            "\\b(dl|driver license|license)\\b",
            RegexOption.IGNORE_CASE
        )

        private val DISTRICT_HEADER = Regex("^[A-Z ]+DISTRICT \\d+$")

        private val COLUMN_WORDS = setOf("DATE", "COUNTY", "LOCATION", "TYPE")

        private val CONTINUE_WORDS = setOf(
            "of", "near", "at", "just", "the", "and", "or", "a", "an",
            "to", "for", "with", "by", "from", "in", "on", "(", "-", ",", "&", "@"
        )

        private val pdfDateFormat =
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US)

        /** Parse dates like "11/20/2026". */
        fun parsePdfDate(raw: String): LocalDate? {
            return try {
                LocalDate.parse(raw.trim(), pdfDateFormat)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
