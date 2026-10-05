package com.mtgcompanion.app.ui.lifecounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dungeons: the four cards' rooms and links, and venturing through them. The web app has the same
 * checks — see MtgCompanionWeb/tests/lifecounter/dungeons.test.ts.
 */
class DungeonsTest {

    @Test
    fun `each dungeon has the rooms of its card`() {
        assertEquals(
            listOf(
                "lost-mine:7:Cave Entrance>Temple of Dumathoin",
                "mad-mage:9:Yawning Portal>Mad Wizard's Lair",
                "tomb:5:Trapped Entry>Cradle of the Death God",
                "undercity:9:Secret Entrance>Throne of the Dead Three"
            ),
            DUNGEONS.map { "${it.id}:${it.rooms.size}:${it.rooms.first().name}>${it.rooms.last().name}" }
        )
    }

    @Test
    fun `every room leads somewhere real, below it, and every way through ends at the last room`() {
        for (d in DUNGEONS) {
            val ids = d.rooms.map { it.id }.toSet()
            assertEquals(d.id, d.rooms.size, ids.size)
            val reached = mutableSetOf(d.rooms.first().id)
            for (r in d.rooms) for (n in r.next) {
                assertTrue("${d.id}: ${r.id} -> $n", n in ids)
                assertTrue("${d.id}: $n is below ${r.id}", roomOf(DungeonState(d.id, n))!!.row > r.row)
                reached += n
            }
            assertEquals("${d.id}: every room can be reached", d.rooms.size, reached.size)
            assertEquals("${d.id}: one last room", 1, d.rooms.count { it.next.isEmpty() })
        }
    }

    @Test
    fun `the ways through each dungeon`() {
        fun paths(id: String) = dungeonPaths(dungeonById(id)!!).map { it.joinToString(" > ") }
        assertEquals(
            listOf(
                "cave-entrance > goblin-lair > storeroom > temple-of-dumathoin",
                "cave-entrance > goblin-lair > dark-pool > temple-of-dumathoin",
                "cave-entrance > mine-tunnels > dark-pool > temple-of-dumathoin",
                "cave-entrance > mine-tunnels > fungi-cavern > temple-of-dumathoin"
            ),
            paths("lost-mine")
        )
        assertEquals(4, paths("mad-mage").size)
        assertTrue(paths("mad-mage").all { it.split(" > ").size == 7 })
        assertEquals(
            listOf(
                "trapped-entry > veils-of-fear > sandfall-cell > cradle-of-the-death-god",
                "trapped-entry > oubliette > cradle-of-the-death-god"
            ),
            paths("tomb")
        )
        assertEquals(
            listOf(
                "secret-entrance > forge > trap > archives > throne-of-the-dead-three",
                "secret-entrance > forge > arena > archives > throne-of-the-dead-three",
                "secret-entrance > forge > arena > catacombs > throne-of-the-dead-three",
                "secret-entrance > lost-well > arena > archives > throne-of-the-dead-three",
                "secret-entrance > lost-well > arena > catacombs > throne-of-the-dead-three",
                "secret-entrance > lost-well > stash > catacombs > throne-of-the-dead-three"
            ),
            paths("undercity")
        )
    }

    @Test
    fun `a venture starts a dungeon, Undercity only by venturing into it`() {
        assertEquals(listOf("lost-mine", "mad-mage", "tomb"), ventureOptions(null, false))
        assertEquals(listOf("undercity"), ventureOptions(null, true))
        val none = Venture(null, 0)
        assertNull(venture(none, "undercity"))
        assertEquals(Venture(DungeonState("undercity", "secret-entrance"), 0), venture(none, "undercity", true))
        assertEquals(Venture(DungeonState("tomb", "trapped-entry"), 0), venture(none, "tomb"))
        assertNull(venture(none, "trapped-entry"))
    }

    @Test
    fun `venturing moves to a room below, and the last room completes the dungeon`() {
        var v = venture(Venture(null, 2), "tomb")!!
        assertEquals(listOf("veils-of-fear", "oubliette"), ventureOptions(v.dungeon, false))
        assertNull("no skipping rooms", venture(v, "sandfall-cell"))
        assertNull(venture(v, "cradle-of-the-death-god"))
        v = venture(v, "oubliette")!!
        assertTrue(inDungeon(v.dungeon))
        assertEquals(2, v.completed)
        v = venture(v, "cradle-of-the-death-god")!!
        assertEquals(3, v.completed)
        assertTrue(dungeonDone(v.dungeon))
        assertFalse(inDungeon(v.dungeon))
        // A completed dungeon: the next venture starts a new one.
        assertEquals(listOf("lost-mine", "mad-mage", "tomb"), ventureOptions(v.dungeon, false))
        v = venture(v, "lost-mine")!!
        assertEquals(Venture(DungeonState("lost-mine", "cave-entrance"), 3), v)
    }

    @Test
    fun `venturing into Undercity while in another dungeon moves on in that one`() {
        val v = venture(Venture(null, 0), "mad-mage")!!
        assertEquals(listOf("dungeon-level"), ventureOptions(v.dungeon, true))
        assertEquals(DungeonState("mad-mage", "dungeon-level"), venture(v, "dungeon-level", true)!!.dungeon)
        assertNull(venture(v, "undercity", true))
    }

    @Test
    fun `a dungeon from the wire is kept only when it names a real room`() {
        assertEquals(DungeonState("undercity", "arena"), parseDungeonState("undercity", "arena"))
        assertEquals(DungeonState("tomb", "oubliette"), parseDungeonState("tomb", "oubliette"))
        assertNull(parseDungeonState("undercity", "oubliette"))
        assertNull(parseDungeonState("nowhere", "arena"))
        assertNull(parseDungeonState(null, "arena"))
    }

    @Test
    fun `the map spaces each row across the card`() {
        val layout = dungeonLayout(dungeonById("undercity")!!)
        fun at(id: String) = layout.first { it.id == id }
        assertEquals(0.5, at("secret-entrance").x, 1e-9)
        assertEquals(0.0, at("secret-entrance").row, 1e-9)
        assertEquals(listOf(0.25, 0.5, 0.75), listOf(at("trap").x, at("arena").x, at("stash").x))
        val tomb = dungeonLayout(dungeonById("tomb")!!).first { it.id == "oubliette" }
        assertEquals(listOf(0.7, 1.5), listOf(tomb.x, tomb.row))
    }
}
