package com.mtgcompanion.app.network.mtgjson

/** GET DeckList.json — every precon/theme/sample deck MTGJSON has data for, across Magic's whole history. */
data class MtgJsonDeckIndex(val data: List<MtgJsonDeckSummary> = emptyList())

data class MtgJsonDeckSummary(
    val code: String,
    val fileName: String,
    val name: String,
    val releaseDate: String? = null,
    val type: String
)

/** GET decks/{fileName}.json — one deck's exact contents. */
data class MtgJsonDeckResponse(val data: MtgJsonDeckData)

data class MtgJsonDeckData(
    val commander: List<MtgJsonDeckCard> = emptyList(),
    val mainBoard: List<MtgJsonDeckCard> = emptyList()
)

data class MtgJsonDeckCard(
    val name: String,
    val count: Int = 1,
    val identifiers: MtgJsonCardIdentifiers? = null
)

data class MtgJsonCardIdentifiers(val scryfallId: String? = null)
