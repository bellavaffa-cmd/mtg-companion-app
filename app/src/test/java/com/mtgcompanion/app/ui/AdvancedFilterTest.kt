package com.mtgcompanion.app.ui

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CopyPlace
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.StoragePlace
import com.mtgcompanion.app.data.UNSORTED_COLLECTION_ID
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallCardFace
import com.mtgcompanion.app.network.scryfall.ScryfallPrices
import com.mtgcompanion.app.ui.collection.AdvancedFilter
import com.mtgcompanion.app.ui.collection.CollectionFilter
import com.mtgcompanion.app.ui.collection.CopyFacts
import com.mtgcompanion.app.ui.collection.ActiveChip
import com.mtgcompanion.app.ui.collection.SavedFilter
import com.mtgcompanion.app.ui.collection.advancedFactsOf
import com.mtgcompanion.app.ui.collection.advancedMatches
import com.mtgcompanion.app.ui.collection.compare
import com.mtgcompanion.app.ui.collection.copyFactsOf
import com.mtgcompanion.app.ui.collection.costContains
import com.mtgcompanion.app.ui.collection.filterChips
import com.mtgcompanion.app.ui.collection.manaSymbols
import com.mtgcompanion.app.ui.collection.numberOf
import com.mtgcompanion.app.ui.collection.queryNumber
import com.mtgcompanion.app.ui.collection.removeChip
import com.mtgcompanion.app.ui.collection.savedFiltersFromJson
import com.mtgcompanion.app.ui.collection.savedFiltersToJson
import com.mtgcompanion.app.ui.collection.scryfallQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The All cards Advanced filters, as the web app's tests/collection/advancedFilter.test.ts — the same cases. */
class AdvancedFilterTest {
    private val one = CopyFacts()

    private val atraxa = advancedFactsOf(ScryfallCard(
        id = "x", name = "Atraxa, Praetors' Voice", typeLine = "Legendary Creature — Phyrexian Angel Horror", manaCost = "{G}{W}{U}{B}", cmc = 4.0,
        colors = listOf("W", "U", "B", "G"), colorIdentity = listOf("W", "U", "B", "G"), power = "4", toughness = "4",
        keywords = listOf("Flying", "Vigilance", "Deathtouch", "Lifelink"), legalities = mapOf("commander" to "legal", "modern" to "not_legal"),
        set = "2XM", prices = ScryfallPrices(usd = "20.00", usdFoil = "45.00"), artist = "Victor Adame Minguez", finishes = listOf("nonfoil", "foil"),
        oracleText = "Flying, vigilance, deathtouch, lifelink\nAt the beginning of your end step, proliferate.", gameChanger = false
    ))
    private val bolt = advancedFactsOf(ScryfallCard(
        id = "x", name = "Lightning Bolt", typeLine = "Instant", manaCost = "{R}", cmc = 1.0, colors = listOf("R"), colorIdentity = listOf("R"),
        legalities = mapOf("commander" to "legal", "modern" to "legal", "standard" to "not_legal"), set = "lea", prices = ScryfallPrices(usd = "2.50"),
        artist = "Christopher Rush", flavorText = "The sparkmage shrieked, calling on the rage of the storms of his youth."
    ))
    private val delver = advancedFactsOf(ScryfallCard(
        id = "x", name = "Delver of Secrets // Insectile Aberration", layout = "transform", cmc = 1.0, colorIdentity = listOf("U"), set = "isd",
        cardFaces = listOf(
            ScryfallCardFace(name = "Delver of Secrets", manaCost = "{U}", typeLine = "Creature — Human Wizard", power = "1", toughness = "1", colors = listOf("U"), artist = "Nils Hamm"),
            ScryfallCardFace(name = "Insectile Aberration", manaCost = "", typeLine = "Creature — Human Insect", power = "3", toughness = "2", colors = listOf("U"), artist = "Nils Hamm", flavorText = "If only he had used his gift for good.")
        ),
        prices = ScryfallPrices(usd = "0.25")
    ))
    private val goyf = advancedFactsOf(ScryfallCard(id = "x", name = "Tarmogoyf", typeLine = "Creature — Lhurgoyf", manaCost = "{1}{G}", cmc = 2.0, colors = listOf("G"), colorIdentity = listOf("G"), power = "*", toughness = "1+*"))
    private val jace = advancedFactsOf(ScryfallCard(
        id = "x", name = "Jace, the Mind Sculptor", typeLine = "Legendary Planeswalker — Jace", manaCost = "{2}{U}{U}", cmc = 4.0, colors = listOf("U"), colorIdentity = listOf("U"),
        loyalty = "3", legalities = mapOf("vintage" to "restricted", "standard" to "banned"), gameChanger = true
    ))
    private val solRing = advancedFactsOf(ScryfallCard(
        id = "x", name = "Sol Ring", typeLine = "Artifact", manaCost = "{1}", cmc = 1.0, colors = emptyList(), colorIdentity = emptyList(),
        legalities = mapOf("commander" to "legal", "vintage" to "restricted"), finishes = listOf("etched"), prices = ScryfallPrices(usd = null, usdFoil = "30.00"), fullArt = false
    ))
    private val mox = advancedFactsOf(ScryfallCard(id = "x", name = "Mox Diamond", typeLine = "Artifact", manaCost = "{0}", cmc = 0.0, colors = emptyList(), colorIdentity = emptyList(), reserved = true, fullArt = true))
    private val soldier = advancedFactsOf(ScryfallCard(id = "x", name = "Soldier", typeLine = "Token Creature — Soldier", layout = "token", colors = listOf("W"), colorIdentity = listOf("W"), power = "1", toughness = "1"))
    private val teferi = advancedFactsOf(ScryfallCard(id = "x", name = "Teferi, Temporal Archmage", typeLine = "Legendary Planeswalker — Teferi", oracleText = "Teferi, Temporal Archmage can be your commander.", colors = listOf("U"), colorIdentity = listOf("U"), cmc = 6.0, loyalty = "5"))

    private fun matches(a: AdvancedFilter, facts: com.mtgcompanion.app.ui.collection.AdvancedFacts?, copies: CopyFacts? = one, toUsd: (Double) -> Double = { it }) =
        advancedMatches(a, facts, copies, toUsd)

    @Test
    fun `numbers - typed numbers only, star and blanks are none`() {
        assertEquals(3.0, numberOf("3"))
        assertEquals(2.5, numberOf(" 2.5 "))
        assertEquals(-1.0, numberOf("-1"))
        assertNull(numberOf("*"))
        assertNull(numberOf("1+*"))
        assertNull(numberOf(""))
        assertNull(numberOf("abc"))
    }

    @Test
    fun `compare - every operator`() {
        assertTrue(compare(3.0, "=", 3.0))
        assertTrue(compare(2.0, "<", 3.0))
        assertTrue(compare(3.0, "<=", 3.0))
        assertTrue(compare(4.0, ">", 3.0))
        assertTrue(compare(3.0, ">=", 3.0))
        assertFalse(compare(3.0, "!=", 3.0))
        assertTrue(compare(4.0, "!=", 3.0))
        assertFalse(compare(3.0, "<", 3.0))
    }

    @Test
    fun `mana symbols - braces, plain letters and hybrid`() {
        assertEquals(listOf("2", "U", "U"), manaSymbols("{2}{U}{U}"))
        assertEquals(listOf("2", "U", "U"), manaSymbols("2uu"))
        assertEquals(listOf("10", "W/U"), manaSymbols("{10}{w/u}"))
        assertEquals(emptyList<String>(), manaSymbols(""))
    }

    @Test
    fun `cost contains - as much generic, and each coloured symbol as often`() {
        assertTrue(costContains(listOf("2", "U", "U"), listOf("1", "U")))
        assertTrue(costContains(listOf("2", "U", "U"), listOf("U", "U")))
        assertFalse(costContains(listOf("2", "U", "U"), listOf("U", "U", "U")))
        assertFalse(costContains(listOf("1", "G"), listOf("2")))
        assertTrue(costContains(listOf("G", "W", "U", "B"), listOf("B", "G")))
    }

    @Test
    fun `no advanced filter lets everything through, even cards not loaded yet`() {
        assertFalse(AdvancedFilter().active)
        assertTrue(advancedMatches(AdvancedFilter(), null, null))
        // A number field that isn't a number yet isn't on.
        assertFalse(AdvancedFilter(mv = "*").active)
        assertFalse(AdvancedFilter(colorTarget = "color", colorMode = "exactly").active)
    }

    @Test
    fun `a card not loaded yet is left out while a filter is on, so is one with no copies while a Your copies filter is`() {
        assertFalse(matches(AdvancedFilter(mv = "3"), null))
        assertFalse(matches(AdvancedFilter(inDeck = "yes"), bolt, null))
        assertTrue(matches(AdvancedFilter(mv = "3", mvOp = "<="), bolt, null))
    }

    @Test
    fun `colours - commander identity at most, including, exactly`() {
        fun ub(mode: String) = AdvancedFilter(colorTarget = "identity", colorMode = mode, colors = listOf("U", "B"))
        assertTrue(matches(ub("atMost"), jace))
        assertTrue(matches(ub("atMost"), solRing))
        assertFalse(matches(ub("atMost"), atraxa))
        assertTrue(matches(ub("including"), atraxa))
        assertFalse(matches(ub("including"), jace))
        assertFalse(matches(ub("exactly"), atraxa))
        assertTrue(matches(AdvancedFilter(colorMode = "exactly", colors = listOf("U")), jace))
    }

    @Test
    fun `colours - card colour reads every face of a double-faced card`() {
        assertTrue(matches(AdvancedFilter(colorTarget = "color", colorMode = "exactly", colors = listOf("U")), delver))
        assertTrue(matches(AdvancedFilter(colorTarget = "color", colorMode = "including", colors = listOf("R")), bolt))
    }

    @Test
    fun `colours - colourless and multicolour`() {
        assertTrue(matches(AdvancedFilter(colors = listOf("C")), solRing))
        assertFalse(matches(AdvancedFilter(colors = listOf("C")), bolt))
        assertTrue(matches(AdvancedFilter(multicolor = true), atraxa))
        assertFalse(matches(AdvancedFilter(multicolor = true), bolt))
        assertTrue(matches(AdvancedFilter(multicolor = true, colorMode = "including", colors = listOf("G")), atraxa))
    }

    @Test
    fun `mana value with an operator`() {
        assertTrue(matches(AdvancedFilter(mvOp = "<=", mv = "3"), bolt))
        assertFalse(matches(AdvancedFilter(mvOp = "<=", mv = "3"), atraxa))
        assertTrue(matches(AdvancedFilter(mvOp = "=", mv = "0"), mox))
        assertFalse(matches(AdvancedFilter(mvOp = "!=", mv = "4"), atraxa))
        assertTrue(matches(AdvancedFilter(mvOp = ">", mv = "3"), atraxa))
        assertFalse(matches(AdvancedFilter(mvOp = ">=", mv = "0"), soldier))
    }

    @Test
    fun `mana cost must hold at least the symbols typed`() {
        assertTrue(matches(AdvancedFilter(manaCost = "{2}{U}{U}"), jace))
        assertTrue(matches(AdvancedFilter(manaCost = "{U}{U}"), jace))
        assertFalse(matches(AdvancedFilter(manaCost = "{U}{U}{U}"), jace))
        assertTrue(matches(AdvancedFilter(manaCost = "u"), delver))
        assertFalse(matches(AdvancedFilter(manaCost = "{R}"), jace))
    }

    @Test
    fun `power and toughness - star fails a number, and every face counts`() {
        assertTrue(matches(AdvancedFilter(powerOp = ">=", power = "4"), atraxa))
        assertFalse(matches(AdvancedFilter(powerOp = ">=", power = "0"), goyf))
        assertFalse(matches(AdvancedFilter(toughnessOp = ">=", toughness = "0"), goyf))
        // Delver's back face is a 3/2.
        assertTrue(matches(AdvancedFilter(powerOp = ">=", power = "3"), delver))
        assertTrue(matches(AdvancedFilter(toughnessOp = "=", toughness = "2"), delver))
        assertFalse(matches(AdvancedFilter(powerOp = ">=", power = "1"), bolt))
    }

    @Test
    fun loyalty() {
        assertTrue(matches(AdvancedFilter(loyaltyOp = ">=", loyalty = "3"), jace))
        assertFalse(matches(AdvancedFilter(loyaltyOp = ">", loyalty = "3"), jace))
        assertFalse(matches(AdvancedFilter(loyaltyOp = ">=", loyalty = "1"), atraxa))
    }

    @Test
    fun `format - legal, banned, restricted`() {
        assertTrue(matches(AdvancedFilter(format = "commander", legality = "legal"), atraxa))
        assertFalse(matches(AdvancedFilter(format = "modern", legality = "legal"), atraxa))
        assertTrue(matches(AdvancedFilter(format = "standard", legality = "banned"), jace))
        assertTrue(matches(AdvancedFilter(format = "vintage", legality = "restricted"), solRing))
        assertFalse(matches(AdvancedFilter(format = "vintage", legality = "restricted"), bolt))
    }

    @Test
    fun `sets - any of the sets picked`() {
        assertTrue(matches(AdvancedFilter(sets = listOf("2xm")), atraxa))
        assertTrue(matches(AdvancedFilter(sets = listOf("isd", "lea")), bolt))
        assertFalse(matches(AdvancedFilter(sets = listOf("isd")), bolt))
    }

    @Test
    fun `card is - commander, Game Changer, Reserved List, double-faced, full art, token`() {
        assertTrue(matches(AdvancedFilter(cardIs = listOf("commander")), atraxa))
        assertTrue(matches(AdvancedFilter(cardIs = listOf("commander")), teferi))
        assertFalse(matches(AdvancedFilter(cardIs = listOf("commander")), jace))
        assertTrue(matches(AdvancedFilter(cardIs = listOf("gamechanger")), jace))
        assertFalse(matches(AdvancedFilter(cardIs = listOf("gamechanger")), atraxa))
        assertTrue(matches(AdvancedFilter(cardIs = listOf("reserved")), mox))
        assertFalse(matches(AdvancedFilter(cardIs = listOf("reserved")), solRing))
        assertTrue(matches(AdvancedFilter(cardIs = listOf("dfc")), delver))
        assertFalse(matches(AdvancedFilter(cardIs = listOf("dfc")), bolt))
        assertTrue(matches(AdvancedFilter(cardIs = listOf("fullart")), mox))
        assertTrue(matches(AdvancedFilter(cardIs = listOf("token")), soldier))
        assertFalse(matches(AdvancedFilter(cardIs = listOf("token")), bolt))
        assertTrue(matches(AdvancedFilter(cardIs = listOf("reserved", "fullart")), mox))
        assertFalse(matches(AdvancedFilter(cardIs = listOf("reserved", "token")), mox))
    }

    @Test
    fun `keywords - every one, in any case`() {
        assertTrue(matches(AdvancedFilter(keywords = "flying, DEATHTOUCH"), atraxa))
        assertFalse(matches(AdvancedFilter(keywords = "flying, trample"), atraxa))
        assertTrue(matches(AdvancedFilter(keywords = " , "), bolt))
    }

    @Test
    fun `price per copy - dollars, the chosen currency, foil when every copy is foil`() {
        assertTrue(matches(AdvancedFilter(priceMin = "10"), atraxa))
        assertFalse(matches(AdvancedFilter(priceMax = "10"), atraxa))
        assertTrue(matches(AdvancedFilter(priceMin = "2", priceMax = "3"), bolt))
        assertTrue(matches(AdvancedFilter(priceMin = "2.5"), bolt))
        // Typed in pounds at 0.8 to the dollar: £16 is $20.
        val toUsd = { n: Double -> n / 0.8 }
        assertTrue(matches(AdvancedFilter(priceMin = "16"), atraxa, one, toUsd))
        assertFalse(matches(AdvancedFilter(priceMin = "17"), atraxa, one, toUsd))
        // Only foils owned: the foil price.
        assertTrue(matches(AdvancedFilter(priceMin = "40"), atraxa, CopyFacts(nonfoil = 0, foil = 1)))
        // No non-foil price: the foil one.
        assertTrue(matches(AdvancedFilter(priceMin = "25"), solRing))
        // No price at all: can't be judged.
        assertFalse(matches(AdvancedFilter(priceMax = "100"), goyf))
    }

    @Test
    fun `artist and flavour text contain, every face too`() {
        assertTrue(matches(AdvancedFilter(artist = "rush"), bolt))
        assertFalse(matches(AdvancedFilter(artist = "guay"), bolt))
        assertTrue(matches(AdvancedFilter(artist = "nils"), delver))
        assertTrue(matches(AdvancedFilter(flavor = "SPARKMAGE"), bolt))
        assertTrue(matches(AdvancedFilter(flavor = "gift for good"), delver))
        assertFalse(matches(AdvancedFilter(flavor = "storm"), atraxa))
    }

    private fun entry(id: String, quantity: Int, foil: Int = 0, condition: String? = null, language: String? = null) =
        CollectionEntry(id, id, null, quantity = quantity, foilQuantity = foil, condition = condition, language = language)

    private val collections = listOf(
        Collection("b1", "Trade binder", listOf(entry("bolt", 2, 1, condition = "LP", language = "ja"), entry("ring", 0, 1)), createdAt = 0, type = "OWNED"),
        Collection("b2", "Bulk", listOf(entry("bolt", 1, 0, condition = "NM")), createdAt = 0, type = "OWNED"),
        Collection("w", "Wishlist", listOf(entry("jace", 1)), createdAt = 0, type = "WISHLIST")
    )
    private val decks = listOf(
        Deck("d1", "Izzet", cards = listOf(DeckCardEntry("bolt", "bolt", null, quantity = 1), DeckCardEntry("jace", "jace", null, quantity = 1)), createdAt = 0)
    )

    @Test
    fun `copies - binders and decks, not wishlists, a copy with no language said is English`() {
        val facts = copyFactsOf(collections, decks)
        assertEquals(CopyFacts(4, 1, listOf("LP", "NM"), listOf("ja", "en"), listOf("b1", "b2"), true, 5, emptyList(), 4), facts["bolt"])
        assertEquals(CopyFacts(1, 0, emptyList(), emptyList(), emptyList(), true, 1, emptyList(), 0), facts["jace"])
        assertEquals(CopyFacts(0, 1, emptyList(), listOf("en"), listOf("b1"), false, 1, emptyList(), 1), facts["ring"])
    }

    @Test
    fun `your copies - finish, etched kept as foil, condition, language, binder, in a deck, copies`() {
        val facts = copyFactsOf(collections, decks)
        val boltCopies = facts["bolt"]
        val ringCopies = facts["ring"]
        assertTrue(matches(AdvancedFilter(finishes = listOf("foil")), bolt, boltCopies))
        assertFalse(matches(AdvancedFilter(finishes = listOf("nonfoil")), solRing, ringCopies))
        // Sol Ring's printing only comes etched: its foil copy is etched.
        assertTrue(matches(AdvancedFilter(finishes = listOf("etched")), solRing, ringCopies))
        assertFalse(matches(AdvancedFilter(finishes = listOf("foil")), solRing, ringCopies))
        assertTrue(matches(AdvancedFilter(finishes = listOf("nonfoil", "etched")), solRing, ringCopies))
        assertTrue(matches(AdvancedFilter(conditions = listOf("NM", "MP")), bolt, boltCopies))
        assertFalse(matches(AdvancedFilter(conditions = listOf("DMG")), bolt, boltCopies))
        assertTrue(matches(AdvancedFilter(language = "ja"), bolt, boltCopies))
        assertFalse(matches(AdvancedFilter(language = "de"), bolt, boltCopies))
        assertTrue(matches(AdvancedFilter(binder = "b2"), bolt, boltCopies))
        assertFalse(matches(AdvancedFilter(binder = "b2"), solRing, ringCopies))
        assertTrue(matches(AdvancedFilter(inDeck = "yes"), bolt, boltCopies))
        assertFalse(matches(AdvancedFilter(inDeck = "no"), bolt, boltCopies))
        assertTrue(matches(AdvancedFilter(inDeck = "no"), solRing, ringCopies))
        assertTrue(matches(AdvancedFilter(copiesOp = ">=", copies = "4"), bolt, boltCopies))
        assertFalse(matches(AdvancedFilter(copiesOp = "<", copies = "2"), bolt, boltCopies))
    }

    @Test
    fun `your copies - the place they are kept, a place inside it, or none yet`() {
        val shelf = StoragePlace("shelf", "Shelf", PlaceKind.SHELF.name, createdAt = 1)
        val red = StoragePlace("red", "Red box", PlaceKind.BOX.name, parentId = "shelf", createdAt = 2)
        val facts = copyFactsOf(listOf(
            Collection(
                UNSORTED_COLLECTION_ID, "Unsorted",
                listOf(entry("bolt", 2).copy(places = listOf(CopyPlace("red", 2))), entry("ring", 1)),
                createdAt = 0, type = "OWNED", storagePlaces = listOf(shelf, red)
            )
        ), emptyList())
        assertEquals(listOf("shelf", "red"), facts["bolt"]?.places)
        assertTrue(matches(AdvancedFilter(place = "shelf"), bolt, facts["bolt"]))
        assertFalse(matches(AdvancedFilter(place = "red"), solRing, facts["ring"]))
        assertTrue(matches(AdvancedFilter(place = "none"), solRing, facts["ring"]))
        assertFalse(matches(AdvancedFilter(place = "none"), bolt, facts["bolt"]))
        assertEquals(listOf("Red box"), filterChips(CollectionFilter(), AdvancedFilter(place = "red"), binderName, usd) { if (it == "red") "Red box" else null }.map { it.label })
        assertEquals(listOf("No place yet"), filterChips(CollectionFilter(), AdvancedFilter(place = "none"), binderName, usd).map { it.label })
        assertEquals("", removeChip(CollectionFilter(), AdvancedFilter(place = "red"), "place").second.place)
        assertEquals(1, AdvancedFilter(place = "red").count)
    }

    @Test
    fun `count - one per chip`() {
        assertEquals(0, AdvancedFilter().count)
        assertEquals(8, AdvancedFilter(colors = listOf("U", "B"), mv = "3", sets = listOf("mh3", "ltr"), cardIs = listOf("reserved"), priceMin = "1", priceMax = "5", finishes = listOf("foil"), inDeck = "no").count)
    }

    private val usd = { n: Double -> "$" + String.format(java.util.Locale.US, "%.2f", n) }
    private val binderName = { id: String -> if (id == "b1") "Trade binder" else null }
    private fun chips(basic: CollectionFilter, a: AdvancedFilter) = filterChips(basic, a, binderName, usd)

    @Test
    fun `query - the mockup's filters`() {
        assertEquals(
            "t:creature id<=ub mv<=3 f:commander",
            scryfallQuery(CollectionFilter(type = "creature"), AdvancedFilter(colorTarget = "identity", colorMode = "atMost", colors = listOf("B", "U"), mvOp = "<=", mv = "3", format = "commander"))
        )
    }

    @Test
    fun `query - every Scryfall field, Your copies left out`() {
        val a = AdvancedFilter(
            colorTarget = "color", colorMode = "including", colors = listOf("G", "W"), multicolor = true, mvOp = "!=", mv = "2", manaCost = "2uu",
            powerOp = ">", power = "3", toughnessOp = "<", toughness = "5", loyaltyOp = "=", loyalty = "4", format = "vintage", legality = "restricted",
            sets = listOf("mh3", "ltr"), cardIs = listOf("token", "commander", "gamechanger", "reserved", "dfc", "fullart"), keywords = "Flying, first strike",
            priceMin = "1", priceMax = "5.5", artist = "Rebecca Guay", flavor = "storms",
            finishes = listOf("foil"), conditions = listOf("NM"), language = "ja", binder = "b1", inDeck = "yes", copies = "2"
        )
        assertEquals(
            "t:legendary t:creature o:\"draw a card\" id>=wu (r:rare or r:mythic) c>=wg c:m mv!=2 m:{2}{U}{U} pow>3 tou<5 loy=4 restricted:vintage (e:mh3 or e:ltr) " +
                "is:commander is:gamechanger is:reserved is:dfc is:fullart t:token kw:flying kw:\"first strike\" usd>=1 usd<=5.5 a:\"Rebecca Guay\" ft:storms",
            scryfallQuery(CollectionFilter(type = "legendary  Creature", text = "draw a card", colors = setOf('U', 'W'), rarities = setOf("mythic", "rare")), a)
        )
        assertEquals("", scryfallQuery(CollectionFilter(), AdvancedFilter(finishes = listOf("foil"), conditions = listOf("NM"), language = "ja", binder = "b1", inDeck = "yes", copies = "2")))
    }

    @Test
    fun `query - colourless, banned, one set, one rarity, prices in another currency`() {
        assertEquals("r:common c=c banned:modern e:lea", scryfallQuery(CollectionFilter(rarities = setOf("common")), AdvancedFilter(colorTarget = "color", colors = listOf("C"), format = "modern", legality = "banned", sets = listOf("lea"))))
        assertEquals("id=r", scryfallQuery(CollectionFilter(), AdvancedFilter(colorTarget = "identity", colorMode = "exactly", colors = listOf("R"))))
        // £8 at 0.8 to the dollar is $10; £1 is $1.25.
        assertEquals("usd>=1.25 usd<=10", scryfallQuery(CollectionFilter(), AdvancedFilter(priceMin = "1", priceMax = "8")) { it / 0.8 })
        assertEquals("0.33", queryNumber(1.0 / 3))
        assertEquals("3", queryNumber(3.0))
    }

    @Test
    fun `chips - the mockup's, short and with mana symbols`() {
        val chips = chips(
            CollectionFilter(type = "creature"),
            AdvancedFilter(colorTarget = "identity", colorMode = "atMost", colors = listOf("B", "U"), mvOp = "<=", mv = "3", format = "commander", sets = listOf("mh3"), finishes = listOf("foil"))
        )
        assertEquals(listOf("Creature", "Identity ≤ UB", "MV ≤ 3", "Legal in Commander", "MH3", "Foil"), chips.map { it.text })
        assertEquals(ActiveChip("colors", "Identity ≤", listOf("U", "B")), chips[1])
    }

    @Test
    fun `chips - every kind`() {
        val chips = chips(
            CollectionFilter(text = "draw a card", colors = setOf('G'), rarities = setOf("rare")),
            AdvancedFilter(
                colorTarget = "color", colors = listOf("C"), multicolor = true, manaCost = "{2}{U}", powerOp = ">=", power = "3", toughnessOp = "<", toughness = "2", loyaltyOp = "=", loyalty = "4",
                format = "vintage", legality = "restricted", cardIs = listOf("reserved", "commander"), keywords = "flying,  deathtouch", priceMin = "1.5", priceMax = "5",
                artist = "Guay", flavor = "storm", finishes = listOf("etched", "nonfoil"), conditions = listOf("DMG", "NM"), language = "ja", binder = "b1", inDeck = "no", copiesOp = ">=", copies = "2"
            )
        )
        assertEquals(listOf(
            "Text: draw a card", "Colour G", "Rare", "Colour C", "Multicolour", "Cost 2U", "Power ≥ 3", "Toughness < 2", "Loyalty = 4",
            "Restricted in Vintage", "Can be a commander", "Reserved List", "Keywords: flying, deathtouch", "Price $1.50–$5.00", "Artist: Guay", "Flavour: storm",
            "Non-foil", "Etched", "NM", "Damaged", "Japanese", "Trade binder", "Not in a deck", "Copies ≥ 2"
        ), chips.map { it.text })
        assertEquals(listOf("Price ≤ $5.00", "In a deck"), chips(CollectionFilter(), AdvancedFilter(priceMax = "5", inDeck = "yes")).map { it.text })
        assertEquals(listOf("Price ≥ $5.00"), chips(CollectionFilter(), AdvancedFilter(priceMin = "5")).map { it.text })
    }

    @Test
    fun `removing a chip takes off just that filter`() {
        val b = CollectionFilter(type = "creature", colors = setOf('U', 'B'))
        val a = AdvancedFilter(sets = listOf("mh3", "ltr"), priceMin = "1", priceMax = "5", inDeck = "yes", colors = listOf("U"))
        assertEquals(setOf('B'), removeChip(b, a, "color:U").first.colors)
        assertEquals(listOf("ltr"), removeChip(b, a, "set:mh3").second.sets)
        val noPrice = removeChip(b, a, "price").second
        assertEquals("", noPrice.priceMin + noPrice.priceMax)
        assertEquals("any", removeChip(b, a, "inDeck").second.inDeck)
        assertEquals(emptyList<String>(), removeChip(b, a, "colors").second.colors)
        assertEquals("", removeChip(b, a, "type").first.type)
        // The rest stays.
        assertEquals(a, removeChip(b, a, "type").second)
    }

    private val savedJson = "[{\"id\":\"s1\",\"name\":\"Cheap blue\",\"basic\":{\"type\":\"creature\",\"text\":\"\",\"colors\":[\"U\",\"B\"],\"rarities\":[\"rare\",\"mythic\"]}," +
        "\"advanced\":{\"colorTarget\":\"identity\",\"colorMode\":\"atMost\",\"colors\":[\"U\",\"B\"],\"multicolor\":false,\"mvOp\":\"<=\",\"mv\":\"3\",\"manaCost\":\"\"," +
        "\"powerOp\":\">=\",\"power\":\"\",\"toughnessOp\":\">=\",\"toughness\":\"\",\"loyaltyOp\":\">=\",\"loyalty\":\"\",\"format\":\"commander\",\"legality\":\"legal\"," +
        "\"sets\":[\"mh3\"],\"cardIs\":[\"commander\",\"token\"],\"keywords\":\"flying\",\"priceMin\":\"\",\"priceMax\":\"5\",\"artist\":\"Rebecca \\\"Becky\\\" Guay\",\"flavor\":\"\"," +
        "\"finishes\":[\"nonfoil\",\"foil\"],\"conditions\":[\"NM\",\"DMG\"],\"language\":\"ja\",\"binder\":\"b1\",\"place\":\"red\",\"inDeck\":\"no\",\"copiesOp\":\">=\",\"copies\":\"2\"}}]"

    @Test
    fun `saved filters - the same JSON text as the web app, and back`() {
        val saved = listOf(SavedFilter(
            id = "s1", name = "Cheap blue",
            basic = CollectionFilter(type = "creature", colors = setOf('B', 'U'), rarities = setOf("mythic", "rare")),
            advanced = AdvancedFilter(
                colors = listOf("B", "U"), mv = "3", format = "commander", sets = listOf("mh3"), cardIs = listOf("token", "commander"), keywords = "flying", priceMax = "5",
                artist = "Rebecca \"Becky\" Guay", finishes = listOf("foil", "nonfoil"), conditions = listOf("DMG", "NM"), language = "ja", binder = "b1", place = "red", inDeck = "no", copies = "2"
            )
        ))
        assertEquals(savedJson, savedFiltersToJson(saved))
        val back = savedFiltersFromJson(savedJson)
        assertEquals(savedJson, savedFiltersToJson(back))
        assertEquals("Rebecca \"Becky\" Guay", back[0].advanced.artist)
    }

    @Test
    fun `saved filters - anything unreadable is skipped, missing fields take their defaults`() {
        assertEquals(emptyList<SavedFilter>(), savedFiltersFromJson("not json"))
        assertEquals(emptyList<SavedFilter>(), savedFiltersFromJson("{\"id\":\"x\"}"))
        assertEquals(emptyList<SavedFilter>(), savedFiltersFromJson(null))
        val read = savedFiltersFromJson("[{\"id\":\"a\",\"name\":\"Old\",\"advanced\":{\"mv\":\"2\",\"mvOp\":\"??\",\"colors\":[\"U\",\"X\"],\"inDeck\":\"maybe\"}},{\"name\":\"no id\"}]")
        assertEquals(1, read.size)
        assertEquals(CollectionFilter(), read[0].basic)
        assertEquals(AdvancedFilter(mv = "2", colors = listOf("U")), read[0].advanced)
    }
}
