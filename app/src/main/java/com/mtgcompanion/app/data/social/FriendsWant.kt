package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckOwnership
import com.mtgcompanion.app.data.PlacedCard
import com.mtgcompanion.app.data.holdsCards
import com.mtgcompanion.app.data.pocketLabel
import com.mtgcompanion.app.data.sameCardName

/*
 * "Friends want these" on a binder: the friends whose wishlists want cards in it, from the same
 * two-way matches the Friends screen shows (trade_matches — no new server function), each with the
 * cards of theirs the user wants. Per friend: how many cards and what they're worth, each card with
 * its pocket, "Priya has 2 cards you want: …", Propose a trade (the composer started with both sides)
 * and Bring to game night — the cards go on the "Bring to game night" deck's pull list (PullList.kt),
 * so pulling them for the night is the pull list as for any deck.
 *
 * Pure, so it can be tested. Mirrors the web app's src/social/friendsWant.ts, with the same tests
 * (FriendsWantTest.kt ↔ tests/social/friendsWant.test.ts).
 */

/** A card in the binder a friend wants: the copy that would go, and its price (null: not known). */
data class WantedHere(val card: PlacedCard, val price: Double?)

/** One friend's wants in the binder, and the cards of theirs the user wants ([theyHave]). */
data class FriendWants(val friend: String, val cards: List<WantedHere>, val value: Double, val theyHave: List<TradeCard>)

/** Which copy goes first: marked for trade, then in a pocket, then the earliest pocket. */
private val goesFirst = compareByDescending<PlacedCard> { (it.entry.forTrade ?: 0) > 0 }
    .thenByDescending { (it.line.page ?: 0) > 0 && (it.line.slot ?: 0) > 0 }
    .thenBy { it.line.page ?: Int.MAX_VALUE }
    .thenBy { it.line.slot ?: Int.MAX_VALUE }

/**
 * The friends in [matches] who want cards in the binder ([here], its copies), most cards first (then
 * the most value). Each wanted card once, by name, the dearest first. [priceOf]: a copy's price.
 */
fun friendsWantHere(matches: List<TradeMatch>, here: List<PlacedCard>, priceOf: (PlacedCard) -> Double?): List<FriendWants> =
    matches.mapNotNull { m ->
        val cards = m.theyWant.distinctBy { it.name.lowercase() }.mapNotNull { want ->
            here.filter { sameCardName(it.entry.name, want.name) && it.line.qty > 0 }.sortedWith(goesFirst).firstOrNull()
        }.distinctBy { it.entry.name.lowercase() }
            .map { WantedHere(it, priceOf(it)) }
            .sortedWith(compareByDescending<WantedHere> { it.price ?: 0.0 }.thenBy { it.card.entry.name.lowercase() })
        if (cards.isEmpty()) null else FriendWants(m.friend, cards, cards.sumOf { it.price ?: 0.0 }, m.theyHave)
    }.sortedWith(compareByDescending<FriendWants> { it.cards.size }.thenByDescending { it.value }.thenBy { it.friend })

/** Where the copy is: "Page 4, slot 6", or "Not in a pocket yet". */
fun wantedWhere(w: WantedHere): String {
    val page = w.card.line.page ?: 0
    val slot = w.card.line.slot ?: 0
    return if (page > 0 && slot > 0) pocketLabel(page, slot) else "Not in a pocket yet"
}

/** "3 cards · $21" — [value] already written as money, or null when no prices are known. */
fun wantsLine(count: Int, value: String?): String =
    "$count ${if (count == 1) "card" else "cards"}" + (value?.let { " · $it" } ?: "")

/** "Priya has 2 cards you want: Sheoldred, Smothering Tithe" — or null when they have none. Up to 3 names, then "and N more". */
fun hasLine(name: String, theyHave: List<TradeCard>): String? {
    val names = theyHave.map { it.name }.distinctBy { it.lowercase() }
    if (names.isEmpty()) return null
    val shown = if (names.size <= 3) names.joinToString(", ") else names.take(3).joinToString(", ") + " and ${names.size - 3} more"
    return "$name has ${names.size} ${if (names.size == 1) "card" else "cards"} you want: $shown"
}

/** The cards as the user's side of a trade: one copy each, out of the binder it's in. */
fun wantedAsTrade(cards: List<WantedHere>): List<TradeCard> = cards.map { w ->
    val e = w.card.entry
    TradeCard(e.scryfallId, e.name, e.imageUrl, foil = w.card.line.isFoil, quantity = 1, collectionId = w.card.collectionId, condition = e.condition)
}

// ---- Bring to game night ----

/** The deck the cards for game night are gathered on; its pull list says where each is. */
const val GAME_NIGHT_DECK = "Bring to game night"

/** The wanted cards as lines of the game night deck. */
fun wantedAsDeckCards(cards: List<WantedHere>): List<DeckCardEntry> = cards.map { w ->
    DeckCardEntry(w.card.entry.scryfallId, w.card.entry.name, w.card.entry.imageUrl, quantity = 1)
}

/**
 * [decks] with [cards] on the "Bring to game night" deck — made (virtual, so every card is on its
 * pull list) with the id [newId] when there isn't one. A card already on it isn't added twice. On a
 * deck already pulled into a deck box (it holds cards now), the new cards come in as proxies, so
 * they're still on the pull list. Also the deck's id.
 */
fun bringToGameNight(decks: List<Deck>, cards: List<DeckCardEntry>, newId: String): Pair<List<Deck>, String> {
    val existing = decks.firstOrNull { it.name == GAME_NIGHT_DECK && it.archived != true && it.sample != true }
    val deck = existing ?: Deck(id = newId, name = GAME_NIGHT_DECK, ownership = DeckOwnership.VIRTUAL.name)
    val added = mutableListOf<DeckCardEntry>()
    for (c in cards) {
        if ((deck.cards + added).any { sameCardName(it.name, c.name) }) continue
        added += if (deck.holdsCards) c.copy(proxyQuantity = c.quantity) else c
    }
    val next = deck.copy(cards = deck.cards + added)
    return (if (existing == null) decks + next else decks.map { if (it.id == deck.id) next else it }) to deck.id
}
