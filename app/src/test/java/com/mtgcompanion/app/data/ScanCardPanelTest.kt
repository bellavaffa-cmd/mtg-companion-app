package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scanner's Last scanned panel. The web app has the same checks — tests/scan/scanCardPanel.test.ts. */
class ScanCardPanelTest {

    @Test
    fun theDetailsLineIsTheCardsBottomLeft() {
        assertEquals("FIN · 0306 · L · EN", scanDetailsLine("fin", "0306", "common", "Basic Land — Forest", null, false))
        assertEquals("DSK · 123 · M · JP · Foil", scanDetailsLine("dsk", "123", "mythic", "Creature — Horror", "ja", true))
        assertEquals("SLD · 1502 · S · EN", scanDetailsLine("sld", "1502", "special", "Artifact", "en", false))
        assertEquals("TFIN · 4 · T · EN", scanDetailsLine("tfin", "4", "common", "Token Creature — Moogle", "en", false))
        assertEquals("M21 · 12 · U · KR", scanDetailsLine("m21", "12", "uncommon", "Instant", "ko", false))
        assertEquals("R · EN", scanDetailsLine(null, null, "rare", "Sorcery", "en", false))
    }

    @Test
    fun languagesAsPrinted() {
        assertEquals("EN", printedLanguage(null))
        assertEquals("CS", printedLanguage("zhs"))
        assertEquals("CT", printedLanguage("zht"))
        assertEquals("DE", printedLanguage("de"))
    }

    @Test
    fun howItWasIdentified() {
        assertEquals("From small print", scanHowLabel(ScanHow.SMALL_PRINT, true))
        assertEquals("By name", scanHowLabel(ScanHow.NAME, true))
        assertEquals("Best guess", scanHowLabel(ScanHow.NAME, false))
        assertEquals("Best guess", scanHowLabel(ScanHow.SIGHT, false))
        assertEquals("By sight", scanHowLabel(ScanHow.SIGHT, true))
        assertEquals("Learned", scanHowLabel(ScanHow.LEARNED, false))
        assertEquals("Picked by you", scanHowLabel(ScanHow.PICKED, true))
        assertEquals("Best guess", scanHowLabel(null, false))
        assertNull(scanHowLabel(null, true))
        assertTrue(scanIsGuess(ScanHow.NAME, false))
        assertFalse(scanIsGuess(ScanHow.LEARNED, false))
        assertFalse(scanIsGuess(ScanHow.SMALL_PRINT, false))
    }

    @Test
    fun talkBackHearsItInOneGo() {
        assertEquals(
            "Forest, FIN 306, basic land, English, $0.40",
            scanPanelSpoken("Forest", "fin", "0306", "common", "Basic Land — Forest", "en", false, "$0.40", false)
        )
        assertEquals(
            "Sol Ring, C21 263, uncommon, Japanese, foil, best guess, check the printing",
            scanPanelSpoken("Sol Ring", "c21", "263", "uncommon", "Artifact", "ja", true, null, true)
        )
        assertEquals("×3 in this scan", copiesInScan(3))
    }

    @Test
    fun theFoilPriceForAFoilCopy() {
        assertEquals("2.00", panelPrice("0.40", "2.00", true))
        assertEquals("0.40", panelPrice("0.40", "2.00", false))
        assertEquals("0.40", panelPrice("0.40", null, true))
        assertEquals("2.00", panelPrice(null, "2.00", false))
    }

    @Test
    fun theCameraReadingAnotherPrintingIsOnlyTakenOnTwoReadsRunning() {
        val watch = HeldPrintingWatch()
        assertNull(watch.see("FIN" to "307"))
        assertEquals("FIN" to "307", watch.see("fin" to "0307"))
        // A frame with nothing read doesn't break it; another printing starts again.
        assertEquals("FIN" to "307", watch.see(null))
        assertNull(watch.see("FIN" to "308"))
        assertEquals("FIN" to "308", watch.see("FIN" to "308"))
        watch.reset()
        assertNull(watch.see(null))
    }

    @Test
    fun aMismatchIsAnotherPrintingNotLeadingZerosOrCase() {
        assertNull(printingMismatch("fin" to "0306", "FIN" to "306"))
        assertNull(printingMismatch("fin" to "306", null))
        assertEquals("FIN" to "307", printingMismatch("fin" to "0306", "FIN" to "307"))
        assertEquals("FIC" to "306", printingMismatch("fin" to "306", "FIC" to "306"))
        assertEquals("FIN" to "307", printingMismatch(null, "FIN" to "307"))
        assertEquals("Camera reads FIN · 307", cameraReadsLine("FIN" to "307"))
        assertEquals("Use FIN 307", cameraButtonLabel("fin" to "307"))
    }

    private val red = StoragePlace("red", "Red box", PlaceKind.BOX.name)

    private fun entry(id: String, name: String, quantity: Int, foil: Int = 0, places: List<CopyPlace>? = null) =
        CollectionEntry(id, name, null, quantity = quantity, foilQuantity = foil, places = places)

    @Test
    fun whatYouOwnThisPrintingFirstThenTheOthers() {
        val collections = listOf(
            Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, listOf(
                entry("fin-306", "Forest", 2, places = listOf(CopyPlace("red", 2))),
                entry("fin-307", "Forest", 3),
                entry("bolt", "Lightning Bolt", 4)
            ), createdAt = 0, type = CollectionType.OWNED.name, storagePlaces = listOf(red)),
            Collection("trade", "Trade binder", listOf(entry("fin-306", "Forest", 0, foil = 1)), createdAt = 1, type = CollectionType.OWNED.name),
            Collection("wish", "Wishlist", listOf(entry("fin-306", "Forest", 9)), createdAt = 2, type = CollectionType.WISHLIST.name)
        )
        val decks = listOf(
            Deck("k", "Krenko", cards = listOf(DeckCardEntry("fin-306", "Forest", null, quantity = 1), DeckCardEntry("m21-1", "Forest", null, quantity = 1)), ownership = DeckOwnership.PHYSICAL.name),
            Deck("v", "Online", cards = listOf(DeckCardEntry("fin-306", "Forest", null, quantity = 4)), ownership = DeckOwnership.VIRTUAL.name)
        )
        val s = ownedSummary(collections, decks, "fin-306", "Forest")
        assertEquals(OwnedSummary(4, 4, listOf("Red box" to 2, "Trade binder" to 1, "Krenko deck" to 1)), s)
        assertEquals("You own 4 · 2 in Red box, 1 in Trade binder, … · +4 in other printings", ownedLine(s))
        assertEquals("You own 1 · 1 in Krenko deck", ownedLine(OwnedSummary(1, 0, listOf("Krenko deck" to 1))))
        assertEquals("None of this printing · +3 in other printings", ownedLine(OwnedSummary(0, 3, emptyList())))
        assertEquals("You don't own this card yet", ownedLine(ownedSummary(collections, decks, "x", "Sol Ring")))
    }
}
