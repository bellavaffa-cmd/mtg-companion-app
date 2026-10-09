package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.social.TradeMatch

/*
 * Sorting recipes: how a pile of cards splits, saved and reused. A recipe is a name, the smart piles
 * to "first, pull out" (cards a deck needs, cards a collection goal is missing, cards a friend wants,
 * cards new for a binder, copies past a playset to trade), up to three levels that split the rest (value bands, colour, colour identity,
 * set, mana value, rarity, card type, A–Z ranges, collector number) and what to "also keep apart"
 * (foils, not English, played). The piles follow from the recipe, always the same way: the smart
 * piles first, then the keep-apart piles, then every combination of the levels — at most
 * MAX_RECIPE_PILES, the rest sharing an "Everything else" pile. Each card scanned goes in one pile:
 * a smart pile when one applies (decks before goals before friends before binders before trade), else
 * a keep-apart pile, else the levels' pile.
 *
 * Where recipes are kept: the Unsorted pile's "sortRecipes" (Collection.sortRecipes), so they sync
 * like the storage places and gear. Two devices' recipes merge recipe by recipe (mergeRecipes): one
 * added on either is kept, one deleted on either stays deleted, each field goes to whoever changed it.
 * A pile saved by an app from before recipes comes without the key and keeps this device's
 * (keepRecipesFromOlderApp); so does a recipe saved by an app from before the Goals need pile, which
 * drops that pile and the recipe's "goals" key (see SortRecipe.goals).
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/sortRecipes.ts rule for rule; both
 * run the same test vectors (app/src/test/resources/sortRecipeVectors.json ↔ tests/collection/sortRecipeVectors.json).
 */

// ---- What a recipe is ----

/**
 * The smart piles, in priority order. A deck's need comes first (a deck is played; its pull list waits
 * for the card), then a goal's (the user's own target, which a copy for a deck still counts towards
 * once it's in the collection), then a friend's want (one copy, for a trade that may not happen), then
 * a binder's gap and a copy past a playset (both just tidying).
 */
val SMART_KINDS = listOf("DECKS", "GOALS", "FRIENDS", "BINDER", "TRADE")

/** The switches under "First, pull out". */
val SMART_LABELS = mapOf(
    "DECKS" to "Cards my decks need",
    "GOALS" to "Cards my goals need",
    "FRIENDS" to "Cards friends want",
    "BINDER" to "New for a binder (fills a gap)",
    "TRADE" to "More than a playset · to trade"
)

/** The smart piles' names on the table. */
val SMART_PILE_NAMES = mapOf("DECKS" to "Decks need", "GOALS" to "Goals need", "FRIENDS" to "Friends want", "BINDER" to "Binder gaps", "TRADE" to "To trade")

val APART_KINDS = listOf("FOIL", "FOREIGN", "PLAYED")
val APART_LABELS = mapOf("FOIL" to "Foils", "FOREIGN" to "Not English", "PLAYED" to "Played")

/** In the editor's order: the common ways first. Stored by name, so the order is free to change. */
val LEVEL_BYS = listOf("VALUE", "COLOUR", "TYPE", "SET", "MANA_VALUE", "RARITY", "IDENTITY", "NAME", "NUMBER")
val LEVEL_LABELS = mapOf(
    "VALUE" to "Value", "COLOUR" to "Colour", "IDENTITY" to "Colour identity", "SET" to "Set", "MANA_VALUE" to "Mana value",
    "RARITY" to "Rarity", "TYPE" to "Card type", "NAME" to "A–Z", "NUMBER" to "Collector number"
)

/**
 * One way of splitting ([by], a LEVEL_BYS name). [cuts]: VALUE — the band edges in the user's
 * currency, highest first ("$20+", "$5–20"…); MANA_VALUE and NUMBER — where each bucket starts, lowest
 * first. [letters]: NAME — where each A–Z range starts. [sets]: SET — the sets with a pile each
 * (lowercase codes), the rest "Other sets". [lands]: COLOUR — lands get a pile of their own (else they
 * go with colourless). [restOn]: VALUE — only the top bands are piles of their own; the cheapest band
 * goes on to the next level. The web app's SplitLevel, field for field.
 */
data class SplitLevel(
    val by: String,
    val cuts: List<Double>? = null,
    val letters: List<String>? = null,
    val sets: List<String>? = null,
    val lands: Boolean? = null,
    val restOn: Boolean? = null
)

/** Where one pile is filed: a place's id, BY_RULE ("the box whose rule fits"), or "" — no place (Unsorted). */
data class PileGoTo(val pile: String, val to: String = "")

/**
 * A recipe; [pullOut] are SMART_KINDS names, [apart] APART_KINDS names. [goals]: whether [pullOut] has
 * the Goals need pile ("GOALS"), as an app that knows that pile writes it — always, on or off. An app
 * from before it drops "GOALS" from [pullOut] and leaves this key out, so a recipe without it was
 * saved by such an app and keeps this device's Goals need pile (keepRecipesFromOlderApp). The web
 * app's SortRecipe, field for field.
 */
data class SortRecipe(
    val id: String,
    val name: String,
    val pullOut: List<String> = emptyList(),
    val levels: List<SplitLevel> = emptyList(),
    val apart: List<String> = emptyList(),
    val goTo: List<PileGoTo>? = null,
    val createdAt: Long = 0L,
    val goals: Boolean? = null
)

const val MAX_LEVELS = 3
const val MAX_RECIPE_PILES = 24
/** Copies a playset has: the copy after it is one to trade. */
const val PLAYSET = 4

private val DEFAULT_CUTS = mapOf("VALUE" to listOf(2.0), "MANA_VALUE" to listOf(0.0, 2.0, 3.0, 4.0, 5.0), "NUMBER" to listOf(1.0, 100.0, 200.0, 300.0))
private val DEFAULT_LETTERS = listOf("A", "F", "L", "R")

/** A level as both apps keep it: only the fields its kind uses, tidied (sorted, no repeats). */
fun splitLevel(l: SplitLevel): SplitLevel {
    val by = if (l.by in LEVEL_BYS) l.by else "COLOUR"
    return when (by) {
        "VALUE" -> {
            val cuts = l.cuts.orEmpty().filter { it.isFinite() && it > 0 }.map { Math.round(it * 100) / 100.0 }.distinct().sortedDescending()
            SplitLevel(by, cuts = cuts.ifEmpty { DEFAULT_CUTS.getValue("VALUE") }, restOn = if (l.restOn == true) true else null)
        }
        "MANA_VALUE", "NUMBER" -> {
            val low = if (by == "MANA_VALUE") 0.0 else 1.0
            val given = l.cuts.orEmpty().filter { it.isFinite() && it >= low }.map { Math.floor(it) }
            SplitLevel(by, cuts = (listOf(low) + given.ifEmpty { DEFAULT_CUTS.getValue(by) }).distinct().sorted())
        }
        "NAME" -> {
            val given = l.letters.orEmpty().map { it.trim().take(1).uppercase() }.filter { it.length == 1 && it[0] in 'A'..'Z' }
            SplitLevel(by, letters = (listOf("A") + given.ifEmpty { DEFAULT_LETTERS }).distinct().sorted())
        }
        "SET" -> SplitLevel(by, sets = l.sets.orEmpty().map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct())
        "COLOUR" -> SplitLevel(by, lands = if (l.lands == true) true else null)
        else -> SplitLevel(by)
    }
}

/** A recipe as both apps write it: known kinds only, in their fixed order, at most MAX_LEVELS levels, and [SortRecipe.goals] said. */
fun sortRecipe(r: SortRecipe): SortRecipe {
    // One line per pile, the last said winning.
    val goTo = mutableListOf<PileGoTo>()
    for (g in r.goTo.orEmpty()) {
        if (g.pile.isEmpty()) continue
        val line = PileGoTo(g.pile, g.to)
        val at = goTo.indexOfFirst { it.pile == g.pile }
        if (at >= 0) goTo[at] = line else goTo += line
    }
    val pullOut = SMART_KINDS.filter { it in r.pullOut }
    return SortRecipe(
        id = r.id,
        name = r.name.trim().ifEmpty { "My recipe" },
        pullOut = pullOut,
        levels = r.levels.take(MAX_LEVELS).map { splitLevel(it) },
        apart = APART_KINDS.filter { it in r.apart },
        goTo = goTo.ifEmpty { null },
        createdAt = r.createdAt,
        goals = "GOALS" in pullOut
    )
}

// ---- Templates ----

/**
 * "Start from": the ready-made recipes. [sets]: the sets the user's binders sorted by set hold, for
 * Binder by set. [goals]: the user has a goal under way — then "What my collection needs" pulls out
 * the cards the goals need too (without one, that pile would only stand empty on the table).
 */
fun recipeTemplates(sets: List<String> = emptyList(), goals: Boolean = false): List<SortRecipe> {
    val smart = listOf("DECKS", "FRIENDS", "BINDER")
    val needs = if (goals) listOf("DECKS", "GOALS", "FRIENDS", "BINDER", "TRADE") else listOf("DECKS", "FRIENDS", "BINDER", "TRADE")
    return listOf(
        SortRecipe("tpl-colour", "Commander by colour", smart, listOf(SplitLevel("COLOUR", lands = true))),
        SortRecipe("tpl-type", "By card type", smart, listOf(SplitLevel("TYPE"))),
        SortRecipe("tpl-set", "Binder by set", smart, listOf(splitLevel(SplitLevel("SET", sets = sets.take(5))), splitLevel(SplitLevel("NUMBER")))),
        SortRecipe("tpl-value", "Rares by value", smart, listOf(SplitLevel("VALUE", cuts = listOf(20.0, 5.0, 1.0)))),
        SortRecipe("tpl-needs", "What my collection needs", needs)
    )
}

/**
 * By card type's line. A card with two types goes in the first pile of TYPE_SECTIONS it fits
 * (typeSection): an artifact creature with the creatures, an artifact land with the artifacts.
 */
const val TYPE_TEMPLATE_LINE = "Creatures · Instants · Sorceries · Artifacts · Enchantments · Lands — an artifact creature goes with Creatures"

/** What "Make your own recipe" starts with. */
fun newRecipe(id: String, now: Long): SortRecipe = SortRecipe(
    id, "My recipe", listOf("DECKS", "FRIENDS", "BINDER"),
    listOf(SplitLevel("VALUE", cuts = listOf(2.0), restOn = true), SplitLevel("COLOUR")), listOf("FOIL"), createdAt = now
)

val SortRecipe.isTemplate: Boolean get() = id.startsWith("tpl-")

// ---- The piles ----

/** One bucket of a level: its key in a pile's key, its words, and a colour for its band (null: none of its own). */
data class RecipeBucket(val key: String, val label: String, val band: String?)

private val RECIPE_COLOUR_BUCKETS = listOf(
    Triple("W", "White", "#f3efe0"), Triple("U", "Blue", "#4d8fe0"), Triple("B", "Black", "#6f6a78"), Triple("R", "Red", "#e0674d"),
    Triple("G", "Green", "#5fbf7a"), Triple("M", "Multicolour", "#d8b56a"), Triple("C", "Colourless", "#a7a8b3"), Triple("L", "Lands", "#b08b5a")
)
private val RECIPE_RARITY_BUCKETS = listOf(
    Triple("mythic", "Mythic", "#e2694a"), Triple("rare", "Rare", "#e6b45e"), Triple("uncommon", "Uncommon", "#c0c6d0"), Triple("common", "Common", "#6f6a78")
)
private const val VALUE_BAND = "#b98cf0"
/** The smart piles' band: gold, as the app's accent. */
const val SMART_BAND = "#e6b45e"
val APART_BANDS = mapOf("FOIL" to "#9fd6ff", "FOREIGN" to "#f07fa8", "PLAYED" to "#c9a27a")
/** Bands for piles with no colour of their own, by pile number. */
val RECIPE_PILE_COLOURS = listOf("#e6b45e", "#e2694a", "#5bcb8f", "#6aa8f0", "#c58af0", "#f07fa8")

/** A whole number without its ".0": the bucket labels count piles and pages. */
private fun whole(n: Double): String = if (n == Math.floor(n) && !n.isInfinite()) n.toLong().toString() else n.toString()

/** A level's buckets, in order. [fmt] writes an amount in the user's currency. */
fun levelBuckets(level: SplitLevel, fmt: (Double) -> String): List<RecipeBucket> {
    val l = splitLevel(level)
    return when (l.by) {
        "VALUE" -> {
            val cuts = l.cuts!!
            fun top(i: Int) = if (i == 0) (if (l.restOn == true) "${fmt(cuts[0])} and up" else "${fmt(cuts[0])}+") else "${fmt(cuts[i])}–${fmt(cuts[i - 1])}"
            cuts.indices.map { RecipeBucket("v$it", top(it), VALUE_BAND) } + RecipeBucket("v${cuts.size}", "under ${fmt(cuts.last())}", VALUE_BAND)
        }
        "COLOUR" -> RECIPE_COLOUR_BUCKETS.filter { it.first != "L" || l.lands == true }
            .map { (key, label, band) -> RecipeBucket(key, if (key == "C" && l.lands != true) "Colourless and lands" else label, band) }
        "IDENTITY" -> RECIPE_COLOUR_BUCKETS.filter { it.first != "L" }.map { (key, label, band) -> RecipeBucket(key, label, band) }
        "SET" -> l.sets!!.map { RecipeBucket(it, it.uppercase(), null) } + RecipeBucket("other", "Other sets", null)
        "MANA_VALUE", "NUMBER" -> {
            val cuts = l.cuts!!
            val p = if (l.by == "MANA_VALUE") "MV " else "#"
            val k = if (l.by == "MANA_VALUE") "m" else "c"
            cuts.mapIndexed { i, c ->
                val next = cuts.getOrNull(i + 1)
                val label = when {
                    next == null -> "$p${whole(c)}+"
                    next - 1 == c -> "$p${whole(c)}"
                    else -> "$p${whole(c)}–${whole(next - 1)}"
                }
                RecipeBucket("$k$i", label, null)
            }
        }
        "RARITY" -> RECIPE_RARITY_BUCKETS.map { (key, label, band) -> RecipeBucket(key, label, band) }
        "TYPE" -> TYPE_SECTIONS.map { RecipeBucket(it.lowercase(), it, null) }
        else -> {
            val letters = l.letters!!
            letters.mapIndexed { i, c ->
                val end = if (i + 1 < letters.size) (letters[i + 1][0] - 1).toString() else "Z"
                RecipeBucket("n$i", if (end == c) c else "$c–$end", null)
            }
        }
    }
}

/** A pile on the table: its number (from 1, left to right), its key, its name, its kind (SMART, APART, LEVEL) and its band's colour. */
data class RecipePile(val number: Int, val key: String, val name: String, val kind: String, val band: String)

/** The piles a recipe makes; [wanted]: how many it would make uncapped ([capped]: more than MAX_RECIPE_PILES). */
data class DerivedPiles(val piles: List<RecipePile>, val wanted: Int, val capped: Boolean)

/** The pile the levels lead to when a recipe has none. */
const val ALL_KEY = "L:all"
/** The pile the levels' piles past the cap share. */
const val REST_KEY = "L:rest"

private data class Combo(val keys: List<String>, val labels: List<String>, val band: String?)

private fun combos(levels: List<SplitLevel>, fmt: (Double) -> String): List<Combo> {
    if (levels.isEmpty()) return listOf(Combo(emptyList(), emptyList(), null))
    val first = levels[0]
    val rest = levels.drop(1)
    val buckets = levelBuckets(first, fmt)
    val after = combos(rest, fmt)
    val out = mutableListOf<Combo>()
    buckets.forEachIndexed { i, b ->
        if (first.by == "VALUE" && first.restOn == true && i < buckets.size - 1) {
            out += Combo(listOf(b.key), listOf(b.label), b.band)
            return@forEachIndexed
        }
        // The cheap band going on to the next level is just that level's piles ("White", not "under $2 · White").
        val quiet = first.by == "VALUE" && first.restOn == true && rest.isNotEmpty()
        for (c in after) out += Combo(listOf(b.key) + c.keys, if (quiet) c.labels else listOf(b.label) + c.labels, if (quiet) c.band ?: b.band else b.band ?: c.band)
    }
    return out
}

private data class PileDraft(val key: String, val name: String, val kind: String, val band: String)

/** The piles [recipe] makes, in table order. [fmt] writes an amount in the user's currency. */
fun derivePiles(recipe: SortRecipe, fmt: (Double) -> String): DerivedPiles {
    val r = sortRecipe(recipe)
    val out = mutableListOf<PileDraft>()
    for (k in r.pullOut) out += PileDraft("S:$k", SMART_PILE_NAMES.getValue(k), "SMART", SMART_BAND)
    for (a in r.apart) out += PileDraft("A:$a", APART_LABELS.getValue(a), "APART", APART_BANDS.getValue(a))
    val levelPiles = if (r.levels.isEmpty()) listOf(PileDraft(ALL_KEY, "Bulk", "LEVEL", ""))
    else combos(r.levels, fmt).map { PileDraft("L:" + it.keys.joinToString("/"), it.labels.joinToString(" · "), "LEVEL", it.band ?: "") }
    val wanted = out.size + levelPiles.size
    val capped = wanted > MAX_RECIPE_PILES
    val kept = if (capped) levelPiles.take(maxOf(0, MAX_RECIPE_PILES - out.size - 1)) + PileDraft(REST_KEY, "Everything else", "LEVEL", "") else levelPiles
    val piles = (out + kept).mapIndexed { i, p -> RecipePile(i + 1, p.key, p.name, p.kind, p.band.ifEmpty { RECIPE_PILE_COLOURS[i % RECIPE_PILE_COLOURS.size] }) }
    return DerivedPiles(piles, wanted, capped)
}

/** The warning when a recipe makes more piles than fit: null when they fit. */
fun capWarning(d: DerivedPiles): String? =
    if (d.capped) "That makes ${d.wanted} piles — only $MAX_RECIPE_PILES fit, so the last ones share pile $MAX_RECIPE_PILES, “Everything else”." else null

/** A level in a few words, under its name in the recipe: "$2 and up apart, rest continue", "W · U · B · R · G · Multi · Colourless/lands". */
fun levelLine(level: SplitLevel, fmt: (Double) -> String): String {
    val l = splitLevel(level)
    if (l.by == "COLOUR") return if (l.lands == true) "W · U · B · R · G · Multi · Colourless · Lands" else "W · U · B · R · G · Multi · Colourless/lands"
    if (l.by == "IDENTITY") return "W · U · B · R · G · Multi · Colourless"
    val buckets = levelBuckets(l, fmt)
    if (l.by == "VALUE" && l.restOn == true) return buckets.dropLast(1).joinToString(" · ") { it.label } + " apart, rest continue"
    if (l.by == "SET" && l.sets!!.isEmpty()) return "One pile per set you name"
    return buckets.joinToString(" · ") { it.label }
}

/** A recipe in a line, under its name: "Value $2+ apart · then colour · 12 piles". */
fun recipeLine(recipe: SortRecipe, fmt: (Double) -> String): String {
    val r = sortRecipe(recipe)
    val parts = r.levels.mapIndexed { i, l ->
        val words = if (l.by == "VALUE" && l.restOn == true) "Value ${fmt(l.cuts!![0])}+ apart" else LEVEL_LABELS.getValue(l.by)
        if (i == 0) words else "then ${lowerWord(words)}"
    }.toMutableList()
    if (parts.isEmpty()) parts += if (r.pullOut.isNotEmpty()) r.pullOut.joinToString(" · ") { SMART_PILE_NAMES.getValue(it) } else "One pile"
    val n = derivePiles(r, fmt).piles.size
    return (parts + "$n ${if (n == 1) "pile" else "piles"}").joinToString(" · ")
}

/** A word's first letter lowercased, unless it's an abbreviation ("MV", "DSK", "A–Z"): "Blue" → "blue". */
fun lowerWord(s: String): String =
    if (s.length >= 2 && s[0].isUpperCase() && s[1].isLowerCase()) s[0].lowercase() + s.substring(1) else s

// ---- Which pile a card goes in ----

/** What a recipe needs to know of a scanned card. Prices are in US dollars; [lang] as Scryfall codes it ("en", "ja"…). */
data class RecipeCard(
    val name: String,
    /** The printing (what a set goal goes by); null when not known. */
    val scryfallId: String? = null,
    val colors: List<String>? = null,
    val colorIdentity: List<String>? = null,
    val typeLine: String? = null,
    val set: String? = null,
    val collectorNumber: String? = null,
    val cmc: Double? = null,
    val rarity: String? = null,
    val usd: Double? = null,
    val usdFoil: Double? = null,
    val foil: Boolean = false,
    val lang: String? = null,
    val played: Boolean = false
)

/** The card's price in US dollars for its finish, or null when not known. */
fun cardPrice(c: RecipeCard): Double? = if (c.foil) c.usdFoil ?: c.usd else c.usd ?: c.usdFoil

private fun recipeFacts(c: RecipeCard) = CardFacts(c.name, c.colors.orEmpty(), c.typeLine, c.set, c.collectorNumber)
private fun leadingNumber(s: String?): Double = Regex("^\\d+").find((s ?: "").trim())?.value?.toDouble() ?: 0.0
private fun <T : Comparable<T>> lastAtOrBelow(starts: List<T>, v: T): Int {
    var at = 0
    starts.forEachIndexed { i, s -> if (s <= v) at = i }
    return at
}

/** The bucket [card] falls in, for [level]. [rate]: the user's currency per US dollar. */
fun bucketOf(level: SplitLevel, card: RecipeCard, rate: Double): String {
    val l = splitLevel(level)
    return when (l.by) {
        "VALUE" -> {
            val usd = cardPrice(card)
            val cuts = l.cuts!!
            if (usd == null) return "v${cuts.size}"
            val local = usd * rate
            val i = cuts.indexOfFirst { local >= it }
            "v${if (i < 0) cuts.size else i}"
        }
        "COLOUR" -> {
            val s = colourSection(recipeFacts(card))
            val k = RECIPE_COLOUR_BUCKETS.firstOrNull { it.second == s }?.first ?: "C"
            if (k == "L" && l.lands != true) "C" else k
        }
        "IDENTITY" -> {
            val id = card.colorIdentity.orEmpty().filter { it.length == 1 && it in listOf("W", "U", "B", "R", "G") }
            when {
                id.size > 1 -> "M"
                id.size == 1 -> id[0]
                else -> "C"
            }
        }
        "SET" -> {
            val s = (card.set ?: "").lowercase()
            if (s in l.sets!!) s else "other"
        }
        "MANA_VALUE" -> "m${lastAtOrBelow(l.cuts!!, Math.floor(card.cmc ?: 0.0))}"
        "NUMBER" -> "c${lastAtOrBelow(l.cuts!!, leadingNumber(card.collectorNumber))}"
        "RARITY" -> if (card.rarity in listOf("mythic", "rare", "uncommon")) card.rarity!! else "common"
        "TYPE" -> typeSection(recipeFacts(card)).lowercase()
        else -> {
            val letter = letterOf(card.name)
            "n${if (letter == "#") 0 else lastAtOrBelow(l.letters!!, letter)}"
        }
    }
}

/**
 * Why a card goes in a smart pile ([kind], a SMART_KINDS name): DECKS — [deckId] and [deck] need it;
 * GOALS — goal [goalId] ([goal]) is missing it, and has [have] of its [need] copies with it (and the
 * session's) in; it's filed into binder [placeId] when the goal has one (its set's binder);
 * FRIENDS — [friend] (user [friendId]) wants it; BINDER — it's new for binder [placeId] ([binder]), at [page] and [slot];
 * TRADE — it's [copy] copies owned. The web app's SortReason, field for field.
 */
data class SortReason(
    val kind: String,
    val deckId: String? = null,
    val deck: String? = null,
    val friend: String? = null,
    val friendId: String? = null,
    val placeId: String? = null,
    val binder: String? = null,
    val page: Int? = null,
    val slot: Int? = null,
    val copy: Int? = null,
    val goalId: String? = null,
    val goal: String? = null,
    val have: Int? = null,
    val need: Int? = null
)

/** The pile a card goes in, why (a smart pile's reason), and the other smart reasons that applied. */
data class RecipeChoice(val pile: Int, val key: String, val reason: SortReason?, val also: List<SortReason>)

/** The key of the levels' pile [card] goes in (before the cap). */
fun levelKey(recipe: SortRecipe, card: RecipeCard, rate: Double): String {
    val r = sortRecipe(recipe)
    if (r.levels.isEmpty()) return ALL_KEY
    val keys = mutableListOf<String>()
    for (l in r.levels) {
        val k = bucketOf(l, card, rate)
        keys += k
        if (l.by == "VALUE" && l.restOn == true && k != "v${l.cuts!!.size}") break
    }
    return "L:" + keys.joinToString("/")
}

/** The keep-apart kinds [card] is: foil, not English, played. */
fun apartOf(card: RecipeCard): List<String> = listOfNotNull(
    if (card.foil) "FOIL" else null,
    if (!card.lang.isNullOrEmpty() && card.lang.lowercase() != "en") "FOREIGN" else null,
    if (card.played) "PLAYED" else null
)

/**
 * The pile [card] goes in: the first smart pile the recipe pulls out that one of [reasons] (as
 * reasonsFor finds them, in priority order) is for; else the first keep-apart pile it is; else its
 * levels' pile ("Everything else" past the cap). [rate]: the user's currency per US dollar.
 */
fun pileFor(recipe: SortRecipe, derived: DerivedPiles, card: RecipeCard, reasons: List<SortReason>, rate: Double): RecipeChoice {
    val r = sortRecipe(recipe)
    fun byKey(key: String) = derived.piles.firstOrNull { it.key == key }
    val reason = reasons.firstOrNull { it.kind in r.pullOut }
    if (reason != null) {
        val p = byKey("S:${reason.kind}")!!
        return RecipeChoice(p.number, p.key, reason, reasons.filter { it !== reason })
    }
    val apart = apartOf(card).firstOrNull { it in r.apart }
    if (apart != null) {
        val p = byKey("A:$apart")!!
        return RecipeChoice(p.number, p.key, null, reasons)
    }
    val p = byKey(levelKey(r, card, rate)) ?: byKey(REST_KEY) ?: derived.piles.last()
    return RecipeChoice(p.number, p.key, null, reasons)
}

// ---- What the collection wants ----

/** A deck that needs a card, and how many copies. */
data class DeckNeed(val deckId: String, val deck: String, val qty: Int)

/** A friend who wants a card: their user id and the name they go by. */
data class FriendWant(val id: String, val name: String)

/**
 * A binder kept in order ([rule], a SortRule name): its pockets in use with their cards' facts, the
 * names in it (lowercase, its loose copies too) and the sets it collects — a card of one of those sets
 * not in it yet fills a gap.
 */
data class OrderedBinder(
    val placeId: String,
    val name: String,
    val rule: String?,
    val pockets: Int,
    val occupied: List<Placed>,
    val names: List<String>,
    val sets: List<String>
)

/**
 * A collection goal under way, as the Goals need pile goes by it: [have] of its [need] copies there,
 * and the copies still [missing] of each card, by its card key (goalCardKey: a set goal's by printing,
 * the others' by name). [foil]: only foil copies count. [placeId]: where its cards are filed — a set
 * goal's set binder (a binder kept in order that holds that set's cards; one holding only that set
 * first), null for the Unsorted pile.
 */
data class GoalNeed(
    val goalId: String,
    val name: String,
    val kind: String,
    val foil: Boolean,
    val have: Int,
    val need: Int,
    val missing: Map<String, Int>,
    val placeId: String? = null
)

/**
 * What the smart piles go by: deck needs and friends' wants by card name (recipeNameKey), the binders
 * in order, copies owned by name, and the goals under way (most nearly done first).
 */
data class SmartContext(
    val deckNeeds: Map<String, List<DeckNeed>> = emptyMap(),
    val friendWants: Map<String, List<FriendWant>> = emptyMap(),
    val binders: List<OrderedBinder> = emptyList(),
    val owned: Map<String, Int> = emptyMap(),
    val goals: List<GoalNeed> = emptyList()
)

/** A card's name as the lookups key it: lowercase, its front face. */
fun recipeNameKey(name: String): String = name.trim().lowercase().split(" // ")[0].trim()

/** Each card the decks need, by name: missing from a deck (copies short), or on its Considering list (one). */
fun deckNeedsOf(collections: List<Collection>, decks: List<Deck>): Map<String, List<DeckNeed>> {
    val out = LinkedHashMap<String, MutableList<DeckNeed>>()
    fun want(name: String, d: Deck, qty: Int) {
        val list = out.getOrPut(recipeNameKey(name)) { mutableListOf() }
        val at = list.indexOfFirst { it.deckId == d.id }
        if (at >= 0) list[at] = list[at].copy(qty = list[at].qty + qty) else list += DeckNeed(d.id, d.name, qty)
    }
    for (d in decks) {
        if (d.isArchived || d.sample == true) continue
        for (m in missingCards(d, collections, decks)) want(m.entry.name, d, m.need)
        for (e in d.considering) want(e.name, d, 1)
    }
    return out
}

/**
 * The friends wanting each card, by name, from the trade matches (what their wishlists want of yours).
 * [nameOf]: a friend's name by their user id — null for someone who isn't a friend now, left out.
 */
fun friendWantsOf(matches: List<TradeMatch>, nameOf: (String) -> String?): Map<String, List<FriendWant>> {
    val out = LinkedHashMap<String, MutableList<FriendWant>>()
    for (m in matches) {
        val name = nameOf(m.friend) ?: continue
        for (w in m.theyWant) {
            val list = out.getOrPut(recipeNameKey(w.name)) { mutableListOf() }
            if (list.none { it.id == m.friend }) list += FriendWant(m.friend, name)
        }
    }
    return out
}

/**
 * The binders kept in order (with a sorting rule), each with its pockets in use, the names in it and
 * the sets it collects. [factsOf]: a printing's facts when its card data is here (null: not yet).
 */
fun orderedBinders(collections: List<Collection>, factsOf: (String) -> CardFacts?): List<OrderedBinder> {
    val out = mutableListOf<OrderedBinder>()
    for (place in placesOf(collections)) {
        if (place.placeKind != PlaceKind.BINDER || place.sortRule.isNullOrEmpty()) continue
        val cards = cardsIn(collections, place.id)
        val sets = cards.mapNotNull { factsOf(it.entry.scryfallId)?.set?.lowercase()?.takeIf { s -> s.isNotEmpty() } }.distinct().sorted()
        out += OrderedBinder(
            placeId = place.id,
            name = place.name,
            rule = place.sortRule,
            pockets = place.pockets,
            occupied = binderPockets(place, cards).map { p -> Placed(p.index, factsOf(p.cards[0].entry.scryfallId) ?: CardFacts(p.cards[0].entry.name)) },
            names = cards.map { recipeNameKey(it.entry.name) }.distinct(),
            sets = sets
        )
    }
    return out
}

/**
 * The goals under way that are missing something, most nearly done first (as the Goals screen lists
 * them), each with what it's missing and where its cards are filed ([binders]: orderedBinders).
 */
fun goalNeedsOf(goals: List<CollectionGoal>, collections: List<Collection>, decks: List<Deck>, binders: List<OrderedBinder> = emptyList()): List<GoalNeed> {
    val open = goals.filter { it.completedAt == null }
    val progress = open.associate { it.id to goalProgress(it, collections, decks) }
    return sortedGoals(open) { progress.getValue(it.id) }.first.mapNotNull { g ->
        val p = progress.getValue(g.id)
        val missing = LinkedHashMap<String, Int>()
        for (l in missingLines(p)) missing[l.key] = l.missing
        if (missing.isEmpty()) return@mapNotNull null
        GoalNeed(g.id, g.name, g.kind, g.isFoil, p.have, p.need, missing, goalBinder(g, binders))
    }
}

/** A set goal's set binder: a binder kept in order holding only that set's cards, else one holding some; null for other goals. */
fun goalBinder(goal: CollectionGoal, binders: List<OrderedBinder>): String? {
    val set = goal.setCode?.lowercase()?.takeIf { goal.kind == "SET" && it.isNotEmpty() } ?: return null
    return (binders.firstOrNull { it.sets == listOf(set) } ?: binders.firstOrNull { set in it.sets })?.placeId
}

/** Copies owned of each card, by name (SortPiles.kt's ownedCounts). */
fun ownedOf(collections: List<Collection>, decks: List<Deck>): Map<String, Int> = ownedCounts(collections, decks)

/** One card sorted this session. [entry]: the card as a new binder entry with no copies yet; [filed]: put away already ("Put in deck now"). */
data class RecipeScan(
    val id: Long,
    val scryfallId: String,
    val name: String,
    /** The set's name ("Dominaria Remastered"), for the card's line. */
    val setName: String? = null,
    val card: RecipeCard,
    val facts: CardFacts,
    val entry: CollectionEntry,
    val pile: Int,
    val key: String,
    val reason: SortReason? = null,
    val also: List<SortReason>? = null,
    val filed: Boolean? = null,
    /** When it was scanned (milliseconds). */
    val at: Long? = null
)

/** The card key [goal] knows [card] by, when the card is a copy it counts (a foil goal counts foil copies only); else null. */
private fun goalKeyOf(goal: GoalNeed, card: RecipeCard, scryfallId: String?): String? =
    if (goal.foil && !card.foil) null else goalCardKey(goal.kind, card.name, scryfallId)

/** Whether a card sorted this session goes into binder [placeId]: a gap it fills, or a goal's card filed there. */
private fun intoBinder(s: RecipeScan, placeId: String): Boolean {
    val r = s.reason ?: return false
    return (r.kind == "BINDER" || r.kind == "GOALS") && r.placeId == placeId
}

/**
 * The smart reasons that apply to one more [card], in priority order — a deck needs it, a goal is
 * missing it, a friend wants it, it's new for a binder, it's past a playset — given what this session
 * has already pulled out ([scans]): a deck's need goes down with each copy pulled for it, a goal's
 * with each copy of the card sorted (whatever its pile: they all go into the collection), a friend
 * wants one copy, a binder's gap is filled once, and every copy scanned counts as owned.
 */
fun reasonsFor(ctx: SmartContext, card: RecipeCard, scans: List<RecipeScan>): List<SortReason> {
    val k = recipeNameKey(card.name)
    val same = scans.filter { recipeNameKey(it.name) == k }
    val out = mutableListOf<SortReason>()
    for (need in ctx.deckNeeds[k].orEmpty()) {
        val taken = same.count { it.reason?.kind == "DECKS" && it.reason.deckId == need.deckId }
        if (need.qty > taken) out += SortReason("DECKS", deckId = need.deckId, deck = need.deck)
    }
    for (g in ctx.goals) {
        val key = goalKeyOf(g, card, card.scryfallId) ?: continue
        val missing = g.missing[key] ?: continue
        // The session's copies of each card the goal is missing, as many as it's missing.
        val coming = HashMap<String, Int>()
        for (s in scans) {
            val sk = goalKeyOf(g, s.card, s.card.scryfallId ?: s.scryfallId) ?: continue
            if (sk in g.missing) coming.merge(sk, 1, Int::plus)
        }
        if ((coming[key] ?: 0) >= missing) continue
        val have = g.have + g.missing.entries.sumOf { (mk, n) -> minOf(n, coming[mk] ?: 0) }
        out += SortReason("GOALS", goalId = g.goalId, goal = g.name, have = have + 1, need = g.need, placeId = g.placeId)
    }
    for (f in ctx.friendWants[k].orEmpty()) {
        if (same.none { it.reason?.kind == "FRIENDS" && it.reason.friendId == f.id }) out += SortReason("FRIENDS", friend = f.name, friendId = f.id)
    }
    val set = (card.set ?: "").lowercase()
    for (b in ctx.binders) {
        if (set.isEmpty() || set !in b.sets || k in b.names) continue
        if (same.any { intoBinder(it, b.placeId) }) continue
        val adds = scans.filter { intoBinder(it, b.placeId) }.map { it.facts }
        val plan = planFit(SortRule.fromName(b.rule), b.occupied, adds + recipeFacts(card), FitMode.KEEP)
        val put = plan.puts.firstOrNull { it.item == adds.size } ?: continue
        val (page, slot) = pocketAt(put.to, b.pockets)
        out += SortReason("BINDER", placeId = b.placeId, binder = b.name, page = page, slot = slot)
        break
    }
    if (!isBasicLand(card.name)) {
        val copy = (ctx.owned[k] ?: 0) + same.size + 1
        if (copy > PLAYSET) out += SortReason("TRADE", copy = copy)
    }
    return out
}

/** The pile for one more card of a session, and why: reasonsFor, then pileFor. */
fun sortCard(recipe: SortRecipe, derived: DerivedPiles, ctx: SmartContext, card: RecipeCard, scans: List<RecipeScan>, rate: Double): RecipeChoice =
    pileFor(recipe, derived, card, reasonsFor(ctx, card, scans), rate)

/**
 * "Send to pile N instead": the next smart pile that wants the card (another of the reasons it had, of
 * a different kind) — or, when none, the pile it would go in without a smart pile. Null when that's
 * the pile it's in already.
 */
fun otherPile(recipe: SortRecipe, derived: DerivedPiles, card: RecipeCard, pile: Int, reason: SortReason?, also: List<SortReason>?, rate: Double): RecipeChoice? {
    val r = sortRecipe(recipe)
    val reasons = listOfNotNull(reason) + also.orEmpty()
    val next = reasons.firstOrNull { x ->
        x.kind != reason?.kind && x.kind in r.pullOut && (reason == null || SMART_KINDS.indexOf(x.kind) > SMART_KINDS.indexOf(reason.kind))
    }
    val choice = if (next != null) pileFor(r, derived, card, listOf(next), rate).copy(also = reasons.filter { it !== next })
    else pileFor(r.copy(pullOut = emptyList()), derived, card, emptyList(), rate).copy(also = reasons)
    return if (choice.pile == pile) null else choice
}

// ---- In words ----

/** A name's first word without the punctuation after it: "Krenko, Mob Boss" → "Krenko". */
fun firstWord(name: String): String = name.trim().split(Regex("\\s+"))[0].trimEnd(',', '.', ':', ';', '!', '?')

/** "Duskmourn binder" — a binder's name as the reason says it. */
fun binderName(name: String): String = if (Regex("binder$", RegexOption.IGNORE_CASE).containsMatchIn(name.trim())) name.trim() else "${name.trim()} binder"

/** 1st, 2nd, 3rd, 4th … 11th, 12th, 13th, 21st. */
fun ordinal(n: Int): String {
    val t = n % 100
    if (t in 11..13) return "${n}th"
    return "$n" + when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
}

/** The big line on a smart pile's card: "KRENKO NEEDS IT", "GOAL · DUSKMOURN UNCOMMONS", "PRIYA WANTS IT", "NEW FOR DUSKMOURN BINDER · p12 s3", "5th COPY · TRADE". */
fun reasonLine(r: SortReason): String = when (r.kind) {
    "DECKS" -> "${firstWord(r.deck ?: "").uppercase()} NEEDS IT"
    "GOALS" -> "GOAL · ${(r.goal ?: "").trim().uppercase()}"
    "FRIENDS" -> "${firstWord(r.friend ?: "").uppercase()} WANTS IT"
    "BINDER" -> "NEW FOR ${binderName(r.binder ?: "").uppercase()} · p${r.page} s${r.slot}"
    else -> "${ordinal(r.copy ?: 0)} COPY · TRADE"
}

/** A line under "Also wanted:": "Priya wants one", "Krenko goblins needs it", "Goal: Duskmourn uncommons 42/92", "New for Duskmourn binder · p12 s3", "5th copy · trade". */
fun alsoLine(r: SortReason): String = when (r.kind) {
    "DECKS" -> "${r.deck} needs it"
    "GOALS" -> "Goal: ${r.goal} ${r.have}/${r.need}"
    "FRIENDS" -> "${r.friend} wants one"
    "BINDER" -> "New for ${binderName(r.binder ?: "")} · p${r.page} s${r.slot}"
    else -> "${ordinal(r.copy ?: 0)} copy · trade"
}

/** "You own 3 already" — copies owned before this one, the session's included; null for none. */
fun ownedLine(ctx: SmartContext, name: String, scans: List<RecipeScan>): String? {
    val n = (ctx.owned[recipeNameKey(name)] ?: 0) + scans.count { recipeNameKey(it.name) == recipeNameKey(name) }
    return if (n > 0) "You own $n already" else null
}

private val RARITY_WORDS = mapOf("common" to "Common", "uncommon" to "Uncommon", "rare" to "Rare", "mythic" to "Mythic", "special" to "Special", "bonus" to "Bonus")

/** A goal's progress with the card in: "41/92 → 42/92". */
fun goalStep(r: SortReason): String = "${(r.have ?: 1) - 1}/${r.need} → ${r.have}/${r.need}"

/**
 * The card's line under its name: "Uncommon · $1.20 · Dominaria Remastered", or "… · missing from
 * Krenko goblins" for a deck, or "… · 41/92 → 42/92" for a goal.
 */
fun cardLine(card: RecipeCard, setName: String?, reason: SortReason?, price: (Double) -> String): String {
    val usd = cardPrice(card)
    val where = when (reason?.kind) {
        "DECKS" -> "missing from ${reason.deck}"
        "GOALS" -> goalStep(reason)
        else -> setName?.takeIf { it.isNotEmpty() } ?: (card.set ?: "").uppercase()
    }
    return listOf(RARITY_WORDS[card.rarity ?: ""] ?: "", usd?.let(price) ?: "No price", where).filter { it.isNotEmpty() }.joinToString(" · ")
}

private val SPOKEN_NUMBERS = listOf(
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
    "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty", "twenty-one", "twenty-two", "twenty-three", "twenty-four"
)

/** What the phone says for a card: "Seven, blue", "One, Krenko needs it". */
fun spokenPile(pile: RecipePile, reason: SortReason?): String {
    val n = SPOKEN_NUMBERS.getOrNull(pile.number) ?: pile.number.toString()
    val what = when (reason?.kind) {
        null -> pile.name.split(" · ").joinToString(", ") { lowerWord(it) }
        "DECKS" -> "${firstWord(reason.deck ?: "")} needs it"
        "GOALS" -> "goal ${reason.goal}"
        "FRIENDS" -> "${firstWord(reason.friend ?: "")} wants it"
        "BINDER" -> "new for ${binderName(reason.binder ?: "")}"
        else -> "trade"
    }
    return n.replaceFirstChar { it.uppercase() } + ", " + what
}

// ---- Capture without tapping ----

/**
 * When to take a card without a tap: once the camera has read the same title [steadyFrames] frames in
 * a row — and never the same physical card twice. After a card is taken it's "held": reads of it are
 * ignored until it has left the frame ([gapFrames] frames in a row with no title) or another card has
 * read steadily in its place. A read whose lookup came to nothing (missed: half a name, or no such
 * card) counts as nothing in view from then on — so it isn't tried over and over, and more of the card
 * coming into the frame reads differently and is taken. Rescan lets the card in view be taken again
 * ("Wrong card?"). The same as the web app's HandsFreeCapture, frame for frame.
 */
class HandsFreeCapture(private val steadyFrames: Int = 3, private val gapFrames: Int = 4) {
    private var lastRead: String? = null
    private var steady = 0
    private var blank = 0
    private var held: String? = null
    private var rejected: String? = null

    /** One frame: the title read, or null. True when this is the moment to take the card. */
    @Synchronized
    fun onRead(read: String?): Boolean {
        val letters = if (read == null) "" else titleLetters(read)
        val title = if (rejected != null && letters == rejected) "" else letters
        if (title.isEmpty()) {
            blank++
            steady = 0
            lastRead = null
            if (blank >= gapFrames) held = null
            return false
        }
        blank = 0
        steady = if (lastRead == title) steady + 1 else 1
        lastRead = title
        val h = held
        if (h != null && sameTitle(title, h)) return false
        if (steady < steadyFrames) return false
        held = title
        steady = 0
        return true
    }

    /** The card taken was found: [name] is what's held until it leaves. */
    @Synchronized
    fun captured(name: String) {
        held = titleLetters(name)
        rejected = null
    }

    /** The card taken came to nothing: its read is nothing in view from now on. */
    @Synchronized
    fun missed() {
        rejected = held
        held = null
    }

    /** Whether a card is held (taken, and not yet gone from the frame). */
    val holding: Boolean
        @Synchronized get() = held != null

    /**
     * The camera moved (the zoom changed): the reads in a row start over, as if the card had moved.
     * The card held is still the one in view, so it stays held.
     */
    @Synchronized
    fun moved() {
        steady = 0
        lastRead = null
    }

    /** Forget the card in view: it can be taken again. */
    @Synchronized
    fun rescan() {
        held = null
        steady = 0
    }

    private fun titleLetters(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Whether two titles are the same card for holding: one inside the other, or the first four letters alike. */
    private fun sameTitle(a: String, b: String): Boolean {
        if (a.contains(b) || b.contains(a)) return true
        var common = 0
        while (common < a.length && common < b.length && a[common] == b[common]) common++
        return common >= 4
    }
}

// ---- When it's done ----

/** A line of the summary: piles [from]–[to] (one pile when equal), their cards, value (US dollars) and who or what they're for. */
data class SummaryRow(val from: Int, val to: Int, val name: String, val cards: Int, val usd: Double, val detail: String?)

data class RecipeSummary(val rows: List<SummaryRow>, val cards: Int, val usd: Double)

private fun counted(names: List<String>): String =
    names.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .joinToString(", ") { "${it.key} ${it.value}" }

/**
 * "Sorted 212 cards": each smart and keep-apart pile with cards, with who it's for ("Krenko 4,
 * Atraxa 2", "Duskmourn uncommons 3, Shock lands 1", "Priya 3, Jo 2", the binders); the levels' piles one by one — or, when there are more
 * than six, as one line ("6–11 · Bulk by colour").
 */
fun summarize(recipe: SortRecipe, derived: DerivedPiles, scans: List<RecipeScan>): RecipeSummary {
    val r = sortRecipe(recipe)
    val rows = mutableListOf<SummaryRow>()
    fun valueOf(list: List<RecipeScan>) = Math.round(list.sumOf { cardPrice(it.card) ?: 0.0 } * 100) / 100.0
    // A value level's own bands ("$2 and up") stay lines of their own; the piles after it — or all the
    // levels' piles — are one line when there are more than six.
    val restOn = r.levels.firstOrNull()?.let { it.by == "VALUE" && it.restOn == true } == true && r.levels.size > 1
    val levels = derived.piles.filter { it.kind == "LEVEL" && !(restOn && it.key != REST_KEY && !it.key.contains('/')) }
    val grouped = levels.size > 6
    for (p in derived.piles) {
        if (grouped && p in levels) continue
        val here = scans.filter { it.pile == p.number }
        if (here.isEmpty()) continue
        val detail = when (p.key) {
            "S:DECKS" -> counted(here.mapNotNull { if (it.reason?.kind == "DECKS") firstWord(it.reason.deck ?: "") else null }.filter { it.isNotEmpty() })
            "S:GOALS" -> counted(here.mapNotNull { s -> s.reason?.takeIf { it.kind == "GOALS" }?.goal?.trim() }.filter { it.isNotEmpty() })
            "S:FRIENDS" -> counted(here.mapNotNull { if (it.reason?.kind == "FRIENDS") firstWord(it.reason.friend ?: "") else null }.filter { it.isNotEmpty() })
            "S:BINDER" -> here.mapNotNull { if (it.reason?.kind == "BINDER") it.reason.binder else null }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
            else -> null
        }
        rows += SummaryRow(p.number, p.number, p.name, here.size, valueOf(here), detail?.ifEmpty { null })
    }
    if (grouped) {
        val numbers = levels.map { it.number }.toSet()
        val here = scans.filter { it.pile in numbers }
        val level = r.levels[if (restOn) 1 else 0]
        if (here.isNotEmpty()) rows += SummaryRow(levels.first().number, levels.last().number, "Bulk by ${lowerWord(LEVEL_LABELS.getValue(level.by))}", here.size, valueOf(here), null)
    }
    return RecipeSummary(rows, scans.size, valueOf(scans))
}

/** What checking one card of a pile found: it belongs, or the pile it should be in (null: not sorted this time). */
data class PileCheck(val belongs: Boolean, val line: String, val goes: Int?)

/**
 * "Check a pile": one card scanned from pile [pile] — it belongs while the pile holds more copies of it
 * than have been checked already ([checked]: the names checked so far that belonged).
 */
fun checkPileCard(derived: DerivedPiles, scans: List<RecipeScan>, pile: Int, checked: List<String>, name: String): PileCheck {
    val k = recipeNameKey(name)
    val inPile = scans.count { it.pile == pile && recipeNameKey(it.name) == k }
    val done = checked.count { recipeNameKey(it) == k }
    if (inPile > done) return PileCheck(true, "Belongs in pile $pile", pile)
    val other = scans.firstOrNull { it.pile != pile && recipeNameKey(it.name) == k } ?: return PileCheck(false, "Doesn't belong — not sorted this time", null)
    val p = derived.piles.firstOrNull { it.number == other.pile }
    return PileCheck(false, "Doesn't belong — pile ${other.pile}" + (p?.let { " · ${it.name}" } ?: ""), other.pile)
}

/**
 * Where a pile is filed: what the recipe says for it, else the box whose rule fits — or no place for
 * decks, goals, friends and trades (a goal's card with a set binder goes there: fileRecipe).
 */
fun pileGoesTo(recipe: SortRecipe, pile: RecipePile): String {
    recipe.goTo.orEmpty().firstOrNull { it.pile == pile.key }?.let { return it.to }
    return if (pile.key == "S:DECKS" || pile.key == "S:GOALS" || pile.key == "S:FRIENDS" || pile.key == "S:TRADE") "" else BY_RULE
}

/** [recipe] with pile [key] filed at [to] (a place's id, BY_RULE or "": no place). */
fun withGoTo(recipe: SortRecipe, key: String, to: String): SortRecipe =
    sortRecipe(recipe.copy(goTo = recipe.goTo.orEmpty().filter { it.pile != key } + PileGoTo(key, to)))

/**
 * The binder kept in order a card sorted this session is filed into, to be fitted in: a binder gap's
 * binder, or a goal's set binder (unless the recipe says where the Goals need pile goes); null for
 * the rest.
 */
fun binderFiledInto(recipe: SortRecipe, s: RecipeScan): String? {
    val r = s.reason ?: return null
    return when {
        r.kind == "BINDER" -> r.placeId
        r.kind == "GOALS" && s.key == "S:GOALS" && recipe.goTo.orEmpty().none { it.pile == "S:GOALS" } -> r.placeId
        else -> null
    }
}

/**
 * "File everything": every card not filed yet goes into the collection at its pile's place, as the
 * old sorter files (fileEveryPile): a binder gap into its binder (waiting beside it to be fitted in
 * order), a goal's card into its set binder the same way (or, with none, where the recipe files the
 * Goals need pile — the Unsorted pile unless the user said otherwise), the deck-need cards with no
 * place, for their decks' pull lists to find, the rest where the recipe files their pile. The goals
 * count them from then on, and the goal watcher celebrates one that's now complete.
 */
fun fileRecipe(collections: List<Collection>, recipe: SortRecipe, derived: DerivedPiles, scans: List<RecipeScan>): FiledPiles {
    val rules = derived.piles.map { p -> PileRule(PileKind.BULK.name, to = pileGoesTo(recipe, p).ifEmpty { null }) }.toMutableList()
    val binderRule = LinkedHashMap<String, Int>()
    val out = mutableListOf<SortScan>()
    for (s in scans) {
        if (s.filed == true) continue
        var pile = s.pile - 1
        val binder = binderFiledInto(recipe, s)
        if (binder != null) {
            val id: String = binder
            pile = binderRule.getOrPut(id) { rules += PileRule(PileKind.BULK.name, to = id); rules.size - 1 }
        }
        out += SortScan(s.id, s.scryfallId, s.name, s.card.rarity, cardPrice(s.card), s.facts, s.entry, pile, "")
    }
    return fileEveryPile(collections, SortSession(source = "", rules = rules, newCards = true, scans = out))
}

// ---- Kept and synced ----

/** The user's own recipes, kept on the Unsorted pile. */
fun recipesOf(collections: List<Collection>): List<SortRecipe> = collections.firstOrNull { it.isUnsorted }?.sortRecipes.orEmpty()

/** [collections] with the recipes set to [recipes] (on the Unsorted pile, made if it isn't there). */
fun withRecipes(collections: List<Collection>, recipes: List<SortRecipe>): List<Collection> =
    withUnsortedPile(collections).map { if (it.isUnsorted) it.copy(sortRecipes = recipes.map { r -> sortRecipe(r) }) else it }

/** [collections] with [recipe] added, or put in place of the one with its id. */
fun saveRecipe(collections: List<Collection>, recipe: SortRecipe): List<Collection> {
    val list = recipesOf(collections)
    return withRecipes(collections, if (list.any { it.id == recipe.id }) list.map { if (it.id == recipe.id) recipe else it } else list + recipe)
}

fun deleteRecipe(collections: List<Collection>, id: String): List<Collection> = withRecipes(collections, recipesOf(collections).filter { it.id != id })

private fun <T> pickRecipe(base: T, mine: T, theirs: T, minePreferred: Boolean): T = when {
    mine == theirs -> mine
    mine == base -> theirs
    theirs == base -> mine
    minePreferred -> mine
    else -> theirs
}

/**
 * Merges two devices' recipes: one added on either side is kept, one deleted on either side stays
 * deleted, and each field (name, smart piles, levels, keep apart, where piles go) goes to whoever
 * changed it — the more recent edit when both did. Null when no side has any.
 */
fun mergeRecipes(base: List<SortRecipe>?, mine: List<SortRecipe>?, theirs: List<SortRecipe>?, minePreferred: Boolean): List<SortRecipe>? {
    if (base == null && mine == null && theirs == null) return null
    val b = base.orEmpty().associateBy { it.id }
    val m = mine.orEmpty().associateBy { it.id }
    val t = theirs.orEmpty().associateBy { it.id }
    val added = (t.keys + m.keys).filter { it !in b }.distinct().sorted()
    val out = mutableListOf<SortRecipe>()
    for (id in b.keys.toList() + added) {
        val br = b[id]
        val mr = m[id]
        val tr = t[id]
        if (br != null && (mr == null || tr == null)) continue
        if (br == null) { out += sortRecipe(tr ?: mr!!); continue }
        out += sortRecipe(
            SortRecipe(
                id = id,
                name = pickRecipe(br.name, mr!!.name, tr!!.name, minePreferred),
                pullOut = pickRecipe(br.pullOut, mr.pullOut, tr.pullOut, minePreferred),
                levels = pickRecipe(br.levels, mr.levels, tr.levels, minePreferred),
                apart = pickRecipe(br.apart, mr.apart, tr.apart, minePreferred),
                goTo = pickRecipe(br.goTo, mr.goTo, tr.goTo, minePreferred),
                createdAt = minOf(mr.createdAt, tr.createdAt)
            )
        )
    }
    return out
}

/**
 * [theirs] with [source]'s recipes, when [theirs] was saved by an app that doesn't know about recipes
 * (no "sortRecipes" key); and each of its recipes saved by an app that doesn't know about the Goals
 * need pile (no "goals" key) with that pile back where [source]'s same recipe pulls it out — the same
 * object otherwise.
 */
fun keepRecipesFromOlderApp(source: Collection, theirs: Collection): Collection {
    if (!theirs.isUnsorted || source.sortRecipes == null) return theirs
    val list = theirs.sortRecipes ?: return theirs.copy(sortRecipes = source.sortRecipes)
    val withGoals = source.sortRecipes.filter { "GOALS" in it.pullOut }.map { it.id }.toSet()
    fun lost(r: SortRecipe) = r.goals == null && r.id in withGoals && "GOALS" !in r.pullOut
    if (list.none(::lost)) return theirs
    return theirs.copy(sortRecipes = list.map { if (lost(it)) sortRecipe(it.copy(pullOut = it.pullOut + "GOALS")) else it })
}

// ---- Pile signs ----

/** A paper's height in millimetres: A4 or LETTER. */
private val PAPER_HEIGHT = mapOf("A4" to 297.0, "LETTER" to 279.4)

private fun escapeHtml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/**
 * The pile signs as a page to print: two to a sheet of [paper] ("A4" or "LETTER"), each the pile's
 * number big, its name under it and its colour across the top — to stand by each pile on the table.
 * Both apps print this same page (the web app from a frame, this app through the print framework, as
 * box labels).
 */
fun pileSignsHtml(piles: List<RecipePile>, paper: String, title: String = "Pile signs"): String {
    val sign = Math.floor(((PAPER_HEIGHT.getValue(paper) - 24) / 2 - 2) * 10) / 10
    val signs = piles.joinToString("") { p -> "<div class=\"sign\" style=\"border-top-color:${escapeHtml(p.band)}\"><div class=\"num\">${p.number}</div><div class=\"name\">${escapeHtml(p.name)}</div></div>" }
    return "<!doctype html><html><head><meta charset=\"utf-8\"><title>" + escapeHtml(title) + "</title><style>" +
        "@page{size:${if (paper == "A4") "A4" else "letter"} portrait;margin:12mm}" +
        "body{margin:0;font-family:Manrope,Arial,sans-serif;color:#14161c;background:#fff}" +
        ".sign{height:${whole(sign)}mm;box-sizing:border-box;margin-bottom:4mm;border:0.4mm dashed #999;border-top:10mm solid #e6b45e;display:flex;flex-direction:column;align-items:center;justify-content:center;break-inside:avoid;page-break-inside:avoid}" +
        ".sign:nth-child(2n){break-after:page;page-break-after:always}" +
        ".num{font-size:170pt;font-weight:800;line-height:1}" +
        ".name{font-size:30pt;font-weight:700;text-align:center;padding:0 10mm}" +
        "</style></head><body>" + signs + "</body></html>"
}
