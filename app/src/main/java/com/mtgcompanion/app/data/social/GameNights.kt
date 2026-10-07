package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.ui.lifecounter.NightPlayer
import com.mtgcompanion.app.ui.lifecounter.NightPlayerKind
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// Game night invites — the rules behind the screens, kept apart so they can be tested. The server
// side is supabase/migrations/20261006070000_game_nights_chat.sql; the calls are GameNightsApi.kt.
// The web app's twin is src/social/nightsLogic.ts, case for case (GameNightsTest.kt ↔
// tests/social/nights.test.ts).

enum class RsvpAnswer(val wire: String) {
    GOING("going"), MAYBE("maybe"), CANT("cant");

    companion object {
        fun of(wire: String?): RsvpAnswer? = entries.firstOrNull { it.wire == wire }
    }
}

/** One person invited to a night: a pod member, or a friend asked along ([member] false). */
data class NightInvitee(
    val user: Profile,
    val member: Boolean,
    val answer: RsvpAnswer?,
    /** The deck they'll bring, as they named it. */
    val deck: String?,
    /** ms */
    val answeredAt: Long?
)

/** A game night as game_nights / game_night answer it. Times in ms. */
data class NightInvite(
    val id: String,
    val podId: String,
    val podName: String,
    val organiser: Profile,
    val startsAt: Long,
    /** The organiser's time zone (IANA). */
    val tz: String,
    /** Where: "Priya's". */
    val place: String,
    val note: String?,
    val cancelled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /** The pod's league season it counts for (id to name), if one runs that day. */
    val seasonId: String?,
    val seasonName: String?,
    /** The organiser first, then by name. */
    val invitees: List<NightInvitee>
)

/** How long a night lasts, for calendars. */
const val NIGHT_HOURS = 4
/** The longest place the server takes. */
const val PLACE_MAX = 80
const val NOTE_MAX = 500
/** The reminder comes this long before the night. */
const val REMINDER_BEFORE_MS = 24L * 60 * 60 * 1000

// ---- Parsing ----

private fun JSONObject.s(name: String): String? = if (!has(name) || isNull(name)) null else optString(name)
private fun JSONObject.l(name: String): Long? = if (!has(name) || isNull(name)) null else optLong(name)

private fun profileOf(o: JSONObject?): Profile = Profile(
    userId = o?.s("user_id").orEmpty(),
    username = o?.s("username").orEmpty(),
    displayName = o?.s("display_name") ?: "Someone",
    avatarPath = o?.s("avatar_path")
)

/** One night from the server's JSON; null when it isn't one. */
fun parseNightInvite(o: JSONObject?): NightInvite? {
    val id = o?.s("id") ?: return null
    val season = o.optJSONObject("season")
    val invitees = o.optJSONArray("invitees") ?: JSONArray()
    return NightInvite(
        id = id,
        podId = o.s("podId").orEmpty(),
        podName = o.s("podName").orEmpty(),
        organiser = profileOf(o.optJSONObject("organiser")),
        startsAt = o.l("startsAt") ?: 0L,
        tz = o.s("tz") ?: "UTC",
        place = o.s("place").orEmpty(),
        note = o.s("note"),
        cancelled = o.optBoolean("cancelled", false),
        createdAt = o.l("createdAt") ?: 0L,
        updatedAt = o.l("updatedAt") ?: 0L,
        seasonId = season?.s("id"),
        seasonName = season?.s("id")?.let { season.s("name").orEmpty() },
        invitees = (0 until invitees.length()).mapNotNull { i ->
            val x = invitees.optJSONObject(i) ?: return@mapNotNull null
            NightInvitee(profileOf(x.optJSONObject("user")), x.optBoolean("member", true), RsvpAnswer.of(x.s("answer")), x.s("deck"), x.l("answeredAt"))
        }
    )
}

fun parseNightInvite(text: String): NightInvite? = text.trim().takeIf { it.startsWith("{") }?.let { parseNightInvite(JSONObject(it)) }

fun parseNightInvites(text: String): List<NightInvite> {
    val t = text.trim()
    if (!t.startsWith("[")) return emptyList()
    val a = JSONArray(t)
    return (0 until a.length()).mapNotNull { parseNightInvite(a.optJSONObject(it)) }
}

// ---- Dates, as both apps write them ----

private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
internal val LONG_DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
internal val SHORT_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

internal fun zoned(ms: Long, zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zone)

/** "Fri 10 Oct". */
fun nightDay(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val d = zoned(ms, zone)
    return "${DAYS[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${SHORT_MONTHS[d.monthValue - 1]}"
}

/** "7pm", "7:30pm", "12am". */
fun nightTime(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val d = zoned(ms, zone)
    val h = if (d.hour % 12 == 0) 12 else d.hour % 12
    val m = if (d.minute == 0) "" else ":" + d.minute.toString().padStart(2, '0')
    return "$h$m${if (d.hour < 12) "am" else "pm"}"
}

/** "Fri 10 Oct · 7pm". */
fun nightWhen(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = "${nightDay(ms, zone)} · ${nightTime(ms, zone)}"

/** The date tile: "FRI" over "10". */
fun dateTile(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<String, String> {
    val d = zoned(ms, zone)
    return DAYS[d.dayOfWeek.value - 1].uppercase() to d.dayOfMonth.toString()
}

// ---- Who's coming ----

fun myInvite(night: NightInvite, me: String): NightInvitee? = night.invitees.firstOrNull { it.user.userId == me }
fun myAnswer(night: NightInvite, me: String): RsvpAnswer? = myInvite(night, me)?.answer
fun goingCount(night: NightInvite): Int = night.invitees.count { it.answer == RsvpAnswer.GOING }
/** Going or maybe. */
fun comingCount(night: NightInvite): Int = night.invitees.count { it.answer == RsvpAnswer.GOING || it.answer == RsvpAnswer.MAYBE }

/** "Sam maybe", "Sam, Jo maybe", or "3 maybe" — those who said maybe, other than the user. */
private fun maybePart(night: NightInvite, me: String): String? {
    val names = night.invitees.filter { it.answer == RsvpAnswer.MAYBE && it.user.userId != me }.map { it.user.displayName }
    if (names.isEmpty()) return null
    return if (names.size <= 2) "${names.joinToString(", ")} maybe" else "${names.size} maybe"
}

/** The user's own answer, as the card says it. */
fun myPart(answer: RsvpAnswer?): String = when (answer) {
    RsvpAnswer.GOING -> "you're going"
    RsvpAnswer.MAYBE -> "you said maybe"
    RsvpAnswer.CANT -> "you can't make it"
    null -> "you haven't answered"
}

/** The People card: "4 going · Sam maybe · you haven't answered" (or "Called off"). */
fun rsvpLine(night: NightInvite, me: String): String {
    if (night.cancelled) return "Called off"
    val going = goingCount(night)
    return listOfNotNull(if (going > 0) "$going going" else "Nobody going yet", maybePart(night, me), myPart(myAnswer(night, me))).joinToString(" · ")
}

/** The chat's card: "Priya's · 4 going, Sam maybe". */
fun chatCardLine(night: NightInvite, me: String): String {
    if (night.cancelled) return "${night.place} · called off"
    val going = goingCount(night)
    return night.place + " · " + listOfNotNull(if (going > 0) "$going going" else "nobody going yet", maybePart(night, me)).joinToString(", ")
}

/** The Play card: "Priya's · you're going · Season 2". */
fun playLine(night: NightInvite, me: String): String {
    if (night.cancelled) return "${night.place} · called off"
    return listOfNotNull(night.place, myPart(myAnswer(night, me)), night.seasonName?.takeIf { it.isNotEmpty() }).joinToString(" · ")
}

/** Under the date: "Thursday crew · counts for Season 2 · asked by Priya". */
fun headerLine(night: NightInvite, me: String): String = listOfNotNull(
    night.podName.takeIf { it.isNotEmpty() },
    night.seasonName?.let { "counts for $it" },
    "asked by " + if (night.organiser.userId == me) "you" else night.organiser.displayName
).joinToString(" · ")

/** "WHO'S COMING · 5 OF 6": going or maybe, of everyone asked. */
fun whoHeader(night: NightInvite): String = "WHO'S COMING · ${comingCount(night)} OF ${night.invitees.size}"

enum class WhoTone { GOING, MAYBE, CANT, NONE }

/** One line of Who's coming: one person, or several with the same answer. */
data class WhoRow(val key: String, val names: String, val status: String, val tone: WhoTone)

private fun toneOf(a: RsvpAnswer?): WhoTone = when (a) {
    RsvpAnswer.GOING -> WhoTone.GOING
    RsvpAnswer.MAYBE -> WhoTone.MAYBE
    RsvpAnswer.CANT -> WhoTone.CANT
    null -> WhoTone.NONE
}

private fun statusOf(tone: WhoTone): String = when (tone) {
    WhoTone.GOING -> "Going"
    WhoTone.MAYBE -> "Maybe"
    WhoTone.CANT -> "Can't"
    WhoTone.NONE -> "No answer yet"
}

private fun bringing(i: NightInvitee) = i.deck != null && (i.answer == RsvpAnswer.GOING || i.answer == RsvpAnswer.MAYBE)

/**
 * Who's coming, as the invite shows it: the organiser ("Going · hosting"), the user ("You"), anyone
 * bringing a named deck on a line of their own ("Going · Krenko"), then everyone else grouped by
 * answer — going, maybe, can't, no answer yet ("Alex, Jo · Going").
 */
fun whoRows(night: NightInvite, me: String): List<WhoRow> {
    val rows = mutableListOf<WhoRow>()
    fun status(i: NightInvitee, extra: String?) = listOfNotNull(statusOf(toneOf(i.answer)), extra).joinToString(" · ")
    val hostId = night.organiser.userId
    night.invitees.firstOrNull { it.user.userId == hostId }?.let { host ->
        rows += WhoRow(host.user.userId, if (host.user.userId == me) "You" else host.user.displayName, status(host, if (host.answer == RsvpAnswer.CANT) null else "hosting"), toneOf(host.answer))
    }
    night.invitees.firstOrNull { it.user.userId == me && it.user.userId != hostId }?.let { mine ->
        rows += WhoRow(me, "You", status(mine, if (mine.answer != RsvpAnswer.CANT) mine.deck else null), toneOf(mine.answer))
    }
    val rest = night.invitees.filter { it.user.userId != hostId && it.user.userId != me }
    rest.filter(::bringing).forEach { rows += WhoRow(it.user.userId, it.user.displayName, status(it, it.deck), toneOf(it.answer)) }
    for (tone in WhoTone.entries) {
        val group = rest.filter { toneOf(it.answer) == tone && !bringing(it) }
        if (group.isNotEmpty()) rows += WhoRow("group-${tone.name.lowercase()}", group.joinToString(", ") { it.user.displayName }, statusOf(tone), tone)
    }
    return rows
}

/** Whether the user may change or call off [night]: they organise it, or own its pod. */
fun canManageNight(night: NightInvite, me: String, podOwner: String?): Boolean = night.organiser.userId == me || podOwner == me

/** Nights still to come (or under way: begun less than 6 hours ago), not called off, soonest first. */
fun upcomingNights(nights: List<NightInvite>, now: Long): List<NightInvite> =
    nights.filter { !it.cancelled && it.startsAt > now - 6 * 60 * 60 * 1000L }.sortedBy { it.startsAt }

/** The night the cards show: the soonest still to come, in [podId] when given. */
fun nextNight(nights: List<NightInvite>, now: Long, podId: String? = null): NightInvite? =
    upcomingNights(nights, now).firstOrNull { podId == null || it.podId == podId }

/** Someone coming to a night, other than the user. */
data class Attendee(val name: String, val userId: String, val deck: String?)

/** Everyone coming (going or maybe) but the user — for Pack your bag and Trade matches tonight. */
fun attendeesOf(night: NightInvite, me: String): List<Attendee> = night.invitees
    .filter { it.user.userId != me && (it.answer == RsvpAnswer.GOING || it.answer == RsvpAnswer.MAYBE) }
    .map { Attendee(it.user.displayName, it.user.userId, it.deck) }

// ---- The reminder the day before ----

/** When to remind the user: a day before it starts. Null when called off, they can't make it, or that's past. */
fun reminderAt(night: NightInvite, me: String, now: Long): Long? {
    if (night.cancelled || myInvite(night, me) == null || myAnswer(night, me) == RsvpAnswer.CANT) return null
    val at = night.startsAt - REMINDER_BEFORE_MS
    return if (at > now) at else null
}

/** "Game night tomorrow" / "7pm at Priya's · 4 going". */
fun reminderText(night: NightInvite, zone: ZoneId = ZoneId.systemDefault()): Pair<String, String> {
    val going = goingCount(night)
    return "Game night tomorrow" to "${nightTime(night.startsAt, zone)} at ${night.place} · ${if (going > 0) "$going going" else "nobody going yet"}"
}

// ---- Calendar (.ics) ----

private fun icsText(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n")

private fun icsTime(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC)
    fun p(n: Int) = n.toString().padStart(2, '0')
    return "${d.year}${p(d.monthValue)}${p(d.dayOfMonth)}T${p(d.hour)}${p(d.minute)}${p(d.second)}Z"
}

/** A content line folded at 75 octets (RFC 5545), never inside a character. */
fun foldIcsLine(line: String): String {
    val out = mutableListOf<String>()
    val current = StringBuilder()
    var size = 0
    var i = 0
    while (i < line.length) {
        val cp = line.codePointAt(i)
        val ch = String(Character.toChars(cp))
        val n = ch.toByteArray(Charsets.UTF_8).size
        val limit = if (out.isEmpty()) 75 else 74 // continuation lines start with a space
        if (size + n > limit) {
            out += current.toString()
            current.setLength(0)
            size = 0
        }
        current.append(ch)
        size += n
        i += Character.charCount(cp)
    }
    out += current.toString()
    return out.joinToString("\r\n ")
}

/** "Game night · Thursday crew". */
fun calendarTitle(night: NightInvite): String = if (night.podName.isNotEmpty()) "Game night · ${night.podName}" else "Game night"

/** The night as an iCalendar file. [now]: when it's made. */
fun nightIcs(night: NightInvite, now: Long): String {
    val lines = listOfNotNull(
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//Manabind//Game night//EN",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
        "BEGIN:VEVENT",
        "UID:${night.id}@manabind.com",
        "DTSTAMP:${icsTime(now)}",
        "DTSTART:${icsTime(night.startsAt)}",
        "DTEND:${icsTime(night.startsAt + NIGHT_HOURS * 60 * 60 * 1000L)}",
        "SUMMARY:${icsText(calendarTitle(night))}",
        "LOCATION:${icsText(night.place)}",
        night.note?.takeIf { it.isNotEmpty() }?.let { "DESCRIPTION:${icsText(it)}" },
        "STATUS:${if (night.cancelled) "CANCELLED" else "CONFIRMED"}",
        "END:VEVENT",
        "END:VCALENDAR"
    )
    return lines.joinToString("\r\n") { foldIcsLine(it) } + "\r\n"
}

// ---- Make pods on the night ----

/** The user's deck to seat them with: its id, name and commander. */
data class MyNightDeck(val id: String, val name: String, val commander: String?)

/**
 * Tonight's game night players with everyone coming to [night] added: the user (with [myDeck], their
 * deck by the name they gave, when they have one) and each friend coming, by account, with the deck
 * they named. Players already there stay as they are.
 */
fun playersFromInvite(players: List<NightPlayer>, night: NightInvite, me: String, myName: String, myDeck: MyNightDeck?, newId: () -> String): List<NightPlayer> {
    val out = players.toMutableList()
    if (out.none { it.kind == NightPlayerKind.ME }) {
        out += NightPlayer(id = newId(), name = myName.ifBlank { "Me" }, kind = NightPlayerKind.ME, deckId = myDeck?.id, deck = myDeck?.name, commander = myDeck?.commander)
    }
    for (a in attendeesOf(night, me)) {
        if (out.any { it.userId == a.userId }) continue
        out += NightPlayer(id = newId(), name = a.name, kind = NightPlayerKind.FRIEND, userId = a.userId, deck = a.deck)
    }
    return out
}

/** The user's deck of the name they gave in their answer (case aside), if they have one. */
fun <D> deckNamed(decks: List<D>, name: String?, nameOf: (D) -> String): D? {
    val n = name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return decks.firstOrNull { nameOf(it).trim().lowercase() == n }
}

/** The bag Pack your bag keeps for [night]. */
fun nightBagId(night: NightInvite): String = "gn-${night.id}"

/** The day a night falls on where the user is, "YYYY-MM-DD" (for Pack your bag). */
fun nightDate(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = zoned(ms, zone).toLocalDate().toString()

/** Days from [from] to [to] by the calendar (0: the same day). */
internal fun calendarDays(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

/** A tapped notification's "open": a night ("night:<id>") or a pod's chat ("pod:<id>"), or null. */
fun notificationTarget(open: String): Pair<String, String>? = when {
    open.startsWith("night:") -> "night" to open.removePrefix("night:")
    open.startsWith("pod:") -> "pod" to open.removePrefix("pod:")
    else -> null
}
