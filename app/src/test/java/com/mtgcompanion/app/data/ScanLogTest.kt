package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scanning list: one row per scan, newest first. The web app has the same checks — see
 * MtgCompanionWeb/tests/scan/scanLog.test.ts.
 */
class ScanLogTest {

    private val t0 = 1_700_000_000_000L
    private fun card(id: String) = ScryfallCard(id = id, name = id)

    /** Rows as the list holds them: newest first. */
    private fun rows(vararg scans: Pair<String, Long>): List<ScanRow> =
        scans.mapIndexed { i, (id, at) -> ScanRow((scans.size - i).toLong(), card(id), t0 + at) }

    @Test
    fun aCardScannedTwiceTakesTwoRowsEachSayingWhichCopyItIs() {
        // Scanned: Sol Ring, Cultivate, Sol Ring again — newest first.
        val list = rows("sol" to 9000L, "cult" to 4000L, "sol" to 0L)
        assertEquals(listOf(2, 1, 1), list.map { copyNumber(list, it) })
        assertEquals(setOf("sol"), repeatedCards(list))
        assertEquals(listOf("sol", "sol"), onlyRepeats(list).map { it.card.id })
    }

    @Test
    fun aRepeatSecondsApartReadsAsTheCameraCatchingOneCardTwice() {
        val quick = rows("sol" to 1500L, "sol" to 0L)
        assertTrue(scannedTwiceOver(quick, quick[0]))
        assertFalse("the first scan of a card is never a repeat", scannedTwiceOver(quick, quick[1]))

        val later = rows("sol" to DOUBLE_MS + 1, "sol" to 0L)
        assertFalse("a copy scanned a minute later is just another copy", scannedTwiceOver(later, later[0]))
    }

    @Test
    fun thePileIsAddedTogetherOnlyWhenItGoesIntoABinder() {
        val list = rows("sol" to 9000L, "cult" to 4000L, "sol" to 0L)
        assertEquals(listOf("sol" to 2, "cult" to 1), grouped(list).map { it.card.id to it.quantity })
    }
}
