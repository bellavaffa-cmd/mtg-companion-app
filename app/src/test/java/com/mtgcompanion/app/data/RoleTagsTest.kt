package com.mtgcompanion.app.data

import com.mtgcompanion.app.ui.collection.ownedCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Card tags: one search finds cards by name or tag, and the automatic tag binders gather owned
 * cards without counting a copy twice. The web app has the same checks — see
 * MtgCompanionWeb/tests/tags/roleTags.test.ts.
 */
class RoleTagsTest {

    @Test
    fun oneSearchFindsACardByItsNameOrByOneOfItsTags() {
        val tags = listOf("ramp", "mana-rock")
        assertTrue(RoleTags.matches("Sol Ring", tags, "sol"))
        assertTrue(RoleTags.matches("Sol Ring", tags, "ramp"))
        assertTrue(RoleTags.matches("Sol Ring", tags, "Mana Rock"))
        assertFalse(RoleTags.matches("Sol Ring", tags, "removal"))
        assertTrue(RoleTags.matches("Sol Ring", emptyList(), "  "))
    }

    @Test
    fun aSearchSaysWhichTagsItMatched() {
        assertEquals(listOf("ramp", "land-ramp"), RoleTags.matched(listOf("ramp", "land-ramp", "tutor"), "ramp"))
        assertEquals(emptyList<String>(), RoleTags.matched(listOf("ramp"), ""))
        assertEquals("Board wipe", RoleTags.label("board-wipe"))
        assertEquals("board-wipe", RoleTags.byLabel("board wipe")?.id)
    }

    @Test
    fun theTagListMatchesTheWebApp() {
        assertEquals(RoleTags.TAGS.size, RoleTags.TAGS.map { it.id }.toSet().size)
        // The deck stats' four core roles are tags too, under the same Scryfall names.
        DeckRole.TAGGED.forEach { role -> assertTrue(role.label, RoleTags.tag(role.otag!!) != null) }
    }

    @Test
    fun ownedCardsGatherCopiesAcrossBindersButNotWishlists() {
        val sol = { qty: Int -> CollectionEntry("sol-1", "Sol Ring", null, quantity = qty) }
        val owned = ownedCards(
            listOf(
                Collection("a", "My binder", listOf(sol(2), CollectionEntry("cult", "Cultivate", null, quantity = 1))),
                Collection("b", "Trades", listOf(sol(1).copy(foilQuantity = 1))),
                Collection("w", "Wants", listOf(CollectionEntry("opal", "Mox Opal", null, quantity = 1)), type = CollectionType.WISHLIST.name)
            )
        )
        assertEquals(listOf("Cultivate", "Sol Ring"), owned.map { it.name })
        val ring = owned.last()
        assertEquals(4, ring.copies)
        assertEquals(listOf("My binder" to 2, "Trades" to 2), ring.where.map { it.name to it.quantity })
    }
}
