package com.mtgcompanion.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardListImporter
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckOwnership
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.GameMode
import com.mtgcompanion.app.data.ListSection
import com.mtgcompanion.app.data.PreconRepository
import com.mtgcompanion.app.data.SAMPLE_BINDER_NAME
import com.mtgcompanion.app.data.parseCardList
import com.mtgcompanion.app.data.pickSamplePrecon
import com.mtgcompanion.app.data.sampleBinderPicks
import com.mtgcompanion.app.data.sampleDeckName
import com.mtgcompanion.app.data.withoutSampleCollections
import com.mtgcompanion.app.data.withoutSampleDecks
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.collection.ImportProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Where pasting a first deck is up to. */
sealed interface PasteProgress {
    data object Idle : PasteProgress
    data class Working(val done: Int, val total: Int) : PasteProgress
    data class Done(val deckId: String, val added: Int, val missing: List<String>) : PasteProgress
    data class Failed(val message: String) : PasteProgress
}

/**
 * The welcome flow's work: importing a collection (as the Collection tab's Import list does), a
 * first deck from a pasted list, and the sample deck and binder. Mirrors the web app's
 * src/onboarding/useWelcome.ts and PasteDeckDialog.tsx.
 */
class WelcomeViewModel(
    private val deckRepository: DeckRepository,
    private val collectionRepository: CollectionRepository,
    private val cardRepository: CardRepository = CardRepository(),
    private val preconRepository: PreconRepository = PreconRepository()
) : ViewModel() {

    private val _importProgress = MutableStateFlow<ImportProgress>(ImportProgress.Idle)
    val importProgress: StateFlow<ImportProgress> = _importProgress.asStateFlow()

    private val _pasteProgress = MutableStateFlow<PasteProgress>(PasteProgress.Idle)
    val pasteProgress: StateFlow<PasteProgress> = _pasteProgress.asStateFlow()

    private val _addingSamples = MutableStateFlow(false)
    val addingSamples: StateFlow<Boolean> = _addingSamples.asStateFlow()
    private val _samplesError = MutableStateFlow<String?>(null)
    val samplesError: StateFlow<String?> = _samplesError.asStateFlow()

    /** A list from another app into a new binder, or (no [name]) the Unsorted pile — CollectionsViewModel.importBinder's way. */
    fun importBinder(name: String?, text: String) {
        val lines = parseCardList(text).lines
        if (lines.isEmpty()) return
        viewModelScope.launch {
            _importProgress.value = ImportProgress.Working(0, lines.size)
            _importProgress.value = try {
                val result = CardListImporter(cardRepository).resolve(lines) { done, total -> _importProgress.value = ImportProgress.Working(done, total) }
                val binderName = name?.ifBlank { "Imported" }
                if (result.cards.isNotEmpty()) {
                    if (binderName == null) {
                        collectionRepository.addUnsorted(result.cards.map { it.toEntry() })
                    } else {
                        val binder = collectionRepository.createCollection(binderName, CollectionType.OWNED)
                        collectionRepository.addEntries(binder.id, result.cards.map { it.toEntry() })
                    }
                }
                ImportProgress.Done(result, binderName ?: "your collection (Unsorted)")
            } catch (e: java.io.IOException) {
                ImportProgress.Failed("You're offline. Try again when you're connected.")
            } catch (e: Exception) {
                ImportProgress.Failed(e.message ?: "Something went wrong.")
            }
        }
    }

    fun resetImport() { _importProgress.value = ImportProgress.Idle }

    /**
     * A pasted decklist as a new Commander deck: the main deck's lines in it, sideboard and maybeboard
     * lines on its Considering list. The deck is only made once some cards are found.
     */
    fun pasteDeck(name: String, text: String) {
        val lines = parseCardList(text).lines.filter { it.name != null }
        if (lines.isEmpty()) return
        viewModelScope.launch {
            _pasteProgress.value = PasteProgress.Working(0, lines.size)
            _pasteProgress.value = try {
                val importer = CardListImporter(cardRepository)
                val main = lines.filter { it.section == ListSection.MAIN }
                val rest = lines.filter { it.section != ListSection.MAIN }
                val mainResult = importer.resolve(main) { done, _ -> _pasteProgress.value = PasteProgress.Working(done, lines.size) }
                val restResult = importer.resolve(rest) { done, _ -> _pasteProgress.value = PasteProgress.Working(main.size + done, lines.size) }
                val entries = mainResult.cards.map { it.card.toDeckEntry((it.quantity + it.foilQuantity).coerceIn(1, 99)) }
                val considering = restResult.cards.map { it.card.toDeckEntry(1) }
                if (entries.isEmpty() && considering.isEmpty()) {
                    PasteProgress.Failed("None of those cards could be found. Check the list and try again.")
                } else {
                    val deck = deckRepository.createDeckWithCards(name.trim().ifEmpty { "My deck" }, GameMode.COMMANDER, entries, null, null)
                    if (considering.isNotEmpty()) deckRepository.addConsideringEntries(deck.id, considering)
                    PasteProgress.Done(deck.id, entries.sumOf { it.quantity }, mainResult.missing + restResult.missing)
                }
            } catch (e: java.io.IOException) {
                PasteProgress.Failed("You're offline. Try again when you're connected.")
            } catch (e: Exception) {
                PasteProgress.Failed(e.message ?: "Something went wrong.")
            }
        }
    }

    fun resetPaste() { _pasteProgress.value = PasteProgress.Idle }

    /**
     * The sample deck — a real precon from MTGJSON, as the precons screen imports it — and a dozen of
     * its cards in a binder, both flagged as samples so they never sync. Adding again replaces them.
     */
    fun addSamples(onAdded: (deckId: String) -> Unit) {
        if (_addingSamples.value) return
        viewModelScope.launch {
            _addingSamples.value = true
            _samplesError.value = null
            try {
                val (deck, binder) = buildSamples()
                deckRepository.change { decks -> withoutSampleDecks(decks) + deck }
                collectionRepository.changeStorage { collections -> withoutSampleCollections(collections) + binder }
                onAdded(deck.id)
            } catch (e: Exception) {
                _samplesError.value = "Couldn't add the samples. Check your connection and try again."
            } finally {
                _addingSamples.value = false
            }
        }
    }

    fun removeSamples() {
        viewModelScope.launch { removeAllSamples(deckRepository, collectionRepository) }
    }

    private suspend fun buildSamples(): Pair<Deck, Collection> {
        val precon = pickSamplePrecon(preconRepository.listCommanderPrecons()) { it.name }
            ?: throw IllegalStateException("No precon to use")
        val contents = preconRepository.getContents(precon.fileName)
        val all = contents.commander + contents.cards
        val byId = cardRepository.getCardsByIds(all.mapNotNull { it.scryfallId }.distinct()).associateBy { it.id }
        val entries = mutableListOf<DeckCardEntry>()
        for (line in all) {
            val card = line.scryfallId?.let { byId[it] } ?: continue
            if (entries.none { it.scryfallId == card.id }) entries += card.toDeckEntry(line.quantity)
        }
        if (entries.isEmpty()) throw IllegalStateException("No sample cards found")
        val commanders = contents.commander.mapNotNull { c -> entries.firstOrNull { it.scryfallId == c.scryfallId } }
        val deck = Deck(
            id = UUID.randomUUID().toString(),
            name = sampleDeckName(precon.name),
            gameMode = GameMode.COMMANDER.name,
            cards = entries,
            commander = commanders.getOrNull(0),
            partnerCommander = commanders.getOrNull(1),
            // Not counted as cards the user owns.
            ownership = DeckOwnership.VIRTUAL.name,
            sample = true
        )
        val binder = Collection(
            id = UUID.randomUUID().toString(),
            name = SAMPLE_BINDER_NAME,
            type = CollectionType.OWNED.name,
            sample = true,
            entries = sampleBinderPicks(entries, commanders.map { it.scryfallId }).map {
                CollectionEntry(it.scryfallId, it.name, it.imageUrl, quantity = 1, backImageUrl = it.backImageUrl, tags = it.tags)
            }
        )
        return deck to binder
    }

    class Factory(
        private val deckRepository: DeckRepository,
        private val collectionRepository: CollectionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WelcomeViewModel(deckRepository, collectionRepository) as T
    }
}

private fun ScryfallCard.toDeckEntry(quantity: Int) =
    DeckCardEntry(id, name, displayImageUrl, quantity, canBeCommander, typeLine, partnerAbility, backImageUrl, tags)

/** "Remove samples": the sample deck and binder go, from wherever it's tapped. Nothing to sync. */
suspend fun removeAllSamples(deckRepository: DeckRepository, collectionRepository: CollectionRepository) {
    deckRepository.applySync { withoutSampleDecks(it) }
    collectionRepository.applySync { withoutSampleCollections(it) }
}
