package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** New sets and the cards in them for the user's decks. The web app has the same checks — tests/collection/newSets.test.ts. */
class NewSetsTest {

    private fun set(code: String, releasedAt: String?, cardCount: Int = 10, setType: String = "expansion", digital: Boolean = false) =
        SetInfo(code, code.uppercase(), cardCount, releasedAt, null, setType, digital)

    private fun entry(name: String, typeLine: String, tags: List<String> = emptyList(), categories: List<String>? = null) =
        DeckCardEntry(scryfallId = name, name = name, imageUrl = null, typeLine = typeLine, tags = tags, categories = categories)

    private fun card(name: String, typeLine: String, identity: List<String>, tags: List<String> = emptyList()) =
        SetCard(name, name, typeLine, identity, tags, null, "rare")

    private fun deck(cards: List<DeckCardEntry>, id: String = "d1", gameMode: String = GameMode.COMMANDER.name, archived: Boolean? = null, commander: DeckCardEntry? = entry("Lathril", "Legendary Creature — Elf Noble")) =
        Deck(id = id, name = "Elves", commander = commander, cards = cards, gameMode = gameMode, archived = archived)

    @Test
    fun upcomingSetsSoonestFirstJustOutNewestFirstOddSetsLeftOut() {
        val lists = releaseSets(
            listOf(
                set("far", "2027-02-01"),
                set("soon", "2026-11-14"),
                set("today", "2026-10-07"),
                set("week", "2026-09-30"),
                set("old", "2026-08-01"),
                set("tok", "2026-11-14", setType = "token"),
                set("arena", "2026-11-01", digital = true),
                set("nodate", null)
            ),
            "2026-10-07"
        )
        assertEquals(listOf("soon", "far"), lists.upcoming.map { it.code })
        assertEquals(listOf("today", "week"), lists.recent.map { it.code })
    }

    @Test
    fun releaseAndCardLabels() {
        assertEquals("Out today", releaseLabel("2026-10-07", "2026-10-07"))
        assertEquals("Out tomorrow", releaseLabel("2026-10-08", "2026-10-07"))
        assertEquals("In 12 days", releaseLabel("2026-10-19", "2026-10-07"))
        assertEquals("Out 3 days ago", releaseLabel("2026-10-04", "2026-10-07"))
        assertEquals("No cards shown yet", cardsLabel(set("a", "2026-11-01", cardCount = 0), "2026-10-07"))
        assertEquals("12 cards shown so far", cardsLabel(set("a", "2026-11-01", cardCount = 12), "2026-10-07"))
        assertEquals("286 cards", cardsLabel(set("a", "2026-10-01", cardCount = 286), "2026-10-07"))
    }

    @Test
    fun followedSetsAreAnnouncedOnceOnReleaseDayOrTheWeekAfter() {
        val sets = listOf(set("a", "2026-10-07"), set("b", "2026-10-08"), set("c", "2026-09-20"), set("d", "2026-10-05"))
        val followed = setOf("a", "b", "c", "d")
        assertEquals(listOf("a", "d"), setsToAnnounce(followed, sets, "2026-10-07", emptySet()).map { it.code })
        assertEquals(listOf("d"), setsToAnnounce(followed, sets, "2026-10-07", setOf("a")).map { it.code })
        assertEquals(emptyList<SetInfo>(), setsToAnnounce(setOf("b"), sets, "2026-10-07", emptySet()))
    }

    @Test
    fun creatureTypesAndThemeSpellings() {
        assertEquals(listOf("Elf", "Druid"), creatureTypes("Legendary Creature — Elf Druid"))
        assertEquals(listOf("Elf"), creatureTypes("Kindred Instant — Elf"))
        assertEquals(emptyList<String>(), creatureTypes("Artifact — Equipment"))
        assertEquals(listOf("Human", "Wizard", "Insect"), creatureTypes("Creature — Human Wizard // Creature — Human Insect"))
        assertEquals("card draw", themeKey("Draw"))
        assertEquals("card draw", themeKey("Card Draw"))
        assertEquals("board wipe", themeKey("Board wipes"))
    }

    @Test
    fun cardsFitADeckByColourIdentityAndWhatTheDeckHasPlentyOf() {
        val elves = (0 until 5).map { entry("Elf $it", "Creature — Elf Druid", listOf("Flying")) }
        val tokens = (0 until 4).map { i -> entry("Maker $i", "Sorcery", if (i < 2) listOf("Tokens") else emptyList(), if (i >= 2) listOf("Tokens") else null) }
        val ramp = (0 until 3).map { entry("Rock $it", "Artifact", categories = listOf("Ramp")) }
        val p = deckProfile(deck(elves + tokens + ramp), listOf("G", "B"))
        // Six elves with the commander; four token makers (tags and categories together); ramp only three.
        assertEquals(6, p.types["Elf"]?.count)
        assertEquals(4, p.themes["tokens"]?.count)
        assertFalse("ramp" in p.themes)
        // Flying is too common to say anything.
        assertFalse("flying" in p.themes)

        val fits = deckFits(
            p,
            listOf(
                card("Elf Lord", "Creature — Elf Warrior", listOf("G"), listOf("Tokens")),
                card("Green Elf", "Creature — Elf", listOf("G")),
                card("Blue Elf", "Creature — Elf", listOf("U")),
                card("Token Spell", "Instant", listOf("B"), listOf("Tokens")),
                card("Plain Rock", "Artifact", emptyList(), listOf("Ramp")),
                card("Flyer", "Creature — Bird", listOf("G"), listOf("Flying")),
                card("Elf 0", "Creature — Elf Druid", listOf("G")),
                card("Forest", "Basic Land — Forest", listOf("G")),
                card("Elf Token", "Token Creature — Elf", listOf("G"))
            ),
            8
        )
        assertEquals(listOf("Elf Lord", "Green Elf", "Token Spell"), fits.map { it.card.name })
        assertEquals("Elf, like 6 cards in the deck · Tokens, like 4", fitReason(fits[0]))
        assertEquals(10, fits[0].score)
    }

    @Test
    fun onlyCommanderDecksWithACommanderNotPutAway() {
        val decks = listOf(
            deck(emptyList()),
            deck(emptyList(), id = "m", gameMode = GameMode.MODERN.name),
            deck(emptyList(), id = "a", archived = true),
            deck(emptyList(), id = "n", commander = null)
        )
        assertEquals(listOf("d1"), commanderDecks(decks).map { it.id })
    }

    @Test
    fun reprintsOfWishlistCardsOnceEachDoubleFacedByTheirFront() {
        val wanted = setOf("sol ring", "delver of secrets")
        val found = wishlistReprints(
            wanted,
            listOf(
                card("Sol Ring", "Artifact", emptyList()),
                card("Sol Ring", "Artifact", emptyList()),
                card("Delver of Secrets // Insectile Aberration", "Creature — Human Wizard // Creature — Human Insect", listOf("U")),
                card("Arcane Signet", "Artifact", emptyList())
            )
        )
        assertEquals(listOf("Sol Ring", "Delver of Secrets // Insectile Aberration"), found.map { it.name })
        assertTrue(found.isNotEmpty())
    }
}
