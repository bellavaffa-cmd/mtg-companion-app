package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToLong

// The rules behind blocking, messages, trade reputation, the activity feed and cards for trade —
// kept apart from the screens so they can be tested. The web app's twin is src/social/moreLogic.ts,
// case for case (SocialMoreLogicTest.kt ↔ tests/social/more.test.ts).

/**
 * Whether a failed call means the server function isn't there yet (its migration not applied):
 * PostgREST answers 404 with code PGRST202. The screens then hide what needs it.
 */
fun isMissingFunction(status: Int, code: String?): Boolean = code == "PGRST202" || (status == 404 && code.isNullOrEmpty())

// ---- Messages ----

/** The longest message the server takes. */
const val MESSAGE_MAX = 2000

/** The private Realtime channel a person's new messages arrive on. */
fun dmTopic(userId: String) = "dm:$userId"

/** A piece of a message: plain text, or a card name written as [[Card Name]]. */
sealed interface MessagePart {
    data class Text(val text: String) : MessagePart
    data class Card(val name: String) : MessagePart
}

private val CARD_LINK = Regex("""\[\[([^\[\]\n]{1,150})]]""")

/**
 * Splits a message into text and card links: "[[Sol Ring]]" becomes a link to Sol Ring. A name is 1
 * to 150 characters with no brackets or line breaks; anything else stays as written.
 */
fun messageParts(body: String): List<MessagePart> {
    val parts = mutableListOf<MessagePart>()
    var last = 0
    for (m in CARD_LINK.findAll(body)) {
        val name = m.groupValues[1].trim()
        if (name.isEmpty()) continue
        val at = m.range.first
        if (at > last) parts += MessagePart.Text(body.substring(last, at))
        parts += MessagePart.Card(name)
        last = m.range.last + 1
    }
    if (last < body.length) parts += MessagePart.Text(body.substring(last))
    return parts
}

/** [list] with [incoming] added: each message once (by id, the newer copy winning), oldest first. */
fun mergeMessages(list: List<DirectMessage>, incoming: List<DirectMessage>): List<DirectMessage> {
    val byId = LinkedHashMap<Long, DirectMessage>()
    for (m in list + incoming) byId[m.id] = m
    return byId.values.sortedBy { it.id }
}

/** A conversation's last message for the list: "You: …" for the user's own, cut to 80 characters. */
fun previewLine(sender: String?, body: String?, me: String): String {
    if (sender == null || body == null) return "No messages yet"
    val text = body.replace(Regex("\\s+"), " ").trim()
    val cut = if (text.length > 80) text.take(79) + "…" else text
    return if (sender == me) "You: $cut" else cut
}

// ---- Trade reputation ----

private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

/** "March 2026", from ms (in UTC, so both apps say the same). */
fun monthYear(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC)
    return "${MONTHS[d.monthValue - 1]} ${d.year}"
}

/** "Trades completed: 12 · since March 2026", or "No trades yet". */
fun tradesLine(total: Int, sinceMs: Long?): String = when {
    total <= 0 -> "No trades yet"
    sinceMs != null && sinceMs > 0 -> "Trades completed: $total · since ${monthYear(sinceMs)}"
    else -> "Trades completed: $total"
}

/** "3 with you", "1 with you", or "None with you yet". */
fun withYouLine(n: Int): String = if (n <= 0) "None with you yet" else "$n with you"

/** "5 positive" (thumbs up after trades). */
fun positiveLine(n: Int): String = "${maxOf(0, n)} positive"

/** Whether the user can give a thumbs up or down for [trade]: accepted, and their side updated. */
fun canRate(trade: Trade, me: String): Boolean = trade.status == TradeStatus.ACCEPTED && when (me) {
    trade.fromUser -> trade.fromApplied
    trade.toUser -> trade.toApplied
    else -> false
}

// ---- Two-way wishlist matches ----

/** "Priya has 2 cards you want, and wants 3 of yours" — or the half that applies; null for neither. */
fun matchSentence(name: String, have: Int, want: Int): String? {
    val has = if (have > 0) "has $have ${if (have == 1) "card" else "cards"} you want" else null
    val wants = if (want > 0) "wants $want of yours" else null
    return when {
        has != null && wants != null -> "$name $has, and $wants"
        has != null -> "$name $has"
        wants != null -> "$name $wants"
        else -> null
    }
}

// ---- Cards for trade ----

/** How many of an entry's copies are for trade: its [CollectionEntry.forTrade], never more than it holds. */
fun forTradeOf(entry: CollectionEntry): Int {
    val n = entry.forTrade ?: 0
    if (n <= 0) return 0
    return minOf(n, maxOf(0, entry.quantity) + maxOf(0, entry.foilQuantity))
}

/**
 * [collections] with [count] copies of one binder card marked for trade (kept between 0 and its
 * copies; 0 leaves it out). Wishlists aren't changed.
 */
fun setForTrade(collections: List<Collection>, collectionId: String, scryfallId: String, count: Int): List<Collection> =
    collections.map { c ->
        if (c.id != collectionId || c.type == "WISHLIST") c
        else c.copy(entries = c.entries.map { e ->
            if (e.scryfallId != scryfallId) e
            else forTradeOf(e.copy(forTrade = count)).let { n -> e.copy(forTrade = if (n > 0) n else null) }
        })
    }

/** One line of the user's for-trade list. */
data class ForTradeLine(val collectionId: String, val binder: String, val entry: CollectionEntry, val count: Int)

/** Every owned card marked for trade, by name then binder. */
fun forTradeLines(collections: List<Collection>): List<ForTradeLine> =
    collections.filter { it.type != "WISHLIST" }
        .flatMap { c -> c.entries.mapNotNull { e -> forTradeOf(e).takeIf { it > 0 }?.let { ForTradeLine(c.id, c.name, e, it) } } }
        .sortedWith(compareBy<ForTradeLine> { it.entry.name }.thenBy { it.binder })

/**
 * One binder's for-trade copies as picks for the card picker: plain copies first, then foil, up to the
 * marked count. The picker's total for a card is its new count (see [setForTrade]).
 */
fun forTradePicks(c: Collection): List<TradeCard> = c.entries.flatMap { e ->
    val n = forTradeOf(e)
    if (n <= 0) return@flatMap emptyList()
    val plain = minOf(n, maxOf(0, e.quantity))
    val foil = n - plain
    listOfNotNull(
        if (plain > 0) TradeCard(e.scryfallId, e.name, e.imageUrl, foil = false, quantity = plain, collectionId = c.id) else null,
        if (foil > 0) TradeCard(e.scryfallId, e.name, e.imageUrl, foil = true, quantity = foil, collectionId = c.id) else null
    )
}

// ---- Activity ----

/** "Sol Ring, Arcane Signet and 2 more" (up to [shown] names). */
fun namesLine(names: List<String>, total: Int, shown: Int = 2): String {
    val listed = names.take(shown)
    val more = maxOf(0, total - listed.size)
    return when {
        listed.isEmpty() -> ""
        more > 0 -> "${listed.joinToString(", ")} and $more more"
        listed.size == 1 -> listed[0]
        else -> "${listed.dropLast(1).joinToString(", ")} and ${listed.last()}"
    }
}

data class ActivityText(val action: String, val detail: String?)

/** What an activity item says after the person's name, and a second line when there is one. */
fun activityText(item: ActivityItem): ActivityText = when (item.kind) {
    "shared" ->
        if (item.itemId == null) ActivityText(if (item.itemKind == "deck") "shared all their decks" else "shared their collection", null)
        else ActivityText("shared a ${if (item.itemKind == "deck") "deck" else "binder"}", item.name)
    "deck_updated" -> ActivityText("updated a deck", item.name)
    "pod_game" -> ActivityText(
        if (item.podName != null) "recorded a game in ${item.podName}" else "recorded a game",
        listOfNotNull(item.winner?.let { "$it won" } ?: "No winner", item.players?.takeIf { it > 0 }?.let { "$it players" }).joinToString(" · ")
    )
    "for_trade" -> {
        val n = item.count ?: item.cards.size
        ActivityText("marked $n ${if (n == 1) "card" else "cards"} for trade", namesLine(item.cards.map { it.first }, n).ifEmpty { null })
    }
    else -> ActivityText("did something new", null)
}

/** "just now", "5 min ago", "3 h ago", "yesterday", "4 days ago", then "12 Mar". */
fun timeAgo(ms: Long, now: Long): String {
    val s = maxOf(0L, ((now - ms) / 1000.0).roundToLong())
    if (s < 60) return "just now"
    val m = s / 60
    if (m < 60) return "$m min ago"
    val h = m / 60
    if (h < 24) return "$h h ago"
    val d = h / 24
    if (d == 1L) return "yesterday"
    if (d < 7) return "$d days ago"
    val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC)
    return "${date.dayOfMonth} ${MONTHS[date.monthValue - 1].take(3)}"
}

// ---- Reports ----

/** Why someone is reported: the server's codes, and the words for them. */
val REPORT_REASONS = listOf(
    "spam" to "Spam",
    "abuse" to "Abuse or harassment",
    "scam" to "Scam or a trade gone wrong",
    "inappropriate" to "Something inappropriate",
    "other" to "Something else"
)
