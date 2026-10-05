package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Blocking, messages, reputation, activity and cards for trade (SocialMoreLogic.kt). The web app runs
 * the same cases in MtgCompanionWeb/tests/social/more.test.ts.
 */
class SocialMoreLogicTest {

    private fun entry(id: String, name: String, q: Int, f: Int, forTrade: Int? = null) =
        CollectionEntry(id, name, null, quantity = q, foilQuantity = f, forTrade = forTrade)

    private fun binder(id: String, entries: List<CollectionEntry>, type: String = "OWNED") = Collection(id, id, entries, createdAt = 0, type = type)

    private fun msg(id: Long, sender: String = "a", body: String = "hi") = DirectMessage(id, "c", sender, "b", body, id * 1000)

    private fun utc(y: Int, mo: Int, d: Int, h: Int = 0) = LocalDateTime.of(y, mo, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `card names in double brackets become links`() {
        assertEquals(
            listOf(MessagePart.Text("Got a "), MessagePart.Card("Sol Ring"), MessagePart.Text(" for you")),
            messageParts("Got a [[Sol Ring]] for you")
        )
        assertEquals(listOf(MessagePart.Card("Sol Ring"), MessagePart.Card("Mana Crypt")), messageParts("[[Sol Ring]][[Mana Crypt]]"))
        assertEquals(listOf(MessagePart.Card("Fire // Ice"), MessagePart.Text("?")), messageParts("[[ Fire // Ice ]]?"))
        assertEquals(listOf(MessagePart.Text("[[ ]] and "), MessagePart.Card("a")), messageParts("[[ ]] and [[a]]"))
        assertEquals(listOf(MessagePart.Text("[[unclosed")), messageParts("[[unclosed"))
        assertEquals(emptyList<MessagePart>(), messageParts(""))
    }

    @Test
    fun `messages merge once each, oldest first`() {
        val merged = mergeMessages(listOf(msg(3), msg(1)), listOf(msg(2), msg(3, "b", "edited")))
        assertEquals(listOf(1L, 2L, 3L), merged.map { it.id })
        assertEquals("edited", merged[2].body)
    }

    @Test
    fun `a conversation's preview line`() {
        assertEquals("No messages yet", previewLine(null, null, "a"))
        assertEquals("You: hi", previewLine("a", "hi", "a"))
        assertEquals("see you Friday", previewLine("b", "  see\n you  Friday ", "a"))
        assertEquals("x".repeat(79) + "…", previewLine("b", "x".repeat(100), "a"))
        assertEquals("dm:u1", dmTopic("u1"))
    }

    @Test
    fun `trade record lines`() {
        assertEquals("March 2026", monthYear(utc(2026, 3, 15)))
        assertEquals("No trades yet", tradesLine(0, null))
        assertEquals("Trades completed: 12 · since March 2026", tradesLine(12, utc(2026, 3, 15)))
        assertEquals("Trades completed: 3", tradesLine(3, null))
        assertEquals("None with you yet", withYouLine(0))
        assertEquals("1 with you", withYouLine(1))
        assertEquals("5 positive", positiveLine(5))
        assertEquals("0 positive", positiveLine(-1))
    }

    @Test
    fun `a trade can be rated once accepted and your side updated`() {
        val t = Trade("t", "a", "b", emptyList(), emptyList(), null, null, TradeStatus.ACCEPTED, fromApplied = true, toApplied = false, updatedAt = "")
        assertTrue(canRate(t, "a"))
        assertFalse(canRate(t, "b"))
        assertFalse(canRate(t, "c"))
        assertFalse(canRate(t.copy(status = TradeStatus.OPEN), "a"))
    }

    @Test
    fun `two-way match sentence`() {
        assertEquals("Priya has 2 cards you want, and wants 3 of yours", matchSentence("Priya", 2, 3))
        assertEquals("Priya has 1 card you want", matchSentence("Priya", 1, 0))
        assertEquals("Priya wants 1 of yours", matchSentence("Priya", 0, 1))
        assertNull(matchSentence("Priya", 0, 0))
    }

    @Test
    fun `copies for trade never exceed the copies there are`() {
        assertEquals(3, forTradeOf(entry("s", "Sol Ring", 2, 1, 5)))
        assertEquals(0, forTradeOf(entry("s", "Sol Ring", 2, 1)))
        assertEquals(0, forTradeOf(entry("s", "Sol Ring", 2, 1, -1)))
        assertEquals(2, forTradeOf(entry("s", "Sol Ring", 4, 0, 2)))
    }

    @Test
    fun `marking copies for trade`() {
        val cols = listOf(
            binder("b1", listOf(entry("s", "Sol Ring", 2, 0), entry("m", "Mana Crypt", 1, 0, 1))),
            binder("w", listOf(entry("s", "Sol Ring", 1, 0)), "WISHLIST")
        )
        val marked = setForTrade(cols, "b1", "s", 5)
        assertEquals(2, marked[0].entries[0].forTrade)
        val cleared = setForTrade(marked, "b1", "m", 0)
        assertNull(cleared[0].entries[1].forTrade)
        assertSame(cols[1], setForTrade(cols, "w", "s", 1)[1])
        assertEquals(listOf("Mana Crypt" to 1, "Sol Ring" to 2), forTradeLines(marked).map { it.entry.name to it.count })
        assertEquals(listOf("Sol Ring"), forTradeLines(cleared).map { it.entry.name })
    }

    @Test
    fun `for-trade copies as picks - plain first, then foil`() {
        val picks = forTradePicks(binder("b1", listOf(entry("s", "Sol Ring", 1, 2, 2), entry("x", "Unmarked", 3, 0))))
        assertEquals(
            listOf(listOf("Sol Ring", false, 1, "b1"), listOf("Sol Ring", true, 1, "b1")),
            picks.map { listOf(it.name, it.foil, it.quantity, it.collectionId) }
        )
    }

    @Test
    fun `names lines`() {
        assertEquals("A, B and 1 more", namesLine(listOf("A", "B", "C"), 3))
        assertEquals("A, B and 3 more", namesLine(listOf("A", "B"), 5))
        assertEquals("A", namesLine(listOf("A"), 1))
        assertEquals("A and B", namesLine(listOf("A", "B"), 2))
        assertEquals("", namesLine(emptyList(), 0))
    }

    @Test
    fun `what activity items say`() {
        val actor = Profile("p", "priya", "Priya", null)
        fun item(kind: String = "shared", itemKind: String? = null, itemId: String? = null, name: String? = null, podName: String? = null,
                 winner: String? = null, players: Int? = null, count: Int? = null, cards: List<String> = emptyList()) =
            ActivityItem(kind, actor, 0, itemKind, itemId, name, podName = podName, winner = winner, players = players, count = count, cards = cards.map { it to null })
        assertEquals(ActivityText("shared a deck", "Atraxa"), activityText(item(itemKind = "deck", itemId = "d", name = "Atraxa")))
        assertEquals(ActivityText("shared a binder", "Trade binder"), activityText(item(itemKind = "collection", itemId = "b", name = "Trade binder")))
        assertEquals(ActivityText("shared all their decks", null), activityText(item(itemKind = "deck")))
        assertEquals(ActivityText("shared their collection", null), activityText(item(itemKind = "collection")))
        assertEquals(ActivityText("updated a deck", "Atraxa"), activityText(item("deck_updated", "deck", "d", "Atraxa")))
        assertEquals(ActivityText("recorded a game in Friday", "Sam won · 4 players"), activityText(item("pod_game", podName = "Friday", winner = "Sam", players = 4)))
        assertEquals(ActivityText("recorded a game", "No winner"), activityText(item("pod_game")))
        assertEquals(
            ActivityText("marked 3 cards for trade", "Sol Ring, Arcane Signet and 1 more"),
            activityText(item("for_trade", count = 3, cards = listOf("Sol Ring", "Arcane Signet", "Mana Crypt")))
        )
        assertEquals(ActivityText("marked 1 card for trade", "Sol Ring"), activityText(item("for_trade", count = 1, cards = listOf("Sol Ring"))))
        assertEquals(ActivityText("did something new", null), activityText(item("something_new")))
    }

    @Test
    fun `how long ago`() {
        val now = utc(2026, 10, 5, 12)
        assertEquals("just now", timeAgo(now - 30_000, now))
        assertEquals("5 min ago", timeAgo(now - 5 * 60_000, now))
        assertEquals("3 h ago", timeAgo(now - 3 * 3_600_000, now))
        assertEquals("yesterday", timeAgo(now - 24 * 3_600_000, now))
        assertEquals("4 days ago", timeAgo(now - 4 * 86_400_000L, now))
        assertEquals("25 Sep", timeAgo(now - 10 * 86_400_000L, now))
        assertEquals("just now", timeAgo(now + 60_000, now))
    }

    @Test
    fun `a missing server function is told apart from other failures`() {
        assertTrue(isMissingFunction(404, "PGRST202"))
        assertTrue(isMissingFunction(404, null))
        assertFalse(isMissingFunction(400, "P0001"))
        assertFalse(isMissingFunction(500, null))
    }
}
