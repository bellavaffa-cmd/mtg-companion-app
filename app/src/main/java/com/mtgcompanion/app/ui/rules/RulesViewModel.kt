package com.mtgcompanion.app.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.ComboRepository
import com.mtgcompanion.app.data.isOffline
import com.mtgcompanion.app.data.rules.Keyword
import com.mtgcompanion.app.data.rules.Keywords
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallRuling
import com.mtgcompanion.app.network.spellbook.Variant
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RulesMode { KEYWORDS, RULINGS, COMBOS }

sealed interface RulingsState {
    data object Idle : RulingsState
    data object Loading : RulingsState
    data class Loaded(val card: ScryfallCard, val rulings: List<ScryfallRuling>) : RulingsState
    data class Error(val message: String) : RulingsState
}

sealed interface ComboSearchState {
    data object Idle : ComboSearchState
    data object Loading : ComboSearchState
    data class Loaded(val combos: List<Variant>) : ComboSearchState
    data class Error(val message: String) : ComboSearchState
}

private data class ComboQueryInput(val card: String, val result: String, val colors: Set<Char>, val mode: RulesMode)

/** Backs the Rules tab: a local keyword glossary, Scryfall card-rulings lookup, and a Commander
 * Spellbook combo search (by card name, what it produces, and color identity). */
@OptIn(FlowPreview::class)
class RulesViewModel(
    private val repository: CardRepository = CardRepository(),
    private val comboRepository: ComboRepository = ComboRepository()
) : ViewModel() {

    private val _mode = MutableStateFlow(RulesMode.KEYWORDS)
    val mode: StateFlow<RulesMode> = _mode.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Filtered keyword glossary — local and instant. */
    val keywords: StateFlow<List<Keyword>> = _query
        .map { Keywords.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Keywords.all)

    private val _rulings = MutableStateFlow<RulingsState>(RulingsState.Idle)
    val rulings: StateFlow<RulingsState> = _rulings.asStateFlow()

    private val _comboCardQuery = MutableStateFlow("")
    val comboCardQuery: StateFlow<String> = _comboCardQuery.asStateFlow()

    private val _comboResultQuery = MutableStateFlow("")
    val comboResultQuery: StateFlow<String> = _comboResultQuery.asStateFlow()

    private val _comboColors = MutableStateFlow<Set<Char>>(emptySet())
    val comboColors: StateFlow<Set<Char>> = _comboColors.asStateFlow()

    private val _combos = MutableStateFlow<ComboSearchState>(ComboSearchState.Idle)
    val combos: StateFlow<ComboSearchState> = _combos.asStateFlow()

    init {
        // Fetch rulings as the user types a card name (only while on the Rulings tab).
        viewModelScope.launch {
            combine(_query, _mode) { q, m -> q.trim() to m }
                .debounce(350)
                .distinctUntilChanged()
                .collectLatest { (q, m) ->
                    when {
                        m != RulesMode.RULINGS -> Unit
                        q.isBlank() -> _rulings.value = RulingsState.Idle
                        else -> runRulings(q)
                    }
                }
        }
        // Search combos as the user fills in any of the 3 filters (only while on the Combos tab).
        viewModelScope.launch {
            combine(_comboCardQuery, _comboResultQuery, _comboColors, _mode) { card, result, colors, m ->
                ComboQueryInput(card.trim(), result.trim(), colors, m)
            }
                .debounce(350)
                .distinctUntilChanged()
                .collectLatest { input ->
                    when {
                        input.mode != RulesMode.COMBOS -> Unit
                        input.card.isBlank() && input.result.isBlank() && input.colors.isEmpty() ->
                            _combos.value = ComboSearchState.Idle
                        else -> runComboSearch(input)
                    }
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun onComboCardQueryChange(newQuery: String) {
        _comboCardQuery.value = newQuery
    }

    fun onComboResultQueryChange(newQuery: String) {
        _comboResultQuery.value = newQuery
    }

    fun toggleComboColor(color: Char) {
        _comboColors.value = _comboColors.value.let { if (color in it) it - color else it + color }
    }

    fun setMode(newMode: RulesMode) {
        if (_mode.value == newMode) return
        _mode.value = newMode
        if (newMode == RulesMode.KEYWORDS) {
            _rulings.value = RulingsState.Idle
            _combos.value = ComboSearchState.Idle
        }
    }

    private suspend fun runComboSearch(input: ComboQueryInput) {
        _combos.value = ComboSearchState.Loading
        val parts = mutableListOf<String>()
        if (input.card.isNotBlank()) parts += "card:\"${input.card}\""
        if (input.result.isNotBlank()) parts += "result:\"${input.result}\""
        if (input.colors.isNotEmpty()) parts += "ci<=${input.colors.sorted().joinToString("")}"
        _combos.value = try {
            ComboSearchState.Loaded(comboRepository.search(parts.joinToString(" ")))
        } catch (e: Exception) {
            ComboSearchState.Error(
                if (isOffline(e)) "You're offline — combo search needs an internet connection." else "Search failed."
            )
        }
    }

    private suspend fun runRulings(cardName: String) {
        _rulings.value = RulingsState.Loading
        _rulings.value = try {
            val (card, rulings) = repository.getRulings(cardName)
            RulingsState.Loaded(card, rulings)
        } catch (e: Exception) {
            RulingsState.Error(
                when {
                    isOffline(e) -> "You're offline — card rulings need an internet connection."
                    else -> "No card found matching \"$cardName\"."
                }
            )
        }
    }
}
