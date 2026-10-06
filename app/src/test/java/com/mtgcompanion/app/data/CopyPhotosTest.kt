package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telling one copy from another for its photos, and the words beside them — the same on both apps.
 * The web app has the same checks — see MtgCompanionWeb/tests/collection/copyPhotos.test.ts.
 */
class CopyPhotosTest {

    private val rares = StoragePlace("rares", "Rares binder", PlaceKind.BINDER.name, createdAt = 1)
    private val red = StoragePlace("red", "Red box", PlaceKind.BOX.name, createdAt = 2)
    private val cols = listOf(
        Collection("stuff", "Trade stuff", listOf(CollectionEntry("opt", "Opt", null, quantity = 1)), createdAt = 1, type = CollectionType.OWNED.name),
        Collection(
            UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME,
            listOf(
                CollectionEntry(
                    "opt", "Opt", null, quantity = 2, foilQuantity = 1, condition = "LP",
                    places = listOf(CopyPlace("red", 1, section = "Blue"), CopyPlace("rares", 1, foil = true, page = 2, slot = 1))
                ),
                CollectionEntry("bolt", "Lightning Bolt", null, quantity = 1)
            ),
            createdAt = 0, type = CollectionType.OWNED.name, storagePlaces = listOf(rares, red)
        ),
        Collection("wish", "Wishlist", listOf(CollectionEntry("opt", "Opt", null, quantity = 4)), createdAt = 2, type = CollectionType.WISHLIST.name)
    )

    @Test
    fun everyCopyOneByOne() {
        val copies = copiesOfCard(cols, "opt")
        assertEquals(listOf("opt||1", "opt||2", "opt|foil|1", "opt||3"), copies.map { it.key })
        assertEquals(listOf("Red box › Blue", "No place yet", "Rares binder p2 s1", "No place yet"), copies.map { it.where })
        assertEquals(listOf("red", null, "rares", null), copies.map { it.placeId })
        assertEquals(listOf(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_ID, "stuff"), copies.map { it.collectionId })
        assertEquals("LP", copies[0].condition)
        assertEquals("x||2", photoKey("x", false, 2))
    }

    @Test
    fun theWords() {
        assertEquals("5 Oct 2026", photoDayLabel("2026-10-05"))
        assertEquals("nonsense", photoDayLabel("nonsense"))
        val money = { usd: Double -> "$" + usd.toInt() }
        assertEquals("$58 · Card shop", boughtLabel(CopyPhoto("k", "ring", "The One Ring", boughtUsd = 58.0, boughtWhere = " Card shop "), money))
        assertEquals("$58", boughtLabel(CopyPhoto("k", "ring", "The One Ring", boughtUsd = 58.0), money))
        assertEquals("", boughtLabel(null, money))
    }

    @Test
    fun askingForPhotos() {
        assertTrue(askForPhotos(62.0, 20.0))
        assertFalse(askForPhotos(10.0, 20.0))
        assertFalse(askForPhotos(20.0, 20.0))
        assertFalse(askForPhotos(62.0, null))
        assertFalse(askForPhotos(null, 20.0))
    }

    @Test
    fun theReportHasThePhotosOfCopiesStillOwned() {
        val photos = listOf(
            CopyPhoto("ring||1", "ring", "The One Ring", front = "a.jpg"),
            CopyPhoto("opt||1", "opt", "Opt", back = "b.jpg"),
            CopyPhoto("bolt||1", "bolt", "Lightning Bolt", boughtUsd = 1.0),
            CopyPhoto("gone||1", "gone", "Gone", front = "c.jpg")
        )
        assertEquals(listOf("opt||1", "ring||1"), photosForReport(photos, setOf("ring", "opt", "bolt")).map { it.key })
    }
}
