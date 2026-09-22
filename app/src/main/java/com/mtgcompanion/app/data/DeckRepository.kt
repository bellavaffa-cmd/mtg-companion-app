package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.noteDeleted
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.decksDataStore by preferencesDataStore(name = "decks")

class DeckRepository(private val context: Context) {

    private val key = stringPreferencesKey("decks_json")
    private val adapter = localMoshi.adapter(DeckStore::class.java)

    val decksFlow: Flow<List<Deck>> = context.decksDataStore.data.map { prefs ->
        prefs[key]?.let { json -> runCatching { adapter.fromJson(json)?.decks }.getOrNull() } ?: emptyList()
    }

    fun deckFlow(deckId: String): Flow<Deck?> = decksFlow.map { decks -> decks.find { it.id == deckId } }

    suspend fun createDeck(name: String, gameMode: GameMode = GameMode.DEFAULT): Deck {
        val deck = Deck(id = UUID.randomUUID().toString(), name = name, gameMode = gameMode.name)
        update { it + deck }
        return deck
    }

    /**
     * A new deck that arrives complete — a precon import. One write for cards and commanders, so its
     * version history starts with the imported list rather than a "commander changed" step after it.
     */
    suspend fun createDeckWithCards(
        name: String,
        gameMode: GameMode,
        entries: List<DeckCardEntry>,
        commander: DeckCardEntry?,
        partnerCommander: DeckCardEntry?
    ): Deck {
        val merged = entries.groupBy { it.scryfallId }.map { (_, copies) ->
            copies.first().copy(quantity = copies.sumOf { it.quantity })
        }
        val deck = Deck(
            id = UUID.randomUUID().toString(),
            name = name,
            gameMode = gameMode.name,
            cards = merged,
            commander = commander,
            partnerCommander = partnerCommander
        )
        update { it + deck }
        return deck
    }

    suspend fun setGameMode(deckId: String, gameMode: GameMode) {
        update { decks -> decks.map { if (it.id == deckId) it.copy(gameMode = gameMode.name) else it } }
    }

    /**
     * One proxy swapped for the real card: the copy leaves the binder and the deck stops counting
     * that copy as a proxy. Both stores are written, so the card is never in two places at once.
     */
    suspend fun swapInProxy(deckId: String, scryfallId: String, collectionRepository: CollectionRepository) {
        val swap = withSwapIn(collectionRepository.collectionsFlow.first(), decksFlow.first(), deckId, scryfallId) ?: return
        collectionRepository.applySync { swap.collections }
        update { swap.decks }
    }

    suspend fun setOwnership(deckId: String, ownership: DeckOwnership) {
        update { decks -> decks.map { if (it.id == deckId) it.copy(ownership = ownership.name) else it } }
    }

    suspend fun setTags(deckId: String, tags: List<String>) {
        update { decks -> decks.map { if (it.id == deckId) it.copy(tags = tags) else it } }
    }

    suspend fun addGameResult(deckId: String, result: GameResult) {
        update { decks -> decks.map { if (it.id == deckId) it.copy(gameResults = it.gameResults + result) else it } }
    }

    suspend fun removeGameResult(deckId: String, resultId: String) {
        update { decks -> decks.map { if (it.id == deckId) it.copy(gameResults = it.gameResults.filterNot { r -> r.id == resultId }) else it } }
    }

    suspend fun deleteDeck(deckId: String) {
        update(deleting = deckId) { decks -> decks.filterNot { it.id == deckId } }
    }

    suspend fun addCardToDeck(deckId: String, card: ScryfallCard) {
        update { decks ->
            decks.map { deck ->
                if (deck.id != deckId) return@map deck
                val existing = deck.cards.find { it.scryfallId == card.id }
                val newCards = if (existing != null) {
                    deck.cards.map { if (it.scryfallId == card.id) it.copy(quantity = it.quantity + 1) else it }
                } else {
                    deck.cards + DeckCardEntry(card.id, card.name, card.displayImageUrl, 1, card.canBeCommander, card.typeLine, card.partnerAbility, card.backImageUrl, card.tags)
                }
                deck.copy(cards = newCards)
            }
        }
    }

    suspend fun removeCardFromDeck(deckId: String, scryfallId: String) {
        update { decks ->
            decks.map { deck ->
                if (deck.id != deckId) return@map deck
                val newCommander = deck.commander?.takeUnless { it.scryfallId == scryfallId }
                val newPartner = deck.partnerCommander?.takeUnless { it.scryfallId == scryfallId }
                deck.copy(cards = deck.cards.filterNot { it.scryfallId == scryfallId }, commander = newCommander, partnerCommander = newPartner)
            }
        }
    }

    /** Set a card's copy count; a quantity of 0 or less removes the card (and clears it as commander). */
    suspend fun setCardQuantity(deckId: String, scryfallId: String, quantity: Int) {
        update { decks ->
            decks.map { deck ->
                if (deck.id != deckId) return@map deck
                if (quantity <= 0) {
                    val newCommander = deck.commander?.takeUnless { it.scryfallId == scryfallId }
                    val newPartner = deck.partnerCommander?.takeUnless { it.scryfallId == scryfallId }
                    deck.copy(cards = deck.cards.filterNot { it.scryfallId == scryfallId }, commander = newCommander, partnerCommander = newPartner)
                } else {
                    deck.copy(cards = deck.cards.map { if (it.scryfallId == scryfallId) it.copy(quantity = quantity) else it })
                }
            }
        }
    }

    /** Swap a card to a different printing (art), keeping its quantity; updates the commander(s) too. */
    suspend fun changeCardPrinting(deckId: String, oldScryfallId: String, newCard: ScryfallCard) {
        // Merges into the new printing if the deck already holds it (see withDeckPrinting).
        update { decks -> decks.map { deck -> if (deck.id != deckId) deck else withDeckPrinting(deck, oldScryfallId, newCard) } }
    }

    /**
     * Sets the main commander. Clearing it (null) also clears any partner commander, since a
     * partner pairing without a main commander is meaningless; setting a new one also drops the
     * existing partner if it no longer has a valid Partner pairing with the new commander.
     */
    suspend fun setCommander(deckId: String, card: DeckCardEntry?) {
        update { decks ->
            decks.map { deck ->
                if (deck.id != deckId) return@map deck
                val newPartner = when {
                    card == null -> null
                    deck.partnerCommander != null && !partnersWith(card, deck.partnerCommander) -> null
                    else -> deck.partnerCommander
                }
                deck.copy(commander = card, partnerCommander = newPartner)
            }
        }
    }

    suspend fun setPartnerCommander(deckId: String, card: DeckCardEntry?) {
        update { decks -> decks.map { if (it.id == deckId) it.copy(partnerCommander = card) else it } }
    }

    /** Add a card entry (with its quantity) to a deck, merging into an existing copy. For moves. */
    suspend fun addEntry(deckId: String, entry: DeckCardEntry) {
        update { decks ->
            decks.map { deck ->
                if (deck.id != deckId) return@map deck
                val existing = deck.cards.find { it.scryfallId == entry.scryfallId }
                val newCards = if (existing != null) {
                    deck.cards.map {
                        if (it.scryfallId == entry.scryfallId) it.copy(quantity = it.quantity + entry.quantity) else it
                    }
                } else {
                    deck.cards + entry
                }
                deck.copy(cards = newCards)
            }
        }
    }

    /**
     * Add many entries in a single write, merging into existing copies. Used by decklist import:
     * calling [addEntry] per card would emit the deck list once per card, and every emission makes
     * observers (deck analysis) re-query Scryfall — enough to rate-limit the app mid-import.
     */
    suspend fun addEntries(deckId: String, entries: List<DeckCardEntry>) {
        if (entries.isEmpty()) return
        update { decks ->
            decks.map { deck ->
                if (deck.id != deckId) return@map deck
                var cards = deck.cards
                entries.forEach { entry ->
                    cards = if (cards.any { it.scryfallId == entry.scryfallId }) {
                        cards.map {
                            if (it.scryfallId == entry.scryfallId) {
                                it.copy(quantity = it.quantity + entry.quantity)
                            } else {
                                it
                            }
                        }
                    } else {
                        cards + entry
                    }
                }
                deck.copy(cards = cards)
            }
        }
    }

    // ---- Cut candidates & considering ----

    suspend fun setReplaceable(deckId: String, scryfallId: String, replaceable: Boolean) {
        updateDeck(deckId) { deck ->
            deck.copy(cards = deck.cards.map { if (it.scryfallId == scryfallId) it.copy(replaceable = replaceable) else it })
        }
    }

    /** Adds [card] to the deck's considering list; already-considered cards are left as they are. */
    suspend fun addToConsidering(deckId: String, card: ScryfallCard) {
        addConsideringEntry(
            deckId,
            DeckCardEntry(card.id, card.name, card.displayImageUrl, 1, card.canBeCommander, card.typeLine, card.partnerAbility, card.backImageUrl, card.tags)
        )
    }

    suspend fun addConsideringEntry(deckId: String, entry: DeckCardEntry) {
        updateDeck(deckId) { deck -> deck.copy(considering = deck.considering.withConsidered(entry)) }
    }

    suspend fun removeFromConsidering(deckId: String, scryfallId: String) {
        updateDeck(deckId) { deck -> deck.copy(considering = deck.considering.filterNot { it.scryfallId == scryfallId }) }
    }

    /** Takes a card (all copies) out of the deck and parks it on the considering list instead. */
    suspend fun moveToConsidering(deckId: String, scryfallId: String) {
        updateDeck(deckId) { deck ->
            val entry = deck.cards.find { it.scryfallId == scryfallId } ?: return@updateDeck deck
            deck.withoutCard(scryfallId).copy(considering = deck.considering.withConsidered(entry))
        }
    }

    /** Commits a considered card to the deck, taking it off the considering list. */
    suspend fun addConsideredToDeck(deckId: String, scryfallId: String) {
        updateDeck(deckId) { deck ->
            val entry = deck.considering.find { it.scryfallId == scryfallId } ?: return@updateDeck deck
            deck.copy(
                cards = deck.cards.withCard(entry),
                considering = deck.considering.filterNot { it.scryfallId == scryfallId }
            )
        }
    }

    /**
     * One step: [outScryfallId] leaves the deck for the considering list and [inScryfallId] leaves
     * the considering list for the deck — so nothing is lost, and the change can be swapped back.
     */
    suspend fun swap(deckId: String, outScryfallId: String, inScryfallId: String) {
        updateDeck(deckId) { deck ->
            val outgoing = deck.cards.find { it.scryfallId == outScryfallId } ?: return@updateDeck deck
            val incoming = deck.considering.find { it.scryfallId == inScryfallId } ?: return@updateDeck deck
            val withoutOut = deck.withoutCard(outScryfallId)
            withoutOut.copy(
                cards = withoutOut.cards.withCard(incoming),
                considering = deck.considering.filterNot { it.scryfallId == inScryfallId }.withConsidered(outgoing)
            )
        }
    }

    /** Overwrite the whole deck list — used when restoring/pulling from Drive sync. */
    /**
     * Writes what a sync pulled, as a change to the decks as they are at that moment — so an edit made
     * while the sync was running isn't overwritten. Records no version: what's pulled already carries
     * its own version history, and a new one would log a sync as if the user had edited the deck.
     */
    suspend fun applySync(transform: (List<Deck>) -> List<Deck>) {
        update(recordVersions = false, transform = transform)
    }

    private suspend fun updateDeck(deckId: String, transform: (Deck) -> Deck) {
        update { decks -> decks.map { if (it.id == deckId) transform(it) else it } }
    }

    /** Which decks the user has deleted here, for the sync to tell a deletion from a lost library. */
    val deletedFlow: Flow<Map<String, Long>> = context.decksDataStore.data.map { prefs ->
        prefs[key]?.let { json -> runCatching { adapter.fromJson(json)?.deleted }.getOrNull() } ?: emptyMap()
    }

    private suspend fun update(recordVersions: Boolean = true, deleting: String? = null, transform: (List<Deck>) -> List<Deck>) {
        context.decksDataStore.edit { prefs ->
            val store = prefs[key]?.let { runCatching { adapter.fromJson(it) }.getOrNull() }
            val current = store?.decks ?: emptyList()
            val next = transform(current)
            val recorded = if (recordVersions) {
                next.map { after -> withVersion(current.find { it.id == after.id }, after) }
            } else next
            prefs[key] = adapter.toJson(DeckStore(recorded, noteDeleted(store?.deleted, deleting)))
        }
    }

    companion object {
        /** Edits closer together than this belong to the same sitting, and share one version. */
        private const val SESSION_MILLIS = 30 * 60 * 1000L
        private const val MAX_VERSIONS = 40
        const val BASELINE_PREFIX = "baseline:"

        private fun DeckCardEntry.considered() = copy(replaceable = false)

        /** A considering list with [entry] on it — it's a list of candidates, so no duplicate rows. */
        private fun List<DeckCardEntry>.withConsidered(entry: DeckCardEntry): List<DeckCardEntry> =
            if (any { it.scryfallId == entry.scryfallId }) this else this + entry.considered()

        private fun List<DeckCardEntry>.withCard(entry: DeckCardEntry): List<DeckCardEntry> =
            if (any { it.scryfallId == entry.scryfallId }) {
                map { if (it.scryfallId == entry.scryfallId) it.copy(quantity = it.quantity + entry.quantity) else it }
            } else this + entry.considered()

        private fun Deck.withoutCard(scryfallId: String) = copy(
            cards = cards.filterNot { it.scryfallId == scryfallId },
            commander = commander?.takeUnless { it.scryfallId == scryfallId },
            partnerCommander = partnerCommander?.takeUnless { it.scryfallId == scryfallId }
        )

        private fun snapshotOf(deck: Deck): DeckVersion {
            val cards = LinkedHashMap<String, Int>()
            deck.cards.forEach { cards[it.name] = (cards[it.name] ?: 0) + it.quantity }
            return DeckVersion(
                id = "",
                savedAt = 0L,
                cards = cards,
                commanders = listOfNotNull(deck.commander?.name, deck.partnerCommander?.name)
            )
        }

        /**
         * Records [after]'s list as a version when it actually differs from [before]'s — changes to
         * tags, flags, the considering list or a card's printing don't count. Edits within one
         * sitting replace that sitting's version instead of piling up one per tap. A deck edited for
         * the first time since versions existed gets its prior list saved first, so there's a
         * "before" to compare against.
         */
        internal fun withVersion(before: Deck?, after: Deck): Deck {
            val snapshot = snapshotOf(after)
            val previous = before?.let { snapshotOf(it) }
            if (previous != null && previous.cards == snapshot.cards && previous.commanders == snapshot.commanders) return after
            if (snapshot.cards.isEmpty() && after.versions.isEmpty()) return after

            val now = System.currentTimeMillis()
            var versions = after.versions
            if (versions.isEmpty() && previous != null && previous.cards.isNotEmpty()) {
                versions = versions + previous.copy(id = BASELINE_PREFIX + UUID.randomUUID(), savedAt = now - 1)
            }
            // A whole list arriving at once into an empty deck — a precon or decklist import — is
            // the starting list too. Without this the first tweak minutes later folds into it and
            // the imported list is gone. One card at a time (building from scratch) isn't one.
            val importedFromNothing = (previous == null || previous.cards.isEmpty()) && snapshot.cards.size > 1
            if (versions.isEmpty() && importedFromNothing) {
                return after.copy(versions = listOf(snapshot.copy(id = BASELINE_PREFIX + UUID.randomUUID(), savedAt = now)))
            }
            val last = versions.lastOrNull()
            // A baseline is the list from before versions existed — never fold new edits into it. Nor
            // into a version that's had a game logged on it: folding would move that version's time
            // past the game and credit the game to the version before.
            val sameSitting = last != null &&
                !last.id.startsWith(BASELINE_PREFIX) &&
                now - last.savedAt < SESSION_MILLIS &&
                after.gameResults.none { it.playedAt >= last.savedAt }
            versions = if (sameSitting) {
                versions.dropLast(1) + snapshot.copy(id = last!!.id, savedAt = now)
            } else {
                versions + snapshot.copy(id = UUID.randomUUID().toString(), savedAt = now)
            }
            return after.copy(versions = versions.takeLast(MAX_VERSIONS))
        }
    }
}
