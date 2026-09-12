package com.mtgcompanion.app.data.artrecognition

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/** One card's entry in the visual-fingerprint index. */
data class ArtCard(val scryfallId: String, val name: String, val set: String)

/**
 * Result of matching a photographed card's art against the index. [score] is the raw int32 dot
 * product between the (quantized) query and the best match — not meaningful on its own, since it
 * isn't normalized across queries. [margin] (score minus the runner-up's score) is the real
 * confidence signal: a real match sits well clear of every other card in the index, a coincidental
 * near-miss doesn't.
 */
data class ArtMatch(val card: ArtCard, val score: Int, val margin: Int)

/**
 * A brute-force nearest-neighbor visual-fingerprint index: one int8-quantized, L2-normalized
 * MobileNetV2 embedding per unique card artwork (~48k physical, scannable printings). Held
 * entirely in memory (~61MB) — small enough on a modern device, and fast enough via a plain
 * linear scan (no proper ANN index needed for an occasional match, not a per-frame one; a scan of
 * ~48k short int8 rows is a handful of milliseconds).
 */
class ArtIndex(
    private val cards: List<ArtCard>,
    private val vectors: ByteArray,
    private val dim: Int,
    val scale: Float
) {
    val size: Int get() = cards.size

    /**
     * [queryEmbedding] must be the same L2-normalized MobileNetV2 embedding [ArtEmbedder] produces
     * (same model, same preprocessing as this index was built with) — quantized here with the
     * index's own [scale] so query and reference vectors sit on the same integer scale. Returns
     * null only when the index is empty; a low-confidence match still returns one — callers gate
     * on [ArtMatch.margin] themselves.
     */
    fun search(queryEmbedding: FloatArray): ArtMatch? {
        if (cards.isEmpty()) return null
        val q = IntArray(dim) { i ->
            val scaled = queryEmbedding[i] / scale * 127f
            scaled.coerceIn(-127f, 127f).let { Math.round(it) }
        }
        var bestScore = Int.MIN_VALUE
        var bestIdx = -1
        var secondScore = Int.MIN_VALUE
        for (row in cards.indices) {
            var dot = 0
            val base = row * dim
            for (i in 0 until dim) {
                dot += vectors[base + i] * q[i]
            }
            if (dot > bestScore) {
                secondScore = bestScore
                bestScore = dot
                bestIdx = row
            } else if (dot > secondScore) {
                secondScore = dot
            }
        }
        if (bestIdx < 0) return null
        return ArtMatch(cards[bestIdx], bestScore, bestScore - secondScore)
    }
}

/**
 * Parses the `.mtgart` binary index format: magic "MTGA" | version(u16) | count(u32) | dim(u16) |
 * scale(f32), all little-endian, then per card `id`(36 bytes ASCII UUID) + `set`(6 bytes UTF-8,
 * space-padded) + `nameLen`(u16) + `name`(UTF-8), and finally one contiguous block of
 * count*dim int8 vectors. Must match the Python build pipeline's writer exactly (external to this
 * repo — see project notes on the art-recognition data pipeline).
 */
object ArtIndexReader {
    private const val MAGIC = "MTGA"
    private const val SUPPORTED_VERSION = 1

    fun read(file: File): ArtIndex {
        file.inputStream().use { stream ->
            val magic = ByteArray(4)
            if (stream.read(magic) != 4 || String(magic, StandardCharsets.US_ASCII) != MAGIC) {
                throw IOException("Not a valid art-recognition index file")
            }
            val header = ByteArray(2 + 4 + 2 + 4)
            if (stream.read(header) != header.size) throw IOException("Truncated art-recognition index header")
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val version = buf.short.toInt() and 0xFFFF
            if (version != SUPPORTED_VERSION) throw IOException("Unsupported art-recognition index version $version")
            val count = buf.int
            val dim = buf.short.toInt() and 0xFFFF
            val scale = buf.float

            val cards = ArrayList<ArtCard>(count)
            val idBytes = ByteArray(36)
            val setBytes = ByteArray(6)
            val lenBytes = ByteArray(2)
            repeat(count) {
                if (stream.read(idBytes) != 36) throw IOException("Truncated art-recognition index (card id)")
                val id = String(idBytes, StandardCharsets.US_ASCII)
                if (stream.read(setBytes) != 6) throw IOException("Truncated art-recognition index (set code)")
                val set = String(setBytes, StandardCharsets.UTF_8).trim()
                if (stream.read(lenBytes) != 2) throw IOException("Truncated art-recognition index (name length)")
                val nameLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                val nameBytes = ByteArray(nameLen)
                if (stream.read(nameBytes) != nameLen) throw IOException("Truncated art-recognition index (name)")
                cards.add(ArtCard(id, String(nameBytes, StandardCharsets.UTF_8), set))
            }

            val vectors = ByteArray(count * dim)
            var read = 0
            while (read < vectors.size) {
                val n = stream.read(vectors, read, vectors.size - read)
                if (n < 0) throw IOException("Truncated art-recognition index (vectors)")
                read += n
            }
            return ArtIndex(cards, vectors, dim, scale)
        }
    }
}
