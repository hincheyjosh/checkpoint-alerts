package com.checkpointalerts.app

import com.checkpointalerts.app.data.Checkpoint
import com.checkpointalerts.app.data.CheckpointType
import com.checkpointalerts.app.work.RefreshWorker
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The warning covers both days around a checkpoint: checkpoints usually
 * run around midnight, so a checkpoint dated tomorrow warns today
 * ("tomorrow") and again tomorrow ("today").
 */
class RefreshWorkerTest {

    private fun checkpoint(id: String, date: LocalDate, county: String = "Sevier") =
        Checkpoint(
            id = id,
            stateCode = "TN",
            county = county,
            city = "",
            location = "Some Road",
            date = date,
            timeText = "Time not listed",
            type = CheckpointType.SOBRIETY,
            sourceUrl = ""
        )

    @Test
    fun selectsTodayAndTomorrowOnly() {
        val today = LocalDate.of(2026, 11, 20)
        val list = listOf(
            checkpoint("past", today.minusDays(1)),
            checkpoint("today", today),
            checkpoint("tomorrow", today.plusDays(1)),
            checkpoint("later", today.plusDays(2))
        )
        val result = RefreshWorker.selectToNotify(list, today, { true }, { false })
        assertEquals(listOf("today", "tomorrow"), result.map { it.id })
    }

    @Test
    fun respectsScopeAndAlreadyNotified() {
        val today = LocalDate.of(2026, 11, 20)
        val list = listOf(
            checkpoint("today-out-of-scope", today, county = "Davidson"),
            checkpoint("today-notified", today),
            checkpoint("tomorrow-ok", today.plusDays(1))
        )
        val result = RefreshWorker.selectToNotify(
            list, today,
            inScope = { it.county == "Sevier" },
            wasNotified = { it == "today-notified" }
        )
        assertEquals(listOf("tomorrow-ok"), result.map { it.id })
    }
}
