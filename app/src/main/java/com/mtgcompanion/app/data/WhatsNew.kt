package com.mtgcompanion.app.data

/*
 * The What's new tour: a few spotlight steps over the Collection's tabs, shown once on a phone after
 * updating to a version with new collection features (the tabs, Storage, Find anything…), and again
 * from Settings › What's new tour. Which steps, their words and whether it's been seen are here, pure;
 * ui/collection/WhatsNewTour.kt draws it and keeps the seen id (WhatsNewStore). Mirrors the web app's
 * src/onboarding/whatsNew.ts (tests: WhatsNewTest.kt ↔ tests/onboarding/whatsNew.test.ts).
 */

/** This tour. A later one gets a new id, and shows once more. */
const val TOUR_ID = "collection-tabs-1"

/** The part of the screen a step lights up: the tab row, the Storage and Cards tabs, the search bar, the header. */
enum class TourTarget { TABS, STORAGE, SEARCH, CARDS, HEADER }

/** What a step's call to action does: set up storage, or open Find anything. */
enum class TourAction { STORAGE_SETUP, FIND }

data class TourCta(val label: String, val action: TourAction)

data class TourStep(val target: TourTarget, val title: String, val body: String, val cta: TourCta? = null)

val TOUR_STEPS = listOf(
    TourStep(
        TourTarget.TABS,
        "Your collection in four tabs",
        "Cards, Binders, Storage and Wishlist, always one tap away. The Collection opens on the tab you used last."
    ),
    TourStep(
        TourTarget.STORAGE,
        "Know where every card is",
        "Make your boxes and binders on Storage, then scan cards as you put them away. Sealed product and graded cards live there too.",
        TourCta("Set it up", TourAction.STORAGE_SETUP)
    ),
    TourStep(
        TourTarget.SEARCH,
        "One search",
        "It searches the tab you're on. Search everywhere, at the bottom, finds your cards, places and decks at once.",
        TourCta("Try it", TourAction.FIND)
    ),
    TourStep(
        TourTarget.CARDS,
        "Every card says where it is",
        "Each card shows its box, deck or who borrowed it. No place yet puts it away. Group by set, binder or place, or show only what's lent out, for sale or graded."
    ),
    TourStep(
        TourTarget.HEADER,
        "A little upkeep",
        "What it's worth and whether it's synced, up top. Needs attention shows when something is worth doing this week."
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
