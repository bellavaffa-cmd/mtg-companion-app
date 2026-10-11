package com.mtgcompanion.app.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mtgcompanion.app.MtgCompanionApplication
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallCollectionResponse
import com.mtgcompanion.app.network.scryfall.ScryfallIdentifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * The background tag pass: every card the user has — in binders, the Wishlist and decks (considering
 * and sideboards too) — gets its role tags (RoleTags.kt) looked up, not only the ones a screen has
 * asked about. Run by WorkManager whenever there's a network: at app start, and again when the cards
 * change (after a sync, an import, a scan). Each 75 cards found are saved at once, so a pass cut
 * short carries on where it stopped; a pass Scryfall couldn't finish is tried again later, waiting
 * longer each time. Once, after the first full pass, the cards' keyword tags are filled in on entries
 * saved before they were kept ([EntryTagBackfill]). The web app's src/tags/tagPass.ts.
 */
object TagPass {
    private const val WORK = "role_tag_pass"
    /** A pass that finds new cards added meanwhile goes round again — at most this many times. */
    private const val ROUNDS = 3
    /** Between requests: the pass shares Scryfall with whatever the user is doing. */
    private const val PAUSE_MS = 250L

    /** Every card the user has, once a name: binders, the Wishlist, decks — commanders, considering and sideboards too. */
    fun refsOf(collections: List<Collection>, decks: List<Deck>): List<TagRef> {
        val out = LinkedHashMap<String, TagRef>()
        fun add(name: String, id: String) {
            val k = RoleTags.key(name)
            if (k.isEmpty()) return
            val had = out[k]
            if (had == null || (had.scryfallId == null && RoleTags.isScryfallId(id))) out[k] = TagRef(name, id.takeIf(RoleTags::isScryfallId))
        }
        for (c in collections) for (e in c.entries) add(e.name, e.scryfallId)
        for (d in decks) {
            for (e in listOfNotNull(d.commander, d.partnerCommander) + d.cards + d.considering + d.sideboard) add(e.name, e.scryfallId)
        }
        return out.values.toList()
    }

    /** Starts a pass when there's a network (one already waiting or running is left to finish — it goes round again for new cards). */
    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<Worker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request) }
    }

    /** Settings › Data and speed's "Retag now": [names] are looked up afresh, now. */
    suspend fun retag(context: Context, names: List<String>) {
        RoleTags.forget(names)
        // A running pass may have finished these already: replace it with a new one.
        val request = OneTimeWorkRequestBuilder<Worker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request) }
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = try {
            val app = applicationContext as MtgCompanionApplication
            val cardRepository = CardRepository()
            var ok = true
            for (round in 1..ROUNDS) {
                val refs = refsOf(app.collectionRepository.collectionsFlow.first(), app.deckRepository.decksFlow.first())
                RoleTags.hint(refs)
                if (!RoleTags.anyDue(refs)) break
                ok = RoleTags.ensureRefs(refs, cardRepository, pauseMs = PAUSE_MS)
                if (!ok) break
            }
            // Keyword tags for entries saved before they were kept: once, in one change.
            if (ok) ok = EntryTagBackfill.runOnce(app, app.collectionRepository, app.deckRepository) { cardRepository.getCollection(it) }
            if (ok) Result.success() else Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * Entries keep their card's keyword and theme tags (ScryfallCard.tags) from when they were added, so
 * card chips show without a lookup; entries saved before that have none. This fills them in once —
 * every binder and deck in one change each, written the way a sync's are (no deck versions), so the
 * next sync carries them in one pass rather than a change per card. The web app's
 * src/tags/tagPass.ts.
 */
object EntryTagBackfill {
    /** Bumped to fill in again (say, if ScryfallCard.tags learns new tags). */
    private const val VERSION = 1
    private const val PREFS = "entry_tag_backfill"

    /** The printings (Scryfall ids) with an entry that has no tags. */
    fun idsNeeding(collections: List<Collection>, decks: List<Deck>): List<String> {
        val ids = LinkedHashSet<String>()
        for (c in collections) for (e in c.entries) if (e.tags.isEmpty() && RoleTags.isScryfallId(e.scryfallId)) ids += e.scryfallId
        for (d in decks) for (e in deckEntries(d)) if (e.tags.isEmpty() && RoleTags.isScryfallId(e.scryfallId)) ids += e.scryfallId
        return ids.toList()
    }

    /** [collections] with every entry that has no tags given [tagsById]'s for its printing; the same list when none changed. */
    fun filled(collections: List<Collection>, tagsById: Map<String, List<String>>): List<Collection> {
        var changed = false
        val out = collections.map { c ->
            if (c.entries.none { it.tags.isEmpty() && !tagsById[it.scryfallId].isNullOrEmpty() }) c
            else {
                changed = true
                c.copy(entries = c.entries.map { e -> if (e.tags.isEmpty()) tagsById[e.scryfallId]?.takeIf { it.isNotEmpty() }?.let { e.copy(tags = it) } ?: e else e })
            }
        }
        return if (changed) out else collections
    }

    /** [decks] with every entry that has no tags given [tagsById]'s; the same list when none changed. */
    fun filledDecks(decks: List<Deck>, tagsById: Map<String, List<String>>): List<Deck> {
        fun DeckCardEntry.fill() = if (tags.isEmpty()) tagsById[scryfallId]?.takeIf { it.isNotEmpty() }?.let { copy(tags = it) } ?: this else this
        var changed = false
        val out = decks.map { d ->
            if (deckEntries(d).none { it.tags.isEmpty() && !tagsById[it.scryfallId].isNullOrEmpty() }) d
            else {
                changed = true
                d.copy(
                    commander = d.commander?.fill(),
                    partnerCommander = d.partnerCommander?.fill(),
                    cards = d.cards.map { it.fill() },
                    considering = d.considering.map { it.fill() },
                    sideboard = d.sideboard.map { it.fill() }
                )
            }
        }
        return if (changed) out else decks
    }

    private fun deckEntries(d: Deck) = listOfNotNull(d.commander, d.partnerCommander) + d.cards + d.considering + d.sideboard

    /**
     * Fetches the cards behind [ids] (75 a request, through [fetch]) and answers each one's tags by id.
     * Null if a request failed — nothing is written then, and it's all tried again next pass.
     */
    suspend fun tagsById(ids: List<String>, fetch: suspend (List<ScryfallIdentifier>) -> ScryfallCollectionResponse): Map<String, List<String>>? {
        val out = HashMap<String, List<String>>()
        for (chunk in ids.chunked(RoleTags.CHUNK)) {
            val response = try {
                fetch(chunk.map { ScryfallIdentifier(id = it) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return null
            }
            for (card: ScryfallCard in response.data) out[card.id] = card.tags
        }
        return out
    }

    /** Whether the fill-in hasn't run yet on this device. */
    fun pending(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("done", 0) < VERSION

    /** Fills in the missing keyword tags, once per [VERSION]. False if Scryfall couldn't be reached (tried again later). */
    suspend fun runOnce(
        context: Context,
        collectionRepository: CollectionRepository,
        deckRepository: DeckRepository,
        fetch: suspend (List<ScryfallIdentifier>) -> ScryfallCollectionResponse
    ): Boolean {
        if (!pending(context)) return true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = idsNeeding(collectionRepository.collectionsFlow.first(), deckRepository.decksFlow.first())
        if (ids.isNotEmpty()) {
            val tags = tagsById(ids, fetch) ?: return false
            if (tags.values.any { it.isNotEmpty() }) {
                // One change each, applied to the cards as they are by then; a sync's write, so no deck versions.
                collectionRepository.changeCollections { filled(it, tags) }
                deckRepository.applySync { filledDecks(it, tags) }
            }
        }
        prefs.edit().putInt("done", VERSION).apply()
        return true
    }
}
