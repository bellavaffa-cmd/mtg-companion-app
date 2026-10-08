package com.mtgcompanion.app.data

/*
 * Collection goals: targets the user sets and works towards — complete a set (or only its uncommons,
 * or in foil), a playset of each card on a list, every card of a deck in foil, or any list of cards
 * in any quantities. Each goal says how many of the copies it needs the user has, what's missing and
 * what that would cost, and once everything is there it's complete (and moves to Completed).
 *
 * What counts as having a card: copies in owned binders, boxes and the Unsorted pile (wishlists
 * never), and — when the goal says so ("count cards in decks") — the real copies in decks the user
 * holds (proxies left out). A foil goal counts foil copies only; in a deck those are the foil copies
 * its pull list brought in (Deck.cameFrom), as decks don't otherwise know a copy's finish. A set goal
 * matches printings (Lightning Bolt from that set, as Set completion does); the others match the card
 * by name, any printing. One copy can count towards several goals.
 *
 * Where goals are kept: the Unsorted pile's "collectionGoals" (Collection.collectionGoals), so they
 * sync like the sorting recipes. Two devices' goals merge goal by goal (mergeGoals): one added on
 * either is kept, one deleted on either stays deleted, and where both changed one the more recently
 * changed (updatedAt) wins whole — a completion is never lost. A pile saved by an app from before
 * goals comes without the key and keeps this device's (keepGoalsFromOlderApp).
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/collectionGoals.ts rule for rule;
 * both run the same test vectors (app/src/test/resources/collectionGoalVectors.json ↔
 * tests/collection/collectionGoalVectors.json).
 */

// ---- What a goal is ----

val GOAL_KINDS = listOf("SET", "PLAYSET", "DECK", "CUSTOM")
val GOAL_KIND_LABELS = mapOf(
    "SET" to "Complete a set",
    "PLAYSET" to "Playsets of a list",
    "DECK" to "Foil a deck",
    "CUSTOM" to "Custom list"
)
val GOAL_KIND_DETAILS = mapOf(
    "SET" to "Every card of a set — or only its uncommons, rares… — optionally in foil.",
    "PLAYSET" to "A playset of each card on a list: \"a playset of each shock land\".",
    "DECK" to "Every card in one of your decks, in foil.",
    "CUSTOM" to "Any cards, any quantities."
)

/** Rarities a set goal can keep to, in this order. */
val GOAL_RARITIES = listOf("common", "uncommon", "rare", "mythic")

/** Copies a playset has, and the most a goal asks of one card. */
const val GOAL_PLAYSET = 4
const val GOAL_MAX_QTY = 99

/**
 * One card a goal wants: [qty] copies of it. [scryfallId] is the printing (what a set goal matches);
 * [imageUrl], [usd] and [usdFoil] are as Scryfall had them when the goal was made, for the list and
 * the value of what's missing; [rarity] and [number] a set goal's printing's. The web app's GoalCard.
 */
data class GoalCard(
    val name: String,
    val scryfallId: String? = null,
    val qty: Int = 1,
    val imageUrl: String? = null,
    val usd: Double? = null,
    val usdFoil: Double? = null,
    val rarity: String? = null,
    val number: String? = null
)

/**
 * A goal ([kind] a GOAL_KINDS name). [setCode] and [rarities] (none: every rarity): SET only;
 * [deckId]: DECK only — its cards are the deck's as it is now, [cards] what it was when the goal was
 * made (used once the deck is gone). [foil]: only foil copies count. [countDecks]: copies in decks
 * count too. [completedAt]: when it was first complete; it stays complete after. The web app's
 * CollectionGoal, field for field.
 */
data class CollectionGoal(
    val id: String,
    val name: String,
    val kind: String,
    val setCode: String? = null,
    val rarities: List<String>? = null,
    val foil: Boolean? = null,
    val deckId: String? = null,
    val cards: List<GoalCard> = emptyList(),
    val countDecks: Boolean? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val completedAt: Long? = null
) {
    val isFoil: Boolean get() = foil == true
    val countsDecks: Boolean get() = countDecks == true
    val isComplete: Boolean get() = completedAt != null
}

/** A card's name as goals key it: trimmed, lowercase (a double-faced card by its whole name, as decks do). */
fun goalNameKey(name: String): String = name.trim().lowercase()

/** How a goal of [kind] keys a card: by printing for a set goal, else by name. */
fun goalCardKey(kind: String, name: String, scryfallId: String?): String =
    if (kind == "SET" && !scryfallId.isNullOrEmpty()) "id:$scryfallId" else "n:${goalNameKey(name)}"

private fun clampQty(n: Int): Int = n.coerceIn(1, GOAL_MAX_QTY)

private fun goalCard(c: GoalCard): GoalCard = GoalCard(
    name = c.name.trim(),
    scryfallId = c.scryfallId?.ifEmpty { null },
    qty = clampQty(c.qty),
    imageUrl = c.imageUrl?.ifEmpty { null },
    usd = c.usd?.takeIf { it.isFinite() },
    usdFoil = c.usdFoil?.takeIf { it.isFinite() },
    rarity = c.rarity?.ifEmpty { null }?.lowercase(),
    number = c.number?.ifEmpty { null }
)

/**
 * A goal as both apps write it: a known kind, only the fields its kind uses, each card once (the
 * copies of a card listed twice added up, the first line's details kept), quantities 1–99, and the
 * flags left out unless on.
 */
fun collectionGoal(g: CollectionGoal): CollectionGoal {
    val kind = if (g.kind in GOAL_KINDS) g.kind else "CUSTOM"
    val cards = mutableListOf<GoalCard>()
    val at = HashMap<String, Int>()
    for (raw in g.cards) {
        if (raw.name.isBlank()) continue
        val c = goalCard(raw)
        val k = goalCardKey(kind, c.name, c.scryfallId)
        val i = at[k]
        if (i == null) { at[k] = cards.size; cards += c } else cards[i] = cards[i].copy(qty = clampQty(cards[i].qty + c.qty))
    }
    val asked = g.rarities.orEmpty().map { it.lowercase() }
    val rarities = if (kind == "SET") GOAL_RARITIES.filter { it in asked } else emptyList()
    return CollectionGoal(
        id = g.id,
        name = g.name.trim().ifEmpty { GOAL_KIND_LABELS.getValue(kind) },
        kind = kind,
        setCode = if (kind == "SET" && !g.setCode.isNullOrEmpty()) g.setCode.trim().lowercase() else null,
        rarities = rarities.ifEmpty { null },
        foil = if (g.foil == true) true else null,
        deckId = if (kind == "DECK" && !g.deckId.isNullOrEmpty()) g.deckId else null,
        cards = cards,
        countDecks = if (g.countDecks == true) true else null,
        createdAt = g.createdAt,
        updatedAt = g.updatedAt,
        completedAt = g.completedAt
    )
}

// ---- Making goals ----

/** A set's printing, as a set goal is made from it. */
data class GoalSetCard(
    val id: String,
    val name: String,
    val rarity: String? = null,
    val number: String? = null,
    val imageUrl: String? = null,
    val usd: Double? = null,
    val usdFoil: Double? = null
)

private val RARITY_PLURALS = mapOf("common" to "commons", "uncommon" to "uncommons", "rare" to "rares", "mythic" to "mythics")

private fun andList(words: List<String>): String =
    if (words.size <= 1) words.joinToString("") else words.dropLast(1).joinToString(", ") + " and " + words.last()

/** "Complete Duskmourn", "Duskmourn uncommons", "Duskmourn foil rares and mythics", "Complete Duskmourn in foil". */
fun setGoalName(setName: String, rarities: List<String>, foil: Boolean): String {
    val picked = GOAL_RARITIES.filter { it in rarities }
    if (picked.isEmpty() || picked.size == GOAL_RARITIES.size) return "Complete $setName" + if (foil) " in foil" else ""
    return setName + (if (foil) " foil" else "") + " " + andList(picked.map { RARITY_PLURALS.getValue(it) })
}

/** A set goal: [cards] (the set's printings) kept to [rarities] (none: all), one of each. */
fun newSetGoal(id: String, setCode: String, setName: String, cards: List<GoalSetCard>, rarities: List<String>, foil: Boolean, now: Long): CollectionGoal {
    val picked = GOAL_RARITIES.filter { it in rarities }
    val keep = if (picked.isEmpty() || picked.size == GOAL_RARITIES.size) null else picked.toSet()
    return collectionGoal(
        CollectionGoal(
            id = id, name = setGoalName(setName, picked, foil), kind = "SET", setCode = setCode, rarities = keep?.let { picked }, foil = foil,
            cards = cards.filter { keep == null || (it.rarity ?: "").lowercase() in keep }.map {
                GoalCard(it.name, it.id, 1, it.imageUrl, it.usd, it.usdFoil, it.rarity, it.number)
            },
            createdAt = now, updatedAt = now
        )
    )
}

/** A deck's cards as a goal wants them: one line per card (a commander once), basic lands left out. */
fun deckGoalCards(deck: Deck): List<GoalCard> {
    val byName = LinkedHashMap<String, MutableList<DeckCardEntry>>()
    for (e in listOfNotNull(deck.commander, deck.partnerCommander) + deck.cards) {
        if (isBasicLand(e.name)) continue
        byName.getOrPut(goalNameKey(e.name)) { mutableListOf() } += e
    }
    return byName.values.map { printings ->
        // One entry per printing: the commander is also among the cards.
        val unique = printings.associateBy { it.scryfallId }.values.toList()
        val first = unique.first()
        GoalCard(first.name, first.scryfallId, unique.sumOf { it.quantity }, first.imageUrl?.ifEmpty { null })
    }
}

/** "Foil Krenko's Goblins" — every card of the deck in foil (or "Own all of …" when not foil). */
fun deckGoalName(deckName: String, foil: Boolean): String = if (foil) "Foil $deckName" else "Own all of $deckName"

/** A deck goal: every card of [deck] (in foil when [foil]); copies in decks count, so the deck's own do. */
fun newDeckGoal(id: String, deck: Deck, foil: Boolean, now: Long): CollectionGoal = collectionGoal(
    CollectionGoal(
        id = id, name = deckGoalName(deck.name, foil), kind = "DECK", deckId = deck.id, foil = foil, cards = deckGoalCards(deck),
        countDecks = true, createdAt = now, updatedAt = now
    )
)

/** A list goal ([kind] PLAYSET or CUSTOM): a playset ([qty] each, default 4) of each card, or a custom list with each card's own count. */
fun newListGoal(id: String, kind: String, name: String, cards: List<GoalCard>, qty: Int?, now: Long): CollectionGoal = collectionGoal(
    CollectionGoal(
        id = id, name = name, kind = kind, cards = if (kind == "PLAYSET") cards.map { it.copy(qty = qty ?: GOAL_PLAYSET) } else cards,
        createdAt = now, updatedAt = now
    )
)

// ---- Progress ----

/** One card of a goal: [need] copies, [have] of them there (at most [need]; [owned]: all counted). [usd]: one copy's price for the goal's finish. */
data class GoalLine(
    val key: String,
    val name: String,
    val scryfallId: String? = null,
    val imageUrl: String? = null,
    val rarity: String? = null,
    val number: String? = null,
    val need: Int,
    val owned: Int,
    val have: Int = 0,
    val missing: Int = 0,
    val usd: Double? = null
)

/** How far a goal has got. [percent] is rounded down, so a goal shows 100% only when complete; [missingUsd] counts only known prices, [unpriced] the missing copies without one. */
data class GoalProgress(
    val have: Int,
    val need: Int,
    val percent: Int,
    val missingUsd: Double,
    val unpriced: Int,
    val complete: Boolean,
    val lines: List<GoalLine>
)

/** A price known now for a printing — fresher than a goal's own; either may be missing. */
data class GoalPrice(val usd: Double? = null, val usdFoil: Double? = null)

/** What [goal] wants now: a deck goal follows its deck (its old list once the deck's gone). */
fun goalTargets(goal: CollectionGoal, decks: List<Deck>): List<GoalCard> {
    if (goal.kind != "DECK") return goal.cards
    val deck = decks.firstOrNull { it.id == goal.deckId } ?: return goal.cards
    val before = goal.cards.associateBy { goalNameKey(it.name) }
    return deckGoalCards(deck).map { c ->
        val was = before[goalNameKey(c.name)]
        GoalCard(c.name, c.scryfallId, c.qty, c.imageUrl ?: was?.imageUrl, was?.usd, was?.usdFoil, was?.rarity, was?.number)
    }
}

/** A deck the user holds the cards of (or a proxy deck's real ones), samples aside. */
private fun holdsCards(d: Deck): Boolean = d.sample != true && (d.ownershipType == DeckOwnership.PHYSICAL || d.ownershipType == DeckOwnership.PROXY)

/**
 * The copies [goal] counts of each card it could want, by its card key: owned binders, boxes and
 * the Unsorted pile (wishlists never), and with countDecks the real copies in decks held. A foil goal
 * counts foil copies only — in a deck, those its pull list brought in foil.
 */
fun goalCounts(goal: CollectionGoal, collections: List<Collection>, decks: List<Deck>): Map<String, Int> {
    val foil = goal.isFoil
    val out = HashMap<String, Int>()
    fun add(k: String, n: Int) { if (n > 0) out.merge(k, n, Int::plus) }
    for (c in collections) {
        if (c.kind == CollectionType.WISHLIST) continue
        for (e in c.entries) add(goalCardKey(goal.kind, e.name, e.scryfallId), if (foil) e.foilQuantity else e.quantity + e.foilQuantity)
    }
    if (goal.countsDecks) {
        for (d in decks) {
            if (!holdsCards(d)) continue
            // The foil copies the deck's pull list brought in, by name, handed out to its cards in order.
            val foils = HashMap<String, Int>()
            if (foil) for (f in d.cameFrom.orEmpty()) if (f.foil == true) foils.merge(goalNameKey(f.name), f.qty, Int::plus)
            for (e in d.cards) {
                val real = e.quantity - proxyCopies(d, e)
                if (real <= 0) continue
                if (!foil) { add(goalCardKey(goal.kind, e.name, e.scryfallId), real); continue }
                val left = foils[goalNameKey(e.name)] ?: 0
                val take = minOf(real, left)
                if (take > 0) { foils[goalNameKey(e.name)] = left - take; add(goalCardKey(goal.kind, e.name, e.scryfallId), take) }
            }
        }
    }
    return out
}

private fun cents(n: Double): Double = Math.round(n * 100) / 100.0

/** A copy's price for [foil]: the foil price, else the plain one — each falling back to the other. */
private fun priceFor(usd: Double?, usdFoil: Double?, foil: Boolean): Double? = if (foil) usdFoil ?: usd else usd ?: usdFoil

/** How far [goal] has got: each card's copies, the totals, what's missing and what that would cost. */
fun goalProgress(goal: CollectionGoal, collections: List<Collection>, decks: List<Deck>, prices: Map<String, GoalPrice> = emptyMap()): GoalProgress {
    val counts = goalCounts(goal, collections, decks)
    val foil = goal.isFoil
    val lines = mutableListOf<GoalLine>()
    val seen = HashMap<String, Int>()
    for (c in goalTargets(goal, decks)) {
        val key = goalCardKey(goal.kind, c.name, c.scryfallId)
        val i = seen[key]
        if (i != null) { lines[i] = lines[i].copy(need = lines[i].need + c.qty); continue }
        seen[key] = lines.size
        val known = c.scryfallId?.let { prices[it] }
        val usd = if (known != null) priceFor(known.usd, known.usdFoil, foil) ?: priceFor(c.usd, c.usdFoil, foil) else priceFor(c.usd, c.usdFoil, foil)
        lines += GoalLine(key, c.name, c.scryfallId, c.imageUrl, c.rarity, c.number, need = c.qty, owned = counts[key] ?: 0, usd = usd)
    }
    var have = 0
    var need = 0
    var missingUsd = 0.0
    var unpriced = 0
    val done = lines.map { l ->
        val h = minOf(l.owned, l.need)
        val missing = l.need - h
        have += h
        need += l.need
        if (missing > 0) { if (l.usd != null) missingUsd += missing * l.usd else unpriced += missing }
        l.copy(have = h, missing = missing)
    }
    return GoalProgress(
        have = have,
        need = need,
        percent = if (need <= 0) 0 else minOf(100, have * 100 / need),
        missingUsd = cents(missingUsd),
        unpriced = unpriced,
        complete = need > 0 && have >= need,
        lines = done
    )
}

/** The cards still missing, in the goal's order. */
fun missingLines(p: GoalProgress): List<GoalLine> = p.lines.filter { it.missing > 0 }

/** "41/92 · 44%" */
fun progressLine(p: GoalProgress): String = "${p.have}/${p.need} · ${p.percent}%"

/** The line under a goal's bar: "51 missing · about $38.20" — or "Complete". [fmt] writes a US dollar amount in the user's currency. */
fun missingLine(p: GoalProgress, fmt: (Double) -> String): String {
    if (p.complete) return "Complete"
    if (p.need <= 0) return "No cards yet"
    val n = p.need - p.have
    val value = if (p.missingUsd > 0) " · about ${fmt(p.missingUsd)}" + (if (p.unpriced > 0) " and more" else "") else ""
    return "$n missing$value"
}

// ---- Completed ----

/** Goals still open, most nearly done first (then by name); then the completed ones, most recent first. */
fun sortedGoals(goals: List<CollectionGoal>, progress: (CollectionGoal) -> GoalProgress): Pair<List<CollectionGoal>, List<CollectionGoal>> {
    val open = goals.filter { it.completedAt == null }
        .map { it to progress(it) }
        .sortedWith(compareByDescending<Pair<CollectionGoal, GoalProgress>> { it.second.percent }.thenByDescending { it.second.have }.thenBy { it.first.name.lowercase() })
        .map { it.first }
    val done = goals.filter { it.completedAt != null }.sortedByDescending { it.completedAt ?: 0L }
    return open to done
}

/** Goals complete now and not before: the goals with each marked complete, and their ids (for the celebration). */
data class Completed(val goals: List<CollectionGoal>, val done: List<String>)

/**
 * Goals that are complete now and weren't before: [goals] with each marked complete at [now], and
 * their ids. A goal already marked stays as it is; a goal with nothing to get never completes. The
 * change doesn't count as an edit (updatedAt stays), so it never outweighs one.
 */
fun completeGoals(goals: List<CollectionGoal>, collections: List<Collection>, decks: List<Deck>, now: Long): Completed {
    val done = mutableListOf<String>()
    val next = goals.map { g ->
        if (g.completedAt != null || !goalProgress(g, collections, decks).complete) g
        else { done += g.id; g.copy(completedAt = now) }
    }
    return Completed(if (done.isEmpty()) goals else next, done)
}

// ---- The scanner ----

/** A goal a scanned card moves on: the goal's progress with that copy in. */
data class GoalHit(val id: String, val name: String, val have: Int, val need: Int)

/**
 * The open goals a scanned card would move on: [pending] copies of it (this one included) are on
 * their way into the collection, and the last of them still fills a gap. Each with its progress once
 * those copies are in. A foil goal moves only for a foil copy.
 */
fun goalHits(goals: List<CollectionGoal>, collections: List<Collection>, decks: List<Deck>, scryfallId: String, name: String, foil: Boolean, pending: Int): List<GoalHit> {
    val out = mutableListOf<GoalHit>()
    for (g in goals) {
        if (g.completedAt != null) continue
        if (g.isFoil && !foil) continue
        val key = goalCardKey(g.kind, name, scryfallId)
        val p = goalProgress(g, collections, decks)
        val line = p.lines.firstOrNull { it.key == key } ?: continue
        if (line.owned + pending - 1 >= line.need) continue
        out += GoalHit(g.id, g.name, p.have + minOf(pending, line.need - line.have), p.need)
    }
    return out
}

/** "Goal: Duskmourn uncommons 41/92" */
fun hitLine(h: GoalHit): String = "Goal: ${h.name} ${h.have}/${h.need}"

// ---- Wishlist and trades ----

/** A missing card for the Wishlist: [quantity] copies wanted. */
data class GoalWant(val scryfallId: String, val name: String, val imageUrl: String?, val quantity: Int)

/**
 * What "Add missing to Wishlist" adds: each missing card once by name (its printings' missing copies
 * together), as many copies as are missing — only where the Wishlist doesn't already want that many.
 */
fun goalWishlistAdds(p: GoalProgress, wishlist: List<CollectionEntry>): List<GoalWant> {
    val wanted = HashMap<String, Int>()
    for (e in wishlist) wanted[goalNameKey(e.name)] = maxOf(wanted[goalNameKey(e.name)] ?: 0, e.quantity)
    val out = LinkedHashMap<String, GoalWant>()
    for (l in missingLines(p)) {
        val k = goalNameKey(l.name)
        val was = out[k]
        out[k] = was?.copy(quantity = was.quantity + l.missing) ?: GoalWant(l.scryfallId ?: "", l.name, l.imageUrl, l.missing)
    }
    return out.values.filter { it.quantity > (wanted[goalNameKey(it.name)] ?: 0) }
}

/** The missing cards' names, once each — what to ask friends for. */
fun missingNames(p: GoalProgress): List<String> = missingLines(p).associateBy({ goalNameKey(it.name) }, { it.name }).values.toList()

// ---- Kept and synced ----

/** The user's goals, kept on the Unsorted pile. */
fun goalsOf(collections: List<Collection>): List<CollectionGoal> = collections.firstOrNull { it.isUnsorted }?.collectionGoals.orEmpty()

/** [collections] with the goals set to [goals] (on the Unsorted pile, made if it isn't there). */
fun withGoals(collections: List<Collection>, goals: List<CollectionGoal>): List<Collection> =
    withUnsortedPile(collections).map { if (it.isUnsorted) it.copy(collectionGoals = goals.map { g -> collectionGoal(g) }) else it }

/** [collections] with [goal] added, or put in place of the one with its id. */
fun saveGoal(collections: List<Collection>, goal: CollectionGoal): List<Collection> {
    val list = goalsOf(collections)
    return withGoals(collections, if (list.any { it.id == goal.id }) list.map { if (it.id == goal.id) goal else it } else list + goal)
}

fun deleteGoal(collections: List<Collection>, id: String): List<Collection> = withGoals(collections, goalsOf(collections).filter { it.id != id })

/** The earlier of two completions; either may be missing. */
private fun firstDone(a: Long?, b: Long?): Long? = if (a == null) b else if (b == null) a else minOf(a, b)

/**
 * Merges two devices' goals: one added on either side is kept, one deleted on either side stays
 * deleted, and one changed on both goes to the more recent change (updatedAt; a tie to
 * [minePreferred]'s side) — as a whole, but completed if either side completed it (at the earlier
 * time). Null when no side has any.
 */
fun mergeGoals(base: List<CollectionGoal>?, mine: List<CollectionGoal>?, theirs: List<CollectionGoal>?, minePreferred: Boolean): List<CollectionGoal>? {
    if (base == null && mine == null && theirs == null) return null
    val b = base.orEmpty().associateBy { it.id }
    val m = mine.orEmpty().associateBy { it.id }
    val t = theirs.orEmpty().associateBy { it.id }
    val added = (t.keys + m.keys).filter { it !in b }.distinct().sorted()
    val out = mutableListOf<CollectionGoal>()
    for (id in b.keys.toList() + added) {
        val bg = b[id]
        val mg = m[id]
        val tg = t[id]
        if (bg != null && (mg == null || tg == null)) continue
        val picked = when {
            mg == null || tg == null -> (tg ?: mg)!!
            mg == tg -> mg
            bg != null && mg == bg -> tg
            bg != null && tg == bg -> mg
            mg.updatedAt > tg.updatedAt -> mg
            tg.updatedAt > mg.updatedAt -> tg
            minePreferred -> mg
            else -> tg
        }
        out += collectionGoal(picked.copy(completedAt = firstDone(mg?.completedAt, tg?.completedAt)))
    }
    return out
}

/**
 * [theirs] with [source]'s goals, when [theirs] was saved by an app that doesn't know about goals
 * (no "collectionGoals" key) — the same object otherwise.
 */
fun keepGoalsFromOlderApp(source: Collection, theirs: Collection): Collection {
    if (theirs.collectionGoals != null || source.collectionGoals == null || !theirs.isUnsorted) return theirs
    return theirs.copy(collectionGoals = source.collectionGoals)
}
