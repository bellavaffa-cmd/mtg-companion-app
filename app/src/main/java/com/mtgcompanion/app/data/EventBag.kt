package com.mtgcompanion.app.data

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/*
 * Packing for an event: "Pack your bag" for tonight's game night, an event, or a quick "Pack for…"
 * with a name and a day. A checklist in three sections —
 *  - Decks: the decks chosen, each with the deck box it's in (Gear.kt), or a warning when some of its
 *    cards are out on loan ("Sol Ring lent to Sam", Loans.kt);
 *  - Tokens and extras: the tokens each deck's cards make, with how many (DeckTokens.kt
 *    tokensToBring), the counters each deck asks for, and the dice and playmat from the gear;
 *  - For trades: per person coming, the cards they want that are in a place ("3 cards Priya wants ·
 *    Trade binder p4, p7", the same trade matches as Friends want these — FriendsWant.kt), and the
 *    cards borrowed from them to give back.
 * The ticks are kept on this device only (EventBagStore.kt): a bag is for one trip, not worth syncing.
 * "Coming home" re-checks the decks and extras that went out (what was traded or given back stays
 * gone); "All packed" when every line is ticked.
 *
 * Pure, so it can be tested. The web app's src/collection/eventBag.ts, rule for rule, with the same
 * tests (EventBagTest.kt ↔ tests/collection/eventBag.test.ts).
 */

enum class BagSection(val label: String) { DECKS("Decks"), EXTRAS("Tokens and extras"), TRADES("For trades") }

/** A bag being packed (this device only). [day]: "2026-10-10". [home]: the lines ticked off coming home. */
data class PackingBag(
    val id: String,
    val name: String,
    val day: String,
    /** Who's coming, by name. */
    val attendees: List<String> = emptyList(),
    val deckIds: List<String> = emptyList(),
    val packed: List<String> = emptyList(),
    val comingHome: Boolean = false,
    val home: List<String> = emptyList(),
    val createdAt: Long = 0L
)

data class BagLine(
    val key: String,
    val section: BagSection,
    val title: String,
    val detail: String,
    /** The detail is a warning (cards out on loan). */
    val warn: Boolean
)

/** One person's wants among the user's placed cards. */
data class WantedBy(val friend: String, val cards: List<PlacedCard>)

/** Cards borrowed from someone and not given back yet. */
data class BorrowedFrom(val from: String, val cards: Int)

data class BagInput(
    /** The decks chosen, in order. */
    val decks: List<Deck>,
    val collections: List<Collection>,
    val gear: List<GearItem>,
    /** Each deck's tokens to bring, by deck id; a deck not here is still loading (or has none). */
    val tokens: Map<String, List<TokenToBring>>,
    /** Each deck's counters, by deck id (DeckTokens.kt countersNeeded). */
    val counters: Map<String, List<String>>,
    val wants: List<WantedBy>,
    val borrowed: List<BorrowedFrom>,
    val attendees: List<String>
)

/** Whether [name] (a friend's display name) is one of [attendees]: the same, or one's first name of the other. */
fun isComing(name: String, attendees: List<String>): Boolean {
    val n = name.trim().lowercase()
    if (n.isEmpty()) return false
    return attendees.any { a ->
        val x = a.trim().lowercase()
        x.isNotEmpty() && (x == n || n.startsWith("$x ") || x.startsWith("$n "))
    }
}

/** "Sol Ring lent to Sam", "Sol Ring and 2 more lent to Sam; Mana Crypt lent to Priya" — or null when none are out. */
fun lentFromDeckLine(deck: Deck, loans: List<Loan>): String? {
    val parts = mutableListOf<String>()
    for (loan in loans) {
        if (!isOpen(loan)) continue
        val names = loan.cards.filter { it.deckId == deck.id && stillOut(it) > 0 }.map { it.name }.distinct()
        if (names.isEmpty()) continue
        val what = when (names.size) {
            1 -> names[0]
            2 -> "${names[0]} and ${names[1]}"
            else -> "${names[0]} and ${names.size - 1} more"
        }
        parts += "$what lent to ${loan.to}"
    }
    return if (parts.isEmpty()) null else parts.joinToString("; ")
}

private fun lowerFirst(s: String) = s.replaceFirstChar { it.lowercase() }

/** "Trade binder p4, p7, Red box" — where [cards] are, by place, with the binder pages. */
fun wantedWhereLine(cards: List<PlacedCard>, collections: List<Collection>): String {
    val places = placesOf(collections)
    val byPlace = LinkedHashMap<String, MutableSet<Int>>()
    for (c in cards) {
        val pages = byPlace.getOrPut(c.line.placeId) { mutableSetOf() }
        val page = c.line.page
        if (page != null && page > 0) pages += page
    }
    return byPlace.entries.joinToString(", ") { (id, pages) ->
        val name = places.firstOrNull { it.id == id }?.name ?: "A place"
        if (pages.isNotEmpty()) "$name ${pages.sorted().joinToString(", ") { "p$it" }}" else name
    }
}

/** "Dice, playmat": the dice and playmats in the gear — both when there are neither, as a reminder. */
fun extrasTitle(gear: List<GearItem>): String {
    val dice = gear.any { it.gearKind == GearKind.DICE }
    val mat = gear.any { it.gearKind == GearKind.PLAYMAT }
    return when {
        dice && !mat -> "Dice"
        mat && !dice -> "Playmat"
        else -> "Dice, playmat"
    }
}

private class TokenLine(val name: String, var count: Int, val forDecks: MutableList<String>)

/** Every line of the bag, section by section. */
fun bagLines(input: BagInput): List<BagLine> {
    val loans = input.collections.firstOrNull { it.isUnsorted }?.loans.orEmpty()
    val out = mutableListOf<BagLine>()
    for (deck in input.decks) {
        val lent = lentFromDeckLine(deck, loans)
        val box = input.gear.firstOrNull { it.gearKind == GearKind.DECK_BOX && it.holds == deck.id }
        out += BagLine("deck:${deck.id}", BagSection.DECKS, deck.name, lent ?: box?.let { "Deck box, ${lowerFirst(it.name)}" } ?: "", lent != null)
    }
    // Each token once, as many as the deck needing most of it; for the decks that make it.
    val tokens = mutableListOf<TokenLine>()
    for (deck in input.decks) {
        for (t in input.tokens[deck.id].orEmpty()) {
            val already = tokens.firstOrNull { sameToken(it.name, t.name) }
            if (already != null) {
                already.count = maxOf(already.count, t.count)
                if (shortDeckName(deck) !in already.forDecks) already.forDecks += shortDeckName(deck)
            } else {
                tokens += TokenLine(t.name, t.count, mutableListOf(shortDeckName(deck)))
            }
        }
    }
    for (t in tokens) {
        out += BagLine("token:${t.name.trim().lowercase()}", BagSection.EXTRAS, "${t.name} tokens ×${t.count}", "for ${namesAnd(t.forDecks)}", false)
    }
    for (deck in input.decks) {
        val kinds = input.counters[deck.id].orEmpty()
        if (kinds.isNotEmpty()) out += BagLine("counters:${deck.id}", BagSection.EXTRAS, countersLabel(kinds), "for ${shortDeckName(deck)}", false)
    }
    out += BagLine("gear:extras", BagSection.EXTRAS, extrasTitle(input.gear), "Gear", false)
    val seen = mutableSetOf<String>()
    for (w in input.wants) {
        if (!isComing(w.friend, input.attendees) || w.cards.isEmpty()) continue
        val key = "wants:${w.friend.trim().lowercase()}"
        if (!seen.add(key)) continue
        val n = w.cards.size
        out += BagLine(key, BagSection.TRADES, "$n ${if (n == 1) "card" else "cards"} ${w.friend} wants", wantedWhereLine(w.cards, input.collections), false)
    }
    for (b in input.borrowed) {
        if (!isComing(b.from, input.attendees) || b.cards <= 0) continue
        val key = "borrowed:${b.from.trim().lowercase()}"
        if (!seen.add(key)) continue
        out += BagLine(key, BagSection.TRADES, "${b.from}'s borrowed cards", "to give back", false)
    }
    return out
}

// ---- Ticking ----

/** The lines coming home re-checks: the decks and extras that went out (trades and give-backs stay gone). */
fun homeLines(bag: PackingBag, lines: List<BagLine>): List<BagLine> = lines.filter { it.section != BagSection.TRADES && it.key in bag.packed }

/** The lines shown: everything while packing, what went out when coming home. */
fun shownLines(bag: PackingBag, lines: List<BagLine>): List<BagLine> = if (bag.comingHome) homeLines(bag, lines) else lines

/** Whether [key] is ticked in the bag's current mode. */
fun isTicked(bag: PackingBag, key: String): Boolean = key in (if (bag.comingHome) bag.home else bag.packed)

/** [bag] with [key] ticked or unticked in its current mode. */
fun toggleTick(bag: PackingBag, key: String): PackingBag {
    fun flip(list: List<String>) = if (key in list) list - key else list + key
    return if (bag.comingHome) bag.copy(home = flip(bag.home)) else bag.copy(packed = flip(bag.packed))
}

/** [bag] with every shown line ticked — "All packed". */
fun tickAll(bag: PackingBag, lines: List<BagLine>): PackingBag {
    val keys = shownLines(bag, lines).map { it.key }
    return if (bag.comingHome) bag.copy(home = (bag.home + keys).distinct()) else bag.copy(packed = (bag.packed + keys).distinct())
}

/** Whether every shown line is ticked. */
fun allTicked(bag: PackingBag, lines: List<BagLine>): Boolean = shownLines(bag, lines).all { isTicked(bag, it.key) }

/** [bag] switched to coming home (nothing ticked off yet), or back to packing. */
fun setComingHome(bag: PackingBag, on: Boolean): PackingBag =
    if (on && !bag.comingHome) bag.copy(comingHome = true, home = emptyList()) else bag.copy(comingHome = on)

/** What went out and isn't back yet — the coming-home diff. */
fun stillAway(bag: PackingBag, lines: List<BagLine>): List<BagLine> = homeLines(bag, lines).filter { it.key !in bag.home }

/** "Everything came back." / "Still to come back: Atraxa and Goblin tokens ×20." */
fun homeSummary(bag: PackingBag, lines: List<BagLine>): String {
    val away = stillAway(bag, lines)
    if (away.isEmpty()) return "Everything came back."
    return "Still to come back: ${namesAnd(away.map { it.title })}."
}

// ---- The day ----

private val MONTH_NAMES = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val WEEKDAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** "Today", "Tomorrow", "Saturday" (within the week), else "12 Oct" — for [day] seen from [today]. */
fun dayLabel(day: String, today: String): String {
    if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(day)) return day
    val d = try { LocalDate.parse(day) } catch (_: DateTimeParseException) { return day }
    val t = try { LocalDate.parse(today) } catch (_: DateTimeParseException) { return day }
    val diff = ChronoUnit.DAYS.between(t, d)
    return when {
        diff == 0L -> "Today"
        diff == 1L -> "Tomorrow"
        diff in 2L..6L -> WEEKDAY_NAMES[d.dayOfWeek.value - 1]
        else -> "${d.dayOfMonth} ${MONTH_NAMES[d.monthValue - 1]}"
    }
}

/** "Saturday · Game night at Priya's". */
fun bagHeading(bag: PackingBag, today: String): String = "${dayLabel(bag.day, today)} · ${bag.name}"

/** A new bag: nothing ticked yet. */
fun newBag(id: String, name: String, day: String, attendees: List<String>, deckIds: List<String>, now: Long): PackingBag = PackingBag(
    id = id, name = name.trim().ifEmpty { "Game night" }, day = day,
    attendees = attendees.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
    deckIds = deckIds.distinct(), createdAt = now
)

/** "Priya, Sam" typed in: the names. */
fun namesFrom(text: String): List<String> = text.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

/** Bags kept: the newest few. */
const val MAX_BAGS = 12
