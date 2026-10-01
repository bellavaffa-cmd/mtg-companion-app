package com.mtgcompanion.app.ui

import com.mtgcompanion.app.tester.ScanOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/** The line the tester app's scanner readout shows for a scan, and files in its activity log. */
class TesterScanOutcomeTest {

    @Test
    fun aCardThatWentInSaysWhatItBecameAndHow() {
        val line = ScanOutcome(7, "Delver of Secrets", "Delver of Secrets // Insectile Aberration", "isd", "51", certain = true, how = "printing read in the frame", tookMs = 480).line()
        assertEquals("Delver of Secrets // Insectile Aberration (ISD #51) certain, printing read in the frame, 480 ms; read \"Delver of Secrets\"", line)
    }

    @Test
    fun aGuessedPrintingSaysSo() {
        val line = ScanOutcome(8, "Sol Ring", "Sol Ring", "cmm", "410", certain = false, how = "name only", tookMs = 90).line()
        assertEquals("Sol Ring (CMM #410) guessed, name only, 90 ms; read \"Sol Ring\"", line)
    }

    @Test
    fun aScanThatAddedNothingSaysWhatWasRead() {
        val line = ScanOutcome(9, "Sol Rlng", null, null, null, certain = false, how = "name only", tookMs = 1200).line()
        assertEquals("Not added: read \"Sol Rlng\" (name only, 1200 ms)", line)
    }
}
