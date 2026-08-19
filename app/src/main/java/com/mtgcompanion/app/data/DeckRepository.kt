package com.mtgcompanion.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.flow.Flow
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

    suspend fun setGameMode(deckId: String, gameMode: GameMode) {
        update { decks -> decks.map { if (it.id == deckId) it.copy(gameMode = gameMode.name) else it } }
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
        update { decks -> decks.filterNot { it.id == deckId } }
    }

    suspend fun addCardToDeck(deckId: String, card: ScryfallCard) {
        update { decks ->
            decks.map { deck ->
                if (deck.id != deckId) return@map deck
                val existing = deck.cards.find { it.scryfallId == card.id }
                val newCards = if (existing != null) {
                    deck.cards.map { if (it.scryfallId == card.id) it.copy(quantity = it.quantity + 1) else it }
                } else {
                    deck.cards + DeckCardEntry(card.id, card.name, card.displayImageUrl, 1, card.canBeCommander, card.typeLine, card.partnerAbility, card.backImageUrl)
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
        update { decks ->
            decks.map { deck ->
                if (deck.id != deckId) return@map deck
                val newCards = deck.cards.map {
                    if (it.scryfallId == oldScryfallId)
                        it.copy(scryfallId = newCard.id, name = newCard.name, imageUrl = newCard.displayImageUrl, typeLine = newCard.typeLine, partnerAbility = newCard.partnerAbility, backImageUrl = newCard.backImageUrl)
                    else it
                }
                fun retarget(entry: DeckCardEntry?) = entry
                    ?.takeIf { it.scryfallId == oldScryfallId }
                    ?.copy(scryfallId = newCard.id, name = newCard.name, imageUrl = newCard.displayImageUrl, typeLine = newCard.typeLine, partnerAbility = newCard.partnerAbility, backImageUrl = newCard.backImageUrl)
                    ?: entry
                deck.copy(cards = newCards, commander = retarget(deck.commander), partnerCommander = retarget(deck.partnerCommander))
            }
        }
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

    /** Overwrite the whole deck list — used when restoring/pulling from Drive sync. */
    suspend fun replaceAll(decks: List<Deck>) {
        update { decks }
    }

    private suspend fun update(transform: (List<Deck>) -> List<Deck>) {
        context.decksDataStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { adapter.fromJson(it)?.decks }.getOrNull() } ?: emptyList()
            prefs[key] = adapter.toJson(DeckStore(transform(current)))
        }
    }
}
