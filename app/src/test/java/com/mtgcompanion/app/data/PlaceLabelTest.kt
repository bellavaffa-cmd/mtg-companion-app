package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Box labels: what the QR code holds and what the label says, the same on both apps. The web app has
 * the same checks — see MtgCompanionWeb/tests/collection/placeLabel.test.ts.
 */
class PlaceLabelTest {

    private fun place(id: String, name: String = id, kind: PlaceKind = PlaceKind.BOX, parentId: String? = null,
                      sections: List<String>? = null, sortRule: SortRule? = null, createdAt: Long = 1) =
        StoragePlace(id, name, kind.name, parentId, null, sections, null, sortRule?.name, createdAt)

    private val shelf = place("shelf", "Shelf, study", PlaceKind.SHELF, createdAt = 1)
    private val red = place("red", "Red box", parentId = "shelf", sortRule = SortRule.COLOUR,
        sections = listOf("White", "Blue", "Black", "Red", "Green", "Other"), createdAt = 2)
    private val loose = place("loose", "Loose", createdAt = 0)
    private val places = listOf(shelf, red, loose)
    private val id = "3f2a9c1e-5b7d-4e8f-9a0b-1c2d3e4f5a6b"

    @Test
    fun aLabelHoldsALinkToItsPlaceAndTheScannersReadItBack() {
        assertEquals("https://manabind.com/place/$id", placeLabelLink(id))
        assertEquals(LabelScan(id, false), placeIdFromLabel(placeLabelLink(id)))
        assertEquals(LabelScan(id, false), placeIdFromLabel("  http://www.manabind.com/place/$id/?x=1 "))
        assertEquals(LabelScan("red", false), placeIdFromLabel("http://localhost:5173/place/red"))
        assertEquals(LabelScan("red", false), placeIdFromLabel("https://example.github.io/mtg-companion-web/place/red"))
        // The first labels held the id alone.
        assertEquals(LabelScan(id, true), placeIdFromLabel(id.uppercase()))
        // Not a label.
        assertNull(placeIdFromLabel("https://manabind.com/add/bob"))
        assertNull(placeIdFromLabel("https://manabind.com/place/"))
        assertNull(placeIdFromLabel("https://manabind.com/place/a/b"))
        assertNull(placeIdFromLabel("https://evil.example/place/red"))
        assertNull(placeIdFromLabel("https://manabind.com/place/%E0%A4%A"))
        assertNull(placeIdFromLabel("red"))
    }

    @Test
    fun whatALabelSays() {
        assertEquals(
            LabelText("Shelf, study", "Red box", "White · Blue · Black · Red · Green · Other", "By colour, then A–Z", null),
            labelText(red, places, LabelShow(), 612)
        )
        assertEquals(
            LabelText(null, "Red box", null, null, "1 copy"),
            labelText(red, places, LabelShow(where = false, sections = false, rule = false, count = true), 1)
        )
        assertEquals(
            LabelText(null, "Shelf, study", null, null, "0 copies"),
            labelText(shelf, places, LabelShow(count = true), 0)
        )
        assertEquals(listOf("loose", "shelf", "red"), labelOrder(places).map { it.id })
        assertEquals(100, LabelSize.DIVIDER.heightMm)
    }
}
