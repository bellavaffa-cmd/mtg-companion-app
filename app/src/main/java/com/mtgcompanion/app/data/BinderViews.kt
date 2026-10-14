package com.mtgcompanion.app.data

/*
 * Binders as views. A copy lives in one place: a binder owns its own copies, a physical or proxy deck
 * owns the copies it plays — what's owned and what it's worth never change here (OwnedCopies.kt). A
 * deck's card can say which binders its real copies belong in (DeckCardEntry.binders, BinderRefs.kt),
 * and a binder's VIEW is its own cards plus the deck copies sorted into it:
 *  - only decks whose cards count as owned (countsAsOwned: Physical and Proxy, never Virtual,
 *    Prototype, a cube or a sample), only main-deck cards, and only real copies — a proxy is never in a
 *    binder;
 *  - a card's refs are walked in order and clamped to its owned real copies (ownedCopies), so a deck
 *    cut from 3 copies to 1 shows 1 wherever it was;
 *  - owned deck copies with no binder, or sorted into one that's gone or is a wishlist, show in the
 *    No binder view (the Unsorted pile, shown as "No binder");
 *  - a friend sees a shared binder's own cards only (never these).
 * So every real copy shows in exactly one view: the views add up to All cards' real copies. Mirrors the
 * web app's src/collection/binderViews.ts (shared cases: tests/collection/binderViewVectors.json).
 */

/** What the Unsorted pile is called wherever it shows (its id and kind stay "unsorted"). */
const val NO_BINDER_TITLE = "No binder"

/** A binder's name as shown: the Unsorted pile is "No binder". */
val Collection.shownName: String get() = if (isUnsorted) NO_BINDER_TITLE else name

/** Whether [c] is a binder deck copies can be sorted into: one of the user's own, not the pile, not a wishlist. */
private fun sortsInto(c: Collection) = c.kind == CollectionType.OWNED && !c.isUnsorted && !c.isWishlist

/**
 * Which binders [entry]'s copies in [deck] are in, as far as it has real owned copies: the refs in
 * order, each cut down to the copies left, the same binder twice made one, none for a deck that
 * doesn't count as owned or a card with only proxies.
 */
fun assignedCopies(deck: Deck, entry: DeckCardEntry): List<BinderRef> {
    var left = ownedCopies(deck, entry)
    val refs = entry.binders
    if (left <= 0 || refs.isNullOrEmpty()) return emptyList()
    val out = ArrayList<BinderRef>()
    for (ref in refs) {
        if (left <= 0) break
        val take = minOf(ref.qty.coerceAtLeast(0), left)
        if (take <= 0) continue
        left -= take
        // A ref cut down keeps its foils: the plain copies are the ones that leave first (BinderFlows.kt).
        val foil = minOf(ref.foils, take)
        val at = out.indexOfFirst { it.id == ref.id }
        if (at >= 0) out[at] = binderRef(ref.id, out[at].qty + take, out[at].foils + foil) else out += binderRef(ref.id, take, foil)
    }
    return out
}

/** Some of a deck card's real copies, shown in a binder's view. */
data class DeckCopies(val deckId: String, val deckName: String, val entry: DeckCardEntry, val qty: Int)

/** Every deck copy that shows in a binder's view, by binder id, and the ones that show in No binder. */
class DeckCopiesIndex(val byBinder: Map<String, List<DeckCopies>>, val noBinder: List<DeckCopies>)

private class IndexCache(val collections: List<Collection>, val decks: List<Deck>, val index: DeckCopiesIndex)

@Volatile private var lastIndex: IndexCache? = null

/**
 * Where every owned deck copy shows, in one walk over the decks — decks in order, their cards in order.
 * The last answer is kept, so the Binders tab, a binder and the Cards tab asking about the same library
 * work it out once.
 */
fun deckCopiesByBinder(collections: List<Collection>, decks: List<Deck>): DeckCopiesIndex {
    lastIndex?.let { if (it.collections === collections && it.decks === decks) return it.index }
    val binders = collections.filter(::sortsInto).mapTo(HashSet()) { it.id }
    val byBinder = LinkedHashMap<String, MutableList<DeckCopies>>()
    val noBinder = ArrayList<DeckCopies>()
    for (d in decks) {
        if (!d.countsAsOwned) continue
        for (e in d.cards) {
            val owned = ownedCopies(d, e)
            if (owned <= 0) continue
            var sorted = 0
            for (ref in assignedCopies(d, e)) {
                if (ref.id !in binders) continue
                byBinder.getOrPut(ref.id) { ArrayList() } += DeckCopies(d.id, d.name, e, ref.qty)
                sorted += ref.qty
            }
            if (owned - sorted > 0) noBinder += DeckCopies(d.id, d.name, e, owned - sorted)
        }
    }
    return DeckCopiesIndex(byBinder, noBinder).also { lastIndex = IndexCache(collections, decks, it) }
}

/** A binder as it shows: its own cards ([own]) and the deck copies sorted into it ([deckCopies]). */
data class BinderView(val binderId: String, val own: List<CollectionEntry>, val deckCopies: List<DeckCopies>) {
    /** Its own copies. */
    val ownCopies: Int get() = own.sumOf { maxOf(0, it.quantity) + maxOf(0, it.foilQuantity) }
    /** Copies shown here that are in decks. */
    val inDecks: Int get() = deckCopies.sumOf { it.qty }
    /** Every copy it shows. */
    val total: Int get() = ownCopies + inDecks
    /** How many decks those are in. */
    val decks: Int get() = deckCopies.mapTo(HashSet()) { it.deckId }.size
}

/** The binder [binderId] as it shows: its cards and the deck copies sorted into it. */
fun binderView(binderId: String, collections: List<Collection>, decks: List<Deck>, index: DeckCopiesIndex = deckCopiesByBinder(collections, decks)): BinderView {
    val c = collections.firstOrNull { it.id == binderId }
    if (c?.isUnsorted == true) return unsortedView(collections, decks, index)
    val shows = c != null && sortsInto(c)
    return BinderView(binderId, c?.entries.orEmpty(), if (shows) index.byBinder[binderId].orEmpty() else emptyList())
}

/** No binder (the Unsorted pile) as it shows: its own cards, and the owned deck copies in no binder. */
fun unsortedView(collections: List<Collection>, decks: List<Deck>, index: DeckCopiesIndex = deckCopiesByBinder(collections, decks)): BinderView =
    BinderView(UNSORTED_COLLECTION_ID, collections.firstOrNull { it.isUnsorted }?.entries.orEmpty(), index.noBinder)

/** One printing's deck copies in a binder's view, from every deck that has them there. */
data class DeckCopyRow(val scryfallId: String, val name: String, val imageUrl: String?, val backImageUrl: String?, val copies: List<DeckCopies>) {
    val qty: Int get() = copies.sumOf { it.qty }
    val decks: List<Pair<String, String>> get() = copies.map { it.deckId to it.deckName }.distinct()
}

/** [copies] as one row a printing, A to Z. */
fun deckCopyRows(copies: List<DeckCopies>): List<DeckCopyRow> =
    copies.groupBy { it.entry.scryfallId }.map { (id, list) ->
        val e = list.first().entry
        DeckCopyRow(id, e.name, e.imageUrl, e.backImageUrl, list)
    }.sortedBy { it.name.lowercase() }

/** A deck copy row's badge: "In Talrand deck", "In 2 decks". */
fun inDecksBadge(decks: List<String>): String =
    if (decks.size == 1) "In ${deckTitle(decks[0])}" else "In ${decks.size} decks"

private fun n(x: Int) = java.text.NumberFormat.getIntegerInstance(java.util.Locale.UK).format(x)
private fun cards(x: Int) = "${n(x)} ${if (x == 1) "card" else "cards"}"

/** A binder row's count: "84 cards", "84 cards · 12 in decks". */
fun binderCountLine(view: BinderView): String =
    cards(view.total) + if (view.inDecks > 0) " · ${n(view.inDecks)} in decks" else ""

/** The No binder row's line: "12 cards, not in a binder", "40 cards · 28 in decks, not in a binder", "All sorted". */
fun noBinderLine(view: BinderView): String = when {
    view.total <= 0 -> "All sorted"
    view.inDecks > 0 -> "${cards(view.total)} · ${n(view.inDecks)} in decks, not in a binder"
    else -> "${cards(view.total)}, not in a binder"
}

/** keptInPlacesLine with the decks its copies are in: "kept in 3 places · 2 decks". */
fun keptInPlacesAndDecksLine(places: Int, cards: Int, decks: Int): String {
    val kept = keptInPlacesLine(places, cards)
    val inDecks = if (decks <= 0) "" else "${n(decks)} ${if (decks == 1) "deck" else "decks"}"
    return listOf(kept, inDecks).filter { it.isNotEmpty() }.joinToString(" · ")
}

// ---- Changing which binder a deck card's copies are in ----

/** [refs] tidied for [entry] in [deck]: the same binder made one, counts over the copies it owns cut, empties gone. */
private fun tidyRefs(deck: Deck, entry: DeckCardEntry, refs: List<BinderRef>): List<BinderRef> =
    assignedCopies(deck, entry.copy(binders = refs))

/** [deck] with [scryfallId]'s main-deck card's binders set to [refs] (tidied); the same deck when that's no change. */
fun withCardBinders(deck: Deck, scryfallId: String, refs: List<BinderRef>): Deck {
    val e = deck.cards.firstOrNull { it.scryfallId == scryfallId } ?: return deck
    val tidy = tidyRefs(deck, e, refs)
    if (e.binders == tidy) return deck
    return deck.copy(cards = deck.cards.map { if (it.scryfallId == scryfallId) it.copy(binders = tidy) else it })
}

/**
 * The refs once [qty] copies of the card move from binder [from] (null: from no binder — its copies in
 * none, or in one that's gone, [valid] being the binders there are) into binder [to]. [current] are its
 * refs as assignedCopies reads them.
 */
fun movedRefs(current: List<BinderRef>, from: String?, to: String?, qty: Int, valid: Set<String>): List<BinderRef> {
    var left = qty
    var foils = 0
    val out = ArrayList<BinderRef>()
    for (r in current) {
        val leaving = if (from == null) r.id !in valid else r.id == from
        if (leaving && left > 0) {
            val take = minOf(left, r.qty)
            left -= take
            // Plain copies first; a foil goes with them only when that's what's left.
            val foil = (take - (r.qty - r.foils)).coerceAtLeast(0)
            foils += foil
            if (r.qty - take > 0) out += binderRef(r.id, r.qty - take, r.foils - foil)
        } else out += r
    }
    val moved = qty - (if (from == null) 0 else left)
    if (to != null && moved > 0) {
        val at = out.indexOfFirst { it.id == to }
        if (at >= 0) out[at] = binderRef(to, out[at].qty + moved, out[at].foils + foils) else out += binderRef(to, moved, foils)
    }
    return out
}

/** The binders deck copies can be sorted into, by id. */
fun sortingBinderIds(collections: List<Collection>): Set<String> = collections.filter(::sortsInto).mapTo(HashSet()) { it.id }

/** The binders deck copies can be sorted into, A to Z. */
fun sortingBinders(collections: List<Collection>): List<Collection> = collections.filter(::sortsInto).sortedBy { it.name.lowercase() }

/** Whether [deck]'s cards can be put in binders: its cards count as owned (Physical or Proxy, not a cube or sample). */
val Deck.sortsIntoBinders: Boolean get() = countsAsOwned

/**
 * [deck] with every real copy that's in no binder (or one that's gone) put in [binderId], and how many
 * copies that was. A copy already in a binder stays where it is.
 */
fun allIntoBinder(deck: Deck, binderId: String, valid: Set<String>): Pair<Deck, Int> {
    if (!deck.countsAsOwned) return deck to 0
    var moved = 0
    val cards = deck.cards.map { e ->
        val owned = ownedCopies(deck, e)
        if (owned <= 0) return@map e
        val current = assignedCopies(deck, e)
        val sorted = current.filter { it.id in valid }.sumOf { it.qty }
        val loose = owned - sorted
        if (loose <= 0) return@map e
        moved += loose
        e.copy(binders = movedRefs(current, null, binderId, loose, valid))
    }
    return (if (moved == 0) deck else deck.copy(cards = cards)) to moved
}

/** "Binder: Rares ×1", "Binders: Rares ×1, Trade ×2"; null when in none. [names]: binder id -> name. */
fun cardBindersLine(refs: List<BinderRef>, names: Map<String, String>): String? {
    val shown = refs.filter { it.id in names }
    if (shown.isEmpty()) return null
    return (if (shown.size == 1) "Binder: " else "Binders: ") + shown.joinToString(", ") { "${names[it.id]} ×${n(it.qty)}" }
}

/**
 * The Cards tab's Group by Binder, by scryfallId: each binder showing copies of the printing — its own
 * or deck copies sorted into it — and No binder (the pile's own and deck copies in no binder) last.
 * Decks aren't groups here; they are in Group by Place.
 */
fun binderGroupIndex(collections: List<Collection>, decks: List<Deck>): Map<String, List<GroupKey>> {
    val out = HashMap<String, LinkedHashMap<String, GroupKey>>()
    fun add(id: String, key: GroupKey) { out.getOrPut(id) { LinkedHashMap() }.putIfAbsent(key.key, key) }
    val none = GroupKey(GROUP_NONE, NO_BINDER_TITLE)
    val names = HashMap<String, String>()
    for (c in collections) {
        if (c.kind != CollectionType.OWNED) continue
        names[c.id] = c.name
        for (e in c.entries) {
            if (e.quantity + e.foilQuantity <= 0) continue
            add(e.scryfallId, if (c.isUnsorted) none else GroupKey("binder:${c.id}", c.name))
        }
    }
    val index = deckCopiesByBinder(collections, decks)
    for ((id, list) in index.byBinder) for (d in list) add(d.entry.scryfallId, GroupKey("binder:$id", names[id].orEmpty()))
    for (d in index.noBinder) add(d.entry.scryfallId, none)
    return out.mapValues { it.value.values.toList() }
}
