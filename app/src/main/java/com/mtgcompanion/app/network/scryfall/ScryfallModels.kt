package com.mtgcompanion.app.network.scryfall

import com.squareup.moshi.Json

data class ScryfallSearchResponse(
    @Json(name = "total_cards") val totalCards: Int? = null,
    @Json(name = "has_more") val hasMore: Boolean = false,
    val data: List<ScryfallCard> = emptyList()
)

/** GET /cards/autocomplete response: a plain list of matching card names. */
data class ScryfallCatalog(val data: List<String> = emptyList())

/** POST /cards/collection body: look up many cards at once (max 75 identifiers per request). */
data class ScryfallCollectionRequest(val identifiers: List<ScryfallIdentifier>)

/**
 * One entry of a /cards/collection lookup. Exactly one addressing mode should be set: by Scryfall
 * [id], by exact [name], or by [set] + [collectorNumber] for a specific printing. Moshi omits the
 * null fields, so only the intended key is sent.
 */
data class ScryfallIdentifier(
    val id: String? = null,
    val name: String? = null,
    val set: String? = null,
    @Json(name = "collector_number") val collectorNumber: String? = null
)

/** POST /cards/collection response: [data] holds the matches, [notFound] the identifiers that missed. */
data class ScryfallCollectionResponse(
    val data: List<ScryfallCard> = emptyList(),
    @Json(name = "not_found") val notFound: List<ScryfallIdentifier> = emptyList()
)

data class ScryfallCard(
    val id: String,
    @Json(name = "oracle_id") val oracleId: String? = null,
    val name: String,
    @Json(name = "mana_cost") val manaCost: String? = null,
    val cmc: Double? = null,
    @Json(name = "type_line") val typeLine: String? = null,
    @Json(name = "oracle_text") val oracleText: String? = null,
    /** e.g. "normal", "transform", "modal_dfc", "split", "adventure", "flip", "meld". Determines
     * whether [cardFaces] represent a flippable back side ([hasFlipSides]) or are just two rules
     * text blocks printed on one face (split/adventure — already merged by [displayOracleText]). */
    val layout: String? = null,
    /** Printed keyword abilities, e.g. ["Flying", "Lifelink"] — feeds [tags] alongside a few
     * heuristic theme tags (Lifegain, Removal, ...) inferred from oracle text. */
    val keywords: List<String>? = null,
    val colors: List<String>? = null,
    @Json(name = "color_identity") val colorIdentity: List<String>? = null,
    @Json(name = "produced_mana") val producedMana: List<String>? = null,
    @Json(name = "image_uris") val imageUris: ScryfallImageUris? = null,
    @Json(name = "card_faces") val cardFaces: List<ScryfallCardFace>? = null,
    val prices: ScryfallPrices? = null,
    @Json(name = "purchase_uris") val purchaseUris: ScryfallPurchaseUris? = null,
    @Json(name = "tcgplayer_id") val tcgplayerId: Long? = null,
    @Json(name = "scryfall_uri") val scryfallUri: String? = null,
    @Json(name = "game_changer") val gameChanger: Boolean? = null,
    val set: String? = null,
    @Json(name = "set_name") val setName: String? = null,
    @Json(name = "collector_number") val collectorNumber: String? = null,
    val rarity: String? = null,
    val power: String? = null,
    val toughness: String? = null,
    /** Format -> legality ("legal", "not_legal", "banned", "restricted"). */
    val legalities: Map<String, String>? = null
) {
    val displayImageUrl: String?
        get() = imageUris?.normal ?: cardFaces?.firstOrNull()?.imageUris?.normal

    /** The second face's image, when this card actually flips ([hasFlipSides]) — null for
     * split/adventure cards, whose "second face" is just more rules text on the same printed image. */
    val backImageUrl: String?
        get() = if (hasFlipSides) cardFaces?.getOrNull(1)?.imageUris?.normal else null

    /** True for cards with a real second side to flip to in the UI: transform, modal DFC, flip,
     * and reversible cards. False for split/adventure (two spells/rules blocks on one printed face). */
    val hasFlipSides: Boolean
        get() = cardFaces?.size == 2 && layout in FLIPPABLE_LAYOUTS

    /** A short label for this specific printing, e.g. "Modern Horizons 3 · 123". */
    val printingLabel: String
        get() = listOfNotNull(setName ?: set?.uppercase(), collectorNumber?.let { "#$it" }).joinToString(" · ")

    /** e.g. "Creature" from "Legendary Creature — Human Wizard" — used for type-grouping and the
     * "similar cards" search. Falls back to "Other" for double-faced cards with mismatched face
     * types or anything unrecognized. */
    val primaryType: String
        get() = PRIMARY_TYPES.firstOrNull { typeLine?.contains(it, ignoreCase = true) == true } ?: "Other"

    /** Printed keywords plus a handful of heuristic theme tags (Lifegain, Card Draw, Removal, ...)
     * inferred from oracle text — shown as chips wherever the full card (not just a cached
     * deck/binder entry) is on hand. Heuristic tags are pattern-matched, not exhaustive. */
    val tags: List<String>
        get() {
            val result = LinkedHashSet<String>()
            keywords?.let(result::addAll)
            val text = displayOracleText.orEmpty()
            THEME_TAG_RULES.forEach { rule -> if (rule.pattern.containsMatchIn(text)) result += rule.label }
            return result.toList()
        }

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

    /**
     * Null if this card has no partner ability. `"Partner"` for a plain partner card (pairs with
     * any other plain-partner commander); otherwise the exact name from "Partner with <Name>"
     * (pairs only with that specific card).
     */
    val partnerAbility: String?
        get() {
            val line = displayOracleText?.lineSequence()?.map { it.trim() }
                ?.firstOrNull { it.startsWith("Partner", ignoreCase = true) } ?: return null
            val withPrefix = "Partner with "
            return if (line.startsWith(withPrefix, ignoreCase = true)) {
                line.removePrefix(withPrefix).substringBefore(" (").trim()
            } else {
                "Partner"
            }
        }

    companion object {
        private val FLIPPABLE_LAYOUTS = setOf("transform", "modal_dfc", "flip", "reversible_card", "double_faced_token")

        /** Checked in order, so e.g. "Artifact Creature" resolves to "Creature" (matches the deck
         * Cards-tab type grouping's own priority — creatures group together even when they're also
         * artifacts/enchantments). */
        val PRIMARY_TYPES = listOf(
            "Creature", "Planeswalker", "Instant", "Sorcery", "Artifact", "Enchantment", "Battle", "Land"
        )

        /** Heuristic oracle-text patterns for common deckbuilding theme tags that aren't printed
         * keywords. Approximate by nature — false negatives (a differently-worded effect) are far
         * more likely than false positives, so this stays a short, conservative list. */
        private data class ThemeTagRule(val label: String, val pattern: Regex)
        private val THEME_TAG_RULES = listOf(
            ThemeTagRule("Lifegain", Regex("gain(s)?\\s+\\S*\\s*life|whenever you gain life", RegexOption.IGNORE_CASE)),
            ThemeTagRule("Card Draw", Regex("draws? (a|two|three|\\d+|that many) cards?", RegexOption.IGNORE_CASE)),
            ThemeTagRule("Removal", Regex("destroy target|exile target|deals? \\d+ damage to target", RegexOption.IGNORE_CASE)),
            ThemeTagRule("Ramp", Regex("search your library for a( basic)? land card|add \\{[wubrgc]\\}", RegexOption.IGNORE_CASE)),
            ThemeTagRule("Tokens", Regex("creates? [^.]*token", RegexOption.IGNORE_CASE)),
            ThemeTagRule("Counterspell", Regex("counter target spell", RegexOption.IGNORE_CASE))
        )
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
    val large: String? = null,
    @Json(name = "art_crop") val artCrop: String? = null
)

/**
 * Scryfall serves the cropped art at the same CDN path as the full image, differing only in the
 * size folder. So a stored "normal" URL can be turned into its art crop without re-fetching.
 */
fun String?.toArtCropUrl(): String? = this
    ?.replace("/normal/", "/art_crop/")
    ?.replace("/large/", "/art_crop/")
    ?.replace("/small/", "/art_crop/")

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

/** GET /cards/{id}/rulings — official rulings for a card. */
data class ScryfallRulingsResponse(val data: List<ScryfallRuling> = emptyList())

data class ScryfallRuling(
    val source: String? = null,
    @Json(name = "published_at") val publishedAt: String? = null,
    val comment: String
)

/** GET /bulk-data — the catalog of downloadable bulk card files. */
data class BulkDataList(val data: List<BulkDataEntry> = emptyList())

/**
 * Scryfall retired the plain-JSON-array `download_uri` in favor of `jsonl_download_uri` — a
 * gzip-compressed, newline-delimited JSON file (one card object per line) — so [downloadUri] maps
 * to that field now (see [OfflineCardRepository.downloadAndStore][com.mtgcompanion.app.data.offline.OfflineCardRepository]
 * for the matching gzip+JSONL read). [type] and [downloadUri] stay nullable rather than required:
 * Moshi's default codegen throws for the WHOLE list the moment any single entry lacks a required
 * field, which would take the entire offline-database download down even if only some unrelated
 * entry (not "oracle_cards") were ever missing one.
 */
data class BulkDataEntry(
    val type: String? = null,
    @Json(name = "jsonl_download_uri") val downloadUri: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    val size: Long? = null
)
