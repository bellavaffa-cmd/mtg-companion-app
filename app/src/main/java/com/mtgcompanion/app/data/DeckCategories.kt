package com.mtgcompanion.app.data

import kotlin.math.floor

// A deck's own categories: the user's groups for its cards — "Ramp", "Removal", "Win cons" — each
// card in as many as it fits (DeckCardEntry.categories), with an optional target per category
// ("Ramp 10/12", Deck.categoryTargets). Also the other ways the Cards list can be grouped — by
// mana value, colour or role tag — and "Suggest categories", which fills them in from the cards'
// Scryfall Tagger role tags (RoleTags.kt). Pure, so it can be tested; the web app's
// src/decks/categories.ts works the same way.

/** The longest category name kept. */
const val MAX_CATEGORY = 40

/** Offered when picking a card's categories, beside the deck's own. */
val COMMON_CATEGORIES = listOf("Ramp", "Draw", "Removal", "Board wipes", "Counterspells", "Tutors", "Protection", "Recursion", "Win cons")

/** What the Cards list can be grouped by. */
enum class DeckGrouping(val label: String) {
    TYPE("Type"), CATEGORY("Category"), MANA_VALUE("Mana value"), COLOUR("Colour"), ROLE("Role tag");

    companion object {
        fun fromName(name: String?): DeckGrouping = entries.firstOrNull { it.name == name } ?: TYPE
    }
}

/** The category a role tag suggests, by the tag's id (RoleTags.kt). */
val ROLE_CATEGORY: Map<String, String> = mapOf(
    "ramp" to "Ramp",
    "mana-rock" to "Ramp",
    "mana-dork" to "Ramp",
    "land-ramp" to "Ramp",
    "mana-engine" to "Ramp",
    "treasure" to "Ramp",
    "draw" to "Draw",
    "wheel" to "Draw",
    "removal" to "Removal",
    "board-wipe" to "Board wipes",
    "counterspell" to "Counterspells",
    "tutor" to "Tutors",
    "protection" to "Protection",
    "recursion" to "Recursion",
    "reanimate" to "Recursion",
    "sacrifice-outlet" to "Sacrifice outlets",
    "tokens" to "Tokens",
    "tax" to "Tax",
    "lifegain" to "Lifegain",
    "burn" to "Burn",
    "graveyard-hate" to "Graveyard hate",
    "extra-turn" to "Extra turns"
)

private fun catKey(name: String) = name.lowercase()
private val byCatName = Comparator<String> { a, b -> catKey(a).compareTo(catKey(b)) }

/** A category name as it's kept: spaces tidied, at most MAX_CATEGORY characters. */
fun tidyCategory(name: String): String =
    name.replace(Regex("\\s+"), " ").trim().take(MAX_CATEGORY).trim()

/** [names] tidied, blanks dropped, each once (the first spelling kept). */
private fun tidyAll(names: List<String>): List<String> {
    val out = mutableListOf<String>()
    names.map(::tidyCategory).forEach { n -> if (n.isNotEmpty() && out.none { catKey(it) == catKey(n) }) out += n }
    return out
}

/** The deck with its categories marked as known (see Deck.categoryTargets). */
private fun Deck.known(): Deck = if (categoryTargets != null) this else copy(categoryTargets = emptyMap())

/** This entry with [categories]; none leaves the key out. */
private fun DeckCardEntry.withEntryCategories(categories: List<String>): DeckCardEntry =
    copy(categories = categories.ifEmpty { null })

/** This deck with the main-deck card [scryfallId] in exactly [categories]. */
fun Deck.withCardCategories(scryfallId: String, categories: List<String>): Deck {
    val tidy = tidyAll(categories)
    return copy(cards = cards.map { if (it.scryfallId == scryfallId) it.withEntryCategories(tidy) else it }).known()
}

/** Every category the deck uses or has a target for, A–Z. */
fun deckCategoryNames(deck: Deck): List<String> =
    tidyAll(deck.cards.flatMap { it.categories.orEmpty() } + deck.categoryTargets.orEmpty().keys).sortedWith(byCatName)

/** How many of the main deck's cards (copies) are in each category, by its name as the deck spells it. */
fun categoryCounts(deck: Deck): Map<String, Int> {
    val counts = LinkedHashMap<String, Int>()
    deckCategoryNames(deck).forEach { counts[it] = 0 }
    deck.cards.forEach { c ->
        tidyAll(c.categories.orEmpty()).forEach { cat ->
            val name = counts.keys.firstOrNull { catKey(it) == catKey(cat) }
            if (name != null) counts[name] = (counts[name] ?: 0) + c.quantity
        }
    }
    return counts
}

/** A category's target, looked up whatever its case. */
fun targetOf(deck: Deck, category: String): Int? =
    deck.categoryTargets.orEmpty().entries.firstOrNull { catKey(it.key) == catKey(category) }?.value

/** This deck with [category]'s target set to [target] (a whole number, 1 or more); null takes it off. */
fun Deck.withCategoryTarget(category: String, target: Int?): Deck {
    val name = tidyCategory(category)
    if (name.isEmpty()) return this
    val targets = LinkedHashMap(categoryTargets.orEmpty().filterKeys { catKey(it) != catKey(name) })
    if (target != null && target >= 1) targets[name] = target
    return copy(categoryTargets = targets)
}

/** The line for a category: "Ramp 10/12" with a target, "Ramp 10" without. */
fun categoryLine(name: String, count: Int, target: Int?): String =
    if (target != null) "$name $count/$target" else "$name $count"

/** This deck with category [from] called [to] on every card and in the targets (merging into [to] if it's there). */
fun Deck.renamedCategory(from: String, to: String): Deck {
    val next = tidyCategory(to)
    if (next.isEmpty()) return removedCategory(from)
    fun rename(list: List<String>) = tidyAll(list.map { if (catKey(it) == catKey(from)) next else it })
    val target = targetOf(this, from)
    var out = copy(cards = cards.map { c -> c.categories?.let { c.withEntryCategories(rename(it)) } ?: c }).known()
    if (target != null) out = out.withCategoryTarget(from, null).withCategoryTarget(next, targetOf(out, next) ?: target)
    return out
}

/** This deck with category [name] taken off every card, and its target gone. */
fun Deck.removedCategory(name: String): Deck {
    val next = cards.map { c -> c.categories?.let { list -> c.withEntryCategories(list.filter { catKey(it) != catKey(name) }) } ?: c }
    return copy(cards = next).known().withCategoryTarget(name, null)
}

/**
 * The categories "Suggest categories" gives each main-deck card that has none yet, from its role tags
 * ([roleTagsOf]: a card's name → its tag ids), by scryfallId. Cards with no matching tag are left out.
 */
fun suggestedCategories(deck: Deck, roleTagsOf: (String) -> List<String>): Map<String, List<String>> {
    val out = LinkedHashMap<String, List<String>>()
    deck.cards.forEach { c ->
        if (c.categories.orEmpty().isNotEmpty()) return@forEach
        val cats = tidyAll(roleTagsOf(c.name).mapNotNull { ROLE_CATEGORY[it] })
        if (cats.isNotEmpty()) out[c.scryfallId] = cats
    }
    return out
}

/** This deck with the suggested categories filled in, and how many cards got some. */
fun Deck.withSuggestedCategories(roleTagsOf: (String) -> List<String>): Pair<Deck, Int> {
    val suggested = suggestedCategories(this, roleTagsOf)
    if (suggested.isEmpty()) return this to 0
    return copy(cards = cards.map { c -> suggested[c.scryfallId]?.let { c.withEntryCategories(it) } ?: c }).known() to suggested.size
}

// ---- Grouping the Cards list ----

/** What grouping needs to know of a card: its mana value, colours, whether it's a land, its role tags' labels. */
data class GroupingFacts(val cmc: Double?, val colors: List<String>?, val land: Boolean, val roles: List<String> = emptyList())

data class CardGroup(
    val key: String,
    val label: String,
    val cards: List<DeckCardEntry>,
    /** Copies in the group. */
    val count: Int,
    /** A category's target, when it has one. */
    val target: Int? = null
)

private val COLOUR_NAMES = mapOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green")
private val COLOUR_ORDER = listOf("W", "U", "B", "R", "G", "multi", "colourless", "land", "unknown")
private val COLOUR_GROUP_LABELS = mapOf("multi" to "Multicolour", "colourless" to "Colourless", "land" to "Lands", "unknown" to "Not known yet")

private fun cardGroup(key: String, label: String, cards: List<DeckCardEntry>, target: Int? = null) =
    CardGroup(key, label, cards.sortedWith { a, b -> catKey(a.name).compareTo(catKey(b.name)) }, cards.sumOf { it.quantity }, target)

/**
 * [cards] grouped [by] anything but type (the type groups are the list's own): by the deck's
 * categories (A–Z, each card in every one it's in, "No category" last, a category with a target shown
 * even when empty), by mana value ("0"…"7+", lands apart), by colour (White…Green, Multicolour,
 * Colourless, lands apart) or by role tag (most cards first, "No role tag" last). [facts] is what's
 * known of each card; one not known yet goes in "Not known yet".
 */
fun groupCards(
    cards: List<DeckCardEntry>,
    by: DeckGrouping,
    facts: (DeckCardEntry) -> GroupingFacts?,
    targets: Map<String, Int> = emptyMap()
): List<CardGroup> = when (by) {
    DeckGrouping.TYPE -> listOf(cardGroup("all", "Cards", cards))
    DeckGrouping.CATEGORY -> {
        val names = tidyAll(cards.flatMap { it.categories.orEmpty() } + targets.keys).sortedWith(byCatName)
        val groups = names.map { name ->
            val target = targets.entries.firstOrNull { catKey(it.key) == catKey(name) }?.value
            cardGroup("cat:${catKey(name)}", name, cards.filter { c -> c.categories.orEmpty().any { catKey(tidyCategory(it)) == catKey(name) } }, target)
        }.filter { it.cards.isNotEmpty() || it.target != null }
        val none = cards.filter { tidyAll(it.categories.orEmpty()).isEmpty() }
        if (none.isNotEmpty()) groups + cardGroup("cat:", "No category", none) else groups
    }
    DeckGrouping.MANA_VALUE -> {
        val buckets = LinkedHashMap<String, MutableList<DeckCardEntry>>()
        cards.forEach { c ->
            val f = facts(c)
            val k = when {
                f == null -> "unknown"
                f.land -> "land"
                else -> "mv" + floor(f.cmc ?: 0.0).toInt().coerceIn(0, 7)
            }
            buckets.getOrPut(k) { mutableListOf() } += c
        }
        val order = listOf("mv0", "mv1", "mv2", "mv3", "mv4", "mv5", "mv6", "mv7", "land", "unknown")
        fun label(k: String) = when (k) {
            "land" -> "Lands"
            "unknown" -> "Not known yet"
            "mv7" -> "7+ mana"
            else -> "${k.drop(2)} mana"
        }
        order.filter { it in buckets }.map { cardGroup(it, label(it), buckets.getValue(it)) }
    }
    DeckGrouping.COLOUR -> {
        val buckets = LinkedHashMap<String, MutableList<DeckCardEntry>>()
        cards.forEach { c ->
            val f = facts(c)
            val colours = f?.colors.orEmpty().filter { it in COLOUR_NAMES }
            val k = when {
                f == null -> "unknown"
                f.land -> "land"
                colours.isEmpty() -> "colourless"
                colours.size > 1 -> "multi"
                else -> colours.first()
            }
            buckets.getOrPut(k) { mutableListOf() } += c
        }
        COLOUR_ORDER.filter { it in buckets }.map { cardGroup(it, COLOUR_NAMES[it] ?: COLOUR_GROUP_LABELS.getValue(it), buckets.getValue(it)) }
    }
    DeckGrouping.ROLE -> {
        val buckets = LinkedHashMap<String, MutableList<DeckCardEntry>>()
        val none = mutableListOf<DeckCardEntry>()
        cards.forEach { c ->
            val roles = facts(c)?.roles.orEmpty().distinct()
            if (roles.isEmpty()) none += c
            roles.forEach { r -> buckets.getOrPut(r) { mutableListOf() } += c }
        }
        val groups = buckets.map { (r, list) -> cardGroup("role:${catKey(r)}", r, list) }
            .sortedWith(compareByDescending<CardGroup> { it.count }.thenComparator { a, b -> catKey(a.label).compareTo(catKey(b.label)) })
        if (none.isNotEmpty()) groups + cardGroup("role:", "No role tag", none) else groups
    }
}
