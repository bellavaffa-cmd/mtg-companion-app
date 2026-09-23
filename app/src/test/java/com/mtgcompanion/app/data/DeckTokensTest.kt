package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallPart
import org.junit.Assert.assertEquals
import org.junit.Test

/** What to bring besides the deck — mirrors the web app's tests/decks/tokens.test.ts. */
class DeckTokensTest {

    private fun entry(id: String, name: String) =
        DeckCardEntry(scryfallId = id, name = name, imageUrl = null, quantity = 1)

    private fun deck(cards: List<DeckCardEntry>, commander: DeckCardEntry? = null) =
        Deck(id = "d1", name = "Tokens", cards = cards, commander = commander)

    private fun card(id: String, name: String, parts: List<ScryfallPart>) =
        ScryfallCard(id = id, name = name, allParts = listOf(ScryfallPart(id, "combo_piece", name)) + parts)

    private val soldier = ScryfallPart("tok-soldier", "token", "Soldier", "Token Creature — Soldier")
    // The same token, printed in another set: a different id, but one piece of cardboard to bring.
    private val soldierAgain = soldier.copy(id = "tok-soldier-2")
    private val treasure = ScryfallPart("tok-treasure", "token", "Treasure", "Token Artifact — Treasure")
    private val emblem = ScryfallPart("tok-emblem", "token", "Elspeth", "Emblem — Elspeth")

    @Test
    fun `a deck asks for the tokens its cards make, and says which cards make them`() {
        val cards = mapOf(
            "a" to card("a", "Captain", listOf(soldier)),
            "b" to card("b", "Sergeant", listOf(soldierAgain)),
            "c" to card("c", "Pirate", listOf(treasure))
        )
        val needed = tokensNeeded(deck(listOf(entry("a", "Captain"), entry("b", "Sergeant"), entry("c", "Pirate"))), cards)
        assertEquals(listOf("Soldier", "Treasure"), needed.map { it.name })
        assertEquals(listOf("Captain", "Sergeant"), needed[0].madeBy)
        assertEquals(listOf("Pirate"), needed[1].madeBy)
    }

    @Test
    fun `the commander counts, and a card is only credited once`() {
        val cards = mapOf("a" to card("a", "Captain", listOf(soldier, soldierAgain)))
        val needed = tokensNeeded(deck(emptyList(), commander = entry("a", "Captain")), cards)
        assertEquals(1, needed.size)
        assertEquals(listOf("Captain"), needed[0].madeBy)
    }

    @Test
    fun `meld halves and combo pieces are cards you own, not things to bring`() {
        val cards = mapOf(
            "a" to ScryfallCard(id = "a", name = "Brisela Half", allParts = listOf(
                ScryfallPart("m1", "meld_part", "Other Half"),
                ScryfallPart("m2", "meld_result", "Brisela")
            ))
        )
        assertEquals(emptyList<TokenNeeded>(), tokensNeeded(deck(listOf(entry("a", "Brisela Half"))), cards))
    }

    @Test
    fun `emblems are listed after the tokens`() {
        val cards = mapOf(
            "a" to card("a", "Elspeth", listOf(emblem)),
            "b" to card("b", "Pirate", listOf(treasure))
        )
        val needed = tokensNeeded(deck(listOf(entry("a", "Elspeth"), entry("b", "Pirate"))), cards)
        assertEquals(listOf("Treasure", "Elspeth"), needed.map { it.name })
        assertEquals(true, needed[1].isEmblem)
    }

    @Test
    fun `nothing is claimed while the cards are still loading`() {
        assertEquals(emptyList<TokenNeeded>(), tokensNeeded(deck(listOf(entry("a", "Captain"))), emptyMap()))
    }

    @Test
    fun `what to say under a token`() {
        val one = TokenNeeded("x", "Soldier", null, listOf("Captain"), false)
        assertEquals("Captain", madeByLabel(one))
        assertEquals("Captain and Sergeant", madeByLabel(one.copy(madeBy = listOf("Captain", "Sergeant"))))
        assertEquals("Captain and 2 more", madeByLabel(one.copy(madeBy = listOf("Captain", "Sergeant", "Pirate"))))
    }
}
