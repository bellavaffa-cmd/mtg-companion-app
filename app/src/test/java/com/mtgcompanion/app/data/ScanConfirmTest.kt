package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * When a camera read counts as a card. The web app has the same checks — see
 * MtgCompanionWeb/tests/scan/scanLogic.test.ts.
 */
class ScanConfirmTest {

    @Test
    fun theWholeNameRead() {
        assertEquals(Confirmation.YES, confirmRead("Lightning Bolt", "Lightning Bolt"))
        assertEquals(Confirmation.YES, confirmRead("lightning bolt", "Lightning Bolt"))
        // Punctuation and a misread letter or two across a full name still name that card.
        assertEquals(Confirmation.YES, confirmRead("Kenriths Transformation", "Kenrith's Transformation"))
        assertEquals(Confirmation.YES, confirmRead("Rhystic Studv", "Rhystic Study"))
        // A card's front face names it.
        assertEquals(Confirmation.YES, confirmRead("Delver of Secrets", "Delver of Secrets // Insectile Aberration"))
    }

    @Test
    fun halfACardIsNotACard() {
        // The very thing that fouls up a scan: part of a name, answered with a real card.
        assertEquals(Confirmation.PARTIAL, confirmRead("Lightning B", "Lightning Bolt"))
        assertEquals(Confirmation.PARTIAL, confirmRead("Sol", "Sol Ring"))
        assertEquals(Confirmation.PARTIAL, confirmRead("of Secrets", "Delver of Secrets"))
        // More than the name was read — another card's title in the frame, say.
        assertEquals(Confirmation.PARTIAL, confirmRead("Sol Ring Cultivate", "Sol Ring"))
    }

    @Test
    fun anotherCardEntirely() {
        assertEquals(Confirmation.DIFFERENT, confirmRead("Lightning Helix", "Lightning Bolt"))
        assertEquals(Confirmation.DIFFERENT, confirmRead("", "Lightning Bolt"))
    }

    @Test
    fun aCardPrintedUnderAnotherNameAnswersToTheNameOnTheCard() {
        // Universes Beyond: "Kefka's Tower" is printed large, "Bolas's Citadel" in smaller type beneath.
        assertEquals(Confirmation.YES, confirmRead("Kefka's ToWer", "Bolas's Citadel", "Kefka's Tower"))
        // The real name underneath is just as good a read.
        assertEquals(Confirmation.YES, confirmRead("Bolas's Citadel", "Bolas's Citadel", "Kefka's Tower"))
        // Half of the flavour name is still half a card.
        assertEquals(Confirmation.PARTIAL, confirmRead("Kefka's", "Bolas's Citadel", "Kefka's Tower"))
        // And something else entirely is still something else.
        assertEquals(Confirmation.DIFFERENT, confirmRead("Sol Ring", "Bolas's Citadel", "Kefka's Tower"))
    }

    @Test
    fun accurateScanningIsTheCarefulDefaultAndFastTradesCareForSpeed() {
        // Accurate is how the scanner has always worked, and what anyone gets until they choose.
        assertEquals(ScanMode.ACCURATE, ScanMode.fromName(null))
        assertEquals(ScanMode.ACCURATE, ScanMode.fromName("not a mode"))
        assertEquals(STEADY_READS, ScanMode.ACCURATE.steadyReads)
        assertEquals(true, ScanMode.ACCURATE.readsSmallPrint)
        // Fast takes a card sooner and leaves the printing to the frame or the art.
        assertEquals(2, ScanMode.FAST.steadyReads)
        assertEquals(false, ScanMode.FAST.readsSmallPrint)
        assertEquals(ScanMode.FAST, ScanMode.fromName("FAST"))
    }
}
