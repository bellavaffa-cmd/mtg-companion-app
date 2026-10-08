package com.mtgcompanion.app.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardListImporter
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.data.CubeCard
import com.mtgcompanion.app.data.CubeSettings
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.ListLine
import com.mtgcompanion.app.data.PullListData
import com.mtgcompanion.app.data.addToCube
import com.mtgcompanion.app.data.asCube
import com.mtgcompanion.app.data.cubeBoxPlace
import com.mtgcompanion.app.data.cubeCardOf
import com.mtgcompanion.app.data.cubeSettings
import com.mtgcompanion.app.data.cubeSize
import com.mtgcompanion.app.data.isCube
import com.mtgcompanion.app.data.limitedDeckFromCube
import com.mtgcompanion.app.data.markCubeProxy
import com.mtgcompanion.app.data.moveIntoCubeBox
import com.mtgcompanion.app.data.newCube
import com.mtgcompanion.app.data.parseCubeList
import com.mtgcompanion.app.data.removeFromCube
import com.mtgcompanion.app.data.savePlace
import com.mtgcompanion.app.data.withCardInfo
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** The cube's cards being added from a list: how far it's got, then what wasn't found. */
data class CubeImportState(val done: Int = 0, val total: Int = 0, val running: Boolean = false, val added: Int? = null, val skipped: Int = 0, val missing: List<String> = emptyList())

/**
 * One cube (data/Cube.kt), with the library around it: the collection it's built from and the other
 * decks. Scryfall's data for the cube's cards and the owned cards is looked up once and kept, for the
 * balance, the filters and the fill suggestions.
 */
class CubeViewModel(
    private val cubeId: String,
    private val decks: DeckRepository,
    private val collectionsRepo: CollectionRepository,
    private val cardRepository: CardRepository = CardRepository()
) : ViewModel() {

    val cube: StateFlow<Deck?> = decks.deckFlow(cubeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val collections: StateFlow<List<Collection>> = collectionsRepo.collectionsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allDecks: StateFlow<List<Deck>> = decks.decksFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _cards = MutableStateFlow<Map<String, ScryfallCard>>(emptyMap())
    /** Scryfall's data by id, for the cube's cards and every owned card; filled in as it's looked up. */
    val cards: StateFlow<Map<String, ScryfallCard>> = _cards.asStateFlow()
    private val asked = HashSet<String>()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _import = MutableStateFlow(CubeImportState())
    val importState: StateFlow<CubeImportState> = _import.asStateFlow()

    /** Owned printings (not the Wishlist's), one entry per printing, copies added together. */
    val owned: StateFlow<List<DeckCardEntry>> = collectionsRepo.collectionsFlow.map { cols ->
        val byId = LinkedHashMap<String, DeckCardEntry>()
        for (c in cols) if (c.kind != CollectionType.WISHLIST) for (e in c.entries) {
            val n = e.quantity + e.foilQuantity
            if (n <= 0) continue
            val had = byId[e.scryfallId]
            byId[e.scryfallId] = had?.copy(quantity = had.quantity + n)
                ?: DeckCardEntry(e.scryfallId, e.name, e.imageUrl, quantity = n, backImageUrl = e.backImageUrl, tags = e.tags, userTags = e.userTags)
        }
        byId.values.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            combine(cube, owned) { c, o -> c?.cards.orEmpty().map { it.scryfallId } + o.map { it.scryfallId } }.collect { ids ->
                val missing = ids.filter { it !in asked }.distinct()
                if (missing.isEmpty()) return@collect
                asked += missing
                _loading.value = true
                val found = runCatching { cardRepository.getCardsByIds(missing) }.getOrDefault(emptyList())
                _cards.update { it + found.associateBy { c -> c.id } }
                _loading.value = false
            }
        }
    }

    /** The facts of a card the lookup has, by id. */
    fun factsOf(id: String): CubeCard? = _cards.value[id]?.let { cubeCardOf(it) }

    /** [e] with what the cube needs to know about its card, when that's been looked up. */
    fun withInfo(e: DeckCardEntry): DeckCardEntry = _cards.value[e.scryfallId]?.let { e.withCardInfo(it) } ?: e

    private fun change(f: (Deck) -> Deck) {
        viewModelScope.launch {
            decks.change { all -> all.map { if (it.id == cubeId && it.isCube) asCube(f(it)) else it } }
        }
    }

    /** Adds [entries]; answers how many went in and how many were left out as already there. */
    fun add(entries: List<DeckCardEntry>, done: (added: Int, skipped: Int) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            var result = 0 to 0
            decks.change { all ->
                all.map {
                    if (it.id != cubeId || !it.isCube) it
                    else addToCube(it, entries.map(::withInfo)).also { r -> result = r.added to r.skipped.size }.cube.let(::asCube)
                }
            }
            done(result.first, result.second)
        }
    }

    fun addCard(card: ScryfallCard, done: (added: Int, skipped: Int) -> Unit = { _, _ -> }) {
        _cards.update { it + (card.id to card) }
        add(listOf(DeckCardEntry(card.id, card.name, card.displayImageUrl, quantity = 1).withCardInfo(card)), done)
    }

    fun remove(scryfallId: String) = change { removeFromCube(it, scryfallId) }
    fun setProxy(scryfallId: String, proxy: Boolean) = change { markCubeProxy(it, scryfallId, proxy) }
    fun rename(name: String) { if (name.isNotBlank()) change { it.copy(name = name.trim()) } }
    fun setSettings(size: Int, singleton: Boolean) = change { it.copy(cube = it.cubeSettings.copy(size = cubeSize(size), singleton = singleton)) }
    fun setPacks(seats: Int, packs: Int, packSize: Int) = change { it.copy(cube = it.cubeSettings.copy(seats = seats, packs = packs, packSize = packSize)) }

    fun delete(done: () -> Unit) {
        viewModelScope.launch { decks.deleteDeck(cubeId); done() }
    }

    /** Makes the cube box: a new storage place, and the cube pointing at it. */
    fun makeBox() {
        viewModelScope.launch {
            val c = cube.value ?: return@launch
            val place = cubeBoxPlace(c, UUID.randomUUID().toString(), System.currentTimeMillis())
            collectionsRepo.changeStorage { savePlace(it, place) }
            change { it.copy(cube = it.cubeSettings.copy(boxPlaceId = place.id)) }
        }
    }

    /** "Move into cube box": the ticked rows' copies go into the box. Answers how many moved. */
    fun moveIntoBox(list: PullListData, ticked: Set<String>, done: (Int) -> Unit) {
        val box = cube.value?.cube?.boxPlaceId ?: return
        viewModelScope.launch {
            var moved = 0
            collectionsRepo.changeStorage { cols -> moveIntoCubeBox(list, ticked, cols, box).also { moved = it.second }.first }
            done(moved)
        }
    }

    /** Adds a pasted or loaded list (CubeCobra plain text and the like), resolving names on Scryfall. */
    fun importList(text: String) {
        val lines = parseCubeList(text)
        if (lines.isEmpty()) {
            _import.value = CubeImportState(added = 0)
            return
        }
        viewModelScope.launch {
            _import.value = CubeImportState(total = lines.size, running = true)
            val result = runCatching {
                CardListImporter(cardRepository).resolve(lines.map { ListLine(it.qty, it.name) }) { done, total ->
                    _import.update { s -> s.copy(done = done, total = total) }
                }
            }.getOrNull()
            if (result == null) {
                _import.value = CubeImportState(added = 0, missing = listOf("Couldn't reach Scryfall — try again when you're online."))
                return@launch
            }
            _cards.update { it + result.cards.associate { c -> c.card.id to c.card } }
            val entries = result.cards.map { DeckCardEntry(it.card.id, it.card.name, it.card.displayImageUrl, quantity = it.quantity + it.foilQuantity).withCardInfo(it.card) }
            add(entries) { added, skipped -> _import.value = CubeImportState(added = added, skipped = skipped, missing = result.missing) }
        }
    }

    fun clearImport() { _import.value = CubeImportState() }

    /** A draft or sealed deck from packs of the cube; answers its id. */
    fun startLimited(cardIds: List<String>, name: String, note: String, done: (String) -> Unit) {
        val c = cube.value ?: return
        val deck = limitedDeckFromCube(c, cardIds, UUID.randomUUID().toString(), name, note, System.currentTimeMillis())
        viewModelScope.launch {
            decks.change { it + deck }
            done(deck.id)
        }
    }

    class Factory(
        private val cubeId: String,
        private val decks: DeckRepository,
        private val collections: CollectionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CubeViewModel(cubeId, decks, collections) as T
    }
}

/** Makes a new cube; answers its id. */
suspend fun createCube(decks: DeckRepository, name: String, size: Int, singleton: Boolean, cards: List<DeckCardEntry> = emptyList()): String {
    val made = newCube(UUID.randomUUID().toString(), name, size, singleton, System.currentTimeMillis())
    val filled = if (cards.isEmpty()) made else addToCube(made, cards).cube
    decks.change { it + filled }
    return filled.id
}

/** The cube's settings with the draft's numbers saved. */
fun CubeSettings.packsOr(): Triple<Int, Int, Int> = Triple(seats ?: com.mtgcompanion.app.data.CUBE_SEATS, packs ?: com.mtgcompanion.app.data.CUBE_PACKS, packSize ?: com.mtgcompanion.app.data.CUBE_PACK_SIZE)
