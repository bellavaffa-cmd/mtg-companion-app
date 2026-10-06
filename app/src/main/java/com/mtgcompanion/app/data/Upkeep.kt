package com.mtgcompanion.app.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * Upkeep: how tidy the storage is — "92% of copies have a place" — and the few things worth doing
 * this week, each opening the screen that does it:
 *  - copies with no place (and, when it's known, where most came from: "Most came from last week's
 *    import") → Put away;
 *  - places with something worth having in them not checked in 90 days → Check;
 *  - loans overdue → Remind;
 *  - boxes 90% full or more → Split;
 *  - deck pull lists started and not finished → Carry on.
 * The phone can say the count once a week, in a notification (UpkeepReminder.kt).
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/upkeep.ts rule for rule, with the
 * same tests (UpkeepTest.kt ↔ tests/collection/upkeep.test.ts).
 */

/** A place not checked for this many days, with something of value in it, is worth checking. */
const val CHECK_AFTER_DAYS = 90
/** At most this many places to check at once — the most valuable. */
const val MAX_CHECKS = 3

private const val DAY_MS = 86_400_000L

enum class UpkeepKind { PUT_AWAY, CHECK, REMIND, SPLIT, CARRY_ON }

/**
 * One thing worth doing: "Red box is 96% full" / "Room for about 28 more" / Split. [placeId] for
 * Check and Split, [personKey] (LoanPerson.key) for Remind, [deckId] for Carry on.
 */
data class UpkeepItem(
    val kind: UpkeepKind,
    val title: String,
    val detail: String,
    val action: String,
    val placeId: String? = null,
    val personKey: String? = null,
    val deckId: String? = null
)

/** The last import on this device: when, how many copies, and into which binder. */
data class ImportNote(val at: Long, val copies: Int, val collectionId: String)

/** A deck's pull list with some rows ticked ([ticked], PullProgress), and since when — null when not known. */
data class PullUnderway(val deckId: String, val ticked: Set<String>, val startedAt: Long? = null)

data class UpkeepReport(val percent: Int, val total: Int, val placed: Int, val items: List<UpkeepItem>)

/** "4 things worth doing this week"; "Nothing to do this week" when there's none. */
fun upkeepHeadline(count: Int): String = when (count) {
    0 -> "Nothing to do this week"
    1 -> "1 thing worth doing this week"
    else -> "$count things worth doing this week"
}

private fun localDate(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** When an import was, as "Most came from …": "today's import", "yesterday's import", "this week's import", "last week's import", "the import on 3 Oct". */
fun importWhen(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val days = ChronoUnit.DAYS.between(localDate(at, zone), localDate(now, zone))
    val d = localDate(at, zone)
    return when {
        days <= 0 -> "today's import"
        days == 1L -> "yesterday's import"
        days < 7 -> "this week's import"
        days < 14 -> "last week's import"
        else -> "the import on ${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
    }
}

/** When a pull list was started: "today", "yesterday", "Tuesday" within the week, else "3 Oct". */
fun startedWhen(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val d = localDate(at, zone)
    val days = ChronoUnit.DAYS.between(d, localDate(now, zone))
    return when {
        days <= 0 -> "today"
        days == 1L -> "yesterday"
        days < 7 -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.UK)
        else -> "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
    }
}

/** "half done" and the like, for a pull list [pulled] of [total] through. */
fun pullStage(pulled: Int, total: Int): String {
    val share = if (total > 0) pulled.toDouble() / total else 0.0
    return when {
        share >= 0.75 -> "nearly done"
        share >= 0.4 -> "half done"
        else -> "started"
    }
}

private val DECK_END = Regex("\\bdeck$", RegexOption.IGNORE_CASE)

/** "Krenko deck" — a deck called "Krenko deck" isn't "Krenko deck deck". */
fun deckTitle(name: String): String = name.trim().let { if (DECK_END.containsMatchIn(it)) it else "$it deck" }

/** "Priya's", "James'". */
fun possessive(name: String): String = name.trim().let { if (it.endsWith("s", ignoreCase = true)) "$it'" else "$it's" }

/**
 * Where most of the copies with no place came from: the last import when it brought in as many as
 * half of them ("Most came from last week's import"), else the binder holding most of them ("Most are
 * in Unsorted"), else the binders holding them ("In Unsorted and Trade binder"). "" when none.
 */
fun unplacedHint(collections: List<Collection>, decks: List<Deck>, lastImport: ImportNote?, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val known = placesOf(collections).map { it.id }.toSet()
    val lent = lentByEntry(lentCopies(collections, decks))
    val byBinder = LinkedHashMap<String, Int>()
    var all = 0
    for (c in collections) {
        if (c.kind == CollectionType.WISHLIST) continue
        for (e in c.entries) {
            if (lentTag(e) != null) continue
            val copies = e.quantity + e.foilQuantity
            val placed = placedCopies(e).filter { it.placeId in known }.sumOf { it.qty }
            val (plainOut, foilOut) = lentOf(lent, c.id, e)
            val n = copies - placed - plainOut - foilOut
            if (n <= 0) continue
            byBinder[c.id] = (byBinder[c.id] ?: 0) + n
            all += n
        }
    }
    if (all <= 0) return ""
    if (lastImport != null && lastImport.copies * 2 >= all && (byBinder[lastImport.collectionId] ?: 0) * 2 >= all) {
        return "Most came from ${importWhen(lastImport.at, now, zone)}"
    }
    val name = { id: String -> collections.firstOrNull { it.id == id }?.name ?: "a binder" }
    val top = byBinder.maxByOrNull { it.value } ?: return ""
    if (top.value * 2 > all) return "Most are in ${name(top.key)}"
    val names = byBinder.entries.sortedByDescending { it.value }.map { name(it.key) }
    return when (names.size) {
        2 -> "In ${names[0]} and ${names[1]}"
        else -> "In ${names[0]}, ${names[1]} and ${names.size - 2} more"
    }
}

/** What's in [placeId] and every place inside it is worth, in US dollars (copies with no price count nothing). */
fun valueWithin(collections: List<Collection>, placeId: String, price: (String, Boolean) -> Double?): Double =
    placeAndInside(placesOf(collections), placeId).sumOf { id ->
        cardsIn(collections, id).sumOf { (price(it.entry.scryfallId, it.line.isFoil) ?: 0.0) * it.line.qty }
    }

/**
 * Everything worth doing this week. [today] is the device's day ("2026-10-06"), [nights] its game
 * nights (for loans due at the next one); [price] one copy's price in US dollars (foil or not), null
 * when not known; [money] writes an amount the way the app shows prices.
 */
fun upkeep(
    collections: List<Collection>,
    decks: List<Deck>,
    now: Long,
    today: String,
    nights: List<NightDay> = emptyList(),
    pulls: List<PullUnderway> = emptyList(),
    lastImport: ImportNote? = null,
    price: (String, Boolean) -> Double? = { _, _ -> null },
    money: (Double) -> String = { "$" + String.format(Locale.UK, "%,.0f", it) },
    zone: ZoneId = ZoneId.systemDefault()
): UpkeepReport {
    val summary = storageSummary(collections, decks)
    val places = placesOf(collections)
    val items = mutableListOf<UpkeepItem>()

    if (summary.unplaced > 0) {
        items += UpkeepItem(
            UpkeepKind.PUT_AWAY,
            "${summary.unplaced} ${if (summary.unplaced == 1) "copy has" else "copies have"} no place",
            unplacedHint(collections, decks, lastImport, now, zone),
            "Put away"
        )
    }

    val stale = places.filter { it.placeKind != PlaceKind.DECK_BOX }.mapNotNull { p ->
        val since = p.lastChecked ?: p.createdAt.takeIf { it > 0 } ?: return@mapNotNull null
        val days = ((now - since) / DAY_MS).toInt()
        if (days < CHECK_AFTER_DAYS) return@mapNotNull null
        // Only the place's own copies: a shelf's boxes are checked one by one.
        val value = cardsIn(collections, p.id).sumOf { (price(it.entry.scryfallId, it.line.isFoil) ?: 0.0) * it.line.qty }
        if (value <= 0.0) null else Triple(p, days, value)
    }.sortedByDescending { it.third }.take(MAX_CHECKS)
    for ((p, days, value) in stale) {
        items += UpkeepItem(
            UpkeepKind.CHECK,
            if (p.lastChecked == null) "${p.name} never checked" else "${p.name} not checked in $days days",
            "${money(value)} inside",
            "Check",
            placeId = p.id
        )
    }

    for (person in loanPeople(loansOf(collections), today, nights)) {
        if (person.overdue <= 0) continue
        val usd = person.loans.sumOf { l -> l.cards.sumOf { c -> (price(c.scryfallId, c.isFoil) ?: 0.0) * stillOut(c) } }
        val cards = "${person.copies} ${if (person.copies == 1) "card" else "cards"}"
        items += UpkeepItem(
            UpkeepKind.REMIND,
            "${possessive(person.name)} ${if (person.loans.size == 1) "loan is" else "loans are"} ${person.overdue} ${if (person.overdue == 1) "day" else "days"} late",
            if (usd > 0) "$cards · ${money(usd)}" else cards,
            "Remind",
            personKey = person.key
        )
    }

    for (n in placeTree(places)) {
        val p = n.place
        if (p.placeKind == PlaceKind.BINDER) continue
        val space = spaceOf(p, collections) ?: continue
        if (!space.nearlyFull) continue
        items += UpkeepItem(UpkeepKind.SPLIT, "${p.name} is ${space.percent}% full", roomLine(space).removeSuffix("."), "Split", placeId = p.id)
    }

    for (u in pulls.sortedBy { it.startedAt ?: Long.MAX_VALUE }) {
        val deck = decks.firstOrNull { it.id == u.deckId } ?: continue
        val list = pullList(deck, collections, decks)
        val pulled = pulledCopies(list.groups.flatMap { it.rows }, u.ticked)
        if (pulled <= 0 || pulled >= list.total) continue
        val started = u.startedAt?.let { ", started ${startedWhen(it, now, zone)}" } ?: ""
        items += UpkeepItem(
            UpkeepKind.CARRY_ON,
            "${deckTitle(deck.name)} pull list ${pullStage(pulled, list.total)}",
            "$pulled of ${list.total} pulled$started",
            "Carry on",
            deckId = deck.id
        )
    }

    return UpkeepReport(placedPercent(summary), summary.total, summary.placed, items)
}
