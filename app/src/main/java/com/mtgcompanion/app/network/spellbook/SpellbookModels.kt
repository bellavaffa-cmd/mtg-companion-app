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
