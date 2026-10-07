package com.mtgcompanion.app.data

/*
 * The What's new tour: a few spotlight steps over the Collection's home, shown once on a phone after
 * updating to a version with new collection features (the home, Storage, Find anything…), and again
 * from Settings › What's new tour. Which steps, their words and whether it's been seen are here, pure;
 * ui/collection/WhatsNewTour.kt draws it and keeps the seen id (WhatsNewStore). Mirrors the web app's
 * src/onboarding/whatsNew.ts (tests: WhatsNewTest.kt ↔ tests/onboarding/whatsNew.test.ts).
 */

/** This tour. A later one gets a new id, and shows once more. */
const val TOUR_ID = "collection-home-1"

/** The part of the screen a step lights up. */
enum class TourTarget { HOME_TILES, HOME_STORAGE, HOME_FIND, HOME_SEALED, HOME_TODO }

/** What a step's call to action does: set up storage, or open Find anything. */
enum class TourAction { STORAGE_SETUP, FIND }

data class TourCta(val label: String, val action: TourAction)

data class TourStep(val target: TourTarget, val title: String, val body: String, val cta: TourCta? = null)

val TOUR_STEPS = listOf(
    TourStep(
        TourTarget.HOME_TILES,
        "Your collection has a home",
        "What it's worth, what's where and what needs doing, on one page. Each tile opens its part of the collection."
    ),
    TourStep(
        TourTarget.HOME_STORAGE,
        "Know where every card is",
        "Make your boxes and binders here, then scan cards as you put them away. Any card page will tell you where it is.",
        TourCta("Set it up", TourAction.STORAGE_SETUP)
    ),
    TourStep(
        TourTarget.HOME_FIND,
        "Find anything",
        "One search for your cards, your places and your decks. Each card says where every copy is — the box, the deck, who borrowed it.",
        TourCta("Try it", TourAction.FIND)
    ),
    TourStep(
        TourTarget.HOME_SEALED,
        "Boxes, slabs, loans and sales",
        "Sealed product and graded cards have places too, and what's lent out or marked to sell is counted here."
    ),
    TourStep(
        TourTarget.HOME_TODO,
        "A little upkeep",
        "The few things worth doing this week, each with a button that does it. Upkeep has the rest."
    )
)

/** "New · 2 of 5". */
fun tourEyebrow(index: Int, total: Int): String = "New · ${index + 1} of $total"

/** The forward button: "Next", and "Done" on the last step. */
fun tourNextLabel(index: Int, total: Int): String = if (index >= total - 1) "Done" else "Next"

/**
 * The steps to show, [hasPlaces] deciding Storage's call to action: "Set it up" before there are any
 * places; with places there's nothing to set up.
 */
fun tourSteps(hasPlaces: Boolean): List<TourStep> =
    TOUR_STEPS.map { if (it.cta?.action == TourAction.STORAGE_SETUP && hasPlaces) it.copy(cta = null) else it }

/**
 * Whether the tour opens by itself on the Collection's home: not seen on this phone yet ([seen]: the
 * id of the last tour seen, null for none), and the library has something of the user's — someone
 * just starting has the welcome steps instead, and the tour waits until there's a collection to show.
 */
fun shouldShowTour(seen: String?, cards: Int, decks: Int): Boolean = seen != TOUR_ID && (cards > 0 || decks > 0)

/** The stored id of the last tour seen; anything unreadable reads as none. */
fun parseTourSeen(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }
