package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Where the collection's value sits. The web app has the same checks — tests/collection/breakdown.test.ts. */
class CollectionBreakdownTest {

    private fun card(id: String, usd: Double?, copies: Int = 1, set: String = "Set $id", colors: String = "", rarity: String = "rare", type: String = "Creature") =
        BreakdownCard(id, id, null, copies, usd, id, set, colors.toSet(), rarity, type)

    @Test
    fun setsBeyondTheTopEightGoTogether() {
        val cards = (1..10).map { card("s$it", usd = it.toDouble()) }
        val b = collectionBreakdown(cards)
        assertEquals(9, b.bySet.size)
        assertEquals("Set s10", b.bySet.first().label)
        assertEquals(Slice("Other sets", 3.0, 2), b.bySet.last())
        assertEquals(55.0, b.totalUsd, 1e-9)
        // Nine sets fit without an "Other" of one.
        assertEquals(9, collectionBreakdown(cards.take(9)).bySet.size)
    }

    @Test
    fun coloursRaritiesAndTypesAddUpByValue() {
        val cards = listOf(
            card("a", 2.0, copies = 2, colors = "R", rarity = "common", type = "Instant"),
            card("b", 10.0, colors = "WU", rarity = "mythic", type = "Legendary Artifact Creature — Golem"),
            card("c", null, copies = 3, colors = "", rarity = "special", type = "Basic Land — Island"),
            card("d", 1.0, copies = 0, colors = "G")
        )
        val b = collectionBreakdown(cards)
        assertEquals(listOf(Slice("Red", 4.0, 2), Slice("Multicolor", 10.0, 1), Slice("Colorless", 0.0, 3)), b.byColor)
        assertEquals(listOf("Common", "Mythic", "Other"), b.byRarity.map { it.label })
        assertEquals(listOf("Creature", "Instant", "Land"), b.byType.map { it.label })
        assertEquals(listOf("b", "a"), b.mostValuable.map { it.id })
    }

    @Test
    fun theTenDearestCardsCountEveryCopy() {
        val cards = (1..12).map { card("c$it", usd = 1.0, copies = it) }
        val top = collectionBreakdown(cards).mostValuable
        assertEquals(10, top.size)
        assertEquals("c12", top.first().id)
        assertEquals(12.0, top.first().value, 1e-9)
    }

    @Test
    fun bucketsByTheUsualRules() {
        assertEquals("Colorless", colorBucket(emptySet()))
        assertEquals("Green", colorBucket(setOf('G')))
        assertEquals("Multicolor", colorBucket(setOf('B', 'G')))
        assertEquals("Mythic", rarityBucket("MYTHIC"))
        assertEquals("Creature", typeBucket("Artifact Creature — Construct"))
        assertEquals("Other", typeBucket("Kindred Tribal"))
    }
}
