package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallCollectionResponse
import com.mtgcompanion.app.network.scryfall.ScryfallIdentifier
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException

/** One card an import found, with its copies added together. */
data class ImportedCard(val card: ScryfallCard, val quantity: Int, val foilQuantity: Int) {
    fun toEntry() = CollectionEntry(card.id, card.name, card.displayImageUrl, quantity, foilQuantity, card.backImageUrl, card.tags)
}

data class ImportResult(val cards: List<ImportedCard>, val missing: List<String>) {
    val added: Int get() = cards.sumOf { it.quantity + it.foilQuantity }
    val foils: Int get() = cards.sumOf { it.foilQuantity }
}

/**
 * Turns an imported card list into real cards: 75 lines at a time through Scryfall's collection
 * lookup (by id, exact printing, or name), then one fuzzy name search each for the few it missed.
 * Mirrors the web app's collection/importCards.ts.
 */
class CardListImporter(private val cards: CardRepository = CardRepository()) {

    suspend fun resolve(lines: List<ListLine>, onProgress: (done: Int, total: Int) -> Unit): ImportResult {
        val found = HashMap<Int, ScryfallCard>() // line index -> card
        var done = 0
        onProgress(0, lines.size)
        for ((chunkIndex, chunk) in lines.chunked(75).withIndex()) {
            val response = collection(chunk.map(::identifier)) ?: continue
            chunk.forEachIndexed { i, line ->
                response.data.firstOrNull { matches(it, line) }?.let {
                    found[chunkIndex * 75 + i] = it
                    done++
                }
            }
            onProgress(done, lines.size)
        }
        // What the batches missed (a mistyped name, a printing Scryfall doesn't know) gets a fuzzy
        // search by name, spaced out as Scryfall asks.
        val missing = mutableListOf<String>()
        lines.forEachIndexed { i, line ->
            if (i in found) return@forEachIndexed
            val name = line.name
            val card = if (name != null) fuzzy(name) else null
            if (card != null) found[i] = card else missing += name ?: line.scryfallId.orEmpty()
            onProgress(++done, lines.size)
            delay(100)
        }
        val byCard = LinkedHashMap<String, ImportedCard>()
        found.toSortedMap().forEach { (i, card) ->
            val line = lines[i]
            val item = byCard[card.id] ?: ImportedCard(card, 0, 0)
            byCard[card.id] = if (line.foil) item.copy(foilQuantity = item.foilQuantity + line.quantity) else item.copy(quantity = item.quantity + line.quantity)
        }
        return ImportResult(byCard.values.toList(), missing)
    }

    private fun identifier(line: ListLine): ScryfallIdentifier = when {
        line.scryfallId != null -> ScryfallIdentifier(id = line.scryfallId)
        line.set != null && line.number != null -> ScryfallIdentifier(set = line.set, collectorNumber = line.number)
        line.set != null -> ScryfallIdentifier(name = line.name, set = line.set)
        else -> ScryfallIdentifier(name = line.name.orEmpty())
    }

    /** Whether [card] is the one [name] means; a double-faced card also answers to its front face. */
    private fun sameName(card: ScryfallCard, name: String?): Boolean {
        val n = name?.lowercase() ?: return false
        val c = card.name.lowercase()
        return c == n || c.startsWith("$n //") || card.cardFaces?.firstOrNull()?.name?.lowercase() == n
    }

    private fun matches(card: ScryfallCard, line: ListLine): Boolean = when {
        line.scryfallId != null -> card.id == line.scryfallId
        // A printing only counts if it's the card the line names: a mistyped number mustn't bring
        // in another card. Missed, the line is looked up by name instead.
        line.set != null && line.number != null ->
            card.set.equals(line.set, true) && card.collectorNumber.equals(line.number, true) && (line.name == null || sameName(card, line.name))
        line.set != null -> sameName(card, line.name) && card.set.equals(line.set, true)
        else -> sameName(card, line.name)
    }

    /** One batch, waiting out a rate limit rather than dropping 75 lines to fuzzy searches. */
    private suspend fun collection(identifiers: List<ScryfallIdentifier>): ScryfallCollectionResponse? {
        repeat(4) { attempt ->
            try {
                return cards.getCollection(identifiers)
            } catch (e: HttpException) {
                if (e.code() == 429) delay(700L * (attempt + 1)) else return null
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                delay(300)
            }
        }
        return null
    }

    private suspend fun fuzzy(name: String): ScryfallCard? {
        repeat(3) { attempt ->
            try {
                return cards.getByFuzzyName(name)
            } catch (e: HttpException) {
                if (e.code() == 429) delay(600L * (attempt + 1)) else return null
            } catch (e: IOException) {
                throw e
            }
        }
        return null
    }
}
