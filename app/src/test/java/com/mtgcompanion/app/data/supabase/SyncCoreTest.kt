package com.mtgcompanion.app.data.supabase

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.localMoshi
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.IOException

/**
 * Sync scenarios: devices editing, syncing, failing and racing through [SyncCore] and a fake server
 * that behaves like library_items — the old push (newest edit wins) and, when [cas], the
 * compare-and-swap push_library_items_v2. The web app runs the same cases (tests/sync/scenarios.ts).
 */
@RunWith(Parameterized::class)
class SyncCoreTest(private val cas: Boolean) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "compare-and-swap = {0}")
        fun servers() = listOf(false, true)
    }

    private val core = SyncCore(localMoshi.adapter(Deck::class.java), localMoshi.adapter(Collection::class.java))

    /** public.library_items and its push functions. Stamps are sortable strings of a write counter. */
    private inner class Server {
        var clock = 1000L
        val rows = LinkedHashMap<String, RemoteRow>()
        var failPush = false
        var loseResponse = false
        var beforePush: (() -> Unit)? = null

        fun stamp(n: Long) = "2026-09-18T%012d".format(n)
        private fun number(stamp: String) = stamp.substringAfter('T').toLong()

        /** Rows newer than a minute before [cursor], as SupabaseSync.pull asks for them. */
        fun pull(cursor: String?): List<RemoteRow> {
            val from = cursor?.let { number(it) - 60 }
            return rows.values.filter { from == null || number(it.serverUpdatedAt) > from }.sortedBy { it.serverUpdatedAt }
        }

        fun fetch(keys: kotlin.collections.Collection<String>) = rows.values.filter { it.key in keys }

        private fun write(item: PushItem, base: Long?) {
            clock++
            rows[item.key] = RemoteRow(item.kind, item.id, item.json, item.editedMs, item.json == null, stamp(clock), base)
        }

        /** Returns what was written; throws like a dropped connection when told to. */
        fun push(batch: List<PushItem>): Set<String> {
            if (failPush) { failPush = false; throw IOException("network down") }
            beforePush?.let { beforePush = null; it() }
            val wrote = LinkedHashSet<String>()
            batch.forEach { item ->
                val existing = rows[item.key]
                if (cas) {
                    if (existing != null && existing.editedMs != item.base) return@forEach // a null base never matches
                    write(item, item.base)
                } else {
                    if (existing != null && existing.editedMs > item.editedMs) return@forEach
                    write(item, null)
                }
                wrote += item.key
            }
            if (loseResponse) { loseResponse = false; throw IOException("timeout") }
            return wrote
        }
    }

    private inner class Device {
        var decks: List<Deck> = emptyList()
        var state = CloudSyncState(userId = "u")
        var skew = 0L
    }

    private lateinit var server: Server
    private val devices = HashMap<String, Device>()
    /** Wall-clock time, in ms; each step moves it on a little, so every edit has its own time. */
    private var time = 1_000_000L

    @Before
    fun start() {
        server = Server()
        devices.clear()
    }

    private fun dev(name: String) = devices.getOrPut(name) { Device() }
    private fun now(name: String): Long { time += 10; return time + dev(name).skew }

    private fun deck(id: String, cards: List<Pair<String, Int>>) = Deck(
        id = id, name = id, createdAt = 1,
        cards = cards.map { (c, q) -> DeckCardEntry(scryfallId = c, name = c, imageUrl = null, quantity = q, typeLine = "Creature") }
    )

    private fun setDeck(name: String, id: String, vararg cards: Pair<String, Int>) {
        val d = dev(name)
        d.decks = (d.decks.filter { it.id != id } + deck(id, cards.toList())).sortedBy { it.id }
    }

    private fun cards(name: String, id: String = "d1") =
        dev(name).decks.first { it.id == id }.cards.map { it.scryfallId to it.quantity }

    private fun show(name: String, id: String = "d1") =
        dev(name).decks.firstOrNull { it.id == id }?.cards?.joinToString(",") { "${it.scryfallId}${it.quantity}" } ?: "(none)"

    private fun server(id: String = "d1"): String {
        val row = server.rows["deck:$id"] ?: return "(none)"
        if (row.deleted) return "(deleted)"
        return localMoshi.adapter(Deck::class.java).fromJson(row.data!!)!!.cards.joinToString(",") { "${it.scryfallId}${it.quantity}" }
    }

    private fun sorted(text: String) = text.split(",").sorted().joinToString(",")

    private fun recordEdits(name: String) {
        val d = dev(name)
        d.state = core.notePending(d.state, core.localJson(d.decks, emptyList()), now(name))
    }

    /** First half of a pass: pull, then write to the library, then keep the state (SupabaseSync's order). */
    private fun pullPhase(name: String, midEdit: ((List<Deck>) -> List<Deck>)? = null, keepState: Boolean = true): Pair<PullResult, Long> {
        val d = dev(name)
        val t = now(name)
        val local = core.localJson(d.decks, emptyList())
        d.state = core.notePending(d.state, local, t)
        val rows = server.pull(d.state.cursor)
        val refetch = core.refetchKeys(d.state)
        val again = if (refetch.isEmpty()) emptyList() else server.fetch(refetch)
        val result = core.pull(d.state, local, rows, again, t)
        val snapshot = d.decks
        midEdit?.let { d.decks = it(d.decks) }
        d.decks = applyRemoteChanges(d.decks, snapshot.associateBy { it.id }, result.deckChanges, { it.id }) { b, m, th ->
            ItemMerge.mergeDecks(b, m, th, minePreferred = true)
        }
        if (keepState) d.state = result.state
        return result to t
    }

    private fun pushPhase(name: String, pulled: Pair<PullResult, Long>) {
        val d = dev(name)
        val batch = core.pushBatch(d.state, pulled.first.local, pulled.second)
        if (batch.isEmpty()) return
        d.state = core.withSent(d.state, batch) // saved before the request
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
            // offline, or the answer was lost: the next pass carries on
        }
    }

    private fun pass(name: String, midEdit: ((List<Deck>) -> List<Deck>)? = null) = pushPhase(name, pullPhase(name, midEdit))

    private fun settle(vararg names: String) = repeat(3) { names.forEach { pass(it) } }

    @Test
    fun `an edit made twice in a row counts once`() {
        setDeck("A", "d1", "x" to 1); settle("A")
        setDeck("A", "d1", "x" to 2); pass("A")
        setDeck("A", "d1", "x" to 3); pass("A"); pass("A")
        assertEquals("x3", show("A"))
        assertEquals("x3", server())
    }

    @Test
    fun `another device's edit merges with ours, and ours counts once`() {
        setDeck("A", "d1", "x" to 1); settle("A", "B")
        setDeck("A", "d1", "x" to 2); pass("A")
        setDeck("B", "d1", "x" to 1, "y" to 1); pass("B")
        settle("A", "B")
        assertEquals("x2,y1", show("A"))
        assertEquals("x2,y1", show("B"))
    }

    @Test
    fun `a push that fails after a pull keeps what was pulled`() {
        setDeck("A", "d1", "x" to 1); settle("A", "B")
        setDeck("B", "d1", "x" to 1, "y" to 1); pass("B")
        setDeck("A", "d2", "q" to 1)
        server.failPush = true; pass("A")
        settle("A", "B")
        assertEquals("x1,y1", show("A"))
        assertEquals("x1,y1", server())
    }

    @Test
    fun `an edit made while a sync is running is merged with what it pulled`() {
        setDeck("A", "d1", "x" to 1); settle("A", "B")
        setDeck("B", "d1", "x" to 1, "y" to 1); pass("B")
        pass("A") { decks -> decks.map { it.copy(cards = it.cards + it.cards[0].copy(scryfallId = "z", name = "z")) } }
        settle("A", "B")
        assertEquals("x1,y1,z1", show("A"))
        assertEquals("x1,y1,z1", show("B"))
    }

    @Test
    fun `a sync stopped after writing what it pulled, before keeping it, loses nothing`() {
        setDeck("A", "d1", "x" to 1); settle("A", "B")
        setDeck("B", "d1", "x" to 1, "y" to 1); pass("B")
        pullPhase("A", keepState = false) // written to the library, then the app is killed
        settle("A", "B")
        assertEquals("x1,y1", show("A"))
        assertEquals("x1,y1", show("B"))
        assertEquals("x1,y1", server())
    }

    @Test
    fun `an edit from a device whose clock is behind still gets through`() {
        setDeck("B", "d1", "x" to 1); settle("B", "A")
        dev("A").skew = -60_000
        setDeck("A", "d1", "x" to 1, "z" to 1)
        settle("A", "B")
        assertEquals("x1,z1", show("A"))
        assertEquals("x1,z1", server())
        assertEquals("x1,z1", show("B"))
    }

    @Test
    fun `a card removed then added back is not doubled, and a deleted deck goes everywhere`() {
        setDeck("A", "d1", "x" to 1, "y" to 1); settle("A", "B")
        setDeck("A", "d1", "x" to 1); settle("A")
        setDeck("A", "d1", "x" to 1, "y" to 1); pass("A")
        settle("A", "B")
        assertEquals("x1,y1", show("A"))
        assertEquals("x1,y1", show("B"))
        dev("B").decks = dev("B").decks.filter { it.id != "d1" }; settle("B", "A")
        assertEquals("(none)", show("A"))
        assertEquals("(deleted)", server())
    }

    @Test
    fun `a row whose push committed late is still pulled`() {
        setDeck("A", "d1", "x" to 1); settle("A", "B")
        setDeck("B", "d2", "q" to 1); pass("B"); pass("B")
        val row = server.rows.getValue("deck:d1")
        val late = deck("d1", listOf("x" to 1, "late" to 1))
        server.rows["deck:d1"] = row.copy(
            data = localMoshi.adapter(Deck::class.java).toJson(late), editedMs = time + 5_000, serverUpdatedAt = server.stamp(server.clock - 3)
        )
        settle("B")
        assertEquals("x1,late1", show("B"))
    }

    @Test
    fun `a partly skipped push, then another device building on the part that landed`() {
        setDeck("A", "d1", "x" to 1); setDeck("A", "d2", "q" to 1); settle("A", "B")
        setDeck("A", "d1", "x" to 2); setDeck("A", "d2", "q" to 1, "r" to 1)
        recordEdits("A")
        server.beforePush = { setDeck("B", "d2", "q" to 1, "s" to 1); pass("B") }
        pass("A")
        pass("B"); setDeck("B", "d1", *(cards("B") + ("y" to 1)).toTypedArray()); pass("B")
        settle("A", "B")
        assertEquals("x2,y1", show("A"))
        assertEquals("x2,y1", server())
        assertEquals("q1,r1,s1", sorted(show("A", "d2")))
    }

    @Test
    fun `a lost push answer, then another device building on it, counts once`() {
        assumeTrue("needs push_library_items_v2 (compare-and-swap)", cas)
        setDeck("A", "d1", "x" to 1); settle("A", "B")
        setDeck("A", "d1", "x" to 2); server.loseResponse = true; pass("A")
        pass("B"); setDeck("B", "d1", *(cards("B") + ("y" to 1)).toTypedArray()); pass("B")
        settle("A", "B")
        assertEquals("x2,y1", show("A"))
        assertEquals("x2,y1", server())
    }

    @Test
    fun `a lost push answer on a single device counts once`() {
        setDeck("A", "d1", "x" to 1); settle("A")
        setDeck("A", "d1", "x" to 2); server.loseResponse = true; pass("A")
        settle("A")
        assertEquals("x2", show("A"))
        assertEquals("x2", server())
    }

    @Test
    fun `two devices syncing the same deck at once keep both edits`() {
        assumeTrue("needs push_library_items_v2 (compare-and-swap)", cas)
        setDeck("A", "d1", "x" to 1); settle("A", "B")
        setDeck("A", "d1", "x" to 1, "a" to 1); setDeck("B", "d1", "x" to 1, "b" to 1)
        recordEdits("A"); recordEdits("B")
        val pulledA = pullPhase("A")
        val pulledB = pullPhase("B")
        pushPhase("A", pulledA)
        pushPhase("B", pulledB)
        settle("A", "B")
        assertEquals("a1,b1,x1", sorted(show("A")))
        assertEquals("a1,b1,x1", sorted(server()))
    }

    @Test
    fun `two devices merging onto the same timestamp keep both edits`() {
        assumeTrue("needs push_library_items_v2 (compare-and-swap)", cas)
        setDeck("C", "d1", "x" to 1); settle("C", "A", "B")
        dev("A").skew = -120_000; dev("B").skew = -120_000
        setDeck("A", "d1", "x" to 1, "a" to 1); setDeck("B", "d1", "x" to 1, "b" to 1)
        recordEdits("A"); recordEdits("B")
        setDeck("C", "d1", "x" to 1, "c" to 1); pass("C")
        val pulledB = pullPhase("B")
        pass("A")
        pushPhase("B", pulledB)
        settle("A", "B", "C")
        assertEquals("a1,b1,c1,x1", sorted(server()))
    }

    @Test
    fun `the same deck already on two devices, synced for the first time at once, keeps both`() {
        setDeck("A", "d1", "x" to 1, "a" to 1); setDeck("B", "d1", "x" to 1, "b" to 1)
        setDeck("B", "d9", "z" to 1)
        val pulledB = pullPhase("B")
        pass("A")
        pushPhase("B", pulledB)
        settle("A", "B")
        assertEquals("a1,b1,x1", sorted(show("B")))
        assertEquals("a1,b1,x1", sorted(server()))
    }

    @Test
    fun `reading back our own write keeps the card order`() {
        setDeck("A", "d1", "x" to 1); settle("A")
        setDeck("A", "d1", "x" to 1, "z" to 1, "a" to 1); pass("A")
        assertEquals("x1,z1,a1", show("A"))
    }

    @Test
    fun `a deletion re-read from the overlap stays deleted`() {
        setDeck("A", "d1", "x" to 1); settle("A", "B")
        dev("B").decks = emptyList(); pass("B")
        pass("A"); pass("A")
        assertEquals("(none)", show("A"))
        assertEquals("(deleted)", server())
    }

    @Test
    fun `a row this version can't read doesn't hold the cursor back, and is read again until it can`() {
        setDeck("A", "d1", "x" to 1); settle("A")
        server.clock++
        server.rows["deck:odd"] = RemoteRow("deck", "odd", "{not a deck", time, false, server.stamp(server.clock))
        setDeck("B", "d2", "q" to 1); pass("B")
        pass("A")
        assertEquals(listOf("deck:odd"), dev("A").state.refetch)
        assertEquals(server.rows.values.maxOf { it.serverUpdatedAt }, dev("A").state.cursor)
        assertEquals("q1", show("A", "d2"))
        // An update that can read it (here: the row becomes readable) picks it up by key.
        server.rows["deck:odd"] = server.rows.getValue("deck:odd").copy(data = localMoshi.adapter(Deck::class.java).toJson(deck("odd", listOf("o" to 1))))
        pass("A")
        assertEquals("o1", show("A", "odd"))
        assertEquals(emptyList<String>(), dev("A").state.refetch)
    }

    // ---- Signing out and back in ----

    private val deckAdapter = localMoshi.adapter(Deck::class.java)

    /** A session ending on its own: kept edits captured, then the library and its bookkeeping removed. */
    private fun sessionEnds(name: String): Rescue? {
        val d = dev(name)
        val rescue = core.captureRescue(d.state, core.localJson(d.decks, emptyList()), "u", now(name))
        d.decks = emptyList()
        d.state = CloudSyncState(userId = "u")
        return rescue
    }

    /** Signing back in: the first pass pulls the account's library, then the kept edits go back in. */
    private fun signBackIn(name: String, rescue: Rescue?) {
        pass(name)
        if (rescue != null) dev(name).decks = rescueDecks(dev(name).decks, rescue, deckAdapter)
        pass(name)
    }

    @Test
    fun `an edit that hadn't synced when the session ended is put back on signing in`() {
        setDeck("A", "d1", "x" to 1); settle("A", "B")
        setDeck("A", "d1", "x" to 2) // not synced yet...
        val rescue = sessionEnds("A") // ...when the server ends the session
        assertEquals(setOf("deck:d1"), rescue?.items?.keys)
        setDeck("B", "d1", "x" to 1, "y" to 1); pass("B") // meanwhile, on another device
        signBackIn("A", rescue)
        settle("A", "B")
        assertEquals("x2,y1", show("A"))
        assertEquals("x2,y1", show("B"))
        assertEquals("x2,y1", server())
    }

    @Test
    fun `a deletion kept through a sign-out goes through, unless the deck changed elsewhere meanwhile`() {
        setDeck("A", "d1", "x" to 1); setDeck("A", "d2", "q" to 1); settle("A", "B")
        dev("A").decks = emptyList()
        val rescue = sessionEnds("A")
        setDeck("B", "d2", "q" to 1, "r" to 1); pass("B") // d2 edited elsewhere; d1 untouched
        signBackIn("A", rescue)
        settle("A", "B")
        assertEquals("(none)", show("A", "d1"))
        assertEquals("(deleted)", server("d1"))
        assertEquals("q1,r1", show("A", "d2"))
    }

    @Test
    fun `a removal cut short pushes no deletions on signing in again`() {
        setDeck("A", "d1", "x" to 1); setDeck("A", "d2", "q" to 1); settle("A")
        // SupabaseSync marks the removal before emptying the library; the app is killed halfway.
        dev("A").state = CloudSyncState(userId = "(removing)")
        dev("A").decks = dev("A").decks.filter { it.id != "d1" }
        // Next start: the marker isn't this account's bookkeeping, so the removal is finished first.
        assertEquals(true, belongsElsewhere(dev("A").state, "u"))
        dev("A").decks = emptyList()
        dev("A").state = CloudSyncState(userId = "u")
        settle("A")
        assertEquals("x1", server("d1"))
        assertEquals("q1", server("d2"))
        assertEquals("x1", show("A", "d1"))
    }

    @Test
    fun `whose library is it`() {
        assertEquals(false, belongsElsewhere(CloudSyncState(userId = null), "u")) // never synced: the device's own
        assertEquals(false, belongsElsewhere(CloudSyncState(userId = "u"), "u"))
        assertEquals(true, belongsElsewhere(CloudSyncState(userId = "v"), "u"))
        assertEquals(true, belongsElsewhere(CloudSyncState(userId = "v"), null)) // signed out, yet an account's library
        assertEquals(true, belongsElsewhere(CloudSyncState(userId = "(removing)"), "u"))
    }

    @Test
    fun `kept edits survive being saved and read back`() {
        val adapter = localMoshi.adapter(Rescue::class.java)
        val rescue = Rescue("u", 123L, mapOf("deck:d1" to RescueItem("{\"id\":\"d1\"}", "{}"), "deck:d2" to RescueItem(null, null)))
        assertEquals(rescue, adapter.fromJson(adapter.toJson(rescue)))
    }
}
