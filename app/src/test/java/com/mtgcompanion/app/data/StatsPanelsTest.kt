package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsPanelsTest {

    @Test
    fun defaultsApplyUntilAPanelIsToggled() {
        assertTrue(StatsPanels.isOpen("summary", emptyMap()))
        assertTrue(StatsPanels.isOpen("curve", emptyMap()))
        assertTrue(StatsPanels.isOpen("roles", emptyMap()))
        assertFalse(StatsPanels.isOpen("types", emptyMap()))
        assertFalse(StatsPanels.isOpen("curve", mapOf("curve" to false)))
        assertTrue(StatsPanels.isOpen("types", mapOf("types" to true)))
    }

    @Test
    fun encodesAndDecodesBothWays() {
        val stored = mapOf("curve" to false, "mana-base" to true)
        assertEquals(stored, StatsPanels.decode(StatsPanels.encode(stored)))
    }

    @Test
    fun ignoresMalformedEntries() {
        assertEquals(mapOf("roles" to false), StatsPanels.decode(setOf("roles=0", "=1", "junk")))
        assertEquals(emptyMap<String, Boolean>(), StatsPanels.decode(null))
    }
}
