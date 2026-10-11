package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.collection.ADVANCED_COLORS
import com.mtgcompanion.app.ui.collection.AdvancedFacts
import com.mtgcompanion.app.ui.collection.AdvancedFilter
import com.mtgcompanion.app.ui.collection.COLLECTION_FILTER_RARITIES
import com.mtgcompanion.app.ui.collection.CollectionFilter
import com.mtgcompanion.app.ui.collection.CopyFacts
import com.mtgcompanion.app.ui.collection.UNTAGGED
import com.mtgcompanion.app.ui.collection.advancedFactsOf
import com.mtgcompanion.app.ui.collection.advancedFrom
import com.mtgcompanion.app.ui.collection.advancedJson
import com.mtgcompanion.app.ui.collection.advancedMatches
import com.mtgcompanion.app.ui.collection.basicFrom
import com.mtgcompanion.app.ui.collection.basicJson
import com.mtgcompanion.app.ui.collection.filterChips
import org.json.JSONArray
import org.json.JSONObject
import com.mtgcompanion.app.ui.collection.CardFacts as FilterFacts

/*
 * A storage place's rules: what it holds, how its sections go and the order inside a section — "a box
 * holds the blue cards, in sections by card type, A–Z inside each". And, when a card fits more than one
 * place, the order the user chose ("Rule order"): the first place that fits gets it.
 *
 *  - Holds: a Cards filter (the filter panel's and Advanced filters', tags and Untagged too), kept on
 *    the place as a saved filter's JSON (StoragePlace.holds). None: the place holds everything.
 *  - Sections by: one level of the sorting recipes (SortRecipes.kt's SplitLevel: colour, card type,
 *    set, value bands…); the place's sections are that level's piles, their names still the user's to
 *    change.
 *  - Then sorted by: up to two of SORT_BYS — A–Z, collector number, mana value, price (dearest first),
 *    set. Put away says where in the section a card goes: "between Counterspell and Cryptic Command".
 *  - Rule order: the Unsorted pile's "placeOrder" (Collection.placeOrder), the places' ids. Places not
 *    in it follow, those that hold something particular before those that hold everything.
 *
 * Older apps know only a place's "sortRule" (COLOUR, SET, TYPE, NAME). A place with only that reads as
 * the rules it meant (placeRules: COLOUR → sections by colour, A–Z; SET → by set, by number; TYPE → by
 * card type, A–Z; NAME → no sections, A–Z), and saving rules writes the nearest "sortRule" too
 * (sortRuleFor) — so an older app still sees roughly what the place does. An older app drops the rule
 * fields when it saves; keepPlaceRules puts them back.
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/storageRules.ts rule for rule; both
 * run the same test vectors (app/src/test/resources/storageRulesVectors.json ↔
 * tests/collection/storageRulesVectors.json).
 */

// ---- What the rules are ----

/** The ways a section can be sorted, in the editor's words' order. Stored by name. */
val SORT_BYS = listOf("NAME", "NUMBER", "MANA_VALUE", "VALUE", "SET")

/** "Then sorted by": the choices offered, as the stored list joined by commas, and their words. */
val SORT_BY_CHOICES = listOf(
    "NAME" to "A–Z",
    "NUMBER" to "Collector number",
    "MANA_VALUE,NAME" to "Mana value, then A–Z",
    "VALUE" to "Price, high to low",
    "SET,NUMBER" to "Set, then number"
)

/** How a sort shows on a place's line: "A–Z", "by mana value". */
private val SORT_BY_SHORT = mapOf(
    "NAME" to "A–Z", "NUMBER" to "by number", "MANA_VALUE" to "by mana value", "VALUE" to "price high to low", "SET" to "by set"
)

/** A place's rules as they're used: what it holds (null: everything), its sections' level and its order inside a section. */
data class PlaceRules(
    val holds: Pair<CollectionFilter, AdvancedFilter>? = null,
    val sectionsBy: SplitLevel? = null,
    val sortBy: List<String> = emptyList()
) {
    val any: Boolean get() = holds != null || sectionsBy != null || sortBy.isNotEmpty()
}

/** A sort as both apps keep it: known ways only, each once, at most two. */
fun cleanSortBy(list: List<String>): List<String> = list.filter { it in SORT_BYS }.distinct().take(2)

/** A section level as both apps keep it (splitLevel); "PLACE" isn't one. */
fun cleanSectionsBy(level: SplitLevel): SplitLevel? = if (level.by in LEVEL_BYS) splitLevel(level) else null

/** "Holds" read from the place: the filter, or null for everything (none set, unreadable, or nothing on). */
fun holdsOf(place: StoragePlace): Pair<CollectionFilter, AdvancedFilter>? = holdsFromMap(place.holds)

fun holdsFromMap(map: Map<String, Any?>?): Pair<CollectionFilter, AdvancedFilter>? {
    if (map == null) return null
    // Asked for again and again (each card against each place): read once per filter.
    synchronized(holdsRead) { if (holdsRead.containsKey(map)) return holdsRead[map] }
    val read = readHolds(map)
    synchronized(holdsRead) { holdsRead[map] = read }
    return read
}

private val holdsRead = java.util.WeakHashMap<Map<String, Any?>, Pair<CollectionFilter, AdvancedFilter>?>()

private fun readHolds(map: Map<String, Any?>): Pair<CollectionFilter, AdvancedFilter>? {
    val o = try { JSONObject(map) } catch (e: Exception) { return null }
    val basic = o.optJSONObject("basic")?.let(::basicFrom) ?: CollectionFilter()
    val advanced = o.optJSONObject("advanced")?.let(::advancedFrom) ?: AdvancedFilter()
    return if (basic.active || advanced.active) basic to advanced else null
}

/**
 * A "Holds" filter as the place keeps it: the saved filter's "basic", and its "advanced" when one is on
 * — the same JSON both apps write. Null when nothing's on (the place holds everything).
 */
fun holdsMap(basic: CollectionFilter, advanced: AdvancedFilter): Map<String, Any?>? {
    if (!basic.active && !advanced.active) return null
    val text = "{\"basic\":${basicJson(basic)}" + (if (advanced.active) ",\"advanced\":${advancedJson(advanced)}" else "") + "}"
    @Suppress("UNCHECKED_CAST")
    return jsonValue(JSONObject(text)) as Map<String, Any?>
}

/** The holds map written afresh (null when it holds everything), so two apps' copies are equal. */
fun cleanHolds(map: Map<String, Any?>?): Map<String, Any?>? = holdsFromMap(map)?.let { (b, a) -> holdsMap(b, a) }

/** org.json's values as plain Kotlin ones — maps, lists, strings, booleans and numbers as Double — as Moshi reads them. */
private fun jsonValue(v: Any?): Any? = when (v) {
    is JSONObject -> LinkedHashMap<String, Any?>().also { m -> v.keys().forEach { k -> m[k] = jsonValue(v.opt(k)) } }
    is JSONArray -> (0 until v.length()).map { jsonValue(v.opt(it)) }
    is Number -> v.toDouble()
    JSONObject.NULL -> null
    else -> v
}

/**
 * The rules a place goes by. A binder's order is its "sortRule" (BinderPages.kt), so a binder has only
 * what it holds. Anything else with new rules goes by them; one with only an older app's "sortRule"
 * goes by what that meant: COLOUR → sections by colour (lands apart), A–Z; SET → by set, by number;
 * TYPE → by card type, A–Z; NAME → no sections, A–Z.
 */
fun placeRules(place: StoragePlace): PlaceRules {
    val holds = holdsOf(place)
    if (place.placeKind == PlaceKind.BINDER) return PlaceRules(holds)
    if (place.sortBy != null || place.sectionsBy != null) {
        return PlaceRules(holds, place.sectionsBy?.let { cleanSectionsBy(it) }, cleanSortBy(place.sortBy.orEmpty()))
    }
    return when (SortRule.fromName(place.sortRule)) {
        SortRule.COLOUR -> PlaceRules(holds, SplitLevel("COLOUR", lands = true), listOf("NAME"))
        SortRule.SET -> PlaceRules(holds, SplitLevel("SET", sets = emptyList()), listOf("NUMBER"))
        SortRule.TYPE -> PlaceRules(holds, SplitLevel("TYPE"), listOf("NAME"))
        SortRule.NAME -> PlaceRules(holds, null, listOf("NAME"))
        null -> PlaceRules(holds)
    }
}

/** The old rule nearest new rules, for apps that only know "sortRule": null when none is near. */
fun sortRuleFor(sectionsBy: SplitLevel?, sortBy: List<String>): SortRule? = when (sectionsBy?.by) {
    "COLOUR" -> SortRule.COLOUR
    "TYPE" -> SortRule.TYPE
    "SET" -> SortRule.SET
    null -> if (sortBy.firstOrNull() == "NAME") SortRule.NAME else if (sortBy.firstOrNull() == "SET" || sortBy.firstOrNull() == "NUMBER") SortRule.SET else null
    else -> null
}

/**
 * [place] with these rules, as the editor saves them: what it holds, and — anything but a binder — its
 * sections' level, its order inside a section and the nearest old "sortRule". [sortBy] is always
 * written once a place has rules ([] for none), to tell this app's save from an older one's.
 */
fun withRules(place: StoragePlace, holds: Map<String, Any?>?, sectionsBy: SplitLevel?, sortBy: List<String>): StoragePlace {
    val cleanHolds = cleanHolds(holds)
    if (place.placeKind == PlaceKind.BINDER) {
        val had = place.sortBy != null || place.holds != null
        return place.copy(holds = cleanHolds, sectionsBy = null, sortBy = if (cleanHolds != null || had) emptyList() else null)
    }
    val level = sectionsBy?.let { cleanSectionsBy(it) }
    val order = cleanSortBy(sortBy)
    val had = place.sortBy != null || place.sectionsBy != null || place.holds != null
    val any = cleanHolds != null || level != null || order.isNotEmpty()
    return place.copy(
        holds = cleanHolds,
        sectionsBy = level,
        sortBy = if (any || had) order else null,
        sortRule = sortRuleFor(level, order)?.name
    )
}

/** Whether a place has a rule to put cards away by: it holds something particular, or it has sections or an order. */
fun hasRule(place: StoragePlace): Boolean {
    val r = placeRules(place)
    return if (place.placeKind == PlaceKind.BINDER) r.holds != null else r.any
}

// ---- Rule order ----

/** The user's rule order (Collection.placeOrder), kept on the Unsorted pile. */
fun placeOrderOf(collections: List<Collection>): List<String> = collections.firstOrNull { it.isUnsorted }?.placeOrder.orEmpty()

/** [collections] with the rule order set to [order] (on the Unsorted pile, made if it isn't there). */
fun withPlaceOrder(collections: List<Collection>, order: List<String>): List<Collection> =
    withUnsortedPile(collections).map { if (it.isUnsorted) it.copy(placeOrder = order.distinct()) else it }

/**
 * The places with a rule, in the order they're tried: as the user ordered them, then the rest in tree
 * order — those holding something particular before those holding everything.
 */
fun rulePlaces(places: List<StoragePlace>, order: List<String>): List<StoragePlace> {
    val ruled = placeTree(places).map { it.place }.filter { hasRule(it) }
    val byId = ruled.associateBy { it.id }
    val listed = order.distinct().mapNotNull { byId[it] }
    val rest = ruled.filter { it.id !in order }
    return listed + rest.filter { holdsOf(it) != null } + rest.filter { holdsOf(it) == null }
}

/** [order] with [id] moved to [to] (an index into rulePlaces), written out in full. */
fun movedInOrder(places: List<StoragePlace>, order: List<String>, id: String, to: Int): List<String> {
    val ids = rulePlaces(places, order).map { it.id }.toMutableList()
    if (!ids.remove(id)) return ids
    ids.add(to.coerceIn(0, ids.size), id)
    return ids
}

// ---- What a card is, for the rules ----

/**
 * A card as the rules judge it: the sorting facts (name, colours, type, set, number, mana value,
 * prices — [facts]), and for "Holds" the Cards filter's facts ([basic], [advanced]: null when the
 * card's data isn't here, so a place that holds something particular can't take it), the copy's own
 * ([copy]: its finish, condition…), its role tags (null: not looked up) and the user's own tags.
 */
data class RuleCard(
    val facts: CardFacts,
    val basic: FilterFacts? = null,
    val advanced: AdvancedFacts? = null,
    val copy: CopyFacts? = null,
    val roleTags: List<String>? = null,
    val userTags: List<String> = emptyList()
)

/** A Scryfall card as the rules judge it. */
fun ruleCardOf(card: ScryfallCard, foil: Boolean = false, roleTags: List<String>? = null, userTags: List<String> = emptyList(), copy: CopyFacts? = null): RuleCard =
    RuleCard(cardFactsOf(card).copy(foil = foil), FilterFacts.of(card), advancedFactsOf(card), copy, roleTags, userTags)

/**
 * A card as the rules judge it from its sorting facts alone, when its Scryfall data isn't at hand (a
 * deck's card being put back, a sorted pile being filed): enough for colours, type, rarity, set, mana
 * value and price — not rules text or keywords. Without a type line there's nothing to judge by.
 */
fun ruleCardOfFacts(f: CardFacts): RuleCard {
    val typeLine = f.typeLine ?: return RuleCard(f)
    val identity = (f.colorIdentity ?: f.colors).orEmpty().map { it.uppercase() }
    return RuleCard(
        f,
        FilterFacts(identity.mapNotNull { it.firstOrNull() }.toSet(), typeLine, f.rarity.orEmpty().lowercase(), "", f.set.orEmpty().lowercase()),
        AdvancedFacts(colors = f.colors.orEmpty().map { it.uppercase() }, identity = identity, cmc = f.cmc, set = f.set.orEmpty().lowercase(), usd = f.usd, usdFoil = f.usdFoil)
    )
}

/** Whether [card] is one [holds] takes — everything when null. Prices typed in the chosen currency go through [toUsd]. */
fun holdsMatch(holds: Pair<CollectionFilter, AdvancedFilter>?, card: RuleCard, toUsd: (Double) -> Double = { it }): Boolean {
    val (basic, advanced) = holds ?: return true
    if (!basic.matches(card.basic)) return false
    if (!basic.matchesTags(card.roleTags, card.userTags)) return false
    val copy = card.copy ?: CopyFacts(nonfoil = if (card.facts.foil) 0 else 1, foil = if (card.facts.foil) 1 else 0)
    return advancedMatches(advanced, card.advanced, copy, toUsd)
}

// ---- Sections ----

/** Dollars as the value sections are named when no currency is given: "$5", "$0.50". */
fun plainDollars(n: Double): String = if (n == Math.floor(n)) "$" + n.toLong() else "$" + String.format(java.util.Locale.US, "%.2f", n)

private fun recipeCardOf(f: CardFacts) = RecipeCard(
    name = f.name, colors = f.colors, colorIdentity = f.colorIdentity, typeLine = f.typeLine, set = f.set,
    collectorNumber = f.collectorNumber, cmc = f.cmc, rarity = f.rarity, usd = f.usd, usdFoil = f.usdFoil, foil = f.foil
)

/**
 * The sections a level makes, in order — what a place's sections start as when its rule is picked.
 * A set level naming no sets makes a section per set as cards come, so none to start with.
 */
fun ruleSections(level: SplitLevel?, fmt: (Double) -> String = ::plainDollars): List<String> {
    val l = level?.let { cleanSectionsBy(it) } ?: return emptyList()
    if (l.by == "SET" && l.sets.isNullOrEmpty()) return emptyList()
    return levelBuckets(l, fmt).map { it.label }
}

/** The section [level] gives a card, by its own name ("Blue", "Creatures", "MH3", "$5–20"). [rate]: the user's currency per US dollar. */
fun sectionLabel(level: SplitLevel, f: CardFacts, fmt: (Double) -> String = ::plainDollars, rate: Double = 1.0): String? {
    val l = cleanSectionsBy(level) ?: return null
    if (l.by == "SET" && l.sets.isNullOrEmpty()) return f.set?.uppercase()?.takeIf { it.isNotEmpty() }
    val key = bucketOf(l, recipeCardOf(f), rate)
    return levelBuckets(l, fmt).firstOrNull { it.key == key }?.label
}

private val LETTER_RANGE_SECTION = Regex("^([A-Z])\\s*[–-]\\s*([A-Z])$", RegexOption.IGNORE_CASE)

private fun holdsLetter(section: String, letter: String): Boolean {
    val range = LETTER_RANGE_SECTION.find(section.trim())
    if (range != null) return letter >= range.groupValues[1].uppercase() && letter <= range.groupValues[2].uppercase()
    return section.trim().uppercase() == letter
}

/**
 * The section of [place] a card goes in, spelled as the place spells it when it has one by that name;
 * null when the place has no sections. A place sorted A–Z with no level but sections like "A–F" files
 * by the name's first letter, as before.
 */
fun ruleSectionFor(place: StoragePlace, f: CardFacts, fmt: (Double) -> String = ::plainDollars, rate: Double = 1.0): String? {
    val r = placeRules(place)
    val sections = place.sections.orEmpty()
    val level = r.sectionsBy
    if (level == null) {
        if (r.sortBy.firstOrNull() == "NAME" && sections.isNotEmpty()) return sections.firstOrNull { holdsLetter(it, letterOf(f.name)) }
        return null
    }
    val label = sectionLabel(level, f, fmt, rate) ?: return null
    return sections.firstOrNull { it.equals(label, ignoreCase = true) } ?: label
}

/**
 * Whether a card fits [place]'s rules: the place holds it, and — when its sections are set — the
 * section the rule gives it is one of them (a box of only Red and Green takes no blue card).
 */
fun fitsPlace(place: StoragePlace, card: RuleCard, toUsd: (Double) -> Double = { it }, fmt: (Double) -> String = ::plainDollars, rate: Double = 1.0): Boolean {
    val r = placeRules(place)
    if (!holdsMatch(r.holds, card, toUsd)) return false
    val sections = place.sections.orEmpty()
    if (sections.isEmpty() || place.placeKind == PlaceKind.BINDER) return true
    if (r.sectionsBy == null && r.sortBy.firstOrNull() != "NAME") return true
    val s = ruleSectionFor(place, card.facts, fmt, rate) ?: return false
    return sections.any { it.equals(s, ignoreCase = true) }
}

/**
 * The place a card goes to: the first in rule order whose rules it fits and that has room
 * ([room]: false for a place that's full). Null when none fits.
 */
fun placeFor(
    places: List<StoragePlace>,
    order: List<String>,
    card: RuleCard,
    room: (StoragePlace) -> Boolean = { true },
    toUsd: (Double) -> Double = { it },
    fmt: (Double) -> String = ::plainDollars,
    rate: Double = 1.0
): StoragePlace? = rulePlaces(places, order).firstOrNull { fitsPlace(it, card, toUsd, fmt, rate) && room(it) }

/** Whether [place] has room for one more card: true when it has no size. */
fun hasRoom(place: StoragePlace, collections: List<Collection>): Boolean {
    val space = spaceOf(place, collections) ?: return true
    return space.used < space.size
}

// ---- Order inside a section ----

private fun leadingNumber(s: String?): Long = Regex("^\\d+").find((s ?: "").trim())?.value?.toLongOrNull() ?: Long.MAX_VALUE
private fun priceOf(f: CardFacts): Double? = if (f.foil) f.usdFoil ?: f.usd else f.usd ?: f.usdFoil
private fun nameOrder(a: CardFacts, b: CardFacts) = a.name.trim().lowercase().compareTo(b.name.trim().lowercase())

private fun compareOne(by: String, a: CardFacts, b: CardFacts): Int = when (by) {
    "NAME" -> nameOrder(a, b)
    "NUMBER" -> leadingNumber(a.collectorNumber).compareTo(leadingNumber(b.collectorNumber)).takeIf { it != 0 }
        ?: (a.collectorNumber ?: "").compareTo(b.collectorNumber ?: "")
    "MANA_VALUE" -> (a.cmc ?: Double.MAX_VALUE).compareTo(b.cmc ?: Double.MAX_VALUE)
    // Dearest first; no price last.
    "VALUE" -> (priceOf(b) ?: -1.0).compareTo(priceOf(a) ?: -1.0)
    "SET" -> (a.set ?: "").lowercase().compareTo((b.set ?: "").lowercase())
    else -> 0
}

/** How two cards go in a section sorted by [sortBy]: each way in turn, then A–Z. */
fun compareForSort(sortBy: List<String>, a: CardFacts, b: CardFacts): Int {
    for (by in sortBy) {
        val c = compareOne(by, a, b)
        if (c != 0) return c
    }
    return nameOrder(a, b)
}

/** [items] in [sortBy]'s order (stable: equal ones keep theirs). */
fun <T> sortedForRule(sortBy: List<String>, items: List<T>, facts: (T) -> CardFacts): List<T> =
    if (sortBy.isEmpty()) items else items.sortedWith { x, y -> compareForSort(sortBy, facts(x), facts(y)) }

/** Whether a sort needs more than a card's name (its number, mana value, price or set). */
fun needsCardData(sortBy: List<String>): Boolean = sortBy.any { it != "NAME" }

/**
 * Where in a section a card goes among [others] (the cards there), in words: "between Counterspell and
 * Cryptic Command", "before Brainstorm", "after Ponder", "next to Opt" (another copy). With none there:
 * "around “L”" A–Z, "around #117" by number, else null.
 */
fun sortHint(sortBy: List<String>, card: CardFacts, others: List<CardFacts>): String? {
    if (sortBy.isEmpty()) return null
    val names = others.distinctBy { it.name.trim().lowercase() }
    if (names.isEmpty()) return when (sortBy.first()) {
        "NAME" -> "around “${letterOf(card.name)}”"
        "NUMBER" -> card.collectorNumber?.let { "around #$it" }
        "SET" -> if (sortBy.getOrNull(1) == "NUMBER") card.collectorNumber?.let { "around #$it" } else null
        else -> null
    }
    names.firstOrNull { sameCardName(it.name, card.name) }?.let { return "next to ${it.name}" }
    val sorted = names.sortedWith { a, b -> compareForSort(sortBy, a, b) }
    val at = sorted.indexOfFirst { compareForSort(sortBy, card, it) < 0 }.let { if (it < 0) sorted.size else it }
    return when (at) {
        0 -> "before ${sorted[0].name}"
        sorted.size -> "after ${sorted.last().name}"
        else -> "between ${sorted[at - 1].name} and ${sorted[at].name}"
    }
}

/**
 * Where a card goes in a place that isn't a binder: its section by the rules, and where in it among
 * the cards there ([cards]: the place's, cardsIn; [factsOf]: a copy's facts when its card data is here,
 * else only its name counts — and then only for A–Z). The section and the hint as words:
 * "Blue › between Counterspell and Cryptic Command".
 */
fun ruleSpot(
    place: StoragePlace,
    f: CardFacts,
    cards: List<PlacedCard>,
    factsOf: (CollectionEntry) -> CardFacts? = { null },
    fmt: (Double) -> String = ::plainDollars,
    rate: Double = 1.0
): Pair<Spot, String?> {
    val r = placeRules(place)
    val section = ruleSectionFor(place, f, fmt, rate)
    val inSection = cards.filter { (it.line.section ?: "").equals(section ?: "", ignoreCase = true) }
    val known = if (needsCardData(r.sortBy)) inSection.mapNotNull { factsOf(it.entry) } else inSection.map { factsOf(it.entry) ?: CardFacts(it.entry.name) }
    val hint = sortHint(r.sortBy, f, known)
    val words = listOfNotNull(section, hint).joinToString(" › ")
    return Spot(place.id, section = section) to words.ifEmpty { null }
}

// ---- What doesn't belong ----

/** One line of copies in a place that its rules say goes elsewhere: another place ([to], null when none fits), or another [section] here. */
data class Misfit(val card: PlacedCard, val wrongPlace: Boolean, val to: StoragePlace?, val section: String?, val line: String)

/**
 * The copies in [place] its rules say don't belong: one it doesn't hold ("Doesn't belong here — belongs
 * in Blue box", or "— no place fits"), or one in the wrong section ("In Red — belongs in Blue"). A copy
 * whose card data isn't here ([cardOf] null) isn't judged.
 */
fun misfits(
    place: StoragePlace,
    collections: List<Collection>,
    cardOf: (PlacedCard) -> RuleCard?,
    toUsd: (Double) -> Double = { it },
    fmt: (Double) -> String = ::plainDollars,
    rate: Double = 1.0
): List<Misfit> {
    val places = placesOf(collections)
    val order = placeOrderOf(collections)
    val r = placeRules(place)
    val out = mutableListOf<Misfit>()
    for (c in cardsIn(collections, place.id)) {
        val card = cardOf(c) ?: continue
        if (!fitsPlace(place, card, toUsd, fmt, rate)) {
            val to = placeFor(places.filter { it.id != place.id }, order, card, toUsd = toUsd, fmt = fmt, rate = rate)
            out += Misfit(c, true, to, null, if (to != null) "Doesn't belong here — belongs in ${to.name}" else "Doesn't belong here — no place fits")
            continue
        }
        if (place.placeKind == PlaceKind.BINDER || r.sectionsBy == null) continue
        val want = ruleSectionFor(place, card.facts, fmt, rate) ?: continue
        val now = c.line.section
        if (now != null && now.equals(want, ignoreCase = true)) continue
        out += Misfit(c, false, null, want, if (now != null) "In $now — belongs in $want" else "No section — belongs in $want")
    }
    return out
}

/** [collections] with a misfit's copies moved where they belong: [spot] (in its place, from ruleSpot), or the right section here. */
fun moveMisfit(collections: List<Collection>, m: Misfit, spot: Spot?): List<Collection> {
    val to = spot ?: if (m.section != null) Spot(m.card.line.placeId, section = m.section) else return collections
    return collections.map { c ->
        if (c.id != m.card.collectionId) c
        else c.copy(entries = c.entries.map { e -> if (e.scryfallId == m.card.entry.scryfallId) moveCopies(e, m.card.line, to, m.card.line.qty).first else e })
    }
}

/**
 * Whether a line of copies fits its place's rules: the place holds it, and (with sections by a level)
 * it's in the section they give it. True in a place with no rules; null when the card's data isn't
 * here — the Storage filters' "Doesn't fit its place".
 */
fun copyFitsItsPlace(places: Map<String, StoragePlace>, copy: PlacedCard, card: RuleCard?, toUsd: (Double) -> Double = { it }): Boolean? {
    val place = places[copy.line.placeId] ?: return true
    if (!hasRule(place)) return true
    if (card == null) return null
    if (!fitsPlace(place, card, toUsd)) return false
    if (place.placeKind == PlaceKind.BINDER || placeRules(place).sectionsBy == null) return true
    val want = ruleSectionFor(place, card.facts) ?: return true
    return want.equals(copy.line.section ?: "", ignoreCase = true)
}

// ---- Places whose rules overlap ----

/** Another place that also takes cards [draft] holds: how many of the cards looked at fit both, and whether it comes first. */
data class Overlap(val place: StoragePlace, val cards: Int, val comesFirst: Boolean) {
    /** "Also fits Red box (Red box comes first)". */
    val line: String get() = "Also fits ${place.name} (" + (if (comesFirst) "${place.name} comes first" else "this place comes first") + ")"
}

/**
 * The places with rules that also take cards [draft] would hold, judged on [cards] (the collection's,
 * as far as their data is here): each with how many fit both and which comes first in rule order. A
 * place holding everything only counts when it comes first — then it takes them all.
 */
fun overlaps(draft: StoragePlace, places: List<StoragePlace>, order: List<String>, cards: List<RuleCard>, toUsd: (Double) -> Double = { it }): List<Overlap> {
    if (holdsOf(draft) == null) return emptyList()
    val all = places.filter { it.id != draft.id } + draft
    val ranked = rulePlaces(all, order)
    val mine = cards.filter { holdsMatch(holdsOf(draft), it, toUsd) }
    if (mine.isEmpty()) return emptyList()
    val at = ranked.indexOfFirst { it.id == draft.id }
    val out = mutableListOf<Overlap>()
    ranked.forEachIndexed { i, p ->
        if (p.id == draft.id) return@forEachIndexed
        val first = at < 0 || i < at
        val h = holdsOf(p)
        if (h == null && !first) return@forEachIndexed
        val n = mine.count { holdsMatch(h, it, toUsd) }
        if (n > 0) out += Overlap(p, n, first)
    }
    return out
}

// ---- In words ----

private val COLOUR_WORDS = mapOf("W" to "white", "U" to "blue", "B" to "black", "R" to "red", "G" to "green", "C" to "colourless")

private fun andList(words: List<String>, join: String = "and"): String = when (words.size) {
    0 -> ""
    1 -> words[0]
    else -> words.dropLast(1).joinToString(", ") + " $join " + words.last()
}

private fun colourWords(letters: List<String>) = ADVANCED_COLORS.filter { it in letters }.mapNotNull { COLOUR_WORDS[it] }

/**
 * What a "Holds" filter takes, in a few words: "blue cards", "blue, mono-colour", "creatures, rare or
 * mythic", "tagged “trade”". [binderName] and [placeName] name the Your copies filters' binder and place.
 */
fun holdsSummary(
    basic: CollectionFilter,
    a: AdvancedFilter,
    binderName: (String) -> String? = { null },
    placeName: (String) -> String? = { null },
    fmt: (Double) -> String = ::plainDollars
): String {
    val parts = mutableListOf<String>()
    var colourOnly = true
    val type = basic.type.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ").lowercase()
    if (type.isNotEmpty()) { parts += if (type.endsWith("s")) type else "${type}s"; colourOnly = false }
    if (basic.colors.isNotEmpty()) parts.add(0, andList(colourWords(basic.colors.map { it.toString() })))
    if (a.pickedColors.isNotEmpty() || "C" in a.colors) {
        val words = colourWords(a.pickedColors)
        parts.add(0, when {
            a.colorless -> "colourless"
            a.colorMode == "exactly" && words.size == 1 -> { colourOnly = false; "${words[0]}, mono-colour" }
            a.colorMode == "exactly" -> { colourOnly = false; "exactly ${andList(words)}" }
            a.colorMode == "atMost" && words.size == 1 -> "${words[0]} or colourless"
            a.colorMode == "atMost" -> { colourOnly = false; "within ${andList(words)}" }
            else -> andList(words)
        })
    }
    if (a.multicolor) { parts += "multicolour"; colourOnly = false }
    if (basic.text.isNotBlank()) { parts += "with “${basic.text.trim()}”"; colourOnly = false }
    val rarities = COLLECTION_FILTER_RARITIES.filter { it in basic.rarities }
    if (rarities.isNotEmpty()) { parts += andList(rarities, "or"); colourOnly = false }
    if (basic.untagged) { parts += UNTAGGED.lowercase(); colourOnly = false }
    if (basic.userTags.isNotEmpty()) { parts += "tagged " + andList(basic.userTags.sorted().map { "“$it”" }); colourOnly = false }
    // The rest as their chips say them.
    val skip = setOf("type", "text", "color", "rarity", "untagged", "usertag", "colors", "multicolor")
    filterChips(CollectionFilter(), a, binderName, fmt, placeName).filter { it.key.substringBefore(':') !in skip }.forEach {
        parts += it.label + if (it.symbols.isNotEmpty()) " " + it.symbols.joinToString("") { s -> "{$s}" } else ""
        colourOnly = false
    }
    if (parts.isEmpty()) return "everything"
    val line = parts.joinToString(", ")
    return if (colourOnly && type.isEmpty()) "$line cards" else line
}

/** A level as a place's line says it: "by colour", "by card type", "by letter". */
fun sectionsWords(level: SplitLevel): String = when (level.by) {
    "NAME" -> "by letter"
    else -> "by " + lowerWord(LEVEL_LABELS[level.by] ?: level.by)
}

/** An order as a place's line says it: "A–Z", "by mana value, then A–Z". */
fun sortWords(sortBy: List<String>): String? {
    if (sortBy.isEmpty()) return null
    if (sortBy == listOf("SET", "NUMBER")) return "by set, then number"
    return sortBy.map { SORT_BY_SHORT[it] ?: it }.joinToString(", then ")
}

/**
 * A place's rules in a line: "Holds blue cards · by card type · A–Z". A binder's says its order
 * ("in order by set, then number"). Empty when it has none.
 */
fun rulesLine(
    place: StoragePlace,
    binderName: (String) -> String? = { null },
    placeName: (String) -> String? = { null },
    fmt: (Double) -> String = ::plainDollars
): String {
    val r = placeRules(place)
    val parts = mutableListOf<String>()
    r.holds?.let { (b, a) -> parts += "Holds " + holdsSummary(b, a, binderName, placeName, fmt) }
    if (place.placeKind == PlaceKind.BINDER) {
        place.rule?.let { parts += "in order ${it.short}" }
    } else {
        r.sectionsBy?.let { parts += sectionsWords(it) }
        sortWords(r.sortBy)?.let { parts += it }
    }
    return parts.joinToString(" · ")
}

// ---- Sync: an older app's save ----

/**
 * [theirs] with the rules [source] has put back where [theirs] was saved by an app that doesn't know
 * about rules: its places with no "sortBy" while [source]'s had one get their "holds" back, and their
 * sections' level and order too unless that app changed the place's "sortRule" (then the old rule it
 * picked is what counts); and the pile's rule order when it has none. The same object when nothing changes.
 */
fun keepPlaceRules(source: Collection, theirs: Collection): Collection {
    val mine = source.storagePlaces?.associateBy { it.id }
    var out = theirs
    val theirPlaces = theirs.storagePlaces
    if (mine != null && theirPlaces != null) {
        fun lost(p: StoragePlace): Boolean {
            val m = mine[p.id] ?: return false
            return p.sortBy == null && p.sectionsBy == null && p.holds == null && (m.sortBy != null || m.sectionsBy != null || m.holds != null)
        }
        if (theirPlaces.any(::lost)) out = out.copy(storagePlaces = theirPlaces.map { p ->
            if (!lost(p)) p else {
                val m = mine.getValue(p.id)
                if ((p.sortRule ?: "") == (m.sortRule ?: "")) p.copy(holds = m.holds, sectionsBy = m.sectionsBy, sortBy = m.sortBy)
                // That app picked another old rule: it's what the place goes by now (placeRules reads it), with what it holds kept.
                else p.copy(holds = m.holds)
            }
        })
    }
    if (theirs.isUnsorted && theirs.placeOrder == null && source.placeOrder != null) out = out.copy(placeOrder = source.placeOrder)
    return out
}
