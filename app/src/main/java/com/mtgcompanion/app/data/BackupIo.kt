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
