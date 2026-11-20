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
import com.checkpointalerts.app.data.CheckpointRepository
import com.checkpointalerts.app.data.SettingsStore
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Daily background refresh. Re-fetches the announcements, then notifies
 * once per checkpoint for any checkpoint happening today inside the user's
 * scope. Runs even when the app hasn't been opened.
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
        val today = LocalDate.now()
        val todays = repo.state.value.checkpoints
            .filter { it.date == today && repo.inScope(it) }
            .filter { !settings.wasNotified(it.id, today.toEpochDay()) }
        if (todays.isNotEmpty()) {
            NotificationHelper.notifyToday(context, todays)
            settings.markNotified(todays.map { it.id }, today.toEpochDay())
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "checkpoint-refresh"
        private const val ONE_TIME_NAME = "checkpoint-refresh-now"

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
