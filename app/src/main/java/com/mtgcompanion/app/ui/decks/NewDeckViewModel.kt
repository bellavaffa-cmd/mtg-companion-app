package com.mtgcompanion.app.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.BACKGROUND_QUERY
import com.mtgcompanion.app.data.CommanderSort
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.GameMode
import com.mtgcompanion.app.data.SecondCommanderKind
import com.mtgcompanion.app.data.allowsSecondCommander
import com.mtgcompanion.app.data.commanderQuery
import com.mtgcompanion.app.data.defaultDeckName
import com.mtgcompanion.app.data.pairCard
import com.mtgcompanion.app.data.secondCommanderKind
import com.mtgcompanion.app.data.withCardInfo
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The new-deck flow's steps, in order. Formats without a commander go from FORMAT straight to NAME. */
enum class NewDeckStep { FORMAT, COMMANDER, SECOND, NAME }

/** The picker's own controls: text, colour chips and sort. Each step starts with fresh ones. */
data class PickerFilter(
    val query: String = "",
    val colours: Set<String> = emptySet(),
    val sort: CommanderSort = CommanderSort.POPULAR
)

data class NewDeckState(
    val step: NewDeckStep = NewDeckStep.FORMAT,
    val mode: GameMode? = null,
    val commander: ScryfallCard? = null,
    /** What kind of second commander [commander] can take in this format; null when none. */
    val secondKind: SecondCommanderKind? = null,
    val partner: ScryfallCard? = null,
    val filter: PickerFilter = PickerFilter(),
    /** The name the user typed; null keeps the suggested one ([suggestedName]). */
    val typedName: String? = null,
    val creating: Boolean = false
) {
    val suggestedName: String
        get() = defaultDeckName(mode ?: GameMode.DEFAULT, commander?.name, partner?.name)
    val name: String get() = typedName ?: suggestedName
}

/**
 * Making a deck from nothing: a format, then (for Commander and Brawl) its commander from every
 * legal one, an optional second commander when the first allows one, and a name.
 */
class NewDeckViewModel(private val repository: DeckRepository) : ViewModel() {

    private val _state = MutableStateFlow(NewDeckState())
    val state: StateFlow<NewDeckState> = _state.asStateFlow()

    /** The list the current step picks from, as it loads. */
    private val _catalog = MutableStateFlow<StateFlow<CatalogState>?>(null)
    val catalog: StateFlow<StateFlow<CatalogState>?> = _catalog.asStateFlow()

    fun pickFormat(mode: GameMode) {
        val query = commanderQuery(mode)
        _state.value = NewDeckState(
            step = if (query != null) NewDeckStep.COMMANDER else NewDeckStep.NAME,
            mode = mode
        )
        _catalog.value = query?.let { CommanderCatalog.load(it) }
    }

    fun pickCommander(card: ScryfallCard) {
        val mode = _state.value.mode ?: return
        val kind = secondCommanderKind(card.pairCard)?.takeIf { allowsSecondCommander(mode, it) }
        _state.update {
            it.copy(
                commander = card, secondKind = kind, partner = null, filter = PickerFilter(), typedName = null,
                step = if (kind != null) NewDeckStep.SECOND else NewDeckStep.NAME
            )
        }
        _catalog.value = when (kind) {
            null -> null
            // A Background isn't a commander on its own, so it has its own list.
            SecondCommanderKind.BACKGROUND -> CommanderCatalog.load(BACKGROUND_QUERY)
            // Partners, Doctors and companions are all commanders themselves.
            else -> commanderQuery(mode)?.let { CommanderCatalog.load(it) }
        }
    }

    /** [card] as the second commander, or null to go on without one. */
    fun pickSecond(card: ScryfallCard?) {
        _state.update { it.copy(partner = card, step = NewDeckStep.NAME) }
    }

    fun setFilter(filter: PickerFilter) {
        _state.update { it.copy(filter = filter) }
    }

    /** Asks again for a list that stopped part way (offline, say). */
    fun retry() {
        val s = _state.value
        val mode = s.mode ?: return
        val query = when {
            s.step == NewDeckStep.SECOND && s.secondKind == SecondCommanderKind.BACKGROUND -> BACKGROUND_QUERY
            else -> commanderQuery(mode)
        } ?: return
        _catalog.value = CommanderCatalog.load(query)
    }

    fun setName(name: String) {
        _state.update { it.copy(typedName = name) }
    }

    /** One step back; false when already at the first, so the screen itself should close. */
    fun back(): Boolean {
        val s = _state.value
        when (s.step) {
            NewDeckStep.FORMAT -> return false
            NewDeckStep.COMMANDER -> {
                _state.value = NewDeckState()
                _catalog.value = null
            }
            NewDeckStep.SECOND -> s.mode?.let { pickFormat(it) }
            NewDeckStep.NAME -> when {
                s.commander != null && s.secondKind != null -> {
                    val commander = s.commander
                    pickCommander(commander)
                }
                s.commander != null -> s.mode?.let { pickFormat(it) }
                else -> {
                    _state.value = NewDeckState()
                    _catalog.value = null
                }
            }
        }
        return true
    }

    /**
     * Makes the deck — its commanders already in it — and hands back its id. Called once; a
     * second tap while it's being made does nothing.
     */
    fun create(onCreated: (deckId: String) -> Unit) {
        val s = _state.value
        val mode = s.mode ?: return
        if (s.creating) return
        _state.update { it.copy(creating = true) }
        val name = s.name.trim().ifBlank { s.suggestedName }
        viewModelScope.launch {
            val commander = s.commander?.let(::entryFor)
            val partner = s.partner?.let(::entryFor)
            val deck = repository.createDeckWithCards(
                name = name,
                gameMode = mode,
                entries = listOfNotNull(commander, partner),
                commander = commander,
                partnerCommander = partner
            )
            onCreated(deck.id)
        }
    }

    /** One copy of [card] with everything a deck keeps about it (commander-ness, pairing, faces, tags). */
    private fun entryFor(card: ScryfallCard): DeckCardEntry =
        DeckCardEntry(card.id, card.name, card.displayImageUrl).withCardInfo(card)

    class Factory(private val repository: DeckRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NewDeckViewModel(repository) as T
    }
}
