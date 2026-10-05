package com.mtgcompanion.app.ui.lifecounter

import com.mtgcompanion.app.data.GameResult
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

// Game night: who's here and what they're playing, split into fair pods, each pod's game started on
// the life counter and its winner noted. Everything here is plain logic, kept apart from the screen
// (GameNightScreen.kt) so it can be tested; the same seed gives the same pods on the web. Mirrors
// the web app's src/lifecounter/gameNight.ts — same JSON keys, same numbers.

/** Commander pods of 3–4, or 1v1 pairs. */
enum class NightFormat { COMMANDER, DUEL }

/** ME: the user. FRIEND: a friend with an account. GUEST: a name typed in. */
enum class NightPlayerKind { ME, FRIEND, GUEST }

data class NightPlayer(
    val id: String,
    val name: String,
    val kind: NightPlayerKind,
    /** A friend's account. */
    val userId: String? = null,
    /** The user's own deck: their games go onto it. */
    val deckId: String? = null,
    /** A friend's shared deck (its item id). */
    val sharedDeckId: String? = null,
    /** What they're playing, as shown. */
    val deck: String? = null,
    val commander: String? = null,
    /** Power bracket 1–5; null: not known, counted as the middle (3). */
    val bracket: Int? = null
)

/** A pod: its players by id, when its game was started, and who won (a player id) once known. */
data class NightPod(
    val id: String,
    val playerIds: List<String>,
    val startedAt: Long? = null,
    val winnerId: String? = null
)

data class GameNight(
    val id: String,
    val createdAt: Long,
    val format: NightFormat = NightFormat.COMMANDER,
    /** The seed the pods were last split with. */
    val seed: Int = 0,
    val players: List<NightPlayer> = emptyList(),
    val pods: List<NightPod> = emptyList()
)

/** The bracket a player counts as: theirs, or the middle when nobody knows. */
const val UNKNOWN_BRACKET = 3
fun bracketOf(p: NightPlayer): Int = p.bracket ?: UNKNOWN_BRACKET

/** What one pairing that also played together last night costs, against the power spread. */
const val REPEAT_COST = 1.0

/** How many shuffles the split starts from; each one is then improved by swapping players. */
private const val RESTARTS = 24

/** A night starts over (keeping its players) once it's this old. */
const val NIGHT_STALE_MS = 20L * 60 * 60 * 1000

/**
 * A small seeded random number generator (mulberry32), giving 0 ≤ n < 1. Plain 32-bit integer
 * steps (Kotlin's Int wraps as JavaScript's Math.imul and `| 0` do), so the web's copy gives the
 * very same numbers.
 */
fun seededRandom(seed: Int): () -> Double {
    var a = seed
    return {
        a += 0x6D2B79F5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}

/**
 * The pod sizes for [n] players. Commander: as even as possible, 3 or 4 each (6 → 3+3, 7 → 4+3,
 * 9 → 3+3+3); five or fewer play as one pod, since five can't split into 3–4. 1v1: pairs, and a
 * three-way game when there's an odd one out.
 */
fun podSizes(n: Int, format: NightFormat): List<Int> {
    if (n <= 0) return emptyList()
    if (format == NightFormat.DUEL) {
        if (n < 4) return listOf(n)
        return if (n % 2 == 0) List(n / 2) { 2 } else List((n - 3) / 2) { 2 } + 3
    }
    if (n <= 5) return listOf(n)
    val k = ceil(n / 4.0).toInt()
    val base = n / k
    val extra = n % k
    return List(k) { i -> if (i < extra) base + 1 else base }
}

/** Who a player is from one night to the next: you, a friend's account, or a guest's name. */
fun playerKey(p: NightPlayer): String = when {
    p.kind == NightPlayerKind.ME -> "me"
    p.kind == NightPlayerKind.FRIEND && !p.userId.isNullOrEmpty() -> "u:${p.userId}"
    else -> "g:" + p.name.trim().lowercase()
}

private fun pairKey(a: String, b: String) = if (a < b) "$a|$b" else "$b|$a"

/** Every two players who shared a pod on [night], by [playerKey]. */
fun pairingsOf(night: GameNight?): Set<String> {
    if (night == null) return emptySet()
    val byId = night.players.associate { it.id to playerKey(it) }
    val pairs = mutableSetOf<String>()
    for (pod in night.pods) {
        val keys = pod.playerIds.mapNotNull { byId[it] }
        for (i in keys.indices) for (j in i + 1 until keys.size) pairs += pairKey(keys[i], keys[j])
    }
    return pairs
}

/** How far apart a pod's brackets are: the sum of each one's squared distance from the pod's average. */
fun powerSpread(brackets: List<Int>): Double {
    if (brackets.size < 2) return 0.0
    var sum = 0.0
    for (b in brackets) sum += b
    val mean = sum / brackets.size
    var spread = 0.0
    for (b in brackets) spread += (b - mean) * (b - mean)
    return spread
}

/** How many pairings in [keys] (one pod's player keys) played together last night. */
fun repeatsIn(keys: List<String>, previous: Set<String>): Int {
    var n = 0
    for (i in keys.indices) for (j in i + 1 until keys.size) if (pairKey(keys[i], keys[j]) in previous) n++
    return n
}

/** What a split costs: every pod's power spread, plus [REPEAT_COST] for each pairing repeated from last night. */
private fun splitCost(pods: List<IntArray>, brackets: List<Int>, keys: List<String>, previous: Set<String>): Double {
    var cost = 0.0
    for (pod in pods) {
        cost += powerSpread(pod.map { brackets[it] })
        if (previous.isNotEmpty()) cost += REPEAT_COST * repeatsIn(pod.map { keys[it] }, previous)
    }
    return cost
}

private const val EPSILON = 1e-9

/**
 * Splits [players] into pods for [format]: players close in power together, and — where it costs
 * no more than that — not the same pairings as last night ([previous], from [pairingsOf]). Tries
 * [RESTARTS] seeded shuffles, improves each by swapping players between pods while that helps,
 * and keeps the cheapest. The same players, seed and last night always give the same pods: each
 * pod's players in the order they were added, the pods in order of their first player.
 */
fun splitPods(players: List<NightPlayer>, format: NightFormat, seed: Int, previous: Set<String> = emptySet()): List<List<String>> {
    val n = players.size
    val sizes = podSizes(n, format)
    if (sizes.size <= 1) return if (n == 0) emptyList() else listOf(players.map { it.id })
    val brackets = players.map { bracketOf(it) }
    val keys = players.map { playerKey(it) }
    val random = seededRandom(seed)
    var best: List<IntArray>? = null
    var bestCost = Double.POSITIVE_INFINITY
    repeat(RESTARTS) {
        val order = IntArray(n) { it }
        for (i in n - 1 downTo 1) {
            val j = (random() * (i + 1)).toInt()
            val t = order[i]; order[i] = order[j]; order[j] = t
        }
        val pods = mutableListOf<IntArray>()
        var at = 0
        for (size in sizes) { pods += order.copyOfRange(at, at + size); at += size }
        var cost = splitCost(pods, brackets, keys, previous)
        var improved = true
        while (improved) {
            improved = false
            for (a in pods.indices) for (b in a + 1 until pods.size) {
                for (i in pods[a].indices) for (j in pods[b].indices) {
                    val x = pods[a][i]; pods[a][i] = pods[b][j]; pods[b][j] = x
                    val c = splitCost(pods, brackets, keys, previous)
                    if (c < cost - EPSILON) { cost = c; improved = true } else { pods[b][j] = pods[a][i]; pods[a][i] = x }
                }
            }
        }
        if (cost < bestCost - EPSILON) { bestCost = cost; best = pods.map { it.copyOf() } }
    }
    return best!!
        .map { it.sorted() }
        .sortedBy { it.first() }
        .map { pod -> pod.map { players[it].id } }
}

/** A new seed for a reshuffle. */
fun newSeed(): Int = (Math.random() * 0x7fffffff).toInt()

/** [night] split afresh with [seed]: new pods, no results. */
fun withPods(night: GameNight, seed: Int, previous: GameNight?): GameNight {
    val groups = splitPods(night.players, night.format, seed, pairingsOf(previous))
    return night.copy(seed = seed, pods = groups.mapIndexed { i, ids -> NightPod("$seed-${i + 1}", ids) })
}

/**
 * [pods] with [playerId] moved into the pod at [toIndex] — or into a pod of their own when
 * [toIndex] is past the last. A pod left empty goes; a pod whose players changed loses its result.
 */
fun movePlayer(pods: List<NightPod>, playerId: String, toIndex: Int, newPodId: String): List<NightPod> {
    val from = pods.indexOfFirst { playerId in it.playerIds }
    if (from == toIndex) return pods
    fun NightPod.reset() = copy(startedAt = null, winnerId = null)
    val next = pods.mapIndexed { i, p -> if (i == from) p.copy(playerIds = p.playerIds - playerId).reset() else p }.toMutableList()
    if (toIndex >= pods.size) next += NightPod(newPodId, listOf(playerId))
    else next[toIndex] = next[toIndex].copy(playerIds = next[toIndex].playerIds + playerId).reset()
    return next.filter { it.playerIds.isNotEmpty() }
}

/** The players in no pod yet (added after the split). */
fun unseated(night: GameNight): List<NightPlayer> =
    night.players.filter { p -> night.pods.none { p.id in it.playerIds } }

/** [night] without [playerId]: off the list and out of their pod. */
fun withoutPlayer(night: GameNight, playerId: String): GameNight = night.copy(
    players = night.players.filterNot { it.id == playerId },
    pods = night.pods
        .map { p -> if (playerId in p.playerIds) p.copy(playerIds = p.playerIds - playerId, winnerId = if (p.winnerId == playerId) null else p.winnerId) else p }
        .filter { it.playerIds.isNotEmpty() }
)

/** A pod's average bracket, to one decimal ("2.7"). */
fun podPower(pod: NightPod, players: List<NightPlayer>): String {
    val brackets = pod.playerIds.mapNotNull { id -> players.firstOrNull { it.id == id } }.map { bracketOf(it) }
    if (brackets.isEmpty()) return "–"
    return String.format(Locale.US, "%.1f", brackets.sum().toDouble() / brackets.size)
}

// ---- Suggesting a deck ----

/** One of the user's decks, as the suggestion sees it: [bracket] when it's been estimated. */
data class DeckChoice(
    val id: String,
    val name: String,
    val gameMode: String,
    val bracket: Int?,
    /** When it was last played (its newest game result), 0 for never. */
    val lastPlayed: Long
)

/** Whether a deck of [gameMode] suits a night of [format]. */
fun deckFitsFormat(gameMode: String, format: NightFormat): Boolean =
    if (format == NightFormat.COMMANDER) gameMode == "COMMANDER" else gameMode != "COMMANDER" && gameMode != "BRAWL"

/**
 * The user's decks best first for tonight: those of the night's format (all of them when none
 * are), closest in bracket to the average of the others' known brackets, then the one played
 * longest ago, then by name. [others]: the other players' brackets, null where not known.
 */
fun suggestedDecks(decks: List<DeckChoice>, format: NightFormat, others: List<Int?>): List<DeckChoice> {
    val fitting = decks.filter { deckFitsFormat(it.gameMode, format) }
    val pool = fitting.ifEmpty { decks }
    val known = others.filterNotNull()
    val target = if (known.isNotEmpty()) known.sum().toDouble() / known.size else null
    fun distance(d: DeckChoice) = if (target == null) 0.0 else abs((d.bracket ?: UNKNOWN_BRACKET) - target)
    return pool.sortedWith(compareBy<DeckChoice>({ distance(it) }, { it.lastPlayed }, { it.name.lowercase() }, { it.id }))
}

/** The next suggestion after [currentId] (the first when it isn't one), so asking again offers another. */
fun nextSuggestion(ordered: List<DeckChoice>, currentId: String?): DeckChoice? {
    if (ordered.isEmpty()) return null
    val at = ordered.indexOfFirst { it.id == currentId }
    return ordered[(at + 1) % ordered.size]
}

// ---- The life counter, and each pod's result ----

/** What the life counter is opened with for a pod: the seats' names and commanders, and which seat is the user's. */
data class TableSeed(
    val players: List<SeedPlayer>,
    /** The user's seat (1-based), or null when they aren't in this pod. */
    val meSeat: Int?,
    /** Where the user's game is saved. */
    val meDeckId: String?
)

data class SeedPlayer(val name: String, val commander: String?)

fun tableSeedOf(pod: NightPod, players: List<NightPlayer>): TableSeed {
    val seated = pod.playerIds.mapNotNull { id -> players.firstOrNull { it.id == id } }
    val me = seated.indexOfFirst { it.kind == NightPlayerKind.ME }
    return TableSeed(
        players = seated.map { SeedPlayer(it.name, it.commander) },
        meSeat = if (me >= 0) me + 1 else null,
        meDeckId = if (me >= 0) seated[me].deckId else null
    )
}

/** The life counter's seating for [count] players: [currentId] when it already seats that many, else the first that does. */
fun layoutIdFor(count: Int, currentId: String): String {
    val current = TableLayouts.all.firstOrNull { it.id == currentId }
    if (current != null && current.playerCount == count) return currentId
    return TableLayouts.all.firstOrNull { it.playerCount == count }?.id ?: currentId
}

private fun sameName(a: String, b: String) = a.trim().lowercase() == b.trim().lowercase()

/**
 * The life counter game [pod] played, if any: the newest one that ended after the pod started with
 * the pod's players at it, by name. Null when there's none yet.
 */
fun podGame(pod: NightPod, players: List<NightPlayer>, games: List<TableGame>): TableGame? {
    val started = pod.startedAt ?: return null
    val names = pod.playerIds.mapNotNull { id -> players.firstOrNull { it.id == id }?.name }
    var found: TableGame? = null
    for (g in games) {
        if (g.endedAt < started || g.players.size != names.size) continue
        if (!names.all { n -> g.players.any { sameName(it.name, n) } }) continue
        if (found == null || g.endedAt > found.endedAt) found = g
    }
    return found
}

/** Who won a pod, and whether the life counter said so. A null [winnerId]: nobody was left standing. */
data class PodResult(val winnerId: String?, val fromTable: Boolean)

/**
 * Who won [pod]: from its life counter game when there is one, otherwise whoever was tapped as
 * the winner. Null while it isn't known.
 */
fun podWinner(pod: NightPod, players: List<NightPlayer>, games: List<TableGame>): PodResult? {
    val game = podGame(pod, players, games)
    if (game != null) {
        val name = game.players.firstOrNull { it.seat == game.winnerSeat }?.name
        val winner = name?.let { n -> pod.playerIds.mapNotNull { id -> players.firstOrNull { it.id == id } }.firstOrNull { sameName(it.name, n) } }
        return PodResult(winner?.id, fromTable = true)
    }
    return pod.winnerId?.let { PodResult(it, fromTable = false) }
}

/** The id a winner tapped on game night is saved under on the user's deck — one per pod. */
fun nightResultId(nightId: String, podId: String) = "night-$nightId-$podId"

/**
 * The user's game in [pod] with [winnerId] tapped as its winner, for the deck they played — or null
 * when they weren't in it or played no deck of theirs. Saved under [nightResultId], so tapping
 * another winner replaces it. (A game played on the life counter is saved by the table instead.)
 */
fun nightResultOf(nightId: String, pod: NightPod, players: List<NightPlayer>, winnerId: String, now: Long): Pair<String, GameResult>? {
    val seated = pod.playerIds.mapNotNull { id -> players.firstOrNull { it.id == id } }
    val me = seated.firstOrNull { it.kind == NightPlayerKind.ME } ?: return null
    val deckId = me.deckId ?: return null
    val others = seated.filter { it !== me }
    return deckId to GameResult(
        id = nightResultId(nightId, pod.id),
        result = if (winnerId == me.id) "WIN" else "LOSS",
        opponent = others.joinToString(", ") { it.name }.ifBlank { null },
        playedAt = now,
        turns = null,
        minutes = null,
        commanders = others.mapNotNull { it.commander }
    )
}

/** A fresh night, or [from]'s players (with what they played) on a new night. */
fun newNight(id: String, now: Long, from: GameNight? = null): GameNight =
    GameNight(id = id, createdAt = now, format = from?.format ?: NightFormat.COMMANDER, seed = 0, players = from?.players.orEmpty())
