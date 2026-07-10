package com.mtgcompanion.app.network.scryfall

import com.squareup.moshi.Json

data class ScryfallSearchResponse(
    @Json(name = "total_cards") val totalCards: Int? = null,
    @Json(name = "has_more") val hasMore: Boolean = false,
    val data: List<ScryfallCard> = emptyList()
)

data class ScryfallCard(
    val id: String,
    @Json(name = "oracle_id") val oracleId: String? = null,
    val name: String,
    @Json(name = "mana_cost") val manaCost: String? = null,
    val cmc: Double? = null,
    @Json(name = "type_line") val typeLine: String? = null,
    @Json(name = "oracle_text") val oracleText: String? = null,
    val colors: List<String>? = null,
    @Json(name = "color_identity") val colorIdentity: List<String>? = null,
    @Json(name = "image_uris") val imageUris: ScryfallImageUris? = null,
    @Json(name = "card_faces") val cardFaces: List<ScryfallCardFace>? = null,
    val prices: ScryfallPrices? = null,
    @Json(name = "purchase_uris") val purchaseUris: ScryfallPurchaseUris? = null,
    @Json(name = "tcgplayer_id") val tcgplayerId: Long? = null,
    @Json(name = "scryfall_uri") val scryfallUri: String? = null
) {
    val displayImageUrl: String?
        get() = imageUris?.normal ?: cardFaces?.firstOrNull()?.imageUris?.normal

    val displayOracleText: String?
        get() = oracleText ?: cardFaces?.joinToString("\n\n") { face ->
            listOfNotNull(face.typeLine, face.oracleText).joinToString("\n")
        }

    val canBeCommander: Boolean
        get() {
            val isLegendaryCreature = typeLine?.contains("Legendary") == true && typeLine.contains("Creature")
            val explicitlyAllowed = oracleText?.contains("can be your commander", ignoreCase = true) == true
            return isLegendaryCreature || explicitlyAllowed
        }
}

data class ScryfallCardFace(
    val name: String? = null,
    @Json(name = "image_uris") val imageUris: ScryfallImageUris? = null,
    @Json(name = "oracle_text") val oracleText: String? = null,
    @Json(name = "type_line") val typeLine: String? = null,
    @Json(name = "mana_cost") val manaCost: String? = null
)

data class ScryfallImageUris(
    val small: String? = null,
    val normal: String? = null,
    val large: String? = null
)

data class ScryfallPrices(
    val usd: String? = null,
    @Json(name = "usd_foil") val usdFoil: String? = null,
    val eur: String? = null,
    val tix: String? = null
)

data class ScryfallPurchaseUris(
    val tcgplayer: String? = null,
    val cardmarket: String? = null,
    val cardhoarder: String? = null
)
