package com.mtgcompanion.app.data

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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mtgcompanion.app.MainActivity
import com.mtgcompanion.app.R
import com.mtgcompanion.app.data.social.PushNotifications
import kotlinx.coroutines.flow.first
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Price alerts on wishlist cards: the user sets a price per card ([CollectionEntry.priceAlert], USD,
 * non-foil) and gets a notification when Scryfall's price is at or under it. Checked in the
 * background a few times a day, and when the app opens. Scryfall updates its prices once a day.
 * The web app checks when it's opened (src/collection/priceAlerts.ts).
 */
object PriceAlerts {
    private const val WORK = "price_alerts"
    private const val CHANNEL = "price_alerts"
    private const val PREFS = "price_alerts"

    /** A card is told about again only when it drops further, or after going back over and dropping again. */
    data class Hit(val collectionId: String, val entry: CollectionEntry, val price: Double)

    fun schedule(context: Context) {
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val work = WorkManager.getInstance(context)
        work.enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<Worker>(8, TimeUnit.HOURS).setConstraints(network).build()
        )
        work.enqueueUniqueWork("${WORK}_now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<Worker>().setConstraints(network).build())
    }

    /** A US dollar price in the currency prices show in (Settings → Prices). */
    fun formatUsd(v: Double): String = Prices.money.value.format(v)

    /** Cards now at or under their alert, not told about at this price yet; remembers them as told. */
    suspend fun check(context: Context, collections: List<Collection>, cardRepository: CardRepository = CardRepository()): List<Hit> {
        val watched = collections.filter { it.kind == CollectionType.WISHLIST }
            .flatMap { c -> c.entries.filter { (it.priceAlert ?: 0.0) > 0.0 }.map { c.id to it } }
        if (watched.isEmpty()) return emptyList()
        val prices = cardRepository.getCardsByIds(watched.map { it.second.scryfallId }.distinct())
            .associate { it.id to it.prices?.usd?.toDoubleOrNull() }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        val hits = mutableListOf<Hit>()
        for ((collectionId, entry) in watched) {
            val price = prices[entry.scryfallId] ?: continue
            val key = entry.scryfallId
            if (price > (entry.priceAlert ?: 0.0)) {
                edit.remove(key)
                continue
            }
            val seen = if (prefs.contains(key)) prefs.getFloat(key, 0f).toDouble() else null
            if (seen != null && price >= seen - 0.001) continue
            edit.putFloat(key, price.toFloat())
            hits += Hit(collectionId, entry, price)
        }
        edit.apply()
        return hits
    }

    fun notify(context: Context, hits: List<Hit>) {
        if (hits.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Price alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When a card on your wishlist gets cheaper than the price you set"
        })
        val first = hits.first()
        val body = if (hits.size == 1) {
            "${first.entry.name} is ${formatUsd(first.price)} — under your ${formatUsd(first.entry.priceAlert ?: 0.0)} alert"
        } else {
            "${hits.size} wishlist cards are under your alert prices: " + hits.joinToString(", ") { "${it.entry.name} ${formatUsd(it.price)}" }
        }
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(PushNotifications.EXTRA_OPEN, "binder:${first.collectionId}")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, WORK.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFE6B45E.toInt())
            .setContentTitle(if (hits.size == 1) "Price drop: ${first.entry.name}" else "Price drops on your wishlist")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(WORK, 0, notification)
        } catch (e: SecurityException) {
            // Permission withdrawn between the check and here: nothing to show.
        }
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = try {
            val collections = CollectionRepository(applicationContext).collectionsFlow.first()
            notify(applicationContext, check(applicationContext, collections))
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
