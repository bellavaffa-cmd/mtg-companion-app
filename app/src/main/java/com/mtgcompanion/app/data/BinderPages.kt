package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/*
 * Binder pages: a binder seen as it sits on the shelf — sheets of pockets, each sheet with a front and
 * a back (page 1 is the front of sheet 1, page 2 its back, page 3 the front of sheet 2…) — and the
 * rules for moving cards about in it:
 *  - the pockets grid of a page (9 pockets: 3 × 3; 12: 3 × 4; 4: 2 × 2…) and a page's summary
 *    ("DMU 12–98", "A–C", "Red · A–F");
 *  - a binder's order, its sorting rule (StoragePlace.sortRule, as a box's: by set then number, A–Z,
 *    by colour then A–Z, by type then A–Z);
 *  - fitting new cards in that order (planFit): where each goes, which cards shift along to make room,
 *    and the steps to do it by hand (fitSteps) — moves listed from the last card backwards, so a pocket
 *    is always empty when a card goes into it;
 *  - closing the gaps, and moving or swapping pockets by hand.
 * A pocket is counted from 0 through the whole binder: page 1's pockets, then page 2's, and so on.
 * Nothing new is kept: a copy's pocket is its line's "page" and "slot" (CopyPlace), as in round 1.
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/binderPages.ts rule for rule, with
 * the same tests (BinderPagesTest.kt ↔ tests/collection/binderPages.test.ts).
 */

// ---- Pages and sheets ----

/** The sheet a page is printed on: pages 1 and 2 are sheet 1. */
fun sheetOf(page: Int): Int = (page + 1) / 2

/** Odd pages are a sheet's front, even pages its back. */
fun sideOf(page: Int): String = if (page % 2 == 1) "Front" else "Back"

/** "Front of sheet 2". */
fun sideLabel(page: Int): String = "${sideOf(page)} of sheet ${sheetOf(page)}"

/** How a page's pockets are laid out: 9 → 3 × 3, 12 → 3 across and 4 down, 4 → 2 × 2, 8 → 2 × 4. Columns to rows. */
fun pageGrid(pockets: Int): Pair<Int, Int> {
    val n = pockets.coerceAtLeast(1)
    var cols = 1
    var c = 1
    while (c * c <= n) {
        if (n % c == 0) cols = c
        c++
    }
    // A number with no good split (7, 11…) is laid out as near a square as it goes.
    if (cols == 1 && n > 3) cols = ceil(sqrt(n.toDouble())).toInt()
    return cols to (n + cols - 1) / cols
}

/** A pocket counted through the whole binder, from 0. */
fun pocketIndex(page: Int, slot: Int, pockets: Int): Int = (page - 1) * pockets + (slot - 1)

/** The page and slot (both from 1) of the pocket [index]. */
fun pocketAt(index: Int, pockets: Int): Pair<Int, Int> = (index / pockets + 1) to (index % pockets + 1)

/** A pocket in use: where it is in the binder and the copies in it. */
data class Pocket(val index: Int, val cards: List<PlacedCard>)

/** Whether a line sits in a pocket the binder's pages have (a slot past the page's pockets doesn't count). */
private fun inPocket(line: CopyPlace, pockets: Int): Boolean =
    (line.page ?: 0) > 0 && (line.slot ?: 0) > 0 && line.slot!! <= pockets

/** The binder's pockets in use, in order. [cards]: its cards (cardsIn). */
fun binderPockets(place: StoragePlace, cards: List<PlacedCard>): List<Pocket> {
    val pockets = place.pockets
    val byIndex = LinkedHashMap<Int, MutableList<PlacedCard>>()
    for (c in cards) {
        if (!inPocket(c.line, pockets)) continue
        byIndex.getOrPut(pocketIndex(c.line.page!!, c.line.slot!!, pockets)) { mutableListOf() } += c
    }
    return byIndex.entries.sortedBy { it.key }.map { Pocket(it.key, it.value) }
}

/** The binder's copies that aren't in a pocket yet, one for each copy — the cards waiting to be fitted in. */
fun looseCopies(place: StoragePlace, cards: List<PlacedCard>): List<PlacedCard> {
    val pockets = place.pockets
    return cards.filter { !inPocket(it.line, pockets) }.flatMap { c -> List(c.line.qty.coerceAtLeast(0)) { c } }
}

/** How many pages the binder shows: up to the last one used, at least one. */
fun pageCount(place: StoragePlace, pockets: List<Pocket>): Int =
    maxOf(1, pockets.maxOfOrNull { pocketAt(it.index, place.pockets).first } ?: 1)

// ---- The order ----

private fun text(a: String, b: String) = a.compareTo(b).coerceIn(-1, 1)
private fun nameKey(f: CardFacts) = f.name.trim().lowercase()
private val NUMBER = Regex("^(\\d{1,9})(.*)$")

/** A collector number as its number and what follows it: "12a" → 12, "a". Missing sorts last. */
private fun numberKey(n: String?): Pair<Long, String> {
    val t = (n ?: "").trim()
    val m = NUMBER.find(t) ?: return Long.MAX_VALUE to t.lowercase()
    return m.groupValues[1].toLong() to m.groupValues[2].lowercase()
}

private fun setKey(f: CardFacts) = f.set?.takeIf { it.isNotEmpty() }?.lowercase() ?: "￿"

private fun bySetNumber(a: CardFacts, b: CardFacts): Int {
    val (na, sa) = numberKey(a.collectorNumber)
    val (nb, sb) = numberKey(b.collectorNumber)
    return text(setKey(a), setKey(b)).takeIf { it != 0 } ?: compareValues(na, nb).takeIf { it != 0 } ?: text(sa, sb)
}

private fun then(first: Int, next: () -> Int) = if (first != 0) first else next()

/**
 * Which of two cards comes first in a binder sorted by [rule] (null: A–Z): by set code then collector
 * number, by name, by colour section (White … Lands) then name, or by type section then name.
 */
fun compareCards(rule: SortRule?, a: CardFacts, b: CardFacts): Int = when (rule) {
    SortRule.SET -> then(bySetNumber(a, b)) { text(nameKey(a), nameKey(b)) }
    SortRule.COLOUR -> then(COLOUR_SECTIONS.indexOf(colourSection(a)) - COLOUR_SECTIONS.indexOf(colourSection(b))) { text(nameKey(a), nameKey(b)) }
    SortRule.TYPE -> then(TYPE_SECTIONS.indexOf(typeSection(a)) - TYPE_SECTIONS.indexOf(typeSection(b))) { text(nameKey(a), nameKey(b)) }
    else -> then(text(nameKey(a), nameKey(b))) { bySetNumber(a, b) }
}

/** What's on a page, short: "DMU 12–98", "DMU 240 – ONE 12", "A–C", "Red · A–F", "Red to Green", "Empty". */
fun pageSummary(rule: SortRule?, facts: List<CardFacts>): String {
    if (facts.isEmpty()) return "Empty"
    val sorted = facts.sortedWith { a, b -> compareCards(rule, a, b) }
    val first = sorted.first()
    val last = sorted.last()
    val letters = if (letterOf(first.name) == letterOf(last.name)) letterOf(first.name) else "${letterOf(first.name)}–${letterOf(last.name)}"
    if (rule == SortRule.SET && sorted.all { !it.set.isNullOrEmpty() }) {
        fun at(f: CardFacts) = listOf(f.set!!.uppercase(), f.collectorNumber ?: "").filter { it.isNotEmpty() }.joinToString(" ")
        if (!first.set!!.equals(last.set!!, ignoreCase = true)) return "${at(first)} – ${at(last)}"
        val set = first.set.uppercase()
        val a = first.collectorNumber
        val b = last.collectorNumber
        if (a.isNullOrEmpty() || b.isNullOrEmpty()) return set
        return if (a == b) "$set $a" else "$set $a–$b"
    }
    if (rule == SortRule.COLOUR || rule == SortRule.TYPE) {
        val section: (CardFacts) -> String = if (rule == SortRule.COLOUR) ::colourSection else ::typeSection
        val a = section(first)
        val b = section(last)
        return if (a == b) "$a · $letters" else "$a to $b"
    }
    return letters
}

/** A card's facts for the order: from Scryfall when it's loaded, else just its name. */
fun factsFrom(data: Map<String, ScryfallCard>?): (PlacedCard) -> CardFacts = { c ->
    data?.get(c.entry.scryfallId)?.let { cardFactsOf(it) } ?: CardFacts(c.entry.name)
}

/** "DMU 107 · foil": a copy's set and number, and whether it's foil. */
fun printingLine(set: String?, collectorNumber: String?, foil: Boolean): String =
    listOf(if (!set.isNullOrEmpty()) "${set.uppercase()} ${collectorNumber ?: ""}".trim() else "", if (foil) "foil" else "")
        .filter { it.isNotEmpty() }.joinToString(" · ")

// ---- Fitting new cards in order ----

/** One pocket's cards moved to another pocket. */
data class PocketMove(val from: Int, val to: Int)

/** Where a new card goes: [item] is its place in the list of cards being added. */
data class FitPut(val item: Int, val to: Int)

/** "Keep the order" shifts cards along to make room; "Fill gaps, no shifting" only uses empty pockets. */
enum class FitMode { KEEP, GAPS }

/** [moves]: the pockets in use that move, each once, from where it is now to where it ends up. */
data class FitPlan(val moves: List<PocketMove>, val puts: List<FitPut>)

/** In a pocket while planning: a card there now ([old], its place in the pockets in use) or a new one ([item]). */
private data class Slot(val old: Int?, val item: Int?, val facts: CardFacts)

/** A pocket in use, for planning: where it is and the facts of the card in it. */
data class Placed(val index: Int, val facts: CardFacts)

/**
 * Where [adding] go in a binder sorted by [rule] whose pockets in use are [occupied], and which cards
 * move to make room. Each new card goes after the last card that sorts the same or before it (so it's
 * stable: after the copies already there, and cards added together keep their order), in the first
 * pocket after that card. When there's no empty pocket there:
 *  - KEEP: the cards from there on shift one along up to the next empty pocket (pages that are full
 *    spill onto the next page) — or, when it moves fewer cards, the cards before it shift one back
 *    into the empty pocket before them;
 *  - GAPS: nothing moves, and it goes in the nearest empty pocket, after its place when that's as near.
 * Several cards are fitted one after another in order, each into the binder as the last one left it.
 */
fun planFit(rule: SortRule?, occupied: List<Placed>, adding: List<CardFacts>, mode: FitMode): FitPlan {
    val slots = HashMap<Int, Slot>()
    occupied.forEachIndexed { i, p -> slots[p.index] = Slot(i, null, p.facts) }
    val order = adding.withIndex().sortedWith { a, b -> then(compareCards(rule, a.value, b.value)) { a.index - b.index } }
    for (x in order) {
        val keys = slots.keys.sorted()
        val gi = keys.indexOfFirst { compareCards(rule, slots.getValue(it).facts, x.value) > 0 }
        val g = if (gi < 0) null else keys[gi]
        val before = if (gi < 0) keys else keys.subList(0, gi)
        val a = before.lastOrNull() ?: -1
        val to: Int
        if (g == null || g - a > 1) {
            to = a + 1
        } else {
            var ahead = g + 1
            while (slots.containsKey(ahead)) ahead++
            var behind = a - 1
            while (behind >= 0 && slots.containsKey(behind)) behind--
            val forward = ahead - g
            val back = if (behind >= 0) a - behind else Int.MAX_VALUE
            if (mode == FitMode.GAPS) {
                to = if (back < forward) behind else ahead
            } else if (back < forward) {
                // The cards from the empty pocket behind up to a shift one back.
                for (k in behind + 1..a) slots[k - 1] = slots.getValue(k)
                slots.remove(a)
                to = a
            } else {
                // The cards from g up to the empty pocket ahead shift one along, the last first.
                for (k in ahead - 1 downTo g) slots[k + 1] = slots.getValue(k)
                slots.remove(g)
                to = g
            }
        }
        slots[to] = Slot(null, x.index, x.value)
    }
    val moves = mutableListOf<PocketMove>()
    val puts = mutableListOf<FitPut>()
    for ((at, s) in slots) {
        if (s.old != null) {
            val from = occupied[s.old].index
            if (from != at) moves += PocketMove(from, at)
        } else puts += FitPut(s.item!!, at)
    }
    return FitPlan(moves.sortedBy { it.from }, puts.sortedBy { it.item })
}

/** One step of the instructions: what to do, and a line under it ("" for none). */
data class FitStep(val title: String, val detail: String)

private val WORDS = listOf("", "one", "two", "three", "four", "five")
private fun howFar(n: Int) = "${WORDS.getOrNull(abs(n)) ?: abs(n).toString()} ${if (n > 0) "along" else "back"}"
private fun at(index: Int, pockets: Int): String {
    val (page, slot) = pocketAt(index, pockets)
    return "page $page, slot $slot"
}

/** A step's event: a move of a pocket in use, or a new card put in. */
private data class FitEvent(val move: PocketMove?, val put: FitPut?)

/**
 * The plan as steps to follow by hand. Cards moving back go first, from the first card on; then the
 * cards moving along and the new cards, from the last pocket backwards — so the pocket a card goes
 * into is always empty by then. Runs of cards moving the same way are one step ("Page 3: move slots
 * 5–7 one along", "Pages 4–5: move 11 cards one along"), and new cards going into one page together
 * are one step. [nameOf]: the card in a pocket in use now; [newCard]: a new card's name and its line
 * ("DMU 97 · foil").
 */
fun fitSteps(plan: FitPlan, pockets: Int, nameOf: (Int) -> String, newCard: (Int) -> Pair<String, String>): List<FitStep> {
    val back = plan.moves.filter { it.to < it.from }.sortedBy { it.from }.map { FitEvent(it, null) }
    val along = (plan.moves.filter { it.to > it.from }.map { FitEvent(it, null) } + plan.puts.map { FitEvent(null, it) })
        .sortedWith { a, b ->
            val ka = a.move?.from ?: a.put!!.to
            val kb = b.move?.from ?: b.put!!.to
            then(kb - ka) { (if (a.move != null) 0 else 1) - (if (b.move != null) 0 else 1) }
        }
    val events = back + along
    val steps = mutableListOf<FitStep>()
    var i = 0
    while (i < events.size) {
        val e = events[i]
        if (e.move != null) {
            val run = mutableListOf(e.move)
            val delta = e.move.to - e.move.from
            val step = if (delta > 0) -1 else 1
            while (i + run.size < events.size) {
                val next = events[i + run.size].move ?: break
                val prev = run.last()
                if (next.from != prev.from + step || next.to - next.from != delta) break
                run += next
            }
            i += run.size
            if (run.size == 1) {
                steps += FitStep("Move ${nameOf(e.move.from)} from ${at(e.move.from, pockets)} to ${at(e.move.to, pockets)}", "")
                continue
            }
            val froms = run.map { it.from }.sorted()
            val (firstPage, firstSlot) = pocketAt(froms.first(), pockets)
            val (lastPage, lastSlot) = pocketAt(froms.last(), pockets)
            val title = if (firstPage == lastPage) "Page $firstPage: move slots $firstSlot–$lastSlot ${howFar(delta)}"
            else "Pages $firstPage–$lastPage: move ${run.size} cards ${howFar(delta)}"
            steps += FitStep(title, if (delta > 0) "Starting from the last card, so nothing is in the way" else "Starting from the first card, so nothing is in the way")
        } else {
            val put = e.put!!
            val page = pocketAt(put.to, pockets).first
            val group = mutableListOf(put)
            while (i + group.size < events.size) {
                val next = events[i + group.size].put ?: break
                if (pocketAt(next.to, pockets).first != page) break
                group += next
            }
            i += group.size
            if (group.size == 1) {
                val (name, detail) = newCard(put.item)
                steps += FitStep("Put $name in ${at(put.to, pockets)}", detail)
            } else {
                val lines = group.sortedBy { it.to }.map { "Slot ${pocketAt(it.to, pockets).second}: ${newCard(it.item).first}" }
                steps += FitStep("Put ${group.size} cards in page $page", lines.joinToString(" · "))
            }
        }
    }
    return steps
}

// ---- Moving pockets by hand ----

/** Closing the gaps: every pocket in use, in order, moved up to fill the empty pockets before it. */
fun closeGapsMoves(occupied: List<Int>): List<PocketMove> =
    occupied.sorted().mapIndexed { to, from -> PocketMove(from, to) }.filter { it.from != it.to }

/** Dragging the pocket [from] to [to]: the pockets between shift over by one to make room. */
fun reorderMoves(from: Int, to: Int, occupied: Set<Int>): List<PocketMove> {
    if (from == to || from !in occupied) return emptyList()
    if (to !in occupied) return listOf(PocketMove(from, to))
    val moves = mutableListOf(PocketMove(from, to))
    if (from < to) for (k in from + 1..to) { if (k in occupied) moves += PocketMove(k, k - 1) }
    else for (k in to until from) { if (k in occupied) moves += PocketMove(k, k + 1) }
    return moves.sortedBy { it.from }
}

/** Swapping two pockets (either may be empty). */
fun swapMoves(a: Int, b: Int, occupied: Set<Int>): List<PocketMove> {
    if (a == b) return emptyList()
    return listOfNotNull(if (a in occupied) PocketMove(a, b) else null, if (b in occupied) PocketMove(b, a) else null)
}

/** The moves that take [moves] back. */
fun undoMoves(moves: List<PocketMove>): List<PocketMove> = moves.map { PocketMove(it.to, it.from) }

/** [collections] with the binder's pockets moved, all at once — every copy in a moved pocket goes with it. */
fun relocate(collections: List<Collection>, place: StoragePlace, moves: List<PocketMove>): List<Collection> {
    if (moves.isEmpty()) return collections
    val pockets = place.pockets
    val to = moves.associate { it.from to it.to }
    fun moved(e: CollectionEntry) = placedCopies(e).any { it.placeId == place.id && inPocket(it, pockets) && pocketIndex(it.page!!, it.slot!!, pockets) in to }
    return collections.map { c ->
        if (c.kind == CollectionType.WISHLIST || c.entries.none(::moved)) c
        else c.copy(entries = c.entries.map { e ->
            if (!moved(e)) e
            else withPlaces(e, placedCopies(e).map { l ->
                if (l.placeId != place.id || !inPocket(l, pockets)) l
                else {
                    val target = to[pocketIndex(l.page!!, l.slot!!, pockets)]
                    if (target == null) l
                    else {
                        val (page, slot) = pocketAt(target, pockets)
                        l.copy(page = page, slot = slot)
                    }
                }
            })
        })
    }
}

/** [collections] with a fit plan done: the pockets moved, then each new card ([items], loose copies) put in its pocket. */
fun applyFit(collections: List<Collection>, place: StoragePlace, plan: FitPlan, items: List<PlacedCard>): List<Collection> {
    val pockets = place.pockets
    var out = relocate(collections, place, plan.moves)
    for (put in plan.puts) {
        val item = items.getOrNull(put.item) ?: continue
        val (page, slot) = pocketAt(put.to, pockets)
        out = out.map { c ->
            if (c.id != item.collectionId) c
            else c.copy(entries = c.entries.map { e ->
                if (e.scryfallId == item.entry.scryfallId) moveCopies(e, item.line, Spot(place.id, page = page, slot = slot), 1).first else e
            })
        }
    }
    return out
}

/** A fit plan with what it needs: the cards being added and the binder's pockets in use. */
data class LooseFit(val plan: FitPlan, val items: List<PlacedCard>, val pockets: List<Pocket>)

/** The plan for fitting the binder's loose copies in order. */
fun fitLooseCards(collections: List<Collection>, place: StoragePlace, factsOf: (PlacedCard) -> CardFacts, mode: FitMode): LooseFit {
    val cards = cardsIn(collections, place.id)
    val pockets = binderPockets(place, cards)
    val items = looseCopies(place, cards)
    val plan = planFit(place.rule, pockets.map { Placed(it.index, factsOf(it.cards.first())) }, items.map(factsOf), mode)
    return LooseFit(plan, items, pockets)
}
