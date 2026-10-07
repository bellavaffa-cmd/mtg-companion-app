package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proxy sheets: what to print, and where it goes on the page. The web app's
 * tests/decks/proxySheet.test.ts has the same cases.
 */
class ProxySheetTest {

    private fun card(name: String, quantity: Int = 1, proxyQuantity: Int? = null) =
        DeckCardEntry("id-$name", name, "https://cards.scryfall.io/normal/front/a/b/$name.jpg?1", quantity, proxyQuantity = proxyQuantity)

    private fun deck(ownership: DeckOwnership, vararg cards: DeckCardEntry) =
        Deck("d", "Deck", cards = cards.toList(), ownership = ownership.name)

    private fun binder(vararg names: Pair<String, Int>) =
        Collection("b", "b", names.map { (name, q) -> CollectionEntry("id-$name", name, null, quantity = q) }, type = CollectionType.OWNED.name)

    @Test
    fun `nine cards to a page, at real size, in the middle of the paper`() {
        val a4 = sheetLayout(PaperSize.A4)
        assertEquals(9, a4.slots.size)
        assertEquals(10.5, a4.leftMm, 0.0)
        assertEquals(16.5, a4.topMm, 0.0)
        assertEquals(SheetSlot(73.5, 104.5), a4.slots[4])
        assertEquals(listOf(10.5, 73.5, 136.5, 199.5), a4.cutsXMm)
        assertEquals(listOf(16.5, 104.5, 192.5, 280.5), a4.cutsYMm)
        val letter = sheetLayout(PaperSize.LETTER)
        assertEquals(13.45, letter.leftMm, 0.0)
        assertEquals(7.7, letter.topMm, 0.0)
        assertEquals(SheetSlot(139.45, 183.7), letter.slots[8])
        assertEquals(PaperSize.LETTER, defaultPaper("us"))
        assertEquals(PaperSize.A4, defaultPaper("IT"))
        assertEquals(PaperSize.A4, defaultPaper(null))
    }

    @Test
    fun `copies spread over pages of nine, backs after their fronts`() {
        val picks = listOf(
            ProxyPick("Bolt", "1", "https://cards.scryfall.io/small/front/1.jpg", copies = 4),
            ProxyPick("Delver", "2", "https://cards.scryfall.io/normal/front/2.jpg", "https://cards.scryfall.io/normal/back/2.jpg", 3),
            ProxyPick("Island", "3", null, copies = 0)
        )
        assertEquals(7, sheetCards(picks, false).size)
        val cards = sheetCards(picks, true)
        assertEquals(10, cards.size)
        assertEquals(listOf("Delver", "Delver (back)"), cards.subList(4, 6).map { it.name })
        assertEquals("https://cards.scryfall.io/large/front/1.jpg", cards[0].imageUrl)
        assertEquals(listOf(CARDS_PER_PAGE, 1), sheetPages(cards).map { it.size })
        assertEquals("10 cards · 2 pages", sheetSummary(picks, true))
        assertEquals("7 cards · 1 page", sheetSummary(picks, false))
        assertEquals("Nothing picked", sheetSummary(emptyList(), false))
        assertEquals(7, pickedCopies(picks))
    }

    @Test
    fun `picture addresses go to the large size`() {
        assertEquals("https://cards.scryfall.io/large/front/a/b/x.jpg?1", proxyImageUrl("https://cards.scryfall.io/art_crop/front/a/b/x.jpg?1"))
        assertEquals("https://cards.scryfall.io/large/front/a/b/x.jpg?1", proxyImageUrl("https://cards.scryfall.io/png/front/a/b/x.png?1"))
        assertEquals("https://example.com/x.jpg", proxyImageUrl("https://example.com/x.jpg"))
        assertNull(proxyImageUrl(null))
    }

    @Test
    fun `choosing how many - never below 0 or above 99`() {
        var picks = listOf(ProxyPick("Bolt", "1", null, copies = 1))
        picks = withCopies(picks, "bolt", 5)
        assertEquals(5, picks[0].copies)
        assertEquals(0, withCopies(picks, "Bolt", -2)[0].copies)
        assertEquals(99, withCopies(picks, "Bolt", 500)[0].copies)
    }

    @Test
    fun `from a pull list, a deck and Spread thin`() {
        val d = deck(DeckOwnership.VIRTUAL, card("Sol Ring"), card("Bolt", 4), card("Forest", 10))
        val fromPull = picksFromNeeds(d, listOf(Triple("Bolt", "other", 2), Triple("bolt", "other", 1)))
        assertEquals(1, fromPull.size)
        assertEquals(3, fromPull[0].copies)
        // The deck's own printing, picture and all.
        assertEquals("id-Bolt", fromPull[0].scryfallId)

        // A deck on paper: the cards you don't own are picked; basics and owned ones start at 0.
        val fromDeck = picksForDeck(d, listOf(binder("Bolt" to 1)), listOf(d))
        assertEquals(listOf("Bolt" to 3, "Sol Ring" to 1, "Forest" to 0), fromDeck.map { it.name to it.copies })
        // A deck you hold: its proxies.
        val held = deck(DeckOwnership.PHYSICAL, card("Sol Ring", 1, 1), card("Bolt", 4))
        assertEquals(listOf("Sol Ring" to 1, "Bolt" to 0), picksForDeck(held, emptyList(), listOf(held)).map { it.name to it.copies })

        val thin = listOf(ThinCard("Sol Ring", "x", listOf("s1", "s2"), owned = 1, used = 3, short = 2, decks = emptyList()))
        assertEquals(listOf(Triple("Sol Ring", "s1", 2)), picksFromThin(thin).map { Triple(it.name, it.scryfallId, it.copies) })
    }

    @Test
    fun `printed copies marked as proxies, never more than the deck plays`() {
        val d = deck(DeckOwnership.VIRTUAL, card("Bolt", 4, 1), card("Sol Ring"))
        val picks = listOf(ProxyPick("Bolt", "id-Bolt", null, copies = 5), ProxyPick("Sol Ring", "id-Sol Ring", null, copies = 1))
        assertEquals(listOf(4, 1), markPrintedAsProxies(d, picks).cards.map { it.proxyQuantity })
        // A deck you hold counts what it hasn't got as proxies already.
        val held = deck(DeckOwnership.PHYSICAL, card("Bolt", 4))
        assertSame(held, markPrintedAsProxies(held, picks))
        assertSame(d, markPrintedAsProxies(d, emptyList()))
    }

    @Test
    fun `the printed page has every card at its spot, the mark and low ink`() {
        val picks = listOf(ProxyPick("Bolt & Co", "1", "https://cards.scryfall.io/normal/front/1.jpg", copies = 10))
        val html = proxySheetHtml(picks, ProxyOptions(PaperSize.A4, marked = true, lowInk = true))
        assertEquals(2, Regex("class=\"page\"").findAll(html).count())
        assertEquals(10, Regex("class=\"card\"").findAll(html).count())
        assertTrue(html.contains("Bolt &amp; Co"))
        assertTrue(html.contains("left:73.5mm;top:104.5mm"))
        assertTrue(html.contains(PROXY_MARK))
        assertTrue(html.contains("grayscale(1)"))
        assertTrue(html.contains("size: A4"))
        val plain = proxySheetHtml(picks, ProxyOptions(PaperSize.LETTER, marked = false, lowInk = false))
        assertTrue(!plain.contains(PROXY_MARK) && !plain.contains("grayscale") && plain.contains("size: letter"))
    }
}
