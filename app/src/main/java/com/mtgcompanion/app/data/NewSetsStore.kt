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
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mtgcompanion.app.MainActivity
import com.mtgcompanion.app.R
import com.mtgcompanion.app.data.social.PushNotifications
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Where New sets keeps its bits on this phone: the sets followed, the ones already announced, and
 * Scryfall's set data, kept a while so opening the app doesn't ask again each time — the release list
 * for 12 hours (in a file), a set's cards for this run (12 hours at most). Requests go through
 * CardRepository, paced to Scryfall's limits (ScryfallPacer). Mirrors the web app's
 * src/collection/newSetsStore.ts.
 */
object NewSetsStore {
    private const val PREFS = "new_sets"
    private const val FOLLOWED = "followed"
    private const val TOLD = "told"
    private const val FILE = "release_sets.json"
    private const val REVEALS_SEEN = "reveals_seen_"
    private const val REVEALS_TOLD_AT = "reveals_told_at"
    private const val FRESH_MS = 12 * 60 * 60 * 1000L
    /** Pages of a set's cards to read at most: 175 a page, so a big set and its extras. */
    private const val MAX_PAGES = 6

    private var app: Context? = null
    private val _followed = MutableStateFlow<Set<String>>(emptySet())
    /** The sets followed, by code. */
    val followed: StateFlow<Set<String>> = _followed.asStateFlow()
    private val _told = MutableStateFlow<Set<String>>(emptySet())
    /** Followed sets already announced. */
    val told: StateFlow<Set<String>> = _told.asStateFlow()
    private val cards = HashMap<String, Pair<Long, List<SetCard>>>()

    fun init(context: Context) {
        app = context.applicationContext
        val p = prefs(context)
        _followed.value = p.getStringSet(FOLLOWED, emptySet()).orEmpty().toSet()
        _told.value = p.getStringSet(TOLD, emptySet()).orEmpty().toSet()
        if (_followed.value.isNotEmpty()) SetReleaseCheck.schedule(context)
    }

    private fun prefs(context: Context?) = (context?.applicationContext ?: app!!).getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun today(): String = LocalDate.now().toString()

    /**
     * Follow or stop following [set]: a followed set is announced when it comes out — one already out
     * isn't announced (it's no news). The daily check runs only while a set is followed.
     */
    fun setFollowed(context: Context, set: SetInfo, on: Boolean) {
        val next = if (on) _followed.value + set.code else _followed.value - set.code
        _followed.value = next
        val edit = prefs(context).edit().putStringSet(FOLLOWED, next)
        if (on && set.releasedAt.orEmpty() <= today()) {
            _told.value = _told.value + set.code
            edit.putStringSet(TOLD, _told.value)
        }
        edit.apply()
        if (next.isEmpty()) SetReleaseCheck.cancel(context) else SetReleaseCheck.schedule(context)
    }

    /** The revealed cards (ids) already seen that fit a deck, by set: what's new is told (revealNews). */
    fun revealsSeen(context: Context, code: String): Set<String>? =
        prefs(context).getStringSet("$REVEALS_SEEN$code", null)?.toSet()

    /** [ids] seen for [code] — on its page, or told in a notification. */
    fun markRevealsSeen(context: Context, code: String, ids: Iterable<String>) {
        val had = revealsSeen(context, code)
        if (had != null && ids.all { it in had }) return
        prefs(context).edit().putStringSet("$REVEALS_SEEN$code", had.orEmpty() + ids).apply()
    }

    fun revealsToldAt(context: Context): Long? = prefs(context).getLong(REVEALS_TOLD_AT, 0L).takeIf { it > 0 }
    fun markRevealsTold(context: Context, at: Long) { prefs(context).edit().putLong(REVEALS_TOLD_AT, at).apply() }

    /** [codes] announced: they're not announced again. */
    fun markTold(context: Context, codes: List<String>) {
        if (codes.isEmpty()) return
        _told.value = _told.value + codes
        prefs(context).edit().putStringSet(TOLD, _told.value).apply()
    }

    private fun setToJson(s: SetInfo) = JSONObject().put("code", s.code).put("name", s.name).put("cardCount", s.cardCount)
        .put("releasedAt", s.releasedAt ?: JSONObject.NULL).put("iconSvgUri", s.iconSvgUri ?: JSONObject.NULL)
        .put("setType", s.setType ?: JSONObject.NULL).put("digital", s.digital).put("printedSize", s.printedSize ?: JSONObject.NULL)

    private fun setFromJson(o: JSONObject) = SetInfo(
        o.getString("code"), o.optString("name", o.getString("code").uppercase()), o.optInt("cardCount"),
        if (o.isNull("releasedAt")) null else o.optString("releasedAt"),
        if (o.isNull("iconSvgUri")) null else o.optString("iconSvgUri"),
        if (o.isNull("setType")) null else o.optString("setType"),
        o.optBoolean("digital"),
        if (o.isNull("printedSize") || !o.has("printedSize")) null else o.optInt("printedSize")
    )

    /**
     * The sets worth listing — out in the last two months or still to come — from Scryfall, or as kept
     * from the last 12 hours. Throws when it can't reach Scryfall and has nothing kept.
     */
    suspend fun releaseSets(context: Context, cardRepository: CardRepository = CardRepository()): List<SetInfo> {
        val file = File(context.filesDir, FILE)
        val kept = withContext(Dispatchers.IO) {
            runCatching {
                val o = JSONObject(file.readText())
                val a = o.getJSONArray("sets")
                o.getLong("at") to (0 until a.length()).map { setFromJson(a.getJSONObject(it)) }
            }.getOrNull()
        }
        if (kept != null && System.currentTimeMillis() - kept.first < FRESH_MS) return kept.second
        val since = addDays(today(), -60)
        val sets = try {
            cardRepository.getSets().values.filter { listable(it) && it.releasedAt.orEmpty() > since }
        } catch (e: Exception) {
            return kept?.second ?: throw e
        }
        withContext(Dispatchers.IO) {
            runCatching {
                file.writeText(JSONObject().put("at", System.currentTimeMillis()).put("sets", JSONArray(sets.map { setToJson(it) })).toString())
            }
        }
        return sets
    }

    /** [card] as the matching keeps it — with the role tags its rules text shows (Tagger hasn't tagged a new card yet). */
    fun setCardOf(card: ScryfallCard) = SetCard(
        card.id, card.name, card.typeLine.orEmpty(), card.colorIdentity.orEmpty(), card.tags, card.displayImageUrl, card.rarity,
        roles = RoleTags.tagsFor(null, card.displayOracleText, emptyMap()).map { RoleTags.label(it) },
        releasedAt = card.releasedAt,
        usd = card.prices?.usd,
        commanderLegality = card.legalities?.get("commander")
    )

    /**
     * The cards Scryfall has for [code] so far — every printing (showcase frames and all), no basic
     * lands — the most recently revealed first.
     */
    suspend fun setCards(code: String, cardRepository: CardRepository = CardRepository()): List<SetCard> {
        synchronized(cards) { cards[code] }?.let { (at, list) -> if (System.currentTimeMillis() - at < FRESH_MS) return list }
        val out = mutableListOf<SetCard>()
        for (page in 1..MAX_PAGES) {
            val result = cardRepository.search("e:${code.lowercase()} -t:basic", order = "spoiled", dir = "desc", page = page, unique = "prints")
            out += result.cards.map { setCardOf(it) }
            if (!result.hasMore) break
        }
        synchronized(cards) { cards[code] = System.currentTimeMillis() to out }
        return out
    }

    /** Each deck's commanders' colour identity together, by deck id (one request for them all). */
    suspend fun commanderIdentities(decks: List<Deck>, cardRepository: CardRepository = CardRepository()): Map<String, List<String>> {
        val ids = decks.flatMap { listOfNotNull(it.commander?.scryfallId, it.partnerCommander?.scryfallId) }
        val byId = cardRepository.getCardsByIds(ids).associate { it.id to it.colorIdentity.orEmpty() }
        return decks.mapNotNull { d ->
            // A commander Scryfall didn't send can't be checked: the deck is left out.
            val main = d.commander?.scryfallId?.let { byId[it] } ?: return@mapNotNull null
            val partner = d.partnerCommander?.scryfallId?.let { byId[it] }.orEmpty()
            d.id to (main + partner).distinct()
        }.toMap()
    }
}

/**
 * The release-day check: twice a day while a set is followed, a notification for each followed set
 * that's out (once) — and, once a day at most, one for cards newly revealed for a followed set that
 * fit the user's decks (Spoilers.kt). Opt-in — it runs only once a set is followed (the bell on New sets).
 */
object SetReleaseCheck {
    private const val WORK = "set_release"
    private const val CHANNEL = "set_release"
    private const val REVEALS_CHANNEL = "set_reveals"

    fun schedule(context: Context) {
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<Worker>(12, TimeUnit.HOURS).setConstraints(network).build()
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    /** "Bloomburrow is out today" — or "2 sets you follow are out". */
    fun notify(context: Context, sets: List<SetInfo>) {
        if (sets.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "New sets", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "The day a set you follow comes out"
        })
        val first = sets.first()
        val today = NewSetsStore.today()
        val title = if (sets.size == 1) "${first.name} is ${releaseLabel(first.releasedAt ?: today, today).replaceFirstChar { it.lowercase() }}" else "${sets.size} sets you follow are out"
        val body = if (sets.size == 1) "See the cards for your decks" else sets.joinToString(", ") { it.name } + " — see the cards for your decks"
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(PushNotifications.EXTRA_OPEN, if (sets.size == 1) "newset:${first.code}" else "newsets")
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

    /** "3 new cards revealed for Bloomburrow that fit your decks" (Spoilers.kt's revealNews). */
    fun notifyReveals(context: Context, news: List<Pair<SetInfo, Int>>) {
        if (news.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(REVEALS_CHANNEL, "Spoilers", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "New cards revealed for a set you follow that fit your decks (once a day at most)"
        })
        val first = news.first().first
        val title = revealNewsTitle(news)
        val body = if (news.size == 1) "See which decks they'd go in" else news.joinToString(", ") { "${it.first.name} (${it.second})" }
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(PushNotifications.EXTRA_OPEN, if (news.size == 1) "newset:${first.code}" else "newsets")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, REVEALS_CHANNEL.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, REVEALS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFE6B45E.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(REVEALS_CHANNEL, 0, notification)
        } catch (e: SecurityException) {
            // Permission withdrawn between the check and here: nothing to show.
        }
    }

    /**
     * Followed sets not out yet (or just out) with cards revealed since last time that fit one of the
     * user's Commander decks — once a day at most. A set looked at for the first time only notes
     * what's there.
     */
    private suspend fun checkReveals(context: Context, followed: Set<String>, sets: List<SetInfo>) {
        val now = System.currentTimeMillis()
        if (!mayTellReveals(NewSetsStore.revealsToldAt(context), now)) return
        val today = NewSetsStore.today()
        val lists = releaseSets(sets, today)
        val watched = (lists.upcoming + lists.recent).filter { it.code in followed && it.cardCount > 0 }
        if (watched.isEmpty()) return
        val decks = commanderDecks(DeckRepository(context).decksFlow.first())
        if (decks.isEmpty()) return
        val identities = NewSetsStore.commanderIdentities(decks)
        val profiles = decks.mapNotNull { d ->
            identities[d.id]?.let { deckProfile(d, it) { name -> RoleTags.tagsOf(name)?.map { id -> RoleTags.label(id) } } }
        }
        val news = mutableListOf<Pair<SetInfo, Int>>()
        for (set in watched) {
            val fitting = fitsByCard(NewSetsStore.setCards(set.code), profiles, today).keys.toList()
            val fresh = revealNews(NewSetsStore.revealsSeen(context, set.code), fitting)
            NewSetsStore.markRevealsSeen(context, set.code, fitting)
            if (fresh.isNotEmpty()) news += set to fresh.size
        }
        if (news.isEmpty()) return
        notifyReveals(context, news)
        NewSetsStore.markRevealsTold(context, now)
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = try {
            NewSetsStore.init(applicationContext)
            val followed = NewSetsStore.followed.value
            if (followed.isNotEmpty()) {
                val sets = NewSetsStore.releaseSets(applicationContext)
                val out = setsToAnnounce(followed, sets, NewSetsStore.today(), NewSetsStore.told.value)
                notify(applicationContext, out)
                NewSetsStore.markTold(applicationContext, out.map { it.code })
                // The spoilers' news: a failure here doesn't hold up the release-day news above.
                runCatching { checkReveals(applicationContext, followed, sets) }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
