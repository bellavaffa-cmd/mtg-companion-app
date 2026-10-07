package com.mtgcompanion.app.data.supabase

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.PendingReset
import com.mtgcompanion.app.data.ResetScope
import com.mtgcompanion.app.data.StoragePlace
import com.mtgcompanion.app.data.UNSORTED_COLLECTION_ID
import com.mtgcompanion.app.data.WISHLIST_ID
import com.mtgcompanion.app.data.localMoshi
import com.mtgcompanion.app.data.resetLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.IOException

/**
 * Reset collection and the sync: two devices and a fake server (as in SyncCoreTest), the reset made on
 * one, and every device ending up with the reset — nothing coming back from the other device's copies.
 * The web app's tests/settings/resetCollection.test.ts runs the same cases.
 */
@RunWith(Parameterized::class)
class ResetSyncTest(private val cas: Boolean) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "compare-and-swap = {0}")
        fun servers() = listOf(false, true)
    }

    private val core = SyncCore(localMoshi.adapter(Deck::class.java), localMoshi.adapter(Collection::class.java))

    private inner class Server {
        var clock = 1000L
        val rows = LinkedHashMap<String, RemoteRow>()
        var failPush = false

        fun stamp(n: Long) = "2026-09-18T%012d".format(n)
        private fun number(stamp: String) = stamp.substringAfter('T').toLong()

        fun pull(cursor: String?): List<RemoteRow> {
            val from = cursor?.let { number(it) - 60 }
            return rows.values.filter { from == null || number(it.serverUpdatedAt) > from }.sortedBy { it.serverUpdatedAt }
        }

        fun fetch(keys: kotlin.collections.Collection<String>) = rows.values.filter { it.key in keys }

        private fun write(item: PushItem, base: Long?) {
            clock++
            rows[item.key] = RemoteRow(item.kind, item.id, item.json, item.editedMs, item.json == null, stamp(clock), base)
        }

        fun push(batch: List<PushItem>): Set<String> {
            if (failPush) { failPush = false; throw IOException("network down") }
            val wrote = LinkedHashSet<String>()
            batch.forEach { item ->
                val existing = rows[item.key]
                if (cas) {
                    if (existing != null && existing.editedMs != item.base) return@forEach
                    write(item, item.base)
                } else {
                    if (existing != null && existing.editedMs > item.editedMs) return@forEach
                    write(item, null)
                }
                wrote += item.key
            }
            return wrote
        }
    }

    /** A device: its library, what it noted as deleted (as the repositories keep it), its sync state. */
    private inner class Device {
        var decks: List<Deck> = emptyList()
        var collections: List<Collection> = emptyList()
        var deletedDecks: Map<String, Long> = emptyMap()
        var deletedCollections: Map<String, Long> = emptyMap()
        var state = CloudSyncState(userId = "u")
    }

    private lateinit var server: Server
    private val devices = HashMap<String, Device>()
    private var time = 1_000_000L

    @Before
    fun start() {
        server = Server()
        devices.clear()
    }

    private fun dev(name: String) = devices.getOrPut(name) { Device() }
    private fun now(): Long { time += 10; return time }

    private fun deletedKeys(d: Device) =
        d.deletedDecks.keys.mapTo(HashSet()) { "deck:$it" } + d.deletedCollections.keys.map { "collection:$it" }

    private fun card(id: String, quantity: Int) = CollectionEntry(scryfallId = id, name = id, imageUrl = null, quantity = quantity)

    /** The user resetting on [name], as CollectionReset does through the repositories. */
    private fun reset(name: String, scope: ResetScope) {
        val d = dev(name)
        val plan = resetLibrary(d.decks, d.collections, scope)
        val t = now()
        d.decks = plan.decks
        d.collections = plan.collections
        d.deletedDecks = d.deletedDecks + plan.deletedDecks.associateWith { t }
        d.deletedCollections = d.deletedCollections + plan.deletedCollections.associateWith { t }
    }

    private fun recordEdits(name: String) {
        val d = dev(name)
        d.state = core.notePending(d.state, core.localJson(d.decks, d.collections), now(), deletedKeys(d))
    }

    private fun pass(name: String) {
        val d = dev(name)
        val t = now()
        val local = core.localJson(d.decks, d.collections)
        d.state = core.notePending(d.state, local, t, deletedKeys(d))
        val rows = server.pull(d.state.cursor)
        val refetch = core.refetchKeys(d.state)
        val again = if (refetch.isEmpty()) emptyList() else server.fetch(refetch)
        val result = core.pull(d.state, local, rows, again, t)
        val deckSnapshot = d.decks.associateBy { it.id }
        val collectionSnapshot = d.collections.associateBy { it.id }
        d.decks = applyRemoteChanges(d.decks, deckSnapshot, result.deckChanges, { it.id }) { b, m, th -> ItemMerge.mergeDecks(b, m, th, minePreferred = true) }
        d.collections = applyRemoteChanges(d.collections, collectionSnapshot, result.collectionChanges, { it.id }) { b, m, th -> ItemMerge.mergeCollections(b, m, th, minePreferred = true) }
        d.state = result.state
        val batch = core.pushBatch(d.state, result.local, t)
        if (batch.isEmpty()) return
        d.state = core.withSent(d.state, batch)
        try {
            val wrote = server.push(batch)
            d.state = if (cas) core.afterPushV2(d.state, batch, wrote) else {
                val landed = if (wrote.size == batch.size) wrote else {
                    val stamps = batch.associate { it.key to it.editedMs }
                    server.fetch(batch.map { it.key }).filter { stamps[it.key] == it.editedMs }.mapTo(HashSet()) { it.key }
                }
                core.afterPushV1(d.state, batch, landed)
            }
        } catch (e: IOException) {
            // offline: the next pass carries on
        }
    }

    private fun settle(vararg names: String) = repeat(3) { names.forEach { pass(it) } }

    private fun cards(name: String, id: String) =
        dev(name).collections.firstOrNull { it.id == id }?.entries?.joinToString(",") { "${it.scryfallId}${it.quantity}" } ?: "(none)"

    private fun setUp() {
        val a = dev("A")
        a.decks = listOf(Deck(id = "d1", name = "d1", createdAt = 1), Deck(id = "d2", name = "d2", createdAt = 1))
        a.collections = listOf(
            Collection(id = UNSORTED_COLLECTION_ID, name = "Unsorted", createdAt = 1, entries = listOf(card("a", 2)), storagePlaces = listOf(StoragePlace(id = "p1", name = "Box", createdAt = 1))),
            Collection(id = WISHLIST_ID, name = "Wishlist", type = "WISHLIST", createdAt = 1, entries = listOf(card("w", 1))),
            Collection(id = "b1", name = "b1", createdAt = 1, entries = listOf(card("b", 3)))
        )
        settle("A", "B")
        assertEquals("b3", cards("B", "b1"))
    }

    @Test
    fun `after Everything, the other device converges and nothing comes back`() {
        setUp()
        reset("A", ResetScope.EVERYTHING)
        settle("A", "B")
        for (name in listOf("A", "B")) {
            assertTrue(name, dev(name).decks.isEmpty())
            assertEquals(name, listOf(UNSORTED_COLLECTION_ID, WISHLIST_ID), dev(name).collections.map { it.id }.sorted())
            assertEquals(name, "", cards(name, UNSORTED_COLLECTION_ID))
            assertEquals(name, "", cards(name, WISHLIST_ID))
            assertEquals(name, emptyList<StoragePlace>(), dev(name).collections.first { it.id == UNSORTED_COLLECTION_ID }.storagePlaces)
        }
        assertTrue(server.rows["deck:d1"]!!.deleted)
        assertTrue(server.rows["collection:b1"]!!.deleted)
    }

    @Test
    fun `a device that touched the cards before syncing doesn't bring them back`() {
        setUp()
        reset("A", ResetScope.CARDS)
        // B, not yet synced, adds a copy of a card A's reset removed, and a new card.
        val b = dev("B")
        b.collections = b.collections.map { if (it.id == "b1") it.copy(entries = listOf(card("b", 4), card("n", 1))) else it }
        pass("A")
        settle("B", "A")
        for (name in listOf("A", "B")) {
            // The reset's cards stay gone; the card added elsewhere meanwhile is kept.
            assertEquals(name, "n1", cards(name, "b1"))
            assertEquals(name, "", cards(name, UNSORTED_COLLECTION_ID))
            assertEquals(name, 2, dev(name).decks.size)
        }
    }

    @Test
    fun `a reset made offline goes when back online, and still wins`() {
        setUp()
        reset("A", ResetScope.COLLECTION)
        recordEdits("A")
        server.failPush = true
        pass("A")
        assertEquals(false, server.rows["collection:b1"]!!.deleted)
        settle("A", "B")
        for (name in listOf("A", "B")) {
            assertEquals(name, listOf(UNSORTED_COLLECTION_ID, WISHLIST_ID), dev(name).collections.map { it.id }.sorted())
            assertEquals(name, "", cards(name, UNSORTED_COLLECTION_ID))
            assertEquals(name, 2, dev(name).decks.size)
        }
    }

    @Test
    fun `Undo before anything was sent leaves every device as it was`() {
        setUp()
        val a = dev("A")
        val before = Triple(a.decks, a.collections, a.deletedDecks to a.deletedCollections)
        val pending = PendingReset(ResetScope.EVERYTHING, before, 0L)
        reset("A", ResetScope.EVERYTHING)
        // The sync is held while Undo is offered; Undo puts the stores back.
        val (d, c, deleted) = pending.undo()!!
        a.decks = d; a.collections = c; a.deletedDecks = deleted.first; a.deletedCollections = deleted.second
        settle("A", "B")
        assertEquals(2, dev("A").decks.size)
        assertEquals("b3", cards("A", "b1"))
        assertEquals("b3", cards("B", "b1"))
        assertEquals(2, dev("B").decks.size)
    }
}
