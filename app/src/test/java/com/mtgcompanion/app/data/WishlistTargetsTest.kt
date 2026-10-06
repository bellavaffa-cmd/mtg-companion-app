package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.ItemMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Price targets on the Wishlist: the target from a percentage, when one goes off and comes back,
 * "Any printing counts", "Foil only", the row's words and the "Under your price" box. The web app
 * has the same checks — tests/collection/wishlistTargets.test.ts.
 */
class WishlistTargetsTest {

    private fun entry(
        id: String,
        target: Double? = null,
        quantity: Int = 1,
        name: String = id,
        any: Boolean? = null,
        foilOnly: Boolean? = null
    ) = CollectionEntry(id, name, null, quantity = quantity, priceAlert = target, alertAnyPrinting = any, alertFoilOnly = foilOnly)

    private fun wishlist(vararg entries: CollectionEntry) =
        Collection(WISHLIST_ID, "Wishlist", entries.toList(), createdAt = 0, type = CollectionType.WISHLIST.name)

    private val usd: (Double, Boolean) -> String = { v, whole -> if (whole) "$" + Math.round(v) else "$" + String.format(java.util.Locale.US, "%.2f", v) }

    @Test
    fun aTargetSoMuchOffTodaysPriceToTheCent() {
        assertEquals(14.72, targetFromPercent(18.4, 20)!!, 0.0)
        assertEquals(16.56, targetFromPercent(18.4, 10)!!, 0.0)
        assertEquals(0.89, targetFromPercent(0.99, 10)!!, 0.0)
        assertNull(targetFromPercent(null, 10))
        assertNull(targetFromPercent(0.0, 10))
    }

    @Test
    fun setTargetsForAllSetsOneOnlyWhereThereIsNoneAndAPrice() {
        val entries = listOf(entry("a", target = 5.0), entry("b"), entry("c"))
        assertEquals(mapOf("b" to 18.0), targetsForAll(entries, mapOf("a" to 10.0, "b" to 20.0, "c" to null), 10))
    }

    @Test
    fun aTargetIsToldAboutOnceThenAgainOnlyOnTheNextDrop() {
        val w = alertWatches(listOf(wishlist(entry("t", target = 15.0)))).single()
        assertEquals(AlertStep.Forget, alertStep(w, 18.4, told = null))
        assertEquals(AlertStep.Tell(14.5), alertStep(w, 14.5, told = null))
        // Still under, no cheaper: quiet.
        assertEquals(AlertStep.Quiet, alertStep(w, 14.5, told = 14.5))
        assertEquals(AlertStep.Quiet, alertStep(w, 14.9, told = 14.5))
        // The next drop: told again.
        assertEquals(AlertStep.Tell(13.0), alertStep(w, 13.0, told = 14.5))
        // Back over: rearmed, so going under again tells again.
        assertEquals(AlertStep.Forget, alertStep(w, 16.0, told = 13.0))
        assertEquals(AlertStep.Tell(14.9), alertStep(w, 14.9, told = null))
    }

    @Test
    fun anyPrintingCountsTheCheapestPrintingOfTheSameCard() {
        assertTrue(sameCard("Sol Ring", "sol ring"))
        assertTrue(sameCard("Delver of Secrets", "Delver of Secrets // Insectile Aberration"))
        assertFalse(sameCard("Sol Ring", "Sol Talisman"))
        val printings = listOf(
            PrintingPrice("Sol Ring", 3.0, 12.0),
            PrintingPrice("Sol Ring", 1.5, null),
            PrintingPrice("Sol Ring", null, 8.0),
            PrintingPrice("Sol Talisman", 0.2, 0.3)
        )
        assertEquals(1.5 to 8.0, cheapestPrinting("Sol Ring", printings))
        assertEquals(null to null, cheapestPrinting("Mana Crypt", printings))

        // The check finds an any-printing target's prices under its own key, apart from its printing's.
        val (any, own) = alertWatches(listOf(wishlist(entry("sol", target = 2.0, name = "Sol Ring", any = true), entry("crypt", target = 100.0))))
        assertEquals("any:sol", any.priceKey)
        assertEquals("crypt", own.priceKey)
        val hits = alertHits(listOf(any, own), mapOf("sol" to (3.0 to 12.0), "any:sol" to (1.5 to 8.0), "crypt" to (150.0 to null)))
        assertEquals(listOf("sol" to 1.5), hits.map { it.watch.entry.scryfallId to it.price })
    }

    @Test
    fun foilOnlyChecksTheFoilPrice() {
        val w = alertWatches(listOf(wishlist(entry("f", target = 10.0, foilOnly = true)))).single()
        assertEquals(12.0, alertPrice(w, 3.0, 12.0))
        assertNull(alertPrice(w, 3.0, null))
        val plain = alertWatches(listOf(wishlist(entry("p", target = 10.0)))).single()
        assertEquals(3.0, alertPrice(plain, 3.0, 12.0))
    }

    @Test
    fun aTargetIsSetWithBothOptionsAndTakenOffWithoutThem() {
        val set = withTarget(entry("a"), 15.0, TargetOptions(anyPrinting = true, foilOnly = false))
        assertEquals(Triple(15.0, true, false), Triple(set.priceAlert, set.alertAnyPrinting, set.alertFoilOnly))
        assertNull(withTarget(set, null).priceAlert)
    }

    @Test
    fun theRowSaysHowFarOffItsTargetACardIs() {
        assertEquals("No target · tap to set one", targetLine(null, 38.9, null, usd))
        assertEquals("Target \$15 · \$3.40 to go", targetLine(15.0, 18.4, null, usd))
        assertEquals("Target \$60 · \$21 to go", targetLine(60.0, 81.0, null, usd))
        assertEquals("Target \$70 · dropped 12% this week", targetLine(70.0, 64.2, 12, usd))
        assertEquals("Target \$70 · under your price", targetLine(70.0, 64.2, null, usd))
        assertEquals("Target \$70", targetLine(70.0, null, null, usd))
        assertEquals("\$15", shortPrice(15.0, usd))
        assertEquals("\$14.72", shortPrice(14.72, usd))
        assertEquals("2 cards · 1 with a target", targetCount(listOf(entry("a", target = 1.0), entry("b"))))
        assertEquals(20.0, wishlistTotal(listOf(entry("a", quantity = 2), entry("b")), mapOf("a" to 10.0, "b" to null))!!, 0.0)
        assertNull(wishlistTotal(listOf(entry("b")), emptyMap()))
    }

    @Test
    fun theWeeksDropAndTheYearsLowComeFromTheCardsOwnHistoryOrNotAtAll() {
        fun p(day: Long, v: Double?, foil: Double? = null) = PricePoint(day, v, foil, null)
        val track = PriceTrack(listOf(p(100, 20.0, 40.0), p(150, 14.1, 30.0), p(190, 73.0), p(196, 64.2, 25.0)), lastDay = 200)
        assertEquals(12, weekDrop(track, foil = false, today = 200))
        // The foil held at 30 a week ago (its last price before then), and is 25 now.
        assertEquals(17, weekDrop(track, foil = true, today = 200))
        assertEquals(14.1, yearLow(track, foil = false)!!, 0.0)
        assertEquals(25.0, yearLow(track, foil = true)!!, 0.0)
        // A week's history isn't enough for a year's low, and a day's isn't enough for a week's drop.
        val short = PriceTrack(listOf(p(195, 20.0), p(199, 15.0)), lastDay = 200)
        assertNull(yearLow(short, foil = false))
        assertNull(weekDrop(short, foil = false, today = 200))
        assertNull(weekDrop(null, foil = false, today = 200))
    }

    @Test
    fun theUnderYourPriceBoxUntilGotItThenAgainOnTheNextDropOrTheNextTimeUnder() {
        val watches = alertWatches(listOf(wishlist(entry("s", target = 70.0))))
        fun hitsAt(price: Double) = alertHits(watches, mapOf("s" to (price to null)))
        assertEquals(1, underYourPrice(hitsAt(64.2), emptyMap()).size)
        val gotIt = withGotIt(emptyMap(), underYourPrice(hitsAt(64.2), emptyMap()))
        assertEquals(mapOf("s" to 64.2), gotIt)
        assertEquals(0, underYourPrice(hitsAt(64.2), gotIt).size)
        assertEquals(1, underYourPrice(hitsAt(60.0), gotIt).size)
        // Back over its target: the "Got it" is forgotten, so the next time under shows again.
        assertEquals(emptyMap<String, Double>(), gotItKept(gotIt, hitsAt(80.0)))
        assertSame(gotIt, gotItKept(gotIt, hitsAt(64.2)))
    }

    @Test
    fun anOlderAppsSaveKeepsATargetsOptions() {
        val mine = wishlist(entry("a", target = 15.0, any = true, foilOnly = false), entry("b"))
        val older = wishlist(entry("a", target = 12.0), entry("b"))
        assertEquals(entry("a", target = 12.0, any = true, foilOnly = false), keepAlertOptionsFromOlderApp(mine, older).entries[0])
        assertSame(mine, keepAlertOptionsFromOlderApp(mine, mine))

        // Through the merge: the older app changed the target, this one the options; both stay.
        val base = wishlist(entry("a", target = 15.0, any = false, foilOnly = false))
        val here = wishlist(entry("a", target = 15.0, any = true, foilOnly = true))
        val merged = ItemMerge.mergeCollections(base, here, wishlist(entry("a", target = 12.0)), minePreferred = true)
        assertEquals(entry("a", target = 12.0, any = true, foilOnly = true), merged.entries[0])
    }
}
