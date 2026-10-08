package com.mtgcompanion.app.data

// Spoiler season: the cards revealed so far for a set coming out soon (or just out), built on New sets
// (NewSets.kt). Pure, so it can be tested. Mirrors the web app's src/collection/spoilers.ts, with the
// same tests (SpoilersTest.kt ↔ tests/collection/spoilers.test.ts).
//
//  - The countdown and how much of the set is revealed ("48 of 286 revealed").
//  - Wanting a revealed card: a normal Wishlist entry for that printing with [CollectionEntry.preRelease]
//    set to the set's release date. Before that day it shows "Releases in 5 days" and no price; from
//    that day on the date is taken off (withReleasedCleared) and the Wishlist's price targets apply.
//  - Which of the user's Commander decks a revealed card could go in (cardFits): in the commander's
//    colours, not in the deck already, legal in Commander once it's out (before that Scryfall can't
//    say), and sharing a theme, role or creature type the deck has plenty of (DeckProfile).
//  - Opening packs: the wanted cards from the set, ticked off as they're pulled (withPulled).
//  - The daily "new cards revealed that fit your decks" news (revealNews, mayTellReveals).

/** "Releases in 5 days", "Releases tomorrow", "Out today" — null once it's out (or with no date). */
fun releaseCountdown(releasedAt: String?, today: String): String? {
    if (releasedAt.isNullOrBlank()) return null
    val d = daysUntil(releasedAt, today)
    return when {
        d > 1 -> "Releases in $d days"
        d == 1L -> "Releases tomorrow"
        d == 0L -> "Out today"
        else -> null
    }
}

/**
 * How much of [set] Scryfall has: before release "48 of 286 revealed" (its printed size, when Scryfall
 * knows it) or "48 revealed"; "Nothing revealed yet"; once out, "286 cards".
 */
fun revealedLabel(set: SetInfo, today: String): String {
    val n = set.cardCount
    if (n <= 0) return "Nothing revealed yet"
    if (set.releasedAt.orEmpty() <= today) return "$n ${if (n == 1) "card" else "cards"}"
    val total = set.printedSize
    return if (total != null && total > 0 && n < total) "$n of $total revealed" else "$n revealed"
}

/** Whether [entry] is wanted from the spoilers and its set isn't out yet. */
fun isPreRelease(entry: CollectionEntry, today: String): Boolean = entry.preRelease.let { it != null && it > today }

/** "Releases in 5 days" for a Wishlist card wanted before its set is out; null otherwise (its price shows). */
fun preReleaseLabel(entry: CollectionEntry, today: String): String? =
    if (isPreRelease(entry, today)) releaseCountdown(entry.preRelease, today) else null

/** How many of the printing [scryfallId] the Wishlist wants. */
fun wantedCount(collections: List<Collection>, scryfallId: String): Int =
    collections.firstOrNull { it.isWishlist }?.entries?.firstOrNull { it.scryfallId == scryfallId }?.quantity ?: 0

private fun nameKey(name: String) = name.trim().lowercase()

/**
 * [collections] with [quantity] of the revealed [card] wanted on the Wishlist (made if needed): that
 * printing, the count as given (0 takes it off), wanted by hand. Before [releasedAt] it's flagged
 * pre-release with that date; once the set is out it's a plain Wishlist card.
 */
fun withSpoilerWant(collections: List<Collection>, card: SetCard, quantity: Int, releasedAt: String?, today: String): List<Collection> {
    val existing = collections.firstOrNull { it.isWishlist }
    val entries = existing?.entries.orEmpty()
    val had = entries.firstOrNull { it.scryfallId == card.id }
    val pre = releasedAt?.takeIf { it > today }
    val next = when {
        quantity <= 0 -> entries.filterNot { it.scryfallId == card.id }
        had != null -> entries.map { if (it.scryfallId != card.id) it else it.copy(quantity = quantity, auto = false, preRelease = pre ?: it.preRelease) }
        else -> entries + CollectionEntry(card.id, card.name, card.imageUrl, quantity = quantity, tags = card.tags, preRelease = pre)
    }
    if (existing != null && next == entries) return collections
    val wishlist = (existing ?: Collection(WISHLIST_ID, WISHLIST_NAME, createdAt = 0, type = CollectionType.WISHLIST.name)).copy(
        entries = next,
        notWanted = existing?.notWanted.orEmpty().let { list -> if (quantity > 0) list.filterNot { nameKey(it) == nameKey(card.name) } else list }
    )
    return if (existing != null) collections.map { if (it.isWishlist) wishlist else it } else collections + wishlist
}

/**
 * [collections] with the pre-release date taken off every Wishlist card whose set is out ([today] or
 * before) — from then on it's a plain Wishlist card, priced and watched like any other. The same list
 * (the same instance) when nothing changes.
 */
fun withReleasedCleared(collections: List<Collection>, today: String): List<Collection> {
    val wishlist = collections.firstOrNull { it.isWishlist } ?: return collections
    if (wishlist.entries.none { it.preRelease != null && it.preRelease <= today }) return collections
    val cleared = wishlist.copy(entries = wishlist.entries.map { if (it.preRelease != null && it.preRelease <= today) it.copy(preRelease = null) else it })
    return collections.map { if (it.isWishlist) cleared else it }
}

/**
 * [theirs] with each entry's pre-release date put back where [source] (the same binder, as this
 * device has it) has one and [theirs] doesn't — an entry saved by an app that doesn't know about
 * spoilers comes without it. A date put back that has passed is taken off again by
 * withReleasedCleared. The same object when nothing changes.
 */
fun keepPreReleaseFromOlderApp(source: Collection, theirs: Collection): Collection {
    val mine = source.entries.filter { it.preRelease != null }.associateBy { it.scryfallId }
    if (mine.isEmpty() || theirs.entries.none { it.preRelease == null && it.scryfallId in mine }) return theirs
    return theirs.copy(entries = theirs.entries.map { e -> if (e.preRelease == null) mine[e.scryfallId]?.let { e.copy(preRelease = it.preRelease) } ?: e else e })
}

/** A deck a revealed card could go in, and why ("Elf, like 14 cards in the deck"). */
data class DeckMatch(val deckId: String, val deckName: String, val why: String)

private val BASIC_LAND = Regex("\\bBasic\\b.*\\bLand\\b")
private val TOKEN_LINE = Regex("\\b(Token|Emblem)\\b")

/**
 * The decks (as [profiles]) [card] could go in: within the commander's colour identity, not in the
 * deck already, legal in Commander once the card is out ([today] on or after its release; before
 * that Scryfall marks every card not legal), and sharing a creature type or a theme / role tag with
 * at least MIN_SHARED of the deck's cards. Best first.
 */
fun cardFits(card: SetCard, profiles: List<DeckProfile>, today: String): List<DeckMatch> {
    if (BASIC_LAND.containsMatchIn(card.typeLine) || TOKEN_LINE.containsMatchIn(card.typeLine)) return emptyList()
    val released = card.releasedAt != null && card.releasedAt <= today
    if (released && card.commanderLegality != null && card.commanderLegality != "legal") return emptyList()
    val keys = cardNameKeys(card.name)
    val cardTypes = creatureTypes(card.typeLine)
    val cardThemes = (card.tags + card.roles).map { themeKey(it) }.filter { it.isNotEmpty() && it !in COMMON_KEYWORDS }.toSet()
    val out = mutableListOf<Pair<DeckMatch, Int>>()
    for (p in profiles) {
        if (keys.any { it in p.names }) continue
        if (!p.identity.containsAll(card.colorIdentity)) continue
        val shared = cardTypes.mapNotNull { p.types[it] } + cardThemes.mapNotNull { p.themes[it] }
        if (shared.isEmpty()) continue
        val sorted = shared.sortedWith(compareByDescending<Shared> { it.count }.thenBy { it.label })
        out += DeckMatch(p.deckId, p.deckName, fitReason(DeckFit(card, sorted, 0))) to sorted.sumOf { it.count }
    }
    return out.sortedWith(compareByDescending<Pair<DeckMatch, Int>> { it.second }.thenBy { it.first.deckName }).map { it.first }
}

/** Each of [cards]' decks, by card id (cards that fit none left out). */
fun fitsByCard(cards: List<SetCard>, profiles: List<DeckProfile>, today: String): Map<String, List<DeckMatch>> =
    if (profiles.isEmpty()) emptyMap()
    else cards.mapNotNull { c -> cardFits(c, profiles, today).takeIf { it.isNotEmpty() }?.let { c.id to it } }.toMap()

/** The gallery: every revealed card, or with [onlyMine] only those that fit one of the user's decks. */
fun galleryCards(cards: List<SetCard>, fits: Map<String, List<DeckMatch>>, onlyMine: Boolean): List<SetCard> =
    if (onlyMine) cards.filter { fits[it.id].orEmpty().isNotEmpty() } else cards

/** A wanted card from the set being opened: the Wishlist [entry] and the set's printing of it. */
data class PackCard(val entry: CollectionEntry, val card: SetCard)

/**
 * Opening packs: the Wishlist's cards from this set ([cards]: its revealed printings) — the printing
 * wanted, or another of the same card — A–Z. Cards added by a deck's Considering list count too.
 */
fun openingPacks(collections: List<Collection>, cards: List<SetCard>): List<PackCard> {
    val byId = cards.associateBy { it.id }
    val byName = HashMap<String, SetCard>()
    for (c in cards) for (k in cardNameKeys(c.name)) byName.putIfAbsent(k, c)
    val wishlist = collections.firstOrNull { it.isWishlist } ?: return emptyList()
    return wishlist.entries.filter { it.quantity > 0 }.mapNotNull { e ->
        val card = byId[e.scryfallId] ?: cardNameKeys(e.name).firstNotNullOfOrNull { byName[it] } ?: return@mapNotNull null
        PackCard(e, card)
    }.sortedBy { it.entry.name.lowercase() }
}

/**
 * [collections] once one [pulled] card (a [PackCard]) is out of a pack: one copy of the set's printing
 * into the Unsorted pile (made if needed; [foil] for a foil one), one fewer wanted on the Wishlist
 * (taken off at none).
 */
fun withPulled(collections: List<Collection>, pulled: PackCard, foil: Boolean = false): List<Collection> {
    val card = pulled.card
    return withUnsortedPile(collections).map { c ->
        when {
            c.isWishlist -> c.copy(entries = c.entries.mapNotNull { e ->
                if (e.scryfallId != pulled.entry.scryfallId) e
                else if (e.quantity <= 1) null
                else e.copy(quantity = e.quantity - 1)
            })
            c.isUnsorted -> {
                val had = c.entries.firstOrNull { it.scryfallId == card.id }
                c.copy(entries = if (had != null) c.entries.map {
                    if (it.scryfallId != card.id) it else if (foil) it.copy(foilQuantity = it.foilQuantity + 1) else it.copy(quantity = it.quantity + 1)
                } else c.entries + CollectionEntry(card.id, card.name, card.imageUrl, quantity = if (foil) 0 else 1, foilQuantity = if (foil) 1 else 0, tags = card.tags))
            }
            else -> c
        }
    }
}

/** How often the "new cards revealed" news may come: once a day at most. */
const val REVEAL_NEWS_GAP_MS = 24L * 60 * 60 * 1000

/** Whether the reveal news may be told now: never told, or last told a day or more before [now]. */
fun mayTellReveals(lastTold: Long?, now: Long): Boolean = lastTold == null || now - lastTold >= REVEAL_NEWS_GAP_MS

/**
 * The cards among [fitting] (ids of the set's revealed cards that fit a deck) not [seen] before. The
 * first look at a set ([seen] null) only notes what's there: nothing is news yet.
 */
fun revealNews(seen: Set<String>?, fitting: List<String>): List<String> =
    if (seen == null) emptyList() else fitting.filter { it !in seen }.distinct()

/** "3 new cards revealed for Bloomburrow that fit your decks" — or, for several sets, theirs together. */
fun revealNewsTitle(news: List<Pair<SetInfo, Int>>): String {
    val total = news.sumOf { it.second }
    val cards = if (total == 1) "1 new card" else "$total new cards"
    return if (news.size == 1) "$cards revealed for ${news[0].first.name} that fit your decks"
    else "$cards revealed that fit your decks"
}
