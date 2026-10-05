package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Binder pages: sheets, the order, fitting new cards in and moving pockets, the same on both apps. The
 * web app has the same checks — see MtgCompanionWeb/tests/collection/binderPages.test.ts.
 */
class BinderPagesTest {

    private fun f(name: String, set: String? = null, number: String? = null, colors: List<String>? = null, typeLine: String? = null) =
        CardFacts(name, colors, typeLine, set, number)

    private fun names(list: List<String>) = list.mapIndexed { i, n -> Placed(i, f(n)) }

    private fun at(placeId: String, qty: Int, page: Int? = null, slot: Int? = null) = CopyPlace(placeId, qty, null, null, page, slot)

    private fun entry(id: String, quantity: Int, places: List<CopyPlace>? = null) = CollectionEntry(id, id, null, quantity = quantity, places = places)

    private fun pile(entries: List<CollectionEntry>, places: List<StoragePlace>) =
        Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, entries, createdAt = 0, type = CollectionType.OWNED.name, storagePlaces = places)

    private fun moves(vararg pairs: Pair<Int, Int>) = pairs.map { PocketMove(it.first, it.second) }
    private fun puts(vararg pairs: Pair<Int, Int>) = pairs.map { FitPut(it.first, it.second) }

    @Test
    fun pagesAreTheFrontsAndBacksOfSheets() {
        assertEquals(listOf(1, 1, 2, 2), listOf(1, 2, 3, 4).map(::sheetOf))
        assertEquals(listOf("Front", "Back", "Front"), listOf(1, 2, 3).map(::sideOf))
        assertEquals("Front of sheet 2", sideLabel(3))
        assertEquals("Back of sheet 2", sideLabel(4))
    }

    @Test
    fun aPageLaysItsPocketsOutInAGrid() {
        assertEquals(3 to 3, pageGrid(9))
        assertEquals(3 to 4, pageGrid(12))
        assertEquals(2 to 2, pageGrid(4))
        assertEquals(2 to 4, pageGrid(8))
        assertEquals(3 to 3, pageGrid(7))
        assertEquals(1 to 1, pageGrid(1))
        assertEquals(22, pocketIndex(3, 5, 9))
        assertEquals(3 to 5, pocketAt(22, 9))
        assertEquals(1 to 1, pocketAt(0, 12))
    }

    @Test
    fun theOrderBySetThenNumberAZColourType() {
        fun sorted(rule: SortRule?, list: List<CardFacts>) = list.sortedWith { a, b -> compareCards(rule, a, b) }.map { it.name }
        val list = listOf(f("Zap", "one", "1"), f("Bolt", "dmu", "98a"), f("Shock", "dmu", "98"), f("Abrade", "dmu", "12"))
        assertEquals(listOf("Abrade", "Shock", "Bolt", "Zap"), sorted(SortRule.SET, list))
        assertEquals(listOf("Abrade", "Bolt", "Shock", "Zap"), sorted(SortRule.NAME, list))
        assertEquals(listOf("Abrade", "Bolt", "Shock", "Zap"), sorted(null, list))
        val colours = listOf(f("Opt", colors = listOf("U")), f("Bolt", colors = listOf("R")), f("Abrade", colors = listOf("R")), f("Sol Ring", colors = emptyList()))
        assertEquals(listOf("Opt", "Abrade", "Bolt", "Sol Ring"), sorted(SortRule.COLOUR, colours))
        assertEquals(listOf("Goblin", "Opt"), sorted(SortRule.TYPE, listOf(f("Opt", typeLine = "Instant"), f("Goblin", typeLine = "Creature — Goblin"))))
    }

    @Test
    fun aPagesSummarySaysWhatsOnIt() {
        assertEquals("DMU 12–98", pageSummary(SortRule.SET, listOf(f("Bolt", "dmu", "98"), f("Abrade", "dmu", "12"))))
        assertEquals("DMU 240 – ONE 12", pageSummary(SortRule.SET, listOf(f("Zap", "one", "12"), f("Bolt", "dmu", "240"))))
        assertEquals("DMU 98", pageSummary(SortRule.SET, listOf(f("Bolt", "dmu", "98"))))
        assertEquals("A–C", pageSummary(SortRule.NAME, listOf(f("Counterspell"), f("Abrade"), f("Bolt"))))
        assertEquals("B", pageSummary(null, listOf(f("Bolt"), f("Brainstorm"))))
        assertEquals("Red · A–F", pageSummary(SortRule.COLOUR, listOf(f("Fury", colors = listOf("R")), f("Abrade", colors = listOf("R")))))
        assertEquals("Blue to Red", pageSummary(SortRule.COLOUR, listOf(f("Fury", colors = listOf("R")), f("Opt", colors = listOf("U")))))
        assertEquals("Empty", pageSummary(SortRule.SET, emptyList()))
    }

    @Test
    fun keepingTheOrderCardsShiftAlongOnlyAsFarAsTheNextEmptyPocket() {
        // A B D E F G H in pockets 0–6, then an empty pocket: C goes where D was, D–H one along.
        val plan = planFit(SortRule.NAME, names(listOf("A", "B", "D", "E", "F", "G", "H")), listOf(f("C")), FitMode.KEEP)
        assertEquals(puts(0 to 2), plan.puts)
        assertEquals(moves(2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7), plan.moves)
        // An empty pocket after E: only D and E move.
        val gap = planFit(SortRule.NAME, names(listOf("A", "B", "D", "E")) + Placed(5, f("F")), listOf(f("C")), FitMode.KEEP)
        assertEquals(moves(2 to 3, 3 to 4), gap.moves)
        // Room right after B already: nothing moves.
        val room = planFit(SortRule.NAME, names(listOf("A", "B")) + Placed(4, f("D")), listOf(f("C")), FitMode.KEEP)
        assertEquals(FitPlan(emptyList(), puts(0 to 2)), room)
    }

    @Test
    fun keepingTheOrderCardsShiftBackInsteadWhenThatMovesFewer() {
        // Pocket 0 empty, 1–20 full: C between B (2) and D (3) moves A and B back rather than D…U along.
        val letters = "ABDEFGHIJKLMNOPQRSTU".map { it.toString() }
        val plan = planFit(SortRule.NAME, letters.mapIndexed { i, n -> Placed(i + 1, f(n)) }, listOf(f("C")), FitMode.KEEP)
        assertEquals(moves(1 to 0, 2 to 1), plan.moves)
        assertEquals(puts(0 to 2), plan.puts)
    }

    @Test
    fun keepingTheOrderIsStableANewCopyGoesAfterTheOnesThereAndCardsAddedTogetherKeepTheirOrder() {
        assertEquals(FitPlan(moves(2 to 3), puts(0 to 2)), planFit(SortRule.NAME, names(listOf("Bolt", "Bolt", "Shock")), listOf(f("Bolt")), FitMode.KEEP))
        // Several at once, two copies of one card among them: in order, each into the binder as the last left it.
        val many = planFit(SortRule.NAME, names(listOf("A", "Z")), listOf(f("Shock"), f("Bolt"), f("Bolt")), FitMode.KEEP)
        assertEquals(moves(1 to 4), many.moves)
        assertEquals(puts(0 to 3, 1 to 1, 2 to 2), many.puts)
        // The last card of all goes after the last pocket in use.
        assertEquals(FitPlan(emptyList(), puts(0 to 2)), planFit(SortRule.NAME, names(listOf("A", "B")), listOf(f("Z")), FitMode.KEEP))
        assertEquals(FitPlan(emptyList(), puts(0 to 1, 1 to 0)), planFit(SortRule.NAME, emptyList(), listOf(f("B"), f("A")), FitMode.KEEP))
    }

    @Test
    fun fullPagesSpillOntoTheNextPageAndTheStepsSaySoFromTheLastCardBack() {
        // 4 pockets a page, pages 1 and 2 full.
        val animals = listOf("Ant", "Bat", "Cat", "Dog", "Eel", "Fox", "Gnu", "Hen")
        val plan = planFit(SortRule.NAME, names(animals), listOf(f("Cow")), FitMode.KEEP)
        assertEquals(puts(0 to 3), plan.puts)
        assertEquals(listOf("3>4", "4>5", "5>6", "6>7", "7>8"), plan.moves.map { "${it.from}>${it.to}" })
        val steps = fitSteps(plan, 4, { animals[it] }, { "Cow" to "M21 7" })
        assertEquals(
            listOf(
                FitStep("Pages 1–2: move 5 cards one along", "Starting from the last card, so nothing is in the way"),
                FitStep("Put Cow in page 1, slot 4", "M21 7")
            ),
            steps
        )
    }

    @Test
    fun fillingGapsMovesNothingAndTakesTheNearestEmptyPocket() {
        // Pockets 1–9 full, 0 and 10 empty: C (between B and D) goes in pocket 0, nearer than 10.
        val letters = "ABDEFGHIJ".map { it.toString() }
        val plan = planFit(SortRule.NAME, letters.mapIndexed { i, n -> Placed(i + 1, f(n)) }, listOf(f("C")), FitMode.GAPS)
        assertEquals(FitPlan(emptyList(), puts(0 to 0)), plan)
        // As near both ways: the one after.
        val even = planFit(SortRule.NAME, listOf(Placed(1, f("A")), Placed(2, f("D")), Placed(4, f("E"))), listOf(f("C")), FitMode.GAPS)
        assertEquals(FitPlan(emptyList(), puts(0 to 3)), even)
    }

    @Test
    fun stepsMovesOfDifferentLengthsAreStepsOfTheirOwnAndNewCardsOnOnePageAreOneStep() {
        val plan = planFit(SortRule.NAME, names(listOf("A", "B", "D", "F")), listOf(f("C"), f("E")), FitMode.KEEP)
        assertEquals(moves(2 to 3, 3 to 5), plan.moves)
        assertEquals(puts(0 to 2, 1 to 4), plan.puts)
        val name = { i: Int -> listOf("A", "B", "D", "F")[i] }
        assertEquals(
            listOf(
                "Put E in page 1, slot 5",
                "Move F from page 1, slot 4 to page 1, slot 6",
                "Move D from page 1, slot 3 to page 1, slot 4",
                "Put C in page 1, slot 3"
            ),
            fitSteps(plan, 9, name, { listOf("C", "E")[it] to "" }).map { it.title }
        )
        // New cards in empty pockets of one page: one step, slot by slot.
        val two = planFit(SortRule.NAME, listOf(Placed(0, f("A")), Placed(5, f("M"))), listOf(f("Z"), f("B")), FitMode.KEEP)
        assertEquals(listOf(FitStep("Put 2 cards in page 1", "Slot 2: B · Slot 7: Z")), fitSteps(two, 9, { "" }, { listOf("Z", "B")[it] to "" }))
        // A run on one page.
        val run = fitSteps(FitPlan(moves(4 to 5, 5 to 6, 6 to 7), emptyList()), 9, { "" }, { "" to "" })
        assertEquals(listOf("Page 1: move slots 5–7 one along"), run.map { it.title })
    }

    @Test
    fun closingTheGapsMovesEveryCardUpFromTheFirst() {
        assertEquals(moves(2 to 1, 5 to 2), closeGapsMoves(listOf(5, 0, 2)))
        assertEquals(emptyList<PocketMove>(), closeGapsMoves(listOf(0, 1)))
        val steps = fitSteps(FitPlan(closeGapsMoves(listOf(0, 2, 3, 4)), emptyList()), 9, { "" }, { "" to "" })
        assertEquals(listOf(FitStep("Page 1: move slots 3–5 one back", "Starting from the first card, so nothing is in the way")), steps)
    }

    @Test
    fun draggingAPocketShiftsTheOnesBetweenTappingTwoSwapsThem() {
        val all = setOf(0, 1, 2, 3)
        assertEquals(moves(0 to 3, 1 to 0, 2 to 1, 3 to 2), reorderMoves(0, 3, all))
        assertEquals(moves(1 to 2, 2 to 3, 3 to 1), reorderMoves(3, 1, all))
        assertEquals(moves(0 to 5), reorderMoves(0, 5, all))
        assertEquals(emptyList<PocketMove>(), reorderMoves(4, 0, all))
        assertEquals(moves(1 to 3, 3 to 1), swapMoves(1, 3, all))
        assertEquals(moves(1 to 6), swapMoves(1, 6, all))
        assertEquals(moves(6 to 1), undoMoves(moves(1 to 6)))
    }

    @Test
    fun movingPocketsMovesEveryCopyInThemAtOnceAndFittingPutsTheLooseCopiesIn() {
        val rb = StoragePlace("rb", "Rares binder", PlaceKind.BINDER.name, pocketsPerPage = 4, sortRule = SortRule.NAME.name, createdAt = 1)
        val cols = listOf(pile(listOf(
            // Two copies of one card in pockets 1 and 2 shift together without running into each other.
            entry("Bolt", 2, listOf(at("rb", 1, 1, 1), at("rb", 1, 1, 2))),
            entry("Opt", 1, listOf(at("rb", 1, 1, 3))),
            entry("Abrade", 1, listOf(at("rb", 1))),
            entry("Wrath", 1, listOf(at("rb", 1, 1, 9)))
        ), listOf(rb)))
        val shift = moves(0 to 1, 1 to 2, 2 to 4)
        val moved = relocate(cols, rb, shift)
        assertEquals(
            listOf(
                listOf(at("rb", 1, 1, 2), at("rb", 1, 1, 3)),
                listOf(at("rb", 1, 2, 1)),
                listOf(at("rb", 1)),
                // A slot past the page's pockets isn't one of its pockets: it's left as it is.
                listOf(at("rb", 1, 1, 9))
            ),
            moved[0].entries.map { it.places }
        )
        assertEquals(cols, relocate(moved, rb, undoMoves(shift)))

        val cards = cardsIn(cols, "rb")
        assertEquals(listOf(0 to listOf("Bolt"), 1 to listOf("Bolt"), 2 to listOf("Opt")), binderPockets(rb, cards).map { p -> p.index to p.cards.map { it.entry.name } })
        assertEquals(listOf("Abrade", "Wrath"), looseCopies(rb, cards).map { it.entry.name })
        assertEquals(1, pageCount(rb, binderPockets(rb, cards)))
        val fit = fitLooseCards(cols, rb, { CardFacts(it.entry.name) }, FitMode.KEEP)
        // Abrade before Bolt: everything one along; Wrath after Opt.
        assertEquals(puts(0 to 0, 1 to 4), fit.plan.puts)
        val done = applyFit(cols, rb, fit.plan, fit.items)
        assertEquals(
            listOf(
                listOf(at("rb", 1, 1, 2), at("rb", 1, 1, 3)),
                listOf(at("rb", 1, 1, 4)),
                listOf(at("rb", 1, 1, 1)),
                listOf(at("rb", 1, 2, 1))
            ),
            done[0].entries.map { it.places }
        )
    }
}
