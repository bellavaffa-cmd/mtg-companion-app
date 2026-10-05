package com.mtgcompanion.app.data

import org.json.JSONObject

// The welcome flow and sample content: which steps are still to do, when the flow opens by itself,
// and the sample deck and binder — what they hold, and keeping them out of the sync. No storage or
// network here; WelcomeScreen and Home's "Get started" card use it. Mirrors the web app's
// src/onboarding/onboarding.ts, and OnboardingTest has the same cases as its
// tests/onboarding/onboarding.test.ts.

/** The welcome flow's steps, in order. [key] is the same word the web uses (?step=deck). */
enum class WelcomeStep(val key: String) {
    COLLECTION("collection"), DECK("deck"), ACCOUNT("account"), DONE("done");

    companion object {
        fun fromKey(key: String?): WelcomeStep? = entries.firstOrNull { it.key == key }
    }
}

/** The steps that can be left for later and picked up from Home. */
private val RESUMABLE = listOf(WelcomeStep.COLLECTION, WelcomeStep.DECK, WelcomeStep.ACCOUNT)

/**
 * What the welcome flow remembers on this device. [finished] is "done" once the last step was
 * reached, "skipped" when the user left it early, null before either. [opened]: it has opened by
 * itself once, and doesn't again — Home's "Get started" card picks up from there.
 */
data class WelcomeState(val finished: String? = null, val opened: Boolean = false)

/** The stored form; anything unreadable reads as never opened. */
fun parseWelcomeState(raw: String?): WelcomeState {
    if (raw.isNullOrBlank()) return WelcomeState()
    return try {
        val json = JSONObject(raw)
        val finished = json.optString("finished").takeIf { it == "done" || it == "skipped" }
        WelcomeState(finished = finished, opened = json.opt("opened") == true)
    } catch (e: Exception) {
        WelcomeState()
    }
}

fun welcomeStateJson(state: WelcomeState): String =
    JSONObject().put("finished", state.finished ?: JSONObject.NULL).put("opened", state.opened).toString()

/** What the steps look at: the user's own decks and cards (samples aside) and their account. */
data class WelcomeFacts(
    /** Copies in the user's binders and the Unsorted pile; not the Wishlist, not samples. */
    val cards: Int = 0,
    /** Decks, not counting samples. */
    val decks: Int = 0,
    /** Whether a sample deck or binder is in the library. */
    val samples: Boolean = false,
    /** False when this build has no accounts: the account step is then nothing to do. */
    val accountsAvailable: Boolean = true,
    val signedIn: Boolean = false,
    /** Signed in with a username picked (the social profile). */
    val hasProfile: Boolean = false
)

fun isSample(deck: Deck): Boolean = deck.sample == true
fun isSample(collection: Collection): Boolean = collection.sample == true

/** The library facts the steps need, from the decks and binders; the account fields are left as they come. */
fun libraryFacts(decks: List<Deck>, collections: List<Collection>, base: WelcomeFacts = WelcomeFacts()): WelcomeFacts =
    base.copy(
        cards = collections
            .filter { !isSample(it) && it.kind != CollectionType.WISHLIST }
            .sumOf { c -> c.entries.sumOf { it.quantity + it.foilQuantity } },
        decks = decks.count { !isSample(it) },
        samples = decks.any { isSample(it) } || collections.any { isSample(it) }
    )

/** Whether [step] needs nothing more from the user. Done is never done: it's where the flow ends. */
fun stepDone(step: WelcomeStep, facts: WelcomeFacts): Boolean = when (step) {
    WelcomeStep.COLLECTION -> facts.cards > 0
    WelcomeStep.DECK -> facts.decks > 0
    WelcomeStep.ACCOUNT -> !facts.accountsAvailable || (facts.signedIn && facts.hasProfile)
    WelcomeStep.DONE -> false
}

/** A library with nothing of the user's own in it yet. Samples don't count. */
fun isEmptyLibrary(facts: WelcomeFacts): Boolean = facts.cards == 0 && facts.decks == 0

/**
 * Whether the welcome flow opens by itself: once, on a first open, signed out, with nothing in the
 * library. Someone who already has decks or cards, or is signed in (an update, a second device whose
 * library hasn't come down yet), never gets it unasked.
 */
fun shouldOpenWelcome(state: WelcomeState, facts: WelcomeFacts): Boolean =
    state.finished == null && !state.opened && !facts.signedIn && isEmptyLibrary(facts)

/** Whether Home shows the "Get started" card instead of its empty widgets. */
fun showGetStarted(facts: WelcomeFacts): Boolean = isEmptyLibrary(facts)

/** The steps still to do, in order — what "Get started" offers to pick up. */
fun stepsToDo(facts: WelcomeFacts): List<WelcomeStep> = RESUMABLE.filter { !stepDone(it, facts) }

/** Where the flow opens when resumed: the first step not done, or the end. */
fun firstStepToDo(facts: WelcomeFacts): WelcomeStep = stepsToDo(facts).firstOrNull() ?: WelcomeStep.DONE

/** The step after [step]; Done stays put. */
fun nextStep(step: WelcomeStep): WelcomeStep = WelcomeStep.entries.getOrElse(step.ordinal + 1) { WelcomeStep.DONE }

// ---- Sample content ----

/**
 * The precons the sample deck is taken from, best first: well-known lists that MTGJSON has. When none
 * of them is there, the newest precon is used.
 */
val SAMPLE_PRECONS = listOf("Elven Empire", "Draconic Dissent", "Lorehold Legacies")

/** The precon to use from the [list] (newest first, as the precons screen lists them); null for an empty list. */
fun <T> pickSamplePrecon(list: List<T>, name: (T) -> String): T? {
    for (wanted in SAMPLE_PRECONS) {
        list.firstOrNull { name(it).equals(wanted, ignoreCase = true) }?.let { return it }
    }
    return list.firstOrNull()
}

/** The sample deck's name: the precon's, clearly marked. */
fun sampleDeckName(preconName: String): String = "Sample: $preconName"
const val SAMPLE_BINDER_NAME = "Sample binder"
/** How many cards the sample binder holds. */
const val SAMPLE_BINDER_SIZE = 12

private val BASIC_LANDS = setOf("plains", "island", "swamp", "mountain", "forest", "wastes")
private val BASIC_TYPE = Regex("\\bbasic\\b.*\\bland\\b", RegexOption.IGNORE_CASE)

/**
 * The cards for the sample binder, from the sample deck's: the first [size] different ones that
 * aren't basic lands or the commanders, so it looks like a binder rather than a pile of Forests.
 */
fun sampleBinderPicks(cards: List<DeckCardEntry>, commanderIds: List<String> = emptyList(), size: Int = SAMPLE_BINDER_SIZE): List<DeckCardEntry> {
    val out = mutableListOf<DeckCardEntry>()
    val names = mutableSetOf<String>()
    for (card in cards) {
        if (out.size >= size) break
        val name = card.name.lowercase()
        if (name in BASIC_LANDS || BASIC_TYPE.containsMatchIn(card.typeLine.orEmpty())) continue
        if (card.scryfallId in commanderIds || !names.add(name)) continue
        out += card
    }
    return out
}

/** The decks without the samples. */
fun withoutSampleDecks(decks: List<Deck>): List<Deck> = decks.filterNot { isSample(it) }

/** The binders without the samples. */
fun withoutSampleCollections(collections: List<Collection>): List<Collection> = collections.filterNot { isSample(it) }
