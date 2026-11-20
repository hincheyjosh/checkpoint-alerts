package com.checkpointalerts.app.data

import java.time.LocalDate

/** One publicly announced checkpoint. Only ever built from what a source
 *  actually lists — the app never invents checkpoint data. */
data class Checkpoint(
    val id: String,
    val stateCode: String,
    val county: String,
    val city: String,
    val location: String,
    val date: LocalDate,
    /** e.g. "8 pm", or "Time not listed" when the announcement gives none. */
    val timeText: String,
    val type: CheckpointType,
    val sourceUrl: String
) {
    val isToday: Boolean get() = date == LocalDate.now()
}

enum class CheckpointType(val label: String) {
    SOBRIETY("Sobriety"),
    LICENSE("License")
}
