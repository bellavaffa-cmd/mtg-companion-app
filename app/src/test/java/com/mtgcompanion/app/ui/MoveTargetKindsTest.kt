package com.mtgcompanion.app.ui

import com.mtgcompanion.app.ui.common.MoveTarget
import com.mtgcompanion.app.ui.common.SourceKind
import com.mtgcompanion.app.ui.common.kindsToChoose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Whether the "where to?" dialog asks binder or deck first, before listing either. */
class MoveTargetKindsTest {

    private val binder = MoveTarget(SourceKind.BINDER, "b", "Trades")
    private val deck = MoveTarget(SourceKind.DECK, "d", "Spirits")

    @Test
    fun bindersAndDecksTogetherAreAskedAboutFirst() {
        assertEquals(listOf(SourceKind.BINDER, SourceKind.DECK), kindsToChoose(listOf(deck, binder), canMakeBinder = false))
    }

    @Test
    fun onlyOneKindGoesStraightToItsList() {
        assertNull(kindsToChoose(listOf(deck, deck), canMakeBinder = false))
        assertNull(kindsToChoose(listOf(binder), canMakeBinder = false))
        assertNull(kindsToChoose(emptyList(), canMakeBinder = true))
    }

    @Test
    fun beingAbleToMakeABinderCountsAsHavingBinders() {
        assertEquals(listOf(SourceKind.BINDER, SourceKind.DECK), kindsToChoose(listOf(deck), canMakeBinder = true))
    }
}
