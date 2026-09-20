package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What's kept of a card's combos: a week, then it's asked for again. The web app has the same
 * checks — see MtgCompanionWeb/tests/api/comboCache.test.ts.
 */
class ComboCacheTest {

    private val now = 1_700_000_000_000L

    @Test
    fun aCardIsTheSameCardHoweverItIsTyped() {
        assertEquals("sol ring", ComboCache.key("  Sol Ring "))
        assertEquals(ComboCache.fileName("Sol Ring"), ComboCache.fileName("  sol ring"))
        assertNotEquals(ComboCache.fileName("Sol Ring"), ComboCache.fileName("Sol Talisman"))
        // Any card name has to make a name a file system accepts.
        assertTrue(ComboCache.fileName("Yawgmoth, Thran Physician // Test?").matches(Regex("[0-9a-f]{16}\\.json")))
    }

    @Test
    fun staleCardsGoAndOnlyTheNewestKeepAreHeld() {
        val saved = mapOf(
            "fresh" to now,
            "yesterday" to now - 24 * 60 * 60 * 1000,
            "ancient" to now - ComboCache.TTL_MS - 1
        )
        assertEquals(setOf("fresh", "yesterday"), ComboCache.toKeep(saved, ComboCache.KEEP, now))

        val many = (0 until ComboCache.KEEP + 30).associate { i -> "card $i" to now + i }
        val keep = ComboCache.toKeep(many, ComboCache.KEEP, now + ComboCache.KEEP)
        assertEquals(ComboCache.KEEP, keep.size)
        // The ones asked for longest ago went; the newest stayed.
        assertTrue("card 0" !in keep)
        assertTrue("card ${ComboCache.KEEP + 29}" in keep)
    }

    @Test
    fun aDecklistIsKeptUnderThatExactList() {
        val key = ComboCache.deckKey(listOf("Omnath, Locus of Mana"), listOf("Sol Ring", "Cultivate"))
        // Order and case don't make it a different deck; a card added does.
        assertEquals(key, ComboCache.deckKey(listOf("omnath, locus of mana"), listOf("cultivate", " Sol Ring ", "Cultivate")))
        assertNotEquals(key, ComboCache.deckKey(listOf("Omnath, Locus of Mana"), listOf("Sol Ring", "Cultivate", "Rhystic Study")))

        // Decklists are kept a few at a time, the newest first.
        val many = (0 until ComboCache.KEEP_DECKS + 5).associate { i -> "deck $i" to now + i }
        val keep = ComboCache.toKeep(many, ComboCache.KEEP_DECKS, now + ComboCache.KEEP_DECKS)
        assertEquals(ComboCache.KEEP_DECKS, keep.size)
        assertTrue("deck 0" !in keep)
    }
}
