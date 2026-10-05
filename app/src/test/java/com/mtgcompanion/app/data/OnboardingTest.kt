package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The welcome flow's steps and the sample content. The web app has the same cases — see
 * MtgCompanionWeb/tests/onboarding/onboarding.test.ts.
 */
class OnboardingTest {

    private fun facts(
        cards: Int = 0, decks: Int = 0, samples: Boolean = false,
        accountsAvailable: Boolean = true, signedIn: Boolean = false, hasProfile: Boolean = false
    ) = WelcomeFacts(cards, decks, samples, accountsAvailable, signedIn, hasProfile)

    private fun deck(id: String, sample: Boolean = false) = Deck(id = id, name = id, ownership = "VIRTUAL", sample = if (sample) true else null)
    private fun binder(id: String, copies: Int, type: String = "OWNED", sample: Boolean = false) = Collection(
        id = id, name = id, type = type, sample = if (sample) true else null,
        entries = if (copies > 0) listOf(CollectionEntry("x", "X", null, quantity = copies, foilQuantity = 1)) else emptyList()
    )

    @Test
    fun `each step is done by what the library and account hold`() {
        assertEquals(false, stepDone(WelcomeStep.COLLECTION, facts()))
        assertEquals(true, stepDone(WelcomeStep.COLLECTION, facts(cards = 3)))
        assertEquals(true, stepDone(WelcomeStep.DECK, facts(decks = 1)))
        // Signed in is not enough: friends need a username.
        assertEquals(false, stepDone(WelcomeStep.ACCOUNT, facts(signedIn = true)))
        assertEquals(true, stepDone(WelcomeStep.ACCOUNT, facts(signedIn = true, hasProfile = true)))
        // A build without accounts has nothing to do there.
        assertEquals(true, stepDone(WelcomeStep.ACCOUNT, facts(accountsAvailable = false)))
        assertEquals(false, stepDone(WelcomeStep.DONE, facts(cards = 1, decks = 1, signedIn = true, hasProfile = true)))
    }

    @Test
    fun `the steps still to do, and where a resumed flow opens`() {
        assertEquals(listOf(WelcomeStep.COLLECTION, WelcomeStep.DECK, WelcomeStep.ACCOUNT), stepsToDo(facts()))
        assertEquals(listOf(WelcomeStep.DECK, WelcomeStep.ACCOUNT), stepsToDo(facts(cards = 5)))
        assertEquals(WelcomeStep.DECK, firstStepToDo(facts(cards = 5)))
        assertEquals(WelcomeStep.DONE, firstStepToDo(facts(cards = 5, decks = 1, signedIn = true, hasProfile = true)))
        assertEquals(WelcomeStep.DECK, nextStep(WelcomeStep.COLLECTION))
        assertEquals(WelcomeStep.DONE, nextStep(WelcomeStep.ACCOUNT))
        assertEquals(WelcomeStep.DONE, nextStep(WelcomeStep.DONE))
        assertEquals(WelcomeStep.DECK, WelcomeStep.fromKey("deck"))
        assertNull(WelcomeStep.fromKey("nope"))
    }

    @Test
    fun `the flow opens by itself only once, on a first open with an empty library`() {
        val fresh = WelcomeState(finished = null, opened = false)
        assertEquals(true, shouldOpenWelcome(fresh, facts()))
        assertEquals(false, shouldOpenWelcome(fresh.copy(opened = true), facts()))
        assertEquals(false, shouldOpenWelcome(fresh.copy(finished = "skipped"), facts()))
        assertEquals(false, shouldOpenWelcome(fresh.copy(finished = "done"), facts()))
        // Someone who already has decks or cards (an update, a second device) isn't interrupted…
        assertEquals(false, shouldOpenWelcome(fresh, facts(decks = 2)))
        assertEquals(false, shouldOpenWelcome(fresh, facts(cards = 1)))
        // …nor is someone signed in whose library hasn't come down yet.
        assertEquals(false, shouldOpenWelcome(fresh, facts(signedIn = true)))
        // Samples aren't the user's own: Home still offers to get started.
        assertEquals(true, showGetStarted(facts(samples = true)))
        assertEquals(false, showGetStarted(facts(decks = 1)))
    }

    @Test
    fun `what the flow remembers reads back, and anything else reads as new`() {
        assertEquals(WelcomeState("skipped", true), parseWelcomeState(welcomeStateJson(WelcomeState("skipped", true))))
        assertEquals(WelcomeState("done", false), parseWelcomeState(welcomeStateJson(WelcomeState("done", false))))
        assertEquals(WelcomeState(null, true), parseWelcomeState(welcomeStateJson(WelcomeState(null, true))))
        assertEquals(WelcomeState(), parseWelcomeState(null))
        assertEquals(WelcomeState(), parseWelcomeState("{not json"))
        assertEquals(WelcomeState(), parseWelcomeState("{\"finished\":\"maybe\",\"opened\":\"yes\"}"))
        // The web's stored form reads the same.
        assertEquals(WelcomeState("done", true), parseWelcomeState("{\"finished\":\"done\",\"opened\":true}"))
    }

    @Test
    fun `library facts leave out samples and the Wishlist`() {
        val decks = listOf(deck("d1"), deck("s1", sample = true))
        val collections = listOf(
            binder("b1", 2), // 2 + 1 foil
            binder("unsorted", 1), // the pile counts: they're cards the user owns
            binder("wishlist", 4, type = "WISHLIST"),
            binder("s2", 9, sample = true)
        )
        val f = libraryFacts(decks, collections)
        assertEquals(Triple(5, 1, true), Triple(f.cards, f.decks, f.samples))
        val empty = libraryFacts(emptyList(), listOf(binder("wishlist", 0, type = "WISHLIST")))
        assertEquals(Triple(0, 0, false), Triple(empty.cards, empty.decks, empty.samples))
    }

    @Test
    fun `samples are flagged, removed together, and never synced`() {
        val decks = listOf(deck("d1"), deck("s1", sample = true))
        val collections = listOf(binder("b1", 1), binder("s2", 1, sample = true))
        assertEquals(true, isSample(decks[1]))
        assertEquals(false, isSample(decks[0]))
        assertEquals(listOf("d1"), withoutSampleDecks(decks).map { it.id })
        assertEquals(listOf("b1"), withoutSampleCollections(collections).map { it.id })
        // Left out of the JSON on everything that isn't a sample, so other decks' JSON is unchanged.
        assertEquals(false, localMoshi.adapter(Deck::class.java).toJson(decks[0]).contains("sample"))
    }

    @Test
    fun `the sample precon is a well-known one when it is there, else the newest`() {
        val list = listOf("Brand New Precon", "Lorehold Legacies", "Elven Empire")
        assertEquals("Elven Empire", pickSamplePrecon(list) { it })
        assertEquals("Lorehold Legacies", pickSamplePrecon(listOf("Brand New Precon", "Lorehold Legacies")) { it })
        assertEquals("Brand New Precon", pickSamplePrecon(listOf("Brand New Precon", "Older")) { it })
        assertNull(pickSamplePrecon(emptyList<String>()) { it })
        assertEquals("Sample: Elven Empire", sampleDeckName("Elven Empire"))
    }

    @Test
    fun `the sample binder takes different cards, not basics or the commander`() {
        fun card(id: String, name: String, type: String) = DeckCardEntry(id, name, null, typeLine = type)
        val cards = listOf(
            card("c", "Lathril, Blade of the Elves", "Legendary Creature — Elf Noble"),
            card("f", "Forest", "Basic Land — Forest"),
            card("g", "Snow-Covered Forest", "Basic Snow Land — Forest"),
            card("a", "Arcane Signet", "Artifact"),
            card("a2", "Arcane Signet", "Artifact"),
            card("s", "Sol Ring", "Artifact"),
            card("e", "Elvish Archdruid", "Creature — Elf Druid")
        )
        assertEquals(listOf("a", "s", "e"), sampleBinderPicks(cards, listOf("c")).map { it.scryfallId })
        assertEquals(listOf("a", "s"), sampleBinderPicks(cards, listOf("c"), 2).map { it.scryfallId })
    }
}
