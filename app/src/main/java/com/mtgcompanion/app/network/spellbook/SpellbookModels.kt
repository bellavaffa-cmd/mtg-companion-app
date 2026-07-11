package com.mtgcompanion.app.network.spellbook

import com.squareup.moshi.Json

data class VariantsResponse(
    val count: Int? = null,
    val results: List<Variant> = emptyList()
)

data class Variant(
    val id: String,
    val uses: List<ComboCardUsage> = emptyList(),
    val produces: List<ComboFeatureUsage> = emptyList(),
    val description: String? = null,
    val popularity: Int? = null
)

data class ComboCardUsage(
    val card: ComboCard,
    val quantity: Int? = null
)

data class ComboCard(
    val id: Int,
    val name: String
)

data class ComboFeatureUsage(
    val feature: ComboFeature,
    val quantity: Int? = null
)

data class ComboFeature(
    val id: Int,
    val name: String
)

// --- find-my-combos (which combos a decklist contains) ---

data class FindMyCombosRequest(
    val commanders: List<DeckCardRef>,
    val main: List<DeckCardRef>
)

data class DeckCardRef(val card: String, val quantity: Int = 1)

data class FindMyCombosResponse(val results: FindMyCombosResults? = null)

data class FindMyCombosResults(
    /** Combos fully present in the deck. */
    val included: List<Variant> = emptyList()
)
