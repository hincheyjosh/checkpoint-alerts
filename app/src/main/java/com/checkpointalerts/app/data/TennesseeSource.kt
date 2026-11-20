package com.checkpointalerts.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Tennessee checkpoint source.
 *
 * Tennessee law requires sobriety checkpoints to be announced in advance, and
 * the Tennessee Highway Patrol publishes them via press releases. There is no
 * official API or feed, so this source reads the public listings that
 * republish those announcements:
 *  - duicheckpointlocator.com/location/tennessee (paginated)
 *  - duiblock.net/dui-checkpoint-locations/tennessee/
 *
 * The parser is defensive by design: those pages can change layout, so it
 * looks for HTML tables first, falls back to list items, and drops any row it
 * cannot classify. It only ever returns what the source lists — never
 * invented data. If every source fails, fetch() throws and the app keeps
 * showing the last saved data with a "couldn't refresh" state.
 */
class TennesseeSource : CheckpointSource {

    override val stateCode = "TN"
    override val stateName = "Tennessee"

    private val pageUrls = listOf(
        "https://www.duicheckpointlocator.com/location/tennessee",
        "https://www.duicheckpointlocator.com/location/tennessee/page/2",
        "https://www.duicheckpointlocator.com/location/tennessee/page/3",
        "https://www.duiblock.net/dui-checkpoint-locations/tennessee/"
    )

    private val userAgent =
        "Mozilla/5.0 (Linux; Android 14; moto g) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    override suspend fun fetch(): List<Checkpoint> = withContext(Dispatchers.IO) {
        val found = mutableListOf<Checkpoint>()
        var anyPageOk = false
        for (url in pageUrls) {
            try {
                val doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(25_000)
                    .get()
                val before = found.size
                parseDocument(doc, url, found)
                if (found.size > before) {
                    anyPageOk = true
                } else if (url.contains("/page/")) {
                    // Paginated listings run newest-first; an empty page means
                    // later pages only hold older (already past) entries.
                    break
                }
            } catch (_: Exception) {
                // One dead source must not kill the whole refresh; keep going.
            }
        }
        if (!anyPageOk && found.isEmpty()) {
            throw IOException("Could not load checkpoint listings")
        }
        found.distinctBy { it.id }
            .sortedWith(compareBy({ it.date }, { it.county }, { it.location }))
    }

    /** Parse an already-fetched page. Exposed for unit tests. */
    fun parseHtml(html: String, sourceUrl: String): List<Checkpoint> {
        val out = mutableListOf<Checkpoint>()
        parseDocument(Jsoup.parse(html, sourceUrl), sourceUrl, out)
        return out.distinctBy { it.id }
            .sortedWith(compareBy({ it.date }, { it.county }, { it.location }))
    }

    private fun parseDocument(doc: Document, sourceUrl: String, out: MutableList<Checkpoint>) {
        // Strategy 1: HTML tables — the layout both aggregators use.
        for (table in doc.select("table")) {
            for (row in table.select("tr")) {
                val cells = row.select("td")
                    .map { it.text().trim() }
                    .filter { it.isNotEmpty() }
                if (cells.size < 3) continue
                parseRow(cells, sourceUrl)?.let { out += it }
            }
        }
        if (out.isNotEmpty()) return
        // Strategy 2: list/article items that mention a date and a county.
        for (el in doc.select("li, article, p")) {
            val text = el.text().trim()
            if (text.length < 20 || text.length > 600) continue
            parseFreeText(text, sourceUrl)?.let { out += it }
        }
    }

    private fun parseRow(cells: List<String>, sourceUrl: String): Checkpoint? {
        val rowText = cells.joinToString(" ")
        val type = classify(rowText, defaultSobriety = true) ?: return null

        var date: LocalDate? = null
        var dateCell = -1
        for ((i, cell) in cells.withIndex()) {
            val parsed = parseDate(cell)
            if (parsed != null) {
                date = parsed
                dateCell = i
                break
            }
        }
        val d = date ?: return null
        if (d.isBefore(LocalDate.now())) return null

        var county: String? = null
        var countyCell = -1
        for ((i, cell) in cells.withIndex()) {
            if (i == dateCell) continue
            val match = matchCounty(cell)
            if (match != null) {
                county = match; countyCell = i; break
            }
        }
        val countyName = county ?: return null

        val location = cells.withIndex()
            .filter { it.index != dateCell && it.index != countyCell }
            .map { it.value.trim() }
            .filter { it.isNotEmpty() && !it.equals("Tennessee", ignoreCase = true) && !it.equals("TN", ignoreCase = true) }
            .maxByOrNull { it.length }
            ?.let { cleanLocation(it) }
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val time = TIME_REGEX.find(rowText)?.value ?: "Time not listed"
        return Checkpoint(
            id = "TN|$countyName|$d|${location.hashCode()}",
            stateCode = "TN",
            county = countyName,
            city = "",
            location = location,
            date = d,
            timeText = time,
            type = type,
            sourceUrl = sourceUrl
        )
    }

    private fun parseFreeText(text: String, sourceUrl: String): Checkpoint? {
        val type = classify(text, defaultSobriety = false) ?: return null
        val d = parseDate(text) ?: return null
        if (d.isBefore(LocalDate.now())) return null
        val county = TnCounties.ALL.firstOrNull { text.contains(it, ignoreCase = true) }
            ?: return null
        val location = cleanLocation(
            text.replace(DATE_REGEX, "")
                .replace(county, "", ignoreCase = true)
                .replace("Tennessee", "", ignoreCase = true)
                .trim().trim(' ', '-', '–', ':', '.')
        ).takeIf { it.length >= 8 } ?: return null
        val time = TIME_REGEX.find(text)?.value ?: "Time not listed"
        return Checkpoint(
            id = "TN|$county|$d|${location.hashCode()}",
            stateCode = "TN",
            county = county,
            city = "",
            location = location,
            date = d,
            timeText = time,
            type = type,
            sourceUrl = sourceUrl
        )
    }

    /** Sobriety vs license. Seat-belt and other enforcement types are out of
     *  scope for this app, so those rows are dropped. Table rows come from a
     *  DUI-checkpoint listing, so a row with no type words at all is a
     *  sobriety checkpoint by default; free-text items must say so
     *  explicitly, or they are dropped. */
    private fun classify(text: String, defaultSobriety: Boolean): CheckpointType? {
        val lower = text.lowercase(Locale.US)
        if (lower.contains("seat belt") || lower.contains("seatbelt")) return null
        return when {
            lower.contains("license") -> CheckpointType.LICENSE
            lower.contains("sobriety") || lower.contains("dui") ||
                lower.contains("dwi") || lower.contains("checkpoint") ->
                CheckpointType.SOBRIETY
            defaultSobriety -> CheckpointType.SOBRIETY
            else -> null
        }
    }

    private fun matchCounty(cell: String): String? {
        val trimmed = cell.trim()
        return TnCounties.ALL.firstOrNull { name ->
            trimmed.equals(name, ignoreCase = true) ||
                trimmed.equals("$name County", ignoreCase = true) ||
                trimmed.contains(name, ignoreCase = true)
        }
    }

    // Aggregators rewrite locations into sentences ("X identified as a
    // checkpoint site"). Strip the boilerplate so the card shows the place.
    private val locationSuffixes = listOf(
        "identified as a checkpoint site",
        "designated for checkpoint activity",
        "selected for checkpoint operations",
        "has been designated for checkpoint activity",
        "is listed as a location for a tennessee highway patrol sobriety checkpoint",
        "has been selected for an upcoming sobriety checkpoint",
        "identified as a sobriety checkpoint site",
        "designated as a checkpoint location",
        "selected by the tennessee highway patrol for checkpoint operations",
        "has been identified as a checkpoint location",
        "has been announced as a checkpoint location"
    )

    private val locationPrefixes = listOf(
        "a sobriety checkpoint is expected along ",
        "a checkpoint is expected on ",
        "a checkpoint is expected along ",
        "authorities have scheduled checkpoint operations at ",
        "checkpoint operations are expected on ",
        "a driver license checkpoint is planned near ",
        "a driver license checkpoint is planned along ",
        "a sobriety checkpoint is planned on ",
        "a sobriety checkpoint is planned along ",
        "the tennessee highway patrol has reported sobriety checkpoint operations near ",
        "authorities are planning checkpoint operations on ",
        "checkpoint activity is planned along ",
        "checkpoint activity is scheduled at ",
        "authorities have planned checkpoint operations at ",
        "a sobriety checkpoint is scheduled on ",
        "a sobriety checkpoint is expected near ",
        "a checkpoint is scheduled near "
    )

    private fun cleanLocation(raw: String): String {
        var s = raw.trim()
        val lower = s.lowercase(Locale.US)
        for (prefix in locationPrefixes) {
            if (lower.startsWith(prefix)) {
                s = s.substring(prefix.length).trim()
                break
            }
        }
        val lower2 = s.lowercase(Locale.US)
        for (suffix in locationSuffixes) {
            val idx = lower2.indexOf(suffix)
            if (idx > 0) {
                s = s.substring(0, idx).trim()
                break
            }
        }
        return s.trim().trimEnd('.', ',').trim()
    }

    companion object {
        private val TIME_REGEX =
            Regex("(\\d{1,2}(:\\d{2})?\\s*[ap]\\.?m\\.?)", RegexOption.IGNORE_CASE)

        private val DATE_REGEX = Regex(
            "(Mon|Tue|Wed|Thu|Fri|Sat|Sun)[a-z]*,?\\s+" +
                "(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s+" +
                "\\d{1,2}(st|nd|rd|th)?,?\\s+\\d{4}",
            RegexOption.IGNORE_CASE
        )

        private val dateFormats = listOf(
            "EEEE, MMMM d, yyyy",
            "EEE, MMMM d, yyyy",
            "EEEE, MMM d, yyyy",
            "EEE, MMM d, yyyy",
            "MMMM d, yyyy",
            "MMM d, yyyy"
        ).map { DateTimeFormatter.ofPattern(it, Locale.US) }

        /** Parse dates like "Fri, Nov 20, 2026", "Saturday, October 31, 2026",
         *  "November 20th, 2026", or a date embedded in a longer sentence. */
        fun parseDate(raw: String): LocalDate? {
            var s = raw.trim()
                .replace(Regex("(\\d)(st|nd|rd|th)\\b"), "$1")
            for (fmt in dateFormats) {
                try {
                    return LocalDate.parse(s, fmt)
                } catch (_: DateTimeParseException) {
                }
            }
            val m = DATE_REGEX.find(s) ?: return null
            val found = m.value
                .replace(Regex("(\\d)(st|nd|rd|th)\\b"), "$1")
            for (fmt in dateFormats) {
                try {
                    return LocalDate.parse(found, fmt)
                } catch (_: DateTimeParseException) {
                }
            }
            return null
        }
    }
}
