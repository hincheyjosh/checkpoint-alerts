package com.checkpointalerts.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.checkpointalerts.app.data.Checkpoint
import com.checkpointalerts.app.data.CheckpointRepository
import com.checkpointalerts.app.data.SettingsStore
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Daily background refresh. Re-fetches the announcements, then notifies
 * once per checkpoint for any checkpoint happening today or tomorrow
 * inside the user's scope. Checkpoints usually run around midnight, so
 * the warning covers both days: a checkpoint dated tomorrow warns today
 * ("tomorrow") and again tomorrow ("today"). Runs even when the app
 * hasn't been opened.
 */
class RefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = SettingsStore(context)
        val repo = CheckpointRepository(context, settings)
        repo.loadCache()
        // Best effort: even if the refresh fails, last-known data may still
        // hold a checkpoint for today, so notify from whatever we have.
        repo.refresh()
        // The list still refreshes with alerts off — it just never notifies.
        if (!settings.notificationsEnabled) return Result.success()
        val today = LocalDate.now()
        val toNotify = selectToNotify(
            repo.state.value.checkpoints,
            today,
            inScope = { repo.inScope(it) },
            wasNotified = { settings.wasNotified(it, today.toEpochDay()) }
        )
        val todays = toNotify.filter { it.date == today }
        val tomorrows = toNotify.filter { it.date == today.plusDays(1) }
        if (todays.isNotEmpty()) {
            NotificationHelper.notifyCheckpoints(context, todays, "today")
        }
        if (tomorrows.isNotEmpty()) {
            NotificationHelper.notifyCheckpoints(context, tomorrows, "tomorrow")
        }
        if (toNotify.isNotEmpty()) {
            settings.markNotified(toNotify.map { it.id }, today.toEpochDay())
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "checkpoint-refresh"
        private const val ONE_TIME_NAME = "checkpoint-refresh-now"

        /**
         * Which in-scope checkpoints to warn about on [today]: those dated
         * today or tomorrow. A checkpoint warned about as "tomorrow" warns
         * again as "today" — the per-day notified set makes that happen
         * naturally, since [wasNotified] is asked about today only.
         * Exposed for unit tests.
         */
        fun selectToNotify(
            checkpoints: List<Checkpoint>,
            today: LocalDate,
            inScope: (Checkpoint) -> Boolean,
            wasNotified: (String) -> Boolean
        ): List<Checkpoint> {
            val tomorrow = today.plusDays(1)
            return checkpoints.filter {
                (it.date == today || it.date == tomorrow) &&
                    inScope(it) && !wasNotified(it.id)
            }
        }

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Daily refresh. KEEP so re-enqueueing never duplicates the job. */
        fun enqueuePeriodic(context: Context) {
            val work = PeriodicWorkRequestBuilder<RefreshWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, work
            )
        }

        /** One immediate refresh (first launch, manual pull-to-refresh). */
        fun enqueueNow(context: Context) {
            val work = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(constraints())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_NAME, ExistingWorkPolicy.KEEP, work
            )
        }
    }
}
