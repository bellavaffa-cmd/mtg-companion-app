package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spoiler season. The web app has the same checks — tests/collection/spoilers.test.ts. */
class SpoilersTest {

    private val today = "2026-10-08"

    private fun set(code: String, releasedAt: String?, cardCount: Int = 10, printedSize: Int? = null) =
        SetInfo(code, code.uppercase(), cardCount, releasedAt, null, "expansion", false, printedSize)

    private fun card(
        name: String, typeLine: String = "Creature — Elf Druid", identity: List<String> = listOf("G"), tags: List<String> = emptyList(),
        roles: List<String> = emptyList(), releasedAt: String? = "2026-10-20", legality: String? = "not_legal", id: String = name
    ) = SetCard(id, name, typeLine, identity, tags, null, "rare", roles, releasedAt, null, legality)

    private fun entry(name: String, typeLine: String = "Creature — Elf Druid", tags: List<String> = emptyList()) =
        DeckCardEntry(scryfallId = name, name = name, imageUrl = null, typeLine = typeLine, tags = tags)

    private fun deck(id: String, name: String, cards: List<DeckCardEntry>) =
        Deck(id = id, name = name, commander = entry("Lathril", "Legendary Creature — Elf Noble"), cards = cards, gameMode = GameMode.COMMANDER.name)

    private fun wishlist(vararg entries: CollectionEntry) =
        Collection(WISHLIST_ID, WISHLIST_NAME, entries.toList(), createdAt = 0, type = CollectionType.WISHLIST.name)

    // --- Countdown and revealed count

    @Test
    fun countdownBeforeReleaseOnlyThenNothing() {
        assertEquals("Releases in 5 days", releaseCountdown("2026-10-13", today))
        assertEquals("Releases tomorrow", releaseCountdown("2026-10-09", today))
        assertEquals("Out today", releaseCountdown("2026-10-08", today))
        assertNull(releaseCountdown("2026-10-01", today))
        assertNull(releaseCountdown(null, today))
    }

    @Test
    fun revealedOfTotalWhenScryfallKnowsTheSize() {
        assertEquals("48 of 286 revealed", revealedLabel(set("a", "2026-11-01", 48, 286), today))
        assertEquals("48 revealed", revealedLabel(set("a", "2026-11-01", 48), today))
        // More printings than numbered cards (showcase frames): no "of".
        assertEquals("300 revealed", revealedLabel(set("a", "2026-11-01", 300, 286), today))
        assertEquals("Nothing revealed yet", revealedLabel(set("a", "2026-11-01", 0, 286), today))
        assertEquals("286 cards", revealedLabel(set("a", "2026-10-01", 286, 286), today))
    }

    // --- The pre-release flag's life

    @Test
    fun wantingARevealedCardPutsItOnTheWishlistFlaggedUntilRelease() {
        val c = card("Elvish Spoiler")
        val after = withSpoilerWant(emptyList(), c, 2, "2026-10-20", today)
        val e = after.single { it.isWishlist }.entries.single()
        assertEquals("Elvish Spoiler", e.name)
        assertEquals(2, e.quantity)
        assertEquals("2026-10-20", e.preRelease)
        assertFalse(e.auto)
        assertTrue(isPreRelease(e, today))
        assertEquals("Releases in 12 days", preReleaseLabel(e, today))
        assertEquals(2, wantedCount(after, c.id))

        // A different count replaces it; 0 takes it off.
        val three = withSpoilerWant(after, c, 3, "2026-10-20", today)
        assertEquals(3, wantedCount(three, c.id))
        assertEquals(0, wantedCount(withSpoilerWant(three, c, 0, "2026-10-20", today), c.id))
    }

    @Test
    fun wantingACardAlreadyOutIsAPlainWishlistCard() {
        val after = withSpoilerWant(emptyList(), card("Old News", releasedAt = "2026-10-01"), 1, "2026-10-01", today)
        val e = after.single { it.isWishlist }.entries.single()
        assertNull(e.preRelease)
        assertNull(preReleaseLabel(e, today))
    }

    @Test
    fun wantingUndoesNotInterested() {
        val start = listOf(wishlist().copy(notWanted = listOf("Elvish Spoiler")))
        val after = withSpoilerWant(start, card("Elvish Spoiler"), 1, "2026-10-20", today)
        assertTrue(after.single { it.isWishlist }.notWanted.isEmpty())
    }

    @Test
    fun theFlagClearsOnReleaseDayAndNotBefore() {
        val start = listOf(wishlist(
            CollectionEntry("a", "Soon", null, quantity = 1, preRelease = "2026-10-20"),
            CollectionEntry("b", "Today", null, quantity = 1, preRelease = "2026-10-08"),
            CollectionEntry("c", "Plain", null, quantity = 1)
        ))
        val after = withReleasedCleared(start, today)
        val byId = after.single { it.isWishlist }.entries.associateBy { it.scryfallId }
        assertEquals("2026-10-20", byId.getValue("a").preRelease)
        assertNull(byId.getValue("b").preRelease)
        assertNull(byId.getValue("c").preRelease)
        // Nothing to clear: the same list.
        assertSame(after, withReleasedCleared(after, today))
        // The day before release nothing changes.
        assertSame(start, withReleasedCleared(start, "2026-10-07"))
    }

    @Test
    fun standingCollectionsClearTheFlagGivenToday() {
        val start = listOf(wishlist(CollectionEntry("b", "Today", null, quantity = 1, preRelease = "2026-10-08")))
        val after = withStandingCollections(start, emptyList(), today)
        assertNull(after.single { it.isWishlist }.entries.single().preRelease)
    }

    @Test
    fun anOlderAppsSaveGetsTheFlagBack() {
        val mine = wishlist(CollectionEntry("a", "Soon", null, quantity = 1, preRelease = "2026-10-20"))
        val theirs = wishlist(CollectionEntry("a", "Soon", null, quantity = 2))
        val healed = keepPreReleaseFromOlderApp(mine, theirs)
        assertEquals("2026-10-20", healed.entries.single().preRelease)
        assertEquals(2, healed.entries.single().quantity)
        // Nothing lacking: the same object.
        assertSame(healed, keepPreReleaseFromOlderApp(mine, healed))
    }

    // --- Fits your decks

    private val elves = deck("d1", "Elves", (1..5).map { entry("Elf $it", tags = listOf("Card draw")) })
    private val goblins = deck("d2", "Goblins", (1..5).map { entry("Goblin $it", "Creature — Goblin Warrior") })

    private fun profiles(roles: (String) -> List<String>? = { null }) = listOf(
        deckProfile(elves, listOf("G"), roles),
        deckProfile(goblins, listOf("R"), roles)
    )

    @Test
    fun aCardFitsTheDecksInItsColoursThatShareAType() {
        val fits = cardFits(card("New Elf"), profiles(), today)
        assertEquals(listOf("Elves"), fits.map { it.deckName })
        assertTrue(fits.single().why.startsWith("Elf, like"))
        // Outside the commander's colours: nowhere.
        assertTrue(cardFits(card("Blue Elf", identity = listOf("U")), profiles(), today).isEmpty())
    }

    @Test
    fun rolesAndThemesMatchLikeTypes() {
        // A non-Elf with card draw fits the deck full of card draw.
        val drawer = card("Green Sage", "Creature — Human Druid", tags = listOf("Card Draw"))
        assertEquals(listOf("Elves"), cardFits(drawer, profiles(), today).map { it.deckName })
        // The deck's role tags (from RoleTags) count as its themes: "Token maker" ↔ the card's "Tokens".
        val goblinRoles = profiles { name -> if (name.startsWith("Goblin")) listOf("Token maker") else null }
        val tokens = card("Red Tokens", "Sorcery", identity = listOf("R"), tags = listOf("Tokens"))
        assertEquals(listOf("Goblins"), cardFits(tokens, goblinRoles, today).map { it.deckName })
        assertTrue(cardFits(tokens, profiles(), today).isEmpty())
    }

    @Test
    fun legalityCountsOnlyOnceTheCardIsOut() {
        // Before release every card is "not_legal" on Scryfall: ignored.
        assertEquals(1, cardFits(card("New Elf", releasedAt = "2026-10-20", legality = "not_legal"), profiles(), today).size)
        // Once out, a card not legal in Commander fits nothing; a legal one fits.
        assertTrue(cardFits(card("Banned Elf", releasedAt = "2026-10-01", legality = "banned"), profiles(), today).isEmpty())
        assertEquals(1, cardFits(card("Legal Elf", releasedAt = "2026-10-01", legality = "legal"), profiles(), today).size)
    }

    @Test
    fun cardsInTheDeckAlreadyTokensAndBasicsFitNothing() {
        assertTrue(cardFits(card("Elf 1"), profiles(), today).isEmpty())
        assertTrue(cardFits(card("Elf Warrior", "Token Creature — Elf Warrior"), profiles(), today).isEmpty())
        assertTrue(cardFits(card("Forest", "Basic Land — Forest", identity = emptyList()), profiles(), today).isEmpty())
    }

    @Test
    fun onlyCardsForMyDecksFilter() {
        val cards = listOf(card("New Elf"), card("Blue Thing", "Instant", listOf("U")))
        val fits = fitsByCard(cards, profiles(), today)
        assertEquals(setOf("New Elf"), fits.keys)
        assertEquals(listOf("New Elf"), galleryCards(cards, fits, onlyMine = true).map { it.name })
        assertEquals(2, galleryCards(cards, fits, onlyMine = false).size)
        assertTrue(fitsByCard(cards, emptyList(), today).isEmpty())
    }

    // --- Opening packs

    @Test
    fun openingPacksListsTheWantedCardsFromTheSet() {
        val set = listOf(card("New Elf", id = "p1"), card("Showcase Elf", id = "p2"), card("Reprint Ring", id = "p3"))
        val collections = listOf(wishlist(
            CollectionEntry("p2", "Showcase Elf", null, quantity = 2),
            CollectionEntry("old-ring", "Reprint Ring", null, quantity = 1),
            CollectionEntry("x", "Elsewhere", null, quantity = 1)
        ))
        val packs = openingPacks(collections, set)
        assertEquals(listOf("Reprint Ring", "Showcase Elf"), packs.map { it.entry.name })
        // Another printing wanted: this set's printing is the one pulled.
        assertEquals("p3", packs.first().card.id)
    }

    @Test
    fun tickingAPulledCardAddsItToUnsortedAndWantsOneFewer() {
        val set = listOf(card("Showcase Elf", id = "p2"))
        var collections = listOf(wishlist(CollectionEntry("p2", "Showcase Elf", null, quantity = 2, preRelease = "2026-10-20")))
        collections = withPulled(collections, openingPacks(collections, set).single())
        assertEquals(1, wantedCount(collections, "p2"))
        assertEquals(1, collections.single { it.isUnsorted }.entries.single { it.scryfallId == "p2" }.quantity)
        collections = withPulled(collections, openingPacks(collections, set).single(), foil = true)
        assertEquals(0, wantedCount(collections, "p2"))
        assertTrue(collections.single { it.isWishlist }.entries.isEmpty())
        val pile = collections.single { it.isUnsorted }.entries.single()
        assertEquals(1, pile.quantity)
        assertEquals(1, pile.foilQuantity)
        assertTrue(openingPacks(collections, set).isEmpty())
    }

    // --- The daily news

    @Test
    fun revealNewsOnceADayAndOnlyWhatsNew() {
        assertTrue(mayTellReveals(null, 1_000L))
        assertFalse(mayTellReveals(0L + 1, REVEAL_NEWS_GAP_MS))
        assertTrue(mayTellReveals(1L, 1L + REVEAL_NEWS_GAP_MS))
        // The first look only notes what's there.
        assertTrue(revealNews(null, listOf("a", "b")).isEmpty())
        assertEquals(listOf("c"), revealNews(setOf("a", "b"), listOf("a", "b", "c", "c")))
        assertEquals("1 new card revealed for AAA that fit your decks", revealNewsTitle(listOf(set("aaa", "2026-11-01") to 1)))
        assertEquals("4 new cards revealed that fit your decks", revealNewsTitle(listOf(set("aaa", "2026-11-01") to 1, set("bbb", "2026-11-02") to 3)))
    }
}
