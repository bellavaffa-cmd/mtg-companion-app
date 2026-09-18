package com.mtgcompanion.app.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.isOffline
import com.mtgcompanion.app.data.rules.Keyword
import com.mtgcompanion.app.data.rules.Keywords
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallRuling
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

enum class RulesMode { KEYWORDS, RULINGS }

sealed interface RulingsState {
    data object Idle : RulingsState
    data object Loading : RulingsState
    data class Loaded(val card: ScryfallCard, val rulings: List<ScryfallRuling>) : RulingsState
    data class Error(val message: String) : RulingsState
}

/** Backs the Rules tab: a local keyword glossary and Scryfall card-rulings lookup. */
@OptIn(FlowPreview::class)
class RulesViewModel(private val repository: CardRepository = CardRepository()) : ViewModel() {

    private val _mode = MutableStateFlow(RulesMode.KEYWORDS)
    val mode: StateFlow<RulesMode> = _mode.asStateFlow()

    // Each tab keeps its own search: a keyword typed on one isn't a card name on the other.
    private val keywordQuery = MutableStateFlow("")
    private val rulingsQuery = MutableStateFlow("")

    /** What the search box shows: the current tab's own query. */
    val query: StateFlow<String> = combine(_mode, keywordQuery, rulingsQuery) { m, k, r ->
        if (m == RulesMode.RULINGS) r else k
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Filtered keyword glossary — local and instant. */
    val keywords: StateFlow<List<Keyword>> = keywordQuery
        .map { Keywords.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Keywords.all)

    private val _rulings = MutableStateFlow<RulingsState>(RulingsState.Idle)
    val rulings: StateFlow<RulingsState> = _rulings.asStateFlow()

    init {
        // Fetch rulings as the user types a card name (only while on the Rulings tab).
        viewModelScope.launch {
            combine(rulingsQuery, _mode) { q, m -> q.trim() to m }
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
    }

    fun onQueryChange(newQuery: String) {
        if (_mode.value == RulesMode.RULINGS) rulingsQuery.value = newQuery else keywordQuery.value = newQuery
    }

    fun setMode(newMode: RulesMode) {
        if (_mode.value == newMode) return
        _mode.value = newMode
        if (newMode == RulesMode.KEYWORDS) _rulings.value = RulingsState.Idle
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
