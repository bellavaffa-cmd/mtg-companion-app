package com.mtgcompanion.app.ui

import com.mtgcompanion.app.ui.collection.CardFacts
import com.mtgcompanion.app.ui.collection.CollectionFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionFilterTest {
    private val elf = CardFacts(setOf('G'), "Creature — Elf Druid", "common")
    private val charm = CardFacts(setOf('W', 'U'), "Instant", "uncommon")
    private val ring = CardFacts(emptySet(), "Artifact", "rare")

    @Test
    fun `no filter passes everything, even a card with no data`() {
        assertTrue(CollectionFilter().matches(null))
        assertTrue(CollectionFilter().matches(elf))
    }

    @Test
    fun `an active filter leaves out a card with no data`() {
        assertFalse(CollectionFilter(colors = setOf('G')).matches(null))
    }

    @Test
    fun `a card must have every chosen color`() {
        assertTrue(CollectionFilter(colors = setOf('W')).matches(charm))
        assertTrue(CollectionFilter(colors = setOf('W', 'U')).matches(charm))
        assertFalse(CollectionFilter(colors = setOf('W', 'G')).matches(charm))
        assertFalse(CollectionFilter(colors = setOf('G')).matches(ring))
    }

    @Test
    fun `any chosen type or rarity is enough`() {
        assertTrue(CollectionFilter(types = setOf("Creature", "Land")).matches(elf))
        assertFalse(CollectionFilter(types = setOf("Land")).matches(elf))
        assertTrue(CollectionFilter(rarities = setOf("rare", "mythic")).matches(ring))
        assertFalse(CollectionFilter(rarities = setOf("mythic")).matches(ring))
    }

    @Test
    fun `filters combine`() {
        val filter = CollectionFilter(colors = setOf('G'), types = setOf("Creature"), rarities = setOf("common"))
        assertTrue(filter.matches(elf))
        assertFalse(filter.copy(rarities = setOf("rare")).matches(elf))
    }
}
