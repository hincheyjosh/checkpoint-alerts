package com.checkpointalerts.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class RepoState(
    val checkpoints: List<Checkpoint>,
    val isRefreshing: Boolean,
    val lastUpdatedMillis: Long,
    /** True when the last refresh failed and the list is last-known data. */
    val refreshError: Boolean
)

/** Owns fetching, caching, and scope filtering of checkpoints. */
class CheckpointRepository(
    private val context: Context,
    private val settings: SettingsStore
) {
    private val cacheFile: File
        get() = File(context.filesDir, "checkpoints.json")

    private val _state = MutableStateFlow(
        RepoState(
            checkpoints = emptyList(),
            isRefreshing = false,
            lastUpdatedMillis = settings.lastUpdatedMillis,
            refreshError = false
        )
    )
    val state: StateFlow<RepoState> = _state.asStateFlow()

    /** Counties the user's scope covers, or null for the whole state. */
    fun scopeCounties(): Set<String>? {
        if (settings.scope != AlertScope.NEARBY) return null
        val home = settings.homeCounty
        if (home.isBlank()) return null
        return TnCounties.scopeCounties(home)
    }

    fun inScope(checkpoint: Checkpoint): Boolean {
        val counties = scopeCounties() ?: return true
        return counties.contains(checkpoint.county)
    }

    /** Load the saved cache (if any) so the list is never empty on launch. */
    fun loadCache() {
        val cached = readCache()
        _state.value = _state.value.copy(
            checkpoints = cached,
            lastUpdatedMillis = settings.lastUpdatedMillis
        )
    }

    /** Re-fetch from the source. On failure the last saved list stays put
     *  and refreshError is set — the UI says so plainly. Returns true on a
     *  successful fetch. */
    suspend fun refresh(): Boolean {
        _state.value = _state.value.copy(isRefreshing = true)
        val source = SourceRegistry.forState(settings.stateCode)
        if (source == null) {
            _state.value = _state.value.copy(isRefreshing = false, refreshError = true)
            return false
        }
        return try {
            val fresh = source.fetch()
            withContext(Dispatchers.IO) { writeCache(fresh) }
            settings.lastUpdatedMillis = System.currentTimeMillis()
            _state.value = RepoState(
                checkpoints = fresh,
                isRefreshing = false,
                lastUpdatedMillis = settings.lastUpdatedMillis,
                refreshError = false
            )
            true
        } catch (_: Exception) {
            _state.value = _state.value.copy(isRefreshing = false, refreshError = true)
            false
        }
    }

    private fun readCache(): List<Checkpoint> {
        return try {
            val file = cacheFile
            if (!file.exists()) return emptyList()
            val arr = JSONArray(file.readText())
            val out = mutableListOf<Checkpoint>()
            val today = LocalDate.now()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val date = LocalDate.parse(o.getString("date"))
                if (date.isBefore(today)) continue
                out += Checkpoint(
                    id = o.getString("id"),
                    stateCode = o.optString("state", "TN"),
                    county = o.getString("county"),
                    city = o.optString("city", ""),
                    location = o.getString("location"),
                    date = date,
                    timeText = o.optString("time", "Time not listed"),
                    type = CheckpointType.valueOf(o.getString("type")),
                    sourceUrl = o.optString("source", "")
                )
            }
            out.sortedWith(compareBy({ it.date }, { it.county }, { it.location }))
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeCache(list: List<Checkpoint>) {
        try {
            val arr = JSONArray()
            for (c in list) {
                arr.put(
                    JSONObject().apply {
                        put("id", c.id)
                        put("state", c.stateCode)
                        put("county", c.county)
                        put("city", c.city)
                        put("location", c.location)
                        put("date", c.date.toString())
                        put("time", c.timeText)
                        put("type", c.type.name)
                        put("source", c.sourceUrl)
                    }
                )
            }
            cacheFile.writeText(arr.toString())
        } catch (_: Exception) {
            // Cache write failure is non-fatal; the in-memory list still works.
        }
    }
}
