package com.mtgcompanion.app.ui.lifecounter

import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.toArgb
import com.mtgcompanion.app.data.cleanMulligans
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

// Players' phones as remotes for a life counter table. The table publishes the game on its match's
// private channel; a player who joined a seat by QR code sends requests for their own seat, which
// the table applies (or ignores, with remotes switched off). The same messages go between this app
// and the web app — keep src/lifecounter/remote.ts in step.
//
// ---- Added with the gameplay update (all optional, so old and new clients keep working) ----
//
// Every addition is backwards compatible: a table ignores an action "type" it doesn't know, a remote
// ignores a state key it doesn't know, and each side reads a missing key as "not supported" (null).
//
// Remote -> table (actions, for the sender's own seat):
//   {"type":"deckInfo","deck":"Krenko Goblins"|null,
//    "tokens":[{"id":"<scryfall id>","name":"Goblin","pt":"1/1"|null}, …],
//    "triggers":[{"name":"Phyrexian Arena","step":"upkeep"|"draw"|"combat"|"end"}, …]}
//       The tokens the seat's deck makes and its "at the beginning of your …" cards. Sent when the
//       player picks a deck, and again whenever the table's state shows no "tokens" for the seat.
//       The table keeps at most 40 of each, names cut to 80 characters. Unknown steps are dropped.
//   {"type":"token","id":"<token id from deckInfo>","delta":1|-1|…}
//       One of the seat's deck tokens up or down (|delta| <= 100; never below 0). An older remote
//       still moves the plain Tokens counter with {"type":"counter","counter":"tokens",…}.
//   {"type":"holdOk"}
//       "OK, go on": any seat other than the one holding clears a "hold on" ("hold" in the state).
//       The holder itself still lets go with {"type":"hold","on":false}.
//
// Table -> remotes (keys of the published state):
//   players[i].tokens: [{"id":"…","name":"Goblin","pt":"1/1"|null,"count":3}, …]
//       Present (possibly []) once the table knows the seat's deck tokens; absent/null otherwise —
//       a remote then keeps its own counts, as before.
//   clock: {"elapsedMs":754000,"paused":false}
//       The game clock when this state was sent (time paused doesn't count). Count on from the
//       moment it arrived, unless paused. Absent from older tables: use startedAt.
//   turnTimer: {"seconds":120,"leftMs":87000} | null
//       The per-turn timer of the player whose turn it is ("turn"): leftMs left of it when this
//       state was sent, below 0 once the turn has run over. null/absent: no turn timer.
//
// ---- Added with dungeons, the new counters and mulligans (all optional, the same rules) ----
//
// Remote -> table:
//   {"type":"counter","counter":"rad"|"speed"|"ring",…}
//       Three more counter names. Speed and the Ring stop at 4. An older table doesn't know them
//       and ignores the request (counterOfWire gives null).
//   {"type":"venture","to":"<dungeon id or room id>","undercity":true|false}
//       Ventures: starts a dungeon ("lost-mine", "mad-mage", "tomb"; "undercity" only with
//       "undercity":true) or moves to a room joined below the seat's current room. Anything else is
//       ignored. Room and dungeon ids are in Dungeons.kt / dungeons.ts.
//   {"type":"leaveDungeon"}
//       Takes the seat's marker out of its dungeon without completing it.
//   {"type":"ringBearer","name":"Frodo"|null}
//       The seat's Ring-bearer (trimmed, at most 60 characters; blank or null: nobody).
//   {"type":"mulligan","value":0..7|null}
//       Mulligans the seat took this game (0: kept seven; null: not recorded).
//
// Table -> remotes, on each players[i] (absent from older tables, which read as "not tracked"):
//   counters.rad / counters.speed / counters.ring — in the existing counters object; an older
//       remote shows only the counters it knows and ignores the rest.
//   dungeon: {"id":"undercity","room":"arena"} | null    dungeonsCompleted: 2
//   ringBearer: "Frodo" | null                           mulligans: 1 | null
//   A table that knows these always sends dungeonsCompleted; without it a remote doesn't offer them.
//
// Already in the protocol and used for the remote extras: "hold" (hold on), "target" (pointing,
// shown as an announce), "concede", "planar" (with "plane" in the state), and showCard's lookup for
// rulings (done on the phone; nothing goes to the table unless the card is shown).

const val REMOTE_VERSION = 1

/** Counter names on the wire: poison, then PlayerCounter's others in lower case. */
fun PlayerCounter.wire(): String = name.lowercase()
fun counterOfWire(name: String): PlayerCounter? = PlayerCounter.entries.firstOrNull { it.wire() == name }

data class RemoteDamage(val from: Int, val slot: Int, val amount: Int)

data class RemoteSeat(
    val seat: Int,
    val name: String,
    /** The seat colour (sRGB, #rrggbb) and the ink that reads on it. */
    val color: String,
    val ink: String,
    val life: Int,
    /** Why they're out (LIFE, POISON, COMMANDER_DAMAGE, KILLED), or null. */
    val out: String?,
    val poison: Int,
    /** Counters beyond poison, by wire name. */
    val counters: Map<String, Int>,
    /** Damage this seat has taken from each opponent's commander ([RemoteDamage.slot] 1: their partner). */
    val commanderDamage: List<RemoteDamage>,
    val background: String?,
    val deck: String?,
    /** The commander of that deck ("A & B" for partners), for the other players' game records. */
    val commander: String?,
    val userId: String?,
    val avatarPath: String?,
    /** Whether this seat has a change of its own it could undo. */
    val canUndo: Boolean,
    /** Plays two commanders (a partner), so damage from each is kept apart. */
    val partner: Boolean,
    /** Times this seat has cast its commander; the tax is twice that. 0 from a table that doesn't say. */
    val commanderCasts: Int = 0,
    /** The same for its partner, when [partner]. */
    val partnerCasts: Int = 0,
    /** The seat's deck tokens and how many of each are out; null from a table that doesn't track them. */
    val tokens: List<RemoteToken>? = null,
    /** Where the seat's venture marker is. */
    val dungeon: DungeonState? = null,
    /** Null from a table that doesn't know dungeons, the Ring or mulligans (see the notes at the top). */
    val dungeonsCompleted: Int? = null,
    val ringBearer: String? = null,
    /** Mulligans this game; null: not recorded. */
    val mulligans: Int? = null
) {
    fun damageFrom(from: Int, slot: Int): Int = commanderDamage.firstOrNull { it.from == from && it.slot == slot }?.amount ?: 0
}

data class RemoteTurn(val seat: Int, val number: Int)
data class RemoteShownCard(val name: String, val imageUrl: String, val seat: Int)
data class RemoteOver(val winner: Int?, val turns: Int, val minutes: Int)
/** The Planechase plane the table is on, and how many planes are left in its deck. */
data class RemotePlane(val name: String, val imageUrl: String?, val left: Int)

/**
 * Something that just happened, for everyone to see for a few seconds: a roll, a coin, the planar
 * die, an emote, or a player pointing at another. Only the latest is sent; [id] is how the table
 * and the remotes tell a new one from the one they've already shown.
 */
data class RemoteAnnounce(
    val id: String,
    val seat: Int,
    /** "roll", "coin", "planar", "emote" or "target". */
    val kind: String,
    /** When it happened (epoch ms). */
    val at: Long,
    /** A roll's die. */
    val sides: Int? = null,
    /** What came up: a roll's number, "Heads"/"Tails", or the planar die's face. */
    val value: String? = null,
    /** An emote's id (see [REMOTE_EMOTES]). */
    val emote: String? = null,
    /** The seat a target points at. */
    val to: Int? = null
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("seat", seat).put("kind", kind).put("at", at).also { o ->
        sides?.let { o.put("sides", it) }
        value?.let { o.put("value", it) }
        emote?.let { o.put("emote", it) }
        to?.let { o.put("to", it) }
    }

    companion object {
        fun parse(o: JSONObject): RemoteAnnounce? = runCatching {
            RemoteAnnounce(
                id = o.getString("id"), seat = o.getInt("seat"), kind = o.getString("kind"), at = o.optLong("at"),
                sides = if (o.has("sides") && !o.isNull("sides")) o.optInt("sides") else null,
                value = if (o.isNull("value")) null else o.optString("value"),
                emote = if (o.isNull("emote")) null else o.optString("emote"),
                to = if (o.has("to") && !o.isNull("to")) o.optInt("to") else null
            )
        }.getOrNull()
    }
}

data class RemoteState(
    val v: Int,
    val gameId: String,
    /** False when the table's owner has switched remotes off. */
    val remotes: Boolean,
    /** Whose turn it is, when the table tracks turns. */
    val turn: RemoteTurn?,
    val startedAt: Long,
    val longPress: Int,
    val players: List<RemoteSeat>,
    val shownCard: RemoteShownCard?,
    val over: RemoteOver?,
    // Everything below came later: a table from before leaves it out, and it reads as nothing.
    val monarch: Int? = null,
    val initiative: Int? = null,
    /** "DAY" or "NIGHT"; null while the table isn't tracking it. */
    val dayNight: String? = null,
    /** The seat that asked everyone to hold on, until they let go or the turn passes. */
    val hold: Int? = null,
    val plane: RemotePlane? = null,
    val announce: RemoteAnnounce? = null,
    /** The game clock as sent; null from an older table. */
    val clock: RemoteClock? = null,
    /** The turn timer, while the table runs one. */
    val turnTimer: RemoteTurnTimer? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("v", v).put("gameId", gameId).put("remotes", remotes)
        .put("turn", turn?.let { JSONObject().put("seat", it.seat).put("number", it.number) } ?: JSONObject.NULL)
        .put("startedAt", startedAt).put("longPress", longPress)
        .put("players", JSONArray(players.map { p ->
            JSONObject()
                .put("seat", p.seat).put("name", p.name).put("color", p.color).put("ink", p.ink).put("life", p.life)
                .put("out", p.out ?: JSONObject.NULL).put("poison", p.poison)
                .put("counters", JSONObject(p.counters as Map<*, *>))
                .put("commanderDamage", JSONArray(p.commanderDamage.map { JSONObject().put("from", it.from).put("slot", it.slot).put("amount", it.amount) }))
                .put("background", p.background ?: JSONObject.NULL).put("deck", p.deck ?: JSONObject.NULL)
                .put("commander", p.commander ?: JSONObject.NULL)
                .put("userId", p.userId ?: JSONObject.NULL).put("avatarPath", p.avatarPath ?: JSONObject.NULL)
                .put("canUndo", p.canUndo).put("partner", p.partner)
                .put("commanderCasts", p.commanderCasts).put("partnerCasts", p.partnerCasts)
                .also { o -> p.tokens?.let { t -> o.put("tokens", JSONArray(t.map { it.toJson() })) } }
                .also { o ->
                    p.dungeonsCompleted?.let { done ->
                        o.put("dungeon", p.dungeon?.let { JSONObject().put("id", it.dungeon).put("room", it.room) } ?: JSONObject.NULL)
                            .put("dungeonsCompleted", done)
                            .put("ringBearer", p.ringBearer ?: JSONObject.NULL)
                            .put("mulligans", p.mulligans ?: JSONObject.NULL)
                    }
                }
        }))
        .put("shownCard", shownCard?.let { JSONObject().put("name", it.name).put("imageUrl", it.imageUrl).put("seat", it.seat) } ?: JSONObject.NULL)
        .put("over", over?.let { JSONObject().put("winner", it.winner ?: JSONObject.NULL).put("turns", it.turns).put("minutes", it.minutes) } ?: JSONObject.NULL)
        .put("monarch", monarch ?: JSONObject.NULL).put("initiative", initiative ?: JSONObject.NULL)
        .put("dayNight", dayNight ?: JSONObject.NULL).put("hold", hold ?: JSONObject.NULL)
        .put("plane", plane?.let { JSONObject().put("name", it.name).put("imageUrl", it.imageUrl ?: JSONObject.NULL).put("left", it.left) } ?: JSONObject.NULL)
        .put("announce", announce?.toJson() ?: JSONObject.NULL)
        .also { o -> clock?.let { o.put("clock", JSONObject().put("elapsedMs", it.elapsedMs).put("paused", it.paused)) } }
        .put("turnTimer", turnTimer?.let { JSONObject().put("seconds", it.seconds).put("leftMs", it.leftMs) } ?: JSONObject.NULL)

    companion object {
        /** Null for anything that isn't a game this version understands. */
        fun parse(o: JSONObject): RemoteState? = runCatching {
            if (o.optInt("v") != REMOTE_VERSION) return null
            fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
            val players = o.getJSONArray("players").let { a ->
                (0 until a.length()).map { i ->
                    val p = a.getJSONObject(i)
                    val counters = p.optJSONObject("counters")?.let { c -> c.keys().asSequence().associateWith { c.optInt(it) } } ?: emptyMap()
                    val damage = p.optJSONArray("commanderDamage")?.let { d ->
                        (0 until d.length()).map { j -> d.getJSONObject(j).let { RemoteDamage(it.getInt("from"), it.optInt("slot"), it.getInt("amount")) } }
                    } ?: emptyList()
                    RemoteSeat(
                        seat = p.getInt("seat"), name = p.optString("name"), color = p.optString("color", "#888888"), ink = p.optString("ink", "#000"),
                        life = p.getInt("life"), out = p.str("out"), poison = p.optInt("poison"), counters = counters, commanderDamage = damage,
                        background = p.str("background"), deck = p.str("deck"), commander = p.str("commander"), userId = p.str("userId"), avatarPath = p.str("avatarPath"),
                        canUndo = p.optBoolean("canUndo"), partner = p.optBoolean("partner"),
                        commanderCasts = p.optInt("commanderCasts", 0).coerceAtLeast(0),
                        partnerCasts = p.optInt("partnerCasts", 0).coerceAtLeast(0),
                        tokens = RemoteToken.parseList(p.optJSONArray("tokens")),
                        dungeon = p.optJSONObject("dungeon")?.let { d -> parseDungeonState(d.str("id"), d.str("room")) },
                        // A table that knows dungeons, the Ring and mulligans always sends this.
                        dungeonsCompleted = (p.opt("dungeonsCompleted") as? Number)?.let { cleanCompleted(it.toInt()) },
                        ringBearer = cleanRingBearer(p.str("ringBearer")),
                        mulligans = (p.opt("mulligans") as? Number)?.let { cleanMulligans(it.toInt()) }
                    )
                }
            }
            fun JSONObject.seat(key: String): Int? = if (isNull(key)) null else optInt(key, -1).takeIf { it >= 0 }
            RemoteState(
                v = o.getInt("v"), gameId = o.optString("gameId"), remotes = o.optBoolean("remotes", true),
                turn = o.optJSONObject("turn")?.let { RemoteTurn(it.getInt("seat"), it.getInt("number")) },
                startedAt = o.optLong("startedAt"), longPress = o.optInt("longPress", 10), players = players,
                shownCard = o.optJSONObject("shownCard")?.let { RemoteShownCard(it.getString("name"), it.getString("imageUrl"), it.getInt("seat")) },
                over = o.optJSONObject("over")?.let { RemoteOver(if (it.isNull("winner")) null else it.optInt("winner"), it.optInt("turns"), it.optInt("minutes")) },
                monarch = o.seat("monarch"), initiative = o.seat("initiative"),
                dayNight = o.str("dayNight")?.takeIf { it == "DAY" || it == "NIGHT" },
                hold = o.seat("hold"),
                plane = o.optJSONObject("plane")?.let { RemotePlane(it.optString("name"), it.str("imageUrl"), it.optInt("left")) },
                announce = o.optJSONObject("announce")?.let { RemoteAnnounce.parse(it) },
                clock = o.optJSONObject("clock")?.let { RemoteClock(it.optLong("elapsedMs").coerceAtLeast(0), it.optBoolean("paused")) },
                turnTimer = o.optJSONObject("turnTimer")?.let { t ->
                    t.optInt("seconds", 0).takeIf { it > 0 }?.let { RemoteTurnTimer(it, t.optLong("leftMs")) }
                }
            )
        }.getOrNull()
    }
}

/** Requests a remote sends for its own seat. */
object RemoteActions {
    fun hello() = JSONObject().put("type", "hello")
    fun life(delta: Int) = JSONObject().put("type", "life").put("delta", delta)
    /** [counter]: "poison" or a PlayerCounter's wire name. */
    fun counter(counter: String, delta: Int) = JSONObject().put("type", "counter").put("counter", counter).put("delta", delta)
    /** Damage this seat took from [from]'s commander. */
    fun commanderDamage(from: Int, slot: Int, delta: Int) = JSONObject().put("type", "commanderDamage").put("from", from).put("slot", slot).put("delta", delta)
    /** Damage this seat's commander dealt to [to]. */
    fun dealtDamage(to: Int, slot: Int, delta: Int) = JSONObject().put("type", "dealtDamage").put("to", to).put("slot", slot).put("delta", delta)
    fun endTurn() = JSONObject().put("type", "endTurn")
    fun undo() = JSONObject().put("type", "undo")
    /** [partner]: the deck has two commanders, so the table keeps them apart. */
    fun background(url: String?, deck: String?, commander: String?, partner: Boolean = false) = JSONObject().put("type", "background").put("url", url ?: JSONObject.NULL)
        .put("deck", deck ?: JSONObject.NULL).put("commander", commander ?: JSONObject.NULL).put("partner", partner)
    fun showCard(name: String, imageUrl: String) = JSONObject().put("type", "showCard").put("name", name).put("imageUrl", imageUrl)
    fun hideCard() = JSONObject().put("type", "hideCard")
    /** Take the monarch ([take]), or give it up — only the holder can. */
    fun monarch(take: Boolean) = JSONObject().put("type", "monarch").put("take", take)
    fun initiative(take: Boolean) = JSONObject().put("type", "initiative").put("take", take)
    /** "DAY", "NIGHT", or null to stop tracking it. */
    fun dayNight(value: String?) = JSONObject().put("type", "dayNight").put("value", value ?: JSONObject.NULL)
    /** The table rolls, so nobody has to trust a phone: [sides] 2 is a coin. */
    fun roll(sides: Int) = JSONObject().put("type", "roll").put("sides", sides)
    /** [what]: "roll" (the planar die) or "planeswalk". */
    fun planar(what: String) = JSONObject().put("type", "planar").put("what", what)
    /** [slot] 1: the partner. */
    fun commanderCast(delta: Int, slot: Int = 0) = JSONObject().put("type", "commanderCast").put("delta", delta).put("slot", slot)
    fun hold(on: Boolean) = JSONObject().put("type", "hold").put("on", on)
    fun emote(emote: String) = JSONObject().put("type", "emote").put("emote", emote)
    fun target(to: Int) = JSONObject().put("type", "target").put("to", to)
    fun concede() = JSONObject().put("type", "concede")
    /** The seat's deck tokens and trigger cards (see the notes at the top). */
    fun deckInfo(info: SeatDeckInfo) = deckInfoAction(info)
    /** One of the seat's deck tokens up or down. */
    fun token(id: String, delta: Int) = tokenAction(id, delta)
    /** "OK, go on": clears someone else's hold on. */
    fun holdOk() = JSONObject().put("type", "holdOk")
    /** Ventures to [to]: a dungeon to start, or a room below the seat's ([undercity]: venturing into Undercity). */
    fun venture(to: String, undercity: Boolean) = JSONObject().put("type", "venture").put("to", to).put("undercity", undercity)
    fun leaveDungeon() = JSONObject().put("type", "leaveDungeon")
    fun ringBearer(name: String?) = JSONObject().put("type", "ringBearer").put("name", name ?: JSONObject.NULL)
    /** 0 to 7, or null: not recorded. */
    fun mulligan(value: Int?) = JSONObject().put("type", "mulligan").put("value", value ?: JSONObject.NULL)
}

/** The dice a remote can ask the table to roll; 2 is a coin. */
val REMOTE_DICE = listOf(4, 6, 8, 10, 12, 20)

/** Emotes by wire id, with what to show. Anything else a remote sends is ignored. */
val REMOTE_EMOTES = linkedMapOf(
    "gg" to "🤝 GG",
    "thinking" to "🤔 Thinking…",
    "wait" to "⏳ One sec",
    "laugh" to "😂",
    "wow" to "😮",
    "sorry" to "🙏 Sorry"
)

/**
 * Who holds something only one seat can (the monarch, the initiative, a hold-on) after [seat] asks
 * to [take] it or let it go. Letting go of something you don't hold changes nothing.
 */
fun claimedBy(holder: Int?, seat: Int, take: Boolean): Int? = when {
    take -> seat
    holder == seat -> null
    else -> holder
}

/**
 * A roll the table makes for a remote: a coin for [sides] 2, a die for the others it has. Null for
 * a die it doesn't have.
 */
fun remoteRoll(seat: Int, sides: Int, random: kotlin.random.Random, id: String, at: Long): RemoteAnnounce? = when (sides) {
    2 -> RemoteAnnounce(id, seat, "coin", at, value = if (random.nextBoolean()) "Heads" else "Tails")
    in REMOTE_DICE -> RemoteAnnounce(id, seat, "roll", at, sides = sides, value = random.nextInt(1, sides + 1).toString())
    else -> null
}

/**
 * Whether [seat] may roll the planar die or planeswalk: only in a Planechase game that has a plane
 * up, and — when the table tracks turns — only on their own turn, as the rules have it.
 */
fun planarAllowed(planechase: Boolean, hasPlane: Boolean, turnTracker: Boolean, turnSeat: Int, seat: Int): Boolean =
    planechase && hasPlane && (!turnTracker || turnSeat == seat)

/** What an announcement says, e.g. "Ana rolled a d20: 14". [nameOf] gives a seat's name. */
fun announceText(a: RemoteAnnounce, nameOf: (Int) -> String): String {
    val who = nameOf(a.seat)
    return when (a.kind) {
        "roll" -> "$who rolled a d${a.sides}: ${a.value}"
        "coin" -> "$who flipped a coin: ${a.value}"
        "planar" -> when (a.value) {
            "PLANESWALK" -> "$who planeswalks"
            "CHAOS" -> "$who rolled Chaos"
            else -> "$who rolled a blank"
        }
        "emote" -> "$who: ${REMOTE_EMOTES[a.emote] ?: ""}"
        "target" -> a.to?.let { "$who points at ${nameOf(it)}" } ?: who
        else -> who
    }
}

/** Pictures a remote may put behind its tile or show on the table: Scryfall, Giphy, profile pictures. */
fun allowedRemoteImage(url: String?, scryfallOnly: Boolean = false): Boolean {
    if (url == null || url.length > 500) return false
    val u = runCatching { URI(url) }.getOrNull() ?: return false
    if (u.scheme != "https") return false
    val host = u.host ?: return false
    if (host == "cards.scryfall.io") return true
    if (scryfallOnly) return false
    return Regex("media\\d?\\.giphy\\.com").matches(host) || host == "i.giphy.com" ||
        (host.endsWith(".supabase.co") && (u.path ?: "").startsWith("/storage/v1/object/public/avatars/"))
}

/** A seat colour as #rrggbb (sRGB), for the web app and other phones. */
fun seatHex(colorIndex: Int): Pair<String, String> {
    val seat = seatColor(colorIndex)
    val argb = seat.color.convert(ColorSpaces.Srgb).toArgb()
    return String.format("#%06x", argb and 0xFFFFFF) to (if (seat.whiteText) "#fff" else "#000")
}
