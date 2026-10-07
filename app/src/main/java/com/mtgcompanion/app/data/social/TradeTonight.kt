package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.PlacedCard
import com.mtgcompanion.app.data.pocketLabel
import com.mtgcompanion.app.data.sameCardName

/*
 * "Trade matches tonight" on a game night and in Pack your bag's For trades: for each player there
 * who's a friend, the cards on their shared wishlists that the user has spare (no deck of theirs
 * plays it) or marked for trade — with where each one is — and the cards the user wants that they've
 * marked for trade. All from the same two-way trade matches as the Friends screen (trade_matches — no
 * new server function). A guest, or someone who isn't a friend, is listed by name so the screen can
 * say "Add Priya as a friend to see what they want".
 *
 * Pure, so it can be tested. Mirrors the web app's src/social/tradeTonight.ts, with the same tests
 * (TradeTonightTest.kt ↔ tests/social/tradeTonight.test.ts).
 */

/** Someone at the table: their name, and their account when they're a friend (null: a guest). */
data class TonightPlayer(val name: String, val userId: String?)

/** One of the user's cards a friend wants: the line to offer, and where it is ("Trade binder · Page 4, slot 6"). */
data class TonightCard(val card: TradeCard, val where: String)

/** What can change hands with one friend tonight: the user's cards they want, and theirs for trade the user wants. */
data class TonightMatch(val friend: String, val name: String, val theyWant: List<TonightCard>, val theyHave: List<TradeCard>)

/** Friends with something to trade (the most cards first), friends with nothing tonight, and players who aren't friends. */
data class Tonight(val matches: List<TonightMatch>, val nothing: List<String>, val notFriends: List<String>)

private fun lowerName(s: String) = s.trim().lowercase()

private fun PlacedCard.inPocket() = (line.page ?: 0) > 0 && (line.slot ?: 0) > 0

/** Which copy goes first: marked for trade, then in a pocket, then the earliest pocket. The same order as FriendsWant.kt. */
private val tonightFirst = compareByDescending<PlacedCard> { (it.entry.forTrade ?: 0) > 0 }
    .thenByDescending { it.inPocket() }
    .thenBy { it.line.page ?: Int.MAX_VALUE }
    .thenBy { it.line.slot ?: Int.MAX_VALUE }

/**
 * Where the user's copy of [name] is: "Trade binder · Page 4, slot 6", "Red box › Red", "Red box" —
 * the copy that would go first. "No place yet" when no copy is in a place. [placeNames]: place id to name.
 */
fun whereTonight(name: String, placed: List<PlacedCard>, placeNames: Map<String, String>): String {
    val best = placed.filter { it.line.qty > 0 && sameCardName(it.entry.name, name) }.sortedWith(tonightFirst).firstOrNull()
        ?: return "No place yet"
    val place = placeNames[best.line.placeId] ?: "A place"
    if (best.inPocket()) return "$place · ${pocketLabel(best.line.page!!, best.line.slot!!)}"
    return best.line.section?.let { "$place › $it" } ?: place
}

/**
 * Tonight's trade matches for [players] (the user left out by the caller). A player counts as a
 * friend when their account is in [friends]; each friend once. [decksUse]: the card names the user's
 * decks use, lower case (Spares.kt) — a card on their wishlist is offered when it's marked for trade
 * or no deck uses it.
 */
fun tradeMatchesTonight(
    players: List<TonightPlayer>,
    friends: Set<String>,
    matches: List<TradeMatch>,
    decksUse: Set<String>,
    placed: List<PlacedCard>,
    placeNames: Map<String, String>
): Tonight {
    val out = mutableListOf<TonightMatch>()
    val nothing = mutableListOf<String>()
    val notFriends = mutableListOf<String>()
    val done = mutableSetOf<String>()
    for (p in players) {
        val id = p.userId
        if (id == null || id !in friends) {
            val n = p.name.trim()
            if (n.isNotEmpty() && notFriends.none { lowerName(it) == lowerName(n) }) notFriends += n
            continue
        }
        if (!done.add(id)) continue
        val m = matches.firstOrNull { it.friend == id }
        val theyWant = m?.theyWant.orEmpty()
            .filter { m?.isMarked(it) == true || lowerName(it.name) !in decksUse }
            .distinctBy { lowerName(it.name) }
            .map { TonightCard(it, whereTonight(it.name, placed, placeNames)) }
        val theyHave = m?.theyHave.orEmpty().filter { m?.isMarked(it) == true }.distinctBy { lowerName(it.name) }
        if (theyWant.isEmpty() && theyHave.isEmpty()) nothing += p.name else out += TonightMatch(id, p.name, theyWant, theyHave)
    }
    val sorted = out.sortedWith(compareByDescending<TonightMatch> { it.theyWant.size + it.theyHave.size }.thenBy { lowerName(it.name) })
    return Tonight(sorted, nothing, notFriends)
}

/** "Add Priya as a friend to see what they want". */
fun addFriendLine(name: String): String = "Add $name as a friend to see what they want"

/** "Priya wants 3 of your cards · has 1 card for trade that you want". */
fun tonightLine(m: TonightMatch): String {
    val parts = mutableListOf<String>()
    if (m.theyWant.isNotEmpty()) parts += "wants ${m.theyWant.size} of your cards"
    if (m.theyHave.isNotEmpty()) parts += "has ${m.theyHave.size} ${if (m.theyHave.size == 1) "card" else "cards"} for trade that you want"
    return "${m.name} ${parts.joinToString(" · ")}"
}

/** The cards they want as lines of the "Bring to game night" deck (FriendsWant.kt bringToGameNight). */
fun tonightAsDeckCards(cards: List<TonightCard>): List<DeckCardEntry> =
    cards.map { DeckCardEntry(it.card.scryfallId, it.card.name, it.card.imageUrl, quantity = 1) }

/**
 * The bag's "Who's coming" names as players: a name that's a friend's (the same, or one's first name
 * of the other — EventBag.kt isComing) gets their account. [people]: friends' ids to display names.
 */
fun playersFromNames(names: List<String>, people: List<Pair<String, String>>, isComing: (String, List<String>) -> Boolean): List<TonightPlayer> =
    names.map { n -> TonightPlayer(n, people.firstOrNull { (_, name) -> isComing(name, listOf(n)) }?.first) }
