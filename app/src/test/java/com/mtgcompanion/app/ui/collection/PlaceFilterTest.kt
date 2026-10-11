package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.data.CopyPlace
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckOwnership
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.PlacedCard
import com.mtgcompanion.app.data.SortRule
import com.mtgcompanion.app.data.StoragePlace
import com.mtgcompanion.app.data.UNSORTED_COLLECTION_ID
import com.mtgcompanion.app.data.cardsIn
import com.mtgcompanion.app.data.chipText
import com.mtgcompanion.app.data.copiesByPlace
import com.mtgcompanion.app.data.namesDecksUse
import com.mtgcompanion.app.data.placeChains
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.walkPullList
import com.mtgcompanion.app.data.walkingOrder
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallPrices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Filtering what's in storage — a place's page and the Storage tab — with the Cards tab's filters,
 * copy by copy. The web app has the same checks: tests/collection/placeFilter.test.ts.
 */
class PlaceFilterTest {
    private fun place(id: String, name: String, kind: PlaceKind = PlaceKind.BOX, parentId: String? = null, sections: List<String>? = null,
                      sortRule: SortRule? = null, pockets: Int? = null, createdAt: Long = 1) =
        StoragePlace(id, name, kind.name, parentId, null, sections, pockets, sortRule?.name, createdAt)

    private fun at(placeId: String, qty: Int, foil: Boolean = false, section: String? = null, page: Int? = null, slot: Int? = null) =
        CopyPlace(placeId, qty, if (foil) true else null, section, page, slot)

    private fun entry(id: String, name: String, quantity: Int, foilQuantity: Int, places: List<CopyPlace>, condition: String? = null, language: String? = null) =
        CollectionEntry(id, name, null, quantity = quantity, foilQuantity = foilQuantity, places = places, condition = condition, language = language)

    private val shelf = place("shelf", "Shelf, study", PlaceKind.SHELF, createdAt = 1)
    private val red = place("red", "Red box", parentId = "shelf", sortRule = SortRule.COLOUR, sections = listOf("White", "Blue", "Red"), createdAt = 2)
    private val rares = place("rares", "Rares binder", PlaceKind.BINDER, parentId = "shelf", pockets = 9, createdAt = 3)

    private val collections = listOf(
        Collection(UNSORTED_COLLECTION_ID, "Unsorted", listOf(entry("sr", "Sol Ring", 1, 0, emptyList())), createdAt = 0, type = CollectionType.OWNED.name, storagePlaces = listOf(shelf, red, rares)),
        Collection("b1", "Trade binder", listOf(
            entry("bolt", "Lightning Bolt", 2, 1, listOf(at("red", 2, section = "Red"), at("rares", 1, foil = true, page = 1, slot = 2))),
            entry("gg", "Goblin Guide", 1, 0, listOf(at("red", 1, section = "Red")), condition = "LP"),
            entry("cs", "Counterspell", 1, 0, listOf(at("red", 1, section = "Blue")), language = "ja"),
            entry("atx", "Atraxa, Praetors' Voice", 1, 0, listOf(at("rares", 1, page = 2, slot = 1)))
        ), createdAt = 5, type = CollectionType.OWNED.name)
    )
    private val decks = listOf(Deck("d", "Burn", cards = listOf(DeckCardEntry("bolt2", "Lightning Bolt", null, quantity = 1)), ownership = DeckOwnership.PHYSICAL.name, createdAt = 1))

    private val places = placesOf(collections)
    private val redCopies = cardsIn(collections, "red")
    private val rareCopies = cardsIn(collections, "rares")
    private val all = redCopies + rareCopies
    private val atraxa = rareCopies.first { it.entry.scryfallId == "atx" }
    private val boltFoil = rareCopies.first { it.entry.scryfallId == "bolt" }

    private val data = mapOf(
        "bolt" to ScryfallCard(id = "bolt", name = "Lightning Bolt", typeLine = "Instant", rarity = "common", colorIdentity = listOf("R"), colors = listOf("R"),
            oracleText = "Lightning Bolt deals 3 damage to any target.", prices = ScryfallPrices(usd = "2.50", usdFoil = "9.00"), finishes = listOf("nonfoil", "foil")),
        "gg" to ScryfallCard(id = "gg", name = "Goblin Guide", typeLine = "Creature — Goblin Scout", rarity = "rare", colorIdentity = listOf("R"), colors = listOf("R"),
            power = "2", toughness = "2", prices = ScryfallPrices(usd = "3.00")),
        "cs" to ScryfallCard(id = "cs", name = "Counterspell", typeLine = "Instant", rarity = "uncommon", colorIdentity = listOf("U"), colors = listOf("U"),
            oracleText = "Counter target spell.", prices = ScryfallPrices(usd = "1.00")),
        "atx" to ScryfallCard(id = "atx", name = "Atraxa, Praetors' Voice", typeLine = "Legendary Creature — Phyrexian Angel Horror", rarity = "mythic",
            colorIdentity = listOf("W", "U", "B", "G"), colors = listOf("W", "U", "B", "G"), prices = ScryfallPrices(usd = "20.00"))
    )
    private val facts = FilterFacts(
        card = { id -> data[id]?.let { CardFacts.of(it) } },
        advanced = { id -> data[id]?.let { advancedFactsOf(it) } },
        copies = copyFactsOf(collections, decks),
        chains = placeChains(places),
        usedNames = namesDecksUse(decks)
    )

    private fun f(query: String = "", basic: CollectionFilter = CollectionFilter(), advanced: AdvancedFilter = AdvancedFilter(), sparesOnly: Boolean = false) =
        CardsFilter(query, basic, advanced, sparesOnly)
    private fun names(copies: List<PlacedCard>) = copies.map { "${it.entry.name}${if (it.line.isFoil) " (foil)" else ""} ×${it.line.qty}" }

    @Test
    fun nothingOnEveryCopyAndTheFilterIsOff() {
        assertFalse(CardsFilter().active)
        assertEquals(0, CardsFilter().count)
        assertSame(all, filterCopies(all, CardsFilter(), facts))
    }

    @Test
    fun theSearchFindsCopiesByName() {
        assertEquals(listOf("Lightning Bolt ×2", "Lightning Bolt (foil) ×1"), names(filterCopies(all, f(query = " bolt "), facts)))
        assertEquals("the search is not a filter on the badge", 0, f(query = "bolt").count)
        // As Cards searches, when the screen gives it the tags too.
        val byTag = FilterFacts(facts.card, facts.advanced, facts.copies, facts.chains, facts.usedNames, searchMatches = { c, q -> q == "burn" && c.entry.name == "Lightning Bolt" })
        assertEquals(listOf("Lightning Bolt ×2"), names(filterCopies(redCopies, f(query = "burn"), byTag)))
    }

    @Test
    fun theFilterPanelTypeTextColoursAndRarityAsOnCards() {
        assertEquals(listOf("Counterspell ×1", "Lightning Bolt ×2"), names(filterCopies(redCopies, f(basic = CollectionFilter(type = "instant")), facts)))
        assertEquals(listOf("Goblin Guide ×1", "Lightning Bolt ×2", "Lightning Bolt (foil) ×1"), names(filterCopies(all, f(basic = CollectionFilter(colors = setOf('R'))), facts)))
        assertEquals(listOf("Counterspell ×1", "Atraxa, Praetors' Voice ×1"), names(filterCopies(all, f(basic = CollectionFilter(rarities = setOf("mythic", "uncommon"))), facts)))
        assertEquals(listOf("Counterspell ×1"), names(filterCopies(all, f(basic = CollectionFilter(text = "counter target")), facts)))
        // A card whose data hasn't loaded can't be judged: left out while a filter is on.
        val none = FilterFacts(copies = facts.copies, chains = facts.chains)
        assertEquals(emptyList<PlacedCard>(), filterCopies(all, f(basic = CollectionFilter(type = "instant")), none))
    }

    @Test
    fun yourCopiesAreJudgedCopyByCopy() {
        assertEquals(listOf("Lightning Bolt (foil) ×1"), names(filterCopies(all, f(advanced = AdvancedFilter(finishes = listOf("foil"))), facts)))
        assertEquals(listOf("Counterspell ×1", "Goblin Guide ×1", "Lightning Bolt ×2", "Atraxa, Praetors' Voice ×1"),
            names(filterCopies(all, f(advanced = AdvancedFilter(finishes = listOf("nonfoil"))), facts)))
        assertEquals(listOf("Goblin Guide ×1"), names(filterCopies(all, f(advanced = AdvancedFilter(conditions = listOf("LP"))), facts)))
        assertEquals(listOf("Counterspell ×1"), names(filterCopies(all, f(advanced = AdvancedFilter(language = "ja")), facts)))
        assertEquals(5, filterCopies(all, f(advanced = AdvancedFilter(binder = "b1")), facts).size)
        // A place: the copies in it, and in the places inside it.
        assertEquals(listOf("Atraxa, Praetors' Voice ×1", "Lightning Bolt (foil) ×1"), names(filterCopies(all, f(advanced = AdvancedFilter(place = "rares")), facts)))
        assertEquals(all.size, filterCopies(all, f(advanced = AdvancedFilter(place = "shelf")), facts).size)
        // Every copy in a place has one.
        assertEquals(emptyList<PlacedCard>(), filterCopies(all, f(advanced = AdvancedFilter(place = "none")), facts))
        // The card's copies everywhere: Bolt has 3 in binders and 1 in a deck.
        assertEquals(listOf("Lightning Bolt ×2", "Lightning Bolt (foil) ×1"), names(filterCopies(all, f(advanced = AdvancedFilter(copiesOp = ">=", copies = "3")), facts)))
    }

    @Test
    fun aPriceFilterJudgesAFoilCopyByItsFoilPrice() {
        assertEquals(listOf("Atraxa, Praetors' Voice ×1", "Lightning Bolt (foil) ×1"), names(filterCopies(all, f(advanced = AdvancedFilter(priceMin = "5")), facts)))
    }

    @Test
    fun sparesOnlyCopiesOfCardsNoneOfYourDecksUse() {
        assertEquals(listOf("Counterspell ×1", "Goblin Guide ×1"), names(filterCopies(redCopies, f(sparesOnly = true), facts)))
        assertEquals(1, f(sparesOnly = true).count)
        assertFalse(copyMatches(f(sparesOnly = true), redCopies[0], FilterFacts(chains = facts.chains)))
    }

    @Test
    fun theTotalsMatchingCopiesOfAllAndWhatTheMatchingOnesAreWorth() {
        val price = { c: PlacedCard -> data[c.entry.scryfallId]?.prices?.let { placedCopyUsd(c, it.usd?.toDoubleOrNull(), it.usdFoil?.toDoubleOrNull()) } }
        val matching = filterCopies(all, f(basic = CollectionFilter(colors = setOf('R'))), facts)
        val t = filterTotals(all, matching, price)
        assertEquals(FilterTotals(4, 6, 2.5 * 2 + 3 + 9), t)
        assertEquals("4 of 6 cards · $17", totalsLine(t) { "$" + Math.round(it) })
        assertEquals("4 of 6 cards", totalsLine(filterTotals(all, matching, null)) { "$$it" })
        assertEquals("1 of 1 card", totalsLine(FilterTotals(1, 1, null)) { "$it" })
        assertEquals("14 of 1,312 cards", totalsLine(FilterTotals(14, 1312, null)) { "$it" })
        // A foil copy with no foil price is priced as a plain one, and the other way round.
        assertEquals(2.0, placedCopyUsd(boltFoil, 2.0, null)!!, 0.0)
        assertEquals(4.0, placedCopyUsd(redCopies[0], null, 4.0)!!, 0.0)
        assertEquals(null, placedCopyUsd(redCopies[0], null, null))
    }

    @Test
    fun theChipsTheSearchEachFilterSparesOnlyEachComesOffAndOneClear() {
        val on = f(query = "bolt", basic = CollectionFilter(type = "instant", colors = setOf('R')), advanced = AdvancedFilter(finishes = listOf("foil")), sparesOnly = true)
        val chips = cardsFilterChips(on, { null }, { "$$it" })
        assertEquals(listOf("“bolt”", "Instant", "Colour R", "Foil", "Spares only"), chips.map { it.text })
        assertEquals("", removeCardsFilterChip(on, "search").query)
        assertFalse(removeCardsFilterChip(on, "spares").sparesOnly)
        assertEquals(emptySet<Char>(), removeCardsFilterChip(on, "color:R").basic.colors)
        assertEquals(emptyList<String>(), removeCardsFilterChip(on, "finish:foil").advanced.finishes)
        assertEquals(4, on.count)
    }

    @Test
    fun aBinderThePocketsAndPagesWithAMatch() {
        val matching = filterCopies(rareCopies, f(query = "atraxa"), facts)
        assertEquals(setOf(9), matchingPockets(rares, rareCopies, matching))
        assertEquals(listOf(1, 2), matchingPages(rares, rareCopies, filterCopies(rareCopies, f(basic = CollectionFilter(rarities = setOf("common", "mythic"))), facts)))
        assertEquals(emptySet<Int>(), matchingPockets(rares, rareCopies, emptyList()))
    }

    @Test
    fun theStorageTabsHeading() {
        assertEquals("Cards in storage matching “ramp”", storageMatchingTitle(f(query = "ramp")))
        assertEquals("Cards in storage matching “ramp” · 2 filters", storageMatchingTitle(f(query = "ramp", basic = CollectionFilter(colors = setOf('G', 'U')))))
        assertEquals("Cards in storage matching your filter", storageMatchingTitle(f(sparesOnly = true)))
        assertEquals("Cards in storage matching your filters", storageMatchingTitle(f(sparesOnly = true, basic = CollectionFilter(type = "land"))))
    }

    @Test
    fun resultsGroupedByPlaceAndSectionInWalkingOrderEachRowsPlaceChip() {
        val groups = copiesByPlace(all.reversed(), places)
        assertEquals(listOf(
            "Red box › Blue (Shelf, study): Counterspell ×1",
            "Red box › Red (Shelf, study): Goblin Guide ×1, Lightning Bolt ×2",
            "Rares binder (Shelf, study): Lightning Bolt (foil) ×1, Atraxa, Praetors' Voice ×1"
        ), groups.map { "${it.title} (${it.detail}): ${names(it.copies).joinToString(", ")}" })
        assertEquals(listOf("Counterspell ×1", "Goblin Guide ×1", "Lightning Bolt ×2", "Lightning Bolt (foil) ×1", "Atraxa, Praetors' Voice ×1"), names(walkingOrder(all, places)))
        // A place that's gone has no group.
        assertEquals(listOf("Red box › Blue", "Red box › Red"), copiesByPlace(all, listOf(shelf, red)).map { it.title })
        assertEquals("Red box › Red ×2", chipText(copyChip(redCopies.first { it.entry.name == "Lightning Bolt" }, red)))
        assertEquals("Rares binder ×1", chipText(copyChip(atraxa, rares)))
        assertEquals(2, copyChip(atraxa, rares).page)
    }

    @Test
    fun walkThisListTheCopiesAsAPullListPlaceByPlace() {
        val list = walkPullList(filterCopies(all, f(basic = CollectionFilter(colors = setOf('R'))), facts), places)
        assertEquals(listOf(
            "Red box › Red" to listOf("Goblin Guide ×1 — around “G”", "Lightning Bolt ×2 — around “L”"),
            "Rares binder" to listOf("Lightning Bolt ×1 — Page 1, slot 2 · foil")
        ), list.groups.map { g -> g.title to g.rows.map { r -> "${r.name} ×${r.qty}" + (r.hint?.let { " — $it" } ?: "") } })
        assertEquals(4, list.total)
        assertEquals(2, list.places)
        assertEquals(0, list.toBuy)
        // Keys stay the same while the collection does, so a tick sticks.
        assertEquals(walkPullList(redCopies, places).groups.flatMap { g -> g.rows.map { it.key } }, walkPullList(redCopies, places).groups.flatMap { g -> g.rows.map { it.key } })
        assertEquals(3, list.groups.flatMap { g -> g.rows.map { it.key } }.toSet().size)
    }

    @Test
    fun filtersAreKeptPerPlaceWhileTheAppRunsAndForgottenWhenCleared() {
        var memory: Map<String, CardsFilter> = emptyMap()
        memory = withFilter(memory, "red", f(query = "bolt"))
        memory = withFilter(memory, STORAGE_FILTER_KEY, f(sparesOnly = true))
        assertEquals("bolt", filterIn(memory, "red").query)
        assertEquals(CardsFilter(), filterIn(memory, "rares"))
        assertTrue(filterIn(memory, STORAGE_FILTER_KEY).sparesOnly)
        memory = withFilter(memory, "red", CardsFilter())
        assertFalse("red" in memory)
        assertEquals(1, memory.size)
    }

    @Test
    fun aWalkKeepsItsTicksForTheRowsStillOnIt() {
        val list = walkPullList(redCopies, places)
        WalkSession.start("Red box", list)
        val key = list.groups.first().rows.first().key
        WalkSession.setTicked(setOf(key, "gone"))
        WalkSession.start("Red box · “bolt”", walkPullList(redCopies, places))
        assertEquals(setOf(key), WalkSession.walk.value?.ticked)
        assertEquals("Red box · “bolt”", WalkSession.walk.value?.title)
    }
}
