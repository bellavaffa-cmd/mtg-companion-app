package com.mtgcompanion.app.data.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Anonymous usage counts — the same cases as the web app's tests/usage/usageCounts.test.ts. */
class UsageCountsTest {

    private val idA = "a".repeat(32)
    private val idB = "b".repeat(32)
    private fun ids(vararg list: String): () -> String {
        var i = 0
        return { list.getOrNull(i++) ?: "f".repeat(32) }
    }

    @Test fun `counts per day per event, and only events on the list`() {
        var s = UsageState()
        val next = ids(idA)
        s = UsageCounts.record(s, "deck_created", "2026-10-01", true, next)
        s = UsageCounts.record(s, "deck_created", "2026-10-01", true, next)
        s = UsageCounts.record(s, "screen_decks", "2026-10-01", true, next)
        s = UsageCounts.record(s, "Lightning Bolt", "2026-10-01", true, next)
        s = UsageCounts.record(s, "deck_created", "2026-10-02", true, next)
        assertEquals(
            listOf(
                UsageBucket("2026-10-01", idA, mapOf("deck_created" to 2, "screen_decks" to 1)),
                UsageBucket("2026-10-02", idA, mapOf("deck_created" to 1))
            ),
            s.buckets
        )
    }

    @Test fun `off counts nothing`() {
        assertEquals(UsageState(), UsageCounts.record(UsageState(), "deck_created", "2026-10-01", false, ids(idA)))
    }

    @Test fun `a count stops at the most the server takes`() {
        var s = UsageState(idA, "2026-10-01", "", listOf(UsageBucket("2026-10-01", idA, mapOf("card_scanned" to UsageCounts.MAX_COUNT))))
        s = UsageCounts.record(s, "card_scanned", "2026-10-01", true, ids())
        assertEquals(UsageCounts.MAX_COUNT, s.buckets[0].counts["card_scanned"])
    }

    @Test fun `the install id is made once, kept 89 days and replaced on the 90th, old days keep their id`() {
        var s = UsageCounts.record(UsageState(), "game_started", "2026-01-01", true, ids(idA, idB))
        assertEquals(idA, s.install)
        s = UsageCounts.record(s, "game_started", "2026-03-31", true, ids(idB)) // day 89
        assertEquals(idA, s.install)
        s = UsageCounts.record(s, "game_started", "2026-04-01", true, ids(idB)) // day 90
        assertEquals(idB, s.install)
        assertEquals(listOf("2026-03-31" to idA, "2026-04-01" to idB), s.buckets.map { it.day to it.install })
    }

    @Test fun `days more than a week old are dropped unsent`() {
        val s = UsageState(
            idA, "2026-10-01", "",
            listOf(
                UsageBucket("2026-10-01", idA, mapOf("deck_created" to 1)), // 8 days before
                UsageBucket("2026-10-02", idA, mapOf("deck_created" to 2)) // 7 days before
            )
        )
        assertEquals(listOf(UsageBatch("2026-10-02", idA, mapOf("deck_created" to 2))), UsageCounts.dueBatches(s, "2026-10-09", true))
        val after = UsageCounts.record(s, "deck_created", "2026-10-09", true, ids())
        assertEquals(listOf("2026-10-02", "2026-10-09"), after.buckets.map { it.day })
    }

    @Test fun `only finished days are sent, one try a day, none when off`() {
        var s = UsageCounts.record(UsageState(), "card_scanned", "2026-10-01", true, ids(idA))
        s = UsageCounts.record(s, "card_scanned", "2026-10-02", true, ids())
        assertEquals(emptyList<UsageBatch>(), UsageCounts.dueBatches(s, "2026-10-01", true))
        assertEquals(listOf(UsageBatch("2026-10-01", idA, mapOf("card_scanned" to 1))), UsageCounts.dueBatches(s, "2026-10-02", true))
        assertEquals(emptyList<UsageBatch>(), UsageCounts.dueBatches(s, "2026-10-02", false))
        val tried = UsageCounts.markTried(s, "2026-10-02")
        assertEquals(emptyList<UsageBatch>(), UsageCounts.dueBatches(tried, "2026-10-02", true))
        // It failed (offline, or no record_usage yet): tomorrow tries again with both days.
        assertEquals(2, UsageCounts.dueBatches(tried, "2026-10-03", true).size)
    }

    @Test fun `at most 100 events a send`() {
        val counts = (0 until 150).associate { i -> "e${'a' + (i % 26)}$i" to 1 }
        val s = UsageState(idA, "2026-10-01", "", listOf(UsageBucket("2026-10-01", idA, counts)))
        assertEquals(listOf(100, 50), UsageCounts.dueBatches(s, "2026-10-02", true).map { it.counts.size })
    }

    @Test fun `a day sent is forgotten`() {
        var s = UsageCounts.record(UsageState(), "loan_created", "2026-10-01", true, ids(idA))
        s = UsageCounts.record(s, "loan_created", "2026-10-02", true, ids())
        val batch = UsageCounts.dueBatches(s, "2026-10-02", true).first()
        s = UsageCounts.markSent(UsageCounts.markTried(s, "2026-10-02"), batch)
        assertEquals(listOf(UsageBucket("2026-10-02", idA, mapOf("loan_created" to 1))), s.buckets)
        assertEquals(listOf("2026-10-02"), UsageCounts.dueBatches(s, "2026-10-03", true).map { it.day })
    }

    @Test fun `turning it off forgets everything, install id included`() {
        assertEquals(UsageState("", "", "", emptyList()), UsageCounts.optedOut())
    }

    @Test fun `a stored state is read back, and what doesn't fit is dropped`() {
        val s = UsageCounts.record(UsageState(), "event_started", "2026-10-01", true, ids(idA))
        assertEquals(s, UsageCounts.parse(UsageCounts.toJson(s)))
        val bad = UsageCounts.toJson(
            s.copy(
                buckets = s.buckets + UsageBucket("yesterday", idA, mapOf("deck_created" to 1)) +
                    UsageBucket("2026-10-01", idB, mapOf("Black Lotus" to 3, "deck_created" to -1))
            )
        )
        assertEquals(s.buckets, UsageCounts.parse(bad).buckets)
        assertEquals(UsageState(), UsageCounts.parse("not json"))
        assertEquals(UsageState(), UsageCounts.parse(null))
    }

    @Test fun `event names fit the server's rule`() {
        assertEquals(12, UsageAction.entries.size)
        UsageAction.entries.forEach { assertTrue(it.id, Regex("^[a-z_]{1,40}$").matches(it.id)) }
        assertTrue(UsageCounts.isEvent("screen_settings_section"))
        assertFalse(UsageCounts.isEvent("screen_nowhere"))
        assertTrue(Regex("^[0-9a-f]{32}$").matches(UsageCounts.newInstallId { "0123ABCD-0123-4567-89ab-0123456789ab" }))
        assertEquals(1L, UsageCounts.daysBetween("2026-12-31", "2027-01-01"))
    }

    @Test fun `routes are counted as their screen, without arguments`() {
        assertEquals("home", UsageCounts.screenOfRoute("home"))
        assertEquals("deck", UsageCounts.screenOfRoute("deck/{deckId}?tab={tab}"))
        assertEquals("new_deck", UsageCounts.screenOfRoute("new_deck"))
        assertEquals("pull_list", UsageCounts.screenOfRoute("pull_list/{deckId}?place={place}"))
        assertEquals("collection", UsageCounts.screenOfRoute("collection"))
        assertEquals("collection_detail", UsageCounts.screenOfRoute("collection/{collectionId}"))
        assertEquals("card", UsageCounts.screenOfRoute("detail/{cardName}"))
        assertEquals("settings_section", UsageCounts.screenOfRoute("settings/{section}"))
        assertEquals("scan_tick", UsageCounts.screenOfRoute("scan_tick/{deckId}/{kind}"))
        assertEquals("lend", UsageCounts.screenOfRoute("lend?card={card}&place={place}"))
        assertEquals("life_counter", UsageCounts.screenOfRoute("life_counter"))
        assertNull(UsageCounts.screenOfRoute("tester"))
        assertNull(UsageCounts.screenOfRoute(null))
    }
}
