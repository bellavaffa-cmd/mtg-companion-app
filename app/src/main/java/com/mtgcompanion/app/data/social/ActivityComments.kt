package com.mtgcompanion.app.data.social

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

// The friends' Activity feed, what it may show of the user, and comments on shared decks: calls to
// the server functions in supabase/migrations/20261006080000_activity_comments.sql. The web app's
// twin is src/social/activity.ts.
//
// Until that migration is applied the functions aren't there: [ActivityComments.available] says
// so, the Activity tab keeps reading activity_feed (SocialMore), and comments and the Privacy
// switches stay hidden.

private fun JSONObject.s(name: String): String? = if (!has(name) || isNull(name)) null else optString(name)
private fun JSONObject.i(name: String): Int? = if (!has(name) || isNull(name)) null else optInt(name)

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { if (isNull(it)) null else optString(it) }

private fun <T> JSONArray?.objects(transform: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it)?.let(transform) }

private fun array(text: String): JSONArray = text.trim().let { if (it.startsWith("[")) JSONArray(it) else JSONArray() }

internal fun parseFeedItem(o: JSONObject) = FeedItem(
    kind = o.optString("kind"),
    actor = o.optJSONObject("actor")?.let(::parseProfile),
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
    cards = o.optJSONArray("cards").objects { c -> c.optString("name") to c.s("imageUrl") },
    newDeck = o.optBoolean("new_deck"),
    wanted = o.optJSONArray("wanted").strings(),
    wantedCount = o.i("wanted_count"),
    seasonId = o.s("season_id"),
    ended = o.optBoolean("ended"),
    champion = o.s("champion"),
    championIds = o.optJSONArray("champion_ids").strings(),
    quiet = o.optJSONArray("quiet").strings(),
    startsOn = o.s("starts_on"),
    endsOn = o.s("ends_on"),
    maxNights = o.i("max_nights"),
    itemOwner = o.s("item_owner"),
    commentId = o.s("comment_id"),
    body = o.s("body"),
    cardName = o.s("card_name"),
    reply = o.optBoolean("reply"),
    onMine = o.optBoolean("on_mine")
)

/** friends_activity's (or activity_feed's) answer; items without a person, other than league news, are left out. */
internal fun parseFeed(text: String): List<FeedItem> = array(text).objects(::parseFeedItem).filter { it.actor != null || it.kind == "league" }

internal fun parsePrefs(text: String): ActivityPrefs {
    val t = text.trim()
    if (!t.startsWith("{")) return ActivityPrefs()
    val o = JSONObject(t)
    val d = ActivityPrefs()
    fun b(key: String, default: Boolean) = if (o.has(key) && !o.isNull(key)) o.optBoolean(key, default) else default
    return ActivityPrefs(b("decks", d.decks), b("for_trade", d.forTrade), b("selling", d.selling), b("leagues", d.leagues))
}

internal fun parseSelling(text: String): List<SellingCard>? {
    if (text.trim().let { it.isEmpty() || it == "null" }) return null
    return array(text).objects { o ->
        SellingCard(
            itemId = o.optString("item_id"),
            itemName = o.s("item_name"),
            scryfallId = o.optString("scryfall_id"),
            name = o.optString("name"),
            imageUrl = o.s("image_url"),
            forSale = o.optInt("for_sale"),
            quantity = o.optInt("quantity"),
            foilQuantity = o.optInt("foil_quantity"),
            condition = o.s("condition"),
            wanted = o.optBoolean("wanted")
        )
    }
}

internal fun parseComment(o: JSONObject) = DeckComment(
    id = o.getString("id"),
    parent = o.s("parent"),
    author = parseProfile(o.getJSONObject("author")),
    body = o.optString("body"),
    cardName = o.s("card_name"),
    cardImage = o.s("card_image"),
    hidden = o.optBoolean("hidden"),
    createdAt = o.optLong("created_at"),
    mine = o.optBoolean("mine")
)

internal fun parseDeckComments(text: String): DeckComments? {
    val t = text.trim()
    if (!t.startsWith("{")) return null
    val o = JSONObject(t)
    return DeckComments(o.optBoolean("is_owner"), o.optBoolean("can_comment"), o.optJSONArray("comments").objects(::parseComment))
}

/**
 * The calls themselves, and whether the server has them. One per [SocialRepository]; forgets the
 * answer when the account changes.
 */
class ActivityComments(private val api: SocialApi, private val more: SocialMore) {
    private val _available = MutableStateFlow<Boolean?>(null)
    /** Null until asked (or while it can't be told — offline). */
    val available: StateFlow<Boolean?> = _available.asStateFlow()

    fun reset() { _available.value = null }

    /** Asks the server once whether these functions are there. */
    suspend fun check(): Boolean {
        _available.value?.let { return it }
        return try {
            val ok = (api.call("activity_comments_version").trim().toIntOrNull() ?: 0) >= 1
            _available.value = ok
            ok
        } catch (e: SocialException) {
            if (e.code == "unavailable") _available.value = false
            false
        }
    }

    // ---- The feed ----

    /**
     * Friends' activity, newest first: [limit] items before [before] (ms; null: now). From
     * friends_activity, or the older activity_feed when the server doesn't have it yet.
     */
    suspend fun feed(before: Long? = null, limit: Int = 30): List<FeedItem> {
        val fn = if (check()) "friends_activity" else "activity_feed"
        return parseFeed(api.call(fn, JSONObject().put("p_before", before ?: JSONObject.NULL).put("p_limit", limit)))
    }

    // ---- What friends' Activity shows of the user ----

    suspend fun prefs(): ActivityPrefs = parsePrefs(api.call("activity_prefs"))

    suspend fun setPrefs(p: ActivityPrefs) {
        api.call(
            "set_activity_prefs",
            JSONObject().put("p_decks", p.decks).put("p_for_trade", p.forTrade).put("p_selling", p.selling).put("p_leagues", p.leagues)
        )
    }

    /** [owner]'s To sell list, their cards on the user's wishlists first; null when it can't be seen. */
    suspend fun sellingList(owner: String): List<SellingCard>? = parseSelling(api.call("selling_list", JSONObject().put("p_owner", owner)))

    /** "Ask <name> for it": the wanted cards out of what [owner] marked for trade. */
    suspend fun askFor(owner: String, wanted: List<String>): List<TradeCard> =
        askCards(runCatching { more.forTradeList(owner) }.getOrNull().orEmpty(), wanted)

    // ---- Comments on shared decks ----

    /** The comments on [owner]'s deck [deckId]; null when the user can't see the deck. */
    suspend fun comments(owner: String, deckId: String): DeckComments? =
        parseDeckComments(api.call("deck_comments", JSONObject().put("p_owner", owner).put("p_deck", deckId)))

    suspend fun post(owner: String, deckId: String, body: String, parent: String?, cardName: String?, cardImage: String?): DeckComment =
        parseComment(JSONObject(api.call(
            "post_deck_comment",
            JSONObject().put("p_owner", owner).put("p_deck", deckId).put("p_body", body.trim())
                .put("p_parent", parent ?: JSONObject.NULL).put("p_card_name", cardName ?: JSONObject.NULL).put("p_card_image", cardImage ?: JSONObject.NULL)
        )))

    suspend fun delete(id: String) { api.call("delete_deck_comment", JSONObject().put("p_comment", id)) }

    suspend fun hide(id: String, hidden: Boolean) { api.call("hide_deck_comment", JSONObject().put("p_comment", id).put("p_hidden", hidden)) }
}
