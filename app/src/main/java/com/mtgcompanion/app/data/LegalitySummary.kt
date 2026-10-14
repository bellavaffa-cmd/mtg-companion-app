package com.mtgcompanion.app.data

// Why a deck isn't legal, in a few words for its header pill, and its problems grouped under plain
// headings for the sheet that pill opens. Pure, so it can be tested; the web app's
// src/decks/legalitySummary.ts says the same words (legalitySummaryVectors.json, run by both).

/** The kinds, most important first: the order the sheet's groups go in. */
val LEGALITY_PRIORITY: List<LegalityIssueKind> = listOf(
    LegalityIssueKind.COMMANDER,
    LegalityIssueKind.DECK_SIZE,
    LegalityIssueKind.LEGALITY,
    LegalityIssueKind.COLOR_IDENTITY,
    LegalityIssueKind.COPY_LIMIT,
    LegalityIssueKind.COMPANION
)

/** Said while the cards' legality is still loading or couldn't be read, instead of a guess. */
const val LEGALITY_CHECKING = "Checking…"

/**
 * The header pill's words: "Legal", "Not legal · 3 banned", "Not legal · 98/100 cards", "Not legal ·
 * no commander", "Not legal · 4 problems" when they're of different kinds — or "Checking…" while
 * [loading], and while no problem is found but some cards couldn't be checked yet.
 */
fun legalitySummary(report: LegalityReport?, loading: Boolean = false): String {
    if (report == null || loading) return LEGALITY_CHECKING
    if (report.issues.isEmpty()) return if (report.unchecked > 0) LEGALITY_CHECKING else "Legal"
    return "Not legal · ${legalityReason(report)}"
}

/** The part after "Not legal · ": what's wrong, as briefly as it can be said. */
fun legalityReason(report: LegalityReport): String {
    val issues = report.issues
    val problems = "${issues.size} problem${if (issues.size == 1) "" else "s"}"
    val kinds = issues.map { it.kind }.distinct()
    if (kinds.size != 1) return problems
    val n = issues.size
    return when (kinds.single()) {
        LegalityIssueKind.COMMANDER ->
            if (issues.any { it.reason.startsWith("No commander") }) "no commander"
            else if (n == 1) "commanders can't pair" else problems
        LegalityIssueKind.DECK_SIZE -> {
            val issue = issues.singleOrNull() ?: return problems
            when {
                !issue.sideboard -> "${report.totalCards}/${report.mode.deckSize} cards"
                report.mode.hasSideboard -> "sideboard too big"
                else -> "cards in the sideboard"
            }
        }
        LegalityIssueKind.LEGALITY ->
            if (issues.all { it.reason.startsWith("Banned") }) "$n banned" else "$n not legal"
        LegalityIssueKind.COLOR_IDENTITY -> "$n off-colour"
        LegalityIssueKind.COPY_LIMIT -> if (n == 1) "too many copies" else "$n with too many copies"
        LegalityIssueKind.COMPANION -> "companion"
    }
}

/** The sheet's heading for a kind of problem. */
fun legalityHeading(kind: LegalityIssueKind, mode: GameMode): String = when (kind) {
    LegalityIssueKind.COMMANDER -> "Commander"
    LegalityIssueKind.DECK_SIZE -> "Deck size"
    LegalityIssueKind.LEGALITY -> "Banned or not legal in ${mode.label}"
    LegalityIssueKind.COLOR_IDENTITY -> "Outside the commander's colours"
    LegalityIssueKind.COPY_LIMIT -> "Too many copies"
    LegalityIssueKind.COMPANION -> "Companion"
}

data class LegalityGroup(val kind: LegalityIssueKind, val heading: String, val issues: List<LegalityIssue>)

/** Every problem, grouped by kind under its heading, most important kind first; main deck before sideboard. */
fun groupLegalityIssues(report: LegalityReport): List<LegalityGroup> =
    LEGALITY_PRIORITY.mapNotNull { kind ->
        val inKind = report.issues.filter { it.kind == kind }.sortedBy { it.sideboard }
        if (inKind.isEmpty()) null else LegalityGroup(kind, legalityHeading(kind, report.mode), inKind)
    }

/**
 * How many cards the main deck is off by: positive, that many to add; negative, that many to take
 * out (an exact-size format over its size); 0 when the size is right.
 */
fun deckSizeFix(report: LegalityReport): Int {
    val size = report.mode.deckSize
    return when {
        report.totalCards < size -> size - report.totalCards
        report.mode.exactSize && report.totalCards > size -> size - report.totalCards
        else -> 0
    }
}

/** The deck-size fix as a button's words: "Add 2 cards", "Remove 1 card"; null when none is needed. */
fun deckSizeFixLabel(report: LegalityReport): String? {
    val fix = deckSizeFix(report)
    if (fix == 0) return null
    val n = kotlin.math.abs(fix)
    return "${if (fix > 0) "Add" else "Remove"} $n card${if (n == 1) "" else "s"}"
}

/** A line for the sheet when some cards couldn't be checked; null when every card was. */
fun uncheckedNote(report: LegalityReport): String? {
    val n = report.unchecked
    if (n == 0) return null
    return "$n card${if (n == 1) "" else "s"} couldn't be checked yet — ${if (n == 1) "its" else "their"} legality comes with the card data, which needs a connection."
}
