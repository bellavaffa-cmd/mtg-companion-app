package com.mtgcompanion.app.data.social

import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

// A pod's group chat — the rules behind the screen, kept apart so they can be tested. The server
// side is supabase/migrations/20261006070000_game_nights_chat.sql; the calls are GameNightsApi.kt.
// The web app's twin is the pod chat half of src/social/nightsLogic.ts (PodChatTest.kt ↔
// tests/social/nights.test.ts).

/** What a message shares or points at (the migration's pod_messages.ref). */
data class PodMessageRef(
    /** "night", "deck" or "card" (shared things and game nights); null for league results. */
    val type: String? = null,
    val nightId: String? = null,
    val startsAt: Long? = null,
    val place: String? = null,
    val ownerId: String? = null,
    val itemId: String? = null,
    val name: String? = null,
    val gameId: String? = null,
    val seasonId: String? = null,
    val season: String? = null
)

data class PodMessage(
    val id: Long,
    val podId: String,
    /** Null: posted by Manabind (a league result, a night moved or called off). */
    val sender: String?,
    /** "text", "share", "night", "league" or "system". */
    val kind: String,
    val body: String,
    val ref: PodMessageRef?,
    /** ms */
    val createdAt: Long,
    /** In the chats list only: the sender's name. */
    val senderName: String? = null
)

/** One pod's chat in the list: its last message and how many wait unread. */
data class PodChat(val podId: String, val name: String, val members: Int, val last: PodMessage?, val unread: Int)

/** The longest message the server takes. */
const val POD_MESSAGE_MAX = 2000

private val KINDS = setOf("text", "share", "night", "league", "system")

private fun JSONObject.s(name: String): String? = if (!has(name) || isNull(name)) null else optString(name)
private fun JSONObject.l(name: String): Long? = if (!has(name) || isNull(name)) null else optLong(name)

fun parsePodMessage(o: JSONObject?): PodMessage? {
    if (o == null || !o.has("id") || o.isNull("id")) return null
    val r = o.optJSONObject("ref")
    return PodMessage(
        id = o.optLong("id"),
        podId = o.s("podId").orEmpty(),
        sender = o.s("sender"),
        kind = o.s("kind")?.takeIf { it in KINDS } ?: "text",
        body = o.s("body").orEmpty(),
        ref = r?.let {
            PodMessageRef(it.s("type"), it.s("nightId"), it.l("startsAt"), it.s("place"), it.s("ownerId"), it.s("itemId"), it.s("name"), it.s("gameId"), it.s("seasonId"), it.s("season"))
        },
        createdAt = o.l("createdAt") ?: 0L,
        senderName = o.s("senderName")
    )
}

fun parsePodMessages(text: String): List<PodMessage> {
    val t = text.trim()
    if (!t.startsWith("[")) return emptyList()
    val a = JSONArray(t)
    return (0 until a.length()).mapNotNull { parsePodMessage(a.optJSONObject(it)) }
}

fun parsePodChats(text: String): List<PodChat> {
    val t = text.trim()
    if (!t.startsWith("[")) return emptyList()
    val a = JSONArray(t)
    return (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        val podId = o.s("podId") ?: return@mapNotNull null
        PodChat(podId, o.s("name").orEmpty(), o.optInt("members"), parsePodMessage(o.optJSONObject("last")), o.optInt("unread"))
    }
}

/** A share's JSON for send_pod_message. */
fun podRefJson(ref: PodMessageRef): JSONObject = JSONObject().apply {
    ref.type?.let { put("type", it) }
    ref.nightId?.let { put("nightId", it) }
    ref.itemId?.let { put("itemId", it) }
    ref.name?.let { put("name", it) }
}

/** [list] with [incoming] added: each message once (by id), oldest first. */
fun mergePodMessages(list: List<PodMessage>, incoming: List<PodMessage>): List<PodMessage> {
    val byId = LinkedHashMap<Long, PodMessage>()
    (list + incoming).forEach { byId[it.id] = it }
    return byId.values.sortedBy { it.id }
}

/** What a shared thing is, in a few words: "Shared a game night", "Shared a deck: Krenko". */
fun shareLine(ref: PodMessageRef?): String = when (ref?.type) {
    "night" -> "Shared a game night"
    "deck" -> "Shared a deck: ${ref.name.orEmpty()}".trim()
    "card" -> "Shared a card: ${ref.name.orEmpty()}".trim()
    else -> "Shared something"
}

/** A chat's last message for the list: "You: …", "Sam: …", or what Manabind posted, cut to 80 characters. */
fun podPreview(last: PodMessage?, me: String): String {
    if (last == null) return "No messages yet"
    val what = when (last.kind) {
        "night" -> "Planned a game night"
        "share" -> last.body.trim().ifEmpty { shareLine(last.ref) }
        else -> last.body
    }
    val text = what.replace(Regex("\\s+"), " ").trim()
    val cut = if (text.length > 80) text.take(79) + "…" else text
    val sender = last.sender ?: return cut
    return "${if (sender == me) "You" else last.senderName?.takeIf { it.isNotEmpty() } ?: "Someone"}: $cut"
}

/** "Priya, Sam, Alex, Jo and you" — the pod's people under its name ([others]: everyone but the user). */
fun membersLine(others: List<String>): String = if (others.isEmpty()) "Just you" else "${others.joinToString(", ")} and you"

/** A day heading in the chat: "Today", "Yesterday", "Tuesday" (this past week), then "10 Oct" (with the year when it isn't this one). */
fun chatDayLabel(ms: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val a = zoned(ms, zone).toLocalDate()
    val b = zoned(now, zone).toLocalDate()
    val days = calendarDays(a, b)
    return when {
        days <= 0 -> "Today"
        days == 1L -> "Yesterday"
        days < 7 -> LONG_DAYS[a.dayOfWeek.value - 1]
        else -> "${a.dayOfMonth} ${SHORT_MONTHS[a.monthValue - 1]}" + if (a.year == b.year) "" else " ${a.year}"
    }
}

/** Messages within this long of the one before, from the same person, don't repeat the name. */
const val GROUP_MS = 5 * 60 * 1000L

sealed interface ChatItem {
    val key: String
    data class Day(override val key: String, val label: String) : ChatItem
    data class Message(override val key: String, val message: PodMessage, val mine: Boolean, val showName: Boolean) : ChatItem
}

private fun personal(kind: String) = kind == "text" || kind == "share" || kind == "night"

/** The chat as shown: a heading for each day, and each person's name over the first of a run of their messages. */
fun chatItems(messages: List<PodMessage>, me: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<ChatItem> {
    val out = mutableListOf<ChatItem>()
    var lastDay = ""
    var prev: PodMessage? = null
    for (m in messages) {
        val date = zoned(m.createdAt, zone).toLocalDate()
        val dayKey = "${date.year}-${date.monthValue}-${date.dayOfMonth}"
        var newDay = false
        if (dayKey != lastDay) {
            out += ChatItem.Day("day-$dayKey", chatDayLabel(m.createdAt, now, zone))
            lastDay = dayKey
            newDay = true
        }
        val person = m.sender != null && personal(m.kind)
        val p = prev
        val runs = !newDay && p != null && p.sender == m.sender && personal(p.kind) && m.createdAt - p.createdAt <= GROUP_MS
        out += ChatItem.Message("m-${m.id}", m, m.sender == me, person && m.sender != me && !runs)
        prev = m
    }
    return out
}

/** How many of [messages] wait unread past [readId] — others' and Manabind's, not the user's own. */
fun unreadIn(messages: List<PodMessage>, readId: Long, me: String): Int = messages.count { it.id > readId && it.sender != me }

/** One line of the Chats list: a direct conversation or a pod's chat, by when it last had a message. */
data class ChatRow(val kind: String, val id: String, val at: Long?, val unread: Int)

/** Direct conversations and pod chats in one list, newest first; ones with no messages last, in the order given. */
fun mergeChatRows(dms: List<ChatRow>, pods: List<ChatRow>): List<ChatRow> =
    (dms + pods).withIndex().sortedWith(compareByDescending<IndexedValue<ChatRow>> { it.value.at ?: -1L }.thenBy { it.index }).map { it.value }

fun totalUnread(rows: List<ChatRow>): Int = rows.sumOf { maxOf(0, it.unread) }

/** Under a league result: "Table: Priya 14 · you 11 · Sam 9" (the top [top]). Null before any points. */
fun tableLine(standings: List<Triple<String?, String, Int>>, me: String, top: Int = 3): String? {
    if (standings.isEmpty()) return null
    return "Table: " + standings.take(top).joinToString(" · ") { (userId, name, points) -> "${if (userId == me) "you" else name} $points" }
}

/** The label over a league result: "LEAGUE · SEASON 2", or "GAME" outside a season. */
fun leagueLabel(ref: PodMessageRef?): String = ref?.season?.takeIf { it.isNotEmpty() }?.let { "LEAGUE · ${it.uppercase()}" } ?: "GAME"
