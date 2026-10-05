package com.mtgcompanion.app.ui.lifecounter

// Dungeons and venturing: the four dungeon cards as small maps of rooms, and where a player's venture
// marker can go next. Pure: no state of its own. Mirrors the web app's src/lifecounter/dungeons.ts —
// the same ids, room names, texts and links, so the remote protocol's {"dungeon":{"id":…,"room":…}}
// means the same room on both.
//
// The rules (CR 309, 701.49): to venture into the dungeon, a player who isn't in one picks Lost Mine
// of Phandelver, Dungeon of the Mad Mage or Tomb of Annihilation and enters its first room; one who
// is moves to a room joined below the one they're in. Undercity is entered only by venturing into
// Undercity (taking the initiative, and the upkeep of whoever holds it) — and venturing into
// Undercity while already in a dungeon moves on in that one instead. Reaching the last room
// completes the dungeon; the next venture starts a new one.

/**
 * One room. [row]: how far down the card it is, from 0 (Oubliette, spanning two rows, is 1.5).
 * [next]: the rooms joined below it; none for the last room. [x]: where it sits across the card,
 * 0 to 1, when it isn't simply spaced along its row.
 */
data class DungeonRoom(val id: String, val name: String, val row: Double, val text: String, val next: List<String> = emptyList(), val x: Double? = null)

data class Dungeon(val id: String, val name: String, val rooms: List<DungeonRoom>)

/** Where a player's venture marker is: dungeon and room ids. */
data class DungeonState(val dungeon: String, val room: String)

/** A player's venture marker and how many dungeons they've completed this game. */
data class Venture(val dungeon: DungeonState?, val completed: Int)

private fun room(id: String, name: String, row: Double, text: String, vararg next: String, x: Double? = null) =
    DungeonRoom(id, name, row, text, next.toList(), x)

val DUNGEONS: List<Dungeon> = listOf(
    Dungeon(
        "lost-mine", "Lost Mine of Phandelver", listOf(
            room("cave-entrance", "Cave Entrance", 0.0, "Scry 1.", "goblin-lair", "mine-tunnels"),
            room("goblin-lair", "Goblin Lair", 1.0, "Create a 1/1 red Goblin creature token.", "storeroom", "dark-pool"),
            room("mine-tunnels", "Mine Tunnels", 1.0, "Create a Treasure token.", "dark-pool", "fungi-cavern"),
            room("storeroom", "Storeroom", 2.0, "Put a +1/+1 counter on target creature.", "temple-of-dumathoin"),
            room("dark-pool", "Dark Pool", 2.0, "Each opponent loses 1 life and you gain 1 life.", "temple-of-dumathoin"),
            room("fungi-cavern", "Fungi Cavern", 2.0, "Target creature gets -4/-0 until your next turn.", "temple-of-dumathoin"),
            room("temple-of-dumathoin", "Temple of Dumathoin", 3.0, "Draw a card.")
        )
    ),
    Dungeon(
        "mad-mage", "Dungeon of the Mad Mage", listOf(
            room("yawning-portal", "Yawning Portal", 0.0, "You gain 1 life.", "dungeon-level"),
            room("dungeon-level", "Dungeon Level", 1.0, "Scry 1.", "goblin-bazaar", "twisted-caverns"),
            room("goblin-bazaar", "Goblin Bazaar", 2.0, "Create a Treasure token.", "lost-level"),
            room("twisted-caverns", "Twisted Caverns", 2.0, "Target creature can't attack until your next turn.", "lost-level"),
            room("lost-level", "Lost Level", 3.0, "Scry 2.", "runestone-caverns", "muirals-graveyard"),
            room("runestone-caverns", "Runestone Caverns", 4.0, "Exile the top two cards of your library. You may play them.", "deep-mines"),
            room("muirals-graveyard", "Muiral's Graveyard", 4.0, "Create two 1/1 black Skeleton creature tokens.", "deep-mines"),
            room("deep-mines", "Deep Mines", 5.0, "Scry 3.", "mad-wizards-lair"),
            room("mad-wizards-lair", "Mad Wizard's Lair", 6.0, "Draw three cards and reveal them. You may cast one of them without paying its mana cost.")
        )
    ),
    Dungeon(
        "tomb", "Tomb of Annihilation", listOf(
            room("trapped-entry", "Trapped Entry", 0.0, "Each player loses 1 life.", "veils-of-fear", "oubliette"),
            // The card's right-hand path skips a row: Oubliette sits beside Veils of Fear and Sandfall Cell.
            room("veils-of-fear", "Veils of Fear", 1.0, "Each player loses 2 life unless they discard a card.", "sandfall-cell", x = 0.3),
            room("sandfall-cell", "Sandfall Cell", 2.0, "Each player loses 2 life unless they sacrifice an artifact, a creature, or a land.", "cradle-of-the-death-god", x = 0.3),
            room("oubliette", "Oubliette", 1.5, "Discard a card and sacrifice an artifact, a creature, and a land.", "cradle-of-the-death-god", x = 0.7),
            room("cradle-of-the-death-god", "Cradle of the Death God", 3.0, "Create The Atropal, a legendary 4/4 black God Horror creature token with deathtouch.")
        )
    ),
    Dungeon(
        "undercity", "Undercity", listOf(
            room("secret-entrance", "Secret Entrance", 0.0, "Search your library for a basic land card, reveal it, put it into your hand, then shuffle.", "forge", "lost-well"),
            room("forge", "Forge", 1.0, "Put two +1/+1 counters on target creature.", "trap", "arena"),
            room("lost-well", "Lost Well", 1.0, "Scry 2.", "arena", "stash"),
            room("trap", "Trap!", 2.0, "Target player loses 5 life.", "archives"),
            room("arena", "Arena", 2.0, "Goad target creature.", "archives", "catacombs"),
            room("stash", "Stash", 2.0, "Create a Treasure token.", "catacombs"),
            room("archives", "Archives", 3.0, "Draw a card.", "throne-of-the-dead-three"),
            room("catacombs", "Catacombs", 3.0, "Create a 4/1 black Skeleton creature token with menace.", "throne-of-the-dead-three"),
            room(
                "throne-of-the-dead-three", "Throne of the Dead Three", 4.0,
                "Reveal the top ten cards of your library. Put a creature card from among them onto the battlefield with three +1/+1 counters on it. It gains hexproof until your next turn. Then shuffle."
            )
        )
    )
)

/** The dungeons a plain "venture into the dungeon" may start: all but Undercity. */
val VENTURE_DUNGEONS = listOf("lost-mine", "mad-mage", "tomb")

fun dungeonById(id: String?): Dungeon? = DUNGEONS.firstOrNull { it.id == id }

fun roomOf(state: DungeonState?): DungeonRoom? =
    state?.let { s -> dungeonById(s.dungeon)?.rooms?.firstOrNull { it.id == s.room } }

/** Whether [state] is a dungeon's last room: the dungeon is completed. */
fun dungeonDone(state: DungeonState?): Boolean = roomOf(state)?.next?.isEmpty() == true

/** In a dungeon and not yet at its end. */
fun inDungeon(state: DungeonState?): Boolean = roomOf(state) != null && !dungeonDone(state)

/**
 * Where a venture can go from [state]: room ids below the current room while in a dungeon, else the
 * dungeons that can be started — Undercity alone when [intoUndercity], the other three otherwise.
 */
fun ventureOptions(state: DungeonState?, intoUndercity: Boolean): List<String> = when {
    inDungeon(state) -> roomOf(state)!!.next
    intoUndercity -> listOf("undercity")
    else -> VENTURE_DUNGEONS
}

/**
 * [v] after venturing to [to] (a room id, or a dungeon id when starting one), or null when that
 * isn't a place this venture can go. Entering a dungeon's last room completes it.
 */
fun venture(v: Venture, to: String, intoUndercity: Boolean = false): Venture? {
    if (to !in ventureOptions(v.dungeon, intoUndercity)) return null
    if (inDungeon(v.dungeon)) {
        val next = DungeonState(v.dungeon!!.dungeon, to)
        return Venture(next, v.completed + if (dungeonDone(next)) 1 else 0)
    }
    val d = dungeonById(to)!!
    return Venture(DungeonState(d.id, d.rooms.first().id), v.completed)
}

/** A dungeon state read from the wire: null unless it names a real room. */
fun parseDungeonState(id: String?, room: String?): DungeonState? =
    if (id != null && room != null && roomOf(DungeonState(id, room)) != null) DungeonState(id, room) else null

/** A completed-dungeons count read from the wire: a whole number from 0 to 99. */
fun cleanCompleted(raw: Int): Int = raw.coerceIn(0, 99)

/** Every way through [dungeon], first room to last, as room ids. */
fun dungeonPaths(dungeon: Dungeon): List<List<String>> {
    val byId = dungeon.rooms.associateBy { it.id }
    fun walk(id: String): List<List<String>> {
        val r = byId.getValue(id)
        return if (r.next.isEmpty()) listOf(listOf(id)) else r.next.flatMap { n -> walk(n).map { listOf(id) + it } }
    }
    return walk(dungeon.rooms.first().id)
}

/** Where a room sits on a small map: [x] from 0 to 1 across its row, [row] down the card. */
data class RoomSpot(val id: String, val x: Double, val row: Double)

fun dungeonLayout(dungeon: Dungeon): List<RoomSpot> {
    // Rows read left to right in the order the card lists them.
    val rows = dungeon.rooms.groupBy { it.row }.mapValues { (_, rooms) -> rooms.map { it.id } }
    return dungeon.rooms.map { r ->
        val inRow = rows.getValue(r.row)
        RoomSpot(r.id, r.x ?: ((inRow.indexOf(r.id) + 1).toDouble() / (inRow.size + 1)), r.row)
    }
}
