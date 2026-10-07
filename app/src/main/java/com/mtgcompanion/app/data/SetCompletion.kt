package com.mtgcompanion.app.data

// Set completion: for every set the user owns cards from, how many of its cards they have. A card
// here is a printing (Scryfall's card_count for a set counts printings, showcase and borderless
// versions included), so owning a set's Lightning Bolt in one art and not the other is one of two.
// Owned means what All cards counts as owned: copies in owned binders and in decks, not proxies.
// Mirrors the web app's src/collection/setCompletion.ts.

/** A set as Scryfall describes it. [cardCount]: how many printings it has; [releasedAt]: "2024-08-02". */
data class SetInfo(
    val code: String,
    val name: String,
    val cardCount: Int,
    val releasedAt: String? = null,
    val iconSvgUri: String? = null,
    /** Scryfall's set_type: "expansion", "commander", "token"… (NewSets.kt leaves some out). */
    val setType: String? = null,
    /** Only on MTG Arena or Magic Online. */
    val digital: Boolean = false
)

/** How much of [set] the user has: [owned] of its printings. */
data class SetProgress(val set: SetInfo, val owned: Int) {
    val total: Int get() = set.cardCount
    /** 0..1; 0 when the set's size isn't known. */
    val fraction: Float get() = if (total <= 0) 0f else (owned.toFloat() / total).coerceIn(0f, 1f)
    /** Whole percent, rounded down so a set shows 100% only when it's complete. */
    val percent: Int get() = if (total <= 0) 0 else (owned * 100 / total).coerceIn(0, 100)
    val complete: Boolean get() = total in 1..owned
}

enum class SetSort(val label: String) { PERCENT("Most complete"), NAME("Name"), RELEASE("Newest") }

/**
 * Progress in every set the user owns a card from. [ownedSets]: each owned printing's set code, by
 * scryfallId (a printing whose set isn't known yet is left out). [sets]: Scryfall's sets, by code;
 * a set missing from it is still listed, under its code, with its size unknown.
 */
fun setProgress(ownedSets: Map<String, String>, sets: Map<String, SetInfo>): List<SetProgress> =
    ownedSets.entries
        .groupBy({ it.value.lowercase() }, { it.key })
        .filterKeys { it.isNotBlank() }
        .map { (code, ids) ->
            val set = sets[code] ?: SetInfo(code, code.uppercase(), 0)
            SetProgress(set, ids.distinct().size)
        }

/** [list] in the chosen order; ties go by name. */
fun sortedSets(list: List<SetProgress>, sort: SetSort): List<SetProgress> {
    val byName = compareBy<SetProgress> { it.set.name.lowercase() }
    return when (sort) {
        SetSort.PERCENT -> list.sortedWith(compareByDescending<SetProgress> { it.fraction }.thenByDescending { it.owned }.then(byName))
        SetSort.NAME -> list.sortedWith(byName)
        SetSort.RELEASE -> list.sortedWith(compareByDescending<SetProgress> { it.set.releasedAt.orEmpty() }.then(byName))
    }
}

/** A set's printings the user doesn't own, by scryfallId, in the set's order. */
fun <T> missingFromSet(setCards: List<T>, owned: Set<String>, id: (T) -> String): List<T> =
    setCards.filterNot { id(it) in owned }

/**
 * Real copies held of each printing, by scryfallId, counted as All cards counts them: owned binders
 * (the Unsorted pile too) and every deck's cards, proxies left out. Wishlists don't count.
 */
fun ownedPrintings(collections: List<Collection>, decks: List<Deck>): Map<String, Int> {
    val out = HashMap<String, Int>()
    collections.filter { it.kind == CollectionType.OWNED }.forEach { c ->
        c.entries.forEach { e -> (e.quantity + e.foilQuantity).takeIf { it > 0 }?.let { out.merge(e.scryfallId, it) { a, b -> a + b } } }
    }
    decks.forEach { d ->
        d.cards.forEach { e -> (e.quantity - proxyCopies(d, e)).takeIf { it > 0 }?.let { out.merge(e.scryfallId, it) { a, b -> a + b } } }
    }
    return out
}
