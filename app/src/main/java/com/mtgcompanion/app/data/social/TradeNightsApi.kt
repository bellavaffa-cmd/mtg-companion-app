package com.mtgcompanion.app.data.social

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

// Trade nights: calls to the server functions in
// supabase/migrations/20261008100000_trade_nights.sql. The rules behind the screen are in
// TradeNights.kt. The web app's twin is src/social/tradeNightsApi.ts.
//
// Until that migration is applied the functions aren't there: [TradeNightsApi.available] says so, and
// a game night simply has no Trades section.

/** Someone's list as the server sends it. */
data class NightListRow(val user: Profile, val sources: List<NightSource>, val cards: List<NightCard>, val wants: List<NightWant>, val updatedAt: Long) {
    fun asList() = NightList(user.userId, user.displayName, cards, wants)
}

/** The trade side of a night for the user: are they Going, is it open, their list, the others' lists and the night's trades. */
data class TradeNight(val nightId: String, val going: Boolean, val open: Boolean, val mine: NightListRow?, val others: List<NightListRow>, val trades: List<Trade>)

/** One per [SocialRepository]; forgets what it knows when the account changes. */
class TradeNightsApi(private val api: SocialApi) {
    private val _available = MutableStateFlow<Boolean?>(null)
    /** Null until asked (or while it can't be told — offline). */
    val available: StateFlow<Boolean?> = _available.asStateFlow()

    fun reset() { _available.value = null }

    /** Asks the server once whether these functions are there. */
    suspend fun check(): Boolean {
        _available.value?.let { return it }
        return try {
            val ok = (api.call("trade_nights_version").trim().toIntOrNull() ?: 0) >= 1
            _available.value = ok
            ok
        } catch (e: SocialException) {
            if (e.code == "unavailable") _available.value = false
            false
        }
    }

    /** The trade side of a night for the user, or null when they aren't invited. */
    suspend fun night(nightId: String): TradeNight? = parseTradeNight(api.call("trade_night", JSONObject().put("p_night", nightId)))

    /** Puts up (or replaces) the user's list for the night; answers the night's trade side. */
    suspend fun share(nightId: String, sources: List<NightSource>, cards: List<NightCard>, wants: List<NightWant>): TradeNight? = parseTradeNight(
        api.call(
            "set_trade_night_list",
            JSONObject().put("p_night", nightId).put("p_sources", sourcesJson(sources)).put("p_cards", nightCardsJson(cards)).put("p_wants", wantsJson(wants))
        )
    )

    /** Takes the user's list for the night down. */
    suspend fun stop(nightId: String) { api.call("stop_trade_night_list", JSONObject().put("p_night", nightId)) }

    /** Proposes a trade to someone at the night (they needn't be a friend); answers its id. */
    suspend fun propose(nightId: String, to: String, want: List<TradeCard>, give: List<TradeCard>, message: String): String =
        api.call(
            "propose_night_trade",
            JSONObject().put("p_night", nightId).put("p_to", to).put("p_want", tradeCardsJson(want)).put("p_give", tradeCardsJson(give))
                .put("p_message", message.ifBlank { null } ?: JSONObject.NULL)
        ).trim().trim('"')
}

// ---- JSON ----

internal fun sourcesJson(sources: List<NightSource>): JSONArray = JSONArray().apply {
    sources.forEach { s -> put(JSONObject().put("kind", s.kind).put("name", s.name).apply { s.id?.let { put("id", it) } }) }
}

internal fun nightCardsJson(cards: List<NightCard>): JSONArray = JSONArray().apply {
    cards.forEach { c ->
        put(JSONObject().apply {
            put("scryfallId", c.scryfallId)
            put("name", c.name)
            c.imageUrl?.let { put("imageUrl", it) }
            put("foil", c.foil)
            put("quantity", c.quantity)
            c.collectionId?.let { put("collectionId", it) }
            c.condition?.let { put("condition", it) }
            put("spare", c.spare)
        })
    }
}

internal fun wantsJson(wants: List<NightWant>): JSONArray = JSONArray().apply {
    wants.forEach { put(JSONObject().put("name", it.name).put("weight", it.weight)) }
}

private fun JSONObject.strOrNull(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).takeIf { opt(k) is String }

internal fun parseNightCard(o: JSONObject?): NightCard? {
    if (o == null) return null
    val id = o.strOrNull("scryfallId") ?: return null
    val name = o.strOrNull("name") ?: return null
    val q = o.opt("quantity") as? Number ?: return null
    if (q.toDouble() < 1) return null
    return NightCard(
        id, name, o.strOrNull("imageUrl"), o.opt("foil") == true, q.toInt(), o.strOrNull("collectionId"), o.strOrNull("condition"), o.opt("spare") == true
    )
}

private fun parseSource(o: JSONObject?): NightSource? {
    if (o == null) return null
    return when (o.opt("kind")) {
        NightSource.BINDER -> o.strOrNull("id")?.let { NightSource.binder(it, o.strOrNull("name").orEmpty()) }
        NightSource.BAG -> NightSource(NightSource.BAG, null, o.strOrNull("name") ?: BAG_SOURCE_NAME)
        else -> null
    }
}

private fun JSONArray?.objectsOrNull(): List<JSONObject?> = if (this == null) emptyList() else (0 until length()).map { optJSONObject(it) }

private fun parseListRow(o: JSONObject?): NightListRow? {
    if (o == null) return null
    val u = o.optJSONObject("user") ?: return null
    val userId = u.strOrNull("user_id")?.takeIf { it.isNotEmpty() } ?: return null
    val user = Profile(userId, u.strOrNull("username").orEmpty(), u.strOrNull("display_name") ?: "Someone", u.strOrNull("avatar_path"))
    return NightListRow(
        user,
        o.optJSONArray("sources").objectsOrNull().mapNotNull(::parseSource),
        o.optJSONArray("cards").objectsOrNull().mapNotNull(::parseNightCard),
        o.optJSONArray("wants").objectsOrNull().mapNotNull { w ->
            val name = w?.strOrNull("name") ?: return@mapNotNull null
            val weight = (w.opt("weight") as? Number)?.toInt() ?: return@mapNotNull null
            if (weight > 0) NightWant(name, weight) else null
        },
        (o.opt("updatedAt") as? Number)?.toLong() ?: 0L
    )
}

/** trade_night's answer; null when the user isn't invited (or it isn't one). */
internal fun parseTradeNight(text: String): TradeNight? {
    val t = text.trim()
    if (!t.startsWith("{")) return null
    val o = JSONObject(t)
    val id = o.strOrNull("nightId") ?: return null
    return TradeNight(
        id,
        o.opt("going") == true,
        o.opt("open") == true,
        parseListRow(o.optJSONObject("mine")),
        o.optJSONArray("others").objectsOrNull().mapNotNull(::parseListRow),
        o.optJSONArray("trades").objectsOrNull().mapNotNull { x -> x?.takeIf { it.strOrNull("id") != null }?.let(::parseTrade) }
    )
}
