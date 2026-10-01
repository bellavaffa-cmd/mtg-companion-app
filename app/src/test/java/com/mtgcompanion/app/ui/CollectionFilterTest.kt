package com.mtgcompanion.app.ui

import com.mtgcompanion.app.ui.collection.CardFacts
import com.mtgcompanion.app.ui.collection.CollectionFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionFilterTest {
    private val elf = CardFacts(setOf('G'), "Legendary Creature — Elf Druid", "common", "{T}: Add {G}.")
    private val charm = CardFacts(setOf('W', 'U'), "Instant", "uncommon", "Choose one — Draw a card; or gain 3 life.")
    private val ring = CardFacts(emptySet(), "Artifact", "rare")

    @Test
    fun `no filter passes everything, even a card with no data`() {
        assertTrue(CollectionFilter().matches(null))
        assertTrue(CollectionFilter().matches(elf))
        assertFalse(CollectionFilter(type = "  ").active)
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
    fun `type needs every word, in any order and case`() {
        assertTrue(CollectionFilter(type = "creature legendary").matches(elf))
        assertTrue(CollectionFilter(type = "elf").matches(elf))
        assertFalse(CollectionFilter(type = "legendary artifact").matches(elf))
    }

    @Test
    fun `text is matched as a phrase`() {
        assertTrue(CollectionFilter(text = "draw a card ").matches(charm))
        assertFalse(CollectionFilter(text = "card draw").matches(charm))
        assertFalse(CollectionFilter(text = "draw").matches(ring))
    }

    @Test
    fun `any chosen rarity is enough`() {
        assertTrue(CollectionFilter(rarities = setOf("rare", "mythic")).matches(ring))
        assertFalse(CollectionFilter(rarities = setOf("mythic")).matches(ring))
    }

    @Test
    fun `filters combine`() {
        val filter = CollectionFilter(type = "creature", colors = setOf('G'), rarities = setOf("common"))
        assertTrue(filter.matches(elf))
        assertFalse(filter.copy(text = "flying").matches(elf))
    }
}
