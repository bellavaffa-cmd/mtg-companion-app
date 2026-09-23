package com.mtgcompanion.app.data

/**
 * The user's own tags on a card they own.
 *
 * A tag belongs to the copy, not to the card: "proxy" said of a printing is true of that printing
 * wherever it sits, so tagging it in a binder tags it in every deck it's in too, and it survives
 * being moved between them. That's the difference from [DeckCardEntry.replaceable], a cut candidate,
 * which is only true inside the one deck.
 *
 * A copy is a printing (scryfallId): a different art of the same card is a different card to own,
 * and the library counts copies rather than naming them, so that's as fine as it can get.
 *
 * The web app's src/collection/userTags.ts makes the same decisions.
 */

/** Long enough for "borrowed from Sam", short enough to read as a chip. */
const val MAX_USER_TAG_LENGTH = 30

/** What the user typed, tidied: trimmed, deduped ignoring case, first spelling kept. */
fun tidyUserTags(tags: List<String>): List<String> {
    val out = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    for (raw in tags) {
        val tag = raw.trim().replace(Regex("""\s+"""), " ").take(MAX_USER_TAG_LENGTH)
        if (tag.isEmpty() || !seen.add(tag.lowercase())) continue
        out += tag
    }
    return out
}

private fun DeckCardEntry.tagged(tags: List<String>) = if (userTags == tags) this else copy(userTags = tags)

/** [this] with every copy of [scryfallId] in it carrying [tags]. */
fun Deck.withUserTags(scryfallId: String, tags: List<String>): Deck = copy(
    commander = commander?.let { if (it.scryfallId == scryfallId) it.tagged(tags) else it },
    partnerCommander = partnerCommander?.let { if (it.scryfallId == scryfallId) it.tagged(tags) else it },
    cards = cards.map { if (it.scryfallId == scryfallId) it.tagged(tags) else it },
    considering = considering.map { if (it.scryfallId == scryfallId) it.tagged(tags) else it }
)

/** [this] with every copy of [scryfallId] in it carrying [tags]. */
fun Collection.withUserTags(scryfallId: String, tags: List<String>): Collection = copy(
    entries = entries.map { if (it.scryfallId == scryfallId) (if (it.userTags == tags) it else it.copy(userTags = tags)) else it }
)

/** Every tag on this printing, wherever it's held. */
fun userTagsOf(decks: List<Deck>, collections: List<Collection>, scryfallId: String): List<String> {
    val found = mutableListOf<String>()
    for (deck in decks) {
        for (e in deck.allEntries()) if (e.scryfallId == scryfallId) found += e.userTags
    }
    for (c in collections) {
        for (e in c.entries) if (e.scryfallId == scryfallId) found += e.userTags
    }
    return tidyUserTags(found)
}

/** Every tag the user has used, most-used first, for offering them again. */
fun allUserTags(decks: List<Deck>, collections: List<Collection>): List<String> {
    val counts = LinkedHashMap<String, Pair<String, Int>>()
    fun note(tags: List<String>) {
        for (tag in tidyUserTags(tags)) {
            val at = counts[tag.lowercase()]
            counts[tag.lowercase()] = if (at == null) tag to 1 else at.first to at.second + 1
        }
    }
    for (deck in decks) for (e in deck.allEntries()) note(e.userTags)
    for (c in collections) for (e in c.entries) note(e.userTags)
    return counts.values.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
        .map { it.first }
}

private fun Deck.allEntries(): List<DeckCardEntry> =
    cards + considering + listOfNotNull(commander, partnerCommander)
