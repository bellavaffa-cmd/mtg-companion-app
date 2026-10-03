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
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.flow.first
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Price alerts: on wishlist cards, a price per card ([CollectionEntry.priceAlert], USD, non-foil) to
 * be told when Scryfall's price is at or under it; on owned binder cards, a price to be told when it
 * rises to or over it ([CollectionEntry.priceAlertAbove]). When each goes off is PriceAlertRules.kt.
 * Checked in the background a few times a day, and when the app opens. Scryfall updates its prices
 * once a day. The same check notes each card's price for its history (see CardPriceHistory), once a
 * day. The web app checks when it's opened (src/collection/priceAlerts.ts).
 */
object PriceAlerts {
    private const val WORK = "price_alerts"
    private const val CHANNEL = "price_alerts"
    private const val PREFS = "price_alerts"

    /** A card past its alert, at [price]. A card is told about again only when it moves further past. */
    data class Hit(val collectionId: String, val entry: CollectionEntry, val price: Double, val direction: AlertDirection = AlertDirection.BELOW)

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

    /**
     * Cards now past their alert, not told about at this price yet; remembers them as told. [known]:
     * cards already fetched (by id), so they aren't asked for again.
     */
    suspend fun check(
        context: Context,
        collections: List<Collection>,
        cardRepository: CardRepository = CardRepository(),
        known: Map<String, ScryfallCard> = emptyMap()
    ): List<Hit> {
        val watched = alertWatches(collections)
        if (watched.isEmpty()) return emptyList()
        val missing = watched.map { it.entry.scryfallId }.distinct().filterNot { it in known }
        val cards = known + cardRepository.getCardsByIds(missing).associateBy { it.id }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        val hits = mutableListOf<Hit>()
        for (watch in watched) {
            val prices = cards[watch.entry.scryfallId]?.prices
            val price = alertPrice(watch, prices?.usd?.toDoubleOrNull(), prices?.usdFoil?.toDoubleOrNull()) ?: continue
            val key = watch.memoryKey
            val told = if (prefs.contains(key)) prefs.getFloat(key, 0f).toDouble() else null
            when (val step = alertStep(watch, price, told)) {
                AlertStep.Forget -> edit.remove(key)
                AlertStep.Quiet -> Unit
                is AlertStep.Tell -> {
                    edit.putFloat(key, step.price.toFloat())
                    hits += Hit(watch.collectionId, watch.entry, step.price, watch.direction)
                }
            }
        }
        edit.apply()
        return hits
    }

    /**
     * Once a day, every binder card's prices (wishlists too) for its price history — the cards come
     * back for [check] to use. Only cards not noted today yet (Home notes the owned ones when it works
     * out the collection's value) are asked for.
     */
    private suspend fun notePrices(collections: List<Collection>, cardRepository: CardRepository): Map<String, ScryfallCard> {
        val today = java.time.LocalDate.now().toEpochDay()
        val tracks = CardPriceHistory.load()
        val ids = collections.flatMap { c -> c.entries.map { it.scryfallId } }.distinct().filter { tracks[it]?.lastDay != today }
        if (ids.isEmpty()) return emptyMap()
        val cards = cardRepository.getCardsByIds(ids).associateBy { it.id }
        CardPriceHistory.record(cards.mapValues { it.value.prices })
        return cards
    }

    fun notify(context: Context, hits: List<Hit>) {
        if (hits.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Price alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When a card on your wishlist gets cheaper, or one you own gets dearer, than the price you set"
        })
        val first = hits.first()
        val rises = hits.all { it.direction == AlertDirection.ABOVE }
        val drops = hits.all { it.direction == AlertDirection.BELOW }
        val body = if (hits.size == 1) {
            if (first.direction == AlertDirection.ABOVE) "${first.entry.name} is ${formatUsd(first.price)} — over your ${formatUsd(first.entry.priceAlertAbove ?: 0.0)} alert"
            else "${first.entry.name} is ${formatUsd(first.price)} — under your ${formatUsd(first.entry.priceAlert ?: 0.0)} alert"
        } else {
            when {
                drops -> "${hits.size} wishlist cards are under your alert prices: "
                rises -> "${hits.size} of your cards are over your alert prices: "
                else -> "${hits.size} cards passed your alert prices: "
            } + hits.joinToString(", ") { "${it.entry.name} ${formatUsd(it.price)}" }
        }
        val title = when {
            hits.size == 1 && first.direction == AlertDirection.ABOVE -> "Price rise: ${first.entry.name}"
            hits.size == 1 -> "Price drop: ${first.entry.name}"
            drops -> "Price drops on your wishlist"
            rises -> "Price rises on your cards"
            else -> "Price alerts"
        }
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(PushNotifications.EXTRA_OPEN, "binder:${first.collectionId}")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, WORK.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
            NotificationManagerCompat.from(context).notify(WORK, 0, notification)
        } catch (e: SecurityException) {
            // Permission withdrawn between the check and here: nothing to show.
        }
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = try {
            val collections = CollectionRepository(applicationContext).collectionsFlow.first()
            val cardRepository = CardRepository()
            // A failed price note mustn't stop the alerts.
            val known = runCatching { notePrices(collections, cardRepository) }.getOrDefault(emptyMap())
            notify(applicationContext, check(applicationContext, collections, cardRepository, known))
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
