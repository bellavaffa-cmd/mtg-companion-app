package com.mtgcompanion.app.data.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trade fairness check: totals, the gap, and which cards would even it out. The web app has the
 * same checks — see MtgCompanionWeb/tests/social/tradeFairness.test.ts.
 */
class TradeFairnessTest {

    private fun card(name: String, quantity: Int = 1, foil: Boolean = false) = TradeCard(name.lowercase(), name, foil = foil, quantity = quantity)

    private val prices = mapOf(
        "sol ring" to CardPrice(2.0, 10.0),
        "mana crypt" to CardPrice(150.0, null),
        "brainstorm" to CardPrice(1.5, 4.0),
        "etched thing" to CardPrice(null, 7.0),
        "rhystic study" to CardPrice(40.0, null),
        "smothering tithe" to CardPrice(22.0, null),
        "cyclonic rift" to CardPrice(14.0, null),
        "fact or fiction" to CardPrice(9.0, null)
    )
    private val money = { usd: Double -> "$" + String.format(java.util.Locale.US, "%.2f", usd) }

    @Test
    fun aCopyIsPricedByItsFinishFallingBackToTheOther() {
        assertEquals(2.0, unitPrice(card("Sol Ring"), prices)!!, 0.0)
        assertEquals(10.0, unitPrice(card("Sol Ring", foil = true), prices)!!, 0.0)
        assertEquals(150.0, unitPrice(card("Mana Crypt", foil = true), prices)!!, 0.0)
        assertEquals(7.0, unitPrice(card("Etched Thing"), prices)!!, 0.0)
        assertNull(unitPrice(card("Unknown"), prices))
    }

    @Test
    fun aSideAddsUpItsCopiesAndCountsTheOnesWithNoPrice() {
        assertEquals(SideTotal(7.5, 2), sideTotal(listOf(card("Sol Ring", 3), card("Unknown", 2), card("Brainstorm")), prices))
        assertEquals(SideTotal(0.0, 0), sideTotal(emptyList(), prices))
    }

    @Test
    fun theGapTheVerdictAndTheBalance() {
        val f = fairness(listOf(card("Rhystic Study")), listOf(card("Smothering Tithe"), card("Cyclonic Rift"), card("Unknown")), prices)!!
        assertEquals(4.0, f.diff, 1e-9)
        assertTrue(f.fair) // within a tenth of $40
        assertEquals("Within $4.00 — a fair trade", verdictLine(f, money))
        assertEquals(1, f.unpriced)
        assertEquals("1 card has no price and is left out.", unpricedLine(f.unpriced))
        assertEquals("2 cards have no price and are left out.", unpricedLine(2))
        assertNull(unpricedLine(0))
        assertNull(shortSide(f))

        val uneven = fairness(listOf(card("Fact or Fiction")), listOf(card("Rhystic Study")), prices)!!
        assertEquals(-31.0, uneven.diff, 1e-9)
        assertEquals("You give $31.00 more", verdictLine(uneven, money))
        assertEquals(TradeSide.WANT, shortSide(uneven))
        assertEquals(9.0 / 49.0, uneven.getShare, 1e-9)

        val more = fairness(listOf(card("Mana Crypt")), listOf(card("Sol Ring")), prices)!!
        assertEquals("You get $148.00 more", verdictLine(more, money))
        assertEquals(TradeSide.GIVE, shortSide(more))

        val even = fairness(listOf(card("Sol Ring")), listOf(card("Sol Ring")), prices)!!
        assertEquals("Even — a fair trade", verdictLine(even, money))
        assertEquals(0.5, even.getShare, 0.0)
    }

    @Test
    fun nothingPricedGivesNoVerdict() {
        assertNull(fairness(listOf(card("Unknown")), listOf(card("Other")), prices))
    }

    @Test
    fun fairIsWithinTwoDollarsOrATenthOfTheBiggerSide() {
        assertTrue(isFair(2.0, 3.0, 1.0))
        assertFalse(isFair(2.5, 3.0, 0.5))
        assertTrue(isFair(9.0, 100.0, 91.0))
        assertFalse(isFair(-11.0, 89.0, 100.0))
    }

    @Test
    fun theCardsClosestToTheGapComeFirst() {
        val candidates = listOf(card("Rhystic Study"), card("Smothering Tithe"), card("Cyclonic Rift"), card("Unknown"), card("Sol Ring"), card("Fact or Fiction"), card("Cyclonic Rift"))
        val picked = evenOut(-12.0, candidates, listOf(card("Sol Ring")), prices)
        assertEquals(listOf("Cyclonic Rift", "Fact or Fiction", "Smothering Tithe"), picked.map { it.card.name })
        assertEquals(listOf(14.0, 9.0, 22.0), picked.map { it.price })
        assertEquals("Rhystic Study", evenOut(40.0, candidates, emptyList(), prices, max = 1)[0].card.name)
        // A tie goes to the cheaper card.
        assertEquals("Fact or Fiction", evenOut(11.5, listOf(card("Smothering Tithe"), card("Fact or Fiction"), card("Cyclonic Rift")), emptyList(), prices)[0].card.name)
        // One copy is suggested, whatever the line said.
        assertEquals(1, evenOut(5.0, listOf(card("Brainstorm", 4)), emptyList(), prices)[0].card.quantity)
    }

    @Test
    fun candidatesAreTheirCardsOrTheUsersWantedCardsForTradeOrSpare() {
        val rhystic = card("Rhystic Study")
        val rift = card("Cyclonic Rift")
        val match = TradeMatch(
            "priya",
            theyHave = listOf(rhystic, card("Smothering Tithe")),
            theyWant = listOf(card("Sol Ring"), rift, card("Brainstorm")),
            marked = setOf(rhystic.key, rift.key)
        )
        val decksUse = setOf("sol ring", "cyclonic rift")
        assertEquals(listOf("Rhystic Study", "Smothering Tithe"), candidatesFor(TradeSide.WANT, match, decksUse).map { it.name })
        // Sol Ring is in a deck and not for trade; Cyclonic Rift is in a deck but marked for trade.
        assertEquals(listOf("Cyclonic Rift", "Brainstorm"), candidatesFor(TradeSide.GIVE, match, decksUse).map { it.name })
        assertEquals(emptyList<TradeCard>(), candidatesFor(TradeSide.WANT, null, decksUse))
        assertEquals("To even it out, ask Priya for one of these", evenOutTitle(TradeSide.WANT, "Priya"))
        assertEquals("To even it out, offer one of these", evenOutTitle(TradeSide.GIVE, "Priya"))
    }

    @Test
    fun theServersForTradeMarkIsKept() {
        val m = parseTradeMatches(
            """[{"friend":"priya","they_have":[{"scryfallId":"a","name":"A","foil":false,"quantity":1,"collectionId":"x","forTrade":true},{"scryfallId":"b","name":"B","foil":false,"quantity":1,"collectionId":"x","forTrade":false}],"they_want":[{"scryfallId":"c","name":"C","foil":true,"quantity":1,"collectionId":"y","forTrade":true}]}]"""
        ).single()
        assertTrue(m.isMarked(m.theyHave[0]))
        assertFalse(m.isMarked(m.theyHave[1]))
        assertTrue(m.isMarked(m.theyWant[0]))
    }
}
