package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.ItemMerge
import com.squareup.moshi.JsonReader
import okio.Buffer
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Backup and restore (Settings › Data and speed): everything this phone keeps, in one file you keep —
 * decks and binders (with their storage places, loans, sealed product, graded copies, gear and deck
 * history, which ride along inside them), the copy history, the photos of your copies and the settings
 * worth keeping. The file is JSON, the same in both apps: a backup made on the web restores here and
 * the other way round (each app's own settings only go back into that app). The web app's
 * src/sync/backupFile.ts, rule for rule; BackupIo.kt reads and writes the files.
 *
 * Restoring never deletes anything. Two ways:
 *  - Merge (the default): the backup's decks and binders are merged into what's here with the same
 *    rules the sync uses (ItemMerge), as if this phone and the backup had each added what they hold:
 *    everything here stays, what's only in the backup comes back, a card both have keeps the larger
 *    count, and where both changed something (a deck's name) this phone's wins. Settings here stay.
 *  - Replace: each deck and binder in the backup goes back to how it was in the backup, settings too.
 *    Ones made since the backup are kept.
 * Either way copy history is put together from both, and photos the backup has that are missing here
 * come back (Replace puts the backup's back in place of the ones here). Restoring is a change like any
 * other: signed in, it syncs.
 *
 * The format is versioned: [BACKUP_VERSION] is written into every file, and a file from a newer version
 * is turned down with a message rather than half read.
 */
const val BACKUP_FORMAT = "manabind-backup"
/** The format this app writes, and the newest it reads. */
const val BACKUP_VERSION = 1

data class BackupFile(
    val format: String = BACKUP_FORMAT,
    val version: Int = BACKUP_VERSION,
    /** When it was made, in milliseconds. */
    val createdAt: Long = 0L,
    /** The app that made it: "android" or "web". */
    val from: String = "android",
    val decks: List<Deck> = emptyList(),
    val collections: List<Collection> = emptyList(),
    /** The copy history (CopyHistory.kt), oldest first. */
    val copyHistory: List<CopyMove> = emptyList(),
    /** Each copy's photos and details (CopyPhotos.kt); [photoFiles] holds the pictures. */
    val photos: List<CopyPhoto> = emptyList(),
    /** "Ask for photos when adding a card worth over $X". */
    val photoAskOver: Double? = null,
    /** The photos themselves: their id in [photos] → the JPEG, base64. */
    val photoFiles: Map<String, String> = emptyMap(),
    /** Each app's settings worth keeping, by app ("android", "web"): key → value as that app keeps it. */
    val settings: Map<String, Map<String, String>> = emptyMap()
)

enum class RestoreMode { MERGE, REPLACE }

private val backupAdapter by lazy { localMoshi.adapter(BackupFile::class.java) }

/** The backup of everything given. Samples from the welcome flow aren't the user's, so they're left out. */
fun buildBackup(
    decks: List<Deck>,
    collections: List<Collection>,
    createdAt: Long,
    copyHistory: List<CopyMove> = emptyList(),
    photos: List<CopyPhoto> = emptyList(),
    photoAskOver: Double? = null,
    photoFiles: Map<String, String> = emptyMap(),
    settings: Map<String, String> = emptyMap(),
    from: String = "android"
): BackupFile = BackupFile(
    createdAt = createdAt,
    from = from,
    decks = decks.filterNot { isSample(it) },
    collections = collections.filterNot { isSample(it) },
    copyHistory = copyHistory,
    photos = photos,
    photoAskOver = photoAskOver,
    photoFiles = photoFiles,
    settings = mapOf(from to settings)
)

fun backupJson(backup: BackupFile): String = backupAdapter.toJson(backup)

/** Writes [backup] to [sink] as it goes, rather than as one string first (photos make it big). */
fun writeBackup(backup: BackupFile, sink: okio.BufferedSink) = backupAdapter.toJson(sink, backup)

/** "manabind-backup-2026-10-07.json" for a backup made on [at]'s day. */
fun backupFileName(at: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    "manabind-backup-${Instant.ofEpochMilli(at).atZone(zone).toLocalDate()}.json"

sealed class ParsedBackup {
    data class Ok(val backup: BackupFile) : ParsedBackup()
    /** [reason]: "not-backup", "too-new" or "damaged". */
    data class Refused(val reason: String, val message: String) : ParsedBackup()
}

const val NOT_A_BACKUP = "This file isn't a Manabind backup."
const val BACKUP_TOO_NEW = "This backup was made by a newer version of Manabind. Update the app, then restore it."
const val BACKUP_DAMAGED = "This backup couldn't be read — the file may not have saved completely."

/** Just the format and version, read off the top of the file without reading the rest into objects. */
private fun header(text: String): Pair<String?, Double?>? = runCatching {
    val reader = JsonReader.of(Buffer().writeUtf8(text))
    if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return@runCatching null
    reader.beginObject()
    var format: String? = null
    var version: Double? = null
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "format" -> format = if (reader.peek() == JsonReader.Token.STRING) reader.nextString() else { reader.skipValue(); null }
            "version" -> version = if (reader.peek() == JsonReader.Token.NUMBER) reader.nextDouble() else { reader.skipValue(); null }
            else -> reader.skipValue()
        }
        if (format != null && version != null) break
    }
    format to version
}.getOrNull()

/** Reads a backup file's text: the backup, or why it can't be restored. */
fun parseBackup(text: String): ParsedBackup {
    val head = header(text)
    if (head == null) {
        // A file that starts like a backup but doesn't read was cut short; anything else isn't one.
        return if (text.trimStart().startsWith("{") && text.contains("\"$BACKUP_FORMAT\"")) ParsedBackup.Refused("damaged", BACKUP_DAMAGED)
        else ParsedBackup.Refused("not-backup", NOT_A_BACKUP)
    }
    val (format, version) = head
    if (format != BACKUP_FORMAT || version == null) {
        return if (format == BACKUP_FORMAT) ParsedBackup.Refused("damaged", BACKUP_DAMAGED)
        else ParsedBackup.Refused("not-backup", NOT_A_BACKUP)
    }
    if (version > BACKUP_VERSION) return ParsedBackup.Refused("too-new", BACKUP_TOO_NEW)
    if (version < 1 || version != Math.floor(version)) return ParsedBackup.Refused("damaged", BACKUP_DAMAGED)
    val backup = runCatching { backupAdapter.fromJson(text) }.getOrNull() ?: return ParsedBackup.Refused("damaged", BACKUP_DAMAGED)
    return ParsedBackup.Ok(
        backup.copy(
            from = if (backup.from == "web") "web" else "android",
            decks = backup.decks.filter { it.id.isNotBlank() },
            collections = backup.collections.filter { it.id.isNotBlank() }
        )
    )
}

/** What a backup holds, for the preview before restoring. */
data class BackupSummary(
    val createdAt: Long,
    val from: String,
    val decks: Int,
    val binders: Int,
    val copies: Int,
    val places: Int,
    val loans: Int,
    val sealed: Int,
    val graded: Int,
    val gear: Int,
    val historyEntries: Int,
    val photos: Int
)

private fun isEmptyStanding(c: Collection) = (c.id == WISHLIST_ID || c.id == UNSORTED_COLLECTION_ID) && c.entries.isEmpty()

fun backupSummary(b: BackupFile): BackupSummary {
    val pile = b.collections.firstOrNull { it.isUnsorted }
    return BackupSummary(
        createdAt = b.createdAt,
        from = b.from,
        decks = b.decks.size,
        binders = b.collections.count { !isEmptyStanding(it) },
        // As the Storage tab counts them: binders and the real cards in decks.
        copies = storageSummary(b.collections, b.decks).total,
        places = placesOf(b.collections).size,
        loans = pile?.loans.orEmpty().size,
        sealed = pile?.sealed.orEmpty().sumOf { it.count },
        graded = pile?.graded.orEmpty().size,
        gear = pile?.gear.orEmpty().size,
        historyEntries = b.decks.sumOf { it.history.orEmpty().size },
        photos = b.photoFiles.size
    )
}

private fun n(x: Int) = String.format(Locale.UK, "%,d", x)
private fun count(x: Int, one: String, many: String) = "${n(x)} ${if (x == 1) one else many}"

/** The preview's lines: "12 decks · 40 binders", "24,810 copies in 80 places", … — lines with nothing in them left out. */
fun summaryLines(s: BackupSummary): List<String> {
    val extras = listOfNotNull(
        if (s.loans > 0) count(s.loans, "loan", "loans") else null,
        if (s.sealed > 0) "${n(s.sealed)} sealed" else null,
        if (s.graded > 0) "${n(s.graded)} graded" else null,
        if (s.gear > 0) count(s.gear, "piece of gear", "pieces of gear") else null
    )
    val kept = listOfNotNull(
        if (s.historyEntries > 0) count(s.historyEntries, "deck history entry", "deck history entries") else null,
        if (s.photos > 0) count(s.photos, "photo", "photos") else null
    )
    return listOf(
        "${count(s.decks, "deck", "decks")} · ${count(s.binders, "binder", "binders")}",
        if (s.places > 0) "${count(s.copies, "copy", "copies")} in ${count(s.places, "place", "places")}" else count(s.copies, "copy", "copies"),
        extras.joinToString(" · "),
        kept.joinToString(" · ")
    ).filter { it.isNotEmpty() }
}

/** "Restored 12 decks and 40 binders." — what a restore says when it's done. */
fun restoredMessage(s: BackupSummary, photos: Int): String {
    val what = "${count(s.decks, "deck", "decks")} and ${count(s.binders, "binder", "binders")}"
    return if (photos > 0) "Restored $what, with ${count(photos, "photo", "photos")}." else "Restored $what."
}

/**
 * A deck or binder with nothing in it, as the "base" of a merge: everything on both sides counts as
 * added, and no field matches either side's, so where they differ the merge prefers this phone's.
 */
private fun emptyDeck(d: Deck) = Deck(id = d.id, name = "", gameMode = "", createdAt = 0L, ownership = "")
private fun emptyCollection(c: Collection) = Collection(id = c.id, name = "", createdAt = 0L, type = "")

/**
 * [merged] in the order the cards were in here, then the backup's own: with nothing to agree on, the
 * merge lists everything by id, which would shuffle a binder.
 */
private fun <T> inOrder(merged: List<T>, first: List<T>, then: List<T>, key: (T) -> String): List<T> {
    val rank = HashMap<String, Int>()
    for (e in first + then) rank.putIfAbsent(key(e), rank.size)
    return merged.withIndex().sortedBy { (i, e) -> rank[key(e)] ?: (rank.size + i) }.map { it.value }
}

private fun <T> inOrderOrNull(merged: List<T>?, first: List<T>?, then: List<T>?, key: (T) -> String): List<T>? =
    merged?.let { inOrder(it, first.orEmpty(), then.orEmpty(), key) }

private fun <T : Any> restoreItems(current: List<T>, backup: List<T>, id: (T) -> String, restore: (here: T, saved: T) -> T): List<T> {
    val fromBackup = backup.associateBy(id)
    val out = current.map { here ->
        val saved = fromBackup[id(here)]
        if (saved == null || saved == here) here
        else restore(here, saved).let { if (it == here) here else it }
    }.toMutableList()
    val ids = current.mapTo(HashSet()) { id(it) }
    for (b in backup) if (id(b) !in ids) out += b
    return out
}

/**
 * [current] with [backup]'s decks restored, as [mode] says (see the top of this file). Decks only here
 * are left alone; decks only in the backup come back with their own ids, so another device that still
 * has them agrees on which is which. The sync's merge takes the other side's version of what both
 * added, and its fields where both differ (minePreferred false): this phone's, here, so what's here wins.
 */
fun restoreDecks(current: List<Deck>, backup: BackupFile, mode: RestoreMode): List<Deck> =
    restoreItems(current, backup.decks, { it.id }) { here, saved ->
        if (mode == RestoreMode.REPLACE) saved
        else {
            val m = ItemMerge.mergeDecks(emptyDeck(here), saved, here, minePreferred = false)
            m.copy(
                cards = inOrder(m.cards, here.cards, saved.cards) { it.scryfallId },
                sideboard = inOrder(m.sideboard, here.sideboard, saved.sideboard) { it.scryfallId },
                considering = inOrder(m.considering, here.considering, saved.considering) { it.scryfallId }
            )
        }
    }

/** [current] with [backup]'s binders restored, as [restoreDecks] does the decks. */
fun restoreCollections(current: List<Collection>, backup: BackupFile, mode: RestoreMode): List<Collection> =
    restoreItems(current, backup.collections, { it.id }) { here, saved ->
        if (mode == RestoreMode.REPLACE) saved
        else {
            val m = ItemMerge.mergeCollections(emptyCollection(here), saved, here, minePreferred = false)
            m.copy(
                entries = inOrder(m.entries, here.entries, saved.entries) { it.scryfallId },
                storagePlaces = inOrderOrNull(m.storagePlaces, here.storagePlaces, saved.storagePlaces) { it.id },
                loans = inOrderOrNull(m.loans, here.loans, saved.loans) { it.id },
                sealed = inOrderOrNull(m.sealed, here.sealed, saved.sealed) { it.id },
                graded = inOrderOrNull(m.graded, here.graded, saved.graded) { it.id },
                gear = inOrderOrNull(m.gear, here.gear, saved.gear) { it.id }
            )
        }
    }

/** The copy history after a restore: both logs, each move once, oldest first. */
fun restoredHistory(current: List<CopyMove>, backup: List<CopyMove>, now: Long): List<CopyMove> =
    pruneMoves((current + backup).distinct().withIndex().sortedWith(compareBy({ it.value.at }, { it.index })).map { it.value }, now)

/**
 * The photos after a restore: each copy's from here, with the backup's for copies that have none here —
 * or, to replace, the backup's in place of the ones here. [fromBackup] are the ones the restore takes
 * from the backup; [rename] gives the name a picture is kept under here (the web's ids aren't file
 * names), and the photos returned use it.
 */
data class RestoredPhotos(val photos: List<CopyPhoto>, val fromBackup: List<CopyPhoto>)

fun restoredPhotos(current: List<CopyPhoto>, backup: BackupFile, mode: RestoreMode, rename: (String) -> String = { it }): RestoredPhotos {
    // A photo whose picture isn't in the file comes back without it.
    fun usable(p: CopyPhoto) = p.copy(
        front = p.front?.takeIf { backup.photoFiles.containsKey(it) }?.let(rename),
        back = p.back?.takeIf { backup.photoFiles.containsKey(it) }?.let(rename)
    )
    val here = current.map { it.key }.toSet()
    val fromBackup = backup.photos.filter { mode == RestoreMode.REPLACE || it.key !in here }.map { usable(it) }
    val taken = fromBackup.map { it.key }.toSet()
    return RestoredPhotos(current.filterNot { it.key in taken } + fromBackup, fromBackup)
}

/** A picture's id as a file name on this phone: the web keeps them as "copy_photo:<id>". */
fun photoFileName(id: String): String {
    val safe = id.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.')
    return when {
        safe.isEmpty() -> "photo.jpg"
        safe.endsWith(".jpg") -> safe
        else -> "$safe.jpg"
    }
}
