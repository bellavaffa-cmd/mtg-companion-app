package com.mtgcompanion.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import java.util.Base64

/**
 * The phone's side of a backup (Backup.kt holds the rules): gathering the library, the photos of your
 * copies, the copy history and the settings into the file the user picked (Storage Access Framework),
 * and putting a restored backup's back. The library is restored through the repositories, so the sync
 * sends it to the account like any other change.
 */
class BackupIo(
    private val context: Context,
    private val deckRepository: DeckRepository,
    private val collectionRepository: CollectionRepository,
    private val settingsRepository: SettingsRepository
) {
    /** Writes everything to [uri] (the file Save a backup made). False when it couldn't. */
    suspend fun save(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val saved = CopyPhotoStore.saved.value
            val files = LinkedHashMap<String, String>()
            for (p in saved.photos) for (id in listOfNotNull(p.front, p.back)) {
                if (files.containsKey(id)) continue
                val f = CopyPhotoStore.file(id) ?: continue
                if (f.exists()) files[id] = Base64.getEncoder().encodeToString(f.readBytes())
            }
            val backup = buildBackup(
                decks = deckRepository.decksFlow.first(),
                collections = collectionRepository.collectionsFlow.first(),
                createdAt = System.currentTimeMillis(),
                copyHistory = CopyHistoryStore.moves.value,
                photos = saved.photos,
                photoAskOver = saved.askOver,
                photoFiles = files,
                settings = settingsRepository.backupValues()
            )
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: return@runCatching false
            out.sink().buffer().use { writeBackup(backup, it) }
            true
        }.getOrDefault(false)
    }

    /**
     * The backup made by itself the first time this version ran (binders as views: BinderViews.kt), kept
     * in the app's own storage — once, flagged so it never runs again, and not over one already there.
     * Decks, binders, the copy history and settings; photos stay where they are on the phone. Answers
     * whether one was made now.
     */
    suspend fun saveBeforeBinders(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.getSharedPreferences(BEFORE_BINDERS_PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(BEFORE_BINDERS_DONE, false)) return@runCatching false
            val file = beforeBindersFile(context)
            var made = false
            if (!file.exists()) {
                val decks = deckRepository.decksFlow.first()
                val collections = collectionRepository.collectionsFlow.first()
                if (decks.isNotEmpty() || collections.any { it.entries.isNotEmpty() }) {
                    val backup = buildBackup(
                        decks = decks,
                        collections = collections,
                        createdAt = System.currentTimeMillis(),
                        copyHistory = CopyHistoryStore.moves.value,
                        settings = settingsRepository.backupValues()
                    )
                    val tmp = java.io.File(file.parentFile, "$BEFORE_BINDERS_FILE.tmp")
                    tmp.sink().buffer().use { writeBackup(backup, it) }
                    made = tmp.renameTo(file)
                }
            }
            prefs.edit().putBoolean(BEFORE_BINDERS_DONE, true).apply()
            made
        }.getOrDefault(false)
    }

    /** The backup from before the binders update (saveBeforeBinders): the backup, or null when there's none or it can't be read. */
    suspend fun readBeforeBinders(): ParsedBackup? = withContext(Dispatchers.IO) {
        val file = beforeBindersFile(context)
        if (!file.exists()) null else runCatching { parseBackup(file.readText()) }.getOrNull()
    }

    /** Reads the file at [uri] (the one Restore picked): the backup, or why it can't be restored. */
    suspend fun read(uri: Uri): ParsedBackup = withContext(Dispatchers.IO) {
        val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
        if (text == null) ParsedBackup.Refused("not-backup", "Couldn't read that file.") else parseBackup(text)
    }

    /** Restores [backup] as [mode] says. Answers how many photos came back. */
    suspend fun restore(backup: BackupFile, mode: RestoreMode): Int = withContext(Dispatchers.IO) {
        deckRepository.restoreBackup(backup.decks.mapTo(HashSet()) { it.id }) { restoreDecks(it, backup, mode) }
        collectionRepository.restoreBackup(backup.collections.mapTo(HashSet()) { it.id }) { restoreCollections(it, backup, mode) }
        CopyHistoryStore.replace(restoredHistory(CopyHistoryStore.moves.value, backup.copyHistory, System.currentTimeMillis()))

        val saved = CopyPhotoStore.saved.value
        val (photos, fromBackup) = restoredPhotos(saved.photos, backup, mode, ::photoFileName)
        val wanted = fromBackup.flatMap { listOfNotNull(it.front, it.back) }.toSet()
        val pictures = HashMap<String, ByteArray>()
        for ((id, b64) in backup.photoFiles) {
            val name = photoFileName(id)
            if (name !in wanted || pictures.containsKey(name)) continue
            runCatching { Base64.getDecoder().decode(b64) }.getOrNull()?.let { pictures[name] = it }
        }
        // Pictures of copies whose photos the backup's replaced.
        val kept = photos.flatMap { listOfNotNull(it.front, it.back) }.toSet()
        val dropped = saved.photos.flatMap { listOfNotNull(it.front, it.back) }.filterNot { it in kept }
        CopyPhotoStore.restore(photos, pictures, dropped, if (mode == RestoreMode.REPLACE) backup.photoAskOver else null)

        if (mode == RestoreMode.REPLACE) backup.settings["android"]?.let { settingsRepository.restoreValues(it) }
        fromBackup.count { it.hasPhotos }
    }
}

private const val BEFORE_BINDERS_FILE = "backup-before-binder-views.json"
private const val BEFORE_BINDERS_PREFS = "binder_views"
private const val BEFORE_BINDERS_DONE = "backed_up_before"

/** Where the backup from before the binders update is kept: the app's own storage, never shared. */
fun beforeBindersFile(context: Context): java.io.File = java.io.File(context.filesDir, BEFORE_BINDERS_FILE)
