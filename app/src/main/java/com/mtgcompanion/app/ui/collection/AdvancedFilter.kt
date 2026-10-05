package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.data.CARD_CONDITIONS
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.GameMode
import com.mtgcompanion.app.data.languageName
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

// The All cards Advanced filters: Scryfall-style fields (colours, mana, stats, format, sets, what
// the card is, price, art and words) judged against the Scryfall data already fetched for the cards
// owned, plus "Your copies" fields judged against the collection itself. Also the same filters as a
// Scryfall query (read-only, to copy or open on Scryfall), the removable chips the results show, and
// saved filters as JSON. Pure, so it can be tested.
// Mirrors the web app's src/collection/advancedFilter.ts — same fields, same matching rules, same
// query, same chip wording, and byte-for-byte the same saved-filter JSON.

/** How a number is compared: stored as these, shown as ≤ ≥ ≠. */
val COMPARE_OPS = listOf("=", "<", "<=", ">", ">=", "!=")
val OP_SYMBOLS = mapOf("=" to "=", "<" to "<", "<=" to "≤", ">" to ">", ">=" to "≥", "!=" to "≠")

/** W U B R G, and C for colourless (picked on its own). */
val ADVANCED_COLORS = listOf("W", "U", "B", "R", "G", "C")
val COLOR_TARGETS = listOf("color", "identity")
val COLOR_MODES = listOf("exactly", "including", "atMost")
val COLOR_MODE_LABELS = mapOf("exactly" to "Exactly", "including" to "Including", "atMost" to "At most")
private val COLOR_MODE_SYMBOLS = mapOf("exactly" to "=", "including" to "≥", "atMost" to "≤")
private val COLOR_MODE_OPS = mapOf("exactly" to "=", "including" to ">=", "atMost" to "<=")

/** The app's formats that Scryfall knows (Limited has no Scryfall format): Scryfall's key to its name. */
val FILTER_FORMATS: List<Pair<String, String>> = GameMode.entries.filter { it != GameMode.LIMITED }.map { it.scryfallFormat to it.label }
val LEGALITIES = listOf("legal", "banned", "restricted")
val LEGALITY_LABELS = mapOf("legal" to "Legal", "banned" to "Banned", "restricted" to "Restricted")

val CARD_IS = listOf("commander", "gamechanger", "reserved", "dfc", "fullart", "token")
val CARD_IS_LABELS = mapOf(
    "commander" to "Can be a commander",
    "gamechanger" to "Game Changer",
    "reserved" to "Reserved List",
    "dfc" to "Double-faced",
    "fullart" to "Full art",
    "token" to "Token"
)
private val CARD_IS_QUERY = mapOf(
    "commander" to "is:commander", "gamechanger" to "is:gamechanger", "reserved" to "is:reserved",
    "dfc" to "is:dfc", "fullart" to "is:fullart", "token" to "t:token"
)

val FINISHES = listOf("nonfoil", "foil", "etched")
val FINISH_LABELS = mapOf("nonfoil" to "Non-foil", "foil" to "Foil", "etched" to "Etched")
/** The collection's condition codes, as the chips name them. */
val CONDITION_LABELS = mapOf("NM" to "NM", "LP" to "LP", "MP" to "MP", "HP" to "HP", "DMG" to "Damaged")

val IN_DECK_OPTIONS = listOf("any", "yes", "no")
val IN_DECK_LABELS = mapOf("any" to "Any", "yes" to "In a deck", "no" to "Not in any")

data class AdvancedFilter(
    /** "color" or "identity". */
    val colorTarget: String = "identity",
    /** "exactly", "including" or "atMost". */
    val colorMode: String = "atMost",
    /** WUBRG letters, or just C for colourless. */
    val colors: List<String> = emptyList(),
    val multicolor: Boolean = false,
    val mvOp: String = "<=",
    val mv: String = "",
    /** Symbols the mana cost must hold at least, e.g. "{2}{U}{U}". */
    val manaCost: String = "",
    val powerOp: String = ">=",
    val power: String = "",
    val toughnessOp: String = ">=",
    val toughness: String = "",
    val loyaltyOp: String = ">=",
    val loyalty: String = "",
    /** Scryfall's key ("commander"), or "" for any format. */
    val format: String = "",
    val legality: String = "legal",
    /** Set codes, lower case. */
    val sets: List<String> = emptyList(),
    val cardIs: List<String> = emptyList(),
    /** Comma-separated; the card must have every one. */
    val keywords: String = "",
    /** In the chosen currency, per copy. */
    val priceMin: String = "",
    val priceMax: String = "",
    val artist: String = "",
    val flavor: String = "",
    // Your copies — the collection, not Scryfall.
    val finishes: List<String> = emptyList(),
    val conditions: List<String> = emptyList(),
    /** A language code ("ja"), or "" for any. */
    val language: String = "",
    /** A binder's id, or "" for any. */
    val binder: String = "",
    /** "any", "yes" or "no". */
    val inDeck: String = "any",
    val copiesOp: String = ">=",
    val copies: String = ""
) {
    /** Whether any advanced field is set (a number field only once it's a number). */
    val active: Boolean get() = count > 0

    /** How many advanced filters are on — one per chip they show as. */
    val count: Int
        get() = listOf(
            colorsOn, multicolor, numberOf(mv) != null, manaSymbols(manaCost).isNotEmpty(),
            numberOf(power) != null, numberOf(toughness) != null, numberOf(loyalty) != null,
            format != "", keywordList(keywords).isNotEmpty(),
            numberOf(priceMin) != null || numberOf(priceMax) != null, artist.isNotBlank(), flavor.isNotBlank(),
            language != "", binder != "", inDeck != "any", numberOf(copies) != null
        ).count { it } + sets.size + cardIs.size + finishes.size + conditions.size

    internal val pickedColors: List<String> get() = colors.filter { it != "C" }
    internal val colorless: Boolean get() = "C" in colors && pickedColors.isEmpty()
    internal val colorsOn: Boolean get() = pickedColors.isNotEmpty() || "C" in colors
}

/** A typed number, or null for anything else (blank, "*", "abc"). */
fun numberOf(value: String?): Double? {
    val v = value.orEmpty().trim()
    if (!NUMBER.matches(v)) return null
    return v.toDouble()
}

private val NUMBER = Regex("^-?\\d+(\\.\\d+)?$|^-?\\.\\d+$")

fun compare(value: Double, op: String, target: Double): Boolean = when (op) {
    "=" -> value == target
    "<" -> value < target
    "<=" -> value <= target
    ">" -> value > target
    ">=" -> value >= target
    "!=" -> value != target
    else -> true
}

/** "{2}{U}{U}" (or "2uu") as its symbols: ["2", "U", "U"]. Hybrid and the like stay whole: "W/U". */
fun manaSymbols(cost: String?): List<String> {
    val raw = cost.orEmpty().trim().uppercase()
    if (raw.isEmpty()) return emptyList()
    if ('{' in raw) return Regex("\\{([^}]+)\\}").findAll(raw).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
    return Regex("\\d+|[A-Z]").findAll(raw).map { it.value }.toList()
}

private fun isGeneric(s: String) = s.isNotEmpty() && s.all { it.isDigit() }

/** Whether [have] holds at least the symbols [want]: as many generic mana, and each other symbol as often. */
fun costContains(have: List<String>, want: List<String>): Boolean {
    fun generic(list: List<String>) = list.filter(::isGeneric).sumOf { it.toInt() }
    if (generic(have) < generic(want)) return false
    val counts = HashMap<String, Int>()
    have.filterNot(::isGeneric).forEach { counts[it] = (counts[it] ?: 0) + 1 }
    for (s in want) {
        if (isGeneric(s)) continue
        val n = counts[s] ?: 0
        if (n == 0) return false
        counts[s] = n - 1
    }
    return true
}

private fun keywordList(keywords: String) = keywords.split(',').map { it.trim() }.filter { it.isNotEmpty() }
private fun wubrgOrder(list: Iterable<String>) = list.sortedBy { ADVANCED_COLORS.indexOf(it) }

/** Power, toughness and loyalty as printed, of the card or one of its faces. */
data class FaceStats(val power: String? = null, val toughness: String? = null, val loyalty: String? = null)

/** What the Advanced filters need to know about a card, from its Scryfall data. */
data class AdvancedFacts(
    /** The card's own colours (every face of a double-faced card). */
    val colors: List<String> = emptyList(),
    val identity: List<String> = emptyList(),
    val cmc: Double? = null,
    /** Every face's mana cost, as symbols. */
    val manaCost: List<String> = emptyList(),
    val stats: List<FaceStats> = emptyList(),
    val legalities: Map<String, String> = emptyMap(),
    val set: String = "",
    val canBeCommander: Boolean = false,
    val gameChanger: Boolean = false,
    val reserved: Boolean = false,
    val dfc: Boolean = false,
    val fullArt: Boolean = false,
    val token: Boolean = false,
    val keywords: List<String> = emptyList(),
    /** US dollars; null with no price. */
    val usd: Double? = null,
    val usdFoil: Double? = null,
    val artist: String = "",
    val flavor: String = "",
    /** The printing only comes as etched foil: its foil copies are etched. */
    val etchedOnly: Boolean = false
)

private val TOKEN_WORD = Regex("\\btoken\\b", RegexOption.IGNORE_CASE)

private fun price(v: String?): Double? = v?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it.isFinite() }

fun advancedFactsOf(card: ScryfallCard): AdvancedFacts {
    val faces = card.cardFaces.orEmpty()
    val colors = card.colors ?: faces.flatMap { it.colors.orEmpty() }.distinct()
    val costs = if (!card.manaCost.isNullOrEmpty()) listOf(card.manaCost) else faces.map { it.manaCost.orEmpty() }
    val typeLine = card.typeLine ?: faces.joinToString(" // ") { it.typeLine.orEmpty() }
    val finishes = card.finishes.orEmpty()
    return AdvancedFacts(
        colors = colors.map { it.uppercase() },
        identity = card.colorIdentity.orEmpty().map { it.uppercase() },
        cmc = card.cmc,
        manaCost = costs.flatMap { manaSymbols(it) },
        stats = listOf(FaceStats(card.power, card.toughness, card.loyalty)) + faces.map { FaceStats(it.power, it.toughness, it.loyalty) },
        legalities = card.legalities.orEmpty(),
        set = card.set.orEmpty().lowercase(),
        canBeCommander = card.canBeCommander,
        gameChanger = card.gameChanger == true,
        reserved = card.reserved == true,
        dfc = card.hasFlipSides,
        fullArt = card.fullArt == true,
        token = TOKEN_WORD.containsMatchIn(typeLine) || card.layout == "token" || card.layout == "double_faced_token",
        keywords = card.keywords.orEmpty(),
        usd = price(card.prices?.usd),
        usdFoil = price(card.prices?.usdFoil),
        artist = (listOf(card.artist) + faces.map { it.artist }).filter { !it.isNullOrEmpty() }.joinToString("\n"),
        flavor = (listOf(card.flavorText) + faces.map { it.flavorText }).filter { !it.isNullOrEmpty() }.joinToString("\n"),
        etchedOnly = "etched" in finishes && "foil" !in finishes
    )
}

/** What the Your copies filters need to know about a card: the copies owned, from the collection. */
data class CopyFacts(
    val nonfoil: Int = 1,
    /** Foil copies (etched ones too: the collection keeps them as foil). */
    val foil: Int = 0,
    /** Conditions said for the binder copies (NM, LP…). */
    val conditions: List<String> = emptyList(),
    /** Languages of the binder copies; a copy with none said is English. */
    val languages: List<String> = listOf("en"),
    /** The owned binders holding it. */
    val binders: List<String> = emptyList(),
    val inDeck: Boolean = false,
    /** All copies, in binders and decks — as All cards counts them. */
    val copies: Int = 1
)

/**
 * By scryfallId, the copies in the owned binders (not wishlists) and in decks — the places All cards
 * lists and buildCardSources names. A deck keeps no finish, so its copies count as non-foil.
 */
fun copyFactsOf(collections: List<Collection>, decks: List<Deck>): Map<String, CopyFacts> {
    class Acc {
        var nonfoil = 0
        var foil = 0
        val conditions = mutableListOf<String>()
        val languages = mutableListOf<String>()
        val binders = mutableListOf<String>()
        var inDeck = false
        var copies = 0
    }
    val out = LinkedHashMap<String, Acc>()
    fun MutableList<String>.addOnce(v: String) { if (v !in this) add(v) }
    for (c in collections) {
        if (c.kind != CollectionType.OWNED) continue
        for (e in c.entries) {
            val n = e.quantity + e.foilQuantity
            if (n <= 0) continue
            val f = out.getOrPut(e.scryfallId) { Acc() }
            f.nonfoil += e.quantity
            f.foil += e.foilQuantity
            f.copies += n
            e.condition?.takeIf { it.isNotEmpty() }?.let { f.conditions.addOnce(it) }
            f.languages.addOnce(e.language?.takeIf { it.isNotEmpty() } ?: "en")
            f.binders.addOnce(c.id)
        }
    }
    for (d in decks) {
        for (e in d.cards) {
            if (e.quantity <= 0) continue
            val f = out.getOrPut(e.scryfallId) { Acc() }
            f.nonfoil += e.quantity
            f.copies += e.quantity
            f.inDeck = true
        }
    }
    return out.mapValues { (_, f) -> CopyFacts(f.nonfoil, f.foil, f.conditions.toList(), f.languages.toList(), f.binders.toList(), f.inDeck, f.copies) }
}

/** The price a copy is judged by (US dollars): non-foil, unless every copy is foil — as the price alerts do. */
fun copyPrice(facts: AdvancedFacts, copies: CopyFacts?): Double? =
    if (copies != null && copies.nonfoil <= 0 && copies.foil > 0) facts.usdFoil ?: facts.usd
    else facts.usd ?: facts.usdFoil

private fun statMatches(stats: List<FaceStats>, pick: (FaceStats) -> String?, op: String, target: String): Boolean {
    val t = numberOf(target) ?: return true
    return stats.any { s -> numberOf(pick(s))?.let { compare(it, op, t) } == true }
}

/**
 * Whether a card passes every advanced filter. [facts] null: its data hasn't loaded, so it can't be
 * judged and is left out while a filter is on; the same for [copies] while a Your copies filter is
 * on. Prices are typed in the chosen currency: [toUsd] turns them into dollars.
 */
fun advancedMatches(a: AdvancedFilter, facts: AdvancedFacts?, copies: CopyFacts?, toUsd: (Double) -> Double = { it }): Boolean {
    if (!a.active) return true
    if (facts == null) return false

    val have = if (a.colorTarget == "identity") facts.identity else facts.colors
    if (a.colorless) {
        if (have.isNotEmpty()) return false
    } else if (a.pickedColors.isNotEmpty()) {
        val want = a.pickedColors
        val inside = have.all { it in want }
        val covers = want.all { it in have }
        if (a.colorMode == "exactly" && !(inside && covers)) return false
        if (a.colorMode == "including" && !covers) return false
        if (a.colorMode == "atMost" && !inside) return false
    }
    if (a.multicolor && have.size < 2) return false

    val mv = numberOf(a.mv)
    if (mv != null && (facts.cmc == null || !compare(facts.cmc, a.mvOp, mv))) return false
    val cost = manaSymbols(a.manaCost)
    if (cost.isNotEmpty() && !costContains(facts.manaCost, cost)) return false

    if (!statMatches(facts.stats, { it.power }, a.powerOp, a.power)) return false
    if (!statMatches(facts.stats, { it.toughness }, a.toughnessOp, a.toughness)) return false
    if (!statMatches(facts.stats, { it.loyalty }, a.loyaltyOp, a.loyalty)) return false

    if (a.format.isNotEmpty() && facts.legalities[a.format] != a.legality) return false
    if (a.sets.isNotEmpty() && facts.set !in a.sets) return false

    for (isWhat in a.cardIs) {
        val ok = when (isWhat) {
            "commander" -> facts.canBeCommander
            "gamechanger" -> facts.gameChanger
            "reserved" -> facts.reserved
            "dfc" -> facts.dfc
            "fullart" -> facts.fullArt
            "token" -> facts.token
            else -> true
        }
        if (!ok) return false
    }
    val cardKeywords = facts.keywords.map { it.lowercase() }
    if (!keywordList(a.keywords).all { it.lowercase() in cardKeywords }) return false

    val lo = numberOf(a.priceMin)
    val hi = numberOf(a.priceMax)
    if (lo != null || hi != null) {
        val usd = copyPrice(facts, copies) ?: return false
        // A cent's grace, so a price typed as shown isn't missed by rounding.
        if (lo != null && usd < toUsd(lo) - 0.005) return false
        if (hi != null && usd > toUsd(hi) + 0.005) return false
    }
    if (a.artist.isNotBlank() && !facts.artist.contains(a.artist.trim(), ignoreCase = true)) return false
    if (a.flavor.isNotBlank() && !facts.flavor.contains(a.flavor.trim(), ignoreCase = true)) return false

    val copiesOn = a.finishes.isNotEmpty() || a.conditions.isNotEmpty() || a.language != "" || a.binder != "" || a.inDeck != "any" || numberOf(a.copies) != null
    if (!copiesOn) return true
    if (copies == null) return false
    if (a.finishes.isNotEmpty()) {
        val foilKind = if (facts.etchedOnly) "etched" else "foil"
        val ok = ("nonfoil" in a.finishes && copies.nonfoil > 0) || (foilKind in a.finishes && copies.foil > 0)
        if (!ok) return false
    }
    if (a.conditions.isNotEmpty() && a.conditions.none { it in copies.conditions }) return false
    if (a.language != "" && a.language !in copies.languages) return false
    if (a.binder != "" && a.binder !in copies.binders) return false
    if (a.inDeck == "yes" && !copies.inDeck) return false
    if (a.inDeck == "no" && copies.inDeck) return false
    val n = numberOf(a.copies)
    if (n != null && !compare(copies.copies.toDouble(), a.copiesOp, n)) return false
    return true
}

// ---- The same filters as a Scryfall query ----

/** A number as a query writes it: at most two decimals, none when whole ("1", "1.5", "0.33"). */
fun queryNumber(n: Double): String =
    BigDecimal(n).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

private fun quoteIfNeeded(value: String): String {
    val v = value.replace("\"", "").trim()
    return if (v.any { it.isWhitespace() }) "\"$v\"" else v
}

private fun anyOf(parts: List<String>) = if (parts.size == 1) parts[0] else "(${parts.joinToString(" or ")})"

/**
 * The basic and advanced filters in Scryfall's search syntax — what Scryfall would find, across every
 * card. Your copies filters aren't in it: Scryfall doesn't know the collection. Prices are typed in
 * the chosen currency and searched in US dollars ([toUsd]).
 */
fun scryfallQuery(basic: CollectionFilter, a: AdvancedFilter, toUsd: (Double) -> Double = { it }): String {
    val parts = mutableListOf<String>()
    basic.type.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { parts += "t:${it.lowercase().replace("\"", "")}" }
    if (basic.text.isNotBlank()) parts += "o:${quoteIfNeeded(basic.text.lowercase())}"
    // The basic panel's colours are judged by colour identity.
    if (basic.colors.isNotEmpty()) parts += "id>=${wubrgOrder(basic.colors.map { it.toString() }).joinToString("").lowercase()}"
    val rarities = COLLECTION_FILTER_RARITIES.filter { it in basic.rarities }
    if (rarities.isNotEmpty()) parts += anyOf(rarities.map { "r:$it" })

    val key = if (a.colorTarget == "identity") "id" else "c"
    if (a.colorless) parts += "$key=c"
    else if (a.pickedColors.isNotEmpty()) parts += "$key${COLOR_MODE_OPS[a.colorMode] ?: "<="}${wubrgOrder(a.pickedColors).joinToString("").lowercase()}"
    if (a.multicolor) parts += "$key:m"

    numberOf(a.mv)?.let { parts += "mv${a.mvOp}${queryNumber(it)}" }
    val cost = manaSymbols(a.manaCost)
    if (cost.isNotEmpty()) parts += "m:" + cost.joinToString("") { "{$it}" }
    numberOf(a.power)?.let { parts += "pow${a.powerOp}${queryNumber(it)}" }
    numberOf(a.toughness)?.let { parts += "tou${a.toughnessOp}${queryNumber(it)}" }
    numberOf(a.loyalty)?.let { parts += "loy${a.loyaltyOp}${queryNumber(it)}" }

    if (a.format.isNotEmpty()) parts += "${if (a.legality == "legal") "f" else a.legality}:${a.format}"
    if (a.sets.isNotEmpty()) parts += anyOf(a.sets.map { "e:$it" })
    CARD_IS.filter { it in a.cardIs }.forEach { parts += CARD_IS_QUERY.getValue(it) }
    keywordList(a.keywords).forEach { parts += "kw:${quoteIfNeeded(it.lowercase())}" }

    numberOf(a.priceMin)?.let { parts += "usd>=${queryNumber(toUsd(it))}" }
    numberOf(a.priceMax)?.let { parts += "usd<=${queryNumber(toUsd(it))}" }
    if (a.artist.isNotBlank()) parts += "a:${quoteIfNeeded(a.artist)}"
    if (a.flavor.isNotBlank()) parts += "ft:${quoteIfNeeded(a.flavor)}"
    return parts.joinToString(" ")
}

// ---- Chips: one per filter on, each removable ----

/** One filter on, as a chip: [label], then [symbols] as mana symbols ("Identity ≤" then U, B). */
data class ActiveChip(val key: String, val label: String, val symbols: List<String> = emptyList()) {
    /** As words, for a screen reader and the tests: "Identity ≤ UB". */
    val text: String get() = listOf(label, symbols.joinToString("")).filter { it.isNotEmpty() }.joinToString(" ")
}

private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

/**
 * The chips for the basic and advanced filters on, in the order the panel and the page show them.
 * [binderName] names a binder by id; [formatLocal] shows an amount typed in the chosen currency.
 */
fun filterChips(basic: CollectionFilter, a: AdvancedFilter, binderName: (String) -> String?, formatLocal: (Double) -> String): List<ActiveChip> {
    val chips = mutableListOf<ActiveChip>()
    fun add(key: String, label: String, symbols: List<String> = emptyList()) { chips += ActiveChip(key, label, symbols) }
    val words = basic.type.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    if (words.isNotEmpty()) add("type", cap(words))
    if (basic.text.isNotBlank()) add("text", "Text: ${basic.text.trim()}")
    wubrgOrder(basic.colors.map { it.toString() }).forEach { add("color:$it", "Colour", listOf(it)) }
    COLLECTION_FILTER_RARITIES.filter { it in basic.rarities }.forEach { add("rarity:$it", cap(it)) }

    val target = if (a.colorTarget == "identity") "Identity" else "Colour"
    if (a.colorless) add("colors", target, listOf("C"))
    else if (a.pickedColors.isNotEmpty()) add("colors", "$target ${COLOR_MODE_SYMBOLS[a.colorMode] ?: "≤"}", wubrgOrder(a.pickedColors))
    if (a.multicolor) add("multicolor", "Multicolour")
    fun num(key: String, label: String, op: String, v: String) {
        numberOf(v)?.let { add(key, "$label ${OP_SYMBOLS[op] ?: op} ${queryNumber(it)}") }
    }
    num("mv", "MV", a.mvOp, a.mv)
    val cost = manaSymbols(a.manaCost)
    if (cost.isNotEmpty()) add("manaCost", "Cost", cost)
    num("power", "Power", a.powerOp, a.power)
    num("toughness", "Toughness", a.toughnessOp, a.toughness)
    num("loyalty", "Loyalty", a.loyaltyOp, a.loyalty)
    if (a.format.isNotEmpty()) {
        val name = FILTER_FORMATS.firstOrNull { it.first == a.format }?.second ?: a.format
        add("format", "${LEGALITY_LABELS[a.legality] ?: "Legal"} in $name")
    }
    a.sets.forEach { add("set:$it", it.uppercase()) }
    CARD_IS.filter { it in a.cardIs }.forEach { add("is:$it", CARD_IS_LABELS.getValue(it)) }
    val kws = keywordList(a.keywords)
    if (kws.isNotEmpty()) add("keywords", "Keywords: ${kws.joinToString(", ")}")
    val lo = numberOf(a.priceMin)
    val hi = numberOf(a.priceMax)
    when {
        lo != null && hi != null -> add("price", "Price ${formatLocal(lo)}–${formatLocal(hi)}")
        lo != null -> add("price", "Price ≥ ${formatLocal(lo)}")
        hi != null -> add("price", "Price ≤ ${formatLocal(hi)}")
    }
    if (a.artist.isNotBlank()) add("artist", "Artist: ${a.artist.trim()}")
    if (a.flavor.isNotBlank()) add("flavor", "Flavour: ${a.flavor.trim()}")

    FINISHES.filter { it in a.finishes }.forEach { add("finish:$it", FINISH_LABELS.getValue(it)) }
    CARD_CONDITIONS.filter { it in a.conditions }.forEach { add("condition:$it", CONDITION_LABELS[it] ?: it) }
    if (a.language.isNotEmpty()) add("language", languageName(a.language))
    if (a.binder.isNotEmpty()) add("binder", binderName(a.binder) ?: "Binder")
    if (a.inDeck == "yes") add("inDeck", "In a deck")
    if (a.inDeck == "no") add("inDeck", "Not in a deck")
    num("copies", "Copies", a.copiesOp, a.copies)
    return chips
}

/** The filters with the chip [key] taken off. */
fun removeChip(basic: CollectionFilter, a: AdvancedFilter, key: String): Pair<CollectionFilter, AdvancedFilter> {
    val kind = key.substringBefore(':')
    val value = if (':' in key) key.substringAfter(':') else ""
    var b = basic
    var adv = a
    when (kind) {
        "type" -> b = b.copy(type = "")
        "text" -> b = b.copy(text = "")
        "color" -> b = b.copy(colors = b.colors.filterNot { it.toString() == value }.toSet())
        "rarity" -> b = b.copy(rarities = b.rarities - value)
        "colors" -> adv = adv.copy(colors = emptyList())
        "multicolor" -> adv = adv.copy(multicolor = false)
        "mv" -> adv = adv.copy(mv = "")
        "manaCost" -> adv = adv.copy(manaCost = "")
        "power" -> adv = adv.copy(power = "")
        "toughness" -> adv = adv.copy(toughness = "")
        "loyalty" -> adv = adv.copy(loyalty = "")
        "format" -> adv = adv.copy(format = "")
        "set" -> adv = adv.copy(sets = adv.sets - value)
        "is" -> adv = adv.copy(cardIs = adv.cardIs - value)
        "keywords" -> adv = adv.copy(keywords = "")
        "price" -> adv = adv.copy(priceMin = "", priceMax = "")
        "artist" -> adv = adv.copy(artist = "")
        "flavor" -> adv = adv.copy(flavor = "")
        "finish" -> adv = adv.copy(finishes = adv.finishes - value)
        "condition" -> adv = adv.copy(conditions = adv.conditions - value)
        "language" -> adv = adv.copy(language = "")
        "binder" -> adv = adv.copy(binder = "")
        "inDeck" -> adv = adv.copy(inDeck = "any")
        "copies" -> adv = adv.copy(copies = "")
    }
    return b to adv
}

// ---- Saved filters ----

data class SavedFilter(
    val id: String,
    val name: String,
    val basic: CollectionFilter = CollectionFilter(),
    val advanced: AdvancedFilter = AdvancedFilter()
)

/** [s] as a JSON string, escaped exactly as the web's JSON.stringify does it. */
private fun jsonString(s: String): String {
    val out = StringBuilder("\"")
    for (ch in s) {
        when {
            ch == '"' -> out.append("\\\"")
            ch == '\\' -> out.append("\\\\")
            ch == '\b' -> out.append("\\b")
            ch == '\u000C' -> out.append("\\f")
            ch == '\n' -> out.append("\\n")
            ch == '\r' -> out.append("\\r")
            ch == '\t' -> out.append("\\t")
            ch < ' ' -> out.append(String.format("\\u%04x", ch.code))
            else -> out.append(ch)
        }
    }
    return out.append('"').toString()
}

private fun jsonList(list: List<String>) = list.joinToString(",", "[", "]") { jsonString(it) }

private fun jsonObject(fields: List<Pair<String, String>>) = fields.joinToString(",", "{", "}") { (k, v) -> "${jsonString(k)}:$v" }

private fun basicJson(b: CollectionFilter) = jsonObject(listOf(
    "type" to jsonString(b.type),
    "text" to jsonString(b.text),
    "colors" to jsonList(wubrgOrder(b.colors.map { it.toString() })),
    "rarities" to jsonList(COLLECTION_FILTER_RARITIES.filter { it in b.rarities })
))

// Keys in this order on both apps, so the JSON is the same text.
private fun advancedJson(a: AdvancedFilter) = jsonObject(listOf(
    "colorTarget" to jsonString(a.colorTarget), "colorMode" to jsonString(a.colorMode),
    "colors" to jsonList(wubrgOrder(a.colors)), "multicolor" to a.multicolor.toString(),
    "mvOp" to jsonString(a.mvOp), "mv" to jsonString(a.mv), "manaCost" to jsonString(a.manaCost),
    "powerOp" to jsonString(a.powerOp), "power" to jsonString(a.power),
    "toughnessOp" to jsonString(a.toughnessOp), "toughness" to jsonString(a.toughness),
    "loyaltyOp" to jsonString(a.loyaltyOp), "loyalty" to jsonString(a.loyalty),
    "format" to jsonString(a.format), "legality" to jsonString(a.legality), "sets" to jsonList(a.sets),
    "cardIs" to jsonList(CARD_IS.filter { it in a.cardIs }), "keywords" to jsonString(a.keywords),
    "priceMin" to jsonString(a.priceMin), "priceMax" to jsonString(a.priceMax),
    "artist" to jsonString(a.artist), "flavor" to jsonString(a.flavor),
    "finishes" to jsonList(FINISHES.filter { it in a.finishes }),
    "conditions" to jsonList(CARD_CONDITIONS.filter { it in a.conditions }),
    "language" to jsonString(a.language), "binder" to jsonString(a.binder), "inDeck" to jsonString(a.inDeck),
    "copiesOp" to jsonString(a.copiesOp), "copies" to jsonString(a.copies)
))

/** Saved filters as JSON — the same text the web app writes. */
fun savedFiltersToJson(list: List<SavedFilter>): String = list.joinToString(",", "[", "]") { s ->
    jsonObject(listOf(
        "id" to jsonString(s.id),
        "name" to jsonString(s.name),
        "basic" to basicJson(s.basic),
        "advanced" to advancedJson(s.advanced)
    ))
}

private fun JSONObject.str(key: String, fallback: String = ""): String = opt(key) as? String ?: fallback

private fun JSONObject.strList(key: String): List<String> {
    val arr = optJSONArray(key) ?: return emptyList()
    return (0 until arr.length()).mapNotNull { arr.opt(it) as? String }
}

private fun JSONObject.oneOf(key: String, options: List<String>, fallback: String): String =
    (opt(key) as? String)?.takeIf { it in options } ?: fallback

private fun basicFrom(o: JSONObject) = CollectionFilter(
    type = o.str("type"),
    text = o.str("text"),
    colors = o.strList("colors").filter { it.length == 1 && it[0] in "WUBRG" }.map { it[0] }.toSet(),
    rarities = o.strList("rarities").filter { it in COLLECTION_FILTER_RARITIES }.toSet()
)

private fun advancedFrom(o: JSONObject): AdvancedFilter {
    val d = AdvancedFilter()
    return AdvancedFilter(
        colorTarget = o.oneOf("colorTarget", COLOR_TARGETS, d.colorTarget),
        colorMode = o.oneOf("colorMode", COLOR_MODES, d.colorMode),
        colors = o.strList("colors").filter { it in ADVANCED_COLORS },
        multicolor = o.opt("multicolor") == true,
        mvOp = o.oneOf("mvOp", COMPARE_OPS, d.mvOp),
        mv = o.str("mv"),
        manaCost = o.str("manaCost"),
        powerOp = o.oneOf("powerOp", COMPARE_OPS, d.powerOp),
        power = o.str("power"),
        toughnessOp = o.oneOf("toughnessOp", COMPARE_OPS, d.toughnessOp),
        toughness = o.str("toughness"),
        loyaltyOp = o.oneOf("loyaltyOp", COMPARE_OPS, d.loyaltyOp),
        loyalty = o.str("loyalty"),
        format = o.str("format"),
        legality = o.oneOf("legality", LEGALITIES, d.legality),
        sets = o.strList("sets").map { it.lowercase() },
        cardIs = o.strList("cardIs").filter { it in CARD_IS },
        keywords = o.str("keywords"),
        priceMin = o.str("priceMin"),
        priceMax = o.str("priceMax"),
        artist = o.str("artist"),
        flavor = o.str("flavor"),
        finishes = o.strList("finishes").filter { it in FINISHES },
        conditions = o.strList("conditions").filter { it in CARD_CONDITIONS },
        language = o.str("language"),
        binder = o.str("binder"),
        inDeck = o.oneOf("inDeck", IN_DECK_OPTIONS, d.inDeck),
        copiesOp = o.oneOf("copiesOp", COMPARE_OPS, d.copiesOp),
        copies = o.str("copies")
    )
}

/** Saved filters read back; anything unreadable is skipped, a missing field takes its default. */
fun savedFiltersFromJson(json: String?): List<SavedFilter> {
    if (json.isNullOrBlank()) return emptyList()
    val arr = try { JSONArray(json) } catch (e: Exception) { return emptyList() }
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.opt(i) as? JSONObject ?: return@mapNotNull null
        val id = o.opt("id") as? String ?: return@mapNotNull null
        val name = o.opt("name") as? String ?: return@mapNotNull null
        SavedFilter(
            id = id,
            name = name,
            basic = o.optJSONObject("basic")?.let(::basicFrom) ?: CollectionFilter(),
            advanced = o.optJSONObject("advanced")?.let(::advancedFrom) ?: AdvancedFilter()
        )
    }
}
