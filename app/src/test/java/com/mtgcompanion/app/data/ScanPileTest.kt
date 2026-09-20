package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scanned pile kept on the device, so shutting the app down mid-session doesn't lose it. */
class ScanPileTest {

    private val now = 1_700_000_000_000L

    @Test
    fun aPileFromAnEarlierDayIsNotTheOneYoureInTheMiddleOf() {
        assertFalse(ScanPile.tooOld(now, now))
        assertFalse(ScanPile.tooOld(now - ScanPile.KEEP_MS, now))
        assertTrue(ScanPile.tooOld(now - ScanPile.KEEP_MS - 1, now))
    }

    @Test
    fun scansKeepTheirOwnNumbersAcrossARestart() {
        val rows = listOf(
            ScanRow(7, ScryfallCard(id = "sol", name = "Sol Ring"), now),
            ScanRow(3, ScryfallCard(id = "cult", name = "Cultivate"), now)
        )
        assertEquals(8L, ScanPile.nextId(rows))
        // Nothing kept: scanning starts from one again.
        assertEquals(1L, ScanPile.nextId(emptyList()))
    }
}
