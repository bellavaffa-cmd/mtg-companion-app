package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packing for an event: the checklist from the decks, loans, wants and borrowed cards, the tokens and
 * counters a deck needs, ticking, and coming home — the same on both apps. The web app has the same
 * checks — see MtgCompanionWeb/tests/collection/eventBag.test.ts.
 */
class EventBagTest {

    private fun card(id: String, name: String, quantity: Int = 1) = DeckCardEntry(id, name, null, quantity = quantity)
    private fun deck(id: String, name: String, commander: DeckCardEntry?, cards: List<DeckCardEntry>) =
        Deck(id, name, commander = commander, cards = cards, createdAt = 1)

    private val goblin = ScryfallPart("tok-goblin", "token", "Goblin", "Token Creature — Goblin")
    private val krenko = deck("krenko", "Krenko goblins", card("krenko-c", "Krenko, Mob Boss"), listOf(card("mogg", "Mogg War Marshal"), card("mountain", "Mountain", 30)))
    private val atraxa = deck("atraxa", "Atraxa", card("atraxa-c", "Atraxa, Praetors' Voice"), listOf(card("sol", "Sol Ring"), card("hydra", "Hydra"), card("blight", "Blightsteel")))
    private val cards = mapOf(
        "krenko-c" to ScryfallCard(id = "krenko-c", name = "Krenko, Mob Boss", oracleText = "{T}: Create X 1/1 red Goblin creature tokens, where X is the number of Goblins you control.", allParts = listOf(goblin)),
        "mogg" to ScryfallCard(id = "mogg", name = "Mogg War Marshal", oracleText = "When this enters or dies, create a 1/1 red Goblin creature token.", allParts = listOf(goblin.copy(id = "tok-goblin-2"))),
        "mountain" to ScryfallCard(id = "mountain", name = "Mountain", typeLine = "Basic Land — Mountain"),
        "atraxa-c" to ScryfallCard(id = "atraxa-c", name = "Atraxa, Praetors' Voice", oracleText = "Flying, vigilance, deathtouch, lifelink\nAt the beginning of your end step, proliferate."),
        "sol" to ScryfallCard(id = "sol", name = "Sol Ring", oracleText = "{T}: Add {C}{C}."),
        "hydra" to ScryfallCard(id = "hydra", name = "Hydra", oracleText = "This creature enters with X +1/+1 counters on it."),
        "blight" to ScryfallCard(id = "blight", name = "Blightsteel", keywords = listOf("Infect"), oracleText = "Infect, indestructible")
    )

    @Test
    fun `how many tokens a deck needs - what each card making it makes at once, added up`() {
        assertEquals(2, makesHowMany("create two 1/1 tokens"))
        assertEquals(10, makesHowMany("Create X 1/1 tokens"))
        assertEquals(3, makesHowMany("creates 3 Food tokens"))
        assertEquals(1, makesHowMany("Whenever you attack, investigate."))
        assertEquals(listOf(TokenToBring("Goblin", 11, listOf("Krenko, Mob Boss", "Mogg War Marshal"))), tokensToBring(krenko, cards))
        assertEquals(emptyList<TokenToBring>(), tokensToBring(krenko, emptyMap()))
    }

    @Test
    fun `the counters a deck asks for, most cards first, and how they are said`() {
        assertEquals(listOf("+1/+1", "poison"), countersNeeded(atraxa, cards))
        assertEquals(emptyList<String>(), countersNeeded(krenko, cards))
        assertEquals("Poison and +1/+1 counters", countersLabel(listOf("poison", "+1/+1")))
        assertEquals("Charge counters", countersLabel(listOf("charge")))
        val counterspell = deck("x", "X", null, listOf(card("c", "Counterspell")))
        assertEquals(0, countersNeeded(counterspell, mapOf("c" to ScryfallCard(id = "c", name = "Counterspell", oracleText = "Counter target spell."))).size)
    }

    private val binder = StoragePlace("trade", "Trade binder", PlaceKind.BINDER.name, createdAt = 1)
    private val box = StoragePlace("box", "Red box", PlaceKind.BOX.name, createdAt = 2)
    private fun loan(id: String = "L1", to: String = "Sam", returnedAt: Long? = null, cards: List<LoanCard> = listOf(LoanCard("Sol Ring", "sol", 1, deckId = "atraxa"))) =
        Loan(id, to, cards = cards, lentAt = 1, returnedAt = returnedAt)
    private fun pile(loans: List<Loan>) =
        Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, emptyList(), createdAt = 0, type = CollectionType.OWNED.name, storagePlaces = listOf(binder, box), loans = loans)
    private fun placed(name: String, placeId: String, page: Int? = null) =
        PlacedCard(UNSORTED_COLLECTION_ID, CollectionEntry(name, name, null, quantity = 1), CopyPlace(placeId, 1, page = page, slot = page?.let { 1 }))
    private val gear = listOf(
        GearItem("b1", GearKind.DECK_BOX.name, "Red", 1, holds = "krenko", createdAt = 1),
        GearItem("d1", GearKind.DICE.name, "Dice", 6, createdAt = 2),
        GearItem("p1", GearKind.PLAYMAT.name, "Playmat", 1, createdAt = 3)
    )
    private val input = BagInput(
        decks = listOf(krenko, atraxa),
        collections = listOf(pile(listOf(loan(), loan("L2", "Ana", returnedAt = 5, cards = listOf(LoanCard("Hydra", "hydra", 1, deckId = "atraxa", back = 1)))))),
        gear = gear,
        tokens = mapOf("krenko" to listOf(TokenToBring("Goblin", 20, listOf("Krenko, Mob Boss")))),
        counters = mapOf("atraxa" to listOf("poison", "+1/+1")),
        wants = listOf(
            WantedBy("Priya Shah", listOf(placed("A", "trade", 4), placed("B", "trade", 7), placed("C", "trade", 4))),
            WantedBy("Jo", listOf(placed("D", "box")))
        ),
        borrowed = listOf(BorrowedFrom("Sam", 2), BorrowedFrom("Ana", 1)),
        attendees = listOf("Priya", "Sam")
    )

    @Test
    fun `the bag - decks with their deck box or what is lent, tokens and extras, and for trades`() {
        assertEquals(
            listOf(
                listOf("DECKS", "Krenko goblins", "Deck box, red", "false"),
                listOf("DECKS", "Atraxa", "Sol Ring lent to Sam", "true"),
                listOf("EXTRAS", "Goblin tokens ×20", "for Krenko", "false"),
                listOf("EXTRAS", "Poison and +1/+1 counters", "for Atraxa", "false"),
                listOf("EXTRAS", "Dice, playmat", "Gear", "false"),
                listOf("TRADES", "3 cards Priya Shah wants", "Trade binder p4, p7", "false"),
                listOf("TRADES", "Sam's borrowed cards", "to give back", "false")
            ),
            bagLines(input).map { listOf(it.section.name, it.title, it.detail, it.warn.toString()) }
        )
    }

    @Test
    fun `who is coming - the same name, or a first name`() {
        assertTrue(isComing("Priya Shah", listOf("priya")))
        assertFalse(isComing("Priyanka", listOf("Priya")))
        assertFalse(isComing("", listOf("Priya")))
        assertEquals(
            "Sol Ring and 2 more lent to Sam",
            lentFromDeckLine(atraxa, listOf(loan(cards = listOf(
                LoanCard("Sol Ring", "sol", 1, deckId = "atraxa"), LoanCard("Hydra", "hydra", 1, deckId = "atraxa"), LoanCard("Blightsteel", "blight", 1, deckId = "atraxa")
            ))))
        )
        assertNull(lentFromDeckLine(krenko, listOf(loan())))
    }

    @Test
    fun `ticking, All packed, and coming home re-checks what went out`() {
        val lines = bagLines(input)
        var bag = newBag("b", "Game night at Priya's", "2026-10-10", listOf("Priya", "Sam"), listOf("krenko", "atraxa"), 1)
        bag = toggleTick(bag, "deck:krenko")
        assertFalse(allTicked(bag, lines))
        bag = tickAll(bag, lines)
        assertTrue(allTicked(bag, lines))
        bag = toggleTick(bag, "counters:atraxa")
        bag = setComingHome(bag, true)
        assertEquals(listOf("deck:krenko", "deck:atraxa", "token:goblin", "gear:extras"), shownLines(bag, lines).map { it.key })
        bag = toggleTick(bag, "deck:krenko")
        bag = toggleTick(bag, "gear:extras")
        assertEquals(listOf("deck:atraxa", "token:goblin"), stillAway(bag, lines).map { it.key })
        assertEquals("Still to come back: Atraxa and Goblin tokens ×20.", homeSummary(bag, lines))
        bag = tickAll(bag, lines)
        assertEquals("Everything came back.", homeSummary(bag, lines))
        assertTrue("deck:krenko" in bag.packed)
    }

    @Test
    fun `the day - today, tomorrow, a weekday within the week, else the date`() {
        assertEquals("Today", dayLabel("2026-10-06", "2026-10-06"))
        assertEquals("Tomorrow", dayLabel("2026-10-07", "2026-10-06"))
        assertEquals("Saturday", dayLabel("2026-10-10", "2026-10-06"))
        assertEquals("20 Oct", dayLabel("2026-10-20", "2026-10-06"))
        assertEquals("Saturday · Game night at Priya's", bagHeading(newBag("b", "Game night at Priya's", "2026-10-10", emptyList(), emptyList(), 1), "2026-10-06"))
    }
}
