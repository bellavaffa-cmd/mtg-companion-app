package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.NetworkModule
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallCollectionRequest
import com.mtgcompanion.app.network.scryfall.ScryfallCollectionResponse
import com.mtgcompanion.app.network.scryfall.ScryfallIdentifier
import com.mtgcompanion.app.network.scryfall.ScryfallRuling
import retrofit2.HttpException

/** One page of search results, plus whether Scryfall has more beyond it. */
data class SearchPage(val cards: List<ScryfallCard>, val hasMore: Boolean)

class CardRepository {
    private val api = NetworkModule.scryfallApi

    /** [page] is 1-indexed, matching Scryfall's own paging — 175 cards per page. */
    suspend fun search(query: String, order: String? = null, dir: String? = null, page: Int = 1): SearchPage {
        if (query.isBlank()) return SearchPage(emptyList(), hasMore = false)
        return try {
            val response = api.searchCards(query, page = page, order = order, dir = dir)
            SearchPage(response.data, response.hasMore)
        } catch (e: HttpException) {
            // Scryfall returns 404 when a (possibly partial, as-you-type) query matches
            // no cards — treat that as an empty result rather than an error.
            if (e.code() == 404) SearchPage(emptyList(), hasMore = false) else throw e
        }
    }

    /** Name suggestions for as-you-type search. Empty on a blank query or any failure. */
    suspend fun autocomplete(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        return try {
            api.autocomplete(query).data
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** A random card, for the Search tab's discovery button. */
    suspend fun getRandom(): ScryfallCard = api.getRandomCard()

    /** Every printing of a card (unique arts/sets), newest first, for alternate-art selection. */
    suspend fun getPrintings(cardName: String): List<ScryfallCard> {
        return try {
            api.searchCards(query = "!\"$cardName\"", unique = "prints", order = "released").data
        } catch (e: HttpException) {
            if (e.code() == 404) emptyList() else throw e
        }
    }

    suspend fun getByExactName(name: String): ScryfallCard = api.getCardByExactName(name)

    suspend fun getByFuzzyName(name: String): ScryfallCard = api.getCardByFuzzyName(name)

    /** A specific printing by set code + collector number (exact edition). */
    suspend fun getBySetAndNumber(set: String, number: String): ScryfallCard =
        api.getCardBySetNumber(set.lowercase(), number)

    /** Bulk lookup by Scryfall id; batches into the 75-per-request limit of /cards/collection. */
    suspend fun getCardsByIds(ids: List<String>): List<ScryfallCard> {
        if (ids.isEmpty()) return emptyList()
        return ids.distinct().chunked(75).flatMap { chunk ->
            try {
                api.getCollection(ScryfallCollectionRequest(chunk.map { ScryfallIdentifier(id = it) })).data
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * One /cards/collection request (max 75 identifiers). Use this instead of many /cards/named
     * calls for bulk work — Scryfall rate-limits (429) a rapid series of single-card requests.
     */
    suspend fun getCollection(identifiers: List<ScryfallIdentifier>): ScryfallCollectionResponse =
        api.getCollection(ScryfallCollectionRequest(identifiers))

    /** Resolve a card by (fuzzy) name and fetch its official rulings. */
    suspend fun getRulings(cardName: String): Pair<ScryfallCard, List<ScryfallRuling>> {
        val card = api.getCardByFuzzyName(cardName)
        val rulings = api.getRulings(card.id).data
        return card to rulings
    }

    /**
     * A rough "similar cards" list: same primary type, same colors, and (for non-lands) a mana
     * value within 1 of this card's — Scryfall has no native similarity search, so this is a
     * heuristic query built from the card's own attributes, sorted by EDHREC popularity so
     * well-known cards surface first. Excludes every printing of this card itself. Empty for
     * cards whose type doesn't map to one of [ScryfallCard.PRIMARY_TYPES] (rare, e.g. "Kindred").
     */
    suspend fun findSimilar(card: ScryfallCard, limit: Int = 12): List<ScryfallCard> {
        val query = buildSimilarQuery(card) ?: return emptyList()
        val excludeId = card.oracleId ?: card.id
        return search(query, order = "edhrec").cards
            .filter { (it.oracleId ?: it.id) != excludeId }
            .take(limit)
    }

    private fun buildSimilarQuery(card: ScryfallCard): String? {
        val type = card.primaryType
        if (type == "Other") return null
        val parts = mutableListOf("t:$type")
        if (type == "Land") {
            // Lands don't have a meaningful mana value to compare — match by what they produce.
            card.producedMana?.takeIf { it.isNotEmpty() }?.let { colors ->
                parts += "produces:" + colors.joinToString("") { it.lowercase() }
            }
        } else {
            val colorQuery = if (card.colors.isNullOrEmpty()) "c" else card.colors.joinToString("") { it.lowercase() }
            parts += "c:$colorQuery"
            card.cmc?.let { mv ->
                parts += "mv>=${(mv - 1).coerceAtLeast(0.0).toInt()}"
                parts += "mv<=${(mv + 1).toInt()}"
            }
        }
        return parts.joinToString(" ")
    }
}
