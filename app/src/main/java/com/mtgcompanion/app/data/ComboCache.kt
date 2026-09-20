package com.mtgcompanion.app.data

import android.content.Context
import com.mtgcompanion.app.network.spellbook.Variant
import com.squareup.moshi.Types
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * What Commander Spellbook said, kept on the device: the combos a card is in, and the combos a
 * decklist holds. Both are slow lookups (about a second), so the same question is never asked
 * twice — the answer is kept for a week, "none" included. Each answer is a file of its own, so
 * keeping one doesn't rewrite the rest. Mirrors the web app's src/api/comboCache.ts.
 */
object ComboCache {

    /** How long an answer is trusted. Combos change when cards are printed or banned, not by the hour. */
    const val TTL_MS = 7L * 24 * 60 * 60 * 1000

    /** Cards kept; the ones asked for longest ago go first. */
    const val KEEP = 200

    /** Decklists kept. A deck's answer is only good for that exact list, so a few is plenty. */
    const val KEEP_DECKS = 20

    private const val CARD_DIR = "combos"
    private const val DECK_DIR = "deck-combos"

    private val variantsAdapter = localMoshi.adapter<List<Variant>>(
        Types.newParameterizedType(List::class.java, Variant::class.java)
    )
    private val deckAdapter = localMoshi.adapter(DeckCombos::class.java)

    private var filesDir: File? = null
    private val cardMemory = ConcurrentHashMap<String, List<Variant>>()
    private val deckMemory = ConcurrentHashMap<String, DeckCombos>()

    fun key(cardName: String): String = cardName.trim().lowercase()

    /**
     * A decklist's key: its commanders and its cards, so a deck that's edited asks again while two
     * decks holding the same cards share the one answer.
     */
    fun deckKey(commanders: List<String>, cards: List<String>): String =
        commanders.map { key(it) }.sorted().joinToString("|") + "#" + cards.map { key(it) }.distinct().sorted().joinToString("|")

    /** A file name for [key]: hashed, so any card or decklist is a name a file system accepts. */
    internal fun fileName(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.trim().lowercase().toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) } + ".json"
    }

    /** Opens the store and clears out what's stale or beyond what's kept. */
    fun init(context: Context) {
        filesDir = context.filesDir
        runCatching {
            dir(CARD_DIR); dir(DECK_DIR)
            sweep(CARD_DIR, KEEP, System.currentTimeMillis())
            sweep(DECK_DIR, KEEP_DECKS, System.currentTimeMillis())
        }
    }

    /** The combos kept for [cardName], or null to go and ask. */
    fun get(cardName: String, now: Long = System.currentTimeMillis()): List<Variant>? =
        cardMemory[key(cardName)] ?: read(CARD_DIR, key(cardName), now) { variantsAdapter.fromJson(it) }
            ?.also { cardMemory[key(cardName)] = it }

    /** Keeps [variants] for [cardName] — an empty list ("no combos") too, since that's an answer. */
    fun put(cardName: String, variants: List<Variant>, now: Long = System.currentTimeMillis()) {
        cardMemory[key(cardName)] = variants
        write(CARD_DIR, key(cardName), variantsAdapter.toJson(variants), KEEP, now)
    }

    /** What's kept for the decklist [deckKey], or null to go and ask. */
    fun getDeck(deckKey: String, now: Long = System.currentTimeMillis()): DeckCombos? =
        deckMemory[deckKey] ?: read(DECK_DIR, deckKey, now) { deckAdapter.fromJson(it) }
            ?.also { deckMemory[deckKey] = it }

    /** Keeps [combos] for the decklist [deckKey]. */
    fun putDeck(deckKey: String, combos: DeckCombos, now: Long = System.currentTimeMillis()) {
        deckMemory[deckKey] = combos
        write(DECK_DIR, deckKey, deckAdapter.toJson(combos), KEEP_DECKS, now)
    }

    /** Which of the answers saved at [savedAt] to keep: the fresh ones, newest first, at most [keep]. */
    fun toKeep(savedAt: Map<String, Long>, keep: Int, now: Long): Set<String> =
        savedAt.filterValues { now - it <= TTL_MS }
            .entries.sortedByDescending { it.value }.take(keep)
            .map { it.key }.toSet()

    private fun dir(name: String): File? = filesDir?.let { File(it, name).apply { mkdirs() } }

    private fun <T> read(folder: String, key: String, now: Long, parse: (String) -> T?): T? {
        val file = dir(folder)?.let { File(it, fileName(key)) } ?: return null
        if (!file.exists() || now - file.lastModified() > TTL_MS) return null
        return runCatching { parse(file.readText()) }.getOrNull()
    }

    private fun write(folder: String, key: String, json: String, keep: Int, now: Long) {
        val target = dir(folder) ?: return
        runCatching {
            File(target, fileName(key)).writeText(json)
            sweep(folder, keep, now)
        }
    }

    /** Deletes what [toKeep] leaves out. */
    private fun sweep(folder: String, keep: Int, now: Long) {
        val files = dir(folder)?.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= keep && files.none { now - it.lastModified() > TTL_MS }) return
        val kept = toKeep(files.associate { it.name to it.lastModified() }, keep, now)
        files.filterNot { it.name in kept }.forEach { it.delete() }
    }
}
