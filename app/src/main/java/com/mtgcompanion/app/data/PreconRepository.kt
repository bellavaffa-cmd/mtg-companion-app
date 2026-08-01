package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.NetworkModule
import com.mtgcompanion.app.network.mtgjson.MtgJsonDeckCard

/** One Commander precon, from MTGJSON's deck index. */
data class PreconInfo(
    val fileName: String,
    val name: String,
    val setCode: String,
    val releaseDate: String?
)

/** One card in a precon's exact contents. [scryfallId] is null if MTGJSON couldn't map it (rare). */
data class PreconCardEntry(val name: String, val scryfallId: String?, val quantity: Int)

data class PreconContents(val commander: List<PreconCardEntry>, val cards: List<PreconCardEntry>)

/**
 * Official Commander precon decklists, from MTGJSON's free public deck data — the same data
 * Wizards published, not a set's mixed card pool. The deck index (~600KB, every precon/theme deck
 * MTGJSON has ever indexed) is fetched once and cached in memory; each precon's full contents
 * (~600KB itself, since MTGJSON embeds full multi-language card data) is only fetched on demand,
 * when the user actually opens or imports it.
 */
class PreconRepository {
    private val api = NetworkModule.mtgJsonApi
    private var cachedPrecons: List<PreconInfo>? = null

    suspend fun listCommanderPrecons(): List<PreconInfo> {
        cachedPrecons?.let { return it }
        val list = api.getDeckList().data
            .filter { it.type == "Commander Deck" }
            .sortedByDescending { it.releaseDate.orEmpty() }
            .map { PreconInfo(it.fileName, it.name, it.code, it.releaseDate) }
        cachedPrecons = list
        return list
    }

    suspend fun getContents(fileName: String): PreconContents {
        val data = api.getDeck(fileName).data
        fun map(cards: List<MtgJsonDeckCard>) = cards.map { PreconCardEntry(it.name, it.identifiers?.scryfallId, it.count) }
        return PreconContents(map(data.commander), map(data.mainBoard))
    }
}
