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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mtgcompanion.app.MainActivity
import com.mtgcompanion.app.R
import com.mtgcompanion.app.data.social.PushNotifications
import com.mtgcompanion.app.ui.lifecounter.GameNightStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Upkeep's bits kept on this phone (not synced): the last import, for "Most came from last week's
 * import", and whether the weekly reminder is on. The web app keeps the same in the browser
 * (src/collection/upkeepStore.ts).
 */
object UpkeepStore {
    private const val PREFS = "storage_upkeep"
    private const val IMPORT_AT = "import_at"
    private const val IMPORT_COPIES = "import_copies"
    private const val IMPORT_INTO = "import_into"
    private const val WEEKLY = "weekly_reminder"

    private var app: Context? = null

    /** At start-up, so an import can be noted from anywhere. */
    fun init(context: Context) { app = context.applicationContext }

    private fun prefs(context: Context?) = (context?.applicationContext ?: app)?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastImport(context: Context? = null): ImportNote? {
        val p = prefs(context) ?: return null
        val at = p.getLong(IMPORT_AT, 0L).takeIf { it > 0 } ?: return null
        return ImportNote(at, p.getInt(IMPORT_COPIES, 0), p.getString(IMPORT_INTO, null) ?: UNSORTED_COLLECTION_ID)
    }

    /** An import of [copies] copies into [collectionId] just now. */
    fun noteImport(copies: Int, collectionId: String) {
        if (copies <= 0) return
        prefs(null)?.edit()?.putLong(IMPORT_AT, System.currentTimeMillis())?.putInt(IMPORT_COPIES, copies)?.putString(IMPORT_INTO, collectionId)?.apply()
    }

    fun weeklyOn(context: Context): Boolean = prefs(context)?.getBoolean(WEEKLY, false) ?: false

    fun setWeekly(context: Context, on: Boolean) {
        prefs(context)?.edit()?.putBoolean(WEEKLY, on)?.apply()
        if (on) UpkeepReminder.schedule(context) else UpkeepReminder.cancel(context)
    }
}

/**
 * After an import into [collectionId]: the copies given the places the list's location column says
 * ([targets], ImportPlaces.kt — new places made for the values that want one), and the import noted
 * for Upkeep's "Most came from last week's import".
 */
suspend fun CollectionRepository.afterImport(collectionId: String, result: ImportResult, targets: Map<String, PlaceTarget>, column: String?) {
    val placements = result.cards.filter { it.locations.isNotEmpty() }.map { ImportedPlacement(it.card.id, it.locations) }
    if (placements.isNotEmpty() && targets.isNotEmpty()) {
        val now = System.currentTimeMillis()
        changeStorage { applyImportedPlaces(it, collectionId, placements, targets, column, now) { java.util.UUID.randomUUID().toString() } }
    }
    UpkeepStore.noteImport(result.added, collectionId)
}

/** One copy's price from the prices noted for the price history (CardPriceHistory): the latest known. */
fun priceFromHistory(tracks: Map<String, PriceTrack>): (String, Boolean) -> Double? = { id, foil ->
    val points = tracks[id]?.points.orEmpty()
    val p = points.lastOrNull { it.usd != null || it.usdFoil != null }
    unitPrice(p?.let { PrintingFacts("", "", it.usd, it.usdFoil) }, foil)
}

/** Everything Upkeep needs from this phone, gathered: pull lists, the last import, game nights and noted prices. */
suspend fun upkeepNow(context: Context, collections: List<Collection>, decks: List<Deck>): UpkeepReport {
    val now = System.currentTimeMillis()
    GameNightStore.init(context)
    val nights = GameNightStore.gameNights().second.map { NightDay(it, dayOf(it)) }
    CardPriceHistory.init(context)
    val tracks = runCatching { CardPriceHistory.load() }.getOrDefault(emptyMap())
    return upkeep(
        collections, decks, now, dayOf(now), nights,
        pulls = PullProgress(context).underway(),
        lastImport = UpkeepStore.lastImport(context),
        price = priceFromHistory(tracks),
        money = { Prices.money.value.format(it, whole = true) }
    )
}

/**
 * The weekly reminder: once a week, a notification saying how many things are worth doing in
 * Upkeep — only when there's something. Opt-in, from the Upkeep screen.
 */
object UpkeepReminder {
    private const val WORK = "storage_upkeep"
    private const val CHANNEL = "storage_upkeep"

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<Worker>(7, TimeUnit.DAYS).setInitialDelay(1, TimeUnit.DAYS).build()
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    /** "4 things worth doing this week" / "3 copies have no place · Red box is 96% full". */
    fun notify(context: Context, report: UpkeepReport) {
        if (report.items.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Storage upkeep", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Once a week, what's worth doing to keep your storage tidy"
        })
        val title = upkeepHeadline(report.items.size)
        val body = "${report.percent}% of copies have a place · " + report.items.take(3).joinToString(" · ") { it.title }
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(PushNotifications.EXTRA_OPEN, "upkeep")
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
            if (UpkeepStore.weeklyOn(applicationContext)) {
                val collections = CollectionRepository(applicationContext).collectionsFlow.first()
                val decks = DeckRepository(applicationContext).decksFlow.first()
                notify(applicationContext, upkeepNow(applicationContext, collections, decks))
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
