package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.GoalActivity
import kotlinx.coroutines.CancellationException
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
//
// Completed goals (20261008110000_goal_activity.sql) are the same: until goal_activity_version
// answers, the feed has none, the "Share completed goals" switch is hidden and noting a completion
// does nothing — every call about them fails silently.

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
    onMine = o.optBoolean("on_mine"),
    goalKind = o.s("goal_kind")
)

/** friends_activity's (or activity_feed's) answer; items without a person, other than league news, are left out. */
internal fun parseFeed(text: String): List<FeedItem> = array(text).objects(::parseFeedItem).filter { it.actor != null || it.kind == "league" }

internal fun parsePrefs(text: String): ActivityPrefs {
    val t = text.trim()
    if (!t.startsWith("{")) return ActivityPrefs()
    val o = JSONObject(t)
    val d = ActivityPrefs()
    fun b(key: String, default: Boolean) = if (o.has(key) && !o.isNull(key)) o.optBoolean(key, default) else default
    return ActivityPrefs(b("decks", d.decks), b("for_trade", d.forTrade), b("selling", d.selling), b("leagues", d.leagues), b("goals", d.goals))
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

    private val _goals = MutableStateFlow<Boolean?>(null)
    /** Whether the server has the completed-goals functions; null until asked (or while offline). */
    val goalsAvailable: StateFlow<Boolean?> = _goals.asStateFlow()

    fun reset() {
        _available.value = null
        _goals.value = null
    }

    /** Asks the server once whether the completed-goals functions are there. Never throws. */
    suspend fun checkGoals(): Boolean {
        _goals.value?.let { return it }
        return try {
            val ok = (api.call("goal_activity_version").trim().toIntOrNull() ?: 0) >= 1
            _goals.value = ok
            ok
        } catch (e: SocialException) {
            if (e.code == "unavailable") _goals.value = false
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

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
        val main = parseFeed(api.call(fn, JSONObject().put("p_before", before ?: JSONObject.NULL).put("p_limit", limit)))
        return mergeFeeds(main, goalFeed(before, limit), limit)
    }

    /** Friends' completed goals, for [feed]; none while the server doesn't have them, or when they can't be read. */
    private suspend fun goalFeed(before: Long?, limit: Int): List<FeedItem> {
        if (!checkGoals()) return emptyList()
        return try {
            parseFeed(api.call("friends_goal_activity", JSONObject().put("p_before", before ?: JSONObject.NULL).put("p_limit", limit)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Tells friends' Activity the user completed a goal (the server notes each goal once, and only
     * while "Share completed goals" is on). Fails silently: a server without it, offline, signed out.
     */
    suspend fun postGoalCompleted(goal: GoalActivity) {
        if (!checkGoals()) return
        try {
            api.call(
                "post_goal_completed",
                JSONObject().put("p_goal_id", goal.goalId).put("p_name", goal.name).put("p_kind", goal.kind).put("p_cards", goal.cards)
                    .put("p_cover", goal.cover ?: JSONObject.NULL)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not there yet, offline or signed out: the goal just isn't announced.
        }
    }

    // ---- What friends' Activity shows of the user ----

    /** The switches; [ActivityPrefs.goals] from goal_activity_pref once the server has it (on until then). */
    suspend fun prefs(): ActivityPrefs {
        val p = parsePrefs(api.call("activity_prefs"))
        if (!checkGoals()) return p
        val goals = try { api.call("goal_activity_pref").trim() } catch (e: CancellationException) { throw e } catch (e: Exception) { "" }
        return if (goals == "false") p.copy(goals = false) else p
    }

    /** Saves the switches: the four of set_activity_prefs, and "Share completed goals" apart when it changed. */
    suspend fun setPrefs(p: ActivityPrefs, before: ActivityPrefs? = null) {
        if (before == null || before.copy(goals = p.goals) != p) api.call(
            "set_activity_prefs",
            JSONObject().put("p_decks", p.decks).put("p_for_trade", p.forTrade).put("p_selling", p.selling).put("p_leagues", p.leagues)
        )
        if ((before == null || before.goals != p.goals) && checkGoals()) api.call("set_goal_activity_pref", JSONObject().put("p_on", p.goals))
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
