package com.mtgcompanion.app.ui

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.ui.common.AddVerb
import com.mtgcompanion.app.ui.common.MAX_ADD_QUANTITY
import com.mtgcompanion.app.ui.common.MoveTarget
import com.mtgcompanion.app.ui.common.QuantityLimits
import com.mtgcompanion.app.ui.common.SourceKind
import com.mtgcompanion.app.ui.common.UndoStep
import com.mtgcompanion.app.ui.common.addToMessage
import com.mtgcompanion.app.ui.common.addToTitle
import com.mtgcompanion.app.ui.common.asTarget
import com.mtgcompanion.app.ui.common.cardsSubject
import com.mtgcompanion.app.ui.common.copiesTaken
import com.mtgcompanion.app.ui.common.kindsToChoose
import com.mtgcompanion.app.ui.common.quantityLimits
import com.mtgcompanion.app.ui.common.undoSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words, copy counts and Undo of the one "Add to…" picker and its confirmation. */
class AddToTest {

    // ---- Wording ----

    @Test
    fun titlesSayWhatIsBeingDone() {
        assertEquals("Add Sol Ring to…", addToTitle(AddVerb.ADD, "Sol Ring"))
        assertEquals("Move 3 cards to…", addToTitle(AddVerb.MOVE, cardsSubject(3, null)))
        assertEquals("Copy Sol Ring to…", addToTitle(AddVerb.COPY, cardsSubject(1, "Sol Ring")))
    }

    @Test
    fun oneCardIsCalledByItsNameAndMoreAreCounted() {
        assertEquals("Sol Ring", cardsSubject(1, "Sol Ring"))
        assertEquals("1 card", cardsSubject(1, null))
        assertEquals("4 cards", cardsSubject(4, "Sol Ring"))
    }

    @Test
    fun confirmationsSayWhereTheCardsWent() {
        assertEquals("Added Sol Ring to Atraxa", addToMessage(AddVerb.ADD, "Sol Ring", "Atraxa"))
        assertEquals("Moved 3 cards to Trades", addToMessage(AddVerb.MOVE, "3 cards", "Trades"))
        assertEquals("Copied Sol Ring to Spares", addToMessage(AddVerb.COPY, "Sol Ring", "Spares"))
        assertEquals("Added Sol Ring to Considering in Atraxa", addToMessage(AddVerb.ADD, "Sol Ring", "Atraxa", considering = true))
        assertEquals("Added 4 × Forest to Lands", addToMessage(AddVerb.ADD, "Forest", "Lands", quantity = 4))
    }

    // ---- First step: a binder or a deck? ----

    @Test
    fun beingAbleToMakeADeckCountsAsHavingDecks() {
        val binder = MoveTarget(SourceKind.BINDER, "b", "Trades")
        assertNull(kindsToChoose(listOf(binder), canMakeBinder = true))
        assertEquals(listOf(SourceKind.BINDER, SourceKind.DECK), kindsToChoose(listOf(binder), canMakeBinder = true, canMakeDeck = true))
        assertEquals(listOf(SourceKind.BINDER, SourceKind.DECK), kindsToChoose(emptyList(), canMakeBinder = true, canMakeDeck = true))
    }

    @Test
    fun aDeckShowsItsCommanderAndCardCount() {
        val commander = DeckCardEntry("a", "Atraxa", "https://img/a.jpg")
        val deck = Deck("d", "Superfriends", commander = commander, cards = listOf(commander, DeckCardEntry("f", "Forest", null, quantity = 30)))
        assertEquals(MoveTarget(SourceKind.DECK, "d", "Superfriends", "https://img/a.jpg", 31), deck.asTarget())
        val binder = Collection("b", "Trades", entries = listOf(CollectionEntry("s", "Sol Ring", null, quantity = 2, foilQuantity = 1)))
        assertEquals(MoveTarget(SourceKind.BINDER, "b", "Trades", null, 3), binder.asTarget())
    }

    // ---- How many ----

    @Test
    fun addingStartsAtOneCopy() {
        assertEquals(QuantityLimits(1, MAX_ADD_QUANTITY), quantityLimits(AddVerb.ADD))
        // A card being added has no copies anywhere to be limited by.
        assertEquals(QuantityLimits(1, MAX_ADD_QUANTITY), quantityLimits(AddVerb.ADD, 4))
    }

    @Test
    fun movingOrCopyingStartsAtEveryCopyAndCantTakeMore() {
        assertEquals(QuantityLimits(4, 4), quantityLimits(AddVerb.MOVE, 4))
        assertEquals(QuantityLimits(2, 2), quantityLimits(AddVerb.COPY, 2))
        assertEquals(QuantityLimits(1, MAX_ADD_QUANTITY), quantityLimits(AddVerb.MOVE, null))
    }

    @Test
    fun theStepperStaysBetweenOneAndTheMost() {
        val limits = QuantityLimits(3, 3)
        assertEquals(3, limits.step(3, 1))
        assertEquals(2, limits.step(3, -1))
        assertEquals(1, limits.step(1, -1))
    }

    @Test
    fun plainCopiesGoBeforeFoils() {
        val entry = CollectionEntry("s", "Sol Ring", null, quantity = 2, foilQuantity = 2)
        assertEquals(1 to 0, copiesTaken(entry, 1))
        assertEquals(2 to 1, copiesTaken(entry, 3))
        assertEquals(2 to 2, copiesTaken(entry, 9))
    }

    // ---- Undo ----

    private val sol = DeckCardEntry("sol", "Sol Ring", null)
    private val forest = DeckCardEntry("forest", "Forest", null, quantity = 10)

    @Test
    fun undoingAnAddTakesTheCopiesBackOut() {
        val before = listOf(Deck("d", "Deck", cards = listOf(forest)))
        val after = listOf(Deck("d", "Deck", cards = listOf(forest, sol)))
        assertEquals(listOf(UndoStep.DeckCard("d", "sol", null, stillThere = true)), undoSteps(before, after, emptyList(), emptyList()))
    }

    @Test
    fun undoingAnExtraCopyPutsTheOldCountBack() {
        val before = listOf(Deck("d", "Deck", cards = listOf(forest)))
        val after = listOf(Deck("d", "Deck", cards = listOf(forest.copy(quantity = 12))))
        assertEquals(listOf(UndoStep.DeckCard("d", "forest", forest, stillThere = true)), undoSteps(before, after, emptyList(), emptyList()))
    }

    @Test
    fun undoingAMovePutsTheCardBackWhereItWas() {
        val binderBefore = listOf(Collection("b", "Trades", entries = listOf(CollectionEntry("sol", "Sol Ring", null, quantity = 1))))
        val binderAfter = listOf(Collection("b", "Trades"))
        val decksBefore = listOf(Deck("d", "Deck"))
        val decksAfter = listOf(Deck("d", "Deck", cards = listOf(sol)))
        val steps = undoSteps(decksBefore, decksAfter, binderBefore, binderAfter)
        assertEquals(2, steps.size)
        assertTrue(UndoStep.DeckCard("d", "sol", null, stillThere = true) in steps)
        assertTrue(UndoStep.BinderCard("b", "sol", CollectionEntry("sol", "Sol Ring", null, quantity = 1), stillThere = false) in steps)
    }

    @Test
    fun undoingAConsiderTakesItOffTheList() {
        val before = listOf(Deck("d", "Deck"))
        val after = listOf(Deck("d", "Deck", considering = listOf(sol)))
        assertEquals(listOf(UndoStep.Considered("d", "sol", null, stillThere = true)), undoSteps(before, after, emptyList(), emptyList()))
    }

    @Test
    fun movingToTheSideboardIsSaidAndUndoneLikeTheRest() {
        assertEquals("Moved 2 × Duress to the sideboard in Rakdos", addToMessage(AddVerb.MOVE, "Duress", "Rakdos", quantity = 2, sideboard = true))
        val pick = com.mtgcompanion.app.ui.common.AddToPick(MoveTarget(SourceKind.DECK, "d", "Burn", hasSideboard = true), sideboard = true)
        assertEquals("Added Sol Ring to the sideboard in Burn", addToMessage(AddVerb.ADD, "Sol Ring", pick))
        val before = listOf(Deck("d", "Deck", cards = listOf(sol)))
        val after = listOf(Deck("d", "Deck", sideboard = listOf(sol)))
        val steps = undoSteps(before, after, emptyList(), emptyList())
        assertTrue(UndoStep.Sideboard("d", "sol", null, stillThere = true) in steps)
        assertTrue(UndoStep.DeckCard("d", "sol", sol, stillThere = false) in steps)
    }

    @Test
    fun onlyDecksWhoseFormatHasOneOfferASideboard() {
        assertTrue(Deck("d", "Burn", gameMode = com.mtgcompanion.app.data.GameMode.MODERN.name).asTarget().hasSideboard)
        assertTrue(!Deck("d", "Atraxa").asTarget().hasSideboard)
    }

    @Test
    fun aDeckOrBinderMadeForTheCardsIsDeletedAgain() {
        val after = listOf(Deck("new", "New", cards = listOf(sol)))
        val binders = listOf(Collection("nb", "New binder", entries = listOf(CollectionEntry("sol", "Sol Ring", null, quantity = 1))))
        assertEquals(
            listOf(UndoStep.DeleteDeck("new"), UndoStep.DeleteBinder("nb")),
            undoSteps(emptyList(), after, emptyList(), binders, created = setOf("new", "nb"))
        )
    }

    @Test
    fun aPileMadeOnTheWayIsEmptiedNotDeleted() {
        val pile = listOf(Collection("unsorted", "Unsorted", entries = listOf(CollectionEntry("sol", "Sol Ring", null, quantity = 1))))
        assertEquals(listOf(UndoStep.BinderCard("unsorted", "sol", null, stillThere = true)), undoSteps(emptyList(), emptyList(), emptyList(), pile))
    }

    @Test
    fun theWishlistsOwnCardsAreLeftToTheApp() {
        val after = listOf(Collection("wishlist", "Wishlist", entries = listOf(CollectionEntry("sol", "Sol Ring", null, quantity = 1, auto = true))))
        assertEquals(emptyList<UndoStep>(), undoSteps(emptyList(), emptyList(), listOf(Collection("wishlist", "Wishlist")), after))
    }

    @Test
    fun commandersComeBackLast() {
        val before = listOf(Deck("d", "Deck", commander = sol, cards = listOf(sol)))
        val after = listOf(Deck("d", "Deck"))
        val steps = undoSteps(before, after, emptyList(), emptyList())
        assertEquals(listOf(UndoStep.DeckCard("d", "sol", sol, stillThere = false), UndoStep.Commanders("d", sol, null)), steps)
    }

    @Test
    fun nothingChangedNothingToUndo() {
        val decks = listOf(Deck("d", "Deck", cards = listOf(sol)))
        assertEquals(emptyList<UndoStep>(), undoSteps(decks, decks, emptyList(), emptyList()))
    }
}
