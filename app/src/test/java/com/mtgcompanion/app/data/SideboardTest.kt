package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A deck's sideboard: which formats have one, moving cards, importing, and the rules on it. */
class SideboardTest {

    private fun card(id: String, quantity: Int = 1, name: String = id) =
        DeckCardEntry(scryfallId = id, name = name, imageUrl = null, quantity = quantity, typeLine = "Instant")

    private fun modern(cards: List<DeckCardEntry>, sideboard: List<DeckCardEntry> = emptyList()) =
        Deck("d", "Burn", cards = cards, sideboard = sideboard, gameMode = GameMode.MODERN.name)

    @Test
    fun `only formats without a commander have a sideboard`() {
        assertFalse(GameMode.COMMANDER.hasSideboard)
        assertFalse(GameMode.BRAWL.hasSideboard)
        assertTrue(GameMode.MODERN.hasSideboard)
        assertTrue(GameMode.STANDARD.hasSideboard)
    }

    @Test
    fun `moving a card to the sideboard and back keeps every copy`() {
        val deck = modern(listOf(card("bolt", 4), card("guide", 4)), listOf(card("bolt", 1)))
        val sided = deck.movedToSideboard("bolt")
        assertEquals(listOf("guide"), sided.cards.map { it.scryfallId })
        assertEquals(5, sided.sideboard.single().quantity)
        val back = sided.movedToMain("bolt")
        assertEquals(listOf("guide" to 4, "bolt" to 5), back.cards.map { it.scryfallId to it.quantity })
        assertTrue(back.sideboard.isEmpty())
    }

    @Test
    fun `a commander isn't sided out, and a cut flag doesn't follow a card there`() {
        val atraxa = card("atraxa")
        val deck = Deck("d", "A", commander = atraxa, cards = listOf(atraxa, card("sol").copy(replaceable = true)))
        assertEquals(deck, deck.movedToSideboard("atraxa"))
        assertFalse(deck.movedToSideboard("sol").sideboard.single().replaceable)
    }

    @Test
    fun `sideboard counts can be set, and zero takes the card off`() {
        val deck = modern(emptyList(), listOf(card("duress", 2)))
        assertEquals(3, deck.withSideboardQuantity("duress", 3).sideboard.single().quantity)
        assertTrue(deck.withSideboardQuantity("duress", 0).sideboard.isEmpty())
        assertEquals(4, deck.withSideboardCopies(card("duress", 2)).sideboardCount)
    }

    @Test
    fun `imported sideboard lines go to the sideboard where there is one, else to Considering`() {
        assertEquals(DeckPart.SIDEBOARD, importPart(ListSection.SIDEBOARD, GameMode.MODERN))
        assertEquals(DeckPart.CONSIDERING, importPart(ListSection.SIDEBOARD, GameMode.COMMANDER))
        assertEquals(DeckPart.CONSIDERING, importPart(ListSection.SIDEBOARD, GameMode.BRAWL))
        assertEquals(DeckPart.CONSIDERING, importPart(ListSection.MAYBEBOARD, GameMode.MODERN))
        assertEquals(DeckPart.MAIN, importPart(ListSection.MAIN, GameMode.MODERN))
    }

    @Test
    fun `old saved decks without a sideboard read as an empty one`() {
        val adapter = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build().adapter(Deck::class.java)
        val deck = adapter.fromJson("""{"id":"d","name":"Old","cards":[],"gameMode":"MODERN"}""")!!
        assertEquals(emptyList<DeckCardEntry>(), deck.sideboard)
        assertTrue(adapter.toJson(modern(emptyList(), listOf(card("x")))).contains("\"sideboard\":["))
    }

    private val legal = mapOf("modern" to "legal")
    private fun scry(id: String, name: String = id, legalities: Map<String, String> = legal, type: String = "Instant") =
        ScryfallCard(id = id, name = name, typeLine = type, legalities = legalities)

    @Test
    fun `copy limits count the main deck and the sideboard together`() {
        val deck = modern(listOf(card("bolt", 3, "Lightning Bolt")), listOf(card("bolt2", 2, "Lightning Bolt")))
        val issues = evaluateLegality(deck, mapOf("bolt" to scry("bolt", "Lightning Bolt"), "bolt2" to scry("bolt2", "Lightning Bolt")))
            .issues.filter { it.kind == LegalityIssueKind.COPY_LIMIT }
        val issue = issues.single()
        assertEquals("Max 4 copies allowed (has 3 + 2 in the sideboard).", issue.reason)
        assertEquals("bolt", issue.scryfallId)
        assertEquals(2, issue.fixQuantity)
    }

    @Test
    fun `when cutting the main deck can't fix it, no fix is offered`() {
        val deck = modern(listOf(card("bolt", 1, "Lightning Bolt")), listOf(card("bolt", 4, "Lightning Bolt")))
        val issue = evaluateLegality(deck, mapOf("bolt" to scry("bolt", "Lightning Bolt"))).issues.single { it.kind == LegalityIssueKind.COPY_LIMIT }
        assertNull(issue.fixQuantity)
    }

    @Test
    fun `the main deck alone reads as it always did, and basics are unlimited`() {
        val deck = modern(listOf(card("bolt", 5, "Lightning Bolt"), card("mtn", 20, "Mountain")), listOf(card("mtn2", 3, "Mountain")))
        val issue = evaluateLegality(deck, emptyMap()).issues.single { it.kind == LegalityIssueKind.COPY_LIMIT }
        assertEquals("Max 4 copies allowed (has 5).", issue.reason)
        assertEquals(4, issue.fixQuantity)
    }

    @Test
    fun `a sideboard holds at most fifteen, and Commander has none`() {
        val big = modern(emptyList(), (1..16).map { card("c$it") })
        assertTrue(evaluateLegality(big, emptyMap()).issues.any { it.reason.startsWith("Sideboard has 16 cards") })
        val fifteen = modern(emptyList(), (1..15).map { card("c$it") })
        assertFalse(evaluateLegality(fifteen, emptyMap()).issues.any { it.reason.startsWith("Sideboard") })
        val commander = Deck("d", "C", sideboard = listOf(card("x")))
        assertTrue(evaluateLegality(commander, emptyMap()).issues.any { it.reason.startsWith("Commander has no sideboard") })
    }

    @Test
    fun `a banned sideboard card is an issue too`() {
        val deck = modern(emptyList(), listOf(card("oko", name = "Oko")))
        val issue = evaluateLegality(deck, mapOf("oko" to scry("oko", "Oko", mapOf("modern" to "banned")))).issues.single { it.kind == LegalityIssueKind.LEGALITY }
        assertEquals("Banned in Modern (sideboard).", issue.reason)
    }

    @Test
    fun `adding a card counts the copies already in the sideboard`() {
        val deck = modern(listOf(card("bolt", 2)), listOf(card("bolt", 2)))
        val result = checkAdd(deck, listOf(AddCandidate("bolt", "bolt")), mapOf("bolt" to scry("bolt", "bolt"))).single()
        assertEquals(listOf("Over the copy limit (4 max)"), result.problems)
    }
}
