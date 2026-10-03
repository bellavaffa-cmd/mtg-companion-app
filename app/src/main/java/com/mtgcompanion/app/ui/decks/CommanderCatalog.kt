package com.mtgcompanion.app.ui.decks

import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.isOffline
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Everything one search found so far. [complete] once the last page is in; [error] when a page
 * failed (the cards before it stay, and the next [CommanderCatalog.load] carries on from there).
 */
data class CatalogState(
    val cards: List<ScryfallCard> = emptyList(),
    val complete: Boolean = false,
    val error: String? = null,
    /** The next Scryfall page to ask for. */
    internal val nextPage: Int = 1
) {
    val loading: Boolean get() = !complete && error == null
}

/**
 * The new-deck picker's card lists — every commander of a format, every Background — each loaded
 * page by page, most played first, and kept for as long as the app runs, so the picker opens
 * instantly the second time. Requests are spaced on top of the app's own Scryfall pacing.
 */
object CommanderCatalog {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository by lazy { CardRepository() }
    private val states = HashMap<String, MutableStateFlow<CatalogState>>()
    private val jobs = HashMap<String, Job>()

    /** [query]'s cards as they arrive; starts (or resumes) the loading when it isn't done. */
    fun load(query: String): StateFlow<CatalogState> = synchronized(this) {
        val state = states.getOrPut(query) { MutableStateFlow(CatalogState()) }
        if (!state.value.complete && jobs[query]?.isActive != true) {
            if (state.value.error != null) state.value = state.value.copy(error = null)
            jobs[query] = scope.launch { fetch(query, state) }
        }
        state.asStateFlow()
    }

    private suspend fun fetch(query: String, state: MutableStateFlow<CatalogState>) {
        while (!state.value.complete) {
            val page = state.value.nextPage
            try {
                val result = repository.search(query, order = "edhrec", page = page)
                val seen = state.value.cards.mapTo(HashSet()) { it.id }
                state.value = state.value.copy(
                    cards = state.value.cards + result.cards.filter { it.id !in seen },
                    complete = !result.hasMore || result.cards.isEmpty(),
                    nextPage = page + 1
                )
                if (!state.value.complete) delay(SCRYFALL_SPACING_MILLIS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.value = state.value.copy(
                    error = if (isOffline(e)) "You're offline — the commander list needs an internet connection."
                    else "Couldn't load every card. Tap to try again."
                )
                return
            }
        }
    }
}
