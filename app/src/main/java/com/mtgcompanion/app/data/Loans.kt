package com.mtgcompanion.app.data

import java.time.LocalDate

/*
 * Loans: cards lent to a friend (by account) or anyone (by name), with where each came from, so
 * "Got them back" puts every card back where it was. They replace the old way of marking copies lent
 * — a user tag starting "lent" — which still counts until it's turned into loans (loansFromTags).
 *
 * Where they're kept: the Unsorted pile's "loans" (Collection.loans, JSON in CollectionModels.kt), so
 * they sync with the library like the storage places do — the pile is always there with the same id
 * on every device. Two devices' loans merge loan by loan (mergeLoans): a loan made on either is kept,
 * one deleted on either stays deleted, each field goes to whoever changed it, a card's "back" only
 * ever goes up. A pile saved by an app from before loans comes without the key and keeps this
 * device's (keepLoansFromOlderApp).
 *
 * Lending a card from a place takes it off its place (it has no place while it's out — the copies
 * with no place that a loan names count as "Lent out", see lentCopies in StoragePlaces.kt); lending
 * one from a deck leaves the deck's list alone and shows the card there as lent out.
 *
 * A friend's loan is also sent to the server (social/SocialApi.kt upsertLoan,
 * supabase/migrations/20261006010000_loans.sql) so the friend sees "Borrowed" — best effort: the loan
 * itself is the library's, and works without it.
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/loans.ts rule for rule, with the
 * same tests (LoansTest.kt ↔ tests/collection/loans.test.ts).
 */

/** Returned loans are forgotten this long after they came back. */
const val KEEP_RETURNED_MS = 365L * 24 * 60 * 60 * 1000

/** [collections] with the loans set to [loans] (on the Unsorted pile, made if it isn't there). */
fun withLoans(collections: List<Collection>, loans: List<Loan>): List<Collection> =
    withUnsortedPile(collections).map { if (it.isUnsorted) it.copy(loans = loans) else it }

/** A loan's card as both apps write it: optional fields left out (null) when not said. */
fun loanCard(c: LoanCard): LoanCard = LoanCard(
    name = c.name,
    scryfallId = c.scryfallId,
    qty = c.qty,
    foil = if (c.isFoil) true else null,
    collectionId = c.collectionId?.takeIf { it.isNotEmpty() },
    placeId = c.placeId?.takeIf { it.isNotEmpty() },
    section = if (!c.placeId.isNullOrEmpty()) c.section?.takeIf { it.isNotEmpty() } else null,
    page = if (!c.placeId.isNullOrEmpty()) c.page?.takeIf { it > 0 } else null,
    slot = if (!c.placeId.isNullOrEmpty()) c.slot?.takeIf { it > 0 } else null,
    deckId = c.deckId?.takeIf { it.isNotEmpty() },
    back = c.back?.takeIf { it > 0 }?.let { minOf(it, c.qty) }
)

/** A loan as both apps write it. */
fun loanOf(l: Loan): Loan = Loan(
    id = l.id,
    to = l.to.trim(),
    friendId = l.friendId?.takeIf { it.isNotEmpty() },
    cards = l.cards.map { loanCard(it) },
    lentAt = l.lentAt,
    backBy = l.backBy?.takeIf { it.isNotEmpty() },
    gameNight = if (l.gameNight == true) true else null,
    note = l.note?.trim()?.takeIf { it.isNotEmpty() },
    returnedAt = l.returnedAt?.takeIf { it > 0 }
)

/** How many copies a loan still has out. */
fun copiesOut(loan: Loan): Int = loan.cards.sumOf { stillOut(it) }

// ---- What can be lent ----

/** Copies that can be lent, from one spot: a place's line, a deck, or a binder's copies with no place. */
data class LendSource(
    val key: String,
    val name: String,
    val scryfallId: String,
    val foil: Boolean,
    /** How many are there to lend. */
    val qty: Int,
    /** "Red box › Red", "Atraxa deck", "Unsorted". */
    val from: String,
    val collectionId: String? = null,
    /** The place's line they're in. */
    val line: CopyPlace? = null,
    val deckId: String? = null
)

/**
 * Everything that can be lent: of the card called [name] (any printing) — or every card in the place
 * [placeId] and the places inside it. Copies already out on loan, or tagged lent, aren't offered.
 * A card's own: its places first, then its decks, then its copies with no place.
 */
fun lendSources(collections: List<Collection>, decks: List<Deck>, name: String? = null, placeId: String? = null): List<LendSource> {
    val places = placesOf(collections)
    val known = places.map { it.id }.toSet()
    val lent = lentCopies(collections, decks)
    val byEntry = lentByEntry(lent)
    val out = mutableListOf<LendSource>()
    fun fromPlace(collectionId: String, e: CollectionEntry, line: CopyPlace) {
        out += LendSource(
            "p:$collectionId:${e.scryfallId}:${line.placeId}|${if (line.isFoil) "foil" else ""}|${line.section ?: ""}|${line.page ?: ""}|${line.slot ?: ""}",
            e.name, e.scryfallId, line.isFoil, line.qty,
            loanCardFrom(LoanCard(e.name, e.scryfallId, 0, placeId = line.placeId, section = line.section), collections, decks),
            collectionId = collectionId, line = line
        )
    }
    if (placeId != null) {
        for (id in placeAndInside(places, placeId)) for (c in cardsIn(collections, id)) fromPlace(c.collectionId, c.entry, c.line)
        return out
    }
    val wanted = name ?: ""
    val owned = collections.filter { it.kind != CollectionType.WISHLIST }
    for (c in owned) for (e in c.entries) {
        if (!sameCardName(e.name, wanted)) continue
        for (line in placedCopies(e)) if (line.placeId in known) fromPlace(c.id, e, line)
    }
    for (d in decks) {
        val real = realCopiesOf(d).filter { sameCardName(it.name, wanted) }
        if (real.isEmpty()) continue
        val qty = real.sumOf { it.quantity } - lentFromDeck(lent, d.id, wanted)
        if (qty > 0) out += LendSource("d:${d.id}", real[0].name, real[0].scryfallId, false, qty, "${d.name} deck", deckId = d.id)
    }
    for (c in owned) for (e in c.entries) {
        if (!sameCardName(e.name, wanted) || lentTag(e) != null) continue
        val clean = if (placedCopies(e).all { it.placeId in known }) e else withPlaces(e, placedCopies(e).filter { it.placeId in known })
        val (free, freeFoil) = unplacedCopies(clean)
        val (plainOut, foilOut) = lentOf(byEntry, c.id, e)
        val plain = free - plainOut
        val foil = freeFoil - foilOut
        if (plain > 0) out += LendSource("n:${c.id}:${e.scryfallId}:", e.name, e.scryfallId, false, plain, c.name, collectionId = c.id)
        if (foil > 0) out += LendSource("n:${c.id}:${e.scryfallId}:foil", e.name, e.scryfallId, true, foil, c.name, collectionId = c.id)
    }
    return out
}

// ---- Lending and getting them back ----

/** What to lend: [qty] copies from one source. */
data class LendPick(val source: LendSource, val qty: Int)

/** The loan's card for [qty] copies from [source]. */
fun loanCardFor(source: LendSource, qty: Int): LoanCard = loanCard(
    LoanCard(
        name = source.name,
        scryfallId = source.scryfallId,
        qty = qty,
        foil = if (source.foil) true else null,
        collectionId = if (source.deckId == null) source.collectionId else null,
        placeId = source.line?.placeId,
        section = source.line?.section,
        page = source.line?.page,
        slot = source.line?.slot,
        deckId = source.deckId
    )
)

private fun editEntry(collections: List<Collection>, collectionId: String, scryfallId: String, fn: (CollectionEntry) -> CollectionEntry): List<Collection> =
    collections.map { c -> if (c.id != collectionId) c else c.copy(entries = c.entries.map { if (it.scryfallId == scryfallId) fn(it) else it }) }

/**
 * [collections] with a new loan of [picks] — [loan]'s person, dates and note; its cards are made from
 * the picks: copies lent from a place come off it (they have no place while they're out); copies from
 * a deck or with no place stay as they are. Returned loans older than a year are forgotten. Unchanged
 * when nothing is picked.
 */
fun lend(collections: List<Collection>, picks: List<LendPick>, loan: Loan): List<Collection> {
    val chosen = picks.filter { it.qty > 0 }
    if (chosen.isEmpty()) return collections
    var out = collections
    val cards = mutableListOf<LoanCard>()
    for ((source, qty) in chosen) {
        var n = minOf(qty, source.qty)
        if (source.line != null && source.collectionId != null) {
            val e = out.firstOrNull { it.id == source.collectionId }?.entries?.firstOrNull { it.scryfallId == source.scryfallId } ?: continue
            val (entry, moved) = moveCopies(e, source.line, null, n)
            n = moved
            out = editEntry(out, source.collectionId, source.scryfallId) { entry }
        }
        if (n > 0) cards += loanCardFor(source, n)
    }
    if (cards.isEmpty()) return collections
    val kept = loansOf(out).filter { isOpen(it) || (it.returnedAt ?: it.lentAt) >= loan.lentAt - KEEP_RETURNED_MS }
    return withLoans(out, kept + loanOf(loan.copy(cards = cards)))
}

/**
 * [collections] with some of loan [loanId]'s cards back: [counts] says how many of each card (by its
 * index), all that are out when null. Each copy goes back where it came from — into its place's spot
 * while the place is there, or into no place; one from a deck is simply in the deck again. When every
 * card is back the loan is returned at [now].
 */
fun returnCards(collections: List<Collection>, loanId: String, now: Long, counts: List<Int>? = null): List<Collection> {
    val loan = loansOf(collections).firstOrNull { it.id == loanId } ?: return collections
    val known = placesOf(collections).map { it.id }.toSet()
    var out = collections
    var changed = false
    val cards = loan.cards.mapIndexed { i, card ->
        val n = minOf(stillOut(card), maxOf(0, if (counts != null) counts.getOrElse(i) { 0 } else stillOut(card)))
        if (n <= 0) return@mapIndexed card
        changed = true
        if (card.placeId != null && card.placeId in known && card.deckId == null) {
            // Into its spot: the entry it came from, or another holding that printing (Unsorted first).
            val spot = Spot(card.placeId, card.section, card.page, card.slot)
            val owned = out.filter { it.kind != CollectionType.WISHLIST }
            val order = owned.filter { it.id == card.collectionId } +
                owned.filter { it.id != card.collectionId && it.isUnsorted } +
                owned.filter { it.id != card.collectionId && !it.isUnsorted }
            var left = n
            for (c in order) {
                if (left <= 0) break
                val e = c.entries.firstOrNull { it.scryfallId == card.scryfallId } ?: continue
                val (entry, moved) = placeCopies(e, spot, left, card.isFoil)
                if (moved <= 0) continue
                left -= moved
                out = editEntry(out, c.id, e.scryfallId) { entry }
            }
        }
        loanCard(card.copy(back = (card.back ?: 0) + n))
    }
    if (!changed) return collections
    val next = loan.copy(cards = cards)
    val done = !isOpen(next)
    return withLoans(out, loansOf(out).map { if (it.id == loanId) loanOf(if (done) next.copy(returnedAt = now) else next) else it })
}

// ---- When they're due ----

/** A game night: when it started and its day ("2026-10-12", the device's own calendar). */
data class NightDay(val at: Long, val day: String)

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun dayParts(day: String): List<Int>? = day.split("-").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 }

/** "12 Oct" for "2026-10-12"; with the year when it isn't [thisYear]'s. */
fun shortDay(day: String, thisYear: Int? = null): String {
    val (y, m, d) = dayParts(day) ?: return day
    if (m !in 1..12) return day
    return "$d ${MONTHS[m - 1]}${if (thisYear != null && thisYear != y) " $y" else ""}"
}

private fun epochDay(day: String): Long {
    val (y, m, d) = dayParts(day) ?: return 0
    return LocalDate.of(y, m, d).toEpochDay()
}

/** Days from [from] to [to] ("2026-10-01" to "2026-10-04": 3). */
fun daysBetween(from: String, to: String): Int = (epochDay(to) - epochDay(from)).toInt()

/** The day [loan] is due back: its date, or the first game night after it was lent; null when none. */
fun dueDay(loan: Loan, nights: List<NightDay>): String? {
    if (loan.backBy != null) return loan.backBy
    if (loan.gameNight != true) return null
    return nights.sortedBy { it.at }.firstOrNull { it.at > loan.lentAt }?.day
}

/** How a loan stands on a day: days overdue (0 when not), and that in words. */
data class LoanDue(val overdue: Int, val label: String)

fun loanDue(loan: Loan, today: String, nights: List<NightDay>): LoanDue {
    if (!isOpen(loan)) return LoanDue(0, "All back")
    val due = dueDay(loan, nights) ?: return LoanDue(0, if (loan.gameNight == true) "Back by next game night" else "No date")
    val days = daysBetween(due, today)
    if (days > 0) return LoanDue(days, "Overdue · $days ${if (days == 1) "day" else "days"}")
    if (days == 0) return LoanDue(0, "Due back today")
    return LoanDue(0, if (loan.backBy != null) "Back by ${shortDay(due, today.take(4).toIntOrNull())}" else "Back by next game night")
}

// ---- The Loans screen ----

/** One person's open loans, for the Loans screen. [overdue]: the most days any is overdue; [label]: the nearest due, in words. */
data class LoanPerson(
    val key: String,
    val name: String,
    val friendId: String?,
    val loans: List<Loan>,
    val copies: Int,
    val overdue: Int,
    val label: String
)

/** The open loans, a group for each person (by friend, or by name), the most overdue first, then A–Z. */
fun loanPeople(loans: List<Loan>, today: String, nights: List<NightDay>): List<LoanPerson> {
    val groups = LinkedHashMap<String, MutableList<Loan>>()
    for (loan in loans.filter { isOpen(it) }.sortedBy { it.lentAt }) {
        val key = if (loan.friendId != null) "f:${loan.friendId}" else "n:${loan.to.trim().lowercase()}"
        groups.getOrPut(key) { mutableListOf() } += loan
    }
    return groups.map { (key, mine) ->
        val dues = mine.map { loanDue(it, today, nights) }
        val overdue = maxOf(0, dues.maxOfOrNull { it.overdue } ?: 0)
        val label = (if (overdue > 0) dues.firstOrNull { it.overdue == overdue } else dues.firstOrNull { it.label != "No date" })?.label ?: "No date"
        LoanPerson(key, mine[0].to.trim(), mine[0].friendId, mine, mine.sumOf { copiesOut(it) }, overdue, label)
    }.sortedWith(compareByDescending<LoanPerson> { it.overdue }.thenComparator { a, b -> a.name.lowercase().compareTo(b.name.lowercase()) })
}

/** A message asking for the cards back, to send any way the user likes. */
fun reminderText(name: String, loans: List<Loan>): String {
    val cards = loans.flatMap { l -> l.cards.filter { stillOut(it) > 0 }.map { if (stillOut(it) > 1) "${stillOut(it)}× ${it.name}" else it.name } }
    return "Hi ${name.trim()} — could I have my cards back when you get a chance? ${cards.joinToString(", ")}. Thanks!"
}

/** A loan's card as the server keeps it for the friend: name, copies still out and printing. */
data class ServerCard(val name: String, val qty: Int, val printingId: String)

/** A loan's cards still out, as the server keeps them, one per printing. */
fun serverCards(loan: Loan): List<ServerCard> {
    val out = mutableListOf<ServerCard>()
    for (c in loan.cards) {
        val n = stillOut(c)
        if (n <= 0) continue
        val i = out.indexOfFirst { it.printingId == c.scryfallId }
        if (i >= 0) out[i] = out[i].copy(qty = out[i].qty + n) else out += ServerCard(c.name, n, c.scryfallId)
    }
    return out
}

// ---- Turning "lent" tags into loans ----

private val BORROWER = Regex("^lent\\b(?:\\s+out)?(?:\\s+to)?[\\s:,-]*(.*)$", RegexOption.IGNORE_CASE)

/** Who a "lent" tag names: "lent to Sam" → Sam, "lent: Priya" → Priya, "lent" → Someone. */
fun tagBorrower(tag: String): String {
    val who = BORROWER.find(tag.trim())?.groupValues?.getOrNull(1)?.trim().orEmpty()
    return if (who.isEmpty()) "Someone" else who.replaceFirstChar { it.uppercase() }
}

/** What turning the tags into loans does: the binders with the loans, and each printing's tags without its "lent" one. */
data class TagLoans(val collections: List<Collection>, val loans: List<Loan>, val retag: List<Pair<String, List<String>>>)

/**
 * "Turn 'lent' tags into loans": the copies with no place of every entry tagged "lent …" become a
 * loan to whoever the tag names (one loan each, lent [now]), and the tag comes off. Copies already
 * out on loan aren't counted twice.
 */
fun loansFromTags(collections: List<Collection>, now: Long, newId: () -> String): TagLoans {
    val known = placesOf(collections).map { it.id }.toSet()
    val byEntry = lentByEntry(lentCopies(collections))
    val people = LinkedHashMap<String, Loan>()
    val retag = LinkedHashMap<String, List<String>>()
    for (c in collections.filter { it.kind != CollectionType.WISHLIST }) for (e in c.entries) {
        val tag = lentTag(e) ?: continue
        retag[e.scryfallId] = e.userTags.filter { it != tag }
        val clean = if (placedCopies(e).all { it.placeId in known }) e else withPlaces(e, placedCopies(e).filter { it.placeId in known })
        val (free, freeFoil) = unplacedCopies(clean)
        val (plainOut, foilOut) = lentOf(byEntry, c.id, e)
        val who = tagBorrower(tag)
        val key = who.lowercase()
        for ((foil, n) in listOf(false to free - plainOut, true to freeFoil - foilOut)) {
            if (n <= 0) continue
            val loan = people[key] ?: Loan(newId(), who, lentAt = now, note = "From your “lent” tags").also { people[key] = it }
            people[key] = loan.copy(cards = loan.cards + loanCard(LoanCard(e.name, e.scryfallId, n, if (foil) true else null, collectionId = c.id)))
        }
    }
    val loans = people.values.map { loanOf(it) }
    return TagLoans(
        if (loans.isNotEmpty()) withLoans(collections, loansOf(collections) + loans) else collections,
        loans,
        retag.map { (id, tags) -> id to tags }
    )
}

/** How many copies are tagged "lent" with no loan yet — what "Turn 'lent' tags into loans" would turn. */
fun taggedLentCopies(collections: List<Collection>): Int =
    loansFromTags(collections, 0) { "" }.loans.sumOf { l -> l.cards.sumOf { it.qty } }

// ---- Sync: merging two devices' loans ----

private fun cardKey(c: LoanCard) =
    listOf(c.scryfallId, if (c.isFoil) "foil" else "", c.collectionId ?: "", c.placeId ?: "", c.section ?: "", c.page?.toString() ?: "", c.slot?.toString() ?: "", c.deckId ?: "").joinToString("|")

private fun <T> pick(base: T, mine: T, theirs: T, minePreferred: Boolean): T = when {
    mine == theirs -> mine
    mine == base -> theirs
    theirs == base -> mine
    minePreferred -> mine
    else -> theirs
}

private fun mergeLoanCards(base: List<LoanCard>, mine: List<LoanCard>, theirs: List<LoanCard>, minePreferred: Boolean): List<LoanCard> {
    val b = base.associateBy { cardKey(it) }
    val m = mine.associateBy { cardKey(it) }
    val t = theirs.associateBy { cardKey(it) }
    val added = (t.keys + m.keys).filter { it !in b }.distinct().sorted()
    val out = mutableListOf<LoanCard>()
    for (key in b.keys.toList() + added) {
        val bc = b[key]
        val mc = m[key]
        val tc = t[key]
        if (bc != null && (mc == null || tc == null)) continue
        if (bc == null) {
            val c = tc ?: mc!!
            out += loanCard(c.copy(qty = maxOf(mc?.qty ?: 0, tc?.qty ?: 0), back = maxOf(mc?.back ?: 0, tc?.back ?: 0)))
            continue
        }
        out += loanCard(
            tc!!.copy(
                name = pick(bc.name, mc!!.name, tc.name, minePreferred),
                qty = pick(bc.qty, mc.qty, tc.qty, minePreferred),
                // Getting cards back only ever counts up.
                back = maxOf(bc.back ?: 0, mc.back ?: 0, tc.back ?: 0)
            )
        )
    }
    return out
}

/**
 * Merges two devices' loans: one made on either side is kept, one deleted on either side stays
 * deleted, each field goes to whoever changed it (the more recent edit when both did), cards merge
 * card by card and the copies back only go up. Returned once every card is back. Null when no side
 * has the key.
 */
fun mergeLoans(base: List<Loan>?, mine: List<Loan>?, theirs: List<Loan>?, minePreferred: Boolean): List<Loan>? {
    if (base == null && mine == null && theirs == null) return null
    val b = base.orEmpty().associateBy { it.id }
    val m = mine.orEmpty().associateBy { it.id }
    val t = theirs.orEmpty().associateBy { it.id }
    val added = (t.keys + m.keys).filter { it !in b }.distinct().sorted()
    val out = mutableListOf<Loan>()
    for (id in b.keys.toList() + added) {
        val bl = b[id]
        val ml = m[id]
        val tl = t[id]
        if (bl != null && (ml == null || tl == null)) continue
        if (bl == null) { out += (tl ?: ml!!); continue }
        val merged = Loan(
            id = id,
            to = pick(bl.to, ml!!.to, tl!!.to, minePreferred),
            friendId = pick(bl.friendId, ml.friendId, tl.friendId, minePreferred),
            cards = mergeLoanCards(bl.cards, ml.cards, tl.cards, minePreferred),
            lentAt = minOf(ml.lentAt, tl.lentAt),
            backBy = pick(bl.backBy, ml.backBy, tl.backBy, minePreferred),
            gameNight = pick(bl.gameNight, ml.gameNight, tl.gameNight, minePreferred),
            note = pick(bl.note, ml.note, tl.note, minePreferred)
        )
        val returnedAt = maxOf(ml.returnedAt ?: 0, tl.returnedAt ?: 0)
        out += loanOf(if (isOpen(merged) || returnedAt == 0L) merged else merged.copy(returnedAt = returnedAt))
    }
    return out
}

/**
 * [theirs] with [source]'s loans, when [theirs] was saved by an app that doesn't know about loans (no
 * "loans" key) — the same object otherwise.
 */
fun keepLoansFromOlderApp(source: Collection, theirs: Collection): Collection {
    if (theirs.loans != null || source.loans == null || !theirs.isUnsorted) return theirs
    return theirs.copy(loans = source.loans)
}
