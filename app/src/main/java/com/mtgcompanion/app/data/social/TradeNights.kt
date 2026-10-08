package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.goalProgress
import com.mtgcompanion.app.data.goalsOf
import com.mtgcompanion.app.data.isArchived
import com.mtgcompanion.app.data.missingNames
import com.mtgcompanion.app.data.namesDecksUse
import com.mtgcompanion.app.data.pullNeeds
import kotlin.math.abs

/*
 * Trade nights: trading at a game night. Each player who's Going can put up a list for the night —
 * the cards they bring to trade (from binders they pick, or their event bag) and the cards they want
 * (Wishlist, cards their decks are missing, collection goals). From everyone's lists:
 *  - Wanted here: the cards the user wants that someone is bringing;
 *  - They want from you: the cards the user brings that someone wants;
 *  - Suggested trades: per person, a fair bundle — the cards each side wants most, worth the same
 *    within the trade-fairness rule (TradeFairness.kt isFair: $2 or a tenth), only cards that are
 *    spare (marked for trade, or no deck of theirs uses them);
 *  - the Trade table: the trades made at the night, agreed ones first, to tick off once swapped.
 * The server keeps the lists (supabase/migrations/20261008100000_trade_nights.sql); everything here
 * is worked out on the device.
 *
 * Pure, so it can be tested. Mirrors the web app's src/social/tradeNights.ts, with the same cases
 * (app/src/test/resources/tradeNightVectors.json ↔ tests/social/tradeNightVectors.json).
 */

/** One line of a list: copies of a printing the player brings. [spare]: no deck of theirs needs it, or it's marked for trade. */
data class NightCard(
    val scryfallId: String,
    val name: String,
    val imageUrl: String? = null,
    val foil: Boolean = false,
    val quantity: Int = 1,
    val collectionId: String? = null,
    val condition: String? = null,
    val spare: Boolean = false
)

/** A card the player wants, and how much: [WANT_WISHLIST], [WANT_DECK] or [WANT_GOAL]. */
data class NightWant(val name: String, val weight: Int)

/** One player's list for the night. */
data class NightList(val userId: String, val name: String, val cards: List<NightCard>, val wants: List<NightWant>)

/** What the cards come from: a binder ([id]), or the event bag ([id] null). */
data class NightSource(val kind: String, val id: String?, val name: String) {
    val isBag: Boolean get() = kind == BAG
    companion object {
        const val BINDER = "binder"
        const val BAG = "bag"
        fun binder(id: String, name: String) = NightSource(BINDER, id, name)
        fun bag() = NightSource(BAG, null, BAG_SOURCE_NAME)
    }
}

const val WANT_WISHLIST = 3
const val WANT_DECK = 2
const val WANT_GOAL = 1
/** A list holds up to this many lines (the server's limit). */
const val NIGHT_MAX_LINES = 500
/** The event bag source's name. */
const val BAG_SOURCE_NAME = "Event bag"

private fun key(s: String) = s.trim().lowercase()

// ---- Wants ----

/**
 * The user's wants: Wishlist names (weight 3), the cards their decks are missing (2), the cards their
 * collection goals are missing (1). Each name once, its highest weight and its first spelling; the
 * most wanted first, then A–Z; up to [NIGHT_MAX_LINES].
 */
fun mergeWants(wishlist: List<String>, decksMissing: List<String>, goalsMissing: List<String>): List<NightWant> {
    val seen = LinkedHashMap<String, NightWant>()
    for ((names, weight) in listOf(wishlist to WANT_WISHLIST, decksMissing to WANT_DECK, goalsMissing to WANT_GOAL)) {
        for (n in names) {
            val k = key(n)
            if (k.isEmpty() || k in seen) continue
            seen[k] = NightWant(n.trim(), weight)
        }
    }
    return seen.values.sortedWith(compareByDescending<NightWant> { it.weight }.thenBy { key(it.name) }).take(NIGHT_MAX_LINES)
}

/** How much [wants] want [name]: 0 when not at all. */
fun wantWeight(wants: List<NightWant>, name: String): Int {
    val k = key(name)
    return wants.filter { key(it.name) == k }.maxOfOrNull { it.weight } ?: 0
}

// ---- Bring list ----

/** The binders picked when the user hasn't picked any: the ones called "…trade…" (not the Wishlist). */
fun defaultSources(collections: List<Collection>): List<NightSource> =
    collections.filter { it.type != "WISHLIST" && "trade" in key(it.name) }.map { NightSource.binder(it.id, it.name) }

private fun spareOf(e: CollectionEntry, decksUse: Set<String>) = (e.forTrade ?: 0) > 0 || key(e.name) !in decksUse

/**
 * The cards the user brings from [sources]: each picked binder's copies (plain and foil apart, up to
 * 99 a line), and for the event bag one copy of each of [bagNames] (the "Bring to game night" cards)
 * from the first binder that has one — a card already brought from a binder isn't added again.
 * [decksUse]: the names the user's decks use, lower case (Spares.kt namesDecksUse, without the
 * "Bring to game night" deck). Up to [NIGHT_MAX_LINES] lines.
 */
fun bringCards(collections: List<Collection>, sources: List<NightSource>, bagNames: List<String>, decksUse: Set<String>): List<NightCard> {
    val out = mutableListOf<NightCard>()
    fun line(e: CollectionEntry, binder: String, foil: Boolean, quantity: Int) = NightCard(
        e.scryfallId, e.name, e.imageUrl, foil, minOf(quantity, 99), binder, e.condition, spareOf(e, decksUse)
    )
    val owned = collections.filter { it.type != "WISHLIST" }
    for (s in sources) {
        if (s.isBag) continue
        val binder = owned.firstOrNull { it.id == s.id } ?: continue
        for (e in binder.entries) {
            if (e.quantity > 0) out += line(e, binder.id, false, e.quantity)
            if (e.foilQuantity > 0) out += line(e, binder.id, true, e.foilQuantity)
        }
    }
    if (sources.any { it.isBag }) {
        for (n in bagNames) {
            val k = key(n)
            if (out.any { key(it.name) == k }) continue
            for (b in owned) {
                val e = b.entries.firstOrNull { key(it.name) == k && it.quantity + it.foilQuantity > 0 } ?: continue
                out += line(e, b.id, e.quantity <= 0, 1)
                break
            }
        }
    }
    return out.take(NIGHT_MAX_LINES)
}

// ---- Matching ----

/** Someone at the night bringing a card: who, with their line. */
data class BroughtBy(val userId: String, val name: String, val card: NightCard)

/** A card the user wants that someone brings. */
data class WantedHereRow(val name: String, val weight: Int, val from: List<BroughtBy>)

/** The cards the user wants ([myWants]) that the others bring — the most wanted first, then A–Z; each person once a card. */
fun wantedHere(myWants: List<NightWant>, others: List<NightList>): List<WantedHereRow> {
    val wants = myWants.sortedWith(compareByDescending<NightWant> { it.weight }.thenBy { key(it.name) })
    val out = mutableListOf<WantedHereRow>()
    val done = mutableSetOf<String>()
    for (w in wants) {
        val k = key(w.name)
        if (!done.add(k)) continue
        val from = others.mapNotNull { o -> o.cards.firstOrNull { key(it.name) == k }?.let { BroughtBy(o.userId, o.name, it) } }
        if (from.isNotEmpty()) out += WantedHereRow(w.name, w.weight, from)
    }
    return out
}

/** One of the user's cards someone wants, and how much. */
data class NightWantedCard(val card: NightCard, val weight: Int)

/** Someone who wants cards the user brings: those cards (each name once). */
data class TheyWantRow(val userId: String, val name: String, val cards: List<NightWantedCard>)

/** Per person, the cards in the user's list ([myCards]) they want — the most wanted first; the people wanting most first. */
fun theyWantFromYou(myCards: List<NightCard>, others: List<NightList>): List<TheyWantRow> {
    val out = mutableListOf<TheyWantRow>()
    for (o in others) {
        val seen = mutableSetOf<String>()
        val cards = mutableListOf<NightWantedCard>()
        for (c in myCards) {
            val k = key(c.name)
            if (k in seen) continue
            val weight = wantWeight(o.wants, c.name)
            if (weight <= 0) continue
            seen += k
            cards += NightWantedCard(c, weight)
        }
        if (cards.isNotEmpty()) out += TheyWantRow(o.userId, o.name, cards.sortedWith(compareByDescending<NightWantedCard> { it.weight }.thenBy { key(it.card.name) }))
    }
    return out.sortedWith(compareByDescending<TheyWantRow> { it.cards.size }.thenBy { key(it.name) }.thenBy { it.userId })
}

// ---- Suggested trades ----

/** A fair bundle with one person: what the user gets and gives (one copy each), and what each side is worth (US dollars). */
data class NightSuggestion(val userId: String, val name: String, val get: List<TradeCard>, val give: List<TradeCard>, val getValue: Double, val giveValue: Double)

/** Up to this many cards a side to start from. */
private const val MAX_SIDE = 10

private class Candidate(val card: NightCard, val weight: Int, val price: Double)

/** A list's line as a trade line: one copy, out of the binder it's in. */
fun nightAsTrade(c: NightCard): TradeCard = TradeCard(c.scryfallId, c.name, c.imageUrl, c.foil, 1, c.collectionId, c.condition)

private fun NightCard.asPriced() = TradeCard(scryfallId, name, foil = foil)

/** [cards] that are spare, priced and wanted by [wants], each name once — the most wanted, then the dearest, then A–Z. */
private fun candidates(cards: List<NightCard>, wants: List<NightWant>, prices: Map<String, CardPrice>): MutableList<Candidate> {
    val seen = mutableSetOf<String>()
    val out = mutableListOf<Candidate>()
    for (c in cards) {
        if (!c.spare) continue
        val k = key(c.name)
        if (k in seen) continue
        val weight = wantWeight(wants, c.name)
        if (weight <= 0) continue
        val price = unitPrice(c.asPriced(), prices)
        if (price == null || price <= 0.0) continue
        seen += k
        out += Candidate(c, weight, price)
    }
    return out.sortedWith(compareByDescending<Candidate> { it.weight }.thenByDescending { it.price }.thenBy { key(it.card.name) })
        .take(MAX_SIDE).toMutableList()
}

private fun total(side: List<Candidate>): Double {
    var s = 0.0
    for (c in side) s += c.price
    return s
}

/**
 * A fair trade between the user ([me]) and [them], or null when there isn't one: each side starts with
 * the spare, priced cards the other wants (up to 10, the most wanted first); then, while it isn't fair,
 * the heavier side gives up a card — the least wanted one that brings the totals closer (then the one
 * bringing them closest, then A–Z) — never its last. Null when a side has nothing, or it can't be made fair.
 */
fun suggestTrade(me: NightList, them: NightList, prices: Map<String, CardPrice>): NightSuggestion? {
    val get = candidates(them.cards, me.wants, prices)
    val give = candidates(me.cards, them.wants, prices)
    if (get.isEmpty() || give.isEmpty()) return null
    while (true) {
        val sg = total(get)
        val sv = total(give)
        val diff = sg - sv
        if (isFair(diff, sg, sv)) {
            return NightSuggestion(them.userId, them.name, get.map { nightAsTrade(it.card) }, give.map { nightAsTrade(it.card) }, sg, sv)
        }
        val heavy = if (diff > 0) get else give
        if (heavy.size <= 1) return null
        val gap = abs(diff)
        var best = -1
        var bestAfter = 0.0
        for (i in heavy.indices) {
            val after = abs(gap - heavy[i].price)
            if (after >= gap) continue
            if (best < 0) { best = i; bestAfter = after; continue }
            val b = heavy[best]
            val c = heavy[i]
            val better = when {
                c.weight != b.weight -> c.weight < b.weight
                after != bestAfter -> after < bestAfter
                else -> key(c.card.name) < key(b.card.name)
            }
            if (better) { best = i; bestAfter = after }
        }
        if (best < 0) return null
        heavy.removeAt(best)
    }
}

/** A fair trade with each of [others] that has one — the biggest first, then A–Z. */
fun suggestedTrades(me: NightList, others: List<NightList>, prices: Map<String, CardPrice>): List<NightSuggestion> =
    others.mapNotNull { suggestTrade(me, it, prices) }
        .sortedWith(compareByDescending<NightSuggestion> { it.getValue + it.giveValue }.thenBy { key(it.name) })

/** The printings whose prices the suggestions need: spare cards either side wants. Sorted, each once. */
fun priceIdsNeeded(me: NightList, others: List<NightList>): List<String> {
    val ids = sortedSetOf<String>()
    for (o in others) {
        for (c in o.cards) if (c.spare && wantWeight(me.wants, c.name) > 0) ids += c.scryfallId
        for (c in me.cards) if (c.spare && wantWeight(o.wants, c.name) > 0) ids += c.scryfallId
    }
    return ids.toList()
}

// ---- Trade table ----

/** AGREED: accepted, the user's binders not updated yet; WAITING: not answered yet; DONE: the user has updated their binders. */
enum class TableState(val wire: String) { AGREED("agreed"), WAITING("waiting"), DONE("done") }

data class TableRow(val trade: Trade, val other: String, val state: TableState)

/** The night's trades as the Trade table lists them: agreed ones first, then waiting, then done; declined, cancelled and countered ones left out. */
fun tradeTable(trades: List<Trade>, me: String): List<TableRow> = trades.mapNotNull { t ->
    val other = if (t.fromUser == me) t.toUser else t.fromUser
    val state = when (t.status) {
        TradeStatus.OPEN -> TableState.WAITING
        TradeStatus.ACCEPTED -> if (if (t.fromUser == me) t.fromApplied else t.toApplied) TableState.DONE else TableState.AGREED
        else -> return@mapNotNull null
    }
    TableRow(t, other, state)
}.sortedBy { it.state.ordinal }

/** "2 agreed · 1 waiting · 1 done" — or null when the table is empty. */
fun nightTableLine(rows: List<TableRow>): String? {
    if (rows.isEmpty()) return null
    fun n(s: TableState) = rows.count { it.state == s }
    return listOfNotNull(
        n(TableState.AGREED).takeIf { it > 0 }?.let { "$it agreed" },
        n(TableState.WAITING).takeIf { it > 0 }?.let { "$it waiting" },
        n(TableState.DONE).takeIf { it > 0 }?.let { "$it done" }
    ).joinToString(" · ")
}

// ---- The user's own library ----

private fun Deck.isBringDeck() = name == GAME_NIGHT_DECK

/**
 * What the user wants for the night, from their library: the Wishlist, the cards their decks need
 * that they don't own at all (the pull list's "not owned"; not archived or sample decks, not the
 * "Bring to game night" deck) and what their unfinished collection goals are missing.
 */
fun nightWantsOf(collections: List<Collection>, decks: List<Deck>): List<NightWant> {
    val wishlist = collections.filter { it.type == "WISHLIST" }.flatMap { c -> c.entries.map { it.name } }
    val owned = collections.filter { it.type != "WISHLIST" }.flatMap { c -> c.entries.filter { it.quantity + it.foilQuantity > 0 }.map { key(it.name) } }.toSet()
    val decksMissing = decks.filter { !it.isArchived && it.sample != true && !it.isBringDeck() }
        .flatMap { d -> pullNeeds(d).map { it.name } }
        .filter { key(it) !in owned }
    val goalsMissing = goalsOf(collections).filter { !it.isComplete }.flatMap { missingNames(goalProgress(it, collections, decks)) }
    return mergeWants(wishlist, decksMissing, goalsMissing)
}

/** The names the user's decks use, for "spare" — without the "Bring to game night" deck, whose cards are there to go. */
fun nightDecksUse(decks: List<Deck>): Set<String> = namesDecksUse(decks.filterNot { it.isBringDeck() })

/** The event bag's cards: the "Bring to game night" deck's (FriendsWant's Bring to game night, Trade matches tonight's Bring them). */
fun bagNamesOf(decks: List<Deck>): List<String> {
    val deck = decks.firstOrNull { it.isBringDeck() && !it.isArchived && it.sample != true } ?: return emptyList()
    return (listOfNotNull(deck.commander, deck.partnerCommander) + deck.cards).map { it.name }
}
