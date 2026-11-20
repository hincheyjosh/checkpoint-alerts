package com.checkpointalerts.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.checkpointalerts.app.MainActivity
import com.checkpointalerts.app.R
import com.checkpointalerts.app.data.Checkpoint

/** Day-of notifications. One notification per checkpoint day: a single
 *  checkpoint gets a detailed notification; several get a short summary. */
object NotificationHelper {

    const val CHANNEL_ID = "checkpoint_alerts"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Checkpoint alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts on days when a checkpoint is happening in your area"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun notifyToday(context: Context, checkpoints: List<Checkpoint>) {
        if (checkpoints.isEmpty()) return
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = NotificationManagerCompat.from(context)
        if (checkpoints.size == 1) {
            val c = checkpoints[0]
            val text = "${c.type.label} checkpoint — ${c.location} · ${c.timeText}"
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_checkpoint)
                .setContentTitle("Checkpoint today in ${c.county} County")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
            nm.notify(c.id.hashCode(), notification)
        } else {
            val counties = checkpoints.map { it.county }.distinct().sorted()
            val lines = checkpoints.sortedBy { it.county }.take(3).joinToString("\n") {
                "• ${it.county} County — ${it.location} · ${it.timeText}"
            }
            val text = "${checkpoints.size} checkpoints today in " +
                counties.joinToString(", ") + ":\n$lines"
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_checkpoint)
                .setContentTitle("${checkpoints.size} checkpoints today")
                .setContentText("${checkpoints.size} checkpoints today in ${counties.joinToString(", ")}")
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
            nm.notify("today-summary".hashCode(), notification)
        }
    }
}
