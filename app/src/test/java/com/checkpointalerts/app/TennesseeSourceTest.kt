package com.checkpointalerts.app

import com.checkpointalerts.app.data.CheckpointType
import com.checkpointalerts.app.data.TennesseeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parser tests against a fixture in the shape of the public Tennessee
 * checkpoint listings (HTML table: State | County | City | Location |
 * Date & Time). Dates are generated relative to today so the tests stay
 * valid whenever they run.
 */
class TennesseeSourceTest {

    private val shortFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)

    private fun fixture(vararg rows: String): String {
        val body = rows.joinToString("\n")
        return "<html><body><table>" +
            "<tr><th>State</th><th>County</th><th>City</th><th>Location</th>" +
            "<th>Date &amp; Time</th></tr>" +
            body +
            "</table></body></html>"
    }

    @Test
    fun parsesTableRowsAndCleansLocations() {
        val d1 = LocalDate.now().plusDays(1)
        val d2 = LocalDate.now().plusDays(2)
        val d3 = LocalDate.now().plusDays(3)
        val html = fixture(
            "<tr><td>Tennessee</td><td>Sevier</td><td></td>" +
                "<td>Collier Drive at Rainbow Road designated for checkpoint activity.</td>" +
                "<td>${d1.format(shortFmt)}</td></tr>",
            "<tr><td>Tennessee</td><td>Jefferson</td><td></td>" +
                "<td>State Route 113 near Nina Road identified as a checkpoint site.</td>" +
                "<td>${d1.format(shortFmt)}</td></tr>",
            "<tr><td>Tennessee</td><td>Knox</td><td></td>" +
                "<td>Cherokee Trail near Cherokee Bluff</td>" +
                "<td>${d2.format(shortFmt)}</td></tr>",
            "<tr><td>Tennessee</td><td>McNairy</td><td></td>" +
                "<td>A driver license checkpoint is planned near the intersection " +
                "of State Route 22 and State Route 69.</td>" +
                "<td>${d3.format(shortFmt)}</td></tr>",
            "<tr><td>Tennessee</td><td>Hardeman</td><td></td>" +
                "<td>A seat belt checkpoint is scheduled along State Route 18 North " +
                "near the 30-mile marker.</td>" +
                "<td>${d1.format(shortFmt)}</td></tr>",
            // Duplicate of the Sevier row — must be deduped.
            "<tr><td>Tennessee</td><td>Sevier</td><td></td>" +
                "<td>Collier Drive at Rainbow Road designated for checkpoint activity.</td>" +
                "<td>${d1.format(shortFmt)}</td></tr>"
        )
        val result = TennesseeSource().parseHtml(html, "https://example.test/tn")
        assertEquals(4, result.size)

        val sevier = result.first { it.county == "Sevier" }
        assertEquals("Collier Drive at Rainbow Road", sevier.location)
        assertEquals(d1, sevier.date)
        assertEquals(CheckpointType.SOBRIETY, sevier.type)

        val jefferson = result.first { it.county == "Jefferson" }
        assertEquals("State Route 113 near Nina Road", jefferson.location)

        val license = result.first { it.county == "McNairy" }
        assertEquals(CheckpointType.LICENSE, license.type)
        assertEquals(
            "the intersection of State Route 22 and State Route 69",
            license.location
        )

        assertTrue(result.none { it.county == "Hardeman" })
    }

    @Test
    fun dropsPastDates() {
        val past = LocalDate.now().minusDays(2)
        val future = LocalDate.now().plusDays(5)
        val html = fixture(
            "<tr><td>Tennessee</td><td>Knox</td><td></td>" +
                "<td>Some Road at Main Street</td>" +
                "<td>${past.format(shortFmt)}</td></tr>",
            "<tr><td>Tennessee</td><td>Knox</td><td></td>" +
                "<td>Other Road at Elm Street</td>" +
                "<td>${future.format(shortFmt)}</td></tr>"
        )
        val result = TennesseeSource().parseHtml(html, "https://example.test/tn")
        assertEquals(1, result.size)
        assertEquals(future, result[0].date)
    }

    @Test
    fun parsesFullWeekdayDates() {
        val d = LocalDate.now().plusDays(4)
        val full = DateTimeFormatter
            .ofPattern("EEEE, MMMM d, yyyy", Locale.US).format(d)
        val html = fixture(
            "<tr><td>Tennessee</td><td>Greene</td><td></td>" +
                "<td>State Route 93 at the old Lane's Grocery market</td>" +
                "<td>$full</td></tr>"
        )
        val result = TennesseeSource().parseHtml(html, "https://example.test/tn")
        assertEquals(1, result.size)
        assertEquals(d, result[0].date)
    }

    @Test
    fun returnsEmptyForUnparseablePages() {
        val html = "<html><body><p>Nothing here yet.</p></body></html>"
        val result = TennesseeSource().parseHtml(html, "https://example.test/tn")
        assertTrue(result.isEmpty())
    }
}
