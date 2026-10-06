package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.ItemMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The deck history (DeckHistory.kt). The web app's tests/decks/deckHistory.test.ts runs the same cases. */
class DeckHistoryTest {
    private fun entry(name: String, quantity: Int = 1, id: String = "id-$name") = DeckCardEntry(scryfallId = id, name = name, imageUrl = null, quantity = quantity)
    private fun names(vararg ns: String) = ns.map { entry(it) }
    private fun deck(cards: List<DeckCardEntry>, ownership: DeckOwnership = DeckOwnership.VIRTUAL) =
        Deck("d", "Deck", cards = cards, createdAt = 0, ownership = ownership.name)
    private var n = 0
    private fun ctx(now: Long, dev: String = "phone1", from: String = "android", value: ((Deck) -> Double?)? = null) =
        HistoryContext(now, from, dev, { "e${++n}" }, value)
    private val min = 60_000L

    private fun edit(d: Deck, cards: List<DeckCardEntry>, now: Long, dev: String = "phone1", from: String = "android") =
        withHistory(d, d.copy(cards = cards), ctx(now, dev, from))

    private fun line(n: String, q: Int = 1) = HistoryLine(n, q)

    @Test
    fun `a list change on a deck from before the history saves the old list first, then the change`() {
        val d = edit(deck(names("A", "B")), names("A", "B", "C"), 10_000)
        val h = d.history!!
        assertEquals(2, h.size)
        assertEquals("start", h[0].kind)
        assertEquals(mapOf("A" to 1, "B" to 1), h[0].list)
        assertEquals(listOf(line("C")), h[1].add)
        assertNull(h[1].cut)
        assertEquals("phone1", h[1].dev)
    }

    @Test
    fun `a change that leaves the list alone records nothing`() {
        val before = deck(names("A"))
        val after = before.copy(tags = listOf("x"), cards = listOf(entry("A", 1, "other-printing")))
        assertSame(after, withHistory(before, after, ctx(1)))
    }

    @Test
    fun `a whole list into an empty deck is an import, one card is just an edit`() {
        val d = withHistory(null, deck(names("A", "B", "C")), ctx(5))
        assertEquals(1, d.history!!.size)
        assertEquals("import", d.history!![0].kind)
        assertEquals(mapOf("A" to 1, "B" to 1, "C" to 1), d.history!![0].list)
        val one = withHistory(deck(emptyList()), deck(names("A")), ctx(5))
        assertEquals(1, one.history!!.size)
        assertNull(one.history!![0].kind)
        assertEquals(mapOf("A" to 1), one.history!![0].list)
    }

    @Test
    fun `edits on one device within ten minutes are one entry, later or on another device a new one`() {
        var d = edit(deck(names("A", "B")), names("A", "B", "C"), 0)
        d = edit(d, names("A", "C", "D"), 5 * min)
        assertEquals(2, d.history!!.size)
        assertEquals(listOf(line("C"), line("D")), d.history!![1].add)
        assertEquals(listOf(line("B")), d.history!![1].cut)
        assertEquals(5 * min, d.history!![1].at)
        // Undone within the sitting: C goes again, so only D in and B out are left.
        d = edit(d, names("A", "D"), 6 * min)
        assertEquals(listOf(line("D")), d.history!![1].add)
        d = edit(d, names("A", "D", "E"), 7 * min, "browser1", "web")
        assertEquals(3, d.history!!.size)
        d = edit(d, names("A", "D", "E", "F"), 8 * min)
        assertEquals(4, d.history!!.size)
        d = edit(d, names("A", "D", "E", "F", "G"), 8 * min + HISTORY_COALESCE_MILLIS)
        assertEquals(5, d.history!!.size)
    }

    @Test
    fun `a game logged since the last entry closes it`() {
        var d = edit(deck(names("A")), names("A", "B"), 0)
        d = d.copy(gameResults = listOf(GameResult("g", "WIN", playedAt = min)))
        d = edit(d, names("A", "B", "C"), 2 * min)
        assertEquals(3, d.history!!.size)
    }

    @Test
    fun `the list at any point is rebuilt from the nearest whole list`() {
        var d = deck(names("A"))
        for (i in 0 until HISTORY_SNAPSHOT_EVERY * 2 + 3) d = edit(d, d.cards + entry("X$i"), i * HISTORY_COALESCE_MILLIS * 2)
        val h = sortedHistory(d.history!!)
        val withList = h.count { it.list != null }
        assertTrue(withList in 3..4)
        assertEquals(listStateOf(d), statesThrough(h).last())
        val at5 = stateAt(h, h[5].id)!!
        assertEquals(listOf("A", "X0", "X1", "X2", "X3", "X4"), at5.cards.keys.sorted())
        assertNull(stateAt(h, "nope"))
    }

    @Test
    fun `commander changes are kept and replayed`() {
        val meren = entry("Meren")
        var d = withHistory(deck(listOf(meren, entry("B"))).copy(commander = meren), deck(listOf(meren, entry("B"), entry("C"))).copy(commander = meren), ctx(0))
        val k = entry("Karador")
        d = withHistory(d, d.copy(cards = d.cards + k, commander = k), ctx(HISTORY_COALESCE_MILLIS * 2))
        assertEquals(listOf("Karador"), sortedHistory(d.history!!).last().cmd)
        val items = historyItems(d)
        assertEquals(listOf("Karador"), items[0].commanders)
        assertNull(items[1].commanders)
    }

    @Test
    fun `a list changed by an app with no history becomes one synced entry before the next change`() {
        var d = edit(deck(names("A")), names("A", "B"), 0)
        d = d.copy(cards = names("B"))
        d = edit(d, names("B", "C"), 20 * min)
        val h = sortedHistory(d.history!!)
        assertEquals("synced", h[h.size - 2].kind)
        assertEquals(listOf(line("A")), h[h.size - 2].cut)
        assertEquals(listStateOf(d), statesThrough(h).last())
    }

    @Test
    fun `the value before and after is kept when known`() {
        val value = { x: Deck -> x.cards.size * 10.0 }
        var d = withHistory(deck(names("A")), deck(names("A", "B")), ctx(0, "p", "android", value))
        assertEquals(10.0, d.history!![1].v0!!, 0.0)
        assertEquals(20.0, d.history!![1].v1!!, 0.0)
        d = withHistory(d, d.copy(cards = names("A", "B", "C")), ctx(min, "p", "android") { null })
        assertEquals(10.0, d.history!![1].v0!!, 0.0)
        assertEquals(20.0, d.history!![1].v1!!, 0.0)
    }

    @Test
    fun `diff - in it then, not now, and added since`() {
        val then = ListState(mapOf("A" to 2, "B" to 1, "C" to 1), emptyList())
        val now = ListState(mapOf("A" to 1, "C" to 1, "D" to 3), emptyList())
        val (gone, added) = versionDiff(then, now)
        assertEquals(listOf(line("A"), line("B")), gone)
        assertEquals(listOf(line("D", 3)), added)
        assertEquals(emptyList<HistoryLine>() to emptyList<HistoryLine>(), diffStates(now, now))
    }

    @Test
    fun `merging two devices' histories keeps every entry once, the newer copy where both have it`() {
        val base = edit(deck(names("A")), names("A", "B"), 0)
        val phone = edit(base, names("A", "B", "C"), 20 * min)
        val web = edit(base, names("A", "B", "D"), 21 * min, "b1", "web")
        val merged = mergeHistory(phone.history, web.history)!!
        assertEquals(4, merged.size)
        assertEquals(4, merged.map { it.id }.toSet().size)
        assertEquals(merged, mergeHistory(web.history, phone.history))
        val phoneLater = edit(phone, names("A", "B", "C", "E"), 22 * min)
        val again = mergeHistory(phoneLater.history, merged)!!
        assertEquals(4, again.size)
        assertEquals(listOf(line("C"), line("E")), again.first { it.id == phoneLater.history!!.last().id }.add)
        assertNull(mergeHistory(null, null))
    }

    @Test
    fun `a deck merge keeps both histories, and one saved by an older app keeps this device's`() {
        val base = edit(deck(names("A")), names("A", "B"), 0)
        val mine = edit(base, names("A", "B", "C"), 20 * min)
        val older = base.copy(cards = names("A", "B", "D"), history = null)
        val merged = ItemMerge.mergeDecks(base, mine, older, minePreferred = true)
        assertEquals(mine.history, merged.history)
        assertEquals(listOf("A", "B", "C", "D"), merged.cards.map { it.name }.sorted())
        assertSame(mine.history, keepHistoryFromOlderApp(mine, older).history)
        assertEquals(emptyList<DeckHistoryEntry>(), keepHistoryFromOlderApp(mine, base.copy(history = emptyList())).history)
    }

    @Test
    fun `the cap keeps the last entries and every named version, and the list can still be rebuilt`() {
        var d = deck(names("A"))
        d = withNamedVersion(withHistory(deck(emptyList()), d, ctx(0)), "First", "", ctx(1))
        for (i in 0 until HISTORY_MAX_ENTRIES + 30) d = edit(d, d.cards + entry("X$i"), (i + 1) * HISTORY_COALESCE_MILLIS * 2)
        val h = d.history!!
        assertEquals(HISTORY_MAX_ENTRIES, h.count { it.kind != "named" })
        assertEquals(1, h.count { it.kind == "named" })
        assertEquals(listStateOf(d), statesThrough(sortedHistory(h)).last())
        assertNotNull(h.first { it.kind != "named" }.list)
        assertEquals(h, capHistory(h))
    }

    @Test
    fun `entries over a year older than the newest go`() {
        val old = DeckHistoryEntry("old", 0, list = mapOf("A" to 1), cmd = emptyList())
        val named = DeckHistoryEntry("n", 1, kind = "named", name = "Kept", list = mapOf("A" to 1), cmd = emptyList())
        val recent = DeckHistoryEntry("new", 400L * 24 * 3600 * 1000, add = listOf(line("B")))
        assertEquals(listOf("n", "new"), capHistory(listOf(old, named, recent)).map { it.id })
    }

    @Test
    fun `a deck from before the history shows its saved versions`() {
        val versions = listOf(
            DeckVersion("baseline:1", 100, mapOf("A" to 1, "B" to 1), emptyList()),
            DeckVersion("2", 200, mapOf("A" to 1, "C" to 1), emptyList())
        )
        val h = historyFromVersions(versions)
        assertEquals("start", h[0].kind)
        assertEquals(listOf(line("C")), h[1].add)
        assertEquals(listOf(line("B")), h[1].cut)
        assertEquals(2, historyItems(deck(names("A", "C")).copy(versions = versions)).size)
        val d = edit(deck(names("A", "C")).copy(versions = versions), names("A", "C", "D"), 300)
        assertEquals(listOf("v:baseline:1", "v:2"), d.history!!.map { it.id }.take(2))
    }

    @Test
    fun `the History screen - newest first, games counted for the list they were played with`() {
        var d = edit(deck(names("A")), names("A", "B"), 0)
        d = withNamedVersion(d, " Before game night ", "Tuned", ctx(min))
        d = d.copy(gameResults = listOf(
            GameResult("g1", "WIN", playedAt = 2 * min),
            GameResult("g2", "LOSS", playedAt = 3 * min),
            GameResult("g3", "WIN", playedAt = 4 * min)
        ))
        d = edit(d, names("A", "B", "C"), 30 * min)
        val items = historyItems(d)
        assertEquals(4, items.size)
        assertTrue(items[0].latest)
        assertEquals("named", items[1].entry.kind)
        assertEquals("Before game night", items[1].entry.name)
        assertEquals("Tuned", items[1].entry.note)
        assertEquals("Went 2–1 with this list.", recordLine(items[1]))
        assertEquals("", recordLine(items[0]))
    }

    @Test
    fun `card lines - one card by name, more as a count and names`() {
        assertEquals("+ Sheoldred, the Apocalypse" to "", linesText("+", listOf(line("Sheoldred, the Apocalypse"))))
        assertEquals("− 3" to "Cultivate, 2 Swamp", linesText("−", listOf(line("Cultivate"), line("Swamp", 2))))
        assertEquals("A, B, C, D, E, …", linesText("+", listOf("A", "B", "C", "D", "E", "F").map { line(it) }).second)
    }

    @Test
    fun `where an entry was made`() {
        assertEquals("this phone", sourceText(DeckHistoryEntry("1", 0, from = "android", dev = "p1"), "android", "p1"))
        assertEquals("another phone", sourceText(DeckHistoryEntry("1", 0, from = "android", dev = "p2"), "android", "p1"))
        assertEquals("manabind.com", sourceText(DeckHistoryEntry("1", 0, from = "web", dev = "b1"), "android", "p1"))
        assertEquals("phone", sourceText(DeckHistoryEntry("1", 0, from = "android", dev = "p2"), "web", "b1"))
        assertEquals("named", sourceText(DeckHistoryEntry("1", 0, kind = "named", from = "web", dev = "b1"), "android", "p1"))
        assertEquals("imported", sourceText(DeckHistoryEntry("1", 0, kind = "import"), "android", "p1"))
    }

    @Test
    fun `going back - cards cut, cards back (proxies in a physical deck), and the ones to fetch`() {
        val now = deck(listOf(entry("A", 2), entry("B"), entry("C")), DeckOwnership.PHYSICAL)
        val then = ListState(mapOf("A" to 1, "B" to 1, "D" to 1, "E" to 2), emptyList())
        val known = { name: String -> if (name == "D") entry("D", 4) else null }
        val plan = restoreList(now, then, known)
        assertEquals(listOf(Triple("A", 1, null), Triple("B", 1, null), Triple("D", 1, 1)), plan.deck.cards.map { Triple(it.name, it.quantity, it.proxyQuantity) })
        assertEquals(listOf("A" to 1, "C" to 0), plan.cuts.map { it.first.name to it.second })
        assertEquals(1, plan.incoming)
        assertEquals(listOf(line("E", 2)), plan.missing)
        val virtual = restoreList(now.copy(ownership = DeckOwnership.VIRTUAL.name), then, known)
        assertNull(virtual.deck.cards.first { it.name == "D" }.proxyQuantity)
    }

    @Test
    fun `going back sets the commander from then, and is recorded as one entry keeping today's list`() {
        val meren = entry("Meren")
        val k = entry("Karador")
        val before = deck(listOf(k, meren, entry("B"))).copy(commander = k)
        val plan = restoreList(before, ListState(mapOf("Meren" to 1, "B" to 1), listOf("Meren")), { null })
        assertEquals("Meren", plan.deck.commander!!.name)
        var d = edit(deck(listOf(meren, entry("B"))).copy(commander = meren), listOf(meren, entry("B"), k), 0)
        d = d.copy(commander = k)
        val restored = withRestore(d, plan.deck.copy(history = d.history), 5, ctx(min))
        val last = sortedHistory(restored.history!!).last()
        assertEquals("restore", last.kind)
        assertEquals(5L, last.to)
        assertEquals(listOf(line("Karador")), last.cut)
        assertSame(restored, withHistory(d, restored, ctx(min)))
        val states = statesThrough(sortedHistory(restored.history!!))
        assertEquals(mapOf("Meren" to 1, "B" to 1, "Karador" to 1), states[states.size - 2].cards)
    }
}
