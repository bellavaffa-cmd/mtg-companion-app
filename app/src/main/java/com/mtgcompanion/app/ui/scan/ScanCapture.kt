package com.mtgcompanion.app.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.mtgcompanion.app.BuildConfig
import com.mtgcompanion.app.data.IndexMatch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Keeping what the scanner saw, so a wrong answer can be explained.
 *
 * A scan works on live camera frames and throws every one away, which means a misread leaves nothing
 * behind but its verdict — and "it said Treasure" doesn't say whether the cause was glare, a blurred
 * title, an outline that caught the table instead of the card, or two printings that genuinely look
 * alike. This writes the frame, the flattened card the model was shown, what was read off it and
 * what the index made of it, so each failure explains itself.
 *
 * Debug builds only. In a release build every method here returns immediately and nothing is written.
 *
 * Files land in the app's own external folder, which `adb pull` can read without root:
 *
 *     adb pull /sdcard/Android/data/com.mtgcompanion.app.debug/files/scan-capture
 *
 * One `scans.jsonl` line per attempt, with `<n>-frame.jpg` (what the camera handed over) and
 * `<n>-flat.jpg` (what the model was shown) beside it.
 *
 * Every call carries the id [begin] handed out, because a lookup runs on while the camera reads the
 * next frames: a single "current attempt" gets the picture from one card and the verdict from
 * another, which is worse than no record at all — it invents failures that never happened.
 */
class ScanCapture(context: Context) {

    /** Off outside debug builds, so no release ever writes a picture of someone's table to disk. */
    val enabled: Boolean = BuildConfig.DEBUG

    private val dir = File(context.applicationContext.getExternalFilesDir(null), "scan-capture")

    // One at a time, off the scanning path: saving a JPEG must never slow a frame down or, worse,
    // throw into it.
    private val io = Executors.newSingleThreadExecutor()

    private val open = java.util.concurrent.ConcurrentHashMap<Int, Attempt>()

    /**
     * Carries on from whatever is already in the folder rather than restarting at 1.
     *
     * Counting from 1 each launch made every restart overwrite the last session's pictures and file
     * two different scans under one id — which is exactly the sort of mix-up this class exists to
     * rule out.
     */
    private val counter = java.util.concurrent.atomic.AtomicInteger(
        runCatching {
            dir.listFiles()?.mapNotNull { it.name.substringBefore('-').toIntOrNull() }?.maxOrNull() ?: 0
        }.getOrDefault(0)
    )

    private class Attempt(val id: Int, val startedAt: Long) {
        val facts = JSONObject()
        val notes = JSONArray()
    }

    /**
     * A card is on its way to being looked up. Returns the id every later call must quote, or 0 when
     * capture is off.
     */
    fun begin(title: String?, bySight: Boolean): Int {
        if (!enabled) return 0
        return runCatching {
            val a = Attempt(counter.incrementAndGet(), System.currentTimeMillis())
            a.facts.put("id", a.id)
            a.facts.put("at", a.startedAt)
            a.facts.put("titleRead", title ?: JSONObject.NULL)
            a.facts.put("bySight", bySight)
            open[a.id] = a
            // A lookup that never comes back would otherwise hold its pictures for ever.
            if (open.size > MAX_OPEN) open.keys.sorted().take(open.size - MAX_OPEN).forEach { finish(it, "lost") }
            a.id
        }.getOrDefault(0)
    }

    /** What the camera handed over — the guide cut, or the whole frame when the guide wasn't used. */
    fun frame(id: Int, bitmap: Bitmap?) = save(id, bitmap, "frame")

    /** What the model was shown: the card found and flattened out (see [FlatCard.lookBitmap]). */
    fun flat(id: Int, bitmap: Bitmap?) = save(id, bitmap, "flat")

    /** The small print strip, as the reader was given it. */
    fun smallPrint(id: Int, bitmap: Bitmap?, read: String?) {
        save(id, bitmap, "smallprint")
        fact(id, "smallPrintRead", read ?: "")
    }

    /** Anything else worth knowing, in the order it happened. */
    fun note(id: Int, text: String) {
        if (!enabled) return
        runCatching { open[id]?.notes?.put(text) }
    }

    fun fact(id: Int, key: String, value: Any?) {
        if (!enabled) return
        runCatching { open[id]?.facts?.put(key, value ?: JSONObject.NULL) }
    }

    /**
     * What the index made of the card. Kept in full rather than just the winner: a wrong pick that
     * was a hair ahead of the right one is a different problem from one that wasn't close.
     */
    fun sight(id: Int, named: String, setCode: String?, fromSmallPrint: Boolean, anywhere: List<IndexMatch>, inName: List<IndexMatch>, inSet: List<IndexMatch>, printing: IndexMatch?) {
        if (!enabled) return
        runCatching {
            val a = open[id] ?: return
            a.facts.put("lookedUpAs", named)
            a.facts.put("setCodeRead", setCode ?: JSONObject.NULL)
            a.facts.put("titleFromSmallPrint", fromSmallPrint)
            a.facts.put("sightAnywhere", matches(anywhere))
            a.facts.put("sightInName", matches(inName))
            a.facts.put("sightInSet", matches(inSet))
            a.facts.put("sightOfPrinting", printing?.let { one(it) } ?: JSONObject.NULL)
        }
    }

    /** How it ended: the card that went in, or why none did. */
    fun finish(id: Int, outcome: String, name: String? = null, set: String? = null, number: String? = null, certain: Boolean? = null) {
        if (!enabled) return
        val done = open.remove(id) ?: return
        runCatching {
            done.facts.put("outcome", outcome)
            done.facts.put("pickedName", name ?: JSONObject.NULL)
            done.facts.put("pickedSet", set ?: JSONObject.NULL)
            done.facts.put("pickedNumber", number ?: JSONObject.NULL)
            done.facts.put("certain", certain ?: JSONObject.NULL)
            done.facts.put("tookMs", System.currentTimeMillis() - done.startedAt)
            done.facts.put("notes", done.notes)
            io.execute {
                runCatching {
                    dir.mkdirs()
                    File(dir, "scans.jsonl").appendText(done.facts.toString() + "\n")
                    prune()
                }.onFailure { Log.w(TAG, "couldn't write the scan record: ${it.message}") }
            }
        }
    }

    private fun save(id: Int, bitmap: Bitmap?, what: String) {
        if (!enabled) return
        if (bitmap == null || open[id] == null) return
        // Copied now: the frame it came from is recycled the moment scanning moves on.
        val copy = runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return
        io.execute {
            runCatching {
                dir.mkdirs()
                File(dir, "$id-$what.jpg").outputStream().use { copy.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            }.onFailure { Log.w(TAG, "couldn't write $what: ${it.message}") }
            copy.recycle()
        }
    }

    /** Keeps the folder from swallowing the phone: the newest [KEEP] attempts' pictures, and no more. */
    private fun prune() {
        val pictures = dir.listFiles { f -> f.name.endsWith(".jpg") } ?: return
        val newest = pictures.mapNotNull { it.name.substringBefore('-').toIntOrNull() }.maxOrNull() ?: return
        pictures.filter { (it.name.substringBefore('-').toIntOrNull() ?: 0) <= newest - KEEP }.forEach { it.delete() }
    }

    private fun matches(list: List<IndexMatch>) = JSONArray().also { arr -> list.take(5).forEach { arr.put(one(it)) } }

    private fun one(match: IndexMatch) = JSONObject().apply {
        put("name", match.entry.name)
        put("set", match.entry.set)
        put("number", match.entry.number)
        put("id", match.entry.id)
        put("score", String.format("%.4f", match.score).toDouble())
    }

    private companion object {
        const val TAG = "ScanCapture"

        /** How many attempts' pictures to keep. At roughly 200 KB an attempt this is tens of MB. */
        const val KEEP = 200

        /** Lookups in flight at once is a handful; far past that means one never came back. */
        const val MAX_OPEN = 16
    }
}
