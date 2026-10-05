package com.mtgcompanion.app.data

// What a binder entry's copies are like: their condition and the language they're printed in
// (CollectionEntry.condition / .language). Both describe every copy in the entry, and both are
// optional — null means the user hasn't said. Stored as short codes so the web app reads the same
// values; other apps' words for them ("Lightly Played", "near_mint", "Japanese") are read by
// [conditionCode] and [languageCode] when a CSV comes in. Mirrors the web app's
// src/collection/copyDetails.ts.

/** Conditions, best first: Near Mint, Lightly / Moderately / Heavily Played, Damaged. */
val CARD_CONDITIONS = listOf("NM", "LP", "MP", "HP", "DMG")

/** Scryfall's language codes, in the order the picker offers them. */
val CARD_LANGUAGES = listOf("en", "ja", "de", "fr", "it", "es", "pt", "ru", "ko", "zhs", "zht")

private val CONDITION_NAMES = mapOf(
    "NM" to "Near Mint",
    "LP" to "Lightly Played",
    "MP" to "Moderately Played",
    "HP" to "Heavily Played",
    "DMG" to "Damaged"
)

private val LANGUAGE_NAMES = mapOf(
    "en" to "English",
    "ja" to "Japanese",
    "de" to "German",
    "fr" to "French",
    "it" to "Italian",
    "es" to "Spanish",
    "pt" to "Portuguese",
    "ru" to "Russian",
    "ko" to "Korean",
    "zhs" to "Chinese Simplified",
    "zht" to "Chinese Traditional"
)

/** "Lightly Played" for "LP"; the code itself for one this app doesn't know. */
fun conditionName(code: String): String = CONDITION_NAMES[code] ?: code

/** "Japanese" for "ja". */
fun languageName(code: String): String = LANGUAGE_NAMES[code] ?: code

/** The small badge on a row: "JA", "ZHS". */
fun languageBadge(code: String): String = code.uppercase()

private fun words(raw: String): String =
    raw.trim().lowercase().replace('_', ' ').replace('-', ' ').replace(Regex("\\s+"), " ")

/**
 * The condition code for what another app wrote: TCGplayer's and Moxfield's words ("Near Mint",
 * "Lightly Played", "Near Mint Foil"), Deckbox's ("Good (Lightly Played)", "Played"), ManaBox's
 * ("near_mint", "excellent", "light_played", "poor") and the short forms (NM, LP, EX, PL…). Null
 * for a blank cell or a word it can't place.
 */
fun conditionCode(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val w = words(raw).removeSuffix(" foil").removeSuffix(" etched").trim()
    if (w.isEmpty()) return null
    // Deckbox's "Good (Lightly Played)" says what it means in brackets.
    Regex("\\(([^)]*)\\)").find(w)?.groupValues?.get(1)?.let { inner -> conditionCode(inner)?.let { return it } }
    return when (w) {
        "nm", "near mint", "mint", "m", "mt", "nm m", "nm/m", "near mint mint" -> "NM"
        "lp", "lightly played", "light played", "slightly played", "sp", "excellent", "ex", "ex+" -> "LP"
        "mp", "moderately played", "played", "pl", "good", "gd", "vg", "very good" -> "MP"
        "hp", "heavily played", "heavy played" -> "HP"
        "dmg", "damaged", "poor", "po", "d" -> "DMG"
        else -> CARD_CONDITIONS.firstOrNull { it.equals(w, ignoreCase = true) }
    }
}

/**
 * The language code for what another app wrote: a name ("Japanese", "Chinese Simplified",
 * "Simplified Chinese") or a code (Scryfall's own, or the usual others: "jp", "zh-CN", "kr"). Null
 * for a blank cell or a language Scryfall doesn't print in.
 */
fun languageCode(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val w = words(raw)
    if (w in CARD_LANGUAGES) return w
    return when (w) {
        "english", "eng" -> "en"
        "japanese", "jp", "jpn" -> "ja"
        "german", "deutsch", "ger", "deu" -> "de"
        "french", "français", "francais", "fra", "fre" -> "fr"
        "italian", "italiano", "ita" -> "it"
        "spanish", "español", "espanol", "spa", "sp" -> "es"
        "portuguese", "portuguese (brazil)", "portuguese brazil", "português", "por", "pt br" -> "pt"
        "russian", "rus" -> "ru"
        "korean", "kr", "kor" -> "ko"
        "chinese simplified", "simplified chinese", "chinese (simplified)", "zh cn", "zh hans", "cs", "chs", "s chinese" -> "zhs"
        "chinese traditional", "traditional chinese", "chinese (traditional)", "zh tw", "zh hant", "ct", "cht", "t chinese" -> "zht"
        else -> null
    }
}

/** The badges a binder row shows for its copies — only what's been set: "LP", "JA". */
fun copyBadges(entry: CollectionEntry): List<String> =
    listOfNotNull(entry.condition, entry.language?.let(::languageBadge))

/**
 * This entry with [added]'s copies put in with its own. One entry describes all its copies, so it
 * keeps its own condition, language and alert, taking [added]'s only where it has none.
 */
internal fun CollectionEntry.withCopiesOf(added: CollectionEntry): CollectionEntry {
    val next = copy(
        quantity = quantity + added.quantity,
        foilQuantity = foilQuantity + added.foilQuantity,
        condition = condition ?: added.condition,
        language = language ?: added.language,
        priceAlertAbove = priceAlertAbove ?: added.priceAlertAbove
    )
    // Where the added copies are kept comes with them (see StoragePlaces.kt).
    return if (places != null || added.places != null) withPlaces(next, places.orEmpty() + added.places.orEmpty()) else next
}
