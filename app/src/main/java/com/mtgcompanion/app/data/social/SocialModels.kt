package com.mtgcompanion.app.data.social

import org.json.JSONArray
import org.json.JSONObject

// Friends, pods, sharing, life counter seats and trades — the shapes the social server functions
// answer with (supabase/migrations/20260919000000_social.sql). Parsed with org.json, like sync.

data class Profile(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarPath: String?
) {
    val handle: String get() = "@$username"
}

data class FriendLink(
    val userId: String,
    val accepted: Boolean,
    /** They asked the user: a request waiting for an answer. */
    val incoming: Boolean
)

data class Pod(val id: String, val name: String, val owner: String, val members: List<String>)

enum class ShareKind(val wire: String) {
    DECK("deck"), COLLECTION("collection");
    companion object {
        fun of(wire: String) = if (wire == "deck") DECK else COLLECTION
    }
}

data class SharedSummary(
    val owner: String,
    val kind: ShareKind,
    val itemId: String,
    val name: String?,
    val cover: String?,
    val cards: Int
)

data class Share(val kind: ShareKind, val itemId: String, val allFriends: Boolean, val podIds: List<String>, val linkToken: String?)

/** One line of a trade. [collectionId] is the giver's binder it comes out of. */
data class TradeCard(
    val scryfallId: String,
    val name: String,
    val imageUrl: String? = null,
    val foil: Boolean = false,
    val quantity: Int = 1,
    val collectionId: String? = null
) {
    /** The same card, finish and binder are one line. */
    val key: String get() = "${collectionId.orEmpty()}:$scryfallId:${if (foil) "f" else "n"}"
}

enum class TradeStatus { OPEN, ACCEPTED, DECLINED, CANCELLED, COUNTERED }

data class Trade(
    val id: String,
    val fromUser: String,
    val toUser: String,
    /** What [fromUser] asks for, out of [toUser]'s binders. */
    val want: List<TradeCard>,
    /** What [fromUser] offers, out of their own binders. */
    val give: List<TradeCard>,
    val message: String?,
    val reply: String?,
    val status: TradeStatus,
    val fromApplied: Boolean,
    val toApplied: Boolean,
    val updatedAt: String
)

data class Overview(
    val me: Profile?,
    val people: Map<String, Profile>,
    val friends: List<FriendLink>,
    val pods: List<Pod>,
    val sharedWithMe: List<SharedSummary>,
    val myShares: List<Share>,
    val trades: List<Trade>
) {
    fun person(id: String): Profile? = if (me?.userId == id) me else people[id]
    val acceptedFriends: List<FriendLink> get() = friends.filter { it.accepted }
    fun isFriend(id: String) = friends.any { it.userId == id && it.accepted }
}

data class Inbox(val friendRequests: Int = 0, val trades: Int = 0) {
    val total: Int get() = friendRequests + trades
}

/** A deck or binder someone shared: its owner, and the item's JSON as the apps sync it. */
data class SharedItem(val owner: Profile, val kind: ShareKind, val data: String)

data class MatchSeat(val seat: Int, val profile: Profile)

data class Match(val id: String, val code: String)

// ---- Parsing ----

private fun JSONObject.str(name: String): String? = if (isNull(name)) null else optString(name)

internal fun parseProfile(o: JSONObject) = Profile(
    userId = o.getString("user_id"),
    username = o.getString("username"),
    displayName = o.getString("display_name"),
    avatarPath = o.str("avatar_path")
)

internal fun parseTradeCards(a: JSONArray?): List<TradeCard> = if (a == null) emptyList() else (0 until a.length()).map { i ->
    val o = a.getJSONObject(i)
    TradeCard(
        scryfallId = o.getString("scryfallId"),
        name = o.getString("name"),
        imageUrl = o.str("imageUrl"),
        foil = o.optBoolean("foil"),
        quantity = o.optInt("quantity", 1),
        collectionId = o.str("collectionId")
    )
}

internal fun tradeCardsJson(cards: List<TradeCard>): JSONArray = JSONArray().apply {
    cards.forEach { c ->
        put(JSONObject().apply {
            put("scryfallId", c.scryfallId)
            put("name", c.name)
            c.imageUrl?.let { put("imageUrl", it) }
            put("foil", c.foil)
            put("quantity", c.quantity)
            c.collectionId?.let { put("collectionId", it) }
        })
    }
}

private fun parseTrade(o: JSONObject) = Trade(
    id = o.getString("id"),
    fromUser = o.getString("from_user"),
    toUser = o.getString("to_user"),
    want = parseTradeCards(o.optJSONArray("want")),
    give = parseTradeCards(o.optJSONArray("give")),
    message = o.str("message"),
    reply = o.str("reply"),
    status = runCatching { TradeStatus.valueOf(o.getString("status").uppercase()) }.getOrDefault(TradeStatus.CANCELLED),
    fromApplied = o.optBoolean("from_applied"),
    toApplied = o.optBoolean("to_applied"),
    updatedAt = o.optString("updated_at")
)

private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).map { transform(getJSONObject(it)) }

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }

internal fun parseOverview(o: JSONObject): Overview {
    val people = o.optJSONObject("people")
    return Overview(
        me = o.optJSONObject("me")?.let(::parseProfile),
        people = people?.keys()?.asSequence()?.associateWith { parseProfile(people.getJSONObject(it)) }.orEmpty(),
        friends = o.optJSONArray("friends").mapObjects {
            FriendLink(it.getString("user_id"), it.getString("status") == "accepted", it.optBoolean("incoming"))
        },
        pods = o.optJSONArray("pods").mapObjects {
            Pod(it.getString("id"), it.getString("name"), it.getString("owner"), it.optJSONArray("members").strings())
        },
        sharedWithMe = o.optJSONArray("shared_with_me").mapObjects {
            SharedSummary(it.getString("owner"), ShareKind.of(it.getString("kind")), it.getString("item_id"), it.str("name"), it.str("cover"), it.optInt("cards"))
        },
        myShares = o.optJSONArray("my_shares").mapObjects(::parseShare),
        trades = o.optJSONArray("trades").mapObjects(::parseTrade)
    )
}

internal fun parseShare(o: JSONObject) = Share(
    kind = ShareKind.of(o.getString("kind")),
    itemId = o.getString("item_id"),
    allFriends = o.optBoolean("all_friends"),
    podIds = o.optJSONArray("pod_ids").strings(),
    linkToken = o.str("link_token")
)
