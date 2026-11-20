package com.checkpointalerts.app

import android.app.Application
import com.checkpointalerts.app.work.NotificationHelper
import com.checkpointalerts.app.work.RefreshWorker

class CheckpointApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        RefreshWorker.enqueuePeriodic(this)
    }
}
