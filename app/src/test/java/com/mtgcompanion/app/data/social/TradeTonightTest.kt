package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.CopyPlace
import com.mtgcompanion.app.data.PlacedCard
import com.mtgcompanion.app.data.isComing
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Trade matches tonight: grouping the trade matches by the friends at the table, where the cards are,
 * and the guests. The web app has the same checks — see MtgCompanionWeb/tests/social/tradeTonight.test.ts.
 */
class TradeTonightTest {

    private fun placed(name: String, placeId: String, page: Int? = null, slot: Int? = null, section: String? = null, forTrade: Int? = null) =
        PlacedCard("c1", CollectionEntry(name.lowercase(), name, null, quantity = 1, forTrade = forTrade), CopyPlace(placeId, 1, null, section, page, slot))

    private fun mc(name: String) = TradeCard(name.lowercase(), name, collectionId = "c1")

    private val places = mapOf("binder" to "Trade binder", "box" to "Red box")
    private val here = listOf(
        placed("Brainstorm", "box", section = "Blue"),
        placed("Brainstorm", "binder", page = 2, slot = 5),
        placed("Cyclonic Rift", "binder", page = 4, slot = 1, forTrade = 1),
        placed("Impulse", "box")
    )

    @Test
    fun whereACardIsTheCopyThatWouldGoFirst() {
        assertEquals("Trade binder · Page 2, slot 5", whereTonight("Brainstorm", here, places))
        assertEquals("Trade binder · Page 4, slot 1", whereTonight("cyclonic rift", here, places))
        assertEquals("Red box", whereTonight("Impulse", here, places))
        assertEquals("No place yet", whereTonight("Opt", here, places))
        assertEquals("Red box › Blue", whereTonight("Brainstorm", listOf(here[0]), places))
    }

    @Test
    fun friendsAtTheTableAreGroupedGuestsAreNamed() {
        val rhystic = mc("Rhystic Study")
        val rift = mc("Cyclonic Rift")
        val opt = mc("Opt")
        val matches = listOf(
            TradeMatch("priya", listOf(rhystic, mc("Sheoldred")), listOf(mc("Brainstorm"), mc("Sol Ring"), rift, mc("brainstorm")), marked = setOf(rhystic.key, rift.key)),
            TradeMatch("sam", listOf(mc("Mana Crypt")), listOf(mc("Sol Ring"))),
            TradeMatch("noor", listOf(opt), emptyList(), marked = setOf(opt.key))
        )
        val tonight = tradeMatchesTonight(
            listOf(
                TonightPlayer("Sam", "sam"),
                TonightPlayer("Priya", "priya"),
                TonightPlayer("Jo", null),
                TonightPlayer("Priya", "priya"),
                TonightPlayer("Ex", "ex"),
                TonightPlayer("Noor", "noor")
            ),
            setOf("priya", "sam", "noor"),
            matches,
            setOf("sol ring", "cyclonic rift"),
            here,
            places
        )
        assertEquals(listOf("Priya", "Noor"), tonight.matches.map { it.name })
        val priya = tonight.matches[0]
        // Sol Ring is played in a deck; Cyclonic Rift too, but it's marked for trade.
        assertEquals(
            listOf("Brainstorm" to "Trade binder · Page 2, slot 5", "Cyclonic Rift" to "Trade binder · Page 4, slot 1"),
            priya.theyWant.map { it.card.name to it.where }
        )
        // Only their cards marked for trade.
        assertEquals(listOf("Rhystic Study"), priya.theyHave.map { it.name })
        assertEquals("Priya wants 2 of your cards · has 1 card for trade that you want", tonightLine(priya))
        assertEquals("Noor has 1 card for trade that you want", tonightLine(tonight.matches[1]))
        // Sam wants only Sol Ring, which a deck plays, and has nothing for trade.
        assertEquals(listOf("Sam"), tonight.nothing)
        assertEquals(listOf("Jo", "Ex"), tonight.notFriends)
        assertEquals("Add Jo as a friend to see what they want", addFriendLine("Jo"))
    }

    @Test
    fun bringThemPutsOneOfEachOnTheGameNightDeck() {
        val lines = tonightAsDeckCards(listOf(TonightCard(mc("Brainstorm").copy(quantity = 3), "Red box")))
        assertEquals(listOf(Triple("Brainstorm", 1, "brainstorm")), lines.map { Triple(it.name, it.quantity, it.scryfallId) })
    }

    @Test
    fun theBagsNamesFindFriendsByNameOrFirstName() {
        val people = listOf("priya" to "Priya Shah", "sam" to "Sam")
        assertEquals(
            listOf(TonightPlayer("Priya", "priya"), TonightPlayer("sam", "sam"), TonightPlayer("Jo", null)),
            playersFromNames(listOf("Priya", "sam", "Jo"), people, ::isComing)
        )
    }
}
