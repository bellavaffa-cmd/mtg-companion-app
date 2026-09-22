package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.CollectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What an accepted trade does to each side's binders, and reading the app's QR links. The web app
 * has the same checks — see MtgCompanionWeb/tests/social/social.test.ts.
 */
class TradeLogicTest {

    @Test
    fun aFriendsWishlistFindsTheUsersCardsOfferedFromTheirBestBinder() {
        val mine = listOf(
            Collection("m1", "Main", listOf(CollectionEntry("sol-f", "Sol Ring", null, quantity = 0, foilQuantity = 1), CollectionEntry("bolt", "Lightning Bolt", null, quantity = 1))),
            Collection("m2", "Trades", listOf(CollectionEntry("sol", "Sol Ring", null, quantity = 2))),
            // The user's own wishlist isn't something they have.
            Collection("mw", "Wants", listOf(CollectionEntry("opal", "Mox Opal", null, quantity = 1)), type = CollectionType.WISHLIST.name)
        )
        val theirs = listOf(
            Collection("t1", "Upgrades", listOf(CollectionEntry("x", "sol ring", null, quantity = 1), CollectionEntry("y", "Mox Opal", null, quantity = 1)), type = CollectionType.WISHLIST.name),
            // Their binder isn't what they want.
            Collection("t2", "Binder", listOf(CollectionEntry("bolt2", "Lightning Bolt", null, quantity = 4)))
        )
        val wanted = cardsTheyWant(mine, theirs)
        assertEquals(listOf("Sol Ring"), wanted.map { it.name })
        val sol = wanted.single()
        assertEquals(3, sol.copies)
        assertEquals("Upgrades", sol.wishlist)
        // Offered from the binder with regular copies, not the foil.
        assertEquals(TradeCard("sol", "Sol Ring", null, foil = false, quantity = 1, collectionId = "m2"), sol.card)
        assertTrue(cardsTheyWant(mine, theirs.drop(1)).isEmpty())
    }

    @Test
    fun wishlistHitsBecomeOneCopyOfEachCard() {
        val hit = { item: String, q: Int, f: Int -> SharedCardHit("u", ShareKind.COLLECTION, item, "Binder", "id-$item", "Rhystic Study", null, q, f) }
        val trade = hitsAsTrade(listOf(hit("b1", 0, 2), hit("b2", 3, 0)))
        assertEquals(listOf(TradeCard("id-b1", "Rhystic Study", null, foil = true, quantity = 1, collectionId = "b1")), trade)
    }

    private val a = "user-a"
    private val b = "user-b"

    // A asks for B's foil Sol Ring from B's binder b-trade; A offers two Bolts from a-main.
    private fun trade(status: TradeStatus = TradeStatus.ACCEPTED, fromApplied: Boolean = false, toApplied: Boolean = false) = Trade(
        id = "t1", fromUser = a, toUser = b,
        want = listOf(TradeCard("sol", "Sol Ring", foil = true, quantity = 1, collectionId = "b-trade")),
        give = listOf(TradeCard("bolt", "Lightning Bolt", foil = false, quantity = 2, collectionId = "a-main")),
        message = null, reply = null, status = status, fromApplied = fromApplied, toApplied = toApplied, updatedAt = ""
    )

    private fun binder(id: String, vararg entries: CollectionEntry) = Collection(id = id, name = id, entries = entries.toList())

    @Test
    fun `each side sees what they give and what they get`() {
        assertEquals(listOf("Lightning Bolt"), tradeSides(trade(), a).give.map { it.name })
        assertEquals(listOf("Sol Ring"), tradeSides(trade(), a).get.map { it.name })
        assertEquals(b, tradeSides(trade(), a).other)
        assertEquals(listOf("Sol Ring"), tradeSides(trade(), b).give.map { it.name })
        assertEquals(listOf("Lightning Bolt"), tradeSides(trade(), b).get.map { it.name })
        assertEquals(a, tradeSides(trade(), b).other)
    }

    @Test
    fun `only an accepted trade waits for each side to update their binders`() {
        assertTrue(awaitingMyUpdate(trade(), a))
        assertFalse(awaitingMyUpdate(trade(fromApplied = true), a))
        assertTrue(awaitingMyUpdate(trade(fromApplied = true), b))
        assertFalse(awaitingMyUpdate(trade(status = TradeStatus.OPEN), b))
        assertTrue(waitingOnMe(trade(status = TradeStatus.OPEN), b))
        assertFalse(waitingOnMe(trade(status = TradeStatus.OPEN), a))
    }

    @Test
    fun `the giver's copies come out and the received ones go in`() {
        val mine = listOf(
            binder("a-main", CollectionEntry("bolt", "Lightning Bolt", null, quantity = 3, foilQuantity = 1)),
            binder("a-trades")
        )
        val result = applyCollectionChanges(mine, tradeChanges(trade(), a, receiveInto = "a-trades", fallbackFrom = null))
        assertTrue(result.short.isEmpty())
        assertEquals(listOf(CollectionEntry("bolt", "Lightning Bolt", null, quantity = 1, foilQuantity = 1)), result.collections[0].entries)
        assertEquals(listOf(CollectionEntry("sol", "Sol Ring", null, quantity = 0, foilQuantity = 1)), result.collections[1].entries)
    }

    @Test
    fun `a card with no copies left leaves the binder, and missing copies are reported`() {
        val done = applyCollectionChanges(
            listOf(binder("b-trade", CollectionEntry("sol", "Sol Ring", null, quantity = 0, foilQuantity = 1))),
            tradeChanges(trade(), b, receiveInto = "b-trade", fallbackFrom = null)
        )
        assertEquals(listOf("Lightning Bolt" to 2), done.collections[0].entries.map { it.name to it.quantity })
        assertTrue(done.short.isEmpty())

        val gone = applyCollectionChanges(listOf(binder("b-trade")), tradeChanges(trade(), b, "b-trade", null))
        assertEquals(listOf("Sol Ring"), gone.short.map { it.card.name })
        assertTrue(gone.collections[0].entries.all { it.quantity >= 0 && it.foilQuantity >= 0 })

        val deleted = applyCollectionChanges(listOf(binder("elsewhere")), tradeChanges(trade(), b, "elsewhere", null))
        assertEquals(listOf("Sol Ring"), deleted.short.map { it.card.name })
    }

    @Test
    fun `picking sets how many of a card, finish and binder`() {
        val sol = TradeCard("sol", "Sol Ring", collectionId = "x")
        val foil = sol.copy(foil = true)
        var list = emptyList<TradeCard>().withQuantity(sol, 2).withQuantity(foil, 1)
        assertEquals(3, list.cardTotal())
        list = list.withQuantity(sol, 0)
        assertEquals(listOf(foil.copy(quantity = 1)), list)
    }

    @Test
    fun `the app's QR links are read, anything else isn't`() {
        val base = SocialApi.PUBLIC_APP_URL
        assertEquals(AppLink.AddFriend("alice_1"), AppLink.parse("${base}add/Alice_1"))
        assertEquals(AppLink.JoinSeat("0123456789abcdef", 3), AppLink.parse("${base}join/0123456789abcdef/3"))
        assertEquals(AppLink.SharedLink("0123456789abcdef0123456789abcdef"), AppLink.parse("${base}s/0123456789abcdef0123456789abcdef"))
        assertEquals(AppLink.AddFriend("bob"), AppLink.parse("http://localhost:5174/add/bob/"))
        // Codes made before manabind.com: on the old address, or anywhere serving the app under its old path.
        assertEquals(AppLink.AddFriend("bob"), AppLink.parse("https://bellavaffa-cmd.github.io/mtg-companion-web/add/bob"))
        assertEquals(AppLink.AddFriend("bob"), AppLink.parse("http://localhost:5174/mtg-companion-web/add/bob/"))
        assertEquals(AppLink.AddFriend("bob"), AppLink.parse("https://www.manabind.com/add/bob"))
        // A browser waiting to be signed in (see qr_login).
        assertEquals(AppLink.WebSignIn("0123456789abcdef0123456789abcdef"), AppLink.parse("${base}login/0123456789abcdef0123456789abcdef"))
        assertNull(AppLink.parse("${base}login/short"))
        assertNull(AppLink.parse("https://example.com/add/bob"))
        assertNull(AppLink.parse("${base}join/not-a-code/3"))
        assertNull(AppLink.parse("${base}add/a"))
        assertNull(AppLink.parse("hello"))
    }
}
