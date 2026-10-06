package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.CopyPlace
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckOwnership
import com.mtgcompanion.app.data.PlacedCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * "Friends want these" on a binder: grouping the trade matches by friend, the lines, and Bring to game
 * night. The web app has the same checks — see MtgCompanionWeb/tests/social/friendsWant.test.ts.
 */
class FriendsWantTest {

    private fun placed(name: String, page: Int? = null, slot: Int? = null, forTrade: Int? = null, collection: String = "c1") =
        PlacedCard(collection, CollectionEntry(name.lowercase(), name, null, quantity = 1, forTrade = forTrade), CopyPlace("b", 1, null, null, page, slot))

    private fun want(name: String) = TradeCard(name.lowercase(), name)

    private val prices = mapOf("Fact or Fiction" to 9.0, "Brainstorm" to 2.0, "Cyclonic Rift" to 10.0, "Impulse" to 3.0)
    private val price: (PlacedCard) -> Double? = { prices[it.entry.name] }

    private val here = listOf(
        placed("Brainstorm", 4, 1), placed("Fact or Fiction", 4, 6), placed("Impulse", 4, 7), placed("Cyclonic Rift", 7, 2), placed("Opt", 4, 3)
    )

    @Test
    fun friendsAreGroupedWithTheCardsTheyWantHere() {
        val matches = listOf(
            TradeMatch("sam", theyHave = emptyList(), theyWant = listOf(want("Impulse"), want("Lightning Bolt"))),
            TradeMatch("priya", theyHave = listOf(want("Sheoldred"), want("Smothering Tithe")),
                theyWant = listOf(want("Brainstorm"), want("Fact or Fiction"), want("cyclonic rift"), want("Brainstorm"))),
            TradeMatch("noor", theyHave = listOf(want("Sol Ring")), theyWant = listOf(want("Mana Crypt")))
        )
        val wants = friendsWantHere(matches, here, price)
        assertEquals(listOf("priya", "sam"), wants.map { it.friend })
        val priya = wants[0]
        assertEquals(listOf("Cyclonic Rift", "Fact or Fiction", "Brainstorm"), priya.cards.map { it.card.entry.name })
        assertEquals(21.0, priya.value, 0.001)
        assertEquals(listOf("Page 7, slot 2", "Page 4, slot 6", "Page 4, slot 1"), priya.cards.map(::wantedWhere))
        assertEquals("3 cards · $21", wantsLine(priya.cards.size, "$21"))
        assertEquals("1 card", wantsLine(1, null))
        assertEquals("Priya has 2 cards you want: Sheoldred, Smothering Tithe", hasLine("Priya", priya.theyHave))
        assertNull(hasLine("Sam", wants[1].theyHave))
        assertEquals(
            "Ana has 5 cards you want: A, B, C and 2 more",
            hasLine("Ana", listOf(want("A"), want("B"), want("C"), want("D"), want("E"), want("a")))
        )
    }

    @Test
    fun theCopyMarkedForTradeGoesFirst() {
        val copies = listOf(placed("Opt", 2, 1, collection = "c1"), placed("Opt", forTrade = 1, collection = "trade"), placed("Opt", 1, 4, collection = "c3"))
        val wants = friendsWantHere(listOf(TradeMatch("sam", emptyList(), listOf(want("Opt")))), copies) { null }
        assertEquals("trade", wants[0].cards[0].card.collectionId)
        assertEquals("Not in a pocket yet", wantedWhere(wants[0].cards[0]))
        assertEquals(0.0, wants[0].value, 0.0)
        val inPockets = friendsWantHere(listOf(TradeMatch("sam", emptyList(), listOf(want("Opt")))), copies.filter { it.collectionId != "trade" }) { null }
        assertEquals("c3", inPockets[0].cards[0].card.collectionId)
        val trade = wantedAsTrade(wants[0].cards)
        assertEquals(listOf(TradeCard("opt", "Opt", null, foil = false, quantity = 1, collectionId = "trade")), trade)
    }

    @Test
    fun bringingCardsToGameNightPutsThemOnItsPullList() {
        val cards = listOf(DeckCardEntry("fof", "Fact or Fiction", null), DeckCardEntry("bs", "Brainstorm", null))
        val other = Deck(id = "d1", name = "Atraxa")
        val (made, id) = bringToGameNight(listOf(other), cards, "new")
        assertEquals("new", id)
        val deck = made.first { it.id == "new" }
        assertEquals(GAME_NIGHT_DECK, deck.name)
        assertEquals(DeckOwnership.VIRTUAL.name, deck.ownership)
        assertEquals(listOf("Fact or Fiction", "Brainstorm"), deck.cards.map { it.name })
        // Again, with one already on it: added once, onto the same deck.
        val (again, sameId) = bringToGameNight(made, listOf(DeckCardEntry("bs2", "Brainstorm", null), DeckCardEntry("imp", "Impulse", null)), "other")
        assertEquals("new", sameId)
        assertEquals(2, again.size)
        assertEquals(listOf("Fact or Fiction", "Brainstorm", "Impulse"), again.first { it.id == "new" }.cards.map { it.name })
        // Once pulled into a deck box, new cards come in as proxies — still to pull.
        val pulled = again.map { if (it.id == "new") it.copy(ownership = DeckOwnership.PHYSICAL.name) else it }
        val (after, _) = bringToGameNight(pulled, listOf(DeckCardEntry("rift", "Cyclonic Rift", null)), "x")
        assertEquals(1, after.first { it.id == "new" }.cards.last().proxyQuantity)
    }
}
