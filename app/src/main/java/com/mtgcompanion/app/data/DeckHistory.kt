package com.mtgcompanion.app.data

import android.content.Context
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// A deck's history: what changed in its list, when and on which device, with saved named versions
// and the games played on each. The web app's src/decks/deckHistory.ts, rule for rule, with the same
// tests (DeckHistoryTest.kt ↔ tests/decks/deckHistory.test.ts).
//
// It rides in the deck's JSON as "history" (oldest first), so the phone and manabind.com show the
// same one. Entries are small: what was added and cut, by name; the commanders when they changed; the
// deck's value before and after when its prices were known. A burst of edits on one device within
// ten minutes is one entry. To rebuild the list at any point, some entries also carry the whole list
// ("list", name -> copies): the first one, every named version, imports and going back, and one
// every SNAPSHOT_EVERY entries — the rest are replayed from the nearest one before.
//
// Two devices' histories merge entry by entry, by id (never doubled), and the newer copy of an entry
// wins (only the device that made an entry folds more edits into it). The history is capped — the
// last MAX_ENTRIES entries within a year of the newest; named versions are never dropped — and an
// entry left without its whole list by the cap gets it, so every point can still be rebuilt. The cap
// counts from the newest entry, not the clock, so both devices cap a merge the same way.
//
// An app from before the history drops it when it saves the deck: a deck saved without the key gets
// this device's back (keepHistoryFromOlderApp), and its list changes, which no entry recorded, become
// one "synced" entry the next time the list is changed here — so the replay never drifts.
//
// JSON, key for key the same in both apps (keys left out when empty):
//   "history": [{ "id": "k3x9…", "at": 1760000000000, "from": "android", "dev": "a1b2c3d4",
//                 "add": [{ "n": "Skullclamp", "q": 1 }], "cut": [{ "n": "Wood Elves", "q": 1 }],
//                 "cmd": ["Meren of Clan Nel Toth"], "v0": 412.3, "v1": 476.1,
//                 "list": { "Skullclamp": 1, … }, "kind": "named", "name": "Before game night",
//                 "note": "…", "to": 1759000000000 }]

/** Edits on one device closer together than this are one entry. */
const val HISTORY_COALESCE_MILLIS = 10 * 60 * 1000L
/** Entries kept besides the named versions, newest first. */
const val HISTORY_MAX_ENTRIES = 100
/** Entries older than this (from the newest) go, named versions aside. */
const val HISTORY_MAX_AGE_MILLIS = 365L * 24 * 60 * 60 * 1000
/** An entry carries the whole list once this many entries have gone by without one. */
const val HISTORY_SNAPSHOT_EVERY = 20

/** Copies of one card, by name. */
data class HistoryLine(val n: String, val q: Int)

/**
 * One change to a deck's list. [kind]: null for an edit; "start" — the list from before the history
 * was kept; "import" — a whole list arriving into an empty deck; "named" — a version the user saved
 * by name; "restore" — going back to an earlier list; "synced" — changes made where no entry was
 * recorded. The web app's DeckHistoryEntry, key for key.
 */
data class DeckHistoryEntry(
    val id: String,
    /** When: the latest change folded into it. */
    val at: Long,
    val kind: String? = null,
    /** The app that made it: "android" or "web". */
    val from: String? = null,
    /** That app's install. */
    val dev: String? = null,
    val add: List<HistoryLine>? = null,
    val cut: List<HistoryLine>? = null,
    /** The commanders after it, when they changed — and on every entry that has the whole list. */
    val cmd: List<String>? = null,
    /** The deck's value before and after, in US dollars, when its prices were known. */
    val v0: Double? = null,
    val v1: Double? = null,
    /** The whole list after it: name -> copies. */
    val list: Map<String, Int>? = null,
    /** A named version's name and note. */
    val name: String? = null,
    val note: String? = null,
    /** Going back: when the list gone back to was. */
    val to: Long? = null
)

/** A list as it stood: name -> copies (commanders included), and the commanders. */
data class ListState(val cards: Map<String, Int>, val commanders: List<String>)

val EMPTY_LIST = ListState(emptyMap(), emptyList())

/** Who is recording: the app, its install, the time, new ids and the deck's value when known. */
data class HistoryContext(
    val now: Long,
    val from: String,
    val dev: String,
    val newId: () -> String,
    val valueOf: ((Deck) -> Double?)? = null
)

private val byTime = compareBy<DeckHistoryEntry>({ it.at }, { it.id })

/** Oldest first; the same time, by id. */
fun sortedHistory(history: List<DeckHistoryEntry>): List<DeckHistoryEntry> = history.sortedWith(byTime)

/** The deck's list: card name -> copies (commanders included), and its commanders. */
fun listStateOf(deck: Deck): ListState {
    val cards = LinkedHashMap<String, Int>()
    deck.cards.forEach { cards[it.name] = (cards[it.name] ?: 0) + it.quantity }
    return ListState(cards, listOfNotNull(deck.commander?.name?.takeIf { it.isNotEmpty() }, deck.partnerCommander?.name?.takeIf { it.isNotEmpty() }))
}

private fun sameCards(a: Map<String, Int>, b: Map<String, Int>): Boolean = a.filterValues { it > 0 } == b.filterValues { it > 0 }

fun sameState(a: ListState, b: ListState): Boolean = sameCards(a.cards, b.cards) && a.commanders == b.commanders

fun cardCount(s: ListState): Int = s.cards.values.sum()

/** What changed from [from] to [to]: the cards added and cut, A–Z. */
fun diffStates(from: ListState, to: ListState): Pair<List<HistoryLine>, List<HistoryLine>> {
    val add = mutableListOf<HistoryLine>()
    val cut = mutableListOf<HistoryLine>()
    for (n in (from.cards.keys + to.cards.keys).toSortedSet()) {
        val delta = (to.cards[n] ?: 0) - (from.cards[n] ?: 0)
        if (delta > 0) add += HistoryLine(n, delta)
        if (delta < 0) cut += HistoryLine(n, -delta)
    }
    return add to cut
}

/** [state] after [entry]. */
fun applyEntry(state: ListState, entry: DeckHistoryEntry): ListState {
    entry.list?.let { return ListState(LinkedHashMap(it), entry.cmd ?: state.commanders) }
    val cards = LinkedHashMap(state.cards)
    entry.add.orEmpty().forEach { cards[it.n] = (cards[it.n] ?: 0) + it.q }
    entry.cut.orEmpty().forEach {
        val left = (cards[it.n] ?: 0) - it.q
        if (left > 0) cards[it.n] = left else cards.remove(it.n)
    }
    return ListState(cards, entry.cmd ?: state.commanders)
}

/** The list after each of [sorted]'s entries, in the same order. */
fun statesThrough(sorted: List<DeckHistoryEntry>): List<ListState> {
    val out = ArrayList<ListState>(sorted.size)
    var state = EMPTY_LIST
    for (e in sorted) {
        state = applyEntry(state, e)
        out += state
    }
    return out
}

/** The list as it was right after the entry [id]; null when there's no such entry. */
fun stateAt(history: List<DeckHistoryEntry>, id: String): ListState? {
    val sorted = sortedHistory(history)
    val i = sorted.indexOfFirst { it.id == id }
    return if (i == -1) null else statesThrough(sorted.subList(0, i + 1))[i]
}

private fun List<HistoryLine>.orNone(): List<HistoryLine>? = ifEmpty { null }

/**
 * The history capped: named versions always, then the last HISTORY_MAX_ENTRIES other entries within
 * HISTORY_MAX_AGE_MILLIS of the newest. An entry kept right after one that went gets the whole list,
 * so the list at every point left can still be rebuilt. [sorted] oldest first.
 */
fun capHistory(sorted: List<DeckHistoryEntry>): List<DeckHistoryEntry> {
    if (sorted.isEmpty()) return sorted
    val newest = sorted.last().at
    val plain = sorted.filter { it.kind != "named" }
    val keep = plain.filter { newest - it.at <= HISTORY_MAX_AGE_MILLIS }.takeLast(HISTORY_MAX_ENTRIES).map { it.id }.toSet()
    if (keep.size == plain.size) return sorted
    val states = statesThrough(sorted)
    val out = mutableListOf<DeckHistoryEntry>()
    var dropped = false
    sorted.forEachIndexed { i, e ->
        if (e.kind != "named" && e.id !in keep) {
            dropped = true
            return@forEachIndexed
        }
        out += if (dropped && e.list == null) e.copy(list = states[i].cards, cmd = states[i].commanders) else e
        dropped = false
    }
    return out
}

/** The history a deck from before it had: its saved versions, as entries. Empty when it had none. */
fun historyFromVersions(versions: List<DeckVersion>): List<DeckHistoryEntry> {
    val sorted = versions.sortedWith(compareBy<DeckVersion>({ it.savedAt }, { it.id }))
    var previous: ListState? = null
    var sinceList = 0
    return sorted.map { v ->
        val state = ListState(v.cards, v.commanders)
        val id = "v:${v.id}"
        val prev = previous
        val entry = if (prev == null) {
            sinceList = 0
            DeckHistoryEntry(id, v.savedAt, kind = "start", list = LinkedHashMap(v.cards), cmd = v.commanders)
        } else {
            sinceList++
            val (add, cut) = diffStates(prev, state)
            val withList = sinceList >= HISTORY_SNAPSHOT_EVERY
            if (withList) sinceList = 0
            DeckHistoryEntry(
                id, v.savedAt, add = add.orNone(), cut = cut.orNone(),
                cmd = if (withList || prev.commanders != v.commanders) v.commanders else null,
                list = if (withList) LinkedHashMap(v.cards) else null
            )
        }
        previous = state
        entry
    }
}

/** The deck's history: its own, or for a deck from before there was one, its saved versions. */
fun historyOf(deck: Deck): List<DeckHistoryEntry> = sortedHistory(deck.history ?: historyFromVersions(deck.versions))

/** A "synced" entry for what changed between the history's last list and [actual], or null. */
private fun catchUp(states: List<ListState>, actual: ListState, at: Long, ctx: HistoryContext): DeckHistoryEntry? {
    val tip = states.lastOrNull() ?: EMPTY_LIST
    if (sameState(tip, actual)) return null
    val (add, cut) = diffStates(tip, actual)
    return DeckHistoryEntry(
        ctx.newId(), at, kind = "synced", add = add.orNone(), cut = cut.orNone(),
        cmd = if (tip.commanders == actual.commanders) null else actual.commanders
    )
}

/** Whether [after]'s history has an entry [before]'s didn't — the change was recorded already. */
private fun recordedAlready(before: Deck?, after: Deck): Boolean {
    val hist = after.history ?: return false
    if (hist === before?.history) return false
    val had = before?.history.orEmpty().map { it.id }.toSet()
    return hist.any { it.id !in had }
}

private fun valueOf(ctx: HistoryContext, deck: Deck?): Double? = if (deck == null) null else ctx.valueOf?.invoke(deck)

/**
 * [after] with its list change recorded in its history, when its list differs from [before]'s —
 * tags, flags, the sideboard or a card's printing don't count. Edits on this device within
 * HISTORY_COALESCE_MILLIS of its last entry fold into it, unless a game was logged since (the game
 * belongs to the list it was played with). A whole list arriving into an empty deck is an import; a
 * deck changed for the first time since the history existed gets its list from before (or its saved
 * versions) first, so there's something to go back to.
 */
fun withHistory(before: Deck?, after: Deck, ctx: HistoryContext): Deck {
    val afterState = listStateOf(after)
    val beforeState = before?.let { listStateOf(it) } ?: EMPTY_LIST
    if (sameState(beforeState, afterState) || recordedAlready(before, after)) return after
    val now = ctx.now
    var hist = sortedHistory(after.history ?: historyFromVersions(before?.versions ?: after.versions))
    if (hist.isEmpty()) {
        if (cardCount(beforeState) == 0) {
            if (cardCount(afterState) > 1) {
                val entry = DeckHistoryEntry(
                    ctx.newId(), now, kind = "import", from = ctx.from, dev = ctx.dev,
                    list = afterState.cards, cmd = afterState.commanders, v1 = valueOf(ctx, after)
                )
                return after.copy(history = listOf(entry))
            }
        } else {
            hist = listOf(
                DeckHistoryEntry(ctx.newId(), now - 1, kind = "start", list = beforeState.cards, cmd = beforeState.commanders, v1 = valueOf(ctx, before))
            )
        }
    }
    val states = statesThrough(hist).toMutableList()
    val lastAt = hist.lastOrNull()?.at
    val synced = if (lastAt != null) catchUp(states, beforeState, maxOf(now - 1, lastAt + 1), ctx) else null
    if (synced != null) {
        hist = hist + synced
        states += beforeState
    }
    val last = hist.lastOrNull()
    val fold = last != null && synced == null && last.kind == null && last.dev == ctx.dev && last.from == ctx.from &&
        now - last.at < HISTORY_COALESCE_MILLIS && after.gameResults.none { it.playedAt >= last.at }
    val v1 = valueOf(ctx, after)
    if (fold && last != null) {
        val prev = if (states.size > 1) states[states.size - 2] else EMPTY_LIST
        val (add, cut) = diffStates(prev, afterState)
        val entry = last.copy(
            at = maxOf(now, last.at), add = add.orNone(), cut = cut.orNone(),
            cmd = if (last.list != null || prev.commanders != afterState.commanders) afterState.commanders else null,
            list = if (last.list != null) afterState.cards else null,
            v1 = v1 ?: last.v1
        )
        return after.copy(history = capHistory(hist.dropLast(1) + entry))
    }
    var sinceList = 0
    for (i in hist.indices.reversed()) {
        if (hist[i].list != null) break
        sinceList++
    }
    val withList = hist.isEmpty() || sinceList + 1 >= HISTORY_SNAPSHOT_EVERY
    val (add, cut) = diffStates(beforeState, afterState)
    val entry = DeckHistoryEntry(
        ctx.newId(), maxOf(now, (synced?.at ?: lastAt ?: Long.MIN_VALUE) + 1), from = ctx.from, dev = ctx.dev,
        add = add.orNone(), cut = cut.orNone(),
        cmd = if (withList || beforeState.commanders != afterState.commanders) afterState.commanders else null,
        list = if (withList) afterState.cards else null,
        v0 = valueOf(ctx, before), v1 = v1
    )
    return after.copy(history = capHistory(hist + entry))
}

/** The history with a "synced" entry first when it doesn't end on [state], and the time for the next entry. */
private fun caughtUp(deck: Deck, state: ListState, ctx: HistoryContext): Pair<List<DeckHistoryEntry>, Long> {
    var hist = historyOf(deck)
    val lastAt = hist.lastOrNull()?.at
    val synced = if (lastAt != null) catchUp(statesThrough(hist), state, maxOf(ctx.now - 1, lastAt + 1), ctx) else null
    if (synced != null) hist = hist + synced
    val at = maxOf(ctx.now, (synced?.at ?: lastAt ?: Long.MIN_VALUE) + 1)
    return hist to at
}

/** [deck] with its list as it is now saved as a named version. */
fun withNamedVersion(deck: Deck, name: String, note: String, ctx: HistoryContext): Deck {
    val state = listStateOf(deck)
    val (hist, at) = caughtUp(deck, state, ctx)
    val entry = DeckHistoryEntry(
        ctx.newId(), at, kind = "named", from = ctx.from, dev = ctx.dev,
        name = name.trim(), note = note.trim().ifEmpty { null }, list = state.cards, cmd = state.commanders, v1 = valueOf(ctx, deck)
    )
    return deck.copy(history = capHistory(hist + entry))
}

/**
 * [after] — [before] taken back to the list from [toAt] — with that recorded as one "restore" entry
 * holding the whole list. The list from before stays in the history, so going back is undone the
 * same way.
 */
fun withRestore(before: Deck, after: Deck, toAt: Long, ctx: HistoryContext): Deck {
    val beforeState = listStateOf(before)
    val afterState = listStateOf(after)
    val (hist, at) = caughtUp(before, beforeState, ctx)
    val (add, cut) = diffStates(beforeState, afterState)
    val entry = DeckHistoryEntry(
        ctx.newId(), at, kind = "restore", from = ctx.from, dev = ctx.dev, add = add.orNone(), cut = cut.orNone(),
        list = afterState.cards, cmd = afterState.commanders, to = toAt, v0 = valueOf(ctx, before), v1 = valueOf(ctx, after)
    )
    return after.copy(history = capHistory(hist + entry))
}

// ---- Sync ----

/** A stable order for two copies of one entry from the same moment, the same in both apps. */
private fun entryKey(e: DeckHistoryEntry): String = listOf(
    e.add.orEmpty().joinToString(",") { "${it.n}*${it.q}" },
    e.cut.orEmpty().joinToString(",") { "${it.n}*${it.q}" },
    (e.list?.size ?: 0).toString(), e.name ?: "", e.note ?: ""
).joinToString("|")

/**
 * Two devices' histories as one: every entry from either, once by id — the newer copy where both
 * have it — oldest first and capped. Null when neither has one.
 */
fun mergeHistory(mine: List<DeckHistoryEntry>?, theirs: List<DeckHistoryEntry>?): List<DeckHistoryEntry>? {
    if (mine == null && theirs == null) return null
    val byId = LinkedHashMap<String, DeckHistoryEntry>()
    for (e in theirs.orEmpty() + mine.orEmpty()) {
        val had = byId[e.id]
        if (had == null || (if (e.at != had.at) e.at > had.at else entryKey(e) > entryKey(had))) byId[e.id] = e
    }
    return capHistory(sortedHistory(byId.values.toList()))
}

/** [theirs] with [source]'s history put back where an app that doesn't know it saved [theirs]. */
fun keepHistoryFromOlderApp(source: Deck, theirs: Deck): Deck =
    if (theirs.history == null && source.history != null) theirs.copy(history = source.history) else theirs

// ---- Showing it ----

/** One entry as the History screen shows it, with the games played while it was the list. */
data class HistoryItem(
    val entry: DeckHistoryEntry,
    val added: List<HistoryLine>,
    val removed: List<HistoryLine>,
    /** The commanders changed, to these. */
    val commanders: List<String>?,
    /** Copies in the list after it. */
    val cards: Int,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    /** The newest entry: its list is the deck's now. */
    val latest: Boolean = false
)

/** Newest first; edits that changed nothing in the end left out. A game counts for the list it was played with. */
fun historyItems(deck: Deck): List<HistoryItem> {
    val sorted = historyOf(deck)
    val states = statesThrough(sorted)
    val items = mutableListOf<HistoryItem>()
    sorted.forEachIndexed { i, entry ->
        val prev = if (i > 0) states[i - 1] else EMPTY_LIST
        val state = states[i]
        val listKind = entry.kind == "start" || entry.kind == "import" || entry.kind == "named"
        val changedCommanders = !listKind && i > 0 && prev.commanders != state.commanders
        if (!listKind && entry.add.isNullOrEmpty() && entry.cut.isNullOrEmpty() && !changedCommanders) return@forEachIndexed
        items += HistoryItem(entry, entry.add.orEmpty(), entry.cut.orEmpty(), if (changedCommanders) state.commanders else null, cardCount(state))
    }
    val counted = items.mapIndexed { i, item ->
        val from = item.entry.at
        val end = items.getOrNull(i + 1)?.entry?.at ?: Long.MAX_VALUE
        val games = deck.gameResults.filter { it.playedAt >= from && it.playedAt < end }
        item.copy(
            wins = games.count { it.result == "WIN" },
            losses = games.count { it.result == "LOSS" },
            draws = games.count { it.result == "DRAW" },
            latest = i == items.size - 1
        )
    }
    return counted.reversed()
}

/** "+ Sheoldred, the Apocalypse", or "+ 4" and "Skullclamp, Viscera Seer, …" — at most [most] names. */
fun linesText(sign: String, lines: List<HistoryLine>, most: Int = 5): Pair<String, String> {
    val total = lines.sumOf { it.q }
    if (lines.size == 1 && total == 1) return "$sign ${lines[0].n}" to ""
    val names = lines.take(most).map { if (it.q > 1) "${it.q} ${it.n}" else it.n }
    return "$sign $total" to names.joinToString(", ") + if (lines.size > most) ", …" else ""
}

/** "Went 2–1 with this list." (with draws, "2–1–1"); empty without games. */
fun recordLine(item: HistoryItem): String {
    if (item.wins + item.losses + item.draws == 0) return ""
    return "Went ${item.wins}–${item.losses}${if (item.draws > 0) "–${item.draws}" else ""} with this list."
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun calendarAt(ms: Long): Calendar = Calendar.getInstance().apply { timeInMillis = ms }

/** "4 Oct", or "4 Oct 2025" in another year. */
fun dayText(at: Long, now: Long): String {
    val d = calendarAt(at)
    val year = if (d.get(Calendar.YEAR) == calendarAt(now).get(Calendar.YEAR)) "" else " ${d.get(Calendar.YEAR)}"
    return "${d.get(Calendar.DAY_OF_MONTH)} ${MONTHS[d.get(Calendar.MONTH)]}$year"
}

/** "Today, 14:20", "Yesterday, 09:05", or "12 Oct". */
fun whenText(at: Long, now: Long): String {
    val d = calendarAt(at)
    val startOfToday = calendarAt(now).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val time = "%02d:%02d".format(d.get(Calendar.HOUR_OF_DAY), d.get(Calendar.MINUTE))
    return when {
        at >= startOfToday -> "Today, $time"
        at >= startOfToday - 24 * 60 * 60 * 1000L -> "Yesterday, $time"
        else -> dayText(at, now)
    }
}

/** The heading: a named version's name and day, otherwise when. */
fun entryTitle(e: DeckHistoryEntry, now: Long): String =
    if (e.kind == "named" && !e.name.isNullOrEmpty()) "${e.name} · ${dayText(e.at, now)}" else whenText(e.at, now)

/**
 * What goes beside the heading: "named", "imported", "synced", or where it was made — "this phone" /
 * "this browser" for this install, "manabind.com" for the web app, "phone" for another phone.
 */
fun sourceText(e: DeckHistoryEntry, hereFrom: String, hereDev: String): String = when {
    e.kind == "named" -> "named"
    e.kind == "import" -> "imported"
    e.kind == "synced" -> "synced"
    e.from == null -> ""
    e.from == hereFrom && e.dev == hereDev -> if (hereFrom == "web") "this browser" else "this phone"
    e.from == "web" -> "manabind.com"
    hereFrom == "web" -> "phone"
    else -> "another phone"
}

// ---- An earlier list ----

/** What differs between the list then and now: in it then, not now; and added since. A–Z. */
fun versionDiff(then: ListState, now: ListState): Pair<List<HistoryLine>, List<HistoryLine>> {
    val (add, cut) = diffStates(then, now)
    return cut to add
}

/** The list then, A–Z. */
fun wholeList(then: ListState): List<HistoryLine> = then.cards.keys.sorted().map { HistoryLine(it, then.cards.getValue(it)) }

data class RestorePlan(
    /** The deck with the list from then. */
    val deck: Deck,
    /** Entries that lost copies, and how many they have left (0: gone) — for the copies going back to the pile. */
    val cuts: List<Pair<DeckCardEntry, Int>>,
    /** Copies coming back into the deck. In a physical deck they're proxies until pulled from storage. */
    val incoming: Int,
    /** Cards from then with nothing to make them from: fetch these and plan again. */
    val missing: List<HistoryLine>
)

/**
 * [deck] taken back to the list [then]. Cards in both keep their printings (copies come off the last
 * printing first); a card coming back takes the deck's own printing, else [known]'s (its sideboard,
 * Considering, binders, or fetched) — or goes in [RestorePlan.missing]. In a physical deck, copies
 * coming back are proxies until they're pulled from storage, so its pull list fetches them. The
 * commanders are set to then's, where the deck has them.
 */
fun restoreList(deck: Deck, then: ListState, known: (String) -> DeckCardEntry?): RestorePlan {
    val physical = deck.ownershipType == DeckOwnership.PHYSICAL
    val cuts = mutableListOf<Pair<DeckCardEntry, Int>>()
    val missing = mutableListOf<HistoryLine>()
    var incoming = 0
    val have = LinkedHashMap<String, Int>()
    deck.cards.forEach { have[it.name] = (have[it.name] ?: 0) + it.quantity }
    val cards = deck.cards.toMutableList()
    for (name in have.keys.sorted()) {
        var over = have.getValue(name) - (then.cards[name] ?: 0)
        var i = cards.size - 1
        while (i >= 0 && over > 0) {
            val e = cards[i]
            if (e.name == name) {
                val take = minOf(over, e.quantity)
                over -= take
                cuts += e to e.quantity - take
                cards[i] = e.copy(quantity = e.quantity - take)
            }
            i--
        }
    }
    val kept = cards.filter { it.quantity > 0 }.map { e ->
        val p = e.proxyQuantity
        if (p == null || p <= e.quantity) e else e.copy(proxyQuantity = e.quantity)
    }.toMutableList()
    for (name in then.cards.keys.sorted()) {
        val delta = then.cards.getValue(name) - (have[name] ?: 0)
        if (delta <= 0) continue
        val at = kept.indexOfFirst { it.name == name }
        if (at != -1) {
            val e = kept[at]
            kept[at] = if (physical) e.copy(quantity = e.quantity + delta, proxyQuantity = (e.proxyQuantity ?: 0) + delta) else e.copy(quantity = e.quantity + delta)
            incoming += delta
            continue
        }
        val from = known(name)
        if (from == null) {
            missing += HistoryLine(name, delta)
            continue
        }
        kept += from.copy(name = name, quantity = delta, replaceable = false, proxyQuantity = if (physical) delta else null)
        incoming += delta
    }
    fun leader(n: String) = kept.firstOrNull { it.name == n }
    fun inDeck(e: DeckCardEntry?) = e?.let { c -> kept.firstOrNull { it.scryfallId == c.scryfallId } }
    val commander = if (then.commanders.isNotEmpty()) leader(then.commanders[0]) ?: inDeck(deck.commander) else null
    val partner = if (then.commanders.size > 1) leader(then.commanders[1]) else null
    return RestorePlan(deck.copy(cards = kept, commander = commander, partnerCommander = partner), cuts, incoming, missing)
}

// ---- This phone ----

/**
 * This phone as the deck history knows it: an install id kept in its preferences, so its entries
 * read "this phone" here and edits in one sitting fold together; and the prices looked up for the
 * decks opened so far, for the value before and after a change.
 */
object HistoryDevice {
    private const val PREFS = "deck_history"
    private const val KEY = "device"
    @Volatile private var device: String? = null
    private val prices = ConcurrentHashMap<String, Double>()
    private val unpriced: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** This install's id: eight letters and digits, made the first time it's asked for. */
    fun id(context: Context): String {
        device?.let { return it }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY, null) ?: UUID.randomUUID().toString().replace("-", "").take(8).also {
            prefs.edit().putString(KEY, it).apply()
        }
        device = id
        return id
    }

    /** Prices looked up for a deck's cards (scryfallId -> US dollars; null: no price). */
    fun notePrices(byId: Map<String, Double?>) {
        byId.forEach { (id, usd) -> if (usd != null) prices[id] = usd else unpriced += id }
    }

    /** The deck's value from the prices known on this phone; null when too few are known. */
    fun valueOf(deck: Deck): Double? {
        val known = HashMap<String, Double?>()
        deck.cards.forEach { e ->
            when {
                prices.containsKey(e.scryfallId) -> known[e.scryfallId] = prices[e.scryfallId]
                e.scryfallId in unpriced -> known[e.scryfallId] = null
            }
        }
        return deckValueOf(deck, known)?.first
    }

    /** Recording on this phone, now. */
    fun context(context: Context, now: Long = System.currentTimeMillis()): HistoryContext =
        HistoryContext(now, "android", id(context), { UUID.randomUUID().toString().replace("-", "").take(12) }, this::valueOf)
}
