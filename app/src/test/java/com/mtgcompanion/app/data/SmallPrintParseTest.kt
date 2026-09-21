package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reading the exact printing from a card's small print. The web app has the same checks — see
 * parseSetAndNumber in MtgCompanionWeb/tests/scan/scanLogic.test.ts.
 */
class SmallPrintParseTest {

    private fun read(text: String) = parseSetAndNumber(text.split("\n"))

    @Test
    fun theSetCodeAndNumberAreReadWhateverTheBulletCameOutAs() {
        assertEquals("MSC" to "211", read("U 0211\nMSC • EN  ARTIST NAME"))
        assertEquals("MSC" to "211", read("0211/0280 U\nMSC · EN"))
        assertEquals("FDN" to "7", read("R 0007\nFDN * EN"))
        assertEquals("MH3" to "100", read("C 0100\nMH3 • EN"))
        // As the reader actually sees it on a camera frame:
        assertEquals("MSC" to "806", read("RR | U 0806          E\nMSC « EN % MiLivos CEraN"))
        assertEquals("MSC" to "172", read("———\nCc 0172\nMSC « EN % DARIUS ZABLOCKIS"))
    }

    @Test
    fun aBulletTheReaderDroppedAltogetherNoLongerCostsThePrinting() {
        // Read off Sol Ring (FRC 21) by the scanner, the bullet gone and the artist run on. This
        // used to come back as no printing at all.
        assertEquals("FRC" to "21", read("U 0021\nFRC ENTTUS LUNTER"))
        assertEquals("FRC" to "21", read("U 0021\nFRC EN TITUS LUNTER"))
        // Other printed languages too.
        assertEquals("MOM" to "5", read("M 0005\nMOM JA"))
    }

    @Test
    fun aNumberWithItsZerosReadAsTheLetterOOrALowercaseRarityStillReads() {
        assertEquals("FRC" to "21", read("u oo21 ™ & © 2026 Wi\nFRC + EN » Trrus Lunren"))
        assertEquals("FRC" to "21", read("U 0O21\nFRC « EN"))
        // All letters and no digit is not a number.
        assertNull(read("Uv oon\nFRC « EN » Titus Lunten"))
    }

    @Test
    fun smallPrintThatCantBeReadWithConfidenceGivesNoPrinting() {
        assertNull(read("Illus. Some Artist")) // an older card: no set code line
        assertNull(read("MSC • EN")) // no number
        assertNull(read("U 0211")) // no set
        assertNull(read(""))
        // A word that happens to be followed by two capitals isn't a set code unless those capitals
        // are a language cards are printed in.
        assertNull(read("U 0021\nRAY XY"))
        // A set code has to stand apart from the language: letters run together are a word.
        assertNull(read("U 0021\nTHEORYEN"))
    }
}
