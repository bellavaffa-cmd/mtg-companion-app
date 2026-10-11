package com.mtgcompanion.app.data

/*
 * The camera scanner's modes — one camera screen, four jobs: Add cards, Sort a pile, Put away, Check.
 * The switch at the top of the scanner moves between them without stopping the camera. This is the
 * logic of it, free of the screen: which mode the scanner opens in from where it was opened, what the
 * Sort a pile mode starts with, and what happens to the cards already scanned when the mode changes.
 * The web app's src/scan/scannerModes.ts, rule for rule.
 */

/** What the camera scanner is doing. [key] is how it's kept on the device; [label] the switch's words. */
enum class ScannerMode(val key: String, val label: String) {
    ADD("add", "Add cards"),
    SORT("sort", "Sort a pile"),
    PUT_AWAY("put_away", "Put away"),
    CHECK("check", "Check");

    companion object {
        fun of(key: String?): ScannerMode? = entries.firstOrNull { it.key == key }
    }
}

/** Where the scanner was opened from. */
enum class ScannerEntry {
    /** The bottom bar's Scan, Home, an empty state, the welcome flow: no job of its own. */
    PLAIN,
    /** Sort a new pile: Storage's menu, the recipes screen, Sealed. */
    SORT,
    /** A place's Put cards away (or Storage's, Upkeep, setup, a binder's Add cards in order). */
    PUT_AWAY,
    /** A place's Check. */
    CHECK,
    /** A deck's pull list or put-back list, ticking it off: a fixed job, no mode switch. */
    TICK
}

/** How the scanner opens: in [mode] (null: a fixed job of its own), and whether the mode switch shows. */
data class ScannerStart(val mode: ScannerMode?, val showSwitch: Boolean)

/**
 * The mode the scanner opens in. The plain entry opens in the mode last picked on the switch on this
 * device ([last]), Add cards the first time; every other entry opens in its own job, and doesn't
 * change what's remembered. Ticking a deck's list is its own fixed job, with the switch hidden.
 */
fun scannerStart(entry: ScannerEntry, last: ScannerMode?): ScannerStart = when (entry) {
    ScannerEntry.PLAIN -> ScannerStart(last ?: ScannerMode.ADD, true)
    ScannerEntry.SORT -> ScannerStart(ScannerMode.SORT, true)
    ScannerEntry.PUT_AWAY -> ScannerStart(ScannerMode.PUT_AWAY, true)
    ScannerEntry.CHECK -> ScannerStart(ScannerMode.CHECK, true)
    ScannerEntry.TICK -> ScannerStart(null, false)
}

/** What switching mode does with the Add cards list (the cards scanned but not added anywhere yet). */
enum class CarryOver {
    /** Nothing to carry: the list is empty. */
    NONE,
    /** The list stays as it is, untouched, and is there again on Add cards. */
    KEEP,
    /** Ask first: "Keep the N scanned cards in the list?" */
    ASK
}

/**
 * Switching from [from] to [to] with [pending] cards in the Add cards list. The list is never thrown
 * away by a switch: Put away and Check are a quick job on the side, and Add cards is where the list
 * lives, so it simply stays. Sort a pile is different — it starts a new pile that's filed on its own,
 * and the cards in the list are easy to mistake for part of it — so leaving Add cards for Sort a pile
 * asks, once per visit to the scanner ([asked]).
 */
fun carryOver(from: ScannerMode?, to: ScannerMode, pending: Int, asked: Boolean): CarryOver = when {
    pending <= 0 -> CarryOver.NONE
    from == ScannerMode.ADD && to == ScannerMode.SORT && !asked -> CarryOver.ASK
    else -> CarryOver.KEEP
}

/** What Sort a pile shows on being picked. */
enum class SortStart {
    /** A sort with a recipe under way, with cards in it: carry on with it. */
    RESUME_RECIPE,
    /** The classic sort ("Keep, spares, decks, bulk") under way, with cards in it: carry on with it. */
    RESUME_CLASSIC,
    /** "By card type · last used — Start · Change recipe". */
    START_LAST,
    /** No recipe used yet: pick one ("Choose a recipe"). */
    PICK
}

/**
 * What Sort a pile starts with: a sort under way carries on (the recipe one first — it's the newer
 * sorter — and only one with cards in it counts), else the recipe used last is offered, else a recipe
 * is picked. [recipeScans] / [classicScans]: the cards in the sort kept on the device, null when none.
 */
fun sortStart(recipeScans: Int?, classicScans: Int?, hasLastRecipe: Boolean): SortStart = when {
    (recipeScans ?: 0) > 0 -> SortStart.RESUME_RECIPE
    (classicScans ?: 0) > 0 -> SortStart.RESUME_CLASSIC
    hasLastRecipe -> SortStart.START_LAST
    else -> SortStart.PICK
}

/** Put away's "Let the app choose": offered once some box or deck box has a rule to go by. */
fun canLetAppChoose(places: List<StoragePlace>): Boolean =
    places.any { it.rule != null && it.placeKind != PlaceKind.BINDER }

/** The put-away target that means "the place whose rule fits each card" (bestPlaceByRule). */
const val PUT_AWAY_BY_RULE = BY_RULE
