package com.mtgcompanion.app.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.ComboRepository
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.EdhrecRepository
import com.mtgcompanion.app.data.GameMode
import com.mtgcompanion.app.data.LegalityReport
import com.mtgcompanion.app.data.evaluateLegality
import com.mtgcompanion.app.ui.common.MoveTarget
import com.mtgcompanion.app.ui.common.SourceKind
import com.mtgcompanion.app.network.edhrec.EdhrecCardView
import com.mtgcompanion.app.ui.collection.fetchPrices
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.spellbook.Variant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A deck card paired with its type category, for the type-grouped Cards tab. */
data class TypeGroup(val type: String, val cards: List<DeckCardEntry>)

/** Everything derived from the deck's card data for the Stats and Analysis tabs. */
data class DeckAnalysis(
    val loading: Boolean = true,
    val byType: List<TypeGroup> = emptyList(),
    val manaCurve: List<Pair<String, Int>> = emptyList(),
    val avgManaValue: Double = 0.0,
    val colorCounts: List<Pair<String, Int>> = emptyList(),
    val typeCounts: List<Pair<String, Int>> = emptyList(),
    val totalUsd: Double = 0.0,
    val bracket: Int = 0,
    val bracketName: String = "",
    val bracketReason: String = "",
    val gameChangers: List<String> = emptyList(),
    val combos: List<Variant> = emptyList(),
    val legality: LegalityReport? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class DeckDetailViewModel(
    private val deckId: String,
    private val repository: DeckRepository,
    private val collectionRepository: CollectionRepository,
    private val cardRepository: CardRepository = CardRepository(),
    private val comboRepository: ComboRepository = ComboRepository(),
    private val edhrecRepository: EdhrecRepository = EdhrecRepository()
) : ViewModel() {

    val deck: StateFlow<Deck?> = repository.deckFlow(deckId).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    /** Other decks and all binders this deck's cards can be moved into. */
    val moveTargets: StateFlow<List<MoveTarget>> =
        combine(repository.decksFlow, collectionRepository.collectionsFlow) { decks, collections ->
            decks.filter { it.id != deckId }.map { MoveTarget(SourceKind.DECK, it.id, it.name) } +
                collections.map { MoveTarget(SourceKind.BINDER, it.id, it.name) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val analysis: StateFlow<DeckAnalysis> = deck.mapLatest { d ->
        if (d == null) return@mapLatest DeckAnalysis(loading = false)
        if (d.cards.isEmpty()) return@mapLatest DeckAnalysis(loading = false, legality = evaluateLegality(d, emptyMap()))
        buildAnalysis(d)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeckAnalysis(loading = true))

    /** scryfallId -> USD price for the deck's cards, for the enlarged-card value/total display. */
    val prices: StateFlow<Map<String, Double>> = deck.mapLatest { d ->
        fetchPrices(cardRepository, d?.cards.orEmpty().map { it.scryfallId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** EDHREC "top cards" suggestions for this deck's commander (null if no commander/data). */
    val suggestions: StateFlow<List<EdhrecCardView>?> = deck.mapLatest { d ->
        val commander = d?.commander?.name ?: return@mapLatest null
        val lists = try {
            edhrecRepository.getRecommendationsForCommander(commander)
        } catch (e: Exception) {
            null
        } ?: return@mapLatest null
        (lists.firstOrNull { it.tag == "topcards" } ?: lists.firstOrNull { it.cardviews.isNotEmpty() })
            ?.cardviews?.take(12)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private suspend fun buildAnalysis(d: Deck): DeckAnalysis {
        val byId = cardRepository.getCardsByIds(d.cards.map { it.scryfallId }).associateBy { it.id }

        // Cards grouped by type (Creatures, Instants, …, Lands last).
        val groups = d.cards
            .groupBy { primaryType(byId[it.scryfallId]?.typeLine) }
            .toList()
            .sortedBy { typeOrder(it.first) }
            .map { (type, cards) -> TypeGroup(type, cards.sortedBy { it.name.lowercase() }) }

        // Mana curve (excludes lands), colours, types, value, game changers.
        val curveBuckets = linkedMapOf("0" to 0, "1" to 0, "2" to 0, "3" to 0, "4" to 0, "5" to 0, "6" to 0, "7+" to 0)
        val colorTotals = linkedMapOf("W" to 0, "U" to 0, "B" to 0, "R" to 0, "G" to 0, "Colorless" to 0)
        val typeTotals = LinkedHashMap<String, Int>()
        var totalUsd = 0.0
        var cmcSum = 0.0
        var nonLandCount = 0
        val gameChangers = mutableListOf<String>()

        d.cards.forEach { entry ->
            val card = byId[entry.scryfallId]
            val qty = entry.quantity
            val type = primaryType(card?.typeLine)
            typeTotals[type] = (typeTotals[type] ?: 0) + qty

            card?.prices?.usd?.toDoubleOrNull()?.let { totalUsd += it * qty }
            if (card?.gameChanger == true) gameChangers += entry.name

            val identity = card?.colorIdentity ?: card?.colors
            if (identity.isNullOrEmpty()) colorTotals["Colorless"] = (colorTotals["Colorless"] ?: 0) + qty
            else identity.forEach { c -> colorTotals[c]?.let { colorTotals[c] = it + qty } }

            if (type != "Land") {
                val cmc = (card?.cmc ?: 0.0)
                cmcSum += cmc * qty
                nonLandCount += qty
                val bucket = if (cmc >= 7) "7+" else cmc.toInt().toString()
                curveBuckets[bucket] = (curveBuckets[bucket] ?: 0) + qty
            }
        }

        val commanderNames = listOfNotNull(d.commander?.name)
        val nonCommanderNames = d.cards.filter { it.scryfallId != d.commander?.scryfallId }.map { it.name }
        val combos = comboRepository.findCombosInDeck(commanderNames, nonCommanderNames)

        val (bracket, bracketName, reason) = estimateBracket(gameChangers.size, combos.size)

        return DeckAnalysis(
            loading = false,
            byType = groups,
            manaCurve = curveBuckets.toList(),
            avgManaValue = if (nonLandCount > 0) cmcSum / nonLandCount else 0.0,
            colorCounts = colorTotals.entries.filter { it.value > 0 }.map { it.key to it.value },
            typeCounts = typeTotals.entries.sortedByDescending { it.value }.map { it.key to it.value },
            totalUsd = totalUsd,
            bracket = bracket,
            bracketName = bracketName,
            bracketReason = reason,
            gameChangers = gameChangers.distinct(),
            combos = combos,
            legality = evaluateLegality(d, byId)
        )
    }

    fun removeCard(scryfallId: String) {
        viewModelScope.launch { repository.removeCardFromDeck(deckId, scryfallId) }
    }

    fun setCommander(card: DeckCardEntry?) {
        viewModelScope.launch { repository.setCommander(deckId, card) }
    }

    /** Change a card's copy count in the deck (used by the enlarged-card quantity stepper). */
    fun setCardQuantity(scryfallId: String, quantity: Int) {
        viewModelScope.launch { repository.setCardQuantity(deckId, scryfallId, quantity) }
    }

    /** Swap a card to a different printing/art, keeping its quantity. */
    fun changePrinting(oldScryfallId: String, newCard: ScryfallCard) {
        viewModelScope.launch { repository.changeCardPrinting(deckId, oldScryfallId, newCard) }
    }

    fun setGameMode(mode: GameMode) {
        viewModelScope.launch { repository.setGameMode(deckId, mode) }
    }

    /** Move a card (all its copies) out of this deck into [target] deck or binder. */
    fun moveCard(entry: DeckCardEntry, target: MoveTarget) {
        viewModelScope.launch {
            when (target.kind) {
                SourceKind.DECK -> repository.addEntry(target.id, entry)
                SourceKind.BINDER -> collectionRepository.addEntry(
                    target.id,
                    CollectionEntry(entry.scryfallId, entry.name, entry.imageUrl, quantity = entry.quantity, foilQuantity = 0)
                )
            }
            repository.removeCardFromDeck(deckId, entry.scryfallId)
        }
    }

    fun deleteDeck(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteDeck(deckId)
            onDeleted()
        }
    }

    /**
     * Parse a pasted decklist ("1 Sol Ring" / "2x Brainstorm" / "Sol Ring") and add each card via
     * Scryfall fuzzy lookup. Reports how many copies were added and which lines couldn't be matched.
     */
    fun importDecklist(text: String, onResult: (added: Int, failed: List<String>) -> Unit) {
        viewModelScope.launch {
            var added = 0
            val failed = mutableListOf<String>()
            val lineRegex = Regex("^(\\d+)\\s*[xX]?\\s+(.+)$")
            text.lines().forEach { raw ->
                val line = raw.trim().removePrefix("#").trim()
                if (line.isEmpty()) return@forEach
                val match = lineRegex.find(line)
                val qty = (match?.groupValues?.get(1)?.toIntOrNull() ?: 1).coerceIn(1, 99)
                val name = match?.groupValues?.get(2)?.trim() ?: line
                try {
                    val card = cardRepository.getByFuzzyName(name)
                    repeat(qty) { repository.addCardToDeck(deckId, card) }
                    added += qty
                } catch (e: Exception) {
                    failed += name
                }
            }
            onResult(added, failed)
        }
    }

    class Factory(
        private val deckId: String,
        private val repository: DeckRepository,
        private val collectionRepository: CollectionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DeckDetailViewModel(deckId, repository, collectionRepository) as T
    }
}

private val typeSortOrder = listOf(
    "Creature", "Planeswalker", "Instant", "Sorcery", "Artifact", "Enchantment", "Battle", "Land", "Other"
)

private fun typeOrder(type: String): Int = typeSortOrder.indexOf(type).let { if (it == -1) typeSortOrder.size else it }

private fun primaryType(typeLine: String?): String {
    val line = typeLine ?: return "Other"
    return typeSortOrder.firstOrNull { it != "Other" && line.contains(it, ignoreCase = true) } ?: "Other"
}

/**
 * Rough Commander-bracket estimate from the count of Game Changers and detected combos.
 * Mirrors WotC's guidance loosely: no game changers/combos = Core; up to a few = Upgraded; more = Optimized.
 * Brackets 1 (Exhibition) and 5 (cEDH) aren't auto-detected.
 */
private fun estimateBracket(gameChangers: Int, combos: Int): Triple<Int, String, String> = when {
    gameChangers == 0 && combos == 0 ->
        Triple(2, "Core", "No Game Changers or two-card combos detected — a typical Core-power deck.")
    gameChangers <= 3 ->
        Triple(
            3, "Upgraded",
            buildString {
                append("$gameChangers Game Changer" + (if (gameChangers == 1) "" else "s"))
                if (combos > 0) append(" and $combos combo" + (if (combos == 1) "" else "s"))
                append(" — within the Upgraded ceiling of 3 Game Changers.")
            }
        )
    else ->
        Triple(
            4, "Optimized",
            "$gameChangers Game Changers exceed the 3 allowed at Upgraded" +
                (if (combos > 0) " and $combos combo(s) are present" else "") +
                ", pushing this to Optimized."
        )
}
