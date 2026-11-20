package com.checkpointalerts.app.data

/** A per-state source of announced checkpoints. There is no national API:
 *  every state publishes announcements differently, so each state gets its
 *  own implementation. Add new states by implementing this interface and
 *  registering the source below. */
interface CheckpointSource {
    val stateCode: String
    val stateName: String

    /** Fetch announced checkpoints (today and future only).
     *  Returns only what the source actually lists — never invented data.
     *  @throws Exception when no source could be read at all. */
    @Throws(Exception::class)
    suspend fun fetch(): List<Checkpoint>
}

object SourceRegistry {
    val sources: List<CheckpointSource> = listOf(TennesseeSource())

    fun forState(code: String): CheckpointSource? =
        sources.firstOrNull { it.stateCode == code }

    val availableStates: List<Pair<String, String>> =
        sources.map { it.stateCode to it.stateName }
}
