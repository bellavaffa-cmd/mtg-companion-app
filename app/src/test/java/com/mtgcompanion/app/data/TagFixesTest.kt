package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallCollectionResponse
import com.mtgcompanion.app.network.scryfall.ScryfallIdentifier
import com.mtgcompanion.app.ui.collection.CollectionFilter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Card tags reach every card (tag-fixes): fallback tags from the text and type line, lookups by
 * printing id first, retries that carry on where they stopped, a new Tagger file re-tagging what's
 * known, and the one-off keyword fill-in. The web app has the same cases —
 * MtgCompanionWeb/tests/tags/tagFixes.test.ts.
 */
class TagFixesTest {
    private val sol = "11111111-aaaa-bbbb-cccc-000000000001"
    private val ringOracle = "aaaaaaaa-0000-0000-0000-000000000001"
    private val sets = mapOf("ramp" to setOf(ringOracle.take(13)), "mana-rock" to setOf(ringOracle.take(13)))

    // ---- Fallback tags: the same vectors as the web app's ----

    @Test
    fun landsFixingAndVanillaComeFromTheTypeLineAndText() {
        assertEquals(listOf("land"), RoleTags.textTags("({T}: Add {G}.)", "Basic Land — Forest").always)
        assertEquals(listOf("land", "fixing"), RoleTags.textTags("({T}: Add {G} or {W}.)\nAs Breeding Pool enters, you may pay 2 life.", "Land — Forest Plains").always)
        assertEquals(listOf("land", "fixing"), RoleTags.textTags("{T}, Sacrifice Evolving Wilds: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.", "Land").always)
        assertEquals(listOf("land", "fixing"), RoleTags.textTags("{T}: Add {C}.\n{T}: Add one mana of any color in your commander's color identity.", "Land", listOf("B", "G", "R", "U", "W")).always)
        assertEquals(listOf("fixing"), RoleTags.textTags("{T}: Add one mana of any color.", "Artifact").always)
        assertEquals(listOf("vanilla"), RoleTags.textTags("", "Creature — Bear").always)
        // A creature that taps for any colour is ramp, not fixing (lands and artifacts only).
        assertEquals(emptyList<String>(), RoleTags.textTags("{T}: Add one mana of any color.", "Creature — Bird").always)
        // A modal double-faced spell with a land back is the spell.
        assertFalse("land" in RoleTags.textTags("Destroy target creature.", "Instant // Land").always)
    }

    @Test
    fun rampDrawAndRemovalFromTheTextOnlyWhenTaggerHasNothing() {
        val signet = RoleTags.textTags("{1}, {T}: Add {B}{R}.", "Artifact")
        assertEquals(listOf("ramp"), signet.fallback)
        assertEquals(listOf("draw"), RoleTags.textTags("Draw two cards.", "Sorcery").fallback)
        assertEquals(listOf("removal"), RoleTags.textTags("Destroy target creature. It can't be regenerated.", "Instant").fallback)
        assertEquals(listOf("removal"), RoleTags.textTags("Lightning Bolt deals 3 damage to any target.", "Instant").fallback)
        // A land's mana is not ramp.
        assertEquals(emptyList<String>(), RoleTags.textTags("({T}: Add {G}.)", "Basic Land — Forest").fallback)
        // Tagger knows the card: its tags only, the text's guesses left out.
        val text = TextTags(fallback = listOf("draw"))
        assertEquals(listOf("ramp", "mana-rock"), RoleTags.combine(ringOracle, text, sets))
        // Tagger has nothing for it (or no file yet): the text's guesses count.
        assertEquals(listOf("draw"), RoleTags.combine("bbbbbbbb-0000-0000-0000-000000000002", text, sets))
        assertEquals(listOf("draw"), RoleTags.combine(null, text, emptyMap()))
        // What the text always says is kept either way.
        assertEquals(listOf("ramp", "mana-rock", "fixing"), RoleTags.combine(ringOracle, TextTags(always = listOf("fixing")), sets))
    }

    @Test
    fun theTagListIsTheWebAppsAndEveryTagHasASource() {
        assertEquals(
            listOf(
                "ramp", "mana-rock", "mana-dork", "land-ramp", "mana-engine", "draw", "tutor", "removal", "board-wipe", "counterspell",
                "protection", "recursion", "reanimate", "sacrifice-outlet", "tokens", "treasure", "tax", "lifegain", "burn",
                "graveyard-hate", "extra-turn", "wheel", "land", "fixing", "vanilla"
            ),
            RoleTags.TAGS.map { it.id }
        )
        RoleTags.TAGS.forEach { assertTrue(it.id, it.slugs.isNotEmpty() || it.text != null || it.derived) }
    }

    // ---- When a card is looked up again ----

    @Test
    fun aCardIsDueAgainAfterAMonthAWeekWithNoTagsOrUnderANewFile() {
        val now = 100L * 24 * 60 * 60 * 1000
        val day = 24 * 60 * 60 * 1000L
        assertTrue(RoleTags.isDue(null, now, "f1"))
        assertFalse(RoleTags.isDue(TagRecord(listOf("ramp"), now - 20 * day, oracle = "x", file = "f1"), now, "f1"))
        assertTrue(RoleTags.isDue(TagRecord(listOf("ramp"), now - 31 * day, oracle = "x", file = "f1"), now, "f1"))
        // No tags: a week, not a month.
        assertFalse(RoleTags.isDue(TagRecord(emptyList(), now - 6 * day, oracle = "x", file = "f1"), now, "f1"))
        assertTrue(RoleTags.isDue(TagRecord(emptyList(), now - 8 * day, oracle = "x", file = "f1"), now, "f1"))
        // Not found under the old file: tried again with the new one. Found: re-tagged without a lookup (below).
        assertTrue(RoleTags.isDue(TagRecord(emptyList(), now - day, file = "f1"), now, "f2"))
        assertFalse(RoleTags.isDue(TagRecord(emptyList(), now - day, oracle = "x", file = "f1"), now, "f2"))
        // Retag now: due at once.
        assertTrue(RoleTags.isDue(TagRecord(listOf("ramp"), 0L, oracle = "x", file = "f1"), now, "f1"))
    }

    @Test
    fun aNewTaggerFileRetagsEveryKnownCardWithoutALookup() {
        val records = mapOf(
            "sol ring" to TagRecord(emptyList(), 5L, oracle = ringOracle.take(13), text = TextTags(fallback = listOf("draw")), file = "f1"),
            "mystery" to TagRecord(emptyList(), 5L, file = "f1"),
            "current" to TagRecord(listOf("draw"), 5L, oracle = "cccccccc-0000", file = "f2")
        )
        val again = RoleTags.retagged(records, sets, "f2")!!
        assertEquals(listOf("ramp", "mana-rock"), again.getValue("sol ring").tags)
        assertEquals("f2", again.getValue("sol ring").file)
        // Kept as it was: still when it was looked up, so not looked up again because of this.
        assertEquals(5L, again.getValue("sol ring").at)
        assertSame(records.getValue("mystery"), again.getValue("mystery"))
        assertSame(records.getValue("current"), again.getValue("current"))
        assertNull(RoleTags.retagged(mapOf("current" to records.getValue("current")), sets, "f2"))
    }

    @Test
    fun theCacheFileKeepsEverythingAndAnOlderOneStillShowsItsTags() {
        val records = mapOf(
            "sol ring" to TagRecord(listOf("ramp"), 7L, "", ringOracle.take(13), TextTags(listOf("fixing"), listOf("draw")), "f1"),
            "nobody" to TagRecord(emptyList(), 8L)
        )
        assertEquals(records, RoleTags.readCards(RoleTags.writeCards(records)))
        val old = RoleTags.readCards("""{"v":4,"cards":{"sol ring":{"t":["ramp"],"at":99,"c":""}}}""")
        assertEquals(listOf("ramp"), old.getValue("sol ring").tags)
        assertTrue(RoleTags.isDue(old.getValue("sol ring"), System.currentTimeMillis(), null))
    }

    // ---- Looking up: id first, name as a fallback, retry and resume ----

    private fun card(id: String, name: String, oracle: String?, text: String = "", type: String = "Artifact") =
        ScryfallCard(id = id, oracleId = oracle, name = name, oracleText = text, typeLine = type, colorIdentity = emptyList())

    @Test
    fun aCardIsLookedUpByItsPrintingFirstSoProxiesAndRenamedCardsResolve() = runBlocking {
        val asked = mutableListOf<List<ScryfallIdentifier>>()
        val ring = card(sol, "Sol Ring", ringOracle, "{T}: Add {C}{C}.")
        val elf = card("22222222-aaaa-bbbb-cccc-000000000002", "Llanowar Elves", "dddddddd-0000-0000-0000-000000000004", "{T}: Add {G}.", "Creature — Elf Druid")
        val fetch: suspend (List<ScryfallIdentifier>) -> ScryfallCollectionResponse = { ids ->
            asked += ids
            ScryfallCollectionResponse(data = ids.mapNotNull { i ->
                when {
                    i.id == sol -> ring
                    i.name == "Llanowar Elves" -> elf
                    else -> null
                }
            }, notFound = ids.filter { it.id != sol && it.name != "Llanowar Elves" })
        }
        val found = HashMap<String, TagRecord>()
        val refs = listOf(
            TagRef("My Sol Ring proxy", sol),
            // A printing Scryfall doesn't know: asked again by name.
            TagRef("Llanowar Elves", "99999999-aaaa-bbbb-cccc-000000000009"),
            // A made-up id (a sample's) never goes to Scryfall as an id.
            TagRef("Treasure", "sample-treasure")
        )
        val ok = RoleTags.lookUp(refs, fetch, sets, "f1", sleep = {}) { m, _ -> found += m }
        assertTrue(ok)
        assertEquals(sol, asked[0][0].id)
        assertEquals("Treasure", asked[0][2].name)
        assertEquals("Llanowar Elves", asked[1][0].name)
        assertEquals(listOf("ramp", "mana-rock"), found.getValue("my sol ring proxy").tags)
        // ...and under its Scryfall name too, for screens asking by it.
        assertEquals(listOf("ramp", "mana-rock"), found.getValue("sol ring").tags)
        // Tagger doesn't know the elf (in this file): its text says ramp.
        assertEquals(listOf("ramp"), found.getValue("llanowar elves").tags)
        assertEquals("dddddddd-0000", found.getValue("llanowar elves").oracle)
        // Not found at all: remembered with no tags and no Oracle id.
        assertEquals(TagRecord(emptyList(), found.getValue("treasure").at, file = "f1"), found.getValue("treasure"))
    }

    @Test
    fun aFailedRequestIsRetriedWithBackoffAndAStoppedLookupResumesWhereItStopped() = runBlocking {
        val refs = (1..160).map { TagRef("Card $it") }
        var calls = 0
        var failing = 0
        val slept = mutableListOf<Long>()
        val fetch: suspend (List<ScryfallIdentifier>) -> ScryfallCollectionResponse = { ids ->
            calls++
            if (failing > 0) { failing--; throw IOException("offline") }
            ScryfallCollectionResponse(data = ids.map { card("id-${it.name}", it.name!!, null) })
        }
        val cache = HashMap<String, TagRecord>()
        // The second request fails twice, then works: backoff, and all 160 found.
        failing = 0
        var n = 0
        val flaky: suspend (List<ScryfallIdentifier>) -> ScryfallCollectionResponse = { ids ->
            n++
            if (n == 2 || n == 3) throw IOException("offline")
            fetch(ids)
        }
        assertTrue(RoleTags.lookUp(refs, flaky, emptyMap(), null, retries = listOf(10L, 20L), sleep = { slept += it }) { m, _ -> cache += m })
        assertEquals(listOf(10L, 20L), slept)
        assertEquals(160, refs.count { RoleTags.key(it.name) in cache })

        // Offline for good after the first 75: stops, keeping those 75...
        cache.clear()
        calls = 0
        var m = 0
        val dies: suspend (List<ScryfallIdentifier>) -> ScryfallCollectionResponse = { ids -> if (++m > 1) throw IOException("offline") else fetch(ids) }
        assertFalse(RoleTags.lookUp(refs, dies, emptyMap(), null, retries = listOf(1L), sleep = {}) { r, _ -> cache += r })
        assertEquals(75, cache.size)
        // ...and the next pass asks only for the rest.
        val now = System.currentTimeMillis()
        val left = refs.filter { RoleTags.isDue(cache[RoleTags.key(it.name)], now, null) }
        assertEquals(85, left.size)
        val asked = mutableListOf<String>()
        assertTrue(RoleTags.lookUp(left, { ids -> asked += ids.map { it.name!! }; fetch(ids) }, emptyMap(), null, sleep = {}) { r, _ -> cache += r })
        assertEquals("Card 76", asked.first())
        assertEquals(160, cache.size)
    }

    // ---- The pass covers every card the user has ----

    @Test
    fun thePassCoversBindersTheWishlistAndEveryPartOfADeck() {
        val collections = listOf(
            Collection("a", "Binder", listOf(CollectionEntry(sol, "Sol Ring", null, quantity = 1))),
            Collection("w", "Wishlist", listOf(CollectionEntry("33333333-aaaa-bbbb-cccc-000000000003", "Mox Opal", null)), type = CollectionType.WISHLIST.name)
        )
        val e = { name: String -> DeckCardEntry("sample-$name", name, null) }
        val decks = listOf(Deck("d", "Deck", commander = e("Omnath"), cards = listOf(e("Omnath"), e("sol ring")), considering = listOf(e("Cultivate")), sideboard = listOf(e("Duress"))))
        val refs = TagPass.refsOf(collections, decks)
        assertEquals(listOf("Sol Ring", "Mox Opal", "Omnath", "Cultivate", "Duress"), refs.map { it.name })
        // One ref a name, with a real printing id where any copy has one.
        assertEquals(sol, refs.first().scryfallId)
        assertNull(refs.first { it.name == "Omnath" }.scryfallId)
    }

    // ---- Untagged and the user's own tags in the Cards filter ----

    @Test
    fun theCardsFilterFindsUntaggedCardsAndTheUsersOwnTags() {
        val untagged = CollectionFilter(untagged = true)
        assertTrue(untagged.active)
        assertTrue(untagged.matches(null))
        assertTrue(untagged.matchesTags(emptyList(), emptyList()))
        assertFalse(untagged.matchesTags(listOf("ramp"), emptyList()))
        // Not looked up yet: not known to be untagged.
        assertFalse(untagged.matchesTags(null, emptyList()))
        val mine = CollectionFilter(userTags = setOf("signed"))
        assertTrue(mine.matchesTags(null, listOf("Signed", "proxy")))
        assertFalse(mine.matchesTags(listOf("ramp"), listOf("proxy")))
        assertEquals(2, CollectionFilter(untagged = true, userTags = setOf("signed")).count)
    }

    // ---- The one-off keyword fill-in ----

    @Test
    fun oldEntriesGetTheirKeywordTagsInOneChange() = runBlocking {
        val bird = "44444444-aaaa-bbbb-cccc-000000000004"
        val collections = listOf(
            Collection("a", "Binder", listOf(CollectionEntry(bird, "Birds of Paradise", null, quantity = 1), CollectionEntry(sol, "Sol Ring", null, quantity = 1, tags = listOf("Ramp"))))
        )
        val decks = listOf(Deck("d", "Deck", cards = listOf(DeckCardEntry(bird, "Birds of Paradise", null))))
        assertEquals(listOf(bird), EntryTagBackfill.idsNeeding(collections, decks))
        val fetch: suspend (List<ScryfallIdentifier>) -> ScryfallCollectionResponse = { ids ->
            ScryfallCollectionResponse(data = ids.map { ScryfallCard(id = it.id!!, name = "Birds of Paradise", keywords = listOf("Flying"), oracleText = "Flying\n{T}: Add one mana of any color.") })
        }
        val tags = EntryTagBackfill.tagsById(listOf(bird), fetch)!!
        assertEquals(listOf("Flying"), tags.getValue(bird).take(1))
        val filled = EntryTagBackfill.filled(collections, tags)
        assertEquals(tags.getValue(bird), filled[0].entries[0].tags)
        // An entry that had tags keeps its own.
        assertEquals(listOf("Ramp"), filled[0].entries[1].tags)
        assertEquals(tags.getValue(bird), EntryTagBackfill.filledDecks(decks, tags)[0].cards[0].tags)
        // Nothing to fill: the same lists, so nothing is written.
        assertSame(filled, EntryTagBackfill.filled(filled, tags))
        assertSame(decks, EntryTagBackfill.filledDecks(decks, emptyMap()))
        // Offline: nothing at all, to be tried again.
        assertNull(EntryTagBackfill.tagsById(listOf(bird)) { throw IOException("offline") })
    }
}
