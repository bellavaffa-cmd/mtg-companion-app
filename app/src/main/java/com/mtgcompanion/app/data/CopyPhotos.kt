package com.mtgcompanion.app.data

/*
 * Photos of your copy: front and back pictures of one particular copy of a card, with what it was
 * bought for and where, and when it was photographed — for insurance, a sale or a grading
 * submission. The photos and those details stay on the device that took them (CopyPhotoStore.kt; the
 * web app keeps its own in the browser): they aren't synced. The copy's condition is the binder
 * entry's (CollectionEntry.condition), which does sync.
 *
 * A copy is told apart by its printing, its finish and its number among the copies of that printing
 * and finish — copy 1, copy 2… counted through the Unsorted pile first, then the binders. Photos go
 * in the Value by place PDF report beside the cards they're of. "Ask for photos when adding a card
 * worth over $X" is a setting kept on the device too.
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/copyPhotos.ts rule for rule, with the
 * same tests (CopyPhotosTest.kt ↔ tests/collection/copyPhotos.test.ts).
 */

/** The key a copy's photos are kept under: "scryfallId|foil|2" ("" for a plain copy). */
fun photoKey(scryfallId: String, foil: Boolean, n: Int): String = "$scryfallId|${if (foil) "foil" else ""}|$n"

/**
 * One copy of a card: its key ([photoKey]), printing, finish and number, the binder it's in, and where
 * it's kept — "Rares binder p2 s1", "Red box › Red", "No place yet". [placeId] null with no place.
 */
data class CopyRef(
    val key: String,
    val collectionId: String,
    val scryfallId: String,
    val name: String,
    val foil: Boolean,
    val n: Int,
    val placeId: String?,
    val where: String,
    val condition: String?
)

/** "Rares binder p2 s1", "Red box › Red", "Red box". */
fun copySpotLabel(place: StoragePlace, line: CopyPlace): String = when {
    line.page != null && line.slot != null -> "${place.name} p${line.page} s${line.slot}"
    line.section != null -> "${place.name} › ${line.section}"
    else -> place.name
}

/**
 * Every copy owned of the card called [name] (any printing), one by one: the Unsorted pile's, then the
 * binders'; in each entry the plain copies, then the foils — those with a place first, in the order of
 * its lines.
 */
fun copiesOfCard(collections: List<Collection>, name: String): List<CopyRef> {
    val places = placesOf(collections).associateBy { it.id }
    val mine = collections.filter { it.kind != CollectionType.WISHLIST }
    val counted = HashMap<String, Int>()
    val out = mutableListOf<CopyRef>()
    for (c in mine.filter { it.isUnsorted } + mine.filter { !it.isUnsorted }) for (e in c.entries) {
        if (!sameCardName(e.name, name)) continue
        val lines = placedCopies(e).filter { it.placeId in places }
        for (foil in listOf(false, true)) {
            var left = if (foil) e.foilQuantity else e.quantity
            fun add(placeId: String?, where: String) {
                val k = "${e.scryfallId}|$foil"
                val n = (counted[k] ?: 0) + 1
                counted[k] = n
                out += CopyRef(photoKey(e.scryfallId, foil, n), c.id, e.scryfallId, e.name, foil, n, placeId, where, e.condition)
            }
            for (line in lines.filter { it.isFoil == foil }) repeat(minOf(line.qty, left).coerceAtLeast(0)) {
                add(line.placeId, copySpotLabel(places.getValue(line.placeId), line))
                left--
            }
            repeat(left.coerceAtLeast(0)) { add(null, "No place yet") }
        }
    }
    return out
}

/**
 * A copy's photos and details, kept on the device. [front] and [back]: the photo files' names (null:
 * none taken); [boughtUsd]: what it cost, in US dollars; [photographedAt]: when the photos were last
 * taken, in milliseconds.
 */
data class CopyPhoto(
    val key: String,
    val scryfallId: String,
    val name: String,
    val foil: Boolean = false,
    val front: String? = null,
    val back: String? = null,
    val boughtUsd: Double? = null,
    val boughtWhere: String? = null,
    val photographedAt: Long? = null
) {
    val hasPhotos: Boolean get() = front != null || back != null
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "5 Oct 2026" for the calendar day "2026-10-05". */
fun photoDayLabel(day: String): String {
    val parts = day.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3 || parts[1] !in 1..12) return day
    return "${parts[2]} ${MONTHS[parts[1] - 1]} ${parts[0]}"
}

/** "$58 · Card shop", "$58", "Card shop", or "" — [money] writes the dollars in the user's currency. */
fun boughtLabel(photo: CopyPhoto?, money: (Double) -> String): String =
    listOfNotNull(photo?.boughtUsd?.let(money), photo?.boughtWhere?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" · ")

/** Whether to ask for photos of a card just added: it's worth over the setting ([over], US dollars; null: never ask). */
fun askForPhotos(usd: Double?, over: Double?): Boolean = over != null && over >= 0 && usd != null && usd > over

/** The photos to put in the report: copies still owned ([owned] scryfallIds) with a photo, by name. */
fun photosForReport(photos: List<CopyPhoto>, owned: Set<String>): List<CopyPhoto> =
    photos.filter { it.hasPhotos && it.scryfallId in owned }.sortedWith(compareBy<CopyPhoto>({ it.name.lowercase() }, { it.key }))
