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
                    deck.cards + DeckCardEntry(card.id, card.name, card.displayImageUrl, 1, card.canBeCommander)
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
                deck.copy(cards = deck.cards.filterNot { it.scryfallId == scryfallId }, commander = newCommander)
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
                    deck.copy(cards = deck.cards.filterNot { it.scryfallId == scryfallId }, commander = newCommander)
                } else {
                    deck.copy(cards = deck.cards.map { if (it.scryfallId == scryfallId) it.copy(quantity = quantity) else it })
                }
            }
        }
    }

    /** Swap a card to a different printing (art), keeping its quantity; updates the commander too. */
    suspend fun changeCardPrinting(deckId: String, oldScryfallId: String, newCard: ScryfallCard) {
        update { decks ->
            decks.map { deck ->
                if (deck.id != deckId) return@map deck
                val newCards = deck.cards.map {
                    if (it.scryfallId == oldScryfallId)
                        it.copy(scryfallId = newCard.id, name = newCard.name, imageUrl = newCard.displayImageUrl)
                    else it
                }
                val newCommander = deck.commander
                    ?.takeIf { it.scryfallId == oldScryfallId }
                    ?.copy(scryfallId = newCard.id, name = newCard.name, imageUrl = newCard.displayImageUrl)
                    ?: deck.commander
                deck.copy(cards = newCards, commander = newCommander)
            }
        }
    }

    suspend fun setCommander(deckId: String, card: DeckCardEntry?) {
        update { decks -> decks.map { if (it.id == deckId) it.copy(commander = card) else it } }
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
