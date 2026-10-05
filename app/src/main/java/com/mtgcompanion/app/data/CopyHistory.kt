package com.mtgcompanion.app.data

/*
 * A copy's history: where a card has been — added, put away, moved, pulled into a deck, put back,
 * lent, returned, checked, sold — as an append-only log kept on this device only (it isn't synced:
 * each device remembers what was done on it). "History" on a card's Where it is shows its moves;
 * "Recent moves" on a place's screen shows the moves in and out of it. Kept for a year (pruneMoves).
 *
 * The log is written where the moves are made (CopyHistoryStore.kt keeps it in a file); this file is
 * the pure part — the entries, their words and the pruning.
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/copyHistory.ts rule for rule, with
 * the same tests (CopyHistoryTest.kt ↔ tests/collection/copyHistory.test.ts).
 */

enum class MoveKind { ADDED, PUT_AWAY, MOVED, PULLED, PUT_BACK, LENT, RETURNED, CHECKED, SOLD }

/**
 * One move of one card's copies. [kind] is a [MoveKind] name; [title] and [detail] are the words shown
 * ("Put away in Red box", "from Unsorted, by scanning"); [places] the ids of the places it touched,
 * for a place's Recent moves. Kept as the web app keeps it, field for field.
 */
data class CopyMove(
    val at: Long,
    val kind: String,
    val name: String,
    val scryfallId: String? = null,
    val qty: Int = 1,
    val title: String,
    val detail: String? = null,
    val places: List<String>? = null
)

/** A place (or anywhere) a move went to or came from: its id ("" for none of the user's places) and its words. */
data class MoveSpot(val id: String, val name: String)

/** The card a move is of. */
data class MoveCard(val name: String, val scryfallId: String? = null)

/** How long moves are kept. */
const val KEEP_MOVES_MS = 365L * 24 * 60 * 60 * 1000
/** At most this many moves are kept, the newest. */
const val MAX_MOVES = 5000

/** [log] without moves older than a year, and no more than [MAX_MOVES] — the newest kept. Oldest first. */
fun pruneMoves(log: List<CopyMove>, now: Long): List<CopyMove> {
    val kept = log.filter { it.at >= now - KEEP_MOVES_MS }
    return if (kept.size > MAX_MOVES) kept.drop(kept.size - MAX_MOVES) else kept
}

/** [log] with [moves] added after it (oldest first, by time — stable), then pruned. */
fun appendMoves(log: List<CopyMove>, moves: List<CopyMove>, now: Long): List<CopyMove> {
    if (moves.isEmpty()) return pruneMoves(log, now)
    return pruneMoves((log + moves.map { tidyMove(it) }).sortedBy { it.at }, now)
}

/** A move as kept: optional fields left out when empty. */
fun tidyMove(m: CopyMove): CopyMove = m.copy(
    scryfallId = m.scryfallId?.takeIf { it.isNotEmpty() },
    qty = maxOf(1, m.qty),
    detail = m.detail?.takeIf { it.isNotEmpty() },
    places = m.places?.filter { it.isNotEmpty() }?.distinct()?.takeIf { it.isNotEmpty() }
)

/** The moves of the card called [name] (any printing), newest first. */
fun movesOfCard(log: List<CopyMove>, name: String): List<CopyMove> = log.filter { sameCardName(it.name, name) }.reversed()

/** The moves in and out of [placeIds] (a place and those inside it), newest first, at most [limit]. */
fun movesOfPlace(log: List<CopyMove>, placeIds: Set<String>, limit: Int = 20): List<CopyMove> =
    log.filter { m -> m.places.orEmpty().any { it in placeIds } }.reversed().take(limit)

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/**
 * The day of a move, as the list shows it: "Today", "Yesterday", "12 Sep", or "12 Sep 2025" in another
 * year. [day] and [today] are calendar days ("2026-09-12") on this device.
 */
fun moveDay(day: String, today: String): String {
    if (day == today) return "Today"
    if (daysBetween(day, today) == 1) return "Yesterday"
    val parts = day.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3 || parts[1] !in 1..12) return day
    val (y, m, d) = parts
    return "$d ${MONTHS[m - 1]}${if (y != today.take(4).toIntOrNull()) " $y" else ""}"
}

// ---- The words for each move ----

private fun times(qty: Int) = if (qty > 1) " ×$qty" else ""

/** "Added to your collection" — from where, when said ("from a trade with Priya", "Booster box, Duskmourn"). */
fun addedMove(at: Long, card: MoveCard, qty: Int, to: MoveSpot?, from: String? = null): CopyMove = tidyMove(
    CopyMove(
        at, MoveKind.ADDED.name, card.name, card.scryfallId, qty,
        title = "Added to your collection${times(qty)}",
        detail = listOf(if (to != null) "into ${to.name}" else "", from ?: "").filter { it.isNotEmpty() }.joinToString(" · "),
        places = listOfNotNull(to?.id)
    )
)

/** "Put away in Red box" — "from Unsorted, by scanning". */
fun putAwayMove(at: Long, card: MoveCard, qty: Int, to: MoveSpot, from: MoveSpot?, how: String? = null): CopyMove = tidyMove(
    CopyMove(
        at, MoveKind.PUT_AWAY.name, card.name, card.scryfallId, qty,
        title = "Put away in ${to.name}${times(qty)}",
        detail = listOf("from ${from?.name ?: "no place"}", how ?: "").filter { it.isNotEmpty() }.joinToString(", "),
        places = listOfNotNull(to.id, from?.id)
    )
)

/** "Moved to Trade binder" — "from Red box", or "Taken off its place" when it goes to none. */
fun movedMove(at: Long, card: MoveCard, qty: Int, from: MoveSpot?, to: MoveSpot?): CopyMove = tidyMove(
    CopyMove(
        at, MoveKind.MOVED.name, card.name, card.scryfallId, qty,
        title = if (to != null) "Moved to ${to.name}${times(qty)}" else "Taken off its place${times(qty)}",
        detail = if (from != null) "from ${from.name}" else "from no place",
        places = listOfNotNull(from?.id, to?.id)
    )
)

/** "Pulled into Atraxa deck" — "from Red box › Colourless". */
fun pulledMove(at: Long, card: MoveCard, qty: Int, deck: String, from: MoveSpot?): CopyMove = tidyMove(
    CopyMove(
        at, MoveKind.PULLED.name, card.name, card.scryfallId, qty,
        title = "Pulled into $deck deck${times(qty)}",
        detail = if (from != null) "from ${from.name}" else "from no place",
        places = listOfNotNull(from?.id)
    )
)

/** "Put back in Red box" — "from Atraxa deck". */
fun putBackMove(at: Long, card: MoveCard, qty: Int, deck: String, to: MoveSpot?): CopyMove = tidyMove(
    CopyMove(
        at, MoveKind.PUT_BACK.name, card.name, card.scryfallId, qty,
        title = if (to != null) "Put back in ${to.name}${times(qty)}" else "Taken out of $deck deck${times(qty)}",
        detail = "from $deck deck",
        places = listOfNotNull(to?.id)
    )
)

/** "Lent to Sam" — "from Atraxa deck · back by next game night". */
fun lentMove(at: Long, card: MoveCard, qty: Int, to: String, from: String, fromPlaceId: String?, due: String?): CopyMove = tidyMove(
    CopyMove(
        at, MoveKind.LENT.name, card.name, card.scryfallId, qty,
        title = "Lent to $to${times(qty)}",
        detail = listOf("from $from", due?.replaceFirstChar { it.lowercase() } ?: "").filter { it.isNotEmpty() }.joinToString(" · "),
        places = listOfNotNull(fromPlaceId)
    )
)

/** "Back from Sam" — "into Rares binder". */
fun returnedMove(at: Long, card: MoveCard, qty: Int, from: String, to: String, toPlaceId: String?): CopyMove = tidyMove(
    CopyMove(
        at, MoveKind.RETURNED.name, card.name, card.scryfallId, qty,
        title = "Back from $from${times(qty)}", detail = "into $to", places = listOfNotNull(toPlaceId)
    )
)

/** "Checked in Red box" — "where it should be". */
fun checkedMove(at: Long, card: MoveCard, place: MoveSpot, result: String): CopyMove = tidyMove(
    CopyMove(at, MoveKind.CHECKED.name, card.name, card.scryfallId, 1, title = "Checked in ${place.name}", detail = result, places = listOf(place.id))
)

/** "Sold" — "to Priya", "from Trade binder". */
fun soldMove(at: Long, card: MoveCard, qty: Int, detail: String, fromPlaceId: String?): CopyMove = tidyMove(
    CopyMove(at, MoveKind.SOLD.name, card.name, card.scryfallId, qty, title = "Sold${times(qty)}", detail = detail, places = listOfNotNull(fromPlaceId))
)
