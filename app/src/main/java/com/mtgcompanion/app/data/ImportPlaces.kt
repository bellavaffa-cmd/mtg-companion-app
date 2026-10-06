package com.mtgcompanion.app.data

/*
 * Import with locations: a CSV from this app or another (ManaBox's "Binder Name", a "Location",
 * "Folder" or "Box" column, this app's own "Place") says where each card is kept. The import shows
 * each value of that column with its copies — "Binder 1" 288, "Box R" 612, blank 118 — and the user
 * matches each to one of their places, a new place, or no place yet; the copies are then given those
 * places as they're added.
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/importPlaces.ts rule for rule, with
 * the same tests (ImportPlacesTest.kt ↔ tests/collection/importPlaces.test.ts).
 */

/** Copies one line of an import said were kept at [value] (as the file spells it). */
data class ImportedLocation(val value: String, val qty: Int, val foil: Boolean)

/** The headers that say where a card is kept, best first (lower case). */
private val LOCATION_HEADERS = listOf(
    "place", "places", "location", "locations", "storage location", "storage place", "storage",
    "binder name", "binder", "folder name", "folder", "box", "box name"
)

/** Any other header with one of these words, unless it's about the kind or an id ("Binder Type"). */
private val LOCATION_WORD = Regex("\\b(binder|location|folder|storage)\\b")
private val NOT_LOCATION = Regex("\\b(type|id|count|quantity)\\b")

/** Which of a CSV's (lower-cased) headers says where cards are kept; -1 when none does. */
fun locationColumnIn(header: List<String>): Int {
    val trimmed = header.map { it.trim().lowercase() }
    for (name in LOCATION_HEADERS) {
        val i = trimmed.indexOf(name)
        if (i >= 0) return i
    }
    return trimmed.indexOfFirst { LOCATION_WORD.containsMatchIn(it) && !NOT_LOCATION.containsMatchIn(it) }
}

/** The key a location value is matched by: trimmed, case aside. "" for blank. */
fun locationKey(value: String?): String = value?.trim()?.lowercase().orEmpty()

/** One value of the location column and how many copies it holds. [value] "" for the rows that leave it blank. */
data class LocationCount(val value: String, val copies: Int) {
    val key: String get() = locationKey(value)
}

/** Each value of the location column with its copies, in the order the file has them; the blank ones last. None when no line says. */
fun locationCounts(lines: List<ListLine>): List<LocationCount> {
    if (lines.none { locationKey(it.location).isNotEmpty() }) return emptyList()
    val counts = LinkedHashMap<String, LocationCount>()
    var blank = 0
    for (l in lines) {
        val key = locationKey(l.location)
        if (key.isEmpty()) { blank += l.quantity; continue }
        val had = counts[key]
        counts[key] = had?.copy(copies = had.copies + l.quantity) ?: LocationCount(l.location!!.trim(), l.quantity)
    }
    return counts.values.toList() + if (blank > 0) listOf(LocationCount("", blank)) else emptyList()
}

/** Where a location value's copies go: one of the user's places, a new place by that name, or no place yet. */
sealed interface PlaceTarget {
    data class Existing(val placeId: String) : PlaceTarget
    data object New : PlaceTarget
    data object None : PlaceTarget
}

/**
 * What each value starts matched to, by [LocationCount.key]: the place with that name (or path, "Shelf
 * › Red box") — so this app's own export comes back where it was — else a new place; blank, no place.
 */
fun suggestTargets(counts: List<LocationCount>, places: List<StoragePlace>): Map<String, PlaceTarget> =
    counts.associate { c ->
        c.key to when {
            c.key.isEmpty() -> PlaceTarget.None
            else -> {
                val match = places.firstOrNull { locationKey(it.name) == c.key }
                    ?: places.firstOrNull { locationKey(placePath(places, it.id)) == c.key }
                if (match != null) PlaceTarget.Existing(match.id) else PlaceTarget.New
            }
        }
    }

/** What a new place made from [value] is: a binder, a deck box, a shelf or a box, by its words — else by the column's ("Binder Name"). */
fun importedPlaceKind(value: String, column: String?): PlaceKind {
    val v = value.lowercase()
    return when {
        Regex("\\bbinders?\\b").containsMatchIn(v) -> PlaceKind.BINDER
        Regex("\\bdeck ?box(es)?\\b").containsMatchIn(v) -> PlaceKind.DECK_BOX
        Regex("\\b(shelf|shelves|cupboard|drawer|cabinet)\\b").containsMatchIn(v) -> PlaceKind.SHELF
        Regex("\\bbox(es)?\\b").containsMatchIn(v) -> PlaceKind.BOX
        column?.lowercase()?.contains("binder") == true -> PlaceKind.BINDER
        else -> PlaceKind.BOX
    }
}

/** One card's copies an import added to [collectionId], and where the list said they're kept. */
data class ImportedPlacement(val scryfallId: String, val locations: List<ImportedLocation>)

/**
 * [collections] after an import into [collectionId]: a new place made for each value matched to
 * [PlaceTarget.New] (its kind from its words, see importedPlaceKind), and the imported copies given
 * the place their value is matched to — up to the entry's copies with no place, plain and foil apart.
 * Values left out of [targets] or matched to no place stay with no place.
 */
fun applyImportedPlaces(
    collections: List<Collection>,
    collectionId: String,
    placements: List<ImportedPlacement>,
    targets: Map<String, PlaceTarget>,
    column: String?,
    now: Long,
    newId: () -> String
): List<Collection> {
    if (placements.none { it.locations.isNotEmpty() }) return collections
    var out = collections
    val places = placesOf(out)
    // The values matched to a new place, each made once, in the file's order.
    val ids = HashMap<String, String>()
    var made = 0
    for (p in placements) for (l in p.locations) {
        val key = locationKey(l.value)
        if (key.isEmpty() || key in ids) continue
        when (val t = targets[key]) {
            is PlaceTarget.Existing -> if (places.any { it.id == t.placeId }) ids[key] = t.placeId
            PlaceTarget.New -> {
                val kind = importedPlaceKind(l.value, column)
                val id = newId()
                out = savePlace(out, StoragePlace(id = id, name = l.value.trim(), kind = kind.name, createdAt = now + made++))
                ids[key] = id
            }
            else -> Unit
        }
    }
    if (ids.isEmpty()) return out
    return out.map { c ->
        if (c.id != collectionId) return@map c
        c.copy(entries = c.entries.map { e ->
            val p = placements.firstOrNull { it.scryfallId == e.scryfallId } ?: return@map e
            var entry = e
            for (l in p.locations) {
                val id = ids[locationKey(l.value)] ?: continue
                entry = placeCopies(entry, Spot(id), l.qty, l.foil).first
            }
            entry
        })
    }
}

/** How many of [counts]' values go to a new place. */
fun newPlaceCount(counts: List<LocationCount>, targets: Map<String, PlaceTarget>): Int =
    counts.count { it.key.isNotEmpty() && targets[it.key] == PlaceTarget.New }
