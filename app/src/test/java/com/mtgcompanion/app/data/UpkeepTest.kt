package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/**
 * Upkeep: the share of copies with a place and the things worth doing this week. The web app has the
 * same checks — see MtgCompanionWeb/tests/collection/upkeep.test.ts.
 */
class UpkeepTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val day = 86_400_000L
    /** 2026-10-06, noon UTC. */
    private val now = LocalDate.of(2026, 10, 6).toEpochDay() * day + day / 2
    private val today = "2026-10-06"

    private fun entry(id: String, qty: Int, places: List<CopyPlace>? = null) = CollectionEntry(id, id, null, quantity = qty, places = places)

    private val rares = StoragePlace("rares", "Rares binder", PlaceKind.BINDER.name, createdAt = now - 200 * day, lastChecked = now - 120 * day)
    private val red = StoragePlace("red", "Red box", PlaceKind.BOX.name, createdAt = now - 10 * day, capacity = 100)
    private val old = StoragePlace("old", "Old box", PlaceKind.BOX.name, createdAt = now - 100 * day)

    private val pile = Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, listOf(
        entry("ring", 3, listOf(CopyPlace("rares", 1))),
        entry("bolt", 96, listOf(CopyPlace("red", 96))),
        entry("elf", 5, listOf(CopyPlace("old", 5)))
    ), createdAt = 0, storagePlaces = listOf(rares, red, old), loans = listOf(
        Loan("l1", "Priya", cards = listOf(LoanCard("Goblin", "gob", qty = 4)), lentAt = now - 20 * day, backBy = "2026-10-03")
    ))
    private val trades = Collection("trades", "Trade binder", listOf(entry("gob", 4), entry("cheap", 1)), createdAt = 0)

    private val price = { id: String, _: Boolean -> mapOf("ring" to 2000.0, "gob" to 7.0, "elf" to 0.0)[id] }

    @Test
    fun theThingsWorthDoingThisWeek() {
        val report = upkeep(listOf(pile, trades), emptyList(), now, today, price = price, zone = utc)
        // 105 copies; 96 + 1 + 5 have a place and 4 are lent: 2 ring and 1 cheap have none.
        assertEquals(109, report.total)
        assertEquals(106, report.placed)
        assertEquals(97, report.percent)
        assertEquals(
            listOf(
                "PUT_AWAY|3 copies have no place|Most are in Unsorted|Put away",
                "CHECK|Rares binder not checked in 120 days|$2,000 inside|Check",
                "REMIND|Priya's loan is 3 days late|4 cards · $28|Remind",
                "SPLIT|Red box is 96% full|Room for about 4 more|Split"
            ),
            report.items.map { "${it.kind}|${it.title}|${it.detail}|${it.action}" }
        )
        assertEquals("rares", report.items[1].placeId)
        assertEquals("n:priya", report.items[2].personKey)
        assertEquals("4 things worth doing this week", upkeepHeadline(report.items.size))
        assertEquals("1 thing worth doing this week", upkeepHeadline(1))
        assertEquals("Nothing to do this week", upkeepHeadline(0))
    }

    @Test
    fun aPlaceWithNothingOfValueOrCheckedLatelyIsLeftAlone() {
        val checked = pile.copy(storagePlaces = listOf(rares.copy(lastChecked = now - 30 * day), red.copy(capacity = null), old))
        val report = upkeep(listOf(checked), emptyList(), now, "2026-10-01", price = price, zone = utc)
        assertEquals(listOf(UpkeepKind.PUT_AWAY), report.items.map { it.kind })
        // Never checked, made long ago, with something in it.
        val never = upkeep(listOf(pile.copy(storagePlaces = listOf(rares.copy(lastChecked = null)))), emptyList(), now, today, price = price, zone = utc)
        assertTrue(never.items.any { it.title == "Rares binder never checked" })
    }

    @Test
    fun whereTheCopiesWithNoPlaceCameFrom() {
        val cols = listOf(pile.copy(loans = emptyList()), trades)
        // 2 ring in Unsorted, 4 gob and 1 cheap in Trade binder.
        assertEquals("Most are in Trade binder", unplacedHint(cols, emptyList(), null, now, utc))
        val note = ImportNote(now - 9 * day, 5, "trades")
        assertEquals("Most came from last week's import", unplacedHint(cols, emptyList(), note, now, utc))
        assertEquals("Most came from today's import", unplacedHint(cols, emptyList(), note.copy(at = now), now, utc))
        assertEquals("Most came from the import on 1 Sep", unplacedHint(cols, emptyList(), note.copy(at = now - 35 * day), now, utc))
        // An import into another binder doesn't explain them.
        assertEquals("Most are in Trade binder", unplacedHint(cols, emptyList(), note.copy(collectionId = "unsorted"), now, utc))
        val even = listOf(pile.copy(loans = emptyList()), trades.copy(entries = listOf(entry("gob", 2))), Collection("c", "Cube", listOf(entry("x", 1)), createdAt = 0))
        assertEquals("In Unsorted, Trade binder and 1 more", unplacedHint(even, emptyList(), null, now, utc))
    }

    @Test
    fun aPullListStartedAndNotFinished() {
        val box = StoragePlace("box", "Box", PlaceKind.BOX.name, createdAt = now)
        val cols = listOf(Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, listOf(
            entry("a", 1, listOf(CopyPlace("box", 1))), entry("b", 1, listOf(CopyPlace("box", 1)))
        ), createdAt = 0, storagePlaces = listOf(box)))
        val deck = Deck("krenko", "Krenko", cards = listOf(DeckCardEntry("a", "a", null), DeckCardEntry("b", "b", null)), ownership = DeckOwnership.PROTOTYPE.name)
        val rows = pullList(deck, cols, listOf(deck)).groups.flatMap { it.rows }
        assertEquals(2, rows.size)
        val started = now - 3 * day
        val pulls = listOf(PullUnderway("krenko", setOf(rows[0].key), started), PullUnderway("gone", setOf("x")))
        val report = upkeep(cols, listOf(deck), now, today, pulls = pulls, zone = utc)
        val weekday = LocalDate.of(2026, 10, 3).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.UK)
        assertEquals(
            listOf("CARRY_ON|Krenko deck pull list half done|1 of 2 pulled, started $weekday|Carry on"),
            report.items.map { "${it.kind}|${it.title}|${it.detail}|${it.action}" }
        )
        assertEquals("krenko", report.items[0].deckId)
        // All ticked: done, nothing to carry on with.
        assertEquals(0, upkeep(cols, listOf(deck), now, today, pulls = listOf(PullUnderway("krenko", rows.map { it.key }.toSet())), zone = utc).items.size)
    }

    @Test
    fun theWordsForWhen() {
        assertEquals("yesterday", startedWhen(now - day, now, utc))
        assertEquals("today", startedWhen(now, now, utc))
        assertEquals("26 Sep", startedWhen(now - 10 * day, now, utc))
        assertEquals("this week's import", importWhen(now - 3 * day, now, utc))
        assertEquals("yesterday's import", importWhen(now - day, now, utc))
        assertEquals("started", pullStage(1, 10))
        assertEquals("nearly done", pullStage(8, 10))
        assertEquals("Krenko deck", deckTitle("Krenko deck"))
        assertEquals("James'", possessive("James"))
    }
}
