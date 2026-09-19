package com.mtgcompanion.app.ui.lifecounter

import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

// Players' phones as remotes for a life counter table. The table publishes the game on its match's
// private channel; a player who joined a seat by QR code sends requests for their own seat, which
// the table applies (or ignores, with remotes switched off). The same messages go between this app
// and the web app — keep src/lifecounter/remote.ts in step.

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
    val userId: String?,
    val avatarPath: String?,
    /** Whether this seat has a change of its own it could undo. */
    val canUndo: Boolean,
    /** Plays two commanders (a partner), so damage from each is kept apart. */
    val partner: Boolean
) {
    fun damageFrom(from: Int, slot: Int): Int = commanderDamage.firstOrNull { it.from == from && it.slot == slot }?.amount ?: 0
}

data class RemoteTurn(val seat: Int, val number: Int)
data class RemoteShownCard(val name: String, val imageUrl: String, val seat: Int)
data class RemoteOver(val winner: Int?, val turns: Int, val minutes: Int)

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
    val over: RemoteOver?
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
                .put("userId", p.userId ?: JSONObject.NULL).put("avatarPath", p.avatarPath ?: JSONObject.NULL)
                .put("canUndo", p.canUndo).put("partner", p.partner)
        }))
        .put("shownCard", shownCard?.let { JSONObject().put("name", it.name).put("imageUrl", it.imageUrl).put("seat", it.seat) } ?: JSONObject.NULL)
        .put("over", over?.let { JSONObject().put("winner", it.winner ?: JSONObject.NULL).put("turns", it.turns).put("minutes", it.minutes) } ?: JSONObject.NULL)

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
                        background = p.str("background"), deck = p.str("deck"), userId = p.str("userId"), avatarPath = p.str("avatarPath"),
                        canUndo = p.optBoolean("canUndo"), partner = p.optBoolean("partner")
                    )
                }
            }
            RemoteState(
                v = o.getInt("v"), gameId = o.optString("gameId"), remotes = o.optBoolean("remotes", true),
                turn = o.optJSONObject("turn")?.let { RemoteTurn(it.getInt("seat"), it.getInt("number")) },
                startedAt = o.optLong("startedAt"), longPress = o.optInt("longPress", 10), players = players,
                shownCard = o.optJSONObject("shownCard")?.let { RemoteShownCard(it.getString("name"), it.getString("imageUrl"), it.getInt("seat")) },
                over = o.optJSONObject("over")?.let { RemoteOver(if (it.isNull("winner")) null else it.optInt("winner"), it.optInt("turns"), it.optInt("minutes")) }
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
    fun background(url: String?, deck: String?) = JSONObject().put("type", "background").put("url", url ?: JSONObject.NULL).put("deck", deck ?: JSONObject.NULL)
    fun showCard(name: String, imageUrl: String) = JSONObject().put("type", "showCard").put("name", name).put("imageUrl", imageUrl)
    fun hideCard() = JSONObject().put("type", "hideCard")
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
