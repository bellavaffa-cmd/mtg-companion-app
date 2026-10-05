package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.ItemMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pull lists and put-back lists: building a deck out of storage and taking it apart again, the same
 * way on both apps. The web app has the same checks — see MtgCompanionWeb/tests/collection/pullList.test.ts.
 */
class PullListTest {

    private fun place(id: String, name: String = id, kind: PlaceKind = PlaceKind.BOX, parentId: String? = null,
                      sections: List<String>? = null, sortRule: SortRule? = null, createdAt: Long = 1) =
        StoragePlace(id, name, kind.name, parentId, null, sections, null, sortRule?.name, createdAt)

    private fun entry(id: String, name: String, quantity: Int, foilQuantity: Int = 0, places: List<CopyPlace>? = null, userTags: List<String> = emptyList()) =
        CollectionEntry(id, name, null, quantity = quantity, foilQuantity = foilQuantity, userTags = userTags, places = places)

    private fun at(placeId: String, qty: Int, foil: Boolean = false, section: String? = null, page: Int? = null, slot: Int? = null) =
        CopyPlace(placeId, qty, if (foil) true else null, section, page, slot)

    private fun pile(entries: List<CollectionEntry>, places: List<StoragePlace>? = null) =
        Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, entries, createdAt = 0, type = CollectionType.OWNED.name, storagePlaces = places)

    private fun binder(id: String, entries: List<CollectionEntry>) = Collection(id, id, entries, createdAt = 5, type = CollectionType.OWNED.name)

    private fun card(id: String, name: String, quantity: Int, proxyQuantity: Int? = null, typeLine: String? = null) =
        DeckCardEntry(id, name, null, quantity = quantity, typeLine = typeLine, proxyQuantity = proxyQuantity)

    private fun deckOf(id: String, name: String, cards: List<DeckCardEntry>, ownership: DeckOwnership, cameFrom: List<CameFrom>? = null) =
        Deck(id, name, cards = cards, ownership = ownership.name, createdAt = 1, cameFrom = cameFrom)

    private val shelf = place("shelf", "Shelf, study", PlaceKind.SHELF, createdAt = 1)
    private val red = place("red", "Red box", parentId = "shelf", sortRule = SortRule.COLOUR, sections = listOf("White", "Blue", "Black", "Red", "Green"), createdAt = 2)
    private val rares = place("rares", "Rares binder", PlaceKind.BINDER, parentId = "shelf", createdAt = 3)
    private val places = listOf(shelf, red, rares)

    private val collections = listOf(
        pile(listOf(
            entry("gm1", "Goblin Matron", 2, places = listOf(at("red", 1, section = "Red"))),
            entry("mtn", "Mountain", 5)
        ), places),
        binder("b1", listOf(
            entry("gb", "Goblin Bombardment", 1, places = listOf(at("red", 1, section = "Red"))),
            entry("gc", "Goblin Chieftain", 1, places = listOf(at("rares", 1, page = 2, slot = 4))),
            entry("pu", "Purphoros", 0, 1, listOf(at("rares", 1, foil = true, page = 5, slot = 1))),
            // Its place is gone: it has no place now.
            entry("it", "Impact Tremors", 1, places = listOf(at("old", 1))),
            entry("sp1", "Skirk Prospector", 1, userTags = listOf("lent to Sam"))
        )),
        Collection("wish", "Wishlist", listOf(entry("lw1", "Legion Warboss", 1)), createdAt = 1, type = CollectionType.WISHLIST.name)
    )

    private val krenko = deckOf("krenko", "Krenko goblins", listOf(
        card("gm2", "Goblin Matron", 2), card("gb", "Goblin Bombardment", 1), card("gc", "Goblin Chieftain", 1), card("pu", "Purphoros", 1),
        card("it", "Impact Tremors", 1), card("sr", "Sol Ring", 1), card("sp", "Skirk Prospector", 1), card("lw", "Legion Warboss", 1),
        card("mtn", "Mountain", 7)
    ), DeckOwnership.PROTOTYPE)
    private val atraxa = deckOf("atraxa", "Atraxa", listOf(card("sr2", "Sol Ring", 1)), DeckOwnership.PHYSICAL)
    private val decks = listOf(krenko, atraxa)

    private fun summary(rows: List<PullRow>) = rows.map { "${it.name} ×${it.qty}" + (it.hint?.let { h -> " — $h" } ?: "") }
    private fun needs(deck: Deck) = pullNeeds(deck).map { "${it.name} ×${it.qty}" }

    @Test
    fun aDeckBeingBuiltNeedsEveryCopyADeckYouHoldOnlyItsProxies() {
        assertEquals(listOf(
            "Goblin Bombardment ×1", "Goblin Chieftain ×1", "Goblin Matron ×2", "Impact Tremors ×1", "Legion Warboss ×1", "Mountain ×7",
            "Purphoros ×1", "Skirk Prospector ×1", "Sol Ring ×1"
        ), needs(krenko))
        val held = deckOf("d", "Held", listOf(card("a", "Sol Ring", 1), card("b", "Arcane Signet", 2, 1)), DeckOwnership.PHYSICAL)
        assertEquals(listOf("Arcane Signet ×1"), needs(held))
        // A proxy deck is proxies all through until real cards are swapped in.
        assertEquals(listOf("Arcane Signet ×1", "Sol Ring ×1"), needs(deckOf("p", "Proxies", listOf(card("a", "Sol Ring", 2, 1), card("b", "Arcane Signet", 1)), DeckOwnership.PROXY)))
        // Copies marked as proxies in a deck being built aren't asked for.
        assertEquals(emptyList<String>(), needs(deckOf("v", "Virtual", listOf(card("a", "Sol Ring", 2, 2)), DeckOwnership.VIRTUAL)))
    }

    @Test
    fun thePullListWalksThePlacesInTreeOrderThenTheRest() {
        val list = pullList(krenko, collections, decks)
        assertEquals(listOf(
            "PLACE: Red box › Red (Shelf, study)",
            "PLACE: Rares binder (Shelf, study)",
            "LOOSE: No place yet (Owned, not put away)",
            "DECK: In another deck (Only another deck has it)",
            "BASIC: Basic lands (From your basics)",
            "MISSING: Not owned"
        ), list.groups.map { "${it.kind}: ${it.title}" + if (it.detail.isNotEmpty()) " (${it.detail})" else "" })
        assertEquals(listOf("Goblin Bombardment ×1 — around “G”", "Goblin Matron ×1 — around “G”"), summary(list.groups[0].rows))
        assertEquals(listOf("Goblin Chieftain ×1 — Page 2, slot 4", "Purphoros ×1 — Page 5, slot 1 · foil"), summary(list.groups[1].rows))
        assertEquals(listOf("Goblin Matron ×1 — In Unsorted", "Impact Tremors ×1 — In b1", "Mountain ×5 — In Unsorted"), summary(list.groups[2].rows))
        assertEquals(listOf("Sol Ring ×1 — in Atraxa — take it?"), summary(list.groups[3].rows))
        assertEquals(listOf("Mountain ×2"), summary(list.groups[4].rows))
        // A lent copy isn't fetched, and a wishlist isn't owned.
        assertEquals(listOf("Legion Warboss ×1", "Skirk Prospector ×1"), summary(list.groups[5].rows))
        assertEquals(14, list.total)
        assertEquals(2, list.toBuy)
        assertEquals(2, list.places)
        assertEquals("1 Legion Warboss\n1 Skirk Prospector", pullBuyList(list))
    }

    @Test
    fun aBoxGoesSectionBySectionInItsOwnOrderABinderPageByPage() {
        val box = place("box", "Box", sortRule = SortRule.COLOUR, sections = listOf("Blue", "Red"))
        val cols = listOf(pile(listOf(
            entry("a", "Arc Lightning", 1, places = listOf(at("box", 1, section = "Red"))),
            entry("b", "Brainstorm", 1, places = listOf(at("box", 1, section = "Blue"))),
            entry("c", "Counterspell", 1, places = listOf(at("box", 1))),
            entry("d", "Abrade", 1, places = listOf(at("box", 1, section = "Red")))
        ), listOf(box)))
        val deck = deckOf("x", "X", listOf(card("a", "Arc Lightning", 1), card("b", "Brainstorm", 1), card("c", "Counterspell", 1), card("d", "Abrade", 1)), DeckOwnership.VIRTUAL)
        val list = pullList(deck, cols, listOf(deck))
        assertEquals(listOf("Box › Blue", "Box › Red", "Box"), list.groups.map { it.title })
        assertEquals(listOf("Abrade", "Arc Lightning"), list.groups[1].rows.map { it.name })
        assertEquals(listOf("Box › Red · around “A”", "Box › Red · around “A”", "Box › Blue · around “B”", "Box · around “C”"), pullRowsAZ(list).map { it.where })
    }

    @Test
    fun tickingScanningToTickAndPullingFromOneBox() {
        val list = pullList(krenko, collections, decks)
        val rows = list.groups.flatMap { it.rows }
        val ticked = mutableSetOf<String>()
        val first = pullRowToTick(rows, ticked, "Goblin Matron")!!
        assertEquals("Red box › Red · around “G”", first.where)
        ticked += first.key
        assertEquals("In Unsorted", pullRowToTick(rows, ticked, "goblin matron")!!.where)
        assertNull(pullRowToTick(rows, ticked, "Legion Warboss"))
        // Only another deck has it: still offered, and the list asks before taking it.
        assertTrue(pullRowToTick(rows, ticked, "Sol Ring")!!.source is PullSource.InDeck)
        assertEquals(1, pulledCopies(rows, ticked))
        assertEquals(listOf("Red box › Red", "Rares binder"), pullGroupsIn(list, collections, "shelf").map { it.title })
        assertEquals(listOf("Rares binder"), pullGroupsIn(list, collections, "rares").map { it.title })
    }

    @Test
    fun movingThePulledCopiesIntoTheDeckBox() {
        val list = pullList(krenko, collections, decks)
        val ticked = list.groups.filter { it.kind != PullGroupKind.MISSING }.flatMap { g -> g.rows.map { it.key } }.toSet()
        val out = movePulled(krenko, list, ticked, collections, decks)
        assertEquals(14, out.moved)
        assertTrue(out.nowPhysical)
        assertEquals(2, out.proxies)
        assertEquals(listOf(TakenFromDeck("atraxa", "Atraxa", "Sol Ring", 1)), out.taken)
        // The copies left their binders; the lent one stayed.
        assertEquals(emptyList<CollectionEntry>(), out.collections[0].entries)
        assertEquals(listOf("Skirk Prospector"), out.collections[1].entries.map { it.name })
        val deck = out.decks.first { it.id == "krenko" }
        assertEquals(DeckOwnership.PHYSICAL.name, deck.ownership)
        assertEquals(listOf("Skirk Prospector 1", "Legion Warboss 1"), deck.cards.filter { it.proxyQuantity != null }.map { "${it.name} ${it.proxyQuantity}" })
        assertEquals(listOf(
            CameFrom("Goblin Bombardment", "red", 1, section = "Red"),
            CameFrom("Goblin Matron", "red", 1, section = "Red"),
            CameFrom("Goblin Chieftain", "rares", 1, page = 2, slot = 4),
            CameFrom("Purphoros", "rares", 1, foil = true, page = 5, slot = 1)
        ), deck.cameFrom)
        // Taken from Atraxa: a proxy there now, its list still whole.
        assertEquals(1, out.decks.first { it.id == "atraxa" }.cards[0].proxyQuantity)
        // Built now: only its proxies are left to pull.
        assertEquals(listOf("Legion Warboss ×1", "Skirk Prospector ×1"), needs(deck))
    }

    @Test
    fun movingSomeADeckYouHoldSwapsProxiesForTheRealThingNothingTickedChangesNothing() {
        val held = deckOf("h", "Held", listOf(card("gb", "Goblin Bombardment", 2, 2)), DeckOwnership.PHYSICAL)
        val list = pullList(held, collections, listOf(held))
        val out = movePulled(held, list, list.groups[0].rows.map { it.key }.toSet(), collections, listOf(held))
        assertEquals(1, out.moved)
        assertEquals(1, out.decks[0].cards[0].proxyQuantity)
        assertFalse(out.nowPhysical)
        val none = movePulled(held, list, emptySet(), collections, listOf(held))
        assertSame(collections, none.collections)
        assertEquals(0, none.moved)
        // A proxy deck says 0 rather than leaving the key out (which would mean "all proxies").
        val proxy = deckOf("p", "P", listOf(card("gb", "Goblin Bombardment", 1)), DeckOwnership.PROXY)
        val pl = pullList(proxy, collections, listOf(proxy))
        assertEquals(0, movePulled(proxy, pl, pl.groups[0].rows.map { it.key }.toSet(), collections, listOf(proxy)).decks[0].cards[0].proxyQuantity)
    }

    @Test
    fun theCardsNotOwnedCanBeMarkedAsProxiesInADeckBeingBuilt() {
        val list = pullList(krenko, collections, decks)
        val marked = markMissingAsProxies(krenko, list)
        assertEquals(listOf("Skirk Prospector", "Legion Warboss"), marked.cards.filter { (it.proxyQuantity ?: 0) > 0 }.map { it.name })
        assertEquals(0, pullList(marked, collections, decks).toBuy)
        assertSame(atraxa, markMissingAsProxies(atraxa, list))
    }

    private val built = deckOf("built", "Built", listOf(
        card("gm2", "Goblin Matron", 2, typeLine = "Creature — Goblin"), card("cs", "Counterspell", 1, typeLine = "Instant"),
        card("sl", "Sol Ring", 1, 1), card("mtn", "Mountain", 3, typeLine = "Basic Land — Mountain")
    ), DeckOwnership.PHYSICAL, listOf(
        CameFrom("Goblin Matron", "red", 1, section = "Red"),
        CameFrom("Mountain", "gone", 3)
    ))
    private val colours = mapOf("gm2" to listOf("R"), "cs" to listOf("U"), "mtn" to emptyList())
    private val facts: (DeckCardEntry) -> CardFacts? = { e -> CardFacts(e.name, colors = colours[e.scryfallId] ?: emptyList(), typeLine = e.typeLine) }

    @Test
    fun puttingBackWhereEachCameFromOrTheBestPlaceByRule() {
        val origin = putBackList(built, collections, PutBackMode.ORIGIN, facts)
        // Blue comes before Red in the box.
        assertEquals(listOf("PLACE: Red box › Blue", "PLACE: Red box › Red", "BASIC: Mountains"), origin.groups.map { "${it.kind}: ${it.title}" })
        assertEquals(
            listOf("Goblin Matron ×1 (came from here) — around “G”", "Goblin Matron ×1 — around “G”"),
            origin.groups[1].rows.map { "${it.name} ×${it.qty}${if (it.fromOrigin) " (came from here)" else ""} — ${it.hint}" }
        )
        // Its place is gone, and no box takes lands: no place yet.
        assertEquals("No place yet · or keep with the deck box", origin.groups[2].detail)
        // The proxy isn't a copy to put back.
        assertEquals(6, origin.total)
        val rule = putBackList(built, collections, PutBackMode.RULE, facts)
        assertEquals(listOf("Goblin Matron ×2"), rule.groups[1].rows.map { "${it.name} ×${it.qty}" })
        // With nothing known of its colours, a card no section takes has no place yet.
        val bare = putBackList(built, collections, PutBackMode.RULE)
        assertEquals(listOf("NONE: No place yet", "BASIC: Mountains"), bare.groups.map { "${it.kind}: ${it.title}" })
        assertEquals(Spot("red", section = "Blue"), bestPlaceByRule(places, CardFacts("Brainstorm", colors = listOf("U")), collections)?.first)
        assertNull(bestPlaceByRule(places, CardFacts("Sol Ring", colors = emptyList()), collections))
        assertEquals("Plains", basicsTitle("Plains"))
        assertEquals("Snow-Covered Islands", basicsTitle("Snow-Covered Island"))
    }

    @Test
    fun takingADeckApartPutsEveryCopyInTheUnsortedPileAtItsPlaceAndLeavesTheList() {
        val list = putBackList(built, collections, PutBackMode.ORIGIN, facts)
        val out = takeApart(built, list, collections)
        assertEquals(3, out.placed)
        assertEquals(3, out.unplaced)
        val gm = out.collections[0].entries.first { it.scryfallId == "gm2" }
        assertEquals(2, gm.quantity)
        assertEquals(listOf(at("red", 2, section = "Red")), placedCopies(gm))
        assertEquals(8, out.collections[0].entries.first { it.scryfallId == "mtn" }.quantity)
        assertEquals(DeckOwnership.VIRTUAL.name, out.deck.ownership)
        assertEquals(emptyList<CameFrom>(), out.deck.cameFrom)
        assertFalse(out.deck.cards.any { it.proxyQuantity != null })
        assertEquals(4, out.deck.cards.size)
    }

    @Test
    fun whereTheCopiesCameFromMergesCardByCardAndAnOlderAppLeavesItAsItWas() {
        fun line(name: String, qty: Int, foil: Boolean = false) = CameFrom(name, "red", qty, if (foil) true else null)
        assertEquals(
            listOf(line("Sol Ring", 3), line("Sol Ring", 1, foil = true)),
            tidyCameFrom(listOf(line("Sol Ring", 1), line("sol ring", 2), line("Sol Ring", 1, foil = true), line("Bolt", 0)))
        )
        val base = listOf(line("Sol Ring", 1))
        val mine = listOf(line("Sol Ring", 1), line("Arcane Signet", 1))
        assertEquals(listOf(line("Arcane Signet", 1)), mergeCameFrom(base, mine, emptyList()))
        assertNull(mergeCameFrom(null, null, null))

        val deck = deckOf("d", "D", listOf(card("a", "Sol Ring", 1)), DeckOwnership.PHYSICAL, base)
        val older = deckOf("d", "D renamed", listOf(card("a", "Sol Ring", 1)), DeckOwnership.PHYSICAL)
        assertEquals(base, keepCameFromFromOlderApp(deck, older).cameFrom)
        assertSame(deck, keepCameFromFromOlderApp(older, deck))
        val merged = ItemMerge.mergeDecks(deck, deck.copy(cameFrom = mine), older, minePreferred = false)
        assertEquals("D renamed", merged.name)
        assertEquals(listOf(line("Arcane Signet", 1), line("Sol Ring", 1)), merged.cameFrom)
        // Neither side knows about it: no key.
        assertNull(ItemMerge.mergeDecks(older, older, older, minePreferred = false).cameFrom)
    }
}
