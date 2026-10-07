package com.mtgcompanion.app.data

import org.json.JSONArray
import org.json.JSONObject

// A pod's shared game log, summed up for the whole group: each player's record, the commanders
// played most and winning most, each player's nemesis, how long games run, and the latest games.
// The games come from the pod_games server function (supabase/migrations/
// 20261005000000_pod_games.sql). Mirrors the web app's src/decks/podStats.ts.

/** One seat in a pod game: a pod member (by account) or a guest (by name). [result]: WIN, LOSS or DRAW. */
data class PodPlayer(
    val userId: String?,
    val name: String,
    val commander: String? = null,
    val deck: String? = null,
    val result: String,
    /** Where they finished (1 = won, 2 = second…), when it was recorded. For league points (League.kt). */
    val place: Int? = null,
    /** Whether they knocked out the first player of the game. */
    val firstBlood: Boolean = false
)

data class PodGame(
    val id: String,
    val clientId: String,
    val recordedBy: String,
    val playedAt: Long,
    val format: String,
    val turns: Int?,
    val minutes: Int?,
    val players: List<PodPlayer>
)

/** One player's games in the pod. [key]: "u:<userId>" for a member, "n:<lower-case name>" for a guest. */
data class PlayerRecord(
    val key: String,
    val userId: String?,
    /** The name they had in their newest game. */
    val name: String,
    val games: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int
) {
    val winRate: Int get() = if (games == 0) 0 else wins * 100 / games
}

/** Games one commander was played in, by anyone. */
data class CommanderRecord(val name: String, val games: Int, val wins: Int) {
    val winRate: Int get() = if (games == 0) 0 else wins * 100 / games
}

/** Who a player does worst against. */
data class PlayerNemesis(val player: PlayerRecord, val nemesis: Matchup)

data class PodStats(
    val games: Int,
    /** Everyone who has played, most games first. */
    val players: List<PlayerRecord>,
    /** Commanders with [PLAYGROUP_MIN_GAMES] or more games, most-played first. */
    val mostPlayed: List<CommanderRecord>,
    /** The same commanders, best win rate first. */
    val best: List<CommanderRecord>,
    /** Each player who has a nemesis, in the order of [players]. */
    val nemeses: List<PlayerNemesis>,
    /** Averages over the games that kept time; null when none did. */
    val averageMinutes: Int?,
    val averageTurns: Int?,
    /** The newest games, at most [POD_LATEST]. */
    val latest: List<PodGame>
)

/** How many games the latest list shows. */
const val POD_LATEST = 10

/** Who a seat is: a member by their account, anyone else by their name (any case, trimmed). */
fun playerKey(userId: String?, name: String): String = if (!userId.isNullOrEmpty()) "u:$userId" else "n:${name.trim().lowercase()}"
fun playerKey(p: PodPlayer): String = playerKey(p.userId, p.name)

private fun average(xs: List<Int>): Int? = if (xs.isEmpty()) null else Math.round(xs.sum().toDouble() / xs.size).toInt()

private class Tally(val name: String) {
    var games = 0
    var wins = 0
    var losses = 0
    var draws = 0
}

fun podStats(games: List<PodGame>): PodStats {
    val newest = games.sortedByDescending { it.playedAt }
    val players = LinkedHashMap<String, Pair<String?, Tally>>()
    val commanders = LinkedHashMap<String, Tally>()
    // Per player, how they did against each opponent: one game counts once against each other seat.
    val against = HashMap<String, LinkedHashMap<String, Tally>>()

    for (g in newest) {
        // A name listed twice in one game counts once.
        val seats = g.players.distinctBy { playerKey(it) }
        for (p in seats) {
            val key = playerKey(p)
            val r = players.getOrPut(key) { p.userId to Tally(p.name.trim()) }.second
            r.games++
            when (p.result) { "WIN" -> r.wins++; "LOSS" -> r.losses++; else -> r.draws++ }

            val commander = p.commander?.trim().orEmpty()
            if (commander.isNotEmpty()) {
                val c = commanders.getOrPut(commander.lowercase()) { Tally(commander) }
                c.games++
                if (p.result == "WIN") c.wins++
            }

            val mine = against.getOrPut(key) { LinkedHashMap() }
            for (o in seats) {
                val other = playerKey(o)
                if (other == key) continue
                val m = mine.getOrPut(other) { Tally(o.name.trim()) }
                m.games++
                when (p.result) { "WIN" -> m.wins++; "LOSS" -> m.losses++ }
            }
        }
    }

    val playerList = players.map { (key, v) -> PlayerRecord(key, v.first, v.second.name, v.second.games, v.second.wins, v.second.losses, v.second.draws) }
        .sortedWith(compareByDescending<PlayerRecord> { it.games }.thenByDescending { it.wins }.thenBy { it.name.lowercase() })
    val ranked = commanders.values.map { CommanderRecord(it.name, it.games, it.wins) }.filter { it.games >= PLAYGROUP_MIN_GAMES }
    return PodStats(
        games = games.size,
        players = playerList,
        mostPlayed = ranked.sortedWith(compareByDescending<CommanderRecord> { it.games }.thenByDescending { it.wins }.thenBy { it.name.lowercase() }),
        best = ranked.sortedWith(compareByDescending<CommanderRecord> { it.wins.toDouble() / it.games }.thenByDescending { it.games }.thenBy { it.name.lowercase() }),
        nemeses = playerList.mapNotNull { player ->
            val matchups = against[player.key]?.values?.map { Matchup(it.name, it.games, it.wins, it.losses) }.orEmpty()
            nemesisOf(matchups)?.let { PlayerNemesis(player, it) }
        },
        averageMinutes = average(games.mapNotNull { g -> g.minutes?.takeIf { it > 0 } }),
        averageTurns = average(games.mapNotNull { g -> g.turns?.takeIf { it > 0 } }),
        latest = newest.take(POD_LATEST)
    )
}

/** Whoever recorded a game can delete it, and so can the pod's owner. */
fun canDeletePodGame(game: PodGame, me: String, podOwner: String): Boolean = game.recordedBy == me || podOwner == me

/**
 * The game as a result on the user's own deck, the way the life counter logs one: the other players'
 * names joined ", ", and their commanders. Null when [me] isn't one of the players.
 */
fun deckResultOf(playedAt: Long, turns: Int?, minutes: Int?, players: List<PodPlayer>, me: String, id: String): GameResult? {
    val mine = players.firstOrNull { it.userId == me } ?: return null
    val others = players.filter { it !== mine }
    return GameResult(
        id = id,
        result = mine.result,
        opponent = others.joinToString(", ") { it.name.trim() }.ifEmpty { null },
        playedAt = playedAt,
        turns = turns?.takeIf { it > 0 },
        minutes = minutes?.takeIf { it > 0 },
        commanders = others.mapNotNull { p -> p.commander?.trim()?.takeIf { it.isNotEmpty() } }
    )
}

/**
 * Checks a game before recording it: answers what's wrong, in words for the screen, or null.
 * The server checks the same (2–10 players, names 1–40 characters, at most one winner).
 */
fun podGameProblem(players: List<PodPlayer>): String? = when {
    players.size < 2 -> "Pick at least two players."
    players.size > 10 -> "A game holds up to 10 players."
    players.any { it.name.isBlank() } -> "Every guest needs a name."
    players.any { it.name.trim().length > 40 } -> "Keep names to 40 characters."
    players.map { playerKey(it) }.toSet().size != players.size -> "Someone is in the game twice."
    players.count { it.result == "WIN" } > 1 -> "Only one player can win."
    players.count { it.firstBlood } > 1 -> "Only one player can draw first blood."
    else -> null
}

/** The players as record_pod_game takes them. */
fun podPlayersJson(players: List<PodPlayer>): JSONArray = JSONArray().apply {
    players.forEach { p ->
        put(
            JSONObject()
                .put("userId", p.userId ?: JSONObject.NULL)
                .put("name", p.name)
                .put("commander", p.commander ?: JSONObject.NULL)
                .put("deck", p.deck ?: JSONObject.NULL)
                .put("result", p.result)
                .apply {
                    // Only sent when known: older servers drop keys they don't know.
                    p.place?.let { put("place", it) }
                    if (p.firstBlood) put("firstBlood", true)
                }
        )
    }
}

private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key).ifEmpty { null }
private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)

/** pod_games' answer: [{id, clientId, recordedBy, playedAt (ms), format, turns, minutes, players}]. */
fun parsePodGames(text: String): List<PodGame> {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed == "null") return emptyList()
    val a = JSONArray(trimmed)
    return (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        val ps = o.optJSONArray("players") ?: JSONArray()
        PodGame(
            id = o.getString("id"),
            clientId = o.optString("clientId"),
            recordedBy = o.optString("recordedBy"),
            playedAt = o.optLong("playedAt"),
            format = o.optString("format"),
            turns = o.intOrNull("turns"),
            minutes = o.intOrNull("minutes"),
            players = (0 until ps.length()).map { j ->
                val p = ps.getJSONObject(j)
                PodPlayer(
                    userId = p.stringOrNull("userId"),
                    name = p.optString("name"),
                    commander = p.stringOrNull("commander"),
                    deck = p.stringOrNull("deck"),
                    result = p.optString("result").uppercase(),
                    place = p.intOrNull("place")?.takeIf { it in 1..10 },
                    firstBlood = p.optBoolean("firstBlood", false)
                )
            }
        )
    }
}
