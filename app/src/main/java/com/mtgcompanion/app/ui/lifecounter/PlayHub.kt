package com.mtgcompanion.app.ui.lifecounter

// The Play tab's short status lines: the start card's table, and one line under each of Game night,
// Playgroup and Events. Pure, so the wording is tested. The web app's twin is
// src/lifecounter/playHub.ts (tests: PlayHubTest.kt / tests/lifecounter/playHub.test.ts).

/** How many recent games the Play tab lists before "All games". */
const val RECENT_SHOWN = 5

private fun plural(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"

/** "4 players · 40 life" — the table a new game starts with. */
fun startGameLine(players: Int, life: Int): String = "${plural(players, "player")} · $life life"

/** "Last game: Sam, Alex and Jo", "Last game: Sam, Alex and 2 more"; null with nobody to name. */
fun lastPlayersLine(names: List<String>): String? {
    val shown = names.map { it.trim() }.filter { it.isNotEmpty() }
    return when {
        shown.isEmpty() -> null
        shown.size == 1 -> "Last game: ${shown[0]}"
        shown.size <= 3 -> "Last game: ${shown.dropLast(1).joinToString(", ")} and ${shown.last()}"
        else -> "Last game: ${shown.take(2).joinToString(", ")} and ${shown.size - 2} more"
    }
}

/**
 * Game night's line: tonight's players and pods, the last night's players once it's gone stale
 * ([stale]: older than a night), or what it does when nobody's been added.
 */
fun gameNightStatus(players: Int, pods: Int, stale: Boolean): String = when {
    players == 0 -> "Fair pods by power"
    stale -> "Last time: ${plural(players, "player")}"
    else -> "Tonight: ${plural(players, "player")}" + if (pods > 0) " · ${plural(pods, "pod")}" else ""
}

/** Playgroup's line: "12 games · nemesis Sam", "3 games", or "No games yet". */
fun playgroupStatus(games: Int, nemesis: String?): String =
    if (games == 0) "No games yet" else plural(games, "game") + (nemesis?.let { " · nemesis $it" } ?: "")

/** Events' line: "1 event running", "3 events", or what it does when there are none. */
fun eventsStatus(running: Int, total: Int): String = when {
    running > 0 -> "${plural(running, "event")} running"
    total > 0 -> plural(total, "event")
    else -> "Swiss or Commander pods"
}
