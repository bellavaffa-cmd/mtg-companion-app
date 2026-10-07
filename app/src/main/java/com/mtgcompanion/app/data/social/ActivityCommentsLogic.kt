package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.Collection
import java.net.URLEncoder

// The rules behind the friends' Activity feed (friends_activity), what it may show of the user
// (Settings › Privacy) and comments on shared decks — kept apart from the screens so they can be
// tested. Server side: supabase/migrations/20261006080000_activity_comments.sql. The web app's twin
// is src/social/activityLogic.ts, case for case (ActivityCommentsLogicTest.kt ↔
// tests/social/activity.test.ts).

// ---- What friends' Activity shows of the user ----

/**
 * What friends' Activity may show of the user. On unless turned off — decks, cards for trade, league
 * results — since they only announce what's already shared with friends or a pod. Selling is off
 * unless turned on: a To sell list is about money and isn't shared anywhere else.
 */
data class ActivityPrefs(
    val decks: Boolean = true,
    val forTrade: Boolean = true,
    val selling: Boolean = false,
    val leagues: Boolean = true
)

/** One of Settings › Privacy's switches: its server key, title and line under it. */
data class ActivityPrefRow(val key: String, val title: String, val detail: String)

/** Settings › Privacy's switches, in order. */
val ACTIVITY_PREF_ROWS = listOf(
    ActivityPrefRow("decks", "New and changed decks", "When you share a deck, or change one you share."),
    ActivityPrefRow("for_trade", "Cards for trade", "When you mark cards for trade — friends who want them see it."),
    ActivityPrefRow("selling", "Your To sell list", "That you're selling cards, and how many. Off unless you turn it on."),
    ActivityPrefRow("leagues", "League results", "Your name in pod league news: who leads, who won.")
)

const val ACTIVITY_PRIVACY_NOTE = "Only friends see your activity, never anyone you've blocked. These change only what shows in " +
    "friends' Activity: what you share stays shared."

fun ActivityPrefs.isOn(key: String): Boolean = when (key) {
    "decks" -> decks
    "for_trade" -> forTrade
    "selling" -> selling
    else -> leagues
}

fun ActivityPrefs.withPref(key: String, on: Boolean): ActivityPrefs = when (key) {
    "decks" -> copy(decks = on)
    "for_trade" -> copy(forTrade = on)
    "selling" -> copy(selling = on)
    else -> copy(leagues = on)
}

// ---- The feed ----

/** One item of friends_activity (or the older activity_feed, whose items are a subset). */
data class FeedItem(
    val kind: String,
    /** Absent for league news. */
    val actor: Profile?,
    /** ms */
    val at: Long,
    val itemKind: String? = null,
    val itemId: String? = null,
    val name: String? = null,
    val cover: String? = null,
    val podId: String? = null,
    val podName: String? = null,
    val format: String? = null,
    val winner: String? = null,
    val players: Int? = null,
    val count: Int? = null,
    val cards: List<Pair<String, String?>> = emptyList(),
    /** shared: a deck made in the 14 days before it was shared. */
    val newDeck: Boolean = false,
    /** for_trade, selling: the cards on the user's wishlists (selling: up to 6 of them). */
    val wanted: List<String> = emptyList(),
    val wantedCount: Int? = null,
    val seasonId: String? = null,
    val ended: Boolean = false,
    val champion: String? = null,
    val championIds: List<String> = emptyList(),
    /** league: pod members whose names its news leaves out (turned off, or blocked). */
    val quiet: List<String> = emptyList(),
    val startsOn: String? = null,
    val endsOn: String? = null,
    val maxNights: Int? = null,
    /** comment: the deck's owner. */
    val itemOwner: String? = null,
    val commentId: String? = null,
    val body: String? = null,
    val cardName: String? = null,
    val reply: Boolean = false,
    val onMine: Boolean = false
)

/** What a tap on an item's action does. */
enum class FeedActionKind { COMMENTS, ASK, SELLING }

data class FeedAction(val label: String, val kind: FeedActionKind)

/** A piece of an item's sentence; [bold] for names. */
data class FeedPart(val text: String, val bold: Boolean = false)

data class FeedLine(
    val parts: List<FeedPart>,
    /** The grey line under it (the time goes after it). */
    val sub: String?,
    val action: FeedAction?
)

/** A season's table as far as the feed needs it: ranked rows, and game nights played. */
data class LeagueRow(val userId: String?, val name: String, val points: Int, val rank: Int)
data class LeagueSnapshot(val standings: List<LeagueRow>, val nights: Int)

data class LeagueText(val text: String, val sub: String?)

private fun plural(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"

/**
 * What league news says: "Priya leads Season 2 by 3 points", "Season 2 champion: Priya". Nobody is
 * named who turned league results off (or is blocked): the news then says only that there's news.
 * [table]: the running season's table, worked out from the pod's games (null while loading).
 */
fun leagueText(item: FeedItem, table: LeagueSnapshot?): LeagueText {
    val season = item.name?.trim()?.ifEmpty { null } ?: "The season"
    val quiet = item.quiet.toSet()
    if (item.ended) {
        val champion = item.champion?.trim()
        if (champion.isNullOrEmpty() || item.championIds.any { it in quiet }) return LeagueText("$season is over", null)
        return LeagueText("$season ${if (" & " in champion) "champions" else "champion"}: $champion", null)
    }
    val left = if (item.maxNights != null && item.maxNights > 0 && table != null) item.maxNights - table.nights else null
    val sub = if (left != null && left > 0) "${plural(left, "game night")} left" else null
    if (table == null) return LeagueText("New results in $season", null)
    val top = table.standings.filter { it.rank == 1 }
    return when {
        top.isEmpty() -> LeagueText("$season has started", sub)
        top.any { it.userId != null && it.userId in quiet } -> LeagueText("New results in $season", sub)
        top.size == 2 -> LeagueText("${top[0].name} and ${top[1].name} share the lead in $season", sub)
        top.size > 2 -> LeagueText("${top.size} players share the lead in $season", sub)
        else -> {
            val next = table.standings.firstOrNull { it.rank > 1 }
            val gap = if (next != null) top[0].points - next.points else 0
            LeagueText(if (gap > 0) "${top[0].name} leads $season by ${plural(gap, "point")}" else "${top[0].name} leads $season", sub)
        }
    }
}

/** What an item says, its grey line and its action. [table]: for running league news (see [leagueText]). */
fun feedLine(item: FeedItem, table: LeagueSnapshot? = null): FeedLine {
    val who = FeedPart(item.actor?.displayName ?: "Someone", bold = true)
    val name = item.name?.trim()?.ifEmpty { null }
    val look = FeedAction("Look and comment", FeedActionKind.COMMENTS)
    fun named(): List<FeedPart> = if (name != null) listOf(FeedPart(": "), FeedPart(name, bold = true)) else emptyList()
    return when (item.kind) {
        "shared" -> when {
            item.itemId == null -> FeedLine(listOf(who, FeedPart(if (item.itemKind == "deck") " shared all their decks" else " shared their collection")), null, null)
            item.itemKind == "deck" -> FeedLine(listOf(who, FeedPart(if (item.newDeck) " built a new deck" else " shared a deck")) + named(), "Shared with friends", look)
            else -> FeedLine(listOf(who, FeedPart(" shared a binder")) + named(), null, null)
        }
        "deck_updated" -> FeedLine(listOf(who, FeedPart(" updated a deck")) + named(), null, look)
        "pod_game" -> FeedLine(
            if (item.podName != null) listOf(who, FeedPart(" recorded a game in "), FeedPart(item.podName, bold = true)) else listOf(who, FeedPart(" recorded a game")),
            listOfNotNull(item.winner?.let { "$it won" } ?: "No winner", item.players?.takeIf { it > 0 }?.let { "$it players" }).joinToString(" · "),
            null
        )
        "for_trade" -> {
            val wanted = item.wanted
            if (wanted.isNotEmpty()) FeedLine(
                listOf(who, FeedPart(" added "), FeedPart(namesLine(wanted, wanted.size), bold = true), FeedPart(" to their trade binder")),
                if (wanted.size == 1) "It's on your wishlist" else "They're on your wishlist",
                FeedAction("Ask ${who.text} for ${if (wanted.size == 1) "it" else "them"}", FeedActionKind.ASK)
            ) else {
                val n = item.count ?: item.cards.size
                FeedLine(listOf(who, FeedPart(" marked ${plural(n, "card")} for trade")), namesLine(item.cards.map { it.first }, n).ifEmpty { null }, null)
            }
        }
        "selling" -> {
            val n = item.count ?: 0
            val w = item.wantedCount ?: item.wanted.size
            FeedLine(
                listOf(who, FeedPart(" is selling ${plural(n, "card")}")),
                if (w > 0) (if (w == 1) "1 is on your wishlist" else "$w are on your wishlist") else null,
                FeedAction("See them", FeedActionKind.SELLING)
            )
        }
        "league" -> {
            val t = leagueText(item, table)
            FeedLine(listOf(FeedPart(item.podName ?: "Your pod", bold = true), FeedPart(": ${t.text}")), t.sub, null)
        }
        "comment" -> {
            val deck = FeedPart(name ?: "a deck", bold = true)
            val parts = when {
                !item.onMine -> listOf(who, FeedPart(" replied to your comment on "), deck)
                item.reply -> listOf(who, FeedPart(" replied on "), deck)
                item.cardName != null -> listOf(who, FeedPart(" commented on "), FeedPart(item.cardName, bold = true), FeedPart(" in "), deck)
                else -> listOf(who, FeedPart(" commented on "), deck)
            }
            FeedLine(parts, item.body?.let { "“${cut(it, 80)}”" }, FeedAction("Reply", FeedActionKind.COMMENTS))
        }
        else -> FeedLine(listOf(who, FeedPart(" did something new")), null, null)
    }
}

/** [s] on one line, cut to [max] characters. */
fun cut(s: String, max: Int): String {
    val one = s.replace(Regex("\\s+"), " ").trim()
    return if (one.length > max) one.take(max - 1) + "…" else one
}

/** The deck an item's "Look and comment" or "Reply" opens. */
data class FeedDeck(val owner: String, val deckId: String)

/** The deck an item's "Look and comment" or "Reply" opens: its owner and id; null when it has none. */
fun feedDeck(item: FeedItem): FeedDeck? {
    if (item.itemKind != "deck" || item.itemId == null) return null
    val owner = if (item.kind == "comment") item.itemOwner else item.actor?.userId
    return owner?.let { FeedDeck(it, item.itemId) }
}

/** The web app's address of a shared deck's comments (the same as SharedItemPage's ?tab=comments). */
fun commentsPath(owner: String, deckId: String): String =
    "/shared/$owner/deck/${URLEncoder.encode(deckId, "UTF-8").replace("+", "%20")}?tab=comments"

private fun same(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

/** "Ask Priya for it": one copy of each wanted card she has marked for trade, as trade lines. */
fun askCards(forTrade: List<ForTradeCard>, names: List<String>): List<TradeCard> {
    val out = mutableListOf<TradeCard>()
    for (name in names) {
        val c = forTrade.firstOrNull { same(it.name, name) } ?: continue
        if (out.any { same(it.name, c.name) }) continue
        out += c.asTrade()
    }
    return out
}

/** One card on a friend's To sell list, as selling_list answers it. [wanted]: on the user's wishlists. */
data class SellingCard(
    val itemId: String,
    val itemName: String?,
    val scryfallId: String,
    val name: String,
    val imageUrl: String?,
    val forSale: Int,
    val quantity: Int,
    val foilQuantity: Int,
    val condition: String?,
    val wanted: Boolean
)

/** "Ask Jo for them" from their To sell list: one copy of each card on the user's wishlists. */
fun sellingAsk(list: List<SellingCard>): List<TradeCard> {
    val out = mutableListOf<TradeCard>()
    for (c in list) {
        if (!c.wanted || out.any { same(it.name, c.name) }) continue
        out += TradeCard(c.scryfallId, c.name, c.imageUrl, foil = c.quantity <= 0, quantity = 1, collectionId = c.itemId, condition = c.condition)
    }
    return out
}

// ---- Comments on shared decks ----

const val COMMENT_MAX = 1000

data class DeckComment(
    val id: String,
    /** The comment it replies to; null for a top-level one. */
    val parent: String?,
    val author: Profile,
    val body: String,
    val cardName: String?,
    val cardImage: String?,
    val hidden: Boolean,
    /** ms */
    val createdAt: Long,
    val mine: Boolean
)

data class CommentThread(val comment: DeckComment, val replies: List<DeckComment>)

/** What deck_comments answers. */
data class DeckComments(val isOwner: Boolean, val canComment: Boolean, val comments: List<DeckComment>)

/**
 * Comments as threads, oldest first: each top-level comment with its replies (oldest first). Replies
 * go one level deep; a reply whose comment isn't there (deleted, hidden) is left out.
 */
fun threadComments(list: List<DeckComment>): List<CommentThread> {
    val byTime = list.sortedWith(compareBy<DeckComment> { it.createdAt }.thenBy { it.id })
    val top = byTime.filter { it.parent == null }
    val replies = byTime.filter { it.parent != null }.groupBy { it.parent }
    return top.map { CommentThread(it, replies[it.id].orEmpty()) }
}

/** How many comments the threads show: the "Comments · 3" count. */
fun commentCount(threads: List<CommentThread>): Int = threads.sumOf { 1 + it.replies.size }

fun commentsTabLabel(n: Int): String = if (n > 0) "Comments · $n" else "Comments"

/** Whether [card] is one of the deck's cards (commanders included). */
fun inDeck(deckCards: List<String>, card: String): Boolean = deckCards.any { same(it, card) }

/** "Priya · on Gray Merchant of Asphodel", "Sam · owner", "You · on the deck". */
fun commentHeader(c: DeckComment, me: String?, owner: String, deckCards: List<String>): String {
    val parts = mutableListOf(if (c.author.userId == me) "You" else c.author.displayName)
    if (c.author.userId == owner && c.author.userId != me) parts += "owner"
    if (c.parent == null) {
        parts += when {
            c.cardName == null -> "on the deck"
            inDeck(deckCards, c.cardName) -> "on ${c.cardName}"
            else -> "on the deck · suggests ${c.cardName}"
        }
    }
    if (c.hidden) parts += "hidden"
    return parts.joinToString(" · ")
}

data class CommentActions(
    val reply: Boolean,
    /** "Consider a swap": the owner opens the deck's Considering; anyone else replies about it. */
    val swap: Boolean,
    /** "Offer it in a trade": a card suggested for the deck that the user has. */
    val offer: Boolean,
    val remove: Boolean,
    val hide: Boolean,
    val report: Boolean
)

/**
 * What the user may do with [c]: reply (top-level comments, when they may comment), consider a swap
 * (a comment on one of the deck's cards), offer the card in a trade (a card suggested for the deck
 * that the user — not the owner — has), delete (their own, or anything on their deck), hide (the
 * owner, others' comments) and report (others' comments).
 */
fun commentActions(c: DeckComment, me: String?, owner: String, canComment: Boolean, deckCards: List<String>, haveCard: Boolean): CommentActions {
    val isOwner = me == owner
    val mine = c.author.userId == me
    val onDeckCard = c.cardName != null && inDeck(deckCards, c.cardName)
    return CommentActions(
        reply = c.parent == null && canComment,
        swap = c.parent == null && onDeckCard && canComment,
        offer = c.cardName != null && !onDeckCard && !isOwner && canComment && haveCard,
        remove = me != null && (mine || isOwner),
        hide = isOwner && !mine,
        report = me != null && !mine
    )
}

/** What a reply starts with when someone other than the owner taps "Consider a swap". */
fun swapReply(card: String) = "Instead of $card: "

fun composerPlaceholder(ownerName: String, isOwner: Boolean, replyTo: String?): String = when {
    replyTo != null -> "Reply to $replyTo"
    isOwner -> "Comment on your deck"
    else -> "Comment on $ownerName's deck"
}

/** The line under the comments. */
fun commentsNote(ownerName: String, isOwner: Boolean): String =
    if (isOwner) "Only friends you share the deck with can comment. You can hide or delete comments."
    else "Only friends $ownerName shares the deck with can comment. $ownerName can hide or delete comments."

/**
 * "Offer it in a trade": one copy of [card] from the user's binders — a copy marked for trade first,
 * then the binder with the most. Null when they have none.
 */
fun offerCard(collections: List<Collection>, card: String): TradeCard? {
    var best: Triple<Collection, com.mtgcompanion.app.data.CollectionEntry, Int>? = null
    for (c in collections) {
        if (c.type == "WISHLIST") continue
        for (e in c.entries) {
            if (!same(e.name, card)) continue
            val copies = maxOf(0, e.quantity) + maxOf(0, e.foilQuantity)
            if (copies <= 0) continue
            val score = (if ((e.forTrade ?: 0) > 0) 10000 else 0) + copies
            if (best == null || score > best.third) best = Triple(c, e, score)
        }
    }
    val (c, e) = best ?: return null
    return TradeCard(e.scryfallId, e.name, e.imageUrl, foil = e.quantity <= 0, quantity = 1, collectionId = c.id, condition = e.condition)
}

/** The report for a comment goes through report_user as the deck, with this id. */
fun commentReportId(commentId: String) = "comment:$commentId"

// ---- Where the feed goes ----

/** Where a tap in the Activity tab goes. */
sealed interface ActivityTarget {
    /** A shared deck or binder; [comments]: open on the deck's Comments. */
    data class SharedItem(val owner: String, val kind: String, val itemId: String, val comments: Boolean = false) : ActivityTarget
    /** Everything a friend shares with the user (their whole collection / all their decks). */
    data class FriendShared(val owner: String) : ActivityTarget
    data class Friend(val userId: String) : ActivityTarget
    /** The Playgroup screen: pods' games and leagues. */
    data object Playgroup : ActivityTarget
    /** The trade composer, to [friend] — SocialRepository.draft holds what it starts with. */
    data class Trade(val friend: String) : ActivityTarget
    /** Settings › Privacy. */
    data object Privacy : ActivityTarget
}

/** Where tapping an item (not its action) goes; null for selling, which opens its list in place. */
fun activityTarget(item: FeedItem): ActivityTarget? {
    val owner = item.actor?.userId
    val deck = feedDeck(item)
    return when {
        item.kind == "comment" -> deck?.let { ActivityTarget.SharedItem(it.owner, "deck", it.deckId, comments = true) }
        item.kind == "selling" -> null
        item.kind == "league" || item.kind == "pod_game" -> ActivityTarget.Playgroup
        (item.kind == "shared" || item.kind == "deck_updated") && owner != null && item.itemId != null && item.itemKind != null ->
            ActivityTarget.SharedItem(owner, item.itemKind, item.itemId)
        item.kind == "shared" && owner != null -> ActivityTarget.FriendShared(owner)
        owner != null -> ActivityTarget.Friend(owner)
        else -> null
    }
}
