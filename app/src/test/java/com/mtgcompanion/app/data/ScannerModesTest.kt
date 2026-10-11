package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scanner's modes (ScannerModes.kt), the same on both apps. The web app has the same checks —
 * see MtgCompanionWeb/tests/scan/scannerModes.test.ts.
 */
class ScannerModesTest {

    @Test
    fun theBottomBarOpensInTheModeLastPickedAndAddCardsTheFirstTime() {
        assertEquals(ScannerStart(ScannerMode.ADD, true), scannerStart(ScannerEntry.PLAIN, null))
        assertEquals(ScannerStart(ScannerMode.SORT, true), scannerStart(ScannerEntry.PLAIN, ScannerMode.SORT))
        assertEquals(ScannerStart(ScannerMode.CHECK, true), scannerStart(ScannerEntry.PLAIN, ScannerMode.CHECK))
    }

    @Test
    fun aJobsOwnEntryOpensInThatJobWhateverWasLastPicked() {
        assertEquals(ScannerStart(ScannerMode.PUT_AWAY, true), scannerStart(ScannerEntry.PUT_AWAY, ScannerMode.SORT))
        assertEquals(ScannerStart(ScannerMode.CHECK, true), scannerStart(ScannerEntry.CHECK, ScannerMode.ADD))
        assertEquals(ScannerStart(ScannerMode.SORT, true), scannerStart(ScannerEntry.SORT, null))
    }

    @Test
    fun tickingADecksListIsItsOwnFixedJobWithNoSwitch() {
        val start = scannerStart(ScannerEntry.TICK, ScannerMode.SORT)
        assertNull(start.mode)
        assertFalse(start.showSwitch)
    }

    @Test
    fun tickingTheStorageWalkListIsTheSameFixedJobWithNoSwitch() {
        ScannerMode.entries.map { it as ScannerMode? }.plus(null).forEach { last ->
            assertEquals(ScannerStart(null, false), scannerStart(ScannerEntry.WALK, last))
        }
        assertEquals(scannerStart(ScannerEntry.TICK, null), scannerStart(ScannerEntry.WALK, null))
    }

    @Test
    fun modesAreKeptByKey() {
        ScannerMode.entries.forEach { assertEquals(it, ScannerMode.of(it.key)) }
        assertNull(ScannerMode.of(null))
        assertNull(ScannerMode.of("tick"))
        assertEquals(listOf("Add cards", "Sort a pile", "Put away", "Check"), ScannerMode.entries.map { it.label })
    }

    @Test
    fun anEmptyListHasNothingToCarry() {
        assertEquals(CarryOver.NONE, carryOver(ScannerMode.ADD, ScannerMode.SORT, 0, asked = false))
    }

    @Test
    fun leavingAddCardsForSortAPileAsksOncePerVisit() {
        assertEquals(CarryOver.ASK, carryOver(ScannerMode.ADD, ScannerMode.SORT, 3, asked = false))
        assertEquals(CarryOver.KEEP, carryOver(ScannerMode.ADD, ScannerMode.SORT, 3, asked = true))
    }

    @Test
    fun putAwayCheckAndComingBackKeepTheListWithoutAsking() {
        assertEquals(CarryOver.KEEP, carryOver(ScannerMode.ADD, ScannerMode.PUT_AWAY, 3, asked = false))
        assertEquals(CarryOver.KEEP, carryOver(ScannerMode.ADD, ScannerMode.CHECK, 3, asked = false))
        assertEquals(CarryOver.KEEP, carryOver(ScannerMode.SORT, ScannerMode.ADD, 3, asked = false))
        // Not from Add cards: the list wasn't what was being scanned into.
        assertEquals(CarryOver.KEEP, carryOver(ScannerMode.PUT_AWAY, ScannerMode.SORT, 3, asked = false))
    }

    @Test
    fun sortAPileCarriesOnASortUnderWayRecipeFirst() {
        assertEquals(SortStart.RESUME_RECIPE, sortStart(recipeScans = 4, classicScans = 2, hasLastRecipe = true))
        assertEquals(SortStart.RESUME_CLASSIC, sortStart(recipeScans = 0, classicScans = 2, hasLastRecipe = true))
        assertEquals(SortStart.RESUME_CLASSIC, sortStart(recipeScans = null, classicScans = 1, hasLastRecipe = false))
    }

    @Test
    fun sortAPileOffersTheLastRecipeOrAsksForOne() {
        assertEquals(SortStart.START_LAST, sortStart(recipeScans = 0, classicScans = 0, hasLastRecipe = true))
        assertEquals(SortStart.START_LAST, sortStart(recipeScans = null, classicScans = null, hasLastRecipe = true))
        assertEquals(SortStart.PICK, sortStart(recipeScans = null, classicScans = 0, hasLastRecipe = false))
    }

    @Test
    fun letTheAppChooseNeedsABoxWithARule() {
        val binder = StoragePlace("b", "Binder", PlaceKind.BINDER.name, sortRule = SortRule.SET.name, createdAt = 1)
        val plain = StoragePlace("p", "Plain box", PlaceKind.BOX.name, createdAt = 2)
        val red = StoragePlace("r", "Red box", PlaceKind.BOX.name, sortRule = SortRule.COLOUR.name, createdAt = 3)
        assertFalse(canLetAppChoose(emptyList()))
        assertFalse(canLetAppChoose(listOf(binder, plain)))
        assertTrue(canLetAppChoose(listOf(binder, plain, red)))
    }
}
