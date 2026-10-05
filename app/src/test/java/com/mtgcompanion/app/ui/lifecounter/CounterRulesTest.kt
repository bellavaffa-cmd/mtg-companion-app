package com.mtgcompanion.app.ui.lifecounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rad, speed and the Ring. The web app has the same checks — see MtgCompanionWeb/tests/lifecounter/counterRules.test.ts. */
class CounterRulesTest {

    @Test
    fun `speed and the Ring stop at 4, rad has no limit, nothing goes below 0`() {
        assertEquals(4, clampCounter("speed", 3 + 5))
        assertEquals(0, clampCounter("speed", -1))
        assertEquals(4, clampCounter("ring", 7))
        assertEquals(2, clampCounter("ring", 2))
        assertEquals(123, clampCounter("rad", 123))
        assertEquals(0, clampCounter("rad", -4))
        assertEquals(40, clampCounter("energy", 40))
    }

    @Test
    fun `max speed is 4`() {
        assertFalse(isMaxSpeed(3))
        assertTrue(isMaxSpeed(4))
    }

    @Test
    fun `each time the Ring tempts you its bearer gains the next ability`() {
        assertEquals(0, ringAbilities(0).size)
        assertEquals(listOf("Legendary, and can't be blocked by creatures with greater power."), ringAbilities(1))
        assertEquals(3, ringAbilities(3).size)
        assertEquals("Whenever it deals combat damage to a player, each opponent loses 3 life.", ringAbilities(4)[3])
        assertEquals(4, ringAbilities(9).size)
    }

    @Test
    fun `a Ring-bearer's name is trimmed, cut to 60 characters, and blank is nobody`() {
        assertEquals("Frodo Baggins", cleanRingBearer("  Frodo Baggins "))
        assertNull(cleanRingBearer("   "))
        assertNull(cleanRingBearer(null))
        assertEquals(60, cleanRingBearer("x".repeat(80))!!.length)
    }
}
