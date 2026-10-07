package com.mtgcompanion.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// League mode: a pod runs seasons over its shared game log (PodStats.kt). A season is a name, the
// days it runs (or a number of game nights) and its scoring rules, kept on the server
// (supabase/migrations/20261006060000_pod_seasons.sql) so the whole pod sees the same season. The
// table — points, wins, win rate, streak, points per game night, the champion — is worked out here
// from the pod's games, the same on every phone. Mirrors the web app's src/decks/league.ts: same
// rules, same JSON keys, same tie-breaks.

/** What the league shows until the server has seasons (the migration isn't applied yet). */
const val LEAGUE_UNAVAILABLE = "Leagues aren't available yet"

/** Points for each thing that can happen in a game. Whole numbers 0–[MAX_RULE_POINTS]. */
data class LeagueRules(
    /** Which preset these came from ([LEAGUE_PRESETS]), or [PRESET_CUSTOM]. */
    val preset: String = PRESET_STANDARD,
    val win: Int = 3,
    /** For finishing second, when the game recorded it. */
    val second: Int = 1,
    /** For each player in a drawn game. */
    val draw: Int = 1,
    /** For playing at all. */
    val played: Int = 0,
    /** For knocking out the game's first player. */
    val firstBlood: Int = 1,
    /** Extra for a win with a deck its player hasn't played in this pod before. */
    val newDeckWin: Int = 0
)

const val PRESET_STANDARD = "standard"
const val PRESET_WINS = "wins"
const val PRESET_EVERYONE = "everyone"
const val PRESET_CUSTOM = "custom"
const val MAX_RULE_POINTS = 10

data class LeaguePreset(val id: String, val label: String, val rules: LeagueRules)

val LEAGUE_PRESETS = listOf(
    LeaguePreset(PRESET_STANDARD, "Standard", LeagueRules(PRESET_STANDARD, win = 3, second = 1, draw = 1, played = 0, firstBlood = 1, newDeckWin = 0)),
    LeaguePreset(PRESET_WINS, "Wins only", LeagueRules(PRESET_WINS, win = 1, second = 0, draw = 0, played = 0, firstBlood = 0, newDeckWin = 0)),
    LeaguePreset(PRESET_EVERYONE, "Everyone scores", LeagueRules(PRESET_EVERYONE, win = 3, second = 1, draw = 1, played = 1, firstBlood = 1, newDeckWin = 1))
)

/** The rules in words: "Win 3 · Second 1 · Draw 1 · First blood +1". */
fun rulesSummary(r: LeagueRules): String = listOfNotNull(
    "Win ${r.win}",
    r.second.takeIf { it > 0 }?.let { "Second $it" },
    r.draw.takeIf { it > 0 }?.let { "Draw $it" },
    r.played.takeIf { it > 0 }?.let { "Playing $it" },
    r.firstBlood.takeIf { it > 0 }?.let { "First blood +$it" },
    r.newDeckWin.takeIf { it > 0 }?.let { "Win with a new deck +$it" }
).joinToString(" · ")

/** One row of a season's table. [key] as [playerKey]. [streak]: "W3", "L1", "D1", or "" before any game. */
data class LeagueStanding(
    val key: String,
    val userId: String?,
    val name: String,
    val rank: Int,
    val points: Int,
    val games: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val streak: String,
    val firstBloods: Int = 0,
    val seconds: Int = 0,
    val newDeckWins: Int = 0
) {
    val winRate: Int get() = if (games == 0) 0 else wins * 100 / games
}

data class Season(
    val id: String,
    val podId: String,
    val name: String,
    /** The first day, "YYYY-MM-DD". */
    val startsOn: String,
    /** The last day, or null to run until [maxNights] game nights (or until it's ended). */
    val endsOn: String?,
    val maxNights: Int?,
    val rules: LeagueRules,
    val createdBy: String?,
    val createdAt: Long,
    /** When it was ended: games after it don't count. Null while it hasn't been. */
    val endedAt: Long?,
    /** The champion's name, kept when it ended ("Priya", or "Priya & Sam"). */
    val champion: String?,
    /** The final table, kept when it ended. */
    val standings: List<LeagueStanding>?
)

/** One player's points on one game night. */
data class NightScore(val key: String, val userId: String?, val name: String, val points: Int)

/** A game night of the season: its day, its games and everyone's points that night, most first. */
data class NightPoints(val night: String, val games: Int, val scores: List<NightScore>)

data class SeasonTable(
    /** The games that count, oldest first. */
    val games: List<PodGame>,
    /** The game nights played, oldest first. */
    val nights: List<String>,
    val standings: List<LeagueStanding>,
    /** Newest night first. */
    val perNight: List<NightPoints>,
    /** Everyone ranked first (more than one on a tie); empty before any game. */
    val champions: List<LeagueStanding>
)

enum class SeasonStatus { UPCOMING, RUNNING, OVER, ENDED }

/** The game night a game belongs to: its day where it was played, "YYYY-MM-DD". */
fun nightOf(playedAt: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(playedAt).atZone(zone).toLocalDate().toString()

/**
 * The pod games that count for [season], oldest first: played on its days, before it was ended,
 * and — when it runs for [Season.maxNights] — on its first that many game nights.
 */
fun seasonGames(season: Season, games: List<PodGame>, zone: ZoneId = ZoneId.systemDefault()): List<PodGame> {
    val inDays = games.filter { g ->
        val night = nightOf(g.playedAt, zone)
        night >= season.startsOn &&
            (season.endsOn == null || night <= season.endsOn) &&
            (season.endedAt == null || g.playedAt <= season.endedAt)
    }.sortedWith(compareBy<PodGame> { it.playedAt }.thenBy { it.id })
    val max = season.maxNights ?: return inDays
    val kept = inDays.map { nightOf(it.playedAt, zone) }.distinct().sorted().take(max).toSet()
    return inDays.filter { nightOf(it.playedAt, zone) in kept }
}

/**
 * Where [season] is on [today] ("YYYY-MM-DD"): not started yet, running, over by its own rules (its
 * last day has passed, or its game nights are all played and that last night is past) but not yet
 * ended on the server, or ended.
 */
fun seasonStatus(season: Season, games: List<PodGame>, today: String, zone: ZoneId = ZoneId.systemDefault()): SeasonStatus {
    if (season.endedAt != null) return SeasonStatus.ENDED
    if (today < season.startsOn) return SeasonStatus.UPCOMING
    if (season.endsOn != null && today > season.endsOn) return SeasonStatus.OVER
    val max = season.maxNights
    if (max != null) {
        val nights = seasonGames(season, games, zone).map { nightOf(it.playedAt, zone) }.distinct()
        if (nights.size >= max && today > nights.max()) return SeasonStatus.OVER
    }
    return SeasonStatus.RUNNING
}

/** What a deck is known by in the log: its name, else its commander; null when neither was recorded. */
private fun deckLabel(p: PodPlayer): String? = (p.deck ?: p.commander)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

/** One seat's points in a game. [newDeck]: the seat won with a deck its player hadn't played in the pod before. */
fun gamePoints(p: PodPlayer, rules: LeagueRules, newDeck: Boolean = false): Int {
    var points = rules.played
    when (p.result) {
        "WIN" -> points += rules.win + if (newDeck) rules.newDeckWin else 0
        "DRAW" -> points += rules.draw
        else -> if (p.place == 2) points += rules.second
    }
    if (p.firstBlood) points += rules.firstBlood
    return points
}

/** The streak shown in the table: the newest result and how many in a row ("W3"), from results oldest first. */
fun streakOf(results: List<String>): String {
    val last = results.lastOrNull() ?: return ""
    val count = results.asReversed().takeWhile { it == last }.size
    return when (last) { "WIN" -> "W"; "LOSS" -> "L"; else -> "D" } + count
}

/** Higher first: points, then wins, then win rate (exactly, not rounded). Name only orders exact ties. */
private fun beats(a: LeagueStanding, b: LeagueStanding): Int = when {
    a.points != b.points -> b.points.compareTo(a.points)
    a.wins != b.wins -> b.wins.compareTo(a.wins)
    else -> (b.wins.toLong() * a.games).compareTo(a.wins.toLong() * b.games)
}

private class LeagueTally(val key: String) {
    var userId: String? = null
    var name = ""
    var points = 0
    var wins = 0
    var losses = 0
    var draws = 0
    var firstBloods = 0
    var seconds = 0
    var newDeckWins = 0
    val results = mutableListOf<String>()
}

/**
 * [season]'s table from the pod's games ([allGames], the whole log — "a new deck" looks at games
 * before the season too). Ranked by points, then wins, then win rate; players equal on all three
 * share a rank, and a shared first place makes joint champions.
 */
fun seasonTable(season: Season, allGames: List<PodGame>, zone: ZoneId = ZoneId.systemDefault()): SeasonTable {
    val counted = seasonGames(season, allGames, zone)
    // When each player first played each deck in the pod.
    val firstPlayed = HashMap<String, Long>()
    for (g in allGames) for (p in g.players) {
        val label = deckLabel(p) ?: continue
        val k = playerKey(p) + "|" + label
        val seen = firstPlayed[k]
        if (seen == null || g.playedAt < seen) firstPlayed[k] = g.playedAt
    }
    val tallies = LinkedHashMap<String, LeagueTally>()
    val nights = LinkedHashMap<String, Pair<IntArray, LinkedHashMap<String, Int>>>()
    for (g in counted) {
        val night = nightOf(g.playedAt, zone)
        val (gameCount, nightScores) = nights.getOrPut(night) { IntArray(1) to LinkedHashMap() }
        gameCount[0]++
        for (p in g.players.distinctBy { playerKey(it) }) {
            val key = playerKey(p)
            val t = tallies.getOrPut(key) { LeagueTally(key) }
            t.userId = p.userId ?: t.userId
            t.name = p.name.trim()
            val label = deckLabel(p)
            val newDeck = p.result == "WIN" && label != null && (firstPlayed[key + "|" + label] ?: Long.MAX_VALUE) >= g.playedAt
            val points = gamePoints(p, season.rules, newDeck)
            t.points += points
            when (p.result) { "WIN" -> t.wins++; "LOSS" -> t.losses++; else -> t.draws++ }
            if (p.firstBlood) t.firstBloods++
            if (p.result == "LOSS" && p.place == 2) t.seconds++
            if (newDeck) t.newDeckWins++
            t.results += p.result
            nightScores[key] = (nightScores[key] ?: 0) + points
        }
    }
    val unranked = tallies.values.map { t ->
        LeagueStanding(t.key, t.userId, t.name, 0, t.points, t.results.size, t.wins, t.losses, t.draws, streakOf(t.results), t.firstBloods, t.seconds, t.newDeckWins)
    }.sortedWith { a, b -> beats(a, b).takeIf { it != 0 } ?: compareValuesBy(a, b, { it.name.lowercase() }, { it.key }) }
    val standings = mutableListOf<LeagueStanding>()
    unranked.forEachIndexed { i, s ->
        val rank = if (i > 0 && beats(unranked[i - 1], s) == 0) standings[i - 1].rank else i + 1
        standings += s.copy(rank = rank)
    }
    val perNight = nights.entries.sortedByDescending { it.key }.map { (night, v) ->
        NightPoints(
            night,
            v.first[0],
            v.second.map { (key, points) -> tallies.getValue(key).let { NightScore(key, it.userId, it.name, points) } }
                .sortedWith(compareByDescending<NightScore> { it.points }.thenBy { it.name.lowercase() })
        )
    }
    return SeasonTable(counted, nights.keys.sorted(), standings, perNight, standings.filter { it.rank == 1 })
}

/** "Season 1 champion: Priya", "Season 1 champions: Priya & Sam"; null with nobody to name. */
fun championLine(seasonName: String, champion: String?): String? {
    if (champion.isNullOrBlank()) return null
    return "$seasonName ${if (" & " in champion) "champions" else "champion"}: $champion"
}

/** The champions' names together, as kept on the server. */
fun championNames(champions: List<LeagueStanding>): String? = champions.joinToString(" & ") { it.name }.ifEmpty { null }

/** The name a new season gets: "Season 3" after two. */
fun nextSeasonName(seasons: List<Season>): String = "Season ${seasons.size + 1}"

/** The season that hasn't been ended yet, if any (one at a time per pod). */
fun runningSeason(seasons: List<Season>): Season? = seasons.firstOrNull { it.endedAt == null }

/** When a season runs, in words ([shortDay] is Loans.kt's): "1 Oct – 31 Dec", "From 1 Oct, for 8 game nights", "From 1 Oct". */
fun seasonDays(season: Season): String = when {
    season.endsOn != null -> "${shortDay(season.startsOn)} – ${shortDay(season.endsOn)}"
    season.maxNights != null -> "From ${shortDay(season.startsOn)}, for ${season.maxNights} game ${if (season.maxNights == 1) "night" else "nights"}"
    else -> "From ${shortDay(season.startsOn)}"
}

/**
 * When a season that's over by its own rules ended: the end of its last day, or of its last game
 * night — never after [now].
 */
fun seasonEndedAt(season: Season, table: SeasonTable, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val last = season.endsOn ?: table.nights.lastOrNull().takeIf { season.maxNights != null } ?: return now
    val end = LocalDate.parse(last).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    return minOf(end, now)
}

/** The checks before saving a season, in words for the screen, or null. Days are "YYYY-MM-DD". */
fun seasonProblem(name: String, startsOn: String, endsOn: String?, maxNights: Int?): String? = when {
    name.isBlank() -> "Give the season a name."
    name.trim().length > 40 -> "Keep the name to 40 characters."
    runCatching { LocalDate.parse(startsOn) }.isFailure -> "Pick the day it starts."
    endsOn != null && runCatching { LocalDate.parse(endsOn) }.isFailure -> "Pick the day it ends."
    endsOn != null && endsOn < startsOn -> "It has to end after it starts."
    maxNights != null && maxNights !in 1..100 -> "A season runs for 1 to 100 game nights."
    else -> null
}

/**
 * Which pod's league a game night counts for: the pod with a running season that has the most of
 * tonight's players in it (by account; the user always counts). Ties go to the first. Null when
 * no pod has a running season.
 */
fun leaguePodFor(candidates: List<Pair<String, List<String>>>, nightUserIds: Set<String>): String? =
    candidates.maxByOrNull { (_, members) -> members.count { it in nightUserIds } }?.first

// ---- JSON (the server's answers, and what it's sent) ----

fun rulesJson(r: LeagueRules): JSONObject = JSONObject()
    .put("preset", r.preset)
    .put("win", r.win)
    .put("second", r.second)
    .put("draw", r.draw)
    .put("played", r.played)
    .put("firstBlood", r.firstBlood)
    .put("newDeckWin", r.newDeckWin)

private fun points(o: JSONObject, key: String, fallback: Int): Int =
    if (o.has(key) && !o.isNull(key)) o.optInt(key, fallback).coerceIn(0, MAX_RULE_POINTS) else fallback

/** Rules from the server; anything missing takes the Standard preset's value. */
fun parseRules(o: JSONObject?): LeagueRules {
    val d = LeagueRules()
    if (o == null) return d
    return LeagueRules(
        preset = o.optString("preset").ifEmpty { PRESET_CUSTOM },
        win = points(o, "win", d.win),
        second = points(o, "second", d.second),
        draw = points(o, "draw", d.draw),
        played = points(o, "played", d.played),
        firstBlood = points(o, "firstBlood", d.firstBlood),
        newDeckWin = points(o, "newDeckWin", d.newDeckWin)
    )
}

/** The final table as end_pod_season keeps it. */
fun standingsJson(standings: List<LeagueStanding>): JSONArray = JSONArray().apply {
    standings.forEach { s ->
        put(
            JSONObject()
                .put("key", s.key)
                .put("userId", s.userId ?: JSONObject.NULL)
                .put("name", s.name)
                .put("rank", s.rank)
                .put("points", s.points)
                .put("games", s.games)
                .put("wins", s.wins)
                .put("losses", s.losses)
                .put("draws", s.draws)
                .put("streak", s.streak)
        )
    }
}

private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).ifEmpty { null }

fun parseStandings(a: JSONArray): List<LeagueStanding> = (0 until a.length()).map { i ->
    val o = a.getJSONObject(i)
    LeagueStanding(
        key = o.optString("key"),
        userId = o.text("userId"),
        name = o.optString("name"),
        rank = o.optInt("rank", i + 1),
        points = o.optInt("points"),
        games = o.optInt("games"),
        wins = o.optInt("wins"),
        losses = o.optInt("losses"),
        draws = o.optInt("draws"),
        streak = o.optString("streak")
    )
}

/** pod_seasons' answer, newest first. */
fun parseSeasons(text: String): List<Season> {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed == "null") return emptyList()
    val a = JSONArray(trimmed)
    return (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        Season(
            id = o.getString("id"),
            podId = o.optString("podId"),
            name = o.optString("name"),
            startsOn = o.optString("startsOn"),
            endsOn = o.text("endsOn"),
            maxNights = if (o.isNull("maxNights") || !o.has("maxNights")) null else o.optInt("maxNights"),
            rules = parseRules(o.optJSONObject("rules")),
            createdBy = o.text("createdBy"),
            createdAt = o.optLong("createdAt"),
            endedAt = if (o.isNull("endedAt") || !o.has("endedAt")) null else o.optLong("endedAt"),
            champion = o.text("champion"),
            standings = o.optJSONArray("standings")?.let(::parseStandings)
        )
    }
}

/** Whoever started a season, or the pod's owner, changes or ends it (the server checks the same). */
fun canManageSeason(season: Season, me: String, podOwner: String): Boolean = season.createdBy == me || podOwner == me
