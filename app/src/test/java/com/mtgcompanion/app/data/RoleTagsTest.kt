package com.mtgcompanion.app.data

import com.mtgcompanion.app.ui.collection.ownedCards
import com.mtgcompanion.app.ui.collection.ownedForTag
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
    fun taggerFileGivesEachTagItsCardsAndEverythingUnderIt() {
        // Tagger's "removal" has no cards of its own, only the tags under it.
        val lines = listOf(
            """{"id":"t1","slug":"removal","aliases":[],"child_ids":["t2"],"taggings":[]}""",
            """{"id":"t2","slug":"creature-removal","child_ids":["t3"],"taggings":[{"oracle_id":"aaaaaaaa-1111-2222-3333-444444444444"}]}""",
            """{"id":"t3","slug":"removal-exile","child_ids":[],"taggings":[{"oracle_id":"bbbbbbbb-1111-2222-3333-444444444444"}]}""",
            """{"id":"t4","slug":"ramp","child_ids":[],"taggings":[{"oracle_id":"cccccccc-1111-2222-3333-444444444444"}]}""",
            """{"id":"t5","slug":"sweeper","aliases":["mass removal"],"child_ids":[],"taggings":[{"oracle_id":"dddddddd-1111-2222-3333-444444444444"}]}""",
            """{"id":"t6","slug":"unrelated","child_ids":[],"taggings":[{"oracle_id":"eeeeeeee-1111-2222-3333-444444444444"}]}"""
        )
        val sets = RoleTags.parseTagFile { block -> lines.forEach(block) }
        assertEquals(setOf("aaaaaaaa-1111", "bbbbbbbb-1111"), sets["removal"])
        assertEquals(setOf("cccccccc-1111"), sets["ramp"])
        assertEquals(setOf("dddddddd-1111"), sets["board-wipe"])
        assertEquals(listOf("removal"), RoleTags.tagsFor("bbbbbbbb-1111-2222-3333-444444444444", "Exile target creature.", sets))
        assertEquals(emptyList<String>(), RoleTags.tagsFor("eeeeeeee-1111-2222-3333-444444444444", null, sets))
    }

    @Test
    fun tokenAndTreasureTagsReadTheRulesText() {
        assertEquals(listOf("tokens"), RoleTags.tagsFor(null, "Create two 1/1 white Soldier creature tokens.", emptyMap()))
        assertEquals(listOf("treasure"), RoleTags.tagsFor(null, "Whenever an opponent casts a spell, create a Treasure token.", emptyMap()))
        // Doubling Season makes no tokens of its own.
        assertEquals(emptyList<String>(), RoleTags.tagsFor(null, "If an effect would create one or more tokens under your control, it creates twice that many creature tokens instead.", emptyMap()))
    }

    @Test
    fun aDeckShortOfRampIsOfferedTheOwnedRampInItsColours() {
        val owned = ownedCards(
            listOf(
                Collection(
                    "a", "My binder", listOf(
                        CollectionEntry("sol", "Sol Ring", null, quantity = 1),
                        CollectionEntry("cult", "Cultivate", null, quantity = 1),
                        CollectionEntry("signet", "Rakdos Signet", null, quantity = 1),
                        CollectionEntry("study", "Rhystic Study", null, quantity = 1)
                    )
                )
            )
        )
        val tags = mapOf("sol ring" to listOf("ramp"), "cultivate" to listOf("ramp"), "rakdos signet" to listOf("ramp"), "rhystic study" to listOf("draw"))
        val identity = mapOf("sol ring" to "", "cultivate" to "G", "rakdos signet" to "BR", "rhystic study" to "U", "omnath, locus of mana" to "G")
        val tagsOf = { name: String -> tags[RoleTags.key(name)] }
        val identityOf = { name: String -> identity[RoleTags.key(name)] }
        val entry = { name: String -> DeckCardEntry(name, name, null) }

        // Sol Ring is already in the deck; the Signet is off-colour for a green commander.
        val green = Deck("d", "Omnath", commander = entry("Omnath, Locus of Mana"), cards = listOf(entry("Sol Ring")))
        assertEquals(listOf("Cultivate"), ownedForTag(owned, green, "ramp", tagsOf, identityOf).map { it.name })
        // No commander: any colour.
        assertEquals(listOf("Cultivate", "Rakdos Signet"), ownedForTag(owned, green.copy(commander = null), "ramp", tagsOf, identityOf).map { it.name })
        // The commander's colours not known yet: nothing, rather than off-colour cards.
        assertEquals(emptyList<String>(), ownedForTag(owned, green.copy(commander = entry("Unknown")), "ramp", tagsOf, identityOf).map { it.name })
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
