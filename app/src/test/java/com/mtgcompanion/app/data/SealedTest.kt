package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.ItemMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Sealed product: the value and change, opening one (a pile to sort, or a deck), the search, and
 * merging two devices' lists — the same on both apps. The web app has the same checks — see
 * MtgCompanionWeb/tests/collection/sealed.test.ts.
 */
class SealedTest {

    private val cupboard = StoragePlace("cup", "Cupboard, hall", PlaceKind.SHELF.name, createdAt = 1)
    private fun box(count: Int = 2, paidUsd: Double? = 210.0, valueUsd: Double? = 238.0, placeId: String? = "cup", id: String = "dsk", valueAt: Long? = null) =
        SealedProduct(id, "Duskmourn Play Booster Box", SealedKind.PLAY_BOX.name, "dsk", count = count, placeId = placeId, paidUsd = paidUsd, valueUsd = valueUsd, valueAt = valueAt, createdAt = 1)
    private fun precon(count: Int = 1, valueUsd: Double? = 41.0, valueAt: Long? = null, preconFile: String? = "BlameGame_DSC", id: String = "blame", name: String = "Precon: Blame Game") =
        SealedProduct(id, name, SealedKind.PRECON.name, preconFile = preconFile, count = count, paidUsd = 45.0, valueUsd = valueUsd, valueAt = valueAt, createdAt = 2)
    private fun pile(sealed: List<SealedProduct>? = null) =
        Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, createdAt = 0, type = CollectionType.OWNED.name, storagePlaces = listOf(cupboard), sealed = sealed)
    private val usd = { n: Double -> "$" + (if (n == Math.floor(n)) n.toLong().toString() else n.toString()) }

    @Test
    fun `the change from paid to now, in whole percent, with a minus sign for a fall`() {
        assertEquals(13, sealedChange(box()))
        assertEquals(-9, sealedChange(precon()))
        assertNull(sealedChange(box(paidUsd = null)))
        assertNull(sealedChange(box(valueUsd = null)))
        assertNull(sealedChange(box(paidUsd = 0.0)))
        assertEquals("+13%", changeLabel(13))
        assertEquals("−9%", changeLabel(-9))
        assertEquals("0%", changeLabel(0))
    }

    @Test
    fun `the total is each value times the count - a product with no value adds nothing`() {
        assertEquals(238.0 * 2 + 41, sealedTotalUsd(listOf(box(), precon(), box(id = "x", valueUsd = null))), 0.001)
    }

    @Test
    fun `a product's line - count, place and price paid, each when there are more than one`() {
        val cols = listOf(pile(listOf(box(), precon())))
        assertEquals("×2 · Cupboard, hall · paid $210 each", sealedLine(box(), cols, usd))
        assertEquals("×1 · No place yet · paid $45", sealedLine(precon(), cols, usd))
    }

    @Test
    fun `opening one takes one off - the last one takes the product off the list`() {
        val cols = listOf(pile(listOf(box(), precon())))
        val (once, opened) = openSealed(cols, "dsk")
        assertEquals("Duskmourn Play Booster Box", opened?.name)
        assertEquals(1, sealedOf(once).first { it.id == "dsk" }.count)
        val (twice, _) = openSealed(once, "dsk")
        assertFalse(sealedOf(twice).any { it.id == "dsk" })
        assertEquals(listOf("blame"), sealedOf(twice).map { it.id })
        assertNull(openSealed(twice, "dsk").second)
    }

    @Test
    fun `an opened box starts a sort of new cards named after it - a sort of new cards under way is kept`() {
        val rules = listOf(PileRule(PileKind.BULK.name))
        assertEquals(SortSession("Duskmourn Play Booster Box", rules, true, emptyList()), sortForOpened(null, box(), rules))
        val scan = SortScan(1, "x", "X", facts = CardFacts("X"), entry = CollectionEntry("x", "X", null, quantity = 1), pile = 0)
        val underWay = SortSession("Bloomburrow", rules, true, listOf(scan))
        assertSame(underWay, sortForOpened(underWay, box(), rules))
        val tidy = SortSession("Old pile", rules, false, listOf(scan))
        assertEquals("Duskmourn Play Booster Box", sortForOpened(tidy, box(), rules).source)
    }

    @Test
    fun `a precon with its decklist opens as a deck - boxes and precons without one open as a pile`() {
        val list = listOf(box(), precon(), precon(id = "own", name = "Precon: Homebrew", preconFile = null))
        assertEquals(listOf("blame"), openablePrecons(list).map { it.id })
        assertEquals(listOf("dsk", "own"), openableBoxes(list).map { it.id })
        assertEquals("Blame Game", preconDeckName(precon()))
    }

    @Test
    fun `the search offers each set as its products, then precons, then the words as your own`() {
        val sets = listOf(SealedSet("dsk", "Duskmourn: House of Horror", "2024-09-27", 400), SealedSet("lea", "Limited Edition Alpha", "1993-08-05", 295))
        val precons = listOf(PreconInfo("BlameGame_DSC", "Blame Game", "DSC", "2024-09-27"))
        assertEquals(
            listOf(
                "Duskmourn: House of Horror Play Booster Box", "Duskmourn: House of Horror Collector Box", "Duskmourn: House of Horror Bundle",
                "Duskmourn: House of Horror Set Booster Box", "Duskmourn: House of Horror Draft Booster Box", "dusk"
            ),
            sealedOptions("dusk", sets, precons).map { it.name }
        )
        val blame = sealedOptions("blame", sets, precons)
        assertEquals(listOf("PRECON Precon: Blame Game", "OTHER blame"), blame.map { "${it.kind.name} ${it.name}" })
        assertEquals("BlameGame_DSC", blame[0].preconFile)
        assertEquals(emptyList<SealedOption>(), sealedOptions("  ", sets, precons))
        assertEquals(
            SealedProduct("n1", "Precon: Blame Game", "PRECON", setCode = "dsc", preconFile = "BlameGame_DSC", count = 1, createdAt = 5),
            newSealed(blame[0], "n1", 5)
        )
    }

    @Test
    fun `saving writes the product as both apps do - money to the cent, empty fields left out`() {
        val cols = saveSealed(listOf(pile()), box(paidUsd = 210.456, valueUsd = null, placeId = null, id = "n", valueAt = 9))
        assertEquals(SealedProduct("n", "Duskmourn Play Booster Box", "PLAY_BOX", "dsk", count = 2, paidUsd = 210.46, createdAt = 1), sealedOf(cols)[0])
    }

    @Test
    fun `merging - counts add up, both opening one is two opened, and a product left with none goes`() {
        val merged = mergeSealed(listOf(box(3), precon()), listOf(box(2), precon()), listOf(box(2), precon(valueUsd = 50.0, valueAt = 7)), false)!!
        assertEquals(1, merged.first { it.id == "dsk" }.count)
        assertEquals(50.0, merged.first { it.id == "blame" }.valueUsd!!, 0.0)
        assertEquals(emptyList<SealedProduct>(), mergeSealed(listOf(box(2)), listOf(box(1)), listOf(box(1)), true))
    }

    @Test
    fun `merging - one added on either side is kept, one deleted on either side stays deleted`() {
        assertEquals(listOf("blame"), mergeSealed(listOf(box()), listOf(box(), precon()), emptyList(), true)!!.map { it.id })
        assertNull(mergeSealed(null, null, null, true))
        assertEquals(2, mergeSealed(emptyList(), listOf(precon(2)), listOf(precon(1)), false)!![0].count)
    }

    @Test
    fun `a pile saved by an app from before sealed product keeps the list`() {
        val mine = pile(listOf(box()))
        val older = pile()
        assertEquals(listOf(box()), keepSealedFromOlderApp(mine, older).sealed)
        assertEquals(emptyList<SealedProduct>(), keepSealedFromOlderApp(mine, pile(emptyList())).sealed)
        // Through the whole binder merge: the older app's save leaves the list as it was.
        assertEquals(listOf(box()), ItemMerge.mergeCollections(mine, mine, older, false).sealed)
    }
}
