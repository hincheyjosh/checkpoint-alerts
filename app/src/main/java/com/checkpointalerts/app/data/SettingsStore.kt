package com.checkpointalerts.app.data

import android.content.Context
import android.content.SharedPreferences

enum class AlertScope {
    WHOLE_STATE,
    NEARBY
}

/** Persisted user settings. Plain SharedPreferences — small, simple, and
 *  enough for a handful of values. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("checkpoint_prefs", Context.MODE_PRIVATE)

    var stateCode: String
        get() = prefs.getString(KEY_STATE, "TN") ?: "TN"
        set(value) = prefs.edit().putString(KEY_STATE, value).apply()

    var scope: AlertScope
        get() = try {
            AlertScope.valueOf(prefs.getString(KEY_SCOPE, AlertScope.WHOLE_STATE.name)!!)
        } catch (_: Exception) {
            AlertScope.WHOLE_STATE
        }
        set(value) = prefs.edit().putString(KEY_SCOPE, value.name).apply()

    /** Home county for the NEARBY scope; blank when none picked yet. */
    var homeCounty: String
        get() = prefs.getString(KEY_COUNTY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_COUNTY, value).apply()

    var lastUpdatedMillis: Long
        get() = prefs.getLong(KEY_LAST_UPDATED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATED, value).apply()

    /** Master switch for day-of checkpoint alerts. On by default; the list
     *  still refreshes when it is off, it just never notifies. */
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply()

    /** True once the app has asked for notification permission at least once,
     *  so first launch prompts but later launches don't nag. */
    var notificationPromptShown: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_PROMPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIF_PROMPTED, value).apply()

    /** Checkpoint ids already notified about, for the given epoch day.
     *  Each checkpoint alerts once per day at most. */
    fun wasNotified(id: String, epochDay: Long): Boolean {
        if (prefs.getLong(KEY_NOTIFY_DAY, -1L) != epochDay) return false
        return prefs.getStringSet(KEY_NOTIFIED, emptySet())?.contains(id) == true
    }

    fun markNotified(ids: List<String>, epochDay: Long) {
        val existing =
            if (prefs.getLong(KEY_NOTIFY_DAY, -1L) == epochDay) {
                prefs.getStringSet(KEY_NOTIFIED, emptySet())?.toMutableSet()
                    ?: mutableSetOf()
            } else {
                mutableSetOf()
            }
        existing.addAll(ids)
        prefs.edit()
            .putLong(KEY_NOTIFY_DAY, epochDay)
            .putStringSet(KEY_NOTIFIED, existing)
            .apply()
    }

    companion object {
        private const val KEY_STATE = "state_code"
        private const val KEY_SCOPE = "alert_scope"
        private const val KEY_COUNTY = "home_county"
        private const val KEY_LAST_UPDATED = "last_updated_ms"
        private const val KEY_NOTIF_PROMPTED = "notif_prompt_shown"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_NOTIFY_DAY = "notify_epoch_day"
        private const val KEY_NOTIFIED = "notified_ids"
    }
}
