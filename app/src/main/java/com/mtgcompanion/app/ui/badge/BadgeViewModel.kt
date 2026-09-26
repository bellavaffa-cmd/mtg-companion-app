package com.mtgcompanion.app.ui.badge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.tokensNeeded
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One token, with everything the badge needs printed on it. */
data class BadgeToken(
    val id: String,
    val name: String,
    val typeLine: String?,
    /** "1/1" for a creature token, null for an emblem or a Treasure. */
    val powerToughness: String?,
    /** What the token actually does. A Pest that doesn't say it drains you is not a Pest. */
    val oracleText: String?,
    val artUrl: String?,
    val emblem: Boolean,
    /** The cards asking for it, so the list reads the same as the deck's own Tokens panel. */
    val madeBy: List<String>
)

/**
 * The deck the badge screen is working from.
 *
 * Thin on purpose: the tokens themselves are built by [badgeTokensFor], because all three places
 * that write a badge need them and a screen's view model is the wrong owner for something two
 * sheets also use.
 */
class BadgeViewModel(
    deckId: String,
    repository: DeckRepository
) : ViewModel() {

    /** The sheet builds its own token list from this. */
    val deck: StateFlow<Deck?> = repository.deckFlow(deckId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val deckName: StateFlow<String> = deck
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    class Factory(
        private val deckId: String,
        private val repository: DeckRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BadgeViewModel(deckId, repository) as T
    }
}

/**
 * The tokens in [deck], each with what a badge needs printed on it.
 *
 * Shared by the badge screen and the in-game sheet on the remote, which both need this and neither
 * of which should be the one that owns it.
 *
 * Returns null while it can't say yet — no deck, or Scryfall didn't answer — and an empty list when
 * the deck genuinely makes no tokens. The screens say different things for those two.
 */
suspend fun badgeTokensFor(deck: Deck?, cardRepository: CardRepository): List<BadgeToken>? {
    if (deck == null) return null
    if (deck.cards.isEmpty()) return emptyList()
    val byId = runCatching {
        cardRepository.getCardsByIds(
            (deck.cards + listOfNotNull(deck.commander, deck.partnerCommander)).map { it.scryfallId }
        ).associateBy { it.id }
    }.getOrElse { return null }

    val needed = tokensNeeded(deck, byId)
    if (needed.isEmpty()) return emptyList()

    // A token whose card doesn't come back still makes a badge — name and type line are enough.
    val cards = runCatching { cardRepository.getCardsByIds(needed.map { it.id }).associateBy { it.id } }
        .getOrElse { emptyMap() }

    return needed.map { token ->
        val card = cards[token.id]
        val power = card?.power
        val toughness = card?.toughness
        BadgeToken(
            id = token.id,
            name = token.name,
            typeLine = token.typeLine ?: card?.typeLine,
            powerToughness = if (power != null && toughness != null) "$power/$toughness" else null,
            oracleText = card?.displayOracleText?.trim()?.takeIf { it.isNotEmpty() },
            artUrl = card?.displayImageUrl.toArtCropUrl(),
            emblem = token.isEmblem,
            madeBy = token.madeBy
        )
    }
}
