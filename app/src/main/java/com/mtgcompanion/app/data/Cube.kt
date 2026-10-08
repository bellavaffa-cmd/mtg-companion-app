package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlin.math.floor

/*
 * Cubes: a list of cards, usually one of each, that a group drafts from. A cube is kept as a deck
 * (Deck.gameMode "CUBE") so it syncs, merges card by card, backs up and shares with friends the way a
 * deck does — no new kind of library item, so nothing on the server changes. Its own settings ride in
 * the deck's JSON under "cube" (CubeSettings). A cube is always:
 *  - Virtual: its cards never count as owned (the copies stay in the collection, in the cube box);
 *  - archived: an app from before cubes shows it with the archived decks and offers it in no picker;
 *  - kept out of the decks list, deck pickers, versions and history by apps that know about cubes.
 *
 * The cube box is a storage place (StoragePlaces.kt) named after the cube. An owned card is "in the
 * cube box" when a copy of it is placed there; the pull list (PullList.kt) finds the rest and "Move
 * into cube box" moves them there, so the collection always says where each copy is. A card nobody
 * owns can be in the cube too: it reads "Not owned" until it's marked as a proxy (the deck's own
 * proxyQuantity, see Proxies.kt).
 *
 * Everything below is pure, so it can be tested. Mirrors the web app's src/decks/cube.ts rule for
 * rule; the shared cases are in cubeVectors.json (CubeTest.kt ↔ tests/decks/cube.test.ts).
 */

/** The deck's game mode that makes it a cube. */
const val CUBE_MODE = "CUBE"

/** The sizes offered when making a cube; any other is "Custom". */
val CUBE_SIZES = listOf(360, 540, 720)
const val CUBE_DEFAULT_SIZE = 360
const val CUBE_MIN_SIZE = 40
const val CUBE_MAX_SIZE = 1500

/** Packs for a draft: 15 cards × 3 packs a seat, 8 seats, unless the cube says otherwise. */
const val CUBE_PACK_SIZE = 15
const val CUBE_PACKS = 3
const val CUBE_SEATS = 8

/**
 * A cube's own settings, as JSON under the deck's "cube" key:
 *   "cube": { "size": 360, "singleton": true, "boxPlaceId": "…", "packSize": 15, "packs": 3, "seats": 8 }
 * Optional keys are left out when not set. The web app's CubeSettings, field for field.
 */
data class CubeSettings(
    val size: Int = CUBE_DEFAULT_SIZE,
    val singleton: Boolean = true,
    /** The storage place that is the cube box; null until one is made. */
    val boxPlaceId: String? = null,
    val packSize: Int? = null,
    val packs: Int? = null,
    val seats: Int? = null
)

val Deck.isCube: Boolean get() = gameMode == CUBE_MODE

/** The cube's settings, or the defaults for one saved without them. */
val Deck.cubeSettings: CubeSettings get() = cube ?: CubeSettings()

/** A size the cube can be: within [CUBE_MIN_SIZE]..[CUBE_MAX_SIZE]. */
fun cubeSize(size: Int): Int = size.coerceIn(CUBE_MIN_SIZE, CUBE_MAX_SIZE)

/** A new, empty cube. */
fun newCube(id: String, name: String, size: Int, singleton: Boolean, now: Long): Deck = Deck(
    id = id,
    name = name.trim().ifEmpty { "My cube" },
    gameMode = CUBE_MODE,
    ownership = DeckOwnership.VIRTUAL.name,
    createdAt = now,
    archived = true,
    cube = CubeSettings(size = cubeSize(size), singleton = singleton)
)

/** [deck] kept a cube: Virtual and archived, whatever an older app did to it. The same object when it is. */
fun asCube(deck: Deck): Deck =
    if (deck.ownership == DeckOwnership.VIRTUAL.name && deck.archived == true && deck.cube != null) deck
    else deck.copy(ownership = DeckOwnership.VIRTUAL.name, archived = true, cube = deck.cubeSettings)

/**
 * [theirs] with [source]'s cube settings put back when [theirs] was saved by an app that doesn't know
 * about cubes (no "cube" key) — the same object otherwise.
 */
fun keepCubeFromOlderApp(source: Deck, theirs: Deck): Deck =
    if (theirs.cube != null || source.cube == null) theirs else theirs.copy(cube = source.cube)

private fun <T> pickCube(base: T, mine: T, theirs: T, minePreferred: Boolean): T = when {
    mine == theirs -> mine
    mine == base -> theirs
    theirs == base -> mine
    else -> if (minePreferred) mine else theirs
}

/** Two devices' cube settings, field by field: whoever changed one, or the more recent edit. Null when neither side has any. */
fun mergeCubeSettings(base: CubeSettings?, mine: CubeSettings?, theirs: CubeSettings?, minePreferred: Boolean): CubeSettings? {
    if (mine == null && theirs == null) return null
    val m = mine ?: theirs!!
    val t = theirs ?: mine!!
    val b = base ?: t
    return CubeSettings(
        size = pickCube(b.size, m.size, t.size, minePreferred),
        singleton = pickCube(b.singleton, m.singleton, t.singleton, minePreferred),
        boxPlaceId = pickCube(b.boxPlaceId, m.boxPlaceId, t.boxPlaceId, minePreferred),
        packSize = pickCube(b.packSize, m.packSize, t.packSize, minePreferred),
        packs = pickCube(b.packs, m.packs, t.packs, minePreferred),
        seats = pickCube(b.seats, m.seats, t.seats, minePreferred)
    )
}

// ---- What the balance needs to know about a card ----

/**
 * One card's facts for the balance, the fill suggestions and the filters: its colours (W U B R G),
 * type line, mana value, rarity, set code, price in US dollars, rules text and the mana it makes.
 */
data class CubeCard(
    val id: String,
    val name: String,
    val colors: List<String> = emptyList(),
    val typeLine: String = "",
    val cmc: Double = 0.0,
    val rarity: String = "",
    val set: String = "",
    val usd: Double? = null,
    val text: String = "",
    val producedMana: List<String> = emptyList()
)

/** A Scryfall card's facts for the cube. */
fun cubeCardOf(card: ScryfallCard): CubeCard = CubeCard(
    id = card.id,
    name = card.name,
    colors = cardColours(card),
    typeLine = card.typeLine ?: card.cardFaces?.firstOrNull()?.typeLine ?: "",
    cmc = card.cmc ?: 0.0,
    rarity = card.rarity.orEmpty().lowercase(),
    set = card.set.orEmpty().lowercase(),
    usd = card.prices?.usd?.toDoubleOrNull() ?: card.prices?.usdFoil?.toDoubleOrNull(),
    text = listOfNotNull(card.oracleText, *card.cardFaces.orEmpty().map { it.oracleText }.toTypedArray()).joinToString("\n"),
    producedMana = card.producedMana.orEmpty()
)

/** A card in the cube and how many copies. */
data class CubeLine(val card: CubeCard, val qty: Int = 1)

private val WUBRG = listOf("W", "U", "B", "R", "G")

/** The balance's colour groups, in order: each colour, then multicolour, colourless and lands. */
val CUBE_GROUPS = listOf("W", "U", "B", "R", "G", "M", "C", "L")
val CUBE_GROUP_LABELS = mapOf(
    "W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green",
    "M" to "Multicolour", "C" to "Colourless", "L" to "Lands"
)

/** Share of the cube each group should have, in thousandths. */
private val GROUP_WEIGHTS = listOf(140, 140, 140, 140, 140, 110, 80, 110)

private fun frontType(typeLine: String) = typeLine.split(" // ")[0]
private fun hasWord(line: String, word: String) = Regex("\\b$word\\b").containsMatchIn(line)

/** Which group a card is in (a key of [CUBE_GROUP_LABELS]): a land, one colour, several, or none. */
fun cubeGroupOf(card: CubeCard): String {
    if (hasWord(frontType(card.typeLine), "Land")) return "L"
    val colours = WUBRG.filter { it in card.colors }
    return when {
        colours.isEmpty() -> "C"
        colours.size > 1 -> "M"
        else -> colours.single()
    }
}

private fun isLandCard(card: CubeCard) = cubeGroupOf(card) == "L"

/** The card types the type mix counts, in order, and their names. */
val CUBE_TYPES = listOf("creature", "planeswalker", "instant", "sorcery", "artifact", "enchantment", "battle", "land", "other")
val CUBE_TYPE_LABELS = mapOf(
    "creature" to "Creatures", "planeswalker" to "Planeswalkers", "instant" to "Instants", "sorcery" to "Sorceries",
    "artifact" to "Artifacts", "enchantment" to "Enchantments", "battle" to "Battles", "land" to "Lands", "other" to "Other"
)

/** A card's main type: a creature first (an artifact creature is a creature), then the rest in [CUBE_TYPES] order. */
fun cubeTypeOf(card: CubeCard): String {
    val line = frontType(card.typeLine)
    for (t in listOf("Creature", "Planeswalker", "Instant", "Sorcery", "Artifact", "Enchantment", "Battle", "Land")) {
        if (hasWord(line, t)) return t.lowercase()
    }
    return "other"
}

// ---- Roles ----

/** The jobs the balance counts, in order, with their share of the cube in thousandths. */
val CUBE_ROLES = listOf("removal", "fixing", "draw", "counter")
val CUBE_ROLE_LABELS = mapOf("removal" to "Removal", "fixing" to "Fixing", "draw" to "Card draw", "counter" to "Counterspells")
private val ROLE_WEIGHTS = mapOf("removal" to 120, "fixing" to 80, "draw" to 60, "counter" to 30)

private val REMOVAL = Regex(
    "(destroy|exile) (target|each|all|up to)|deals? (\\d+|x) damage to (any target|target|each creature)|gets? -\\d+/-\\d+|fights? (target|another|up to)|return target [a-z ]*(creature|permanent) to its owner's hand",
    RegexOption.IGNORE_CASE
)
private val FIXING_TEXT = Regex(
    "mana of any (one )?colou?r|search your library for [^.]*(basic land|plains|island|swamp|mountain|forest)|add \\{[wubrg]\\} or \\{[wubrg]\\}",
    RegexOption.IGNORE_CASE
)
private val DRAW = Regex("draws? (a|an|two|three|four|five|x|\\d+) (additional )?cards?", RegexOption.IGNORE_CASE)
private val COUNTER = Regex("counter target", RegexOption.IGNORE_CASE)

/** The jobs [card] does, in [CUBE_ROLES] order: read from its rules text (and, for a land, the mana it makes). */
fun cubeRolesOf(card: CubeCard): List<String> {
    val out = mutableListOf<String>()
    if (REMOVAL.containsMatchIn(card.text)) out += "removal"
    val colours = card.producedMana.filter { it in WUBRG }.distinct().size
    if ((isLandCard(card) && colours >= 2) || FIXING_TEXT.containsMatchIn(card.text)) out += "fixing"
    if (DRAW.containsMatchIn(card.text)) out += "draw"
    if (COUNTER.containsMatchIn(card.text)) out += "counter"
    return out
}

// ---- Targets and the balance ----

/** [total] split in proportion to [weights], whole numbers that add up to it: the largest remainders get the rest, ties in order. */
fun splitByWeight(total: Int, weights: List<Int>): List<Int> {
    if (total <= 0) return weights.map { 0 }
    val sum = weights.sum()
    val base = weights.map { total * it / sum }.toMutableList()
    val rest = weights.map { total * it % sum }
    var left = total - base.sum()
    for (i in weights.indices.sortedWith(compareByDescending<Int> { rest[it] }.thenBy { it })) {
        if (left <= 0) break
        base[i]++
        left--
    }
    return base
}

/** How many cards of each group a cube of [size] aims for (keys of [CUBE_GROUP_LABELS]). */
fun cubeTargets(size: Int): Map<String, Int> = CUBE_GROUPS.zip(splitByWeight(size, GROUP_WEIGHTS)).toMap()

/** How far a count can be from its target before the balance says so. */
fun cubeTolerance(target: Int): Int = maxOf(2, (target + 9) / 10)

private fun perMille(n: Int, pm: Int) = (n * pm + 500) / 1000

/** The mana value buckets of the curve (non-land cards), and their share in thousandths. */
val CUBE_CURVE = listOf("1", "2", "3", "4", "5", "6+")
private val CURVE_WEIGHTS = listOf(120, 250, 230, 180, 120, 100)

private fun curveKey(cmc: Double): String {
    val mv = floor(cmc).toInt()
    return when {
        mv <= 1 -> "1"
        mv >= 6 -> "6+"
        else -> mv.toString()
    }
}

/** A count against its target ([target] null where there's none). */
data class CubeCount(val key: String, val label: String, val count: Int, val target: Int?)

data class CubeBalance(
    val total: Int,
    val size: Int,
    val groups: List<CubeCount>,
    val curve: List<CubeCount>,
    /** Average mana value of the non-land cards, to two places; 0 with none. */
    val averageMv: Double,
    val types: List<CubeCount>,
    val roles: List<CubeCount>,
    /** What's off, most important first: "12 cards short of 360", "Green is 12 short"… */
    val warnings: List<String>
)

private fun groupVerb(key: String) = if (key == "L") "are" else "is"

/** The cube's balance: counts per colour group, curve, types and roles against simple targets for [size], and what's off. */
fun cubeBalance(lines: List<CubeLine>, size: Int, singleton: Boolean): CubeBalance {
    val total = lines.sumOf { it.qty }
    val targets = cubeTargets(size)
    val groupCounts = CUBE_GROUPS.associateWith { 0 }.toMutableMap()
    val typeCounts = CUBE_TYPES.associateWith { 0 }.toMutableMap()
    val roleCounts = CUBE_ROLES.associateWith { 0 }.toMutableMap()
    val curveCounts = CUBE_CURVE.associateWith { 0 }.toMutableMap()
    var nonLand = 0
    var mvSum = 0.0
    for (l in lines) {
        if (l.qty <= 0) continue
        val g = cubeGroupOf(l.card)
        groupCounts[g] = groupCounts.getValue(g) + l.qty
        val t = cubeTypeOf(l.card)
        typeCounts[t] = typeCounts.getValue(t) + l.qty
        for (r in cubeRolesOf(l.card)) roleCounts[r] = roleCounts.getValue(r) + l.qty
        if (g != "L") {
            nonLand += l.qty
            mvSum += l.card.cmc * l.qty
            val k = curveKey(l.card.cmc)
            curveCounts[k] = curveCounts.getValue(k) + l.qty
        }
    }
    val curveTargets = splitByWeight(perMilleBase(size, targets), CURVE_WEIGHTS)
    val groups = CUBE_GROUPS.map { CubeCount(it, CUBE_GROUP_LABELS.getValue(it), groupCounts.getValue(it), targets.getValue(it)) }
    val curve = CUBE_CURVE.mapIndexed { i, k -> CubeCount(k, k, curveCounts.getValue(k), curveTargets[i]) }
    val creatureTarget = perMille(size - targets.getValue("L"), 450)
    val types = CUBE_TYPES.filter { typeCounts.getValue(it) > 0 || it == "creature" }
        .map { CubeCount(it, CUBE_TYPE_LABELS.getValue(it), typeCounts.getValue(it), if (it == "creature") creatureTarget else null) }
    val roles = CUBE_ROLES.map { CubeCount(it, CUBE_ROLE_LABELS.getValue(it), roleCounts.getValue(it), perMille(size, ROLE_WEIGHTS.getValue(it))) }

    val warnings = mutableListOf<String>()
    fun cards(n: Int) = if (n == 1) "1 card" else "$n cards"
    if (total < size) warnings += "${cards(size - total)} short of $size"
    else if (total > size) warnings += "${cards(total - size)} over $size"
    if (singleton) {
        val byName = HashMap<String, Int>()
        for (l in lines) byName[l.card.name.trim().lowercase()] = (byName[l.card.name.trim().lowercase()] ?: 0) + l.qty
        val doubled = byName.values.count { it > 1 }
        if (doubled == 1) warnings += "1 card has more than one copy"
        else if (doubled > 1) warnings += "$doubled cards have more than one copy"
    }
    for (g in groups) {
        val target = g.target ?: continue
        val diff = g.count - target
        val tol = cubeTolerance(target)
        if (diff <= -tol) warnings += "${g.label} ${groupVerb(g.key)} ${-diff} short"
        else if (diff >= tol) warnings += "${g.label} ${groupVerb(g.key)} $diff over"
    }
    val creatures = typeCounts.getValue("creature")
    if (creatureTarget - creatures >= cubeTolerance(creatureTarget)) warnings += "Creatures are ${creatureTarget - creatures} short"
    for (r in roles) {
        val target = r.target ?: continue
        if (target - r.count >= cubeTolerance(target)) warnings += "${r.label} ${if (r.key == "counter") "are" else "is"} ${target - r.count} short"
    }
    val avg = if (nonLand == 0) 0.0 else Math.round(mvSum / nonLand * 100) / 100.0
    return CubeBalance(total, size, groups, curve, avg, types, roles, warnings)
}

/** The non-land cards a cube of [size] aims for. */
private fun perMilleBase(size: Int, targets: Map<String, Int>) = size - targets.getValue("L")

// ---- Fill from collection ----

private val RARITY_RANK = mapOf("mythic" to 4, "rare" to 3, "uncommon" to 2, "common" to 1)

private val bestFirst = compareByDescending<CubeCard> { RARITY_RANK[it.rarity] ?: 0 }
    .thenByDescending { it.usd ?: 0.0 }
    .thenBy { it.name.lowercase() }

private fun nameKey(name: String) = name.trim().lowercase()

/**
 * "Fill from collection": cards the user owns to bring the cube up to [size], balanced by colour —
 * each group short of its target gets one in turn (W U B R G, multicolour, colourless, lands), the
 * best first (rarer, then dearer, then A–Z), until the groups reach their targets or the cube is full.
 * Cards already in the cube (by name) and basic lands aren't suggested; each name once.
 */
fun cubeFill(cube: List<CubeLine>, owned: List<CubeCard>, size: Int): List<CubeCard> {
    val have = cube.map { nameKey(it.card.name) }.toHashSet()
    var room = size - cube.sumOf { it.qty }
    if (room <= 0) return emptyList()
    val counts = CUBE_GROUPS.associateWith { 0 }.toMutableMap()
    for (l in cube) counts[cubeGroupOf(l.card)] = counts.getValue(cubeGroupOf(l.card)) + l.qty
    val targets = cubeTargets(size)
    val need = CUBE_GROUPS.associateWith { maxOf(0, targets.getValue(it) - counts.getValue(it)) }.toMutableMap()
    val seen = HashSet<String>()
    val pools = CUBE_GROUPS.associateWith { ArrayDeque<CubeCard>() }
    for (c in owned.sortedWith(bestFirst)) {
        val key = nameKey(c.name)
        if (key in have || key in seen || hasWord(frontType(c.typeLine), "Basic")) continue
        seen += key
        pools.getValue(cubeGroupOf(c)).addLast(c)
    }
    val out = mutableListOf<CubeCard>()
    var progress = true
    while (room > 0 && progress) {
        progress = false
        for (g in CUBE_GROUPS) {
            if (room <= 0) break
            if (need.getValue(g) <= 0) continue
            val next = pools.getValue(g).removeFirstOrNull() ?: continue
            out += next
            need[g] = need.getValue(g) - 1
            room--
            progress = true
        }
    }
    return out
}

// ---- Add from collection: the filters ----

/** The filters of "Add from collection". Empty sets and blank text don't filter. */
data class CubeFilter(
    val query: String = "",
    /** Colour groups (keys of [CUBE_GROUP_LABELS]). */
    val groups: Set<String> = emptySet(),
    val rarities: Set<String> = emptySet(),
    /** Words that must all be in the type line. */
    val type: String = "",
    /** A set code. */
    val set: String = "",
    val minUsd: Double? = null,
    val maxUsd: Double? = null
)

/** Whether [card] passes [f]. A card with no price fails a price filter. */
fun cubeFilterMatches(f: CubeFilter, card: CubeCard): Boolean {
    if (f.query.isNotBlank() && !card.name.contains(f.query.trim(), ignoreCase = true)) return false
    if (f.groups.isNotEmpty() && cubeGroupOf(card) !in f.groups) return false
    if (f.rarities.isNotEmpty() && card.rarity.lowercase() !in f.rarities) return false
    val words = f.type.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.any { it !in card.typeLine.lowercase() }) return false
    if (f.set.isNotBlank() && !card.set.equals(f.set.trim(), ignoreCase = true)) return false
    if (f.minUsd != null && (card.usd == null || card.usd < f.minUsd)) return false
    if (f.maxUsd != null && (card.usd == null || card.usd > f.maxUsd)) return false
    return true
}

// ---- Packs for a draft ----

/** A small seeded random number source (mulberry32), the same in both apps, so a seed deals the same packs. */
class CubeRandom(seed: Int) {
    private var a = seed
    fun next(): Double {
        a += 0x6D2B79F5
        var t = (a xor (a ushr 15)) * (a or 1)
        t = (t + (t xor (t ushr 7)) * (t or 61)) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
    }
}

/** [items] shuffled by [seed] (Fisher–Yates from the end). */
fun <T> cubeShuffle(items: List<T>, seed: Int): List<T> {
    val out = items.toMutableList()
    val rnd = CubeRandom(seed)
    for (i in out.size - 1 downTo 1) {
        val j = (rnd.next() * (i + 1)).toInt()
        val x = out[i]; out[i] = out[j]; out[j] = x
    }
    return out
}

/**
 * The packs for a draft: [seats] seats × [packs] packs × [packSize] cards, dealt from [pool] (one
 * item per copy) shuffled by [seed]. Seat s's pack p is the shuffled pool's cards from
 * ((p × seats) + s) × packSize. [short]: how many cards the pool is short (no packs then).
 */
data class CubePacks(val seats: List<List<List<String>>>, val needed: Int, val short: Int)

fun cubePacks(pool: List<String>, seats: Int, packs: Int, packSize: Int, seed: Int): CubePacks {
    val s = seats.coerceAtLeast(1)
    val p = packs.coerceAtLeast(1)
    val n = packSize.coerceAtLeast(1)
    val needed = s * p * n
    if (pool.size < needed) return CubePacks(emptyList(), needed, needed - pool.size)
    val dealt = cubeShuffle(pool, seed)
    val out = (0 until s).map { seat ->
        (0 until p).map { pack ->
            val from = (pack * s + seat) * n
            dealt.subList(from, from + n).toList()
        }
    }
    return CubePacks(out, needed, 0)
}

/** The cube's cards as a pool for packs: each copy once, in the cube's order (scryfall ids). */
fun cubePool(cube: Deck): List<String> = cube.cards.flatMap { e -> List(e.quantity.coerceAtLeast(0)) { e.scryfallId } }

/**
 * A draft or sealed deck (Limited.kt) made from packs of a cube: its pool (the sideboard) is
 * [cardIds], copies added together, and the main deck starts empty — the deck screen's draft and sealed
 * tools take it from there. Virtual: the cards are the cube's.
 */
fun limitedDeckFromCube(cube: Deck, cardIds: List<String>, id: String, name: String, note: String, now: Long): Deck {
    val byId = cube.cards.associateBy { it.scryfallId }
    val counts = LinkedHashMap<String, Int>()
    for (c in cardIds) if (c in byId) counts[c] = (counts[c] ?: 0) + 1
    val pool = counts.map { (cid, n) -> byId.getValue(cid).copy(quantity = n, proxyQuantity = null, replaceable = false, categories = null) }
    return Deck(
        id = id,
        name = name,
        gameMode = GameMode.LIMITED.name,
        ownership = DeckOwnership.VIRTUAL.name,
        createdAt = now,
        sideboard = pool,
        description = note.takeIf { it.isNotBlank() }
    )
}

// ---- The plain list: export and import ----

/** The cube as a plain list, a card a line (a card with two copies on two lines), A–Z — what CubeCobra imports. */
fun cubeListText(cards: List<Pair<String, Int>>): String =
    cards.filter { it.second > 0 }
        .sortedWith(compareBy<Pair<String, Int>> { it.first.lowercase() }.thenBy { it.first })
        .flatMap { (name, n) -> List(n) { name } }
        .joinToString("\n")

/** One card of an imported list: its name and how many copies. */
data class CubeListLine(val name: String, val qty: Int)

private val HEADER = Regex("^(mainboard|maybeboard|sideboard|deck|commander|main|maybe|cube)\\s*:?$", RegexOption.IGNORE_CASE)
private val COUNT = Regex("^(\\d+)\\s*[xX]?\\s+(.+)$")
private val FOIL_MARK = Regex("\\s+\\*[A-Za-z]+\\*$")
private val SET_MARK = Regex("\\s+\\([A-Za-z0-9]{2,6}\\)(\\s+[A-Za-z0-9★-]+)?$")

/** The first field of a CSV line, unquoted. */
private fun csvFields(line: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            quoted && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
            ch == '"' -> quoted = !quoted
            ch == ',' && !quoted -> { out += cur.toString(); cur.setLength(0) }
            else -> cur.append(ch)
        }
        i++
    }
    out += cur.toString()
    return out
}

/**
 * A cube list pasted or read from a file: CubeCobra's plain text (a name a line), a "2 Name" or
 * "2x Name (SET) 123" list, or CubeCobra's CSV export (its "name" column; maybeboard rows left out).
 * Blank lines, "#" and "//" comments and headers like "Mainboard" are skipped; the same card on two
 * lines adds up, under the first spelling, in the order first seen.
 */
fun parseCubeList(text: String): List<CubeListLine> {
    val lines = text.split(Regex("\r?\n")).map { it.trim() }.filter { it.isNotEmpty() }
    val names = mutableListOf<Pair<String, Int>>()
    val first = lines.firstOrNull()
    if (first != null && first.lowercase().startsWith("name,")) {
        val header = csvFields(first).map { it.trim().lowercase() }
        val maybe = header.indexOf("maybeboard")
        for (l in lines.drop(1)) {
            val f = csvFields(l)
            if (maybe >= 0 && f.getOrNull(maybe)?.trim()?.lowercase() == "true") continue
            val name = f.firstOrNull()?.trim().orEmpty()
            if (name.isNotEmpty()) names += name to 1
        }
    } else {
        for (l in lines) {
            if (l.startsWith("#") || l.startsWith("//") || HEADER.matches(l)) continue
            var qty = 1
            var name = l
            COUNT.matchEntire(l)?.let { m -> qty = m.groupValues[1].toInt(); name = m.groupValues[2] }
            name = name.replace(FOIL_MARK, "").replace(SET_MARK, "").trim()
            if (name.isEmpty() || qty <= 0) continue
            names += name to qty
        }
    }
    val out = LinkedHashMap<String, CubeListLine>()
    for ((name, qty) in names) {
        val k = nameKey(name)
        val had = out[k]
        out[k] = if (had == null) CubeListLine(name, qty) else had.copy(qty = had.qty + qty)
    }
    return out.values.toList()
}

// ---- Building the cube ----

/** What adding cards did: the cube after, how many went in, and the names left out as already there (singleton). */
data class CubeAdd(val cube: Deck, val added: Int, val skipped: List<String>)

/**
 * [entries] added to [cube]. In a singleton cube a card whose name is already there is left out (and
 * named in [CubeAdd.skipped]), and each is one copy; otherwise copies of a printing add up.
 */
fun addToCube(cube: Deck, entries: List<DeckCardEntry>): CubeAdd {
    val singleton = cube.cubeSettings.singleton
    val cards = cube.cards.toMutableList()
    val names = cards.map { nameKey(it.name) }.toHashSet()
    val skipped = mutableListOf<String>()
    var added = 0
    for (e in entries) {
        if (e.quantity <= 0) continue
        if (singleton) {
            if (nameKey(e.name) in names) { skipped += e.name; continue }
            cards += e.copy(quantity = 1, replaceable = false, categories = null)
            names += nameKey(e.name)
            added++
        } else {
            val i = cards.indexOfFirst { it.scryfallId == e.scryfallId }
            if (i >= 0) cards[i] = cards[i].copy(quantity = cards[i].quantity + e.quantity)
            else cards += e.copy(replaceable = false, categories = null)
            names += nameKey(e.name)
            added += e.quantity
        }
    }
    return CubeAdd(if (added == 0) cube else cube.copy(cards = cards), added, skipped)
}

/** [cube] without the card [scryfallId]. */
fun removeFromCube(cube: Deck, scryfallId: String): Deck = cube.copy(cards = cube.cards.filterNot { it.scryfallId == scryfallId })

/** [cube] with the card [scryfallId] marked as a proxy (every copy) or not. */
fun markCubeProxy(cube: Deck, scryfallId: String, proxy: Boolean): Deck =
    cube.copy(cards = cube.cards.map { if (it.scryfallId == scryfallId) it.copy(proxyQuantity = if (proxy) it.quantity else null) else it })

// ---- The cube box ----

/** Where one of the cube's cards stands: in the box, owned somewhere else, a proxy, or not owned. */
enum class CubeCardState { IN_BOX, OWNED, PROXY, NOT_OWNED }

data class CubeCardStatus(
    val scryfallId: String,
    val name: String,
    val qty: Int,
    /** Copies placed in the cube box. */
    val inBox: Int,
    /** Copies owned outside the box. */
    val elsewhere: Int,
    val proxies: Int,
    val state: CubeCardState,
    /** Where the copies outside the box are: "Red box ×2 · No place (Unsorted) ×1"; "" for none. */
    val where: String
)

private fun ownedOnly(collections: List<Collection>) = collections.filter { it.kind != CollectionType.WISHLIST }

/** The cube box, when the cube has one and it's still a storage place. */
fun cubeBoxOf(cube: Deck, collections: List<Collection>): StoragePlace? {
    val id = cube.cube?.boxPlaceId ?: return null
    return placesOf(collections).firstOrNull { it.id == id }
}

private class CubeHeld(var inBox: Int = 0, var elsewhere: Int = 0, val where: LinkedHashMap<String, Int> = LinkedHashMap())

/** Each of the cube's cards with where it stands (see [CubeCardState]), in the cube's order. */
fun cubeStatus(cube: Deck, collections: List<Collection>): List<CubeCardStatus> {
    val box = cubeBoxOf(cube, collections)?.id
    val places = placesOf(collections).associateBy { it.id }
    val held = HashMap<String, CubeHeld>()
    for (c in ownedOnly(collections)) for (e in c.entries) {
        val copies = e.quantity + e.foilQuantity
        if (copies <= 0) continue
        val h = held.getOrPut(nameKey(e.name)) { CubeHeld() }
        var placed = 0
        for (line in placedCopies(e)) {
            val place = places[line.placeId] ?: continue
            placed += line.qty
            if (line.placeId == box) h.inBox += line.qty
            else { h.elsewhere += line.qty; h.where[place.name] = (h.where[place.name] ?: 0) + line.qty }
        }
        val loose = copies - placed
        if (loose > 0) {
            h.elsewhere += loose
            val label = "No place (${c.name})"
            h.where[label] = (h.where[label] ?: 0) + loose
        }
    }
    return cube.cards.map { e ->
        val h = held[nameKey(e.name)] ?: CubeHeld()
        val proxies = proxyCopies(cube, e)
        val inBox = minOf(h.inBox, e.quantity)
        val state = when {
            inBox >= e.quantity -> CubeCardState.IN_BOX
            h.elsewhere > 0 -> CubeCardState.OWNED
            proxies > 0 -> CubeCardState.PROXY
            else -> CubeCardState.NOT_OWNED
        }
        CubeCardStatus(e.scryfallId, e.name, e.quantity, inBox, h.elsewhere, proxies, state, h.where.entries.joinToString(" · ") { "${it.key} ×${it.value}" })
    }
}

/** [collections] as if the copies in [placeId] weren't there — so the pull list doesn't fetch what's in the box already. */
private fun withoutPlace(collections: List<Collection>, placeId: String): List<Collection> = collections.map { c ->
    if (c.kind == CollectionType.WISHLIST || c.entries.none { e -> e.places.orEmpty().any { it.placeId == placeId } }) c
    else c.copy(entries = c.entries.mapNotNull { e ->
        val lines = placedCopies(e)
        val there = lines.filter { it.placeId == placeId }
        if (there.isEmpty()) return@mapNotNull e
        val plain = there.filter { !it.isFoil }.sumOf { it.qty }
        val foil = there.filter { it.isFoil }.sumOf { it.qty }
        val left = e.copy(quantity = e.quantity - plain, foilQuantity = e.foilQuantity - foil, places = lines.filter { it.placeId != placeId })
        if (left.quantity + left.foilQuantity <= 0) null else left
    })
}

/**
 * The cube's pull list: every copy still to go into the cube box, with where to fetch it from, in
 * walking order (PullList.kt) — not the copies in the box already, nor the proxies. Copies only another
 * deck holds are listed but stay there; the cards not owned come last.
 */
fun cubePullList(cube: Deck, collections: List<Collection>, decks: List<Deck>): PullListData {
    val box = cubeBoxOf(cube, collections)?.id
    val inBox = HashMap<String, Int>()
    if (box != null) for (c in ownedOnly(collections)) for (e in c.entries) for (line in placedCopies(e)) {
        if (line.placeId == box) inBox[nameKey(e.name)] = (inBox[nameKey(e.name)] ?: 0) + line.qty
    }
    val cards = cube.cards.mapNotNull { e ->
        val there = inBox[nameKey(e.name)] ?: 0
        val take = minOf(there, e.quantity)
        inBox[nameKey(e.name)] = there - take
        val need = e.quantity - take - proxyCopies(cube, e)
        if (need <= 0) null else e.copy(quantity = need, proxyQuantity = null)
    }
    val wanted = cube.copy(cards = cards, ownership = DeckOwnership.VIRTUAL.name, commander = null, partnerCommander = null)
    return pullList(wanted, if (box != null) withoutPlace(collections, box) else collections, decks.filterNot { it.isCube })
}

/**
 * "Move into cube box": the copies of the [ticked] rows go into the box [boxPlaceId] — a copy in a
 * place moves from there, one with no place yet is put there. Copies in another deck, basic lands and
 * cards not owned stay as they are. Also how many copies moved.
 */
fun moveIntoCubeBox(list: PullListData, ticked: Set<String>, collections: List<Collection>, boxPlaceId: String): Pair<List<Collection>, Int> {
    var cols = collections
    var moved = 0
    val to = Spot(boxPlaceId)
    fun change(collectionId: String, scryfallId: String, f: (CollectionEntry) -> Pair<CollectionEntry, Int>) {
        cols = cols.map { c ->
            if (c.id != collectionId) c
            else c.copy(entries = c.entries.map { e ->
                if (e.scryfallId != scryfallId) e
                else { val (out, n) = f(e); moved += n; out }
            })
        }
    }
    for (row in list.groups.flatMap { it.rows }) {
        if (row.key !in ticked) continue
        when (val src = row.source) {
            is PullSource.Place -> change(src.collectionId, src.scryfallId) { e -> moveCopies(e, src.line, to, row.qty) }
            is PullSource.Loose -> change(src.collectionId, src.scryfallId) { e -> placeCopies(e, to, row.qty, src.foil) }
            else -> Unit
        }
    }
    return (if (moved == 0) collections else cols) to moved
}

/** A new storage place to be the cube box: a box named after the cube, sized to it, sorted by colour. */
fun cubeBoxPlace(cube: Deck, id: String, now: Long): StoragePlace = storagePlace(
    StoragePlace(
        id = id,
        name = "${cube.name} box",
        kind = PlaceKind.BOX.name,
        sortRule = SortRule.COLOUR.name,
        capacity = cube.cubeSettings.size,
        createdAt = now
    )
)
