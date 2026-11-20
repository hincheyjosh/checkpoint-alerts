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

/** Notifications for checkpoints happening today or tomorrow. Checkpoints
 *  usually run around midnight, so the warning covers both days: a
 *  checkpoint dated tomorrow warns today ("tomorrow") and again tomorrow
 *  ("today"). One notification per checkpoint per day: a single checkpoint
 *  gets a detailed notification; several get a short summary. */
object NotificationHelper {

    const val CHANNEL_ID = "checkpoint_alerts"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Checkpoint alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts the day before and the day a checkpoint happens in your area"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    /**
     * Notify about [checkpoints] happening on [day] — "today" or
     * "tomorrow", which is what the titles say.
     */
    fun notifyCheckpoints(context: Context, checkpoints: List<Checkpoint>, day: String) {
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
                .setContentTitle("Checkpoint $day in ${c.county} County")
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
            val text = "${checkpoints.size} checkpoints $day in " +
                counties.joinToString(", ") + ":\n$lines"
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_checkpoint)
                .setContentTitle("${checkpoints.size} checkpoints $day")
                .setContentText("${checkpoints.size} checkpoints $day in ${counties.joinToString(", ")}")
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
            nm.notify("$day-summary".hashCode(), notification)
        }
    }
}
