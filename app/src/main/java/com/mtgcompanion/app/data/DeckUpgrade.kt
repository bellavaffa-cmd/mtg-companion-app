package com.mtgcompanion.app.data

/*
 * "Upgrade with my cards": swaps for a deck — cut X, add Y — where Y is a card the user already owns,
 * legal in the deck, doing the same job (RoleTags) and better for it: more played with this commander
 * on EDHREC, or, with no EDHREC numbers (offline, or not a commander deck), a better EDHREC rank for
 * its mana value. Never cuts the commander, a land, a card tagged "keep" or a piece of a combo the deck
 * has; never adds a card that would push the deck's Commander bracket up — those are kept apart,
 * marked, and hidden by default.
 *
 * Pure, so both apps run the same cases: deckUpgradeVectors.json (test resources) is the web app's
 * tests/decks/deckUpgradeVectors.json, run there by tests/decks/deckUpgrade.test.ts. The web app's
 * src/decks/deckUpgrade.ts, rule for rule.
 */

/** The jobs swaps are matched on, the most important first: a card doing several counts for the first. */
val UPGRADE_ROLES = listOf(
    "ramp", "draw", "removal", "board-wipe", "counterspell", "tutor", "protection", "recursion", "reanimate",
    "sacrifice-outlet", "tokens", "graveyard-hate", "burn", "lifegain"
)

/** "Both are …" */
private val ROLE_WORDS = mapOf(
    "ramp" to "ramp", "draw" to "card draw", "removal" to "removal", "board-wipe" to "board wipes", "counterspell" to "counterspells",
    "tutor" to "tutors", "protection" to "protection", "recursion" to "recursion", "reanimate" to "reanimation",
    "sacrifice-outlet" to "sacrifice outlets", "tokens" to "token makers", "graveyard-hate" to "graveyard hate", "burn" to "burn", "lifegain" to "lifegain"
)

fun upgradeRoleWord(role: String): String = ROLE_WORDS[role] ?: role

/** How much better the card coming in has to be: EDHREC percentage points, or offline score points. */
const val UPGRADE_MIN_GAIN = 10.0
/** Swaps shown, and swaps that would raise the bracket kept aside. */
const val MAX_UPGRADE_SWAPS = 15
const val MAX_BRACKET_SWAPS = 5
/** A user tag that says "never suggest cutting this", compared lower-case. */
val KEEP_TAGS = setOf("keep", "must keep", "must-keep", "pinned")

/** What both sides of a swap know about a card. [inclusion]: whole percent of this commander's EDHREC decks; null when not on its page. */
interface UpgradeCard {
    val name: String
    val scryfallId: String
    val typeLine: String?
    val cmc: Double?
    /** RoleTags ids. */
    val roles: List<String>
    val usd: Double?
    val gameChanger: Boolean
    /** Scryfall's edhrec_rank. */
    val edhrecRank: Int?
    val inclusion: Int?
}

/** A card in the deck: a cut candidate unless it's the commander, kept, or a combo piece. */
data class UpgradeDeckCard(
    override val name: String,
    override val scryfallId: String = name,
    override val typeLine: String? = null,
    override val cmc: Double? = null,
    override val roles: List<String> = emptyList(),
    override val usd: Double? = null,
    override val gameChanger: Boolean = false,
    override val edhrecRank: Int? = null,
    override val inclusion: Int? = null,
    val commander: Boolean = false,
    /** Marked as a cut candidate: cut first, for any gain. */
    val replaceable: Boolean = false,
    /** Tagged "keep" ([KEEP_TAGS]): never cut. */
    val keep: Boolean = false,
    /** Part of a combo the deck has: cutting it would break it. */
    val comboPiece: Boolean = false
) : UpgradeCard

/** A card the user owns that isn't in the deck. */
data class UpgradeOwnedCard(
    override val name: String,
    override val scryfallId: String = name,
    override val typeLine: String? = null,
    override val cmc: Double? = null,
    override val roles: List<String> = emptyList(),
    override val usd: Double? = null,
    override val gameChanger: Boolean = false,
    override val edhrecRank: Int? = null,
    override val inclusion: Int? = null,
    /** Colour identity letters ("RG", "" colourless); null when not known yet. */
    val identity: String? = null,
    val legal: Boolean = true,
    /** Adding it completes a combo the deck is one card short of. */
    val completesCombo: Boolean = false,
    /** Copies free to take: in binders and boxes, less those other decks are waiting on. */
    val spare: Int = 0,
    /** With no spare copy, the deck that has (or is waiting on) the only one. */
    val heldBy: String? = null,
    /** Where a spare copy is: "Red box › Red", "Duskmourn binder p3 s5", "Unsorted". */
    val where: String? = null,
    /** The place (or binder) it's in, for "pull from 3 places". */
    val placeKey: String? = null
) : UpgradeCard

/** The deck's Game Changers and complete combos now, for the bracket guard. */
data class UpgradeBracket(val gameChangers: Int, val combos: Int)

data class UpgradeInput(
    /** The commander's name, for "in 61% of Krenko decks". */
    val commander: String?,
    /** The colours cards must be inside ("RG"; "" colourless only); null for no limit. */
    val identity: String?,
    /** EDHREC's numbers are there; false matches by role and EDHREC rank alone. */
    val edhrec: Boolean,
    /** Null for no guard (not Commander). */
    val bracket: UpgradeBracket?,
    val deck: List<UpgradeDeckCard>,
    val owned: List<UpgradeOwnedCard>,
    /** Swaps the user said "Not this one" to ([upgradePairKey]). */
    val dismissed: Set<String> = emptySet()
)

data class UpgradeSwap(
    val key: String,
    val cut: UpgradeDeckCard,
    val add: UpgradeOwnedCard,
    val role: String,
    val reason: String,
    /** "Red box › Red", or "in Atraxa deck" when another deck has the only copy. */
    val where: String,
    val gain: Double,
    /** The card coming in less the one going out, in US dollars; null when either price isn't known. */
    val priceDelta: Double?,
    /** The bracket the deck would move to, for a swap that would raise it; null otherwise. */
    val raisesBracketTo: Int?
)

private fun nameKey(name: String) = name.trim().lowercase()
fun upgradePairKey(cut: String, add: String): String = "${nameKey(cut)}>${nameKey(add)}"

private fun round2(n: Double): Double = Math.round(n * 100) / 100.0

/** The card's score: its EDHREC inclusion, or offline, its EDHREC rank against what it costs. */
fun upgradeScore(card: UpgradeCard, edhrec: Boolean): Double {
    if (edhrec) return (card.inclusion ?: 0).toDouble()
    val rank = card.edhrecRank?.let { 100.0 / (1 + it / 1000.0) } ?: 0.0
    return round2(rank - 3 * (card.cmc ?: 0.0))
}

/** 12345 → "12,345". */
fun withThousands(n: Double): String {
    val whole = kotlin.math.abs(n.toLong())
    val digits = whole.toString()
    val out = StringBuilder()
    digits.forEachIndexed { i, c ->
        if (i > 0 && (digits.length - i) % 3 == 0) out.append(',')
        out.append(c)
    }
    return (if (n.toLong() < 0) "-" else "") + out
}

/** "Krenko, Mob Boss" → "Krenko"; a two-faced card's front face. */
fun shortCommander(name: String): String = name.substringBefore(" // ").substringBefore(",").trim()

/** The first of [UPGRADE_ROLES] a card does, or null. */
fun upgradeRoleOf(roles: List<String>): String? = UPGRADE_ROLES.firstOrNull { it in roles }

/** A rough Commander bracket, as the deck's Stats estimate it (DeckDetailViewModel.estimateBracket). */
fun upgradeBracketOf(gameChangers: Int, combos: Int): Int = when {
    gameChangers == 0 && combos == 0 -> 2
    gameChangers <= 3 -> 3
    else -> 4
}

private fun isInside(identity: String?, limit: String?): Boolean {
    if (limit == null) return true
    if (identity == null) return false
    val allowed = limit.uppercase()
    return identity.uppercase().all { it in allowed }
}

/** Why the swap is worth it, in plain words. */
fun upgradeReason(cut: UpgradeDeckCard, add: UpgradeOwnedCard, role: String, commander: String?, edhrec: Boolean): String {
    val out = StringBuilder("Both are ${upgradeRoleWord(role)}")
    if (edhrec) {
        val whose = if (commander != null) "${shortCommander(commander)} decks" else "decks"
        out.append("; ${add.name} is in ${add.inclusion ?: 0}% of $whose, ")
        out.append(if (cut.inclusion != null) "${cut.name} in ${cut.inclusion}%." else "${cut.name} isn't on EDHREC's list for it.")
    } else {
        val parts = mutableListOf<String>()
        if (add.edhrecRank != null && cut.edhrecRank != null) {
            parts += "${add.name} ranks #${withThousands(add.edhrecRank.toDouble())} on EDHREC, ${cut.name} #${withThousands(cut.edhrecRank.toDouble())}"
        } else if (add.edhrecRank != null) {
            parts += "${add.name} ranks #${withThousands(add.edhrecRank.toDouble())} on EDHREC"
        }
        if (add.cmc != null && cut.cmc != null && add.cmc < cut.cmc) parts += "${add.name} costs ${withThousands(cut.cmc - add.cmc)} less"
        out.append(if (parts.isNotEmpty()) "; ${parts.joinToString("; ")}." else ".")
    }
    if (cut.replaceable) out.append(" You marked ${cut.name} as a cut.")
    return out.toString()
}

/**
 * The swaps for the deck, best first: those that keep its bracket, then (marked with the bracket
 * they'd move it to) those that wouldn't. Role by role, the best card owned for the job is paired
 * with the deck's weakest one doing it — cards marked as cuts first — when it's better by
 * [UPGRADE_MIN_GAIN] (any gain for a marked cut). Each card is in one swap at most, and cards that
 * would raise the bracket on their own are paired last, so they don't take the cuts the others could use.
 */
fun upgradeSwaps(input: UpgradeInput): List<UpgradeSwap> {
    val edhrec = input.edhrec
    val inDeck = input.deck.flatMap { cardNameKeys(it.name) }.toSet()
    fun score(c: UpgradeCard) = upgradeScore(c, edhrec)
    val cuts = input.deck.filter { !it.commander && !it.keep && !it.comboPiece && !isLandType(it.typeLine) && upgradeRoleOf(it.roles) != null }
    val adds = input.owned.filter { c ->
        c.legal && !isLandType(c.typeLine) && upgradeRoleOf(c.roles) != null && isInside(c.identity, input.identity) &&
            (c.spare > 0 || c.heldBy != null) && cardNameKeys(c.name).none { it in inDeck }
    }
    val usedCuts = mutableSetOf<String>()
    val usedAdds = mutableSetOf<String>()
    val found = mutableListOf<UpgradeSwap>()
    val bracket = input.bracket
    val now = bracket?.let { upgradeBracketOf(it.gameChangers, it.combos) } ?: 0
    fun raisesAlone(c: UpgradeOwnedCard) = bracket != null &&
        upgradeBracketOf(bracket.gameChangers + (if (c.gameChanger) 1 else 0), bracket.combos + (if (c.completesCombo) 1 else 0)) > now
    for (pass in listOf(false, true)) {
        for (role in UPGRADE_ROLES) {
            val roleCuts = cuts.filter { role in it.roles }
                .sortedWith(compareByDescending<UpgradeDeckCard> { it.replaceable }.thenBy { score(it) }.thenBy { it.name })
            val roleAdds = adds.filter { role in it.roles && raisesAlone(it) == pass }
                .sortedWith(compareByDescending<UpgradeOwnedCard> { score(it) }.thenBy { it.name })
            for (add in roleAdds) {
                if (nameKey(add.name) in usedAdds) continue
                val cut = roleCuts.firstOrNull { c ->
                    if (nameKey(c.name) in usedCuts || upgradePairKey(c.name, add.name) in input.dismissed) return@firstOrNull false
                    val gain = score(add) - score(c)
                    if (c.replaceable) gain > 0 else gain >= UPGRADE_MIN_GAIN
                } ?: continue
                usedCuts += nameKey(cut.name)
                usedAdds += nameKey(add.name)
                found += UpgradeSwap(
                    key = upgradePairKey(cut.name, add.name),
                    cut = cut, add = add, role = role,
                    reason = upgradeReason(cut, add, role, input.commander, edhrec),
                    where = if (add.spare > 0) add.where ?: "In your collection" else "in ${add.heldBy} deck",
                    gain = round2(score(add) - score(cut)),
                    priceDelta = if (add.usd != null && cut.usd != null) round2(add.usd - cut.usd) else null,
                    raisesBracketTo = null
                )
            }
        }
    }
    val sorted = found.sortedWith(compareByDescending<UpgradeSwap> { it.cut.replaceable }.thenByDescending { it.gain }.thenBy { it.add.name })
    if (bracket == null) return sorted.take(MAX_UPGRADE_SWAPS)
    // Taken in order, as "Apply all" would: each swap that keeps the bracket counts toward the next.
    var gameChangers = bracket.gameChangers
    var combos = bracket.combos
    val keeping = mutableListOf<UpgradeSwap>()
    val raising = mutableListOf<UpgradeSwap>()
    for (swap in sorted) {
        val gc = gameChangers + (if (swap.add.gameChanger) 1 else 0) - (if (swap.cut.gameChanger) 1 else 0)
        val co = combos + (if (swap.add.completesCombo) 1 else 0)
        val after = upgradeBracketOf(gc, co)
        if (after > now) {
            raising += swap.copy(raisesBracketTo = after)
        } else {
            gameChangers = gc
            combos = co
            keeping += swap
        }
    }
    return keeping.take(MAX_UPGRADE_SWAPS) + raising.take(MAX_BRACKET_SWAPS)
}

/** "Would move the deck to bracket 4". */
fun bracketWarning(bracket: Int): String = "Would move the deck to bracket $bracket"

/** "8 upgrades from your cards · $46 of cards you already own · pull from 3 places": what the cards coming in are worth (left out at $0). */
fun upgradeSummary(swaps: List<UpgradeSwap>, money: (Double) -> String): String {
    val n = swaps.size
    val saved = round2(swaps.sumOf { it.add.usd ?: 0.0 })
    val places = swaps.filter { it.add.spare > 0 && it.add.placeKey != null }.mapNotNull { it.add.placeKey }.toSet().size
    val parts = mutableListOf("$n upgrade${if (n == 1) "" else "s"} from your cards")
    if (saved > 0) parts += "${money(saved)} of cards you already own"
    if (places > 0) parts += "pull from $places place${if (places == 1) "" else "s"}"
    return parts.joinToString(" · ")
}

// ---- Where the owned cards are ----

/** One card the user owns, by name: how many copies are free, and where one is. */
data class OwnedSource(
    val name: String,
    val scryfallId: String,
    val spare: Int,
    val heldBy: String?,
    val where: String?,
    val placeKey: String?
)

/**
 * Every card the user owns outside [deckId], by name key: the copies in binders, boxes and the Unsorted
 * pile (wishlists not), less the copies other decks that don't hold their cards are waiting on (their
 * pull lists); and the cards only another deck you hold has. A spare copy's place is the first spot
 * found — a place before a binder with no place.
 */
fun ownedSources(collections: List<Collection>, decks: List<Deck>, deckId: String): Map<String, OwnedSource> {
    val places = collections.firstOrNull { it.storagePlaces != null }?.storagePlaces.orEmpty().associateBy { it.id }
    class Acc(val name: String, var scryfallId: String, var copies: Int = 0, var where: String? = null, var placeKey: String? = null, var placed: Boolean = false)
    val out = LinkedHashMap<String, Acc>()
    for (c in collections) {
        if (c.type == CollectionType.WISHLIST.name) continue
        for (e in c.entries) {
            val copies = e.quantity + e.foilQuantity
            if (copies <= 0) continue
            val key = nameKey(e.name)
            val had = out.getOrPut(key) { Acc(e.name, e.scryfallId) }
            had.copies += copies
            if (!had.placed) {
                val line = e.places.orEmpty().firstOrNull { it.qty > 0 && it.placeId in places }
                if (line != null) {
                    val place = places.getValue(line.placeId)
                    had.where = place.name + (line.section?.let { " › $it" } ?: "") +
                        (if (line.page != null && line.slot != null && line.page > 0 && line.slot > 0) " p${line.page} s${line.slot}" else "")
                    had.placeKey = "place:${place.id}"
                    had.placed = true
                    had.scryfallId = e.scryfallId
                } else if (had.where == null) {
                    had.where = c.name
                    had.placeKey = "binder:${c.id}"
                }
            }
        }
    }
    class Waiting(var qty: Int, val deck: String)
    val waiting = HashMap<String, Waiting>()
    val heldIn = LinkedHashMap<String, Triple<String, String, String>>()
    for (d in decks) {
        if (d.id == deckId) continue
        for (e in d.cards) {
            val key = nameKey(e.name)
            if (d.holdsCards) {
                if (e.quantity - proxyCopies(d, e) > 0 && key !in heldIn) heldIn[key] = Triple(e.name, e.scryfallId, d.name)
            } else {
                val need = (e.quantity - (e.proxyQuantity ?: 0).coerceAtLeast(0)).coerceAtLeast(0)
                if (need <= 0) continue
                val w = waiting[key]
                if (w != null) w.qty += need else waiting[key] = Waiting(need, d.name)
            }
        }
    }
    val result = LinkedHashMap<String, OwnedSource>()
    for ((key, s) in out) {
        val w = waiting[key]
        val spare = s.copies - (w?.qty ?: 0)
        result[key] = OwnedSource(
            s.name, s.scryfallId, spare.coerceAtLeast(0),
            heldBy = if (spare > 0) null else w?.deck ?: heldIn[key]?.third,
            where = if (spare > 0) s.where else null,
            placeKey = if (spare > 0) s.placeKey else null
        )
    }
    for ((key, h) in heldIn) {
        if (key !in result) result[key] = OwnedSource(h.first, h.second, 0, h.third, null, null)
    }
    return result
}

// ---- Swapping ----

/**
 * The deck once a swap is made: [cutId] (all its copies) onto Considering, as a cut always goes, and
 * [add] into the deck — on the deck's pull list (PullList.kt), so it's fetched from where it's kept: a
 * deck holding its cards counts the new copy as still to pull until it's moved in.
 */
fun withUpgrade(deck: Deck, cutId: String, add: DeckCardEntry): Deck {
    val cut = deck.cards.firstOrNull { it.scryfallId == cutId } ?: return deck
    if (deck.cards.any { it.scryfallId == add.scryfallId }) return deck
    val considering = deck.considering.filter { it.scryfallId != cut.scryfallId && it.scryfallId != add.scryfallId }
    val incoming = add.copy(quantity = 1, replaceable = false, proxyQuantity = if (deck.holdsCards) 1 else null)
    return deck.copy(
        cards = deck.cards.filter { it.scryfallId != cutId } + incoming,
        considering = considering + cut.copy(replaceable = false)
    )
}

/** What [applyUpgradesForUndo] did: the library after, and what [undoUpgrades] needs to put it back. */
data class UpgradeApplied(
    val collections: List<Collection>,
    val decks: List<Deck>,
    /** The deck as it was before; null when nothing changed. */
    val before: Deck?,
    /** The real copies of the cards cut that went back to the Unsorted pile. */
    val back: List<CollectionEntry>
)

/**
 * [swaps] (cut id to the entry coming in) made in deck [deckId], in one change: each as [withUpgrade],
 * and the real copies of each card cut (a physical deck's, proxies aside) back to the Unsorted pile,
 * as moving a card to Considering does.
 */
fun applyUpgrades(collections: List<Collection>, decks: List<Deck>, deckId: String, swaps: List<Pair<String, DeckCardEntry>>): Pair<List<Collection>, List<Deck>> =
    applyUpgradesForUndo(collections, decks, deckId, swaps).let { it.collections to it.decks }

/** [applyUpgrades], saying what it did, for Undo. */
fun applyUpgradesForUndo(collections: List<Collection>, decks: List<Deck>, deckId: String, swaps: List<Pair<String, DeckCardEntry>>): UpgradeApplied {
    val before = decks.firstOrNull { it.id == deckId } ?: return UpgradeApplied(collections, decks, null, emptyList())
    var deck = before
    val back = mutableListOf<CollectionEntry>()
    for ((cutId, add) in swaps) {
        val cut = deck.cards.firstOrNull { it.scryfallId == cutId } ?: continue
        val next = withUpgrade(deck, cutId, add)
        if (next === deck) continue
        val leaving = realCopiesLeaving(deck, cut, 0)
        if (leaving > 0) back += pileEntryOf(cut, leaving)
        deck = next
    }
    if (deck === before) return UpgradeApplied(collections, decks, null, emptyList())
    val cols = if (back.isEmpty()) collections
    else withUnsortedPile(collections).map { if (it.id == UNSORTED_COLLECTION_ID) it.copy(entries = intoPile(it.entries, back)) else it }
    return UpgradeApplied(cols, decks.map { if (it.id == deckId) deck else it }, before, back)
}

/**
 * Undo for [applyUpgradesForUndo]: the deck exactly as it was ([before] — cut cards back in their place,
 * added cards and their pull-list marks gone, Considering as it was), and the copies that went back
 * to the Unsorted pile ([back]) taken out of it again.
 */
fun undoUpgrades(collections: List<Collection>, decks: List<Deck>, before: Deck, back: List<CollectionEntry>): Pair<List<Collection>, List<Deck>> {
    val cols = if (back.isEmpty()) collections else collections.map { c ->
        if (c.id != UNSORTED_COLLECTION_ID) c
        else c.copy(entries = back.fold(c.entries) { entries, e -> takenFromUnsorted(entries, e.scryfallId, e.name, e.quantity).first })
    }
    return cols to decks.map { if (it.id == before.id) before else it }
}
