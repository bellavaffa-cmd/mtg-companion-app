package com.mtgcompanion.app.data

/*
 * Copies moving between binders and decks, keeping binders as views (BinderViews.kt) true:
 *  - Into a deck out of a binder (sent from it, a proxy swapped for it, pulled with the pull list):
 *    the copies leave the binder as before, and the deck's card is sorted into that binder
 *    (DeckCardEntry.binders += { binder, n, foil }) — so the binder still lists it, "In Talrand deck".
 *    Where they were kept goes on the deck (Deck.cameFrom). Out of No binder: no ref, the place noted.
 *  - Out of a deck (its count down, the card taken out, the deck deleted keeping its cards, or no
 *    longer counting its cards as owned): the copies in no binder leave first, back to No binder; then
 *    the ones sorted into a binder, the last ref first, back into THAT binder as its own loose copies —
 *    a binder that's gone, No binder — each to the spot it came from (Deck.cameFrom) while that place
 *    is still there. The card's refs are cut to what it still holds.
 *  - A binder deleted: its own copies go with it; deck copies stay in their decks, now in No binder.
 * No copy is made or lost: what's owned and what it's worth (OwnedCopies.kt) never change, and the
 * views still add up to All cards' real copies. Mirrors the web app's src/collection/binderFlows.ts
 * (shared cases: returnToBinderVectors.json).
 */

private fun nameKey(name: String) = name.trim().lowercase()

/** The real copies [deck] holds that are the user's own, all its cards together (ownedCopies). */
fun heldCopies(deck: Deck): Int = deck.cards.sumOf { ownedCopies(deck, it) }

/** [refs] with [add] put in at the end: the same binder's counts added up. */
fun withRef(refs: List<BinderRef>, add: BinderRef): List<BinderRef> {
    if (add.qty <= 0) return refs
    val at = refs.indexOfFirst { it.id == add.id }
    if (at < 0) return refs + add
    return refs.mapIndexed { i, r -> if (i == at) binderRef(r.id, r.qty + add.qty, r.foils + add.foils) else r }
}

/** [deck] with each of the cards [ids] (all, when null) whose binders are set tidied to the copies it holds (assignedCopies); the same deck when they are. */
fun tidiedRefs(deck: Deck, ids: Set<String>? = null): Deck {
    var changed = false
    val cards = deck.cards.map { e ->
        if ((ids != null && e.scryfallId !in ids) || e.binders == null) return@map e
        val tidy = assignedCopies(deck, e)
        if (tidy == e.binders) e else { changed = true; e.copy(binders = tidy) }
    }
    return if (changed) deck.copy(cards = cards) else deck
}

/**
 * Whether copies sent out of a binder into a deck's main list leave the binder: only into a deck whose
 * cards count as owned (Physical or Proxy, not a cube or a sample). Any other deck only lists the card,
 * the copies staying where they are.
 */
fun copiesMoveInto(deck: Deck?): Boolean = deck?.countsAsOwned ?: true

/**
 * [deck] with [entry]'s copies — [entry].quantity of them, [foil] of those foil — come out of the
 * collection [from]: added to the main deck (to the card's entry when it has one), as real copies even
 * in a Proxy deck; sorted into [from] when it's a binder deck copies can be sorted into ([valid]) —
 * never No binder or a wishlist; and [came], where they were kept, noted on the deck (Deck.cameFrom).
 * A deck whose cards don't count as owned just gets the card.
 */
fun withCopiesFromBinder(deck: Deck, entry: DeckCardEntry, from: String?, foil: Int, came: List<CameFrom>, valid: Set<String>): Deck {
    val n = entry.quantity
    if (n <= 0) return deck
    val existing = deck.cards.firstOrNull { it.scryfallId == entry.scryfallId }
    val owned = deck.countsAsOwned
    val base = existing ?: entry.copy(quantity = 0, binders = null, proxyQuantity = null)
    // In a Proxy deck a card is all proxies unless it says otherwise: these copies are real.
    val proxies = if (owned && deck.ownershipType == DeckOwnership.PROXY) proxyCopies(deck, base) else null
    var next = base.copy(quantity = base.quantity + n, proxyQuantity = proxies ?: base.proxyQuantity)
    if (owned && from != null && from in valid) {
        val current = existing?.let { assignedCopies(deck, it) }.orEmpty()
        next = next.copy(binders = withRef(current, binderRef(from, n, foil)))
    }
    val cards = if (existing != null) deck.cards.map { if (it.scryfallId == entry.scryfallId) next else it } else deck.cards + next
    val cameFrom = if (owned && came.any { it.qty > 0 }) tidyCameFrom(deck.cameFrom.orEmpty() + came) else deck.cameFrom
    return tidiedRefs(deck.copy(cards = cards, cameFrom = cameFrom), setOf(entry.scryfallId))
}

/** A card coming out of a binder into a deck: [entry] with its count, [foil] of them foil, and where they were kept ([came]). */
data class FromBinder(val entry: DeckCardEntry, val foil: Int, val came: List<CameFrom>)

/** Where [entry]'s [plain] and [foil] copies going out of their binder were kept, as cameFrom lines (splitPlaces: no place first, then the last). */
fun cameFromOf(entry: CollectionEntry, plain: Int, foil: Int): List<CameFrom> =
    splitPlaces(entry, plain, foil).second.map { cameFromLine(entry.name, it) }

/** Copies of a deck card going back into one collection ([collectionId]; the Unsorted pile is No binder), and the spots they go to. */
data class CopiesBack(
    val collectionId: String,
    val entry: DeckCardEntry,
    val plain: Int,
    val foil: Int,
    /** Where some of them go (CopyPlace lines, foil ones marked); the rest have no place. */
    val spots: List<CopyPlace>
) {
    val copies: Int get() = plain + foil
}

/** What [returnCopies] did. */
data class ReturnResult(
    val collections: List<Collection>,
    /** The deck after, its cards' binders and cameFrom brought up to date; null when it's gone. */
    val deck: Deck?,
    val back: List<CopiesBack>,
    /** Each card's refs left, before they're cut to the deck after (applyReturned does that). */
    val refs: Map<String, List<BinderRef>>,
    /** The cameFrom lines the copies took with them. */
    val used: List<CameFrom>
) {
    /** Copies back in the collection. */
    val copies: Int get() = back.sumOf { it.copies }
}

/** [deck] after [before] with the main-deck card [scryfallId] at [quantity] copies (gone at 0). */
fun withCardCount(before: Deck, scryfallId: String, quantity: Int): Deck =
    if (quantity <= 0) before.copy(cards = before.cards.filterNot { it.scryfallId == scryfallId })
    else before.copy(cards = before.cards.map { if (it.scryfallId == scryfallId) it.copy(quantity = quantity) else it })

/**
 * The deck going from [before] to [after] (null: deleted, its cards kept): each card's owned copies
 * it no longer holds go back into the collection — the ones in no binder first, to No binder; then
 * the ones sorted into a binder, the last ref first, back into that binder (No binder when it's gone),
 * plain copies before foil. Each takes a spot it came from (Deck.cameFrom, the last line first): copies
 * going back to a binder first, then those going to No binder — only lines more than the copies still
 * sorted into a binder can claim. A spot in a place that's gone is no place.
 */
fun returnCopies(collections: List<Collection>, before: Deck, after: Deck?): ReturnResult {
    val valid = sortingBinderIds(collections)
    val placeIds = placesOf(collections).mapTo(HashSet()) { it.id }

    class Chunk(val entry: DeckCardEntry, val to: String, var plain: Int, var foil: Int, val bound: Boolean) {
        val lines = mutableListOf<CameFrom>()
    }
    val chunks = mutableListOf<Chunk>()
    val refs = LinkedHashMap<String, List<BinderRef>>()
    for (e in before.cards) {
        val owned = ownedCopies(before, e)
        val e2 = after?.cards?.firstOrNull { it.scryfallId == e.scryfallId }
        val stays = if (after == null || e2 == null) 0 else ownedCopies(after, e2)
        val leave = owned - stays
        if (leave <= 0) continue
        val left = assignedCopies(before, e).toMutableList()
        val loose = minOf(leave, owned - left.sumOf { it.qty })
        if (loose > 0) chunks += Chunk(e, UNSORTED_COLLECTION_ID, loose, 0, false)
        var rest = leave - loose
        for (i in left.indices.reversed()) {
            if (rest <= 0) break
            val r = left[i]
            val take = minOf(rest, r.qty)
            val foil = (take - (r.qty - r.foils)).coerceAtLeast(0)
            rest -= take
            left[i] = binderRef(r.id, r.qty - take, r.foils - foil)
            chunks += Chunk(e, if (r.id in valid) r.id else UNSORTED_COLLECTION_ID, take - foil, foil, true)
        }
        if (e.binders != null) refs[e.scryfallId] = left.filter { it.qty > 0 }
    }

    // Where they were kept: the deck's cameFrom, the last line first.
    val pool = before.cameFrom.orEmpty().toMutableList()
    val used = mutableListOf<CameFrom>()
    fun claim(c: Chunk, foil: Boolean?, want: Int): Int {
        var got = 0
        for (i in pool.indices.reversed()) {
            if (got >= want) break
            val l = pool[i]
            if (l.qty <= 0 || !sameCardName(l.name, c.entry.name) || (foil != null && (l.foil == true) != foil)) continue
            val take = minOf(l.qty, want - got)
            pool[i] = l.copy(qty = l.qty - take)
            val line = l.copy(qty = take)
            used += line
            c.lines += line
            got += take
            // A copy in no binder that came out of a foil spot goes back a foil.
            if (foil == null && l.foil == true) { c.plain -= take; c.foil += take }
        }
        return got
    }
    for (c in chunks) if (c.bound) { claim(c, true, c.foil); claim(c, false, c.plain) }
    // A line the copies still sorted into a binder could have come with stays theirs.
    val staying = HashMap<String, Int>()
    if (after != null) for (e2 in after.cards) {
        val kept = assignedCopies(after, e2.copy(binders = refs[e2.scryfallId] ?: e2.binders)).sumOf { it.qty }
        if (kept > 0) staying[nameKey(e2.name)] = (staying[nameKey(e2.name)] ?: 0) + kept
    }
    for (c in chunks) if (!c.bound) {
        val lines = pool.filter { it.qty > 0 && sameCardName(it.name, c.entry.name) }.sumOf { it.qty }
        val spare = lines - (staying[nameKey(c.entry.name)] ?: 0)
        if (spare > 0) claim(c, null, minOf(spare, c.plain))
    }

    var cols = if (chunks.any { it.to == UNSORTED_COLLECTION_ID && it.plain + it.foil > 0 }) withUnsortedPile(collections) else collections
    val back = mutableListOf<CopiesBack>()
    for (c in chunks) {
        if (c.plain + c.foil <= 0) continue
        val spots = c.lines.filter { it.placeId in placeIds }.map { copyPlace(it.line.spot, it.qty, it.foil == true) }
        back += CopiesBack(c.to, c.entry, c.plain, c.foil, spots)
        cols = cols.map { col -> if (col.id != c.to) col else col.copy(entries = intoCollection(col.entries, back.last())) }
    }
    val result = ReturnResult(cols, null, back, refs, used)
    return result.copy(deck = after?.let { applyReturned(it, result) })
}

/** [entries] with [back]'s copies put in, at their spots: added to the printing's entry when there is one. */
private fun intoCollection(entries: List<CollectionEntry>, back: CopiesBack): List<CollectionEntry> {
    val at = entries.indexOfFirst { it.scryfallId == back.entry.scryfallId }
    var e = if (at >= 0) entries[at].let { tidied(it.copy(quantity = it.quantity + back.plain, foilQuantity = it.foilQuantity + back.foil)) }
    else pileEntryOf(back.entry, back.plain).copy(foilQuantity = back.foil)
    for (s in back.spots) e = placeCopies(e, s.spot, s.qty, s.isFoil).first
    return if (at >= 0) entries.mapIndexed { i, x -> if (i == at) e else x } else entries + e
}

/**
 * [deck] (as it is now) with what [r] did: its cards' binders cut to what's left of them and to the
 * copies they hold, and the cameFrom lines the copies took gone.
 */
fun applyReturned(deck: Deck, r: ReturnResult): Deck {
    val cards = deck.cards.map { e -> r.refs[e.scryfallId]?.let { e.copy(binders = it) } ?: e }
    val cameFrom = if (deck.cameFrom == null || r.used.isEmpty()) deck.cameFrom else withoutLines(deck.cameFrom, r.used)
    val next = if (cards == deck.cards && cameFrom == deck.cameFrom) deck else deck.copy(cards = cards, cameFrom = cameFrom)
    return tidiedRefs(next, r.refs.keys)
}

/** [lines] less [used], line by line (the same card, spot and finish). */
private fun withoutLines(lines: List<CameFrom>, used: List<CameFrom>): List<CameFrom> {
    val out = lines.toMutableList()
    for (u in used) {
        var want = u.qty
        for (i in out.indices.reversed()) {
            if (want <= 0) break
            val l = out[i]
            if (l.qty <= 0 || !sameCardName(l.name, u.name) || copyKey(l.line) != copyKey(u.line)) continue
            val take = minOf(want, l.qty)
            out[i] = l.copy(qty = l.qty - take)
            want -= take
        }
    }
    return tidyCameFrom(out)
}

/** Undo of [returnCopies]'s copies back in the collection: [back] taken out of [collections] again, spots and all. */
fun takeBack(collections: List<Collection>, back: List<CopiesBack>): List<Collection> {
    var cols = collections
    for (b in back) {
        cols = cols.map { c ->
            if (c.id != b.collectionId) c
            else c.copy(entries = c.entries.mapNotNull { e ->
                if (e.scryfallId != b.entry.scryfallId) return@mapNotNull e
                var x = e
                for (s in b.spots) x = moveCopies(x, s, null, s.qty).first
                x = tidied(x.copy(quantity = (x.quantity - b.plain).coerceAtLeast(0), foilQuantity = (x.foilQuantity - b.foil).coerceAtLeast(0)))
                x.takeIf { it.quantity + it.foilQuantity > 0 }
            })
        }
    }
    return cols
}

/**
 * Where a deck card's owned copies go when the put-back list takes the deck apart (takeApart), in the
 * order its rows take them: each ref's, the last first, to its binder (No binder when [valid] doesn't
 * have it) — so the rows going back to where a card came from go to its binder — then the ones in no
 * binder, to No binder.
 */
fun putBackOrder(deck: Deck, entry: DeckCardEntry, valid: Set<String>): List<Pair<String, Int>> {
    val owned = ownedCopies(deck, entry)
    val refs = assignedCopies(deck, entry)
    val out = mutableListOf<Pair<String, Int>>()
    for (r in refs.reversed()) out += (if (r.id in valid) r.id else UNSORTED_COLLECTION_ID) to r.qty
    val loose = owned - refs.sumOf { it.qty }
    if (loose > 0) out += UNSORTED_COLLECTION_ID to loose
    return out
}

/** What's left of [entry] once [plain] and [foil] of its copies leave it for a deck (null: none), and where they were kept, as cameFrom lines. */
fun leftInBinder(entry: CollectionEntry, plain: Int, foil: Int): Pair<CollectionEntry?, List<CameFrom>> {
    val (staying, going) = splitPlaces(entry, plain, foil)
    val left = entry.copy(quantity = entry.quantity - plain, foilQuantity = entry.foilQuantity - foil, places = if (entry.places == null) null else staying)
    return left.takeIf { it.quantity + it.foilQuantity > 0 } to going.map { cameFromLine(entry.name, it) }
}

/**
 * [quantity] copies (plain first) of the card [scryfallId] in the collection [from] sent to [deck]'s
 * main list, as the binder screen's Move and Copy to a deck do: into a deck that holds its cards they
 * leave the collection and the deck's card is sorted into it (withCopiesFromBinder); any other deck
 * only gets the card. [deckEntry]: the deck's entry to add, made from the binder's when null.
 */
fun sendToDeck(collections: List<Collection>, deck: Deck, from: String, scryfallId: String, quantity: Int, deckEntry: DeckCardEntry? = null): Pair<List<Collection>, Deck> {
    val e = collections.firstOrNull { it.id == from }?.entries?.firstOrNull { it.scryfallId == scryfallId } ?: return collections to deck
    val plain = minOf(e.quantity, quantity.coerceAtLeast(0))
    val foil = minOf(e.foilQuantity, quantity.coerceAtLeast(0) - plain)
    if (plain + foil <= 0) return collections to deck
    val entry = (deckEntry ?: DeckCardEntry(e.scryfallId, e.name, e.imageUrl, backImageUrl = e.backImageUrl, tags = e.tags)).copy(quantity = plain + foil)
    if (!copiesMoveInto(deck)) return collections to withCopiesFromBinder(deck, entry, null, 0, emptyList(), emptySet())
    val (left, came) = leftInBinder(e, plain, foil)
    val cols = collections.map { c -> if (c.id != from) c else c.copy(entries = c.entries.mapNotNull { if (it === e) left else it }) }
    return cols to withCopiesFromBinder(deck, entry, from, foil, came, sortingBinderIds(collections))
}

/** "2 cards in decks stay in their decks." — said when a binder that lists deck copies is deleted; "" when none. */
fun deckCopiesStayLine(n: Int): String =
    if (n <= 0) "" else "${java.text.NumberFormat.getIntegerInstance(java.util.Locale.UK).format(n)} ${if (n == 1) "card" else "cards"} in decks stay in their decks."
