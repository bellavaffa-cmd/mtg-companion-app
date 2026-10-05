package com.mtgcompanion.app.data.social

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

// Blocking and reporting, direct messages, trade reputation, the activity feed and cards for trade:
// calls to the server functions in supabase/migrations/20261006020000_social_more.sql. The web app's
// twin is src/social/more.ts.
//
// Until that migration is applied the functions aren't there: [SocialMore.available] says so, and
// the screens hide what needs them (or say "Not available yet") rather than failing.

/** One direct message, as the server sends it (send_message, get_messages and the "dm:<id>" channel). */
data class DirectMessage(
    val id: Long,
    val conversationId: String,
    val sender: String,
    val recipient: String,
    val body: String,
    /** ms */
    val createdAt: Long
)

data class Conversation(
    val id: String,
    val other: Profile,
    val lastSender: String?,
    val lastBody: String?,
    val lastAt: Long?,
    val unread: Int,
    /** Still friends (and not blocked): new messages can be sent. */
    val canSend: Boolean
)

data class BlockedPerson(val profile: Profile, val blockedAt: Long)

data class Reputation(
    /** Accepted trades, with anyone. */
    val total: Int,
    val withYou: Int,
    /** ms of their first trade, or null. */
    val since: Long?,
    val positive: Int,
    val negative: Int
)

/** One card a friend (or the user) has marked for trade. */
data class ForTradeCard(
    val itemId: String,
    val itemName: String?,
    val scryfallId: String,
    val name: String,
    val imageUrl: String?,
    val forTrade: Int,
    val quantity: Int,
    val foilQuantity: Int,
    val condition: String?
) {
    /** As a trade line: one copy, out of the binder it's in. */
    fun asTrade() = TradeCard(scryfallId, name, imageUrl, foil = quantity <= 0, quantity = 1, collectionId = itemId, condition = condition)
}

/** Wishlist matches both ways with one friend: their cards the user wants, the user's they want. */
data class TradeMatch(val friend: String, val theyHave: List<TradeCard>, val theyWant: List<TradeCard>)

/** One item of the Friends Activity tab, as activity_feed answers it. [cards]: name to picture. */
data class ActivityItem(
    val kind: String,
    val actor: Profile,
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
    val cards: List<Pair<String, String?>> = emptyList()
)

// ---- Parsing ----

private fun JSONObject.s(name: String): String? = if (isNull(name)) null else optString(name)
private fun JSONObject.i(name: String): Int? = if (isNull(name)) null else optInt(name)
private fun JSONObject.l(name: String): Long? = if (isNull(name)) null else optLong(name)

private fun <T> JSONArray?.objects(transform: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it)?.let(transform) }

internal fun parseDirectMessage(o: JSONObject) = DirectMessage(
    id = o.getLong("id"),
    conversationId = o.optString("conversation_id"),
    sender = o.getString("sender"),
    recipient = o.optString("recipient"),
    body = o.optString("body"),
    createdAt = o.optLong("created_at")
)

internal fun parseDirectMessages(text: String): List<DirectMessage> = jsonArray(text).objects(::parseDirectMessage)

internal fun parseActivity(text: String): List<ActivityItem> = jsonArray(text).objects { o ->
    ActivityItem(
        kind = o.optString("kind"),
        actor = parseProfile(o.getJSONObject("actor")),
        at = o.optLong("at"),
        itemKind = o.s("item_kind"),
        itemId = o.s("item_id"),
        name = o.s("name"),
        cover = o.s("cover"),
        podId = o.s("pod_id"),
        podName = o.s("pod_name"),
        format = o.s("format"),
        winner = o.s("winner"),
        players = o.i("players"),
        count = o.i("count"),
        cards = o.optJSONArray("cards").objects { c -> c.optString("name") to c.s("imageUrl") }
    )
}

internal fun parseForTrade(text: String): List<ForTradeCard>? {
    if (text.trim().let { it.isEmpty() || it == "null" }) return null
    return jsonArray(text).objects { o ->
        ForTradeCard(
            itemId = o.getString("item_id"),
            itemName = o.s("item_name"),
            scryfallId = o.optString("scryfall_id"),
            name = o.optString("name"),
            imageUrl = o.s("image_url"),
            forTrade = o.optInt("for_trade"),
            quantity = o.optInt("quantity"),
            foilQuantity = o.optInt("foil_quantity"),
            condition = o.s("condition")
        )
    }
}

internal fun parseTradeMatches(text: String): List<TradeMatch> = jsonArray(text).objects { o ->
    TradeMatch(o.getString("friend"), parseTradeCards(o.optJSONArray("they_have")), parseTradeCards(o.optJSONArray("they_want")))
}

/** A JSON array from a function's answer ("null" or nothing: empty). */
private fun jsonArray(text: String): JSONArray = text.trim().let { if (it.startsWith("[")) JSONArray(it) else JSONArray() }

/**
 * The calls themselves, and whether the server has them. One per [SocialRepository]; forgets the
 * answer when the account changes.
 */
class SocialMore(private val api: SocialApi) {
    private val _available = MutableStateFlow<Boolean?>(null)
    /** Null until asked (or while it can't be told — offline). */
    val available: StateFlow<Boolean?> = _available.asStateFlow()

    fun reset() { _available.value = null }

    /** Asks the server once whether these functions are there. */
    suspend fun check(): Boolean {
        _available.value?.let { return it }
        return try {
            val ok = (api.call("social_more_version").trim().toIntOrNull() ?: 0) >= 1
            _available.value = ok
            ok
        } catch (e: SocialException) {
            if (e.code == "unavailable") _available.value = false
            false
        }
    }

    // ---- Blocking and reporting ----

    suspend fun block(userId: String) { api.call("block_user", JSONObject().put("p_user", userId)) }
    suspend fun unblock(userId: String) { api.call("unblock_user", JSONObject().put("p_user", userId)) }

    suspend fun blocked(): List<BlockedPerson> = jsonArray(api.call("blocked_users")).objects { BlockedPerson(parseProfile(it), it.optLong("blocked_at")) }

    /** [itemKind]: "profile", "deck", "collection", "trade" or "message" — what it's about, if one thing. */
    suspend fun report(userId: String, reason: String, note: String, itemKind: String? = null, itemId: String? = null) {
        api.call(
            "report_user",
            JSONObject().put("p_user", userId).put("p_reason", reason)
                .put("p_note", note.trim().ifEmpty { null } ?: JSONObject.NULL)
                .put("p_item_kind", itemKind ?: JSONObject.NULL).put("p_item_id", itemId ?: JSONObject.NULL)
        )
    }

    // ---- Messages ----

    suspend fun send(to: String, body: String): DirectMessage =
        parseDirectMessage(JSONObject(api.call("send_message", JSONObject().put("p_to", to).put("p_body", body))))

    suspend fun conversations(): List<Conversation> = jsonArray(api.call("list_conversations")).objects { o ->
        val last = o.optJSONObject("last")
        Conversation(
            id = o.getString("id"),
            other = parseProfile(o.getJSONObject("other")),
            lastSender = last?.optString("sender"),
            lastBody = last?.optString("body"),
            lastAt = last?.optLong("created_at"),
            unread = o.optInt("unread"),
            canSend = o.optBoolean("can_send", true)
        )
    }

    /** A page of the conversation with [other], oldest first: the messages before [before] (null: the newest). */
    suspend fun messages(other: String, before: Long? = null, limit: Int = 50): List<DirectMessage> =
        parseDirectMessages(api.call("get_messages", JSONObject().put("p_with", other).put("p_before", before ?: JSONObject.NULL).put("p_limit", limit)))

    suspend fun markRead(other: String) { api.call("mark_read", JSONObject().put("p_with", other)) }

    suspend fun unread(): Int = api.call("unread_messages").trim().toIntOrNull() ?: 0

    // ---- Trade reputation ----

    suspend fun rateTrade(tradeId: String, positive: Boolean) {
        api.call("rate_trade", JSONObject().put("p_trade", tradeId).put("p_positive", positive))
    }

    suspend fun reputation(userId: String): Reputation? {
        val text = api.call("trade_reputation", JSONObject().put("p_user", userId)).trim()
        if (!text.startsWith("{")) return null
        val o = JSONObject(text)
        return Reputation(o.optInt("total"), o.optInt("with_you"), o.l("since"), o.optInt("positive"), o.optInt("negative"))
    }

    /** The user's own ratings of their recent trades: trade id to thumbs up. */
    suspend fun myRatings(): Map<String, Boolean> {
        val text = api.call("my_trade_ratings").trim()
        if (!text.startsWith("{")) return emptyMap()
        val o = JSONObject(text)
        return o.keys().asSequence().associateWith { o.optBoolean(it) }
    }

    // ---- Activity ----

    /** Friends' activity, newest first: [limit] items before [before] (ms; null: now). */
    suspend fun activity(before: Long? = null, limit: Int = 30): List<ActivityItem> =
        parseActivity(api.call("activity_feed", JSONObject().put("p_before", before ?: JSONObject.NULL).put("p_limit", limit)))

    /** Tells friends' feeds the user marked these cards for trade. Failing only costs the feed entry. */
    suspend fun noteForTrade(cards: List<Pair<String, String?>>) {
        if (cards.isEmpty()) return
        runCatching {
            api.call("note_for_trade", JSONObject().put("p_cards", JSONArray().apply {
                cards.take(50).forEach { (name, image) -> put(JSONObject().put("name", name).put("imageUrl", image ?: JSONObject.NULL)) }
            }))
        }
    }

    // ---- Cards for trade ----

    /** What [owner] has marked for trade (a friend, or the user); null when they can't be seen. */
    suspend fun forTradeList(owner: String): List<ForTradeCard>? = parseForTrade(api.call("for_trade_list", JSONObject().put("p_owner", owner)))

    suspend fun tradeMatches(): List<TradeMatch> = parseTradeMatches(api.call("trade_matches"))
}
