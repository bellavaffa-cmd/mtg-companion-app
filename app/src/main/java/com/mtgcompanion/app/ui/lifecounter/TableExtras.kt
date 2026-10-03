package com.mtgcompanion.app.ui.lifecounter

import org.json.JSONArray
import org.json.JSONObject

// What a seat's deck brings to the table, and the game's clocks — plain Kotlin, so it can be tested
// without a phone. The deck's tokens and its "at the beginning of…" cards come either from the table
// owner's own deck (their seat, see LifeCounterSettings.meSeat) or from a player's remote, which
// sends them in a "deckInfo" message (see the protocol notes at the top of LifeCounterRemote.kt).

// ---- Deck tokens ----

/** A token a seat's deck makes, e.g. a 1/1 Goblin. [id] is a Scryfall id of one printing of it. */
data class SeatToken(val id: String, val name: String, val pt: String? = null, val art: String? = null) {
    /** "Goblin 1/1". */
    val label: String get() = if (pt.isNullOrBlank()) name else "$name $pt"
}

/** The tokens and the start-of-turn trigger cards of the deck played at a seat. */
data class SeatDeckInfo(val deck: String?, val tokens: List<SeatToken>, val triggers: List<TriggerCard>)

/** "Goblin 1/1 ×3" — what a token counter on a tile says. */
fun tokenChipText(token: SeatToken, count: Int): String = "${token.label} ×$count"

/** The most of anything a deckInfo message may carry, and the longest a name in it may be. */
const val DECK_INFO_MAX_ITEMS = 40
const val DECK_INFO_MAX_NAME = 80

// ---- Trigger reminders ----

/** The steps of your own turn a card can ask to be remembered at, in the order they come. */
enum class TriggerStep(val wire: String, val label: String, val phrase: String) {
    UPKEEP("upkeep", "Upkeep", "at the beginning of your upkeep"),
    DRAW("draw", "Draw step", "at the beginning of your draw step"),
    COMBAT("combat", "Combat", "at the beginning of combat on your turn"),
    END("end", "End step", "at the beginning of your end step");

    companion object {
        fun ofWire(wire: String?): TriggerStep? = entries.firstOrNull { it.wire == wire }
    }
}

/** A card in the deck that does something at [step] of its owner's turn. */
data class TriggerCard(val name: String, val step: TriggerStep)

/** The steps [oracleText] triggers at, on its owner's turn. Case doesn't matter; reminder text in brackets doesn't count. */
fun triggerStepsOf(oracleText: String?): Set<TriggerStep> {
    if (oracleText.isNullOrBlank()) return emptySet()
    val text = oracleText.replace(Regex("\\([^)]*\\)"), " ").lowercase()
    return TriggerStep.entries.filter { it.phrase in text }.toSet()
}

/**
 * The trigger cards among [cards] (name to oracle text), once each per step, in step order and then
 * in the order the deck lists them.
 */
fun deckTriggers(cards: List<Pair<String, String?>>): List<TriggerCard> {
    val seen = LinkedHashSet<TriggerCard>()
    for (step in TriggerStep.entries) {
        for ((name, text) in cards) if (step in triggerStepsOf(text)) seen += TriggerCard(name, step)
    }
    return seen.toList()
}

/** One line per step: "Upkeep: Phyrexian Arena, Bitterblossom". Empty with nothing to remind. */
fun reminderLines(triggers: List<TriggerCard>): List<String> =
    triggers.groupBy { it.step }.toSortedMap(compareBy { it.ordinal }).map { (step, cards) ->
        "${step.label}: ${cards.joinToString(", ") { it.name }}"
    }

// ---- The game clock and the turn timer ----

/**
 * How long the game has been going, less the time it sat paused. [pausedAt] is set while it's
 * paused; [pausedMs] is the paused time already over.
 */
data class GameClock(val startedAt: Long, val pausedAt: Long? = null, val pausedMs: Long = 0) {
    val paused: Boolean get() = pausedAt != null

    fun elapsed(now: Long): Long = ((pausedAt ?: now) - startedAt - pausedMs).coerceAtLeast(0)

    fun pause(now: Long): GameClock = if (paused) this else copy(pausedAt = now)

    fun resume(now: Long): GameClock = pausedAt?.let { copy(pausedAt = null, pausedMs = pausedMs + (now - it).coerceAtLeast(0)) } ?: this
}

/** "4:05", or "1:02:09" past an hour. A negative time reads the same, with a minus. */
fun formatClock(ms: Long): String {
    val sign = if (ms < 0) "−" else ""
    val total = kotlin.math.abs(ms) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$sign$h:${pad2(m)}:${pad2(s)}" else "$sign$m:${pad2(s)}"
}

private fun pad2(n: Long) = n.toString().padStart(2, '0')

/** A game's length for its records, in whole minutes and never less than one. */
fun gameMinutes(elapsedMs: Long): Int = (elapsedMs / 60_000).toInt().coerceAtLeast(1)

/** The turn timer's choices in minutes; 0 is off. */
val TURN_TIMER_CHOICES = listOf(0, 1, 2, 3, 5)

/**
 * Time left in the turn (ms), or null with the timer off. Measured on the game clock, so pausing the
 * game pauses the turn too. Below zero once the turn has run over.
 */
fun turnTimeLeft(limitMinutes: Int, turnStartElapsed: Long, elapsedNow: Long): Long? =
    if (limitMinutes <= 0) null else limitMinutes * 60_000L - (elapsedNow - turnStartElapsed)

// ---- On the wire (see LifeCounterRemote.kt) ----

/** A seat's deck tokens as the table publishes them, with how many of each are out. */
data class RemoteToken(val id: String, val name: String, val pt: String?, val count: Int) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("pt", pt ?: JSONObject.NULL).put("count", count)

    companion object {
        fun parseList(a: JSONArray?): List<RemoteToken>? = a?.let {
            (0 until it.length()).mapNotNull { i ->
                val o = it.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").takeIf { s -> s.isNotEmpty() } ?: return@mapNotNull null
                RemoteToken(id, o.optString("name"), if (o.isNull("pt")) null else o.optString("pt").ifEmpty { null }, o.optInt("count").coerceAtLeast(0))
            }
        }
    }
}

/** The game clock as the table last sent it: [elapsedMs] when it sent it. */
data class RemoteClock(val elapsedMs: Long, val paused: Boolean)

/** The turn timer: [seconds] a turn, [leftMs] of this one left when the table sent it (below 0: over). */
data class RemoteTurnTimer(val seconds: Int, val leftMs: Long)

/** The deckInfo message a remote sends for its seat. */
fun deckInfoAction(info: SeatDeckInfo): JSONObject = JSONObject()
    .put("type", "deckInfo")
    .put("deck", info.deck ?: JSONObject.NULL)
    .put("tokens", JSONArray(info.tokens.take(DECK_INFO_MAX_ITEMS).map { JSONObject().put("id", it.id).put("name", it.name).put("pt", it.pt ?: JSONObject.NULL) }))
    .put("triggers", JSONArray(info.triggers.take(DECK_INFO_MAX_ITEMS).map { JSONObject().put("name", it.name).put("step", it.step.wire) }))

/**
 * A deckInfo message as the table reads it — trusting no more of it than it must: at most
 * [DECK_INFO_MAX_ITEMS] of each, names cut to [DECK_INFO_MAX_NAME], unknown steps dropped. Null when it
 * isn't one.
 */
fun parseDeckInfo(action: JSONObject): SeatDeckInfo? {
    if (action.optString("type") != "deckInfo") return null
    fun JSONObject.text(key: String, max: Int): String? = if (isNull(key)) null else optString(key).trim().take(max).ifEmpty { null }
    val tokens = action.optJSONArray("tokens")?.let { a ->
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.text("id", 64) ?: return@mapNotNull null
            val name = o.text("name", DECK_INFO_MAX_NAME) ?: return@mapNotNull null
            SeatToken(id, name, o.text("pt", 12))
        }.distinctBy { it.id }.take(DECK_INFO_MAX_ITEMS)
    }.orEmpty()
    val triggers = action.optJSONArray("triggers")?.let { a ->
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val name = o.text("name", DECK_INFO_MAX_NAME) ?: return@mapNotNull null
            val step = TriggerStep.ofWire(o.optString("step")) ?: return@mapNotNull null
            TriggerCard(name, step)
        }.distinct().take(DECK_INFO_MAX_ITEMS)
    }.orEmpty()
    return SeatDeckInfo(action.text("deck", DECK_INFO_MAX_NAME), tokens, triggers)
}

/** A change to one of the seat's deck-token counts. */
fun tokenAction(id: String, delta: Int): JSONObject = JSONObject().put("type", "token").put("id", id).put("delta", delta)

/** [counts] after [id] goes up or down by [delta]: never below 0, and only for a token the seat's deck makes. */
fun changedTokenCounts(counts: Map<String, Int>, tokens: List<SeatToken>, id: String, delta: Int): Map<String, Int> {
    if (tokens.none { it.id == id }) return counts
    val next = ((counts[id] ?: 0) + delta).coerceIn(0, 999)
    return counts + (id to next)
}

/**
 * Who holds the "hold on" after [seat] says "OK, go on" ([RemoteActions.holdOk]): anyone else at the
 * table can let the game go on; the holder lets go with hold(false) as before.
 */
fun holdAfterOk(holder: Int?, seat: Int): Int? = if (holder != null && holder != seat) null else holder
