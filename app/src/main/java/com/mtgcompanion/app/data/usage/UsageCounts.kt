package com.mtgcompanion.app.data.usage

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

// Anonymous feature-usage counts: the rules, with no storage or network (those are in Usage.kt).
// The web app has the same rules and the same names (src/usage/usageCounts.ts), and both send to
// the record_usage function in the owner's Supabase project.
//
// What's counted is a closed list: screens opened and a few feature actions. Never card names, deck
// contents, free text or the account — only an anonymous random install id (a new one every 90
// days), the platform, the app version and the day. Counts are kept per day and sent once the day
// is over, at most one try a day; a day that's never sent is dropped after a week.

/** Feature actions, the same in both apps. */
enum class UsageAction(val id: String) {
    DECK_CREATED("deck_created"),
    CARD_SCANNED("card_scanned"),
    CARDS_IMPORTED("cards_imported"),
    GAME_STARTED("game_started"),
    GAME_RECORDED("game_recorded"),
    PULL_LIST_STARTED("pull_list_started"),
    PLACE_CREATED("place_created"),
    LOAN_CREATED("loan_created"),
    MESSAGE_SENT("message_sent"),
    TRADE_PROPOSED("trade_proposed"),
    FILTER_SAVED("filter_saved"),
    EVENT_STARTED("event_started")
}

data class UsageBucket(
    val day: String,
    /** The install id the day was counted under, so a rotation doesn't tie old days to the new id. */
    val install: String,
    val counts: Map<String, Int>
)

data class UsageState(
    val install: String = "",
    /** The day the install id was made. */
    val installDay: String = "",
    /** The last day a send was tried. */
    val lastTry: String = "",
    val buckets: List<UsageBucket> = emptyList()
)

data class UsageBatch(val day: String, val install: String, val counts: Map<String, Int>)

object UsageCounts {
    const val ROTATE_DAYS = 90
    const val KEEP_DAYS = 7
    const val MAX_COUNT = 10000
    const val MAX_EVENTS_PER_SEND = 100

    /** Screens, by the names both apps count them under (as "screen_<name>"). */
    val SCREENS: List<String> = listOf(
        "home", "search", "search_results", "collection", "collection_detail", "decks", "deck", "new_deck", "precons",
        "settings", "settings_section", "account", "scan", "rules", "life_counter", "play", "events", "event_new", "event",
        "game_night", "value_history", "spread_thin", "playgroup", "friends", "friend", "trades", "trade_new",
        "shared_item", "shared_collection", "friend_shared", "messages", "conversation", "for_trade", "qr_scan", "remote",
        "card", "place", "check", "check_results", "place_fit", "put_away", "loans", "lend", "copy_history",
        "value_by_place", "sort_pile", "place_label", "pull_list", "put_back", "scan_tick", "tag_binder", "set_cards",
        "token_badge", "add_friend", "approve_login", "join_seat", "get_app"
    )

    fun screenEvent(screen: String) = "screen_$screen"

    private val allowed: Set<String> = UsageAction.entries.map { it.id }.toSet() + SCREENS.map { screenEvent(it) }
    fun isEvent(event: String) = event in allowed

    private val DAY = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val INSTALL = Regex("^[0-9a-f]{32}$")

    /** Days from [from] to [to] ("YYYY-MM-DD"); null when either isn't a day. */
    fun daysBetween(from: String, to: String): Long? {
        if (!DAY.matches(from) || !DAY.matches(to)) return null
        return runCatching { LocalDate.parse(to).toEpochDay() - LocalDate.parse(from).toEpochDay() }.getOrNull()
    }

    /** A fresh install id: 32 random hex digits. */
    fun newInstallId(random: () -> String = { UUID.randomUUID().toString() }): String =
        random().replace("-", "").lowercase()

    /** Makes an install id when there's none, or the one there is 90 days old. */
    fun withInstall(state: UsageState, today: String, newId: () -> String): UsageState {
        val age = daysBetween(state.installDay, today)
        if (INSTALL.matches(state.install) && age != null && age >= 0 && age < ROTATE_DAYS) return state
        return state.copy(install = newId(), installDay = today)
    }

    /** Drops days more than a week old (and days ahead of today, from a clock that moved back). */
    fun expire(state: UsageState, today: String): UsageState {
        val buckets = state.buckets.filter { b ->
            val age = daysBetween(b.day, today)
            age != null && age >= -1 && age <= KEEP_DAYS
        }
        return if (buckets.size == state.buckets.size) state else state.copy(buckets = buckets)
    }

    /** Counts one [event] today. Off, or an event not on the list, counts nothing. */
    fun record(state: UsageState, event: String, today: String, enabled: Boolean, newId: () -> String): UsageState {
        if (!enabled || !isEvent(event)) return state
        val s = expire(withInstall(state, today, newId), today)
        val i = s.buckets.indexOfFirst { it.day == today && it.install == s.install }
        val bucket = if (i >= 0) s.buckets[i] else UsageBucket(today, s.install, emptyMap())
        val counts = bucket.counts + (event to minOf((bucket.counts[event] ?: 0) + 1, MAX_COUNT))
        val buckets = if (i >= 0) {
            s.buckets.mapIndexed { j, b -> if (j == i) b.copy(counts = counts) else b }
        } else {
            s.buckets + bucket.copy(counts = counts)
        }
        return s.copy(buckets = buckets)
    }

    /**
     * What to send now: each finished day (before today), at most [MAX_EVENTS_PER_SEND] events a
     * batch — nothing when off or already tried today.
     */
    fun dueBatches(state: UsageState, today: String, enabled: Boolean): List<UsageBatch> {
        if (!enabled || state.lastTry == today) return emptyList()
        return expire(state, today).buckets
            .filter { (daysBetween(it.day, today) ?: 0) >= 1 }
            .flatMap { b ->
                b.counts.entries.filter { it.value > 0 }.chunked(MAX_EVENTS_PER_SEND)
                    .map { chunk -> UsageBatch(b.day, b.install, chunk.associate { it.key to it.value }) }
            }
    }

    /** A send was tried today (it's not tried again until tomorrow, whatever came of it). */
    fun markTried(state: UsageState, today: String): UsageState = expire(state, today).copy(lastTry = today)

    /** A batch went: its counts are dropped. */
    fun markSent(state: UsageState, batch: UsageBatch): UsageState = state.copy(
        buckets = state.buckets.mapNotNull { b ->
            if (b.day != batch.day || b.install != batch.install) b
            else (b.counts - batch.counts.keys).takeIf { it.isNotEmpty() }?.let { b.copy(counts = it) }
        }
    )

    /** Turned off: everything kept is forgotten, install id included. */
    fun optedOut(): UsageState = UsageState()

    fun toJson(state: UsageState): String = JSONObject()
        .put("install", state.install)
        .put("installDay", state.installDay)
        .put("lastTry", state.lastTry)
        .put("buckets", JSONArray().apply {
            state.buckets.forEach { b ->
                put(JSONObject().put("day", b.day).put("install", b.install)
                    .put("counts", JSONObject().apply { b.counts.forEach { (k, v) -> put(k, v) } }))
            }
        })
        .toString()

    /** A stored state, checked; anything that doesn't fit is dropped. */
    fun parse(json: String?): UsageState = runCatching {
        val o = JSONObject(json ?: return UsageState())
        val list = o.optJSONArray("buckets") ?: JSONArray()
        val buckets = (0 until list.length()).mapNotNull { i ->
            val b = list.optJSONObject(i) ?: return@mapNotNull null
            val day = b.optString("day")
            val install = b.optString("install")
            val c = b.optJSONObject("counts")
            if (!DAY.matches(day) || !INSTALL.matches(install) || c == null) return@mapNotNull null
            val counts = c.keys().asSequence().mapNotNull { k ->
                val n = c.opt(k)
                if (isEvent(k) && n is Int && n > 0) k to minOf(n, MAX_COUNT) else null
            }.toMap()
            if (counts.isEmpty()) null else UsageBucket(day, install, counts)
        }
        UsageState(o.optString("install"), o.optString("installDay"), o.optString("lastTry"), buckets)
    }.getOrElse { UsageState() }

    // ---- Screens ----

    /** Routes whose name isn't just their fixed parts joined (NavGraph.kt's Routes). */
    private val ROUTE_NAMES = mapOf(
        "collection/{collectionId}" to "collection_detail",
        "detail/{cardName}" to "card",
        "settings/{section}" to "settings_section",
        "shared/{owner}/{kind}/{itemId}" to "shared_item",
        "shared_link/{token}" to "shared_item"
    )

    /**
     * The screen a nav route (its pattern, "deck/{deckId}?tab={tab}") shows, or null for one not on
     * the list. Arguments are never kept.
     */
    fun screenOfRoute(route: String?): String? {
        val path = route?.substringBefore('?') ?: return null
        val name = ROUTE_NAMES[path] ?: path.split('/').filter { it.isNotEmpty() && !it.startsWith("{") }.joinToString("_")
        return name.takeIf { it in SCREENS }
    }
}
