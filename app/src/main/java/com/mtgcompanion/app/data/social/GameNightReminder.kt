package com.mtgcompanion.app.data.social

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mtgcompanion.app.MainActivity
import com.mtgcompanion.app.R
import java.util.concurrent.TimeUnit

/**
 * The reminder the day before a game night, scheduled on this phone (WorkManager) from the list of
 * nights each time it's fetched: one per night the user hasn't said they can't make, a day before it
 * starts (reminderAt in GameNights.kt). A night called off, or answered Can't, has its reminder taken
 * back. The server sends no reminders of its own; the web app has none.
 */
object GameNightReminders {
    private const val CHANNEL = "game_nights"
    private const val PREFIX = "game_night_reminder_"

    /** Sets (or takes back) the reminder for each of [nights] for the user [me]. Never fails the caller. */
    fun schedule(context: Context, nights: List<NightInvite>, me: String, now: Long = System.currentTimeMillis()) {
        runCatching {
            val work = WorkManager.getInstance(context.applicationContext)
            for (n in nights) {
                val name = PREFIX + n.id
                val at = reminderAt(n, me, now)
                if (at == null) {
                    work.cancelUniqueWork(name)
                    continue
                }
                val (title, body) = reminderText(n)
                val request = OneTimeWorkRequestBuilder<Worker>()
                    .setInitialDelay(at - now, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf("title" to title, "body" to body, "night" to n.id))
                    .build()
                work.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
            }
        }
    }

    private fun notify(context: Context, nightId: String, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Game nights", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A reminder the day before a game night you're invited to"
        })
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(PushNotifications.EXTRA_OPEN, "night:$nightId")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, (PREFIX + nightId).hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFE6B45E.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(PREFIX + nightId, 0, notification)
        } catch (e: SecurityException) {
            // Permission withdrawn between the check and here: nothing to show.
        }
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val night = inputData.getString("night") ?: return Result.success()
            notify(applicationContext, night, inputData.getString("title") ?: "Game night tomorrow", inputData.getString("body").orEmpty())
            return Result.success()
        }
    }
}
