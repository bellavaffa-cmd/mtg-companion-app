package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.ItemMerge
import com.mtgcompanion.app.ui.collection.allCardEntries
import com.mtgcompanion.app.ui.collection.copyFactsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Speed guards over a 25,000-copy collection (BigCollection): each hot path has to stay well inside a
 * generous limit — CI machines vary, so the limits are several times what a laptop takes. What they
 * catch is something going quadratic again: re-tagging every copy once per tagged printing on each
 * write, a place's path worked out again for every copy, Upkeep reading the whole collection once per
 * place. Each test prints its timing. The web app has the same guards — see
 * MtgCompanionWeb/tests/perf/bigCollection.test.ts.
 */
class BigCollectionPerfTest {
    private val lib = BigCollection.build()
    private val all = allCardEntries(lib.collections, lib.decks)

    private fun under(label: String, limitMs: Long, runs: Int = 2, fn: () -> Any?) {
        val (ms, _) = BigCollection.timed(runs, fn)
        println("$label: $ms ms")
        assertTrue("$label took $ms ms (limit $limitMs ms)", ms < limitMs)
    }

    @Test fun `the big collection is 25,000 copies of thousands of printings, in many places and decks`() {
        assertEquals(25_000, BigCollection.ownedCopies(lib))
        assertEquals(BigCollection.PRINTINGS, all.size)
        assertEquals(80, placesOf(lib.collections).size)
        assertEquals(60, lib.decks.size)
        assertEquals(25_000, storageSummary(lib.collections, lib.decks).total)
        assertEquals("the same every time", lib, BigCollection.build())
    }

    @Test fun `the same library as the web app's`() {
        // Draws from the same random source, in the same order: the first few the web app makes.
        val r = BigCollection.random(8)
        assertEquals(listOf(0.1563, 0.6252, 0.3007), List(3) { Math.round(r() * 10_000) / 10_000.0 })
        assertEquals("p-00001-9e3779b1", BigCollection.printingId(1))
        assertEquals("Golmika Golem", lib.collections.first { it.id == "binder-col-0" }.entries.first().name)
        assertEquals(listOf(CopyPlace("box-5-1", 1, section = "White")), lib.collections[0].entries[5].places)
        assertEquals("p-12353-92e715f1", lib.decks[3].cards[7].scryfallId)
    }

    @Test fun `All cards and its filters' facts stay quick`() {
        under("allCardEntries", 1500) { allCardEntries(lib.collections, lib.decks) }
        under("copyFactsOf", 1500) { copyFactsOf(lib.collections, lib.decks) }
        under("spares", 1500) { spares(lib.collections, lib.decks) }
        under("spreadThin", 1500) { spreadThin(lib.collections, lib.decks) }
    }

    @Test fun `Storage, Upkeep and Value by place stay quick`() {
        under("storageSummary", 1500) { storageSummary(lib.collections, lib.decks) }
        under("upkeep", 3000) { upkeep(lib.collections, lib.decks, now = 1_790_000_000_000L, today = "2026-10-07") }
        val facts = { id: String -> PrintingFacts("s01", "1", 1.5, 3.0).takeIf { id.startsWith("p-") } }
        under("value by place", 3000) { valueGroups(valueRows(lib.collections, lib.decks, facts), lib.collections) }
    }

    @Test fun `every write's re-tagging stays quick, and leaves tagged copies as they are`() {
        val ledger = rememberedUserTags(emptyMap(), lib.decks, lib.collections)
        assertTrue(ledger.size > 300)
        under("withRememberedUserTags (decks)", 1000) { lib.decks.withRememberedUserTags(ledger) }
        under("withRememberedUserTagsIn (binders)", 1000) { lib.collections.withRememberedUserTagsIn(ledger) }
        val tagged = lib.collections.withRememberedUserTagsIn(ledger)
        assertSame("nothing to change: the same binders", tagged[0], tagged.withRememberedUserTagsIn(ledger)[0])
    }

    @Test fun `sync merges and the library's JSON stay quick`() {
        val pile = lib.collections[0]
        val mine = pile.copy(entries = pile.entries.mapIndexed { i, e -> if (i % 10 == 0) e.copy(quantity = e.quantity + 1) else e })
        val theirs = pile.copy(entries = pile.entries.filterIndexed { i, _ -> i % 13 != 0 })
        under("mergeCollections (6,000-entry pile)", 3000, runs = 1) { ItemMerge.mergeCollections(pile, mine, theirs, minePreferred = true) }
        val adapter = localMoshi.adapter(CollectionStore::class.java)
        var json = ""
        under("binders to JSON", 3000) { json = adapter.toJson(CollectionStore(lib.collections)); json }
        under("binders from JSON", 3000) { adapter.fromJson(json) }
    }

    @Test fun `backup - writing, reading and restoring 25,000 copies stay quick`() {
        val backup = buildBackup(lib.decks, lib.collections, createdAt = 1L)
        var text = ""
        under("writing the backup", 4000) { text = backupJson(backup); text }
        var parsed: ParsedBackup? = null
        under("reading the backup", 4000) { parsed = parseBackup(text); parsed }
        val read = (parsed as ParsedBackup.Ok).backup
        val changed = lib.collections.mapIndexed { i, c -> if (i == 3) c.copy(entries = c.entries.drop(1)) else c }
        under("merge restore", 5000, runs = 1) { restoreCollections(changed, read, RestoreMode.MERGE) }
    }
}
