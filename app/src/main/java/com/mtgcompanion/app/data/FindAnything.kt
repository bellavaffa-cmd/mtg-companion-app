package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.social.forTradeOf
import java.text.NumberFormat
import java.text.Normalizer
import java.util.Locale

/*
 * Find anything: one search over the user's own cards — each with its copies and every place they
 * are ("Red box › Colourless ×1", "Atraxa deck ×1", "Lent to Sam ×1", "Graded PSA 9 ×1", for trade,
 * to sell) — their places ("Shelf, study · 3 places inside") and their decks (the ones using a card
 * found, "Commander · proxy"), falling through to Search for cards they don't have.
 *
 * Fast on big collections: the library is indexed once (buildFindIndex — one walk over the binders,
 * decks, loans and graded copies), and typing on (a query that starts with the last one) only looks
 * again at what the last one found (Finder). Pure, so it can be tested. Mirrors the web app's
 * src/collection/findAnything.ts (tests: FindAnythingTest.kt ↔ tests/collection/findAnything.test.ts).
 */

/** Where some of a card's copies are, as a chip says it: "Red box › Colourless" ×1. */
enum class FindChipKind { PLACE, DECK, LENT, GRADED, NONE, TRADE, SELL }

data class FindChip(val kind: FindChipKind, val label: String, val qty: Int, val placeId: String? = null, val deckId: String? = null)

/** One of the user's cards (any printing), its copies and where they all are. */
data class FoundCard(
    val name: String,
    val imageUrl: String?,
    /** Copies the user has: in places, binders, decks (real ones), lent out and graded. */
    val copies: Int,
    val chips: List<FindChip>
)

data class FoundPlace(val id: String, val name: String, val line: String)
data class FoundDeck(val id: String, val name: String, val line: String)

private val MARKS = Regex("\\p{M}+")
private val NOT_WORD = Regex("[^a-z0-9]+")

/** Lower case, accents off, anything but letters and digits a space: "Æther Vial" → "aether vial". */
fun normalizeFind(text: String): String {
    val lower = text.lowercase(Locale.ROOT).replace("æ", "ae")
    return Normalizer.normalize(lower, Normalizer.Form.NFD)
        .replace(MARKS, "")
        .replace("'", "").replace("’", "")
        .replace(NOT_WORD, " ")
        .trim()
}

/** A name ready for matching: its plain [text] and its words. */
class FindName(name: String) {
    val text: String = normalizeFind(name)
    val words: List<String> = text.split(' ').filter { it.isNotEmpty() }
}

/**
 * How well [name] matches the query [q] (normalized): 0 not at all; the whole name 4; the name
 * starting with it 3; every word of the query starting a word of the name ("sol ri" → Sol Ring) 2;
 * the query inside the name 1. Anything matching a query matches every query it started as.
 */
fun matchScore(name: FindName, q: String): Int {
    if (q.isEmpty()) return 0
    if (name.text == q) return 4
    if (name.text.startsWith(q)) return 3
    val tokens = q.split(' ')
    if (tokens.all { t -> name.words.any { it.startsWith(t) } }) return 2
    return if (name.text.contains(q)) 1 else 0
}

class FindCardRow(val name: FindName, val card: FoundCard, val key: String)
class FindPlaceRow(val name: FindName, val place: FoundPlace)
class FindDeckRow(val name: FindName, val deck: Deck, val format: String, val cards: Map<String, Boolean>)

/** The library, indexed once for finding. */
class FindIndex(val cards: List<FindCardRow>, val places: List<FindPlaceRow>, val decks: List<FindDeckRow>)

private fun cardKey(name: String) = name.trim().lowercase()
private fun count(n: Int): String = NumberFormat.getIntegerInstance(Locale.UK).format(n)
private val CHIP_ORDER = listOf(FindChipKind.PLACE, FindChipKind.DECK, FindChipKind.LENT, FindChipKind.GRADED, FindChipKind.NONE, FindChipKind.TRADE, FindChipKind.SELL)

private class CardBuild(var name: String, var imageUrl: String?) {
    var copies = 0
    val chips = LinkedHashMap<String, FindChip>()
}

/** Every card, place and deck of the user's, ready to be found. */
fun buildFindIndex(collections: List<Collection>, decks: List<Deck>): FindIndex {
    val places = placesOf(collections)
    val byId = places.associateBy { it.id }
    val lent = lentCopies(collections, decks)
    val byEntry = lentByEntry(lent)
    val cards = LinkedHashMap<String, CardBuild>()
    fun cardOf(name: String, imageUrl: String?): CardBuild {
        val row = cards.getOrPut(cardKey(name)) { CardBuild(name.trim(), imageUrl) }
        if (row.imageUrl == null && imageUrl != null) row.imageUrl = imageUrl
        return row
    }
    fun chip(name: String, imageUrl: String?, c: FindChip) {
        if (c.qty <= 0) return
        val row = cardOf(name, imageUrl)
        val id = "${c.kind}|${c.label}|${c.placeId ?: ""}|${c.deckId ?: ""}"
        val had = row.chips[id]
        row.chips[id] = had?.copy(qty = had.qty + c.qty) ?: c
        if (c.kind != FindChipKind.TRADE && c.kind != FindChipKind.SELL) row.copies += c.qty
    }

    for (c in collections) {
        if (c.kind == CollectionType.WISHLIST) continue
        for (e in c.entries) {
            val copies = maxOf(0, e.quantity) + maxOf(0, e.foilQuantity)
            if (copies <= 0) continue
            var here = 0
            for (line in placedCopies(e)) {
                val place = byId[line.placeId] ?: continue
                here += line.qty
                val label = if (line.section != null) "${place.name} › ${line.section}" else place.name
                chip(e.name, e.imageUrl, FindChip(FindChipKind.PLACE, label, line.qty, placeId = place.id))
            }
            val (plain, foil) = lentOf(byEntry, c.id, e)
            val left = copies - here - plain - foil
            if (left > 0) {
                chip(e.name, e.imageUrl, if (lentTag(e) != null) FindChip(FindChipKind.LENT, "Lent out", left) else FindChip(FindChipKind.NONE, "No place", left))
            }
            chip(e.name, e.imageUrl, FindChip(FindChipKind.TRADE, "For trade", forTradeOf(e)))
            chip(e.name, e.imageUrl, FindChip(FindChipKind.SELL, "To sell", forSaleOf(e)))
        }
    }
    for (l in lent) chip(l.card.name, null, FindChip(FindChipKind.LENT, "Lent to ${l.loan.to}", l.qty))
    val lentFrom = HashMap<String, Int>()
    for (l in lent) {
        val deckId = l.card.deckId ?: continue
        val k = "$deckId|${cardKey(l.card.name)}"
        lentFrom[k] = (lentFrom[k] ?: 0) + l.qty
    }
    val deckRows = mutableListOf<FindDeckRow>()
    for (d in decks) {
        for (e in realCopiesOf(d)) {
            val k = "${d.id}|${cardKey(e.name)}"
            chip(e.name, e.imageUrl, FindChip(FindChipKind.DECK, deckTitle(d.name), e.quantity - (lentFrom[k] ?: 0), deckId = d.id))
            lentFrom.remove(k)
        }
        val used = LinkedHashMap<String, Boolean>()
        for (e in d.cards) {
            if (e.quantity <= 0) continue
            // Known as a card of the deck's even when every copy is a proxy.
            cardOf(e.name, e.imageUrl)
            val k = cardKey(e.name)
            used[k] = (used[k] ?: false) || proxyCopies(d, e) > 0
        }
        deckRows.add(FindDeckRow(FindName(d.name), d, GameMode.fromName(d.gameMode).label, used))
    }
    for (g in gradedOf(collections)) chip(g.name, g.imageUrl, FindChip(FindChipKind.GRADED, "Graded ${gradeLabel(g)}", 1))

    val cardRows = cards.map { (key, b) ->
        val chips = b.chips.values.sortedBy { CHIP_ORDER.indexOf(it.kind) }
        FindCardRow(FindName(b.name), FoundCard(b.name, b.imageUrl, b.copies, chips), key)
    }

    val summary = storageSummary(collections, decks)
    val placeRows = places.map { p ->
        val inside = placeAndInside(places, p.id)
        val copies = inside.sumOf { summary.own[it] ?: 0 }
        val path = parentsOf(places, p.id).joinToString(" › ") { it.name }
        val line = if (inside.size > 1) "${inside.size - 1} ${if (inside.size == 2) "place" else "places"} inside"
        else "${p.placeKind.label} · ${count(copies)} ${if (copies == 1) "copy" else "copies"}"
        FindPlaceRow(FindName(p.name), FoundPlace(p.id, p.name, if (path.isNotEmpty()) "$line · in $path" else line))
    }
    return FindIndex(cardRows, placeRows, deckRows)
}

/** What a query finds. [decksUsing]: decks with a card found in them; [decks]: decks by their name. */
data class FindResult(
    val query: String,
    val cards: List<FoundCard> = emptyList(),
    val places: List<FoundPlace> = emptyList(),
    val decksUsing: List<FoundDeck> = emptyList(),
    val decks: List<FoundDeck> = emptyList(),
    /** More cards matched than are shown. */
    val moreCards: Int = 0
)

object FindLimits {
    const val CARDS = 20
    const val PLACES = 8
    const val DECKS = 8
    const val USING_CARDS = 3
}

private class Scored<T>(val row: T, val score: Int, val text: String)

/** The rows matching [q], best first, then A to Z by their plain words (the same as on the web). */
private fun <T> ranked(rows: List<T>, q: String, name: (T) -> FindName): List<Scored<T>> {
    val out = ArrayList<Scored<T>>()
    for (row in rows) {
        val n = name(row)
        val score = matchScore(n, q)
        if (score > 0) out.add(Scored(row, score, n.text))
    }
    return out.sortedWith(compareByDescending<Scored<T>> { it.score }.thenBy { it.text })
}

/**
 * Finding in [index] as the user types: a query that carries on from the last one only looks again at
 * what that one matched.
 */
class Finder(private val index: FindIndex) {
    private var lastQ = ""
    private var lastCards: List<FindCardRow> = index.cards
    private var lastPlaces: List<FindPlaceRow> = index.places
    private var lastDecks: List<FindDeckRow> = index.decks

    fun find(query: String): FindResult {
        val q = normalizeFind(query)
        if (q.isEmpty()) {
            lastQ = ""
            return FindResult(query)
        }
        val carryOn = lastQ.isNotEmpty() && q.startsWith(lastQ)
        val cards = ranked(if (carryOn) lastCards else index.cards, q) { it.name }
        val places = ranked(if (carryOn) lastPlaces else index.places, q) { it.name }
        val decks = ranked(if (carryOn) lastDecks else index.decks, q) { it.name }
        lastQ = q
        lastCards = cards.map { it.row }
        lastPlaces = places.map { it.row }
        lastDecks = decks.map { it.row }

        val top = cards.take(FindLimits.USING_CARDS).map { it.row.key }
        val using = ArrayList<Pair<String, FoundDeck>>()
        for (d in index.decks) {
            val hits = top.filter { d.cards.containsKey(it) }
            if (hits.isEmpty()) continue
            val proxy = hits.any { d.cards[it] == true }
            using.add(d.name.text to FoundDeck(d.deck.id, d.deck.name, d.format + if (proxy) " · proxy" else ""))
        }
        val decksUsing = using.sortedBy { it.first }.map { it.second }
        val usingIds = decksUsing.map { it.id }.toSet()
        return FindResult(
            query = query,
            cards = cards.take(FindLimits.CARDS).map { it.row.card },
            places = places.take(FindLimits.PLACES).map { it.row.place },
            decksUsing = decksUsing.take(FindLimits.DECKS),
            decks = decks.filter { it.row.deck.id !in usingIds }.take(FindLimits.DECKS).map { FoundDeck(it.row.deck.id, it.row.deck.name, it.row.format) },
            moreCards = maxOf(0, cards.size - FindLimits.CARDS)
        )
    }
}

/** One query over [index], from scratch. */
fun findIn(index: FindIndex, query: String): FindResult = Finder(index).find(query)

/** "4 copies"; a card only in decks as proxies has "Proxy only". */
fun copiesLine(card: FoundCard): String =
    if (card.copies == 0) "Proxy only" else "${count(card.copies)} ${if (card.copies == 1) "copy" else "copies"}"

/** A chip's words: "Red box › Colourless ×1". */
fun chipLabel(chip: FindChip): String = "${chip.label} ×${count(chip.qty)}"
