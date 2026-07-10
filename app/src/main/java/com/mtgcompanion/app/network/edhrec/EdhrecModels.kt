package com.mtgcompanion.app.network.edhrec

import com.squareup.moshi.Json

data class EdhrecPage(
    val container: EdhrecContainer? = null
)

data class EdhrecContainer(
    val description: String? = null,
    @Json(name = "json_dict") val jsonDict: EdhrecJsonDict? = null
)

data class EdhrecJsonDict(
    val cardlists: List<EdhrecCardList> = emptyList()
)

data class EdhrecCardList(
    val header: String? = null,
    val tag: String? = null,
    val cardviews: List<EdhrecCardView> = emptyList()
)

data class EdhrecCardView(
    val id: String? = null,
    val name: String,
    val sanitized: String? = null,
    val url: String? = null,
    val synergy: Double? = null,
    val lift: Double? = null,
    val inclusion: Int? = null,
    @Json(name = "num_decks") val numDecks: Int? = null,
    @Json(name = "potential_decks") val potentialDecks: Int? = null
)

/** Scryfall's image CDN keys off the card id's first two hex chars — no API call needed. */
val EdhrecCardView.scryfallImageUrl: String?
    get() = id?.takeIf { it.length > 2 }?.let { "https://cards.scryfall.io/normal/front/${it[0]}/${it[1]}/$it.jpg" }

/** EDHREC's headline tile stat: what fraction of eligible decks run the card. */
val EdhrecCardView.inclusionPercent: Int?
    get() {
        val potential = potentialDecks ?: return null
        val decks = numDecks ?: return null
        return if (potential > 0) (decks * 100.0 / potential).toInt() else null
    }
