package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.ItemMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gear: sleeves running low, deck boxes and what they hold, tokens, "This deck needs", and two devices'
 * gear merging — the same on both apps. The web app has the same checks — see
 * MtgCompanionWeb/tests/collection/gear.test.ts.
 */
class GearTest {

    private fun card(name: String, quantity: Int = 1) = DeckCardEntry(name.lowercase(), name, null, quantity = quantity)
    private fun deck(id: String, name: String, commander: String?, cards: Int) =
        Deck(id, name, commander = commander?.let { card(it) }, cards = listOf(card("Mountain", cards)), createdAt = 1)

    private val krenko = deck("krenko", "Krenko goblins", "Krenko, Mob Boss", 99)
    private val atraxa = deck("atraxa", "Atraxa", "Atraxa, Praetors' Voice", 99)
    private val limited = deck("limited", "Limited pool", null, 40)
    private val decks = listOf(krenko, atraxa, limited)
    private val tokenBox = StoragePlace("tb", "Token box", PlaceKind.BOX.name, createdAt = 1)
    private val rares = StoragePlace("rares", "Rares binder", PlaceKind.BINDER.name, createdAt = 2)

    private fun g(id: String, kind: GearKind, name: String, count: Int, usedBy: List<String>? = null, holds: String? = null, placeId: String? = null) =
        GearItem(id, kind.name, name, count, usedBy, holds, placeId, createdAt = id.filter { it.isDigit() }.toLongOrNull() ?: 1L)

    private val gear = listOf(
        g("g1", GearKind.SLEEVES, "Black matte sleeves", 38, usedBy = listOf("krenko", "atraxa")),
        g("g2", GearKind.INNER_SLEEVES, "Clear inner sleeves", 412, usedBy = listOf("rares", "krenko")),
        g("g3", GearKind.DECK_BOX, "Red", 1, holds = "krenko"),
        g("g4", GearKind.DECK_BOX, "Black", 1, holds = "atraxa"),
        g("g5", GearKind.DECK_BOX, "White", 1, holds = "limited"),
        g("g6", GearKind.DECK_BOX, "Blue", 1),
        g("g7", GearKind.TOKENS, "Goblin", 24, placeId = "tb"),
        g("g8", GearKind.TOKENS, "Treasure", 18, placeId = "tb"),
        g("g9", GearKind.TOKENS, "Soldier", 12, placeId = "tb"),
        g("g10", GearKind.TOKENS, "Zombie", 32, placeId = "tb")
    )

    private fun pile(gear: List<GearItem>? = null) =
        Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, emptyList(), createdAt = 0, type = CollectionType.OWNED.name, storagePlaces = listOf(tokenBox, rares), gear = gear)

    private val cols = listOf(pile(gear))

    @Test
    fun `a deck takes a sleeve for each card, its commander and its sideboard`() {
        assertEquals(100, sleevesFor(krenko))
        assertEquals(55, sleevesFor(limited.copy(sideboard = listOf(card("Island", 15)))))
        assertEquals("Krenko", shortDeckName(krenko))
        assertEquals("Limited pool", shortDeckName(limited))
    }

    @Test
    fun `sleeves run low when fewer are left than a deck using them takes`() {
        assertTrue(runningLow(gear[0], decks))
        assertFalse(runningLow(gear[0].copy(count = 100), decks))
        assertFalse(runningLow(gear[0].copy(usedBy = emptyList()), decks))
        assertFalse(runningLow(gear[6], decks))
    }

    @Test
    fun `the Gear list - sleeves, inner sleeves, the deck boxes and the tokens as the mockup says them`() {
        val rows = gearRows(gear, decks, cols)
        assertEquals(
            listOf(
                listOf("Black matte sleeves", "38 left", "On Krenko and Atraxa · a deck needs 100 · running low", "true"),
                listOf("Clear inner sleeves", "412 left", "Double-sleeving: Rares binder, Krenko", "false"),
                listOf("Deck boxes", "4 · 1 empty", "Red (Krenko), Black (Atraxa), White (Limited pool), Blue (empty)", "false"),
                listOf("Tokens", "86", "Zombie ×32, Goblin ×24, Treasure ×18 and more · Token box", "false")
            ),
            rows.map { listOf(it.title, it.value, it.line, it.warn.toString()) }
        )
        assertEquals("Goblin ×24, Treasure ×18, Soldier ×12 · Token box", tokensLine(gear.subList(6, 9), cols))
    }

    @Test
    fun `a deck box holding a deck that is gone counts as empty`() {
        val rows = gearRows(listOf(g("g1", GearKind.DECK_BOX, "Red", 1, holds = "gone")), decks, cols)
        assertEquals(listOf("1 · 1 empty", "Red (empty)"), listOf(rows[0].value, rows[0].line))
    }

    @Test
    fun `This deck needs - sleeves, a deck box and its tokens, you have them all or what is missing`() {
        assertEquals(
            "Krenko goblins: 100 sleeves, a deck box and Goblin tokens. You have them all.",
            deckNeedsLine(krenko, deckNeeds(krenko, gear, listOf("Goblin"), decks, cols))
        )
        val fresh = deck("new", "Elves", "Lathril, Blade of the Elves", 99)
        assertEquals(
            "Elves: 100 sleeves, a deck box and Elf Warrior and Goblin tokens. Missing: 100 sleeves, a deck box and Elf Warrior tokens. Blue is an empty deck box.",
            deckNeedsLine(fresh, deckNeeds(fresh, gear, listOf("Elf Warrior", "Goblin"), decks + fresh, cols))
        )
        assertTrue(deckNeeds(fresh, gear + g("g20", GearKind.SLEEVES, "Green", 120), emptyList(), decks + fresh, cols).hasSleeves)
    }

    @Test
    fun `saving and deleting gear keeps it on the Unsorted pile, written the same way`() {
        val saved = saveGear(emptyList(), GearItem("x", GearKind.DICE.name, " Spindown ", 2, usedBy = emptyList(), createdAt = 5))
        assertEquals(listOf(GearItem("x", GearKind.DICE.name, "Spindown", 2, createdAt = 5)), gearOf(saved))
        assertEquals(emptyList<GearItem>(), gearOf(deleteGear(saved, "x")))
        assertEquals(emptyList<GearItem>(), deleteGear(saved, "x").first { it.isUnsorted }.gear)
    }

    @Test
    fun `two devices - added on either kept, deleted on either stays deleted, decks on a pack merge like tags`() {
        val base = listOf(gear[0], gear[6])
        val mine = listOf(gear[0].copy(count = 30, usedBy = listOf("krenko")), g("g30", GearKind.PLAYMAT, "Playmat", 1))
        val theirs = listOf(gear[0].copy(usedBy = listOf("krenko", "atraxa", "limited")), gear[6], g("g31", GearKind.DICE, "Dice", 6))
        val merged = mergeGear(base, mine, theirs, false)!!
        assertEquals(listOf("g1", "g30", "g31"), merged.map { it.id })
        assertEquals(30, merged[0].count)
        assertEquals(listOf("krenko", "limited"), merged[0].usedBy)
        assertNull(mergeGear(null, null, null, true))
    }

    @Test
    fun `a pile saved by an app that does not know gear keeps this device's, also through the merge`() {
        val older = pile()
        assertEquals(gear, keepGearFromOlderApp(pile(gear), older).gear)
        assertSame(older, keepGearFromOlderApp(pile(), older))
        val merged = ItemMerge.mergeCollections(pile(gear), pile(gear + g("g40", GearKind.OTHER, "Life pad", 1)), older, false)
        assertEquals(gear.map { it.id } + "g40", merged.gear?.map { it.id })
    }
}
