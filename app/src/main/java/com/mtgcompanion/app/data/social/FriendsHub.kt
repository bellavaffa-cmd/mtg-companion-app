package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.Loan
import com.mtgcompanion.app.data.isOpen
import com.mtgcompanion.app.data.stillOut

/*
 * The Friends tab, now a tab of the bottom bar: its badge, the trade inbox (Your turn, Waiting on
 * them, Done, and What friends want from you), the one line of context under each friend on People,
 * and each pod's line with its running league season.
 *
 * Pure, so it can be tested. Mirrors the web app's src/social/friendsHub.ts rule for rule, with the
 * same tests (FriendsHubTest.kt ↔ tests/social/friendsHub.test.ts).
 */

// ---- The badge on the bar ----

/** The Friends tab's badge: unread messages, trades waiting on the user and friend requests. */
fun friendsBadge(requests: Int, unread: Int, trades: Int): Int = maxOf(0, requests) + maxOf(0, unread) + maxOf(0, trades)

/** "9+" past nine, so the badge stays small. */
fun badgeText(n: Int): String = if (n > 9) "9+" else "$n"

// ---- The trade inbox ----

/** The user's trades, grouped: theirs to answer or finish, the other person's, and the rest. */
data class TradeInbox(val yourTurn: List<Trade>, val waitingOnThem: List<Trade>, val done: List<Trade>)

/**
 * [trades] grouped for the inbox, leaving out anyone in [blocked]. Your turn: [waitingOnMe]
 * (an answer, or updating the user's binders). Waiting on them: a request the user sent, or an
 * accepted trade only the other side still has to update. Done: everything else. Each newest first.
 */
fun tradeInbox(trades: List<Trade>, me: String, blocked: Set<String> = emptySet()): TradeInbox {
    val shown = trades.filter { (if (it.fromUser == me) it.toUser else it.fromUser) !in blocked }.sortedByDescending { it.updatedAt }
    val yourTurn = shown.filter { waitingOnMe(it, me) }
    val waiting = shown.filter { t ->
        t !in yourTurn && (
            (t.status == TradeStatus.OPEN && t.fromUser == me) ||
                (t.status == TradeStatus.ACCEPTED && (if (t.fromUser == me) !t.toApplied else !t.fromApplied))
            )
    }
    return TradeInbox(yourTurn, waiting, shown.filter { it !in yourTurn && it !in waiting })
}

/** "Fact or Fiction ×3, Cyclonic Rift" — up to [max] cards, then "and N more"; "nothing" for none. */
fun cardNames(cards: List<TradeCard>, max: Int = 2): String {
    if (cards.isEmpty()) return "nothing"
    val names = cards.map { if (it.quantity > 1) "${it.name} ×${it.quantity}" else it.name }
    return if (names.size <= max) names.joinToString(", ") else names.take(max).joinToString(", ") + " and ${names.size - max} more"
}

/** "Impulse for Lightning Greaves": what the user gives, for what they get. */
fun tradeSummary(trade: Trade, me: String): String {
    val sides = tradeSides(trade, me)
    return "${cardNames(sides.give)} for ${cardNames(sides.get)}"
}

/** A finished trade's right-hand word: the user's rating, "Rate it", or how it ended. */
fun doneLabel(trade: Trade, me: String, rating: Boolean?): String = when (trade.status) {
    TradeStatus.ACCEPTED -> when {
        rating == true -> "Rated good"
        rating == false -> "Rated poor"
        canRate(trade, me) -> "Rate it"
        else -> "Done"
    }
    TradeStatus.DECLINED -> "Declined"
    TradeStatus.CANCELLED -> "Cancelled"
    TradeStatus.COUNTERED -> "Countered"
    TradeStatus.OPEN -> "Open"
}

// ---- What friends want from you ----

/** One friend's wants from the user, across all binders: how many cards, where they are, what they're worth. */
data class WantFromYou(val friend: String, val cards: Int, val where: String?, val value: Double?, val match: TradeMatch)

/**
 * The friends in [matches] who want the user's cards (their [TradeMatch.theyWant]), each card once by
 * name. [where]: the binders those cards are in ([binderName] by id), up to two then "+N"; [value]:
 * their prices from [prices] (null when none is known). The dearest first, then the most cards.
 */
fun friendsWantFromYou(matches: List<TradeMatch>, binderName: (String) -> String?, prices: Map<String, CardPrice> = emptyMap()): List<WantFromYou> =
    matches.mapNotNull { m ->
        val cards = m.theyWant.distinctBy { it.name.trim().lowercase() }
        if (cards.isEmpty()) return@mapNotNull null
        val binders = cards.mapNotNull { c -> c.collectionId?.let(binderName) }.distinct()
        val where = when {
            binders.isEmpty() -> null
            binders.size <= 2 -> binders.joinToString(", ")
            else -> binders.take(2).joinToString(", ") + " +${binders.size - 2}"
        }
        val priced = cards.mapNotNull { unitPrice(it, prices) }
        WantFromYou(m.friend, cards.size, where, if (priced.isEmpty()) null else priced.sum(), m)
    }.sortedWith(compareByDescending<WantFromYou> { it.value ?: 0.0 }.thenByDescending { it.cards }.thenBy { it.friend })

/** "Priya · 3 cards". */
fun wantFromYouLine(name: String, cards: Int): String = "$name · $cards ${if (cards == 1) "card" else "cards"}"

// ---- One line of context under each friend ----

/** The quick action beside a friend: start a trade, see the loan, or open the shared shelf. */
enum class FriendAction(val label: String) { TRADE("Trade"), LOAN("Loan"), HOME("Home") }

data class FriendContext(val line: String, val action: FriendAction)

/**
 * What to say under a friend, the first that applies: they want the user's cards ([wants] cards,
 * [wantsValue] already written as money) — "Wants 3 of your cards · $21"; they have the user's cards
 * on loan ([lent], the names still out; [lentDue], loanDue's label) — "Has your Sol Ring · back by
 * 10 Oct"; or they share a household's shelf with the user — "Shares the shelf at home". Null: none.
 */
fun friendContext(wants: Int, wantsValue: String?, lent: List<String>, lentDue: String?, sharesHome: Boolean): FriendContext? {
    if (wants > 0) {
        val what = if (wants == 1) "Wants 1 of your cards" else "Wants $wants of your cards"
        return FriendContext(what + (wantsValue?.let { " · $it" } ?: ""), FriendAction.TRADE)
    }
    if (lent.isNotEmpty()) {
        val what = if (lent.size == 1) "Has your ${lent[0]}" else "Has ${lent.size} of your cards"
        val due = lentDue?.takeIf { it.isNotBlank() && it != "No date" }?.let { it.replaceFirstChar { c -> c.lowercaseChar() } }
        return FriendContext(what + (due?.let { " · $it" } ?: ""), FriendAction.LOAN)
    }
    if (sharesHome) return FriendContext("Shares the shelf at home", FriendAction.HOME)
    return null
}

/** The user's open loans to [friendId]: the names of the copies still out, one per copy. */
fun lentTo(loans: List<Loan>, friendId: String): List<String> =
    loans.filter { it.friendId == friendId && isOpen(it) }.flatMap { l -> l.cards.flatMap { c -> List(stillOut(c)) { c.name } } }

// ---- Pods ----

/** "1st", "2nd", "3rd", "4th", "11th", "21st". */
fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

/** "5 people", "1 person". */
fun peopleLine(members: Int): String = "$members ${if (members == 1) "person" else "people"}"

/** "5 people · Season 2 · you're 2nd", "5 people · Season 2", "3 people · no season". */
fun podLine(members: Int, season: String?, myRank: Int?): String {
    val people = peopleLine(members)
    if (season == null) return "$people · no season"
    return "$people · $season" + (myRank?.takeIf { it > 0 }?.let { " · you're ${ordinal(it)}" } ?: "")
}

/** The note on Play now that people, chats and trades live on the Friends tab. */
const val MOVED_TO_FRIENDS = "People, chats and trades moved to the Friends tab."
