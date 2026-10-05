package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Loans: lending cards with where each came from, getting them back, and merging two devices' loans —
 * the same on both apps. The web app has the same checks — see
 * MtgCompanionWeb/tests/collection/loans.test.ts.
 */
class LoansTest {

    private fun at(placeId: String, qty: Int, foil: Boolean = false, section: String? = null, page: Int? = null, slot: Int? = null) =
        CopyPlace(placeId, qty, if (foil) true else null, section, page, slot)

    private fun entry(id: String, name: String, quantity: Int, foilQuantity: Int = 0, places: List<CopyPlace>? = null, userTags: List<String> = emptyList()) =
        CollectionEntry(id, name, null, quantity = quantity, foilQuantity = foilQuantity, places = places, userTags = userTags)

    private val red = StoragePlace("red", "Red box", PlaceKind.BOX.name, sections = listOf("Red"), createdAt = 1)
    private val rares = StoragePlace("rares", "Rares binder", PlaceKind.BINDER.name, createdAt = 2)

    private fun pile(entries: List<CollectionEntry>, loans: List<Loan>? = null) =
        Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, entries, createdAt = 0, type = CollectionType.OWNED.name, storagePlaces = listOf(red, rares), loans = loans)

    private val atraxa = Deck("atraxa", "Atraxa", cards = listOf(DeckCardEntry("sol", "Sol Ring", null, quantity = 1)), ownership = DeckOwnership.PHYSICAL.name, createdAt = 1)
    private val decks = listOf(atraxa)
    private val cols = listOf(pile(listOf(
        entry("ring", "The One Ring", 1, 0, listOf(at("rares", 1, page = 1, slot = 1))),
        entry("bolt", "Lightning Bolt", 3, 0, listOf(at("red", 2, section = "Red")))
    )))
    private val lentAt = 1_790_000_000_000L
    private val sam = Loan("L1", "Sam", friendId = "f-sam", lentAt = lentAt, gameNight = true, note = "for Saturday")

    private fun source(sources: List<LendSource>, from: String) = sources.first { it.from == from }

    private fun lentToSam(): List<Collection> = lend(
        cols,
        listOf(
            LendPick(lendSources(cols, decks, placeId = "rares")[0], 1),
            LendPick(lendSources(cols, decks, name = "Sol Ring")[0], 1),
            LendPick(source(lendSources(cols, decks, name = "Lightning Bolt"), "Red box › Red"), 1)
        ),
        sam
    )

    private fun entryOf(c: List<Collection>, id: String) = c[0].entries.first { it.scryfallId == id }

    @Test
    fun whatCanBeLentACardFromItsPlacesDecksAndCopiesWithNoPlaceOrEverythingInAPlace() {
        assertEquals(listOf("Red box › Red ×2", "Unsorted ×1"), lendSources(cols, decks, name = "Lightning Bolt").map { "${it.from} ×${it.qty}" })
        assertEquals(listOf("Atraxa deck ×1"), lendSources(cols, decks, name = "Sol Ring").map { "${it.from} ×${it.qty}" })
        assertEquals(listOf("The One Ring · Rares binder ×1"), lendSources(cols, decks, placeId = "rares").map { "${it.name} · ${it.from} ×${it.qty}" })
    }

    @Test
    fun lendingTakesACopyOffItsPlaceNotesWhereEachCameFromAndCountsItAsLentOut() {
        val c = lentToSam()
        val loan = loansOf(c)[0]
        assertEquals(
            Loan(
                "L1", "Sam", friendId = "f-sam", lentAt = lentAt, gameNight = true, note = "for Saturday",
                cards = listOf(
                    LoanCard("The One Ring", "ring", 1, collectionId = UNSORTED_COLLECTION_ID, placeId = "rares", page = 1, slot = 1),
                    LoanCard("Sol Ring", "sol", 1, deckId = "atraxa"),
                    LoanCard("Lightning Bolt", "bolt", 1, collectionId = UNSORTED_COLLECTION_ID, placeId = "red", section = "Red")
                )
            ),
            loan
        )
        assertEquals(emptyList<CopyPlace>(), entryOf(c, "ring").places)
        assertEquals(listOf(at("red", 1, section = "Red")), entryOf(c, "bolt").places)
        val s = storageSummary(c, decks)
        assertEquals(listOf(5, 4, 1, 0, 3), listOf(s.total, s.placed, s.unplaced, s.inDecks, s.lent))
        assertEquals(3, copiesOut(loan))
        // The deck still lists Sol Ring, but it's lent out.
        assertEquals(listOf("Lent to Sam · From Atraxa deck ×1"), whereItIs(c, decks, "Sol Ring").first.map { "${it.title} · ${it.detail} ×${it.qty}" })
        assertEquals(listOf("Red box › Red ×1", "Lent to Sam ×1", "No place yet ×1"), whereItIs(c, decks, "Lightning Bolt").first.map { "${it.title} ×${it.qty}" })
        // A lent copy can't be lent again.
        assertEquals(listOf("Red box › Red ×1", "Unsorted ×1"), lendSources(c, decks, name = "Lightning Bolt").map { "${it.from} ×${it.qty}" })
        assertEquals(emptyList<LendSource>(), lendSources(c, decks, name = "Sol Ring"))
    }

    @Test
    fun gettingSomeBackThenTheRestPutsEachCardWhereItCameFrom() {
        val lent = lentToSam()
        val some = returnCards(lent, "L1", lentAt + 10, listOf(0, 0, 1))
        assertEquals(listOf(at("red", 2, section = "Red")), entryOf(some, "bolt").places)
        assertEquals(1, loansOf(some)[0].cards[2].back)
        assertNull(loansOf(some)[0].returnedAt)
        val all = returnCards(some, "L1", lentAt + 20)
        assertEquals(listOf(at("rares", 1, page = 1, slot = 1)), entryOf(all, "ring").places)
        assertEquals(lentAt + 20, loansOf(all)[0].returnedAt)
        assertEquals(listOf(1, 1, 1), loansOf(all)[0].cards.map { it.back })
        val s = storageSummary(all, decks)
        assertEquals(listOf(4, 1, 0), listOf(s.placed, s.inDecks, s.lent))
        // Nothing left out: nothing changes.
        assertSame(all, returnCards(all, "L1", lentAt + 30))
    }

    @Test
    fun aCardWhosePlaceHasGoneComesBackWithNoPlace() {
        val gone = deletePlace(lentToSam(), "rares")
        val back = returnCards(gone, "L1", lentAt + 10, listOf(1, 0, 0))
        assertEquals(emptyList<CopyPlace>(), entryOf(back, "ring").places)
        assertEquals(1, loansOf(back)[0].cards[0].back)
    }

    @Test
    fun aLoanCountsNoMoreCopiesThanAreStillThere() {
        val lent = lend(cols, listOf(LendPick(source(lendSources(cols, decks, name = "Lightning Bolt"), "Unsorted"), 1)), Loan("L2", "Priya", lentAt = lentAt))
        assertEquals(1, lentCopies(lent, decks).sumOf { it.qty })
        // The copy with no place was removed from the collection.
        val fewer = lent.map { c -> c.copy(entries = c.entries.map { if (it.scryfallId == "bolt") it.copy(quantity = 2) else it }) }
        assertEquals(0, lentCopies(fewer, decks).size)
    }

    @Test
    fun whenALoanIsDueBack() {
        fun loan(backBy: String? = null, gameNight: Boolean? = null, back: Int? = null) =
            Loan("x", "Sam", lentAt = lentAt, cards = listOf(LoanCard("Opt", "opt", 1, back = back)), backBy = backBy, gameNight = gameNight)
        assertEquals(3, daysBetween("2026-10-01", "2026-10-04"))
        assertEquals(LoanDue(3, "Overdue · 3 days"), loanDue(loan(backBy = "2026-10-01"), "2026-10-04", emptyList()))
        assertEquals(LoanDue(1, "Overdue · 1 day"), loanDue(loan(backBy = "2026-10-03"), "2026-10-04", emptyList()))
        assertEquals("Back by 12 Oct", loanDue(loan(backBy = "2026-10-12"), "2026-10-04", emptyList()).label)
        assertEquals("Back by 2 Jan 2027", loanDue(loan(backBy = "2027-01-02"), "2026-10-04", emptyList()).label)
        assertEquals("No date", loanDue(loan(), "2026-10-04", emptyList()).label)
        val nights = listOf(NightDay(lentAt - 1000, "2026-09-20"), NightDay(lentAt + 1000, "2026-10-03"))
        assertEquals("Back by next game night", loanDue(loan(gameNight = true), "2026-10-01", emptyList()).label)
        assertEquals("Due back today", loanDue(loan(gameNight = true), "2026-10-03", nights).label)
        assertEquals(LoanDue(2, "Overdue · 2 days"), loanDue(loan(gameNight = true), "2026-10-05", nights))
        assertEquals("All back", loanDue(loan(backBy = "2026-10-01", back = 1), "2026-10-04", emptyList()).label)
        assertEquals("3 Sep", shortDay("2026-09-03"))
    }

    @Test
    fun theLoansScreenOneGroupPerPersonTheMostOverdueFirst() {
        val card = LoanCard("Opt", "opt", 2)
        val loans = listOf(
            Loan("a", "Sam", friendId = "f-sam", lentAt = 1, cards = listOf(card), gameNight = true),
            Loan("b", "Sam", friendId = "f-sam", lentAt = 2, cards = listOf(card.copy(back = 1)), backBy = "2026-10-20"),
            Loan("c", "priya", lentAt = 3, cards = listOf(card), backBy = "2026-10-01"),
            Loan("d", "Priya ", lentAt = 4, cards = listOf(card)),
            Loan("e", "Alex", lentAt = 5, cards = listOf(card.copy(back = 2)))
        )
        val people = loanPeople(loans, "2026-10-04", emptyList())
        assertEquals(
            listOf("priya · cd · 4 · Overdue · 3 days", "Sam · ab · 3 · Back by next game night"),
            people.map { p -> "${p.name} · ${p.loans.joinToString("") { it.id }} · ${p.copies} · ${p.label}" }
        )
        assertEquals("Hi Sam — could I have my cards back when you get a chance? 2× Opt, Opt. Thanks!", reminderText("Sam", listOf(loans[0], loans[1])))
        assertEquals(
            listOf(ServerCard("Opt", 3, "opt")),
            serverCards(loans[0].copy(cards = listOf(card, card.copy(foil = true, back = 1), LoanCard("Shock", "shock", 1, back = 1))))
        )
    }

    @Test
    fun turningLentTagsIntoLoans() {
        assertEquals("Sam", tagBorrower("lent to sam"))
        assertEquals("Priya", tagBorrower("Lent: Priya"))
        assertEquals("Alex", tagBorrower("lent out to Alex"))
        assertEquals("Someone", tagBorrower("lent"))
        val c = listOf(pile(listOf(
            entry("bolt", "Lightning Bolt", 3, 0, listOf(at("red", 1, section = "Red")), listOf("lent to Sam", "red")),
            entry("shock", "Shock", 0, 1, null, listOf("lent to sam")),
            entry("opt", "Opt", 1, 0, null, listOf("lent"))
        )))
        assertEquals(4, storageSummary(c, emptyList()).lent)
        var n = 0
        val out = loansFromTags(c, lentAt) { "T${++n}" }
        assertEquals(
            listOf("T1 Sam: Lightning Bolt ×2, Shock foil ×1", "T2 Someone: Opt ×1"),
            out.loans.map { l -> "${l.id} ${l.to}: ${l.cards.joinToString(", ") { "${it.name}${if (it.isFoil) " foil" else ""} ×${it.qty}" }}" }
        )
        assertEquals("From your “lent” tags", out.loans[0].note)
        assertEquals(listOf("bolt" to listOf("red"), "shock" to emptyList(), "opt" to emptyList<String>()), out.retag)
        // The copies count once: as loans (the tags still on until they're taken off).
        assertEquals(4, storageSummary(out.collections, emptyList()).lent)
        // Run again with the tags still on: nothing new.
        assertEquals(0, loansFromTags(out.collections, lentAt) { "again" }.loans.size)
    }

    @Test
    fun twoDevicesLoansMergeLoanByLoan() {
        val card = LoanCard("Lightning Bolt", "bolt", 2, collectionId = UNSORTED_COLLECTION_ID)
        val base = listOf(Loan("L1", "Sam", lentAt = 10, cards = listOf(card)), Loan("L2", "Priya", lentAt = 20, cards = listOf(card)))
        // Mine: one back on L1, a note on it; L2 deleted.
        val mine = listOf(base[0].copy(note = "for Saturday", cards = listOf(card.copy(back = 1))))
        // Theirs: L1 renamed, the other back too, and a new loan.
        val theirs = listOf(
            base[0].copy(to = "Sam R.", cards = listOf(card.copy(back = 2)), returnedAt = 99),
            base[1],
            Loan("L3", "Alex", lentAt = 30, cards = listOf(card.copy(qty = 1)))
        )
        assertEquals(
            listOf(
                Loan("L1", "Sam R.", lentAt = 10, note = "for Saturday", cards = listOf(card.copy(back = 2)), returnedAt = 99),
                Loan("L3", "Alex", lentAt = 30, cards = listOf(card.copy(qty = 1)))
            ),
            mergeLoans(base, mine, theirs, true)
        )
        assertNull(mergeLoans(null, null, null, true))
        // A card added to a loan on one side is kept; one taken off on the other stays off.
        val plus = listOf(base[0].copy(cards = listOf(card, LoanCard("Opt", "opt", 1))))
        val minus = listOf(base[0].copy(cards = emptyList()))
        assertEquals(listOf(LoanCard("Opt", "opt", 1)), mergeLoans(listOf(base[0]), plus, minus, false)!![0].cards)
    }

    @Test
    fun aPileSavedByAnAppFromBeforeLoansKeepsThem() {
        val loans = listOf(Loan("L1", "Sam", lentAt = 10, cards = listOf(LoanCard("Opt", "opt", 1))))
        val mine = pile(emptyList(), loans)
        val older = pile(emptyList())
        assertEquals(loans, keepLoansFromOlderApp(mine, older).loans)
        val knows = pile(emptyList(), emptyList())
        assertSame(knows, keepLoansFromOlderApp(mine, knows))
        assertTrue(isOpen(loans[0]))
    }
}
