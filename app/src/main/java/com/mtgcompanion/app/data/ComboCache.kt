package com.mtgcompanion.app.data

import android.content.Context
import com.mtgcompanion.app.network.spellbook.Variant
import com.squareup.moshi.Types
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * The combos each card is in, kept on the device. Commander Spellbook is a slow lookup (about a
 * second), so the same card is never asked for twice: what came back is kept for a week, "no
 * combos" included. A card is a file of its own, so keeping one doesn't rewrite the rest.
 * Mirrors the web app's src/api/comboCache.ts.
 */
object ComboCache {

    /** How long an answer is trusted. Combos change when cards are printed or banned, not by the hour. */
    const val TTL_MS = 7L * 24 * 60 * 60 * 1000

    /** Cards kept; the ones asked for longest ago go first. */
    const val KEEP = 200

    private const val DIR = "combos"
    private val adapter = localMoshi.adapter<List<Variant>>(
        Types.newParameterizedType(List::class.java, Variant::class.java)
    )

    private var dir: File? = null
    private val memory = ConcurrentHashMap<String, List<Variant>>()

    fun key(cardName: String): String = cardName.trim().lowercase()

    /** A card's file: its name hashed, so any card name is a name a file system accepts. */
    internal fun fileName(cardName: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key(cardName).toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) } + ".json"
    }

    /** Opens the store and clears out what's stale or beyond [KEEP]. */
    fun init(context: Context) {
        dir = File(context.filesDir, DIR).apply { mkdirs() }
        runCatching { sweep(System.currentTimeMillis()) }
    }

    /** The combos kept for [cardName], or null to go and ask. */
    fun get(cardName: String, now: Long = System.currentTimeMillis()): List<Variant>? {
        memory[key(cardName)]?.let { return it }
        val file = dir?.let { File(it, fileName(cardName)) } ?: return null
        if (!file.exists() || now - file.lastModified() > TTL_MS) return null
        val variants = runCatching { adapter.fromJson(file.readText()) }.getOrNull() ?: return null
        memory[key(cardName)] = variants
        return variants
    }

    /** Keeps [variants] for [cardName] — an empty list ("no combos") too, since that's an answer. */
    fun put(cardName: String, variants: List<Variant>, now: Long = System.currentTimeMillis()) {
        memory[key(cardName)] = variants
        val folder = dir ?: return
        runCatching {
            File(folder, fileName(cardName)).writeText(adapter.toJson(variants))
            sweep(now)
        }
    }

    /** Which of the cards saved at [savedAt] to keep: the fresh ones, newest first, at most [KEEP]. */
    fun toKeep(savedAt: Map<String, Long>, now: Long): Set<String> =
        savedAt.filterValues { now - it <= TTL_MS }
            .entries.sortedByDescending { it.value }.take(KEEP)
            .map { it.key }.toSet()

    /** Deletes the cards [toKeep] leaves out. */
    private fun sweep(now: Long) {
        val files = dir?.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= KEEP && files.none { now - it.lastModified() > TTL_MS }) return
        val keep = toKeep(files.associate { it.name to it.lastModified() }, now)
        files.filterNot { it.name in keep }.forEach { it.delete() }
    }
}
