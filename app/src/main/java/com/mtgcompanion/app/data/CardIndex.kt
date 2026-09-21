package com.mtgcompanion.app.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.json.JSONObject

/**
 * The card index: a short fingerprint of what every English paper printing looks like, made by an
 * image model from Scryfall's pictures (see MtgCompanionWeb/tools/card-index). A card in the camera,
 * fingerprinted the same way, is looked up by which fingerprints it's nearest to — how the scanner
 * knows a card by sight, even when its name can't be read. Mirrors the web app's
 * src/scan/cardIndex.ts; the file layout is documented in tools/card-index/build_index.py.
 */

data class IndexEntry(
    /** Which picture in the index. */
    val row: Int,
    /** The Scryfall id of the printing. */
    val id: String,
    /** 0 for a card's front, 1 for the back of a double-faced one. */
    val face: Int,
    val name: String,
    val set: String,
    val number: String,
    /** Printings sharing one illustration share this: the picture says which art, not which of them. */
    val group: Int
)

data class IndexMatch(val entry: IndexEntry, /** How alike, from -1 to 1. */ val score: Float)

class CardIndex(bytes: ByteArray) {
    val count: Int
    val dim: Int
    val featDim: Int
    val inputSize: Int
    private val scale: Float
    private val mean: FloatArray
    private val comps: FloatArray
    private val prints: ByteArray
    private val ids: ByteArray
    private val faces: ByteArray
    private val nameOf: IntArray
    private val setOf: ShortArray
    private val numberOf: ShortArray
    private val groups: IntArray
    private val names: List<String>
    private val sets: List<String>
    private val numbers: List<String>

    init {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 28 && b.getInt(0) == MAGIC) { "Not a card index" }
        require(b.getInt(4) == 1) { "Card index version not understood" }
        count = b.getInt(8)
        dim = b.getInt(12)
        featDim = b.getInt(16)
        scale = b.getFloat(20)
        inputSize = b.getInt(24)
        b.position(28)
        mean = FloatArray(featDim).also { b.asFloatBuffer().get(it); b.position(b.position() + 4 * featDim) }
        comps = FloatArray(dim * featDim).also { b.asFloatBuffer().get(it); b.position(b.position() + 4 * dim * featDim) }
        prints = ByteArray(count * dim).also { b.get(it) }
        ids = ByteArray(16 * count).also { b.get(it) }
        faces = ByteArray(count).also { b.get(it) }
        nameOf = IntArray(count).also { b.asIntBuffer().get(it); b.position(b.position() + 4 * count) }
        setOf = ShortArray(count).also { b.asShortBuffer().get(it); b.position(b.position() + 2 * count) }
        numberOf = ShortArray(count).also { b.asShortBuffer().get(it); b.position(b.position() + 2 * count) }
        groups = IntArray(count).also { b.asIntBuffer().get(it); b.position(b.position() + 4 * count) }
        val metaLength = b.getInt()
        val meta = JSONObject(String(bytes, b.position(), metaLength, Charsets.UTF_8))
        fun list(key: String) = meta.getJSONArray(key).let { a -> List(a.length()) { a.getString(it) } }
        names = list("names")
        sets = list("sets")
        numbers = list("numbers")
    }

    // Built the first time a name is asked for: most scans never need it.
    private val byName: Map<String, IntArray> by lazy {
        val map = HashMap<String, MutableList<Int>>()
        for (r in 0 until count) {
            val full = names[nameOf[r]].lowercase()
            for (key in (listOf(full) + full.split(" // ")).toSet()) map.getOrPut(key) { mutableListOf() } += r
        }
        map.mapValues { it.value.toIntArray() }
    }

    fun entry(row: Int): IndexEntry {
        val hex = StringBuilder(32)
        for (i in 0 until 16) hex.append("%02x".format(ids[row * 16 + i].toInt() and 0xFF))
        val h = hex.toString()
        return IndexEntry(
            row = row,
            id = "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}",
            face = faces[row].toInt(),
            name = names[nameOf[row]],
            set = sets[setOf[row].toInt() and 0xFFFF],
            number = numbers[numberOf[row].toInt() and 0xFFFF],
            group = groups[row]
        )
    }

    /** The model's feature vector for a picture, turned into a fingerprint like the index's own. */
    fun fingerprint(features: FloatArray): ByteArray {
        val z = FloatArray(dim)
        for (d in 0 until dim) {
            var sum = 0f
            val base = d * featDim
            for (i in 0 until featDim) sum += (features[i] - mean[i]) * comps[base + i]
            z[d] = sum
        }
        val norm = sqrt(z.sumOf { (it * it).toDouble() }).toFloat().takeIf { it > 0f } ?: 1f
        return ByteArray(dim) { (z[it] / norm * scale).roundToInt().coerceIn(-127, 127).toByte() }
    }

    /** The rows of every printing of [name] (either face's name, for a double-faced card). */
    fun rowsNamed(name: String): IntArray = byName[name.lowercase()] ?: IntArray(0)

    /**
     * The [most] nearest pictures to any of [looks] (fingerprints of the camera's card, measured a
     * few ways — the best of them counts), among [rows] or the whole index, nearest first.
     */
    fun nearest(looks: List<ByteArray>, most: Int = 5, rows: IntArray? = null): List<IndexMatch> {
        val topRow = IntArray(most)
        val topDot = IntArray(most) { Int.MIN_VALUE }
        var filled = 0
        fun consider(r: Int) {
            val base = r * dim
            var best = Int.MIN_VALUE
            for (q in looks) {
                var dot = 0
                for (d in 0 until dim) dot += q[d] * prints[base + d]
                if (dot > best) best = dot
            }
            if (filled < most || best > topDot[filled - 1]) {
                var i = if (filled < most) filled++ else filled - 1
                while (i > 0 && topDot[i - 1] < best) { topDot[i] = topDot[i - 1]; topRow[i] = topRow[i - 1]; i-- }
                topDot[i] = best
                topRow[i] = r
            }
        }
        if (rows != null) for (r in rows) consider(r) else for (r in 0 until count) consider(r)
        val unit = scale * scale
        return (0 until filled).map { IndexMatch(entry(topRow[it]), topDot[it] / unit) }
    }

    private companion object {
        const val MAGIC = 0x5849424d // "MBIX", little-endian
    }
}
