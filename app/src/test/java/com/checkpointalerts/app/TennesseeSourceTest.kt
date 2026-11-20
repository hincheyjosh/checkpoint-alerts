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
 * Parser tests for the official THP monthly PDF (tn.gov), in the shape its
 * text extracts: district sections, a DATE / COUNTY / LOCATION / TYPE
 * header, one row per checkpoint, with locations that wrap across lines.
 * Fixture dates are generated relative to today so the tests stay valid
 * whenever they run. The real November 2026 file's extracted text is also
 * bundled as a test resource and parsed with a fixed "today".
 */
class TennesseeSourceTest {

    private val pdfDateFmt = DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US)

    private fun d(daysFromToday: Int): String =
        LocalDate.now().plusDays(daysFromToday.toLong()).format(pdfDateFmt)

    private fun pdfText(vararg lines: String): String {
        return "TENNESSEE HIGHWAY PATROL CHECKPOINT ENFORCEMENT\n\n" +
            "KNOXVILLE DISTRICT 1\n" +
            "DATE       COUNTY                   LOCATION                          TYPE\n" +
            lines.joinToString("\n")
    }

    @Test
    fun parsesThpPdfRows() {
        val text = pdfText(
            "${d(1)}    Sevier   Collier Drive @ Rainbow Road                     Sobriety",
            "${d(1)}    Jefferson   Sr 113 NEAR NINA ROAD                       Sobriety",
            "${d(1)}    Knox     Cherokee Trail @ Cherokee Bluff                  Sobreity",
            "${d(3)}    McNairy   SR 22 at SR 69                                DL",
            "${d(1)}    Hardeman   SR 18N @ 30MM                              seat belt",
            "Todd Levee Rd. South (North of",
            "${d(2)}    Crockett   Madison Co. Line)                          Sobriety",
            "State Route 62 near the 10 mm (just",
            "${d(2)}    Putnam                                                 Sobriety",
            "west of Fentress County)",
            "${d(2)}    Gibson   North of Madison Co Line                      Sobriety",
            // Duplicate of the Sevier row — must be deduped.
            "${d(1)}    Sevier   Collier Drive @ Rainbow Road                     Sobriety"
        )
        val result = TennesseeSource().parsePdfText(text, "https://example.test/thp.pdf")
        assertEquals(7, result.size)

        val sevier = result.first { it.county == "Sevier" }
        assertEquals("Collier Drive @ Rainbow Road", sevier.location)
        assertEquals(LocalDate.now().plusDays(1), sevier.date)
        assertEquals(CheckpointType.SOBRIETY, sevier.type)

        // The PDF's own "Sobreity" typo still reads as a sobriety checkpoint.
        val knox = result.first { it.county == "Knox" }
        assertEquals("Cherokee Trail @ Cherokee Bluff", knox.location)
        assertEquals(CheckpointType.SOBRIETY, knox.type)

        val license = result.first { it.county == "McNairy" }
        assertEquals(CheckpointType.LICENSE, license.type)

        // Wrapped locations reassemble from the fragments around the date line.
        val crockett = result.first { it.county == "Crockett" }
        assertEquals(
            "Todd Levee Rd. South (North of Madison Co. Line)",
            crockett.location
        )
        val putnam = result.first { it.county == "Putnam" }
        assertEquals(
            "State Route 62 near the 10 mm (just west of Fentress County)",
            putnam.location
        )

        // A county name inside the location must not win over the real county.
        val gibson = result.first { it.location.contains("Madison Co Line") }
        assertEquals("Gibson", gibson.county)

        assertTrue(result.none { it.county == "Hardeman" })
        assertEquals(result.size, result.map { it.id }.distinct().size)
    }

    @Test
    fun parsesRealNovemberPdfText() {
        // NOTE: no bytes-level test here — pdfbox-android needs Android
        // classes, so it cannot run on the JVM. The text test below covers
        // the parsing logic against the real file's extracted text, and the
        // thin parsePdfBytes wrapper is verified on the phone itself.
        val text = requireNotNull(javaClass.classLoader)
            .getResourceAsStream("thp-november-2026.txt")!!
            .readBytes().decodeToString()
        val result = TennesseeSource().parsePdfText(
            text, "https://example.test/thp.pdf", LocalDate.of(2026, 11, 1)
        )
        // 75 dated rows in the file, minus 3 seat-belt rows.
        assertEquals(72, result.size)
        assertEquals(result.size, result.map { it.id }.distinct().size)

        val sevier = result.first { it.county == "Sevier" }
        assertEquals("Collier Drive @ Rainbow Road", sevier.location)
        assertEquals(LocalDate.of(2026, 11, 20), sevier.date)

        val crockett = result.first { it.county == "Crockett" }
        assertEquals(
            "Todd Levee Rd. South (North of Madison Co. Line)",
            crockett.location
        )
        assertEquals(2, result.count { it.type == CheckpointType.LICENSE })
        assertTrue(result.none { it.location.contains("seat", ignoreCase = true) })
    }

    @Test
    fun dropsPastDates() {
        val past = LocalDate.now().minusDays(2).format(pdfDateFmt)
        val future = LocalDate.now().plusDays(5).format(pdfDateFmt)
        val text = pdfText(
            "$past    Knox     Some Road at Main Street                        Sobriety",
            "$future    Knox     Other Road at Elm Street                      Sobriety"
        )
        val result = TennesseeSource().parsePdfText(text, "https://example.test/thp.pdf")
        assertEquals(1, result.size)
        assertEquals(LocalDate.now().plusDays(5), result[0].date)
    }

    @Test
    fun returnsEmptyForUnparseablePages() {
        val result = TennesseeSource().parsePdfText(
            "Nothing here yet.", "https://example.test/thp.pdf"
        )
        assertTrue(result.isEmpty())
    }
}
