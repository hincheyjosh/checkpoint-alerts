package com.checkpointalerts.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-schedule the daily refresh after a reboot. (WorkManager normally
 *  persists periodic work itself; this makes the reschedule explicit.) */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            RefreshWorker.enqueuePeriodic(context)
        }
    }
}
