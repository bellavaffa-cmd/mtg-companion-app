package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.ItemMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Graded cards: marking a copy graded takes it out of the raw copies (so it fills no deck slot and
 * isn't a spare), its value is the one entered, and two devices' slabs merge — the same on both apps.
 * The web app has the same checks — see MtgCompanionWeb/tests/collection/graded.test.ts.
 */
class GradedTest {

    private val safe = StoragePlace("safe", "Safe, study", PlaceKind.BOX.name, sections = listOf("Slabs"), createdAt = 1)
    private val red = StoragePlace("red", "Red box", PlaceKind.BOX.name, createdAt = 2)
    private fun at(placeId: String, qty: Int, section: String? = null) = CopyPlace(placeId, qty, section = section)
    private fun entry(id: String, name: String, quantity: Int, foilQuantity: Int = 0, places: List<CopyPlace>? = null) =
        CollectionEntry(id, name, null, quantity = quantity, foilQuantity = foilQuantity, places = places)
    private fun pile(entries: List<CollectionEntry> = emptyList(), graded: List<GradedCard>? = null, sealed: List<SealedProduct>? = null) =
        Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, entries, createdAt = 0, type = CollectionType.OWNED.name, storagePlaces = listOf(safe, red), graded = graded, sealed = sealed)
    private fun binder(entries: List<CollectionEntry>) = Collection("b1", "Rares", entries, createdAt = 5, type = CollectionType.OWNED.name)
    private fun slab(
        id: String = "g1", company: GradingCompany = GradingCompany.PSA, companyName: String? = null, grade: String = "10", cert: String? = "12345678",
        valueUsd: Double? = 450.0, placeId: String? = "safe", section: String? = "Slabs", collectionId: String? = null, foil: Boolean? = null
    ) = GradedCard(id, "sheo", SHEO, foil = foil, company = company.name, companyName = companyName, grade = grade, cert = cert, valueUsd = valueUsd, placeId = placeId, section = section, collectionId = collectionId, createdAt = 1)
    private val deck = Deck("d1", "Mono black", cards = listOf(DeckCardEntry("sheo", SHEO, null, quantity = 1)), ownership = DeckOwnership.PROTOTYPE.name, createdAt = 1)
    private val cols = listOf(pile(), binder(listOf(entry("sheo", SHEO, 1, places = listOf(at("red", 1))))))

    @Test
    fun `the raw copies that could be graded - a line per place, and the ones with no place`() {
        val c = listOf(pile(), binder(listOf(entry("sheo", SHEO, 3, 1, listOf(at("red", 1))))))
        assertEquals(listOf("Red box ×1", "No place yet (Rares) ×2", "No place yet (Rares) · foil ×1"), rawSources(c, SHEO).map { "${it.label} ×${it.qty}" })
    }

    @Test
    fun `marking a copy graded takes it out of its binder and off its place`() {
        val after = markGraded(cols, rawSources(cols, SHEO)[0], slab())
        assertTrue(after.first { it.id == "b1" }.entries.isEmpty())
        assertEquals(listOf("PSA 10 from b1"), gradedOf(after).map { "${gradeLabel(it)} from ${it.collectionId}" })
        val two = listOf(pile(), binder(listOf(entry("sheo", SHEO, 2, places = listOf(at("red", 1))))))
        val left = markGraded(two, rawSources(two, SHEO)[0], slab()).first { it.id == "b1" }.entries[0]
        assertEquals(1, left.quantity)
        assertEquals(emptyList<CopyPlace>(), placedCopies(left))
    }

    @Test
    fun `a graded copy doesn't fill a deck slot, count as owned for sorting, or as a spare`() {
        val decks = listOf(deck)
        assertTrue(pullList(deck, cols, decks).groups.none { it.kind == PullGroupKind.MISSING })
        assertTrue(missingCards(deck, cols, decks).isEmpty())
        val after = markGraded(cols, rawSources(cols, SHEO)[0], slab())
        assertEquals(listOf(PullGroupKind.MISSING), pullList(deck, after, decks).groups.map { it.kind })
        assertEquals(listOf("$SHEO ×1"), missingCards(deck, after, decks).map { "${it.entry.name} ×${it.need}" })
        assertEquals(0, ownedCounts(after, emptyList())[SHEO.lowercase()] ?: 0)
        val extra = listOf(pile(), binder(listOf(entry("sheo", SHEO, 2))))
        assertEquals(1, spares(extra, emptyList(), 2).size)
        assertEquals(0, spares(markGraded(extra, rawSources(extra, SHEO)[0], slab()), emptyList(), 2).size)
    }

    @Test
    fun `out of its slab it is a raw copy again, in its binder and its place`() {
        val graded = markGraded(cols, rawSources(cols, SHEO)[0], slab())
        val back = backToRaw(graded, "g1")
        assertTrue(gradedOf(back).isEmpty())
        val e = back.first { it.id == "b1" }.entries[0]
        assertEquals(1, e.quantity)
        assertEquals(listOf(at("safe", 1, "Slabs")), e.places)
        val placed = backToRaw(listOf(pile(graded = listOf(slab(collectionId = "b1"))), binder(listOf(entry("sheo", SHEO, 1, places = listOf(at("red", 1)))))), "g1")
        assertEquals(listOf(at("red", 1), at("safe", 1, "Slabs")), placedCopies(placed.first { it.id == "b1" }.entries[0]))
        val loose = backToRaw(listOf(pile(graded = listOf(slab(collectionId = "gone", foil = true)))), "g1")
        assertEquals(listOf("$SHEO 0+1"), loose[0].entries.map { "${it.name} ${it.quantity}+${it.foilQuantity}" })
        assertTrue(gradedOf(removeGraded(graded, "g1")).isEmpty())
    }

    @Test
    fun `on the card's Where it is - the slab, where it is and its cert`() {
        val c = listOf(pile(graded = listOf(slab(), slab(id = "g2", company = GradingCompany.OTHER, companyName = "ACE", grade = "9", cert = null, placeId = null, valueUsd = null))))
        assertEquals(
            listOf(
                GradedLine("g1", "PSA 10", "Safe, study › Slabs · cert 12345678", 450.0, "safe"),
                GradedLine("g2", "ACE 9", "No place yet", null, null)
            ),
            gradedWhere(c, SHEO)
        )
    }

    @Test
    fun `in Value by place - graded at the value entered, sealed by the box, with their labels`() {
        val sealed = SealedProduct("s", "Duskmourn Play Booster Box", SealedKind.PLAY_BOX.name, "dsk", count = 2, placeId = "safe", valueUsd = 238.0, createdAt = 1)
        val c = listOf(pile(listOf(entry("sheo", SHEO, 1, places = listOf(at("safe", 1)))), listOf(slab()), listOf(sealed)))
        val rows = valueRows(c, emptyList()) { PrintingFacts("dmu", "107", 80.0, 95.0) }
        assertEquals(
            listOf("$SHEO ×1 Normal 80.0", "$SHEO ×1 Graded PSA 10 450.0", "Duskmourn Play Booster Box ×2 Sealed 238.0"),
            rows.map { "${it.name} ×${it.qty} ${finishOf(it)} ${it.unitUsd}" }
        )
        val v = valueGroups(rows, c)
        assertEquals(listOf("Safe, study 1006.0 2+2"), v.groups.map { "${it.label} ${it.usd} ${it.copies}+${it.sealed}" })
        assertEquals(2, v.copies)
        assertEquals(2, v.sealed)
    }

    @Test
    fun `merging slabs - added on either side kept, taken off on either stays off, fields to whoever changed them`() {
        val merged = mergeGraded(listOf(slab()), listOf(slab(valueUsd = 500.0), slab(id = "g2")), listOf(slab(placeId = "red", section = null)), false)!!
        assertEquals(listOf("g1 500.0 red null", "g2 450.0 safe Slabs"), merged.map { "${it.id} ${it.valueUsd} ${it.placeId} ${it.section}" })
        assertEquals(emptyList<GradedCard>(), mergeGraded(listOf(slab()), emptyList(), listOf(slab(grade = "9")), true))
        assertNull(mergeGraded(null, null, null, true))
    }

    @Test
    fun `a pile saved by an app from before graded cards keeps them`() {
        val mine = pile(graded = listOf(slab()))
        assertEquals(listOf(slab()), keepGradedFromOlderApp(mine, pile()).graded)
        assertEquals(listOf(slab()), ItemMerge.mergeCollections(mine, mine, pile(), false).graded)
    }

    private companion object {
        const val SHEO = "Sheoldred, the Apocalypse"
    }
}
