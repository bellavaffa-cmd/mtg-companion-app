package com.mtgcompanion.app.data

/*
 * Gear: what the cards are kept and played with — sleeves (and inner sleeves for double-sleeving),
 * deck boxes, tokens, dice, playmats and anything else. Storage › Gear lists it; a deck's "This deck
 * needs" says whether there are sleeves, a deck box and the deck's tokens for it; packing for an event
 * (EventBag.kt) puts the tokens, dice and playmat in the bag.
 *
 * Where it's kept: the Unsorted pile's "gear" (Collection.gear), so it syncs with the library like the
 * storage places and loans do — library_items only holds decks and binders, and the pile is always
 * there with the same id on every device. Two devices' gear merges item by item ([mergeGear]): an item
 * added on either is kept, one deleted on either stays deleted, each field goes to whoever changed it,
 * and the decks using a pack of sleeves merge like a deck's tags. A pile saved by an app from before
 * gear comes without the key and keeps this device's ([keepGearFromOlderApp]).
 *
 *   "gear": [{ "id": "…", "kind": "SLEEVES", "name": "Black matte sleeves", "count": 38, "usedBy": ["<deck id>"], "createdAt": 1790000000000 },
 *            { "id": "…", "kind": "DECK_BOX", "name": "Red", "count": 1, "holds": "<deck id>", "createdAt": … },
 *            { "id": "…", "kind": "TOKENS", "name": "Goblin", "count": 24, "placeId": "<place id>", "createdAt": … }]
 *
 * Pure, so it can be tested. The web app's src/collection/gear.ts, rule for rule, with the same tests
 * (GearTest.kt ↔ tests/collection/gear.test.ts).
 */

/** The user's gear, kept on the Unsorted pile. */
fun gearOf(collections: List<Collection>): List<GearItem> = collections.firstOrNull { it.isUnsorted }?.gear.orEmpty()

/** [collections] with the gear set to [gear] (on the Unsorted pile, made if it isn't there). */
fun withGear(collections: List<Collection>, gear: List<GearItem>): List<Collection> =
    withUnsortedPile(collections).map { if (it.isUnsorted) it.copy(gear = gear) else it }

/** An item written as both apps write it: optional fields null when not set. */
fun gearItem(g: GearItem): GearItem {
    val usedBy = g.usedBy.orEmpty().filter { it.isNotEmpty() }.distinct()
    return GearItem(
        id = g.id,
        kind = GearKind.fromName(g.kind).name,
        name = g.name.trim(),
        count = maxOf(0, g.count),
        usedBy = usedBy.ifEmpty { null },
        holds = g.holds?.ifEmpty { null },
        placeId = g.placeId?.ifEmpty { null },
        note = g.note?.trim()?.ifEmpty { null },
        createdAt = g.createdAt
    )
}

/** [collections] with [item] added, or put in place of the one with its id. */
fun saveGear(collections: List<Collection>, item: GearItem): List<Collection> {
    val list = gearOf(collections)
    val clean = gearItem(item)
    return withGear(collections, if (list.any { it.id == item.id }) list.map { if (it.id == item.id) clean else it } else list + clean)
}

/** [collections] without the gear item [id]. */
fun deleteGear(collections: List<Collection>, id: String): List<Collection> {
    val list = gearOf(collections)
    return if (list.any { it.id == id }) withGear(collections, list.filter { it.id != id }) else collections
}

// ---- Names ----

/** What a deck is called in a short line: its commander's first name ("Krenko"), or the deck's name. */
fun shortDeckName(deck: Deck): String {
    val commander = deck.commander?.name?.trim()
    if (commander.isNullOrEmpty()) return deck.name
    return commander.split(',')[0].trim().ifEmpty { deck.name }
}

/** "Krenko", "Krenko and Atraxa", "Krenko, Atraxa and Rares binder". */
fun namesAnd(names: List<String>): String =
    if (names.size <= 1) names.firstOrNull().orEmpty()
    else "${names.dropLast(1).joinToString(", ")} and ${names.last()}"

/** What a deck, binder or place id is called, short; null when it's gone. */
fun usedByName(id: String, decks: List<Deck>, collections: List<Collection>): String? {
    decks.firstOrNull { it.id == id }?.let { return shortDeckName(it) }
    collections.firstOrNull { it.id == id }?.let { return it.name }
    return placesOf(collections).firstOrNull { it.id == id }?.name
}

// ---- Sleeves ----

/** How many sleeves [deck] takes: its commanders, its cards and its sideboard. */
fun sleevesFor(deck: Deck): Int =
    (if (deck.commander != null) 1 else 0) + (if (deck.partnerCommander != null) 1 else 0) +
        deck.cards.sumOf { maxOf(0, it.quantity) } + deck.sideboard.sumOf { maxOf(0, it.quantity) }

/** The most sleeves one of the decks using [item] takes — 0 when no deck uses them. */
fun deckNeedsOf(item: GearItem, decks: List<Deck>): Int =
    item.usedBy.orEmpty().mapNotNull { id -> decks.firstOrNull { it.id == id } }.maxOfOrNull { sleevesFor(it) } ?: 0

/** Sleeves running low: fewer left than one of the decks using them takes. */
fun runningLow(item: GearItem, decks: List<Deck>): Boolean {
    if (item.gearKind != GearKind.SLEEVES && item.gearKind != GearKind.INNER_SLEEVES) return false
    val need = deckNeedsOf(item, decks)
    return need > 0 && item.count < need
}

// ---- The Gear list ----

/** One row of the Gear list: a title, what it says on the right, the line under it, and whether to warn. */
data class GearRow(val key: String, val title: String, val value: String, val line: String, val warn: Boolean, val items: List<GearItem>)

private val gearByAge = compareBy<GearItem>({ it.createdAt }, { it.id })

/**
 * The Gear list as the screen shows it: each pack of sleeves and inner sleeves on its own, the deck
 * boxes as one row, the tokens as one row, then dice, playmats and the rest, each on its own.
 */
fun gearRows(gear: List<GearItem>, decks: List<Deck>, collections: List<Collection>): List<GearRow> {
    fun names(ids: List<String>?) = ids.orEmpty().mapNotNull { usedByName(it, decks, collections) }
    val sorted = gear.sortedWith(gearByAge)
    val rows = mutableListOf<GearRow>()
    for (g in sorted.filter { it.gearKind == GearKind.SLEEVES }) {
        val parts = mutableListOf<String>()
        val on = names(g.usedBy)
        if (on.isNotEmpty()) parts += "On ${namesAnd(on)}"
        val low = runningLow(g, decks)
        if (low) { parts += "a deck needs ${deckNeedsOf(g, decks)}"; parts += "running low" }
        rows += GearRow(g.id, g.name, "${g.count} left", parts.joinToString(" · ").ifEmpty { "Not on a deck yet" }, low, listOf(g))
    }
    for (g in sorted.filter { it.gearKind == GearKind.INNER_SLEEVES }) {
        val on = names(g.usedBy)
        val low = runningLow(g, decks)
        val parts = mutableListOf(if (on.isNotEmpty()) "Double-sleeving: ${on.joinToString(", ")}" else "Not double-sleeving anything yet")
        if (low) { parts += "a deck needs ${deckNeedsOf(g, decks)}"; parts += "running low" }
        rows += GearRow(g.id, g.name, "${g.count} left", parts.joinToString(" · "), low, listOf(g))
    }
    val boxes = sorted.filter { it.gearKind == GearKind.DECK_BOX }
    if (boxes.isNotEmpty()) {
        fun holding(b: GearItem) = b.holds?.let { usedByName(it, decks, collections) }
        val empty = boxes.count { holding(it) == null }
        rows += GearRow(
            "DECK_BOX", "Deck boxes", "${boxes.size}${if (empty > 0) " · $empty empty" else ""}",
            boxes.joinToString(", ") { "${it.name} (${holding(it) ?: "empty"})" }, false, boxes
        )
    }
    val tokens = sorted.filter { it.gearKind == GearKind.TOKENS }
    if (tokens.isNotEmpty()) rows += GearRow("TOKENS", "Tokens", tokens.sumOf { it.count }.toString(), tokensLine(tokens, collections), false, tokens)
    val places = placesOf(collections)
    for (kind in listOf(GearKind.DICE, GearKind.PLAYMAT, GearKind.OTHER)) {
        for (g in sorted.filter { it.gearKind == kind }) {
            val where = g.placeId?.let { id -> places.firstOrNull { it.id == id }?.name }
            rows += GearRow(g.id, g.name, g.count.toString(), listOfNotNull(kind.label, where, g.note).filter { it.isNotEmpty() }.joinToString(" · "), false, listOf(g))
        }
    }
    return rows
}

/** "Goblin ×24, Treasure ×18, Soldier ×12 and more · Token box". */
fun tokensLine(tokens: List<GearItem>, collections: List<Collection>): String {
    val sorted = tokens.sortedWith(compareByDescending<GearItem> { it.count }.thenBy { it.name.lowercase() })
    val shown = sorted.take(3).joinToString(", ") { "${it.name} ×${it.count}" }
    val places = placesOf(collections)
    val where = sorted.mapNotNull { t -> places.firstOrNull { it.id == t.placeId }?.name }.distinct()
    return shown + (if (sorted.size > 3) " and more" else "") + (if (where.isNotEmpty()) " · ${where.joinToString(", ")}" else "")
}

/** Under Gear on the Storage tab: what's running low, or what there is ("Sleeves, deck boxes and tokens"). */
fun gearSummary(gear: List<GearItem>, decks: List<Deck>): String {
    val low = gear.count { runningLow(it, decks) }
    if (low > 0) return "$low ${if (low == 1) "pack" else "packs"} of sleeves running low"
    val labels = mapOf(
        GearKind.SLEEVES to "sleeves", GearKind.INNER_SLEEVES to "inner sleeves", GearKind.DECK_BOX to "deck boxes", GearKind.TOKENS to "tokens",
        GearKind.DICE to "dice", GearKind.PLAYMAT to "playmats", GearKind.OTHER to "more"
    )
    val kinds = GearKind.entries.filter { k -> gear.any { it.gearKind == k } }.map { labels.getValue(it) }
    val line = namesAnd(kinds.ifEmpty { listOf("sleeves", "deck boxes", "tokens", "dice") })
    return line.replaceFirstChar { it.uppercase() }
}

// ---- This deck needs ----

/** Two token names are the same token: "Goblin" and "goblin", "Goblin token" and "Goblin". */
fun sameToken(a: String, b: String): Boolean {
    fun clean(s: String) = s.trim().lowercase().replace(Regex("\\s+tokens?$"), "")
    return clean(a) == clean(b)
}

data class DeckNeeds(
    val sleeves: Int,
    /** Sleeved already (a pack says it's on the deck), or a pack with enough left. */
    val hasSleeves: Boolean,
    /** A deck box holds it. */
    val hasBox: Boolean,
    /** An empty deck box it could go in. */
    val emptyBox: String?,
    val tokens: List<String>,
    val missingTokens: List<String>
)

/** What [deck] needs from the gear: sleeves, a deck box and [tokens] (the names of the tokens its cards make). */
fun deckNeeds(deck: Deck, gear: List<GearItem>, tokens: List<String>, decks: List<Deck>, collections: List<Collection>): DeckNeeds {
    val sleeves = sleevesFor(deck)
    val packs = gear.filter { it.gearKind == GearKind.SLEEVES }
    val hasSleeves = packs.any { deck.id in it.usedBy.orEmpty() } || packs.any { it.count >= sleeves }
    val boxes = gear.filter { it.gearKind == GearKind.DECK_BOX }
    val hasBox = boxes.any { it.holds == deck.id }
    val empty = boxes.firstOrNull { b -> b.holds == null || usedByName(b.holds, decks, collections) == null }
    val have = gear.filter { it.gearKind == GearKind.TOKENS && it.count > 0 }
    val unique = tokens.filterIndexed { i, t -> tokens.indexOfFirst { sameToken(it, t) } == i }
    return DeckNeeds(
        sleeves, hasSleeves, hasBox, if (hasBox) null else empty?.name,
        unique, unique.filter { t -> have.none { sameToken(it.name, t) } }
    )
}

/** "Goblin tokens", "Goblin and Treasure tokens", "Goblin, Treasure and 2 more tokens". */
fun tokenNames(names: List<String>): String =
    if (names.size <= 3) "${namesAnd(names)} tokens"
    else "${names.take(2).joinToString(", ")} and ${names.size - 2} more tokens"

/**
 * "Krenko goblins: 100 sleeves, a deck box and Goblin tokens. You have them all." — or what's missing:
 * "… Missing: 100 sleeves and Goblin tokens. Blue is an empty deck box."
 */
fun deckNeedsLine(deck: Deck, needs: DeckNeeds): String {
    val all = listOf("${needs.sleeves} sleeves", "a deck box") + (if (needs.tokens.isNotEmpty()) listOf(tokenNames(needs.tokens)) else emptyList())
    val missing = (if (needs.hasSleeves) emptyList() else listOf("${needs.sleeves} sleeves")) +
        (if (needs.hasBox) emptyList() else listOf("a deck box")) +
        (if (needs.missingTokens.isNotEmpty()) listOf(tokenNames(needs.missingTokens)) else emptyList())
    val head = "${deck.name}: ${namesAnd(all)}."
    if (missing.isEmpty()) return "$head You have them all."
    return "$head Missing: ${namesAnd(missing)}." + (needs.emptyBox?.let { " $it is an empty deck box." } ?: "")
}

// ---- Two devices ----

private fun <T> pickGear(base: T, mine: T, theirs: T, minePreferred: Boolean): T = when {
    mine == theirs -> mine
    mine == base -> theirs
    theirs == base -> mine
    minePreferred -> mine
    else -> theirs
}

private fun mergeIdSet(base: List<String>, mine: List<String>, theirs: List<String>): List<String> {
    val out = mutableListOf<String>()
    for (id in theirs + mine) {
        if (id in out) continue
        if (id in base && (id !in mine || id !in theirs)) continue
        out += id
    }
    return out
}

/**
 * Merges two devices' gear: one added on either side is kept, one deleted on either side stays
 * deleted, each field goes to whoever changed it (the more recent edit when both did), and the decks
 * using a pack merge both sides' additions minus what either took off. Null when no side has it.
 */
fun mergeGear(base: List<GearItem>?, mine: List<GearItem>?, theirs: List<GearItem>?, minePreferred: Boolean): List<GearItem>? {
    if (base == null && mine == null && theirs == null) return null
    val b = base.orEmpty().associateBy { it.id }
    val m = mine.orEmpty().associateBy { it.id }
    val t = theirs.orEmpty().associateBy { it.id }
    val added = (t.keys + m.keys).filter { it !in b }.distinct().sorted()
    val out = mutableListOf<GearItem>()
    for (id in b.keys.toList() + added) {
        val bg = b[id]
        val mg = m[id]
        val tg = t[id]
        if (bg != null && (mg == null || tg == null)) continue
        if (bg == null) { out += (tg ?: mg!!); continue }
        out += gearItem(
            GearItem(
                id = id,
                kind = pickGear(bg.kind, mg!!.kind, tg!!.kind, minePreferred),
                name = pickGear(bg.name, mg.name, tg.name, minePreferred),
                count = pickGear(bg.count, mg.count, tg.count, minePreferred),
                usedBy = mergeIdSet(bg.usedBy.orEmpty(), mg.usedBy.orEmpty(), tg.usedBy.orEmpty()),
                holds = pickGear(bg.holds, mg.holds, tg.holds, minePreferred),
                placeId = pickGear(bg.placeId, mg.placeId, tg.placeId, minePreferred),
                note = pickGear(bg.note, mg.note, tg.note, minePreferred),
                createdAt = minOf(mg.createdAt, tg.createdAt)
            )
        )
    }
    return out
}

/**
 * [theirs] with [source]'s gear, when [theirs] was saved by an app that doesn't know about gear (no
 * "gear" key) — the same object otherwise.
 */
fun keepGearFromOlderApp(source: Collection, theirs: Collection): Collection {
    if (theirs.gear != null || source.gear == null || !theirs.isUnsorted) return theirs
    return theirs.copy(gear = source.gear)
}
