package com.mtgcompanion.app.data.supabase

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CameFrom
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.Loan
import com.mtgcompanion.app.data.LoanCard
import com.mtgcompanion.app.data.CopyPlace
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.StoragePlace
import com.mtgcompanion.app.data.UNSORTED_COLLECTION_ID
import com.mtgcompanion.app.data.localMoshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Storage places ride in the binders' JSON (StoragePlaces.kt). An app from before them drops the keys
 * whenever it saves a binder; this app keeps its own and puts them back. The web app runs the same
 * cases as sync scenarios (tests/sync/scenarios.ts).
 */
class StoragePlacesSyncTest {
    private val adapter = localMoshi.adapter(Collection::class.java)
    private val core = SyncCore(localMoshi.adapter(Deck::class.java), adapter)
    private val red = StoragePlace("red", "Red box", PlaceKind.BOX.name, createdAt = 1)
    private val key = "collection:$UNSORTED_COLLECTION_ID"

    private fun pile(entries: List<CollectionEntry>, places: List<StoragePlace>?) =
        Collection(UNSORTED_COLLECTION_ID, "Unsorted", entries, createdAt = 0, type = "OWNED", storagePlaces = places)

    private fun card(id: String, quantity: Int, places: List<CopyPlace>? = null) = CollectionEntry(id, id, null, quantity = quantity, places = places)

    private fun agreed(json: String) =
        CloudSyncState(items = mapOf(key to ItemMeta(json.hashCode(), 100L, base = json, baseMs = 100L)), userId = "u", cursor = "2026-09-18T000000000001")

    @Test
    fun `an older app's save of a binder doesn't lose where its cards are kept`() {
        val mine = pile(listOf(card("x", 2, listOf(CopyPlace("red", 2)))), listOf(red))
        val mineJson = adapter.toJson(mine)
        // An older app read it without the keys it doesn't know, added a card and saved.
        val older = pile(listOf(card("x", 2), card("y", 1)), null)
        val row = RemoteRow("collection", UNSORTED_COLLECTION_ID, adapter.toJson(older), 200L, false, "2026-09-18T000000000002")
        val result = core.pull(agreed(mineJson), core.localJson(emptyList(), listOf(mine)), listOf(row), emptyList(), 300L)
        val healed = result.collectionChanges[UNSORTED_COLLECTION_ID]!!
        assertEquals(listOf(red), healed.storagePlaces)
        assertEquals(listOf(listOf(CopyPlace("red", 2)), null), healed.entries.map { it.places })
        // ...and pushed back, so every other device has them again.
        assertTrue(key in result.state.pending)
        assertEquals(adapter.toJson(healed), result.local[key])
    }

    @Test
    fun `an older app's save of a place doesn't lose when it was last checked`() {
        val checked = red.copy(lastChecked = 1_790_000_000_000L)
        val mine = pile(listOf(card("x", 2, listOf(CopyPlace("red", 2)))), listOf(checked))
        val mineJson = adapter.toJson(mine)
        // An older app renamed the box and saved the place without the key it doesn't know.
        val older = pile(listOf(card("x", 2, listOf(CopyPlace("red", 2)))), listOf(red.copy(name = "Red box, top")))
        val row = RemoteRow("collection", UNSORTED_COLLECTION_ID, adapter.toJson(older), 200L, false, "2026-09-18T000000000002")
        val result = core.pull(agreed(mineJson), core.localJson(emptyList(), listOf(mine)), listOf(row), emptyList(), 300L)
        val healed = result.collectionChanges[UNSORTED_COLLECTION_ID]!!
        assertEquals(listOf(checked.copy(name = "Red box, top")), healed.storagePlaces)
        // ...and pushed back, so every other device has it again.
        assertTrue(key in result.state.pending)
    }

    @Test
    fun `an older app's save of the pile doesn't lose the loans`() {
        val loan = Loan("L1", "Sam", lentAt = 1_790_000_000_000L, cards = listOf(LoanCard("x", "x", 1, collectionId = UNSORTED_COLLECTION_ID, placeId = "red")))
        val mine = pile(listOf(card("x", 2, listOf(CopyPlace("red", 1)))), listOf(red)).copy(loans = listOf(loan))
        val mineJson = adapter.toJson(mine)
        // An app that knows places but not loans renamed the box and saved the pile without the key.
        val older = pile(listOf(card("x", 2, listOf(CopyPlace("red", 1)))), listOf(red.copy(name = "Red box, top")))
        val row = RemoteRow("collection", UNSORTED_COLLECTION_ID, adapter.toJson(older), 200L, false, "2026-09-18T000000000002")
        val result = core.pull(agreed(mineJson), core.localJson(emptyList(), listOf(mine)), listOf(row), emptyList(), 300L)
        val healed = result.collectionChanges[UNSORTED_COLLECTION_ID]!!
        assertEquals(listOf(loan), healed.loans)
        assertEquals("Red box, top", healed.storagePlaces!![0].name)
        // ...and pushed back, so every other device has them again.
        assertTrue(key in result.state.pending)
    }

    @Test
    fun `a card got back on one device and a loan made on another keep both`() {
        val loan = Loan("L1", "Sam", lentAt = 1_790_000_000_000L, cards = listOf(LoanCard("x", "x", 1, collectionId = UNSORTED_COLLECTION_ID, placeId = "red")))
        val other = Loan("L2", "Priya", lentAt = 1_790_000_000_001L, cards = listOf(LoanCard("x", "x", 1, collectionId = UNSORTED_COLLECTION_ID)))
        val base = pile(listOf(card("x", 2)), listOf(red)).copy(loans = listOf(loan))
        val mine = pile(listOf(card("x", 2, listOf(CopyPlace("red", 1)))), listOf(red))
            .copy(loans = listOf(loan.copy(cards = listOf(loan.cards[0].copy(back = 1)), returnedAt = 5)))
        val theirs = base.copy(loans = listOf(loan, other))
        val merged = ItemMerge.mergeCollections(base, mine, theirs, minePreferred = true)
        assertEquals(listOf("L1 1 5", "L2 0 -"), merged.loans!!.map { "${it.id} ${it.cards[0].back ?: 0} ${it.returnedAt ?: "-"}" })
    }

    @Test
    fun `copies given places on two devices at once keep both`() {
        val base = pile(listOf(card("x", 3)), listOf(red))
        val mine = pile(listOf(card("x", 3, listOf(CopyPlace("red", 1)))), listOf(red))
        val theirs = pile(listOf(card("x", 3, listOf(CopyPlace("red", 1, section = "Red")))), listOf(red))
        val row = RemoteRow("collection", UNSORTED_COLLECTION_ID, adapter.toJson(theirs), 200L, false, "2026-09-18T000000000002")
        val state = agreed(adapter.toJson(base)).copy(pending = mapOf(key to 150L))
        val result = core.pull(state, core.localJson(emptyList(), listOf(mine)), listOf(row), emptyList(), 300L)
        // Lines added on both sides come in key order, the same on every device.
        assertEquals(
            listOf(CopyPlace("red", 1, section = "Red"), CopyPlace("red", 1)),
            result.collectionChanges[UNSORTED_COLLECTION_ID]!!.entries[0].places
        )
    }

    @Test
    fun `an older app's save of a deck doesn't lose where its cards came from`() {
        val deckAdapter = localMoshi.adapter(Deck::class.java)
        val deckKey = "deck:d1"
        val mine = Deck("d1", "Krenko", cards = listOf(DeckCardEntry("a", "Sol Ring", null)), createdAt = 1, cameFrom = listOf(CameFrom("Sol Ring", "red", 1)))
        val mineJson = deckAdapter.toJson(mine)
        // An older app read it without "cameFrom", renamed it and saved.
        val older = mine.copy(name = "Krenko goblins", cameFrom = null)
        val row = RemoteRow("deck", "d1", deckAdapter.toJson(older), 200L, false, "2026-09-18T000000000002")
        val state = CloudSyncState(items = mapOf(deckKey to ItemMeta(mineJson.hashCode(), 100L, base = mineJson, baseMs = 100L)), userId = "u", cursor = "2026-09-18T000000000001")
        val result = core.pull(state, core.localJson(listOf(mine), emptyList()), listOf(row), emptyList(), 300L)
        val healed = result.deckChanges["d1"]!!
        assertEquals("Krenko goblins", healed.name)
        assertEquals(listOf(CameFrom("Sol Ring", "red", 1)), healed.cameFrom)
        assertTrue(deckKey in result.state.pending)
    }
}
