package com.mtgcompanion.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// New sets: Scryfall's sets coming out soon and just out, and — for a set whose cards Scryfall has
// shown (previews, then the full set) — which of them suit the user's Commander decks and which are on
// their Wishlist. Followed sets are announced on release day (this app with a notification, the web
// app with a banner when it's opened). Pure, so it can be tested. Mirrors the web app's
// src/collection/newSets.ts, with the same tests (NewSetsTest.kt ↔ tests/collection/newSets.test.ts).
//
// A card suits a deck when its colour identity is within the commander's and it shares something the
// deck already has plenty of: a theme (a printed keyword, a theme tag such as Tokens or Removal, or one
// of the deck's own categories) or a creature type, on at least MIN_SHARED of the deck's cards.

/** Scryfall set types that aren't sets of cards to play: tokens, promos, art cards, digital-only extras. */
val SKIPPED_SET_TYPES = setOf("token", "memorabilia", "minigame", "alchemy", "treasure_chest", "vanguard", "promo")

/** How long a set counts as just out. */
const val RECENT_DAYS = 30

/** How many of a deck's cards must share a theme or creature type for it to count. */
const val MIN_SHARED = 4

/** Keywords nearly every deck is full of, too common to say anything about one. */
val COMMON_KEYWORDS = setOf(
    "flying", "first strike", "double strike", "trample", "haste", "vigilance", "reach", "deathtouch", "menace",
    "defender", "flash", "hexproof", "indestructible", "ward", "lifelink", "scry", "mill", "surveil"
)

/** A set's card, as much of it as the matching needs. */
data class SetCard(
    val id: String,
    val name: String,
    val typeLine: String,
    val colorIdentity: List<String>,
    val tags: List<String>,
    val imageUrl: String?,
    val rarity: String?,
    /** The role tags its rules text shows (RoleTags.kt's labels: "Token maker", "Treasure"…). */
    val roles: List<String> = emptyList(),
    /** When this printing comes out ("2026-11-14"). */
    val releasedAt: String? = null,
    /** Today's price, US dollars, as Scryfall sends it ("1.25"); none before release. */
    val usd: String? = null,
    /** Scryfall's Commander legality ("legal", "not_legal", "banned"); every card is "not_legal" before release. */
    val commanderLegality: String? = null
)

/** Why a card suits a deck: what it shares, with how many of the deck's cards. */
data class Shared(val label: String, val count: Int)
data class DeckFit(val card: SetCard, val shared: List<Shared>, val score: Int)
data class DeckFits(val deckId: String, val deckName: String, val fits: List<DeckFit>)
data class ReleaseSets(val upcoming: List<SetInfo>, val recent: List<SetInfo>)

/** "2026-10-07" + [days]. */
fun addDays(date: String, days: Long): String = LocalDate.parse(date).plusDays(days).toString()

/** Whole days from [today] to [date] (both "2026-10-07"); negative once it's past. */
fun daysUntil(date: String, today: String): Long = ChronoUnit.DAYS.between(LocalDate.parse(today), LocalDate.parse(date))

/** Whether [set] is one to list: a paper set of cards, with a release date. */
fun listable(set: SetInfo): Boolean = !set.releasedAt.isNullOrBlank() && !set.digital && set.setType.orEmpty() !in SKIPPED_SET_TYPES

/**
 * The sets coming out after [today], soonest first, and those out in the last RECENT_DAYS (today
 * included), newest first.
 */
fun releaseSets(sets: Iterable<SetInfo>, today: String, recentDays: Int = RECENT_DAYS): ReleaseSets {
    val since = addDays(today, -recentDays.toLong())
    val listed = sets.filter { listable(it) }
    val byName = compareBy<SetInfo> { it.name }
    return ReleaseSets(
        upcoming = listed.filter { it.releasedAt.orEmpty() > today }.sortedWith(compareBy<SetInfo> { it.releasedAt }.then(byName)),
        recent = listed.filter { val at = it.releasedAt.orEmpty(); at <= today && at > since }.sortedWith(compareByDescending<SetInfo> { it.releasedAt }.then(byName))
    )
}

/** "Out today", "Out tomorrow", "In 12 days", "Out 3 days ago", "Out yesterday". */
fun releaseLabel(releasedAt: String, today: String): String {
    val d = daysUntil(releasedAt, today)
    return when {
        d == 0L -> "Out today"
        d == 1L -> "Out tomorrow"
        d == -1L -> "Out yesterday"
        d > 0 -> "In $d days"
        else -> "Out ${-d} days ago"
    }
}

/** "No cards shown yet" / "12 cards shown so far" (before release) / "286 cards". */
fun cardsLabel(set: SetInfo, today: String): String {
    if (set.cardCount <= 0) return "No cards shown yet"
    val n = "${set.cardCount} ${if (set.cardCount == 1) "card" else "cards"}"
    return if (set.releasedAt.orEmpty() > today) "$n shown so far" else n
}

/**
 * Followed sets to announce: out on or before [today] (in the last week, so a phone off for a while
 * doesn't announce old news), not announced yet.
 */
fun setsToAnnounce(followed: Set<String>, sets: Iterable<SetInfo>, today: String, told: Set<String>): List<SetInfo> {
    val since = addDays(today, -7)
    return sets.filter { s ->
        val at = s.releasedAt
        s.code in followed && s.code !in told && at != null && at <= today && at > since
    }.sortedWith(compareByDescending<SetInfo> { it.releasedAt }.thenBy { it.name })
}

/** A theme as matched: lower case, with the deck categories' and theme tags' spellings brought together. */
fun themeKey(label: String): String {
    val k = label.trim().lowercase()
    return when (k) {
        "draw", "card advantage" -> "card draw"
        "counterspells", "counters" -> "counterspell"
        "board wipes", "wipes", "wraths" -> "board wipe"
        "token", "token maker" -> "tokens"
        "life gain" -> "lifegain"
        "mana ramp" -> "ramp"
        else -> k
    }
}

private val TYPE_DASH = Regex("\\s+[—-]\\s+")
private val CREATURE_TYPE = Regex("\\b(Creature|Kindred|Tribal)\\b")
private val SPACES = Regex("\\s+")

/** The creature types on [typeLine] ("Creature — Elf Druid" → Elf, Druid), front face and back. */
fun creatureTypes(typeLine: String?): List<String> {
    val out = LinkedHashSet<String>()
    for (face in typeLine.orEmpty().split(" // ")) {
        val parts = face.split(TYPE_DASH)
        val subtypes = parts.getOrNull(1) ?: continue
        if (!CREATURE_TYPE.containsMatchIn(parts[0])) continue
        subtypes.trim().split(SPACES).filter { it.isNotEmpty() }.forEach { out += it }
    }
    return out.toList()
}

/** A card's themes: its tags and categories, as keys (with their spelling), without the too-common keywords. */
private fun themesOf(tags: List<String>?, categories: List<String>? = null): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (t in tags.orEmpty() + categories.orEmpty()) {
        val key = themeKey(t)
        if (key.isEmpty() || key in COMMON_KEYWORDS || key in out) continue
        out[key] = t.trim()
    }
    return out
}

/** What a deck has plenty of: themes and creature types on at least MIN_SHARED of its cards. */
data class DeckProfile(
    val deckId: String,
    val deckName: String,
    val identity: Set<String>,
    val themes: Map<String, Shared>,
    val types: Map<String, Shared>,
    val names: Set<String>
)

/**
 * [roles]: a card's role tags by name, as labels ("Card draw"), where they're known (RoleTags.kt) —
 * they count as themes alongside its tags and categories.
 */
fun deckProfile(deck: Deck, identity: List<String>, roles: (String) -> List<String>? = { null }): DeckProfile {
    val seen = HashSet<String>()
    val entries = listOfNotNull(deck.commander, deck.partnerCommander).plus(deck.cards).filter { seen.add(it.name.lowercase()) }
    val themes = LinkedHashMap<String, Shared>()
    val types = LinkedHashMap<String, Shared>()
    for (e in entries) {
        for ((key, label) in themesOf(e.tags + roles(e.name).orEmpty(), e.categories)) {
            val had = themes[key]
            themes[key] = Shared(had?.label ?: label, (had?.count ?: 0) + 1)
        }
        for (t in creatureTypes(e.typeLine)) types[t] = Shared(t, (types[t]?.count ?: 0) + 1)
    }
    return DeckProfile(
        deck.id, deck.name, identity.toSet(),
        themes.filterValues { it.count >= MIN_SHARED },
        types.filterValues { it.count >= MIN_SHARED },
        entries.flatMap { cardNameKeys(it.name) }.toSet()
    )
}

private val BASIC = Regex("\\bBasic\\b")
private val LAND = Regex("\\bLand\\b")
private val TOKEN = Regex("\\b(Token|Emblem)\\b")

/** Not a basic land, a token or an emblem. */
private fun playable(c: SetCard): Boolean =
    !(BASIC.containsMatchIn(c.typeLine) && LAND.containsMatchIn(c.typeLine)) && !TOKEN.containsMatchIn(c.typeLine)

/**
 * [cards] that suit the deck: within its colour identity, not in it already, and sharing a theme or
 * creature type it has plenty of — best first (the more of the deck's cards share it, the better),
 * at most [limit].
 */
fun deckFits(profile: DeckProfile, cards: List<SetCard>, limit: Int = 8): List<DeckFit> {
    val out = mutableListOf<DeckFit>()
    val seen = HashSet<String>()
    for (c in cards) {
        val key = c.name.lowercase()
        if (key in seen || !playable(c)) continue
        if (cardNameKeys(c.name).any { it in profile.names }) continue
        if (!profile.identity.containsAll(c.colorIdentity)) continue
        val shared = mutableListOf<Shared>()
        creatureTypes(c.typeLine).forEach { t -> profile.types[t]?.let { shared += it } }
        themesOf(c.tags).keys.forEach { k -> profile.themes[k]?.let { shared += it } }
        if (shared.isEmpty()) continue
        seen += key
        val sorted = shared.sortedWith(compareByDescending<Shared> { it.count }.thenBy { it.label })
        out += DeckFit(c, sorted, sorted.sumOf { it.count })
    }
    return out.sortedWith(compareByDescending<DeckFit> { it.score }.thenBy { it.card.name }).take(limit)
}

/** "Elf, like 14 cards in the deck · Tokens, like 9". */
fun fitReason(fit: DeckFit): String =
    fit.shared.take(2).mapIndexed { i, s -> if (i == 0) "${s.label}, like ${s.count} cards in the deck" else "${s.label}, like ${s.count}" }
        .joinToString(" · ")

/** The decks the set is checked against: Commander decks with a commander, not put away, not samples. */
fun commanderDecks(decks: List<Deck>): List<Deck> =
    decks.filter { it.mode == GameMode.COMMANDER && it.commander != null && it.archived != true && !isSample(it) }

/** The set's cards whose name is on [wishlistNames] (lower case) — reprints of cards the user wants. Once each. */
fun wishlistReprints(wishlistNames: Set<String>, cards: List<SetCard>): List<SetCard> {
    val seen = HashSet<String>()
    return cards.filter { c -> cardNameKeys(c.name).any { it in wishlistNames } && seen.add(c.name.lowercase()) }
}
