package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun theSetCodeReadsOnItsOwnWhenTheNumberWont() {
        // No number beside it, but the set code still narrows the printings to that set's.
        assertEquals("MSC", parseSetCode(listOf("MSC • EN")))
        assertEquals("FRC", parseSetCode(listOf("Illus. Someone", "FRC ENTTUS LUNTER")))
        assertNull(parseSetCode(listOf("U 0211")))
        assertNull(parseSetCode(listOf("U 0021", "RAY XY")))
    }

    // The owner's report from tester build 39: a Final Fantasy full-art Forest, its small print plainly
    // in view, went in as "Forest again — copy 3". These are the two lines exactly as printed.
    private val finForest = "L 0306   FFIX\nFIN • EN  [paintbrush] ALAYNA DANNER"

    @Test
    fun theFinalFantasyForestsSmallPrintNamesItsPrinting() {
        // The game's code ("FFIX") on the number line is neither a set code nor in the way.
        assertEquals("FIN" to "306", read(finForest))
        assertEquals("FIN" to "306", read("L 0306   FFIX\nFIN • EN  ALAYNA DANNER"))
        assertEquals("FIN", parseSetCode(finForest.split("\n")))
        // The bottom-right lines run on by the reader don't change it.
        assertEquals("FIN" to "306", read("L 0306 FFIX FF© SQUARE ENIX\nFIN • EN ALAYNA DANNER ™ & © 2025 Wizards of the Coast"))
    }

    @Test
    fun aSecondCardsSmallPrintInViewIsNotPairedWithTheFirsts() {
        // The card underneath peeks out of the pile; its small print read first used to give the top
        // card's set code the other card's number (FIN #309, a different Forest).
        assertEquals("FIN" to "306", read("FF© SQUARE ENIX\nL 0309   FFXII\nL 0306   FFIX\nFIN • EN  ALAYNA DANNER"))
        // Both cards' small print read whole: no telling which is which, so no printing at all...
        val both = "$finForest\nL 0307   FFX\nFIN • EN  SOMEONE ELSE"
        assertNull(read(both))
        assertEquals(listOf("FIN" to "306", "FIN" to "307"), smallPrintReadings(both.split("\n")))
        // ...and off the camera's whole frame, two set lines in view are left to the card's own edges.
        assertNull(frameSetAndNumber(listOf("L 0306   FFIX", "FIN • EN  ALAYNA DANNER", "FIN • EN")))
        assertEquals("FIN" to "306", frameSetAndNumber(finForest.split("\n")))
        // Two different set codes: neither is the card's for sure.
        assertNull(parseSetCode(listOf("FIN • EN", "DSK • EN")))
    }

    @Test
    fun everyLayoutOfTheSmallPrintReads() {
        assertEquals("DSK" to "123", read("0123/0277 R\nDSK • EN")) // number/total, before 2023's sets
        assertEquals("DSK" to "123", read("R 0123\nDSK • EN")) // rarity first, since
        assertEquals("SLD" to "1234", read("M 1234\nSLD • EN")) // four digits
        assertEquals("DSK" to "123p", read("R 0123p\nDSK • EN")) // a promo's letter
        assertEquals("SLD" to "1★", read("R 0001★\nSLD • EN"))
        assertEquals("2X2" to "12", read("C 0012\n2X2 • EN")) // digits in the set code
        assertEquals("M21" to "45", read("R 0045\nM21 • EN"))
        assertEquals("40K" to "45", read("R 0045\n40K • EN"))
        assertEquals("PLST" to "211", read("U 0211\nPLST • EN")) // four and five letters
        assertEquals("DSK" to "45", read("R 0045\nDSK • JA")) // Japanese
        assertEquals("DSK" to "45", read("r 0045\ndsk • en")) // read in lowercase
        // Lowercase without a mark between is just words.
        assertNull(read("r 0045\nthe en"))
    }

    @Test
    fun samePrintingIgnoresLeadingZerosAndCase() {
        assertTrue(samePrinting("FIN" to "0306", "fin" to "306"))
        assertFalse(samePrinting("FIN" to "306", "FIN" to "307"))
        assertEquals("123", plainNumber("123p"))
        assertEquals("1", plainNumber("1★"))
    }

    @Test
    fun aPrintingReadWithTheTitleUnreadCountsOnceItReadsTheSameTwice() {
        val streak = SmallPrintStreak()
        assertNull(streak.see("FIN" to "306"))
        assertEquals("FIN" to "0306", streak.see("FIN" to "0306"))
        assertNull(streak.see("FIN" to "308")) // a misread digit starts again
        assertNull(streak.see(null))
        assertNull(streak.see("FIN" to "308"))
    }

    @Test
    fun theNextBasicLandOfAPileIsToldApartByItsSmallPrint() {
        val fin306 = "FIN" to "306"
        val fin307 = "FIN" to "307"
        // The Forest just taken was FIN 306; FIN 307 now reads twice running: a new card.
        assertTrue(newPrintingInView(fin307, fin307, fin306, null))
        assertTrue(newPrintingInView(fin307, fin307, null, fin306))
        // Read once only, or the same printing as before: the card just taken, still in view.
        assertFalse(newPrintingInView(fin307, null, fin306, null))
        assertFalse(newPrintingInView(fin307, fin306, fin306, null))
        assertFalse(newPrintingInView(fin306, fin306, fin306, null))
        // Nothing read now, or nothing to compare with: as before, by name.
        assertFalse(newPrintingInView(null, null, fin306, null))
        assertFalse(newPrintingInView(fin307, fin307, null, null))
        // What was read at the lookup counts over what it went in as, so a printing the look chose
        // differently isn't taken for a new card frame after frame.
        assertFalse(newPrintingInView(fin307, fin307, fin307, fin306))
    }
}
