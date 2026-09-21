package com.mtgcompanion.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reading the card index and finding the nearest printings. The web app has the same checks — see
 * MtgCompanionWeb/tests/scan/cardIndex.test.ts.
 */
class CardIndexTest {

    private data class Row(val id: String, val face: Int, val name: String, val set: String, val number: String, val group: Int, val print: IntArray)

    /** A card index file, written the way build_index.py writes one. */
    private fun indexFile(rows: List<Row>, dim: Int, featDim: Int, comps: List<FloatArray>, scale: Float): ByteArray {
        val names = rows.map { it.name }.distinct().sorted()
        val sets = rows.map { it.set }.distinct().sorted()
        val numbers = rows.map { it.number }.distinct().sorted()
        fun json(l: List<String>) = l.joinToString(",", "[", "]") { "\"$it\"" }
        val meta = "{\"names\":${json(names)},\"sets\":${json(sets)},\"numbers\":${json(numbers)}}".toByteArray()
        val n = rows.size
        val b = ByteBuffer.allocate(28 + 4 * featDim + 4 * dim * featDim + n * dim + 16 * n + n + 4 * n + 2 * n + 2 * n + 4 * n + 4 + meta.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        b.put("MBIX".toByteArray()).putInt(1).putInt(n).putInt(dim).putInt(featDim).putFloat(scale).putInt(224)
        repeat(featDim) { b.putFloat(0f) }
        comps.forEach { row -> row.forEach { b.putFloat(it) } }
        rows.forEach { r -> r.print.forEach { b.put(it.toByte()) } }
        rows.forEach { r -> r.id.replace("-", "").chunked(2).forEach { b.put(it.toInt(16).toByte()) } }
        rows.forEach { b.put(it.face.toByte()) }
        rows.forEach { b.putInt(names.indexOf(it.name)) }
        rows.forEach { b.putShort(sets.indexOf(it.set).toShort()) }
        rows.forEach { b.putShort(numbers.indexOf(it.number).toShort()) }
        rows.forEach { b.putInt(it.group) }
        b.putInt(meta.size).put(meta)
        return b.array()
    }

    private fun id(k: Int) = "0000000$k-aaaa-bbbb-cccc-0123456789ab"

    // Three features, fingerprinted to two numbers: the first two features, as they are.
    private val index = CardIndex(indexFile(listOf(
        Row(id(1), 0, "Sol Ring", "msc", "213", 0, intArrayOf(127, 0)),
        Row(id(2), 0, "Sol Ring", "sld", "2330", 1, intArrayOf(0, 127)),
        Row(id(3), 0, "Lightning Bolt", "2xm", "129", 2, intArrayOf(90, 90)),
        Row(id(4), 1, "Delver of Secrets // Insectile Aberration", "isd", "51", 3, intArrayOf(-127, 0))
    ), 2, 3, listOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f)), 127f))

    @Test
    fun aCardIndexReadsBackWhatWasWritten() {
        assertEquals(4, index.count)
        assertEquals(2, index.dim)
        assertEquals(IndexEntry(1, id(2), 0, "Sol Ring", "sld", "2330", 1), index.entry(1))
        assertEquals(1, index.entry(3).face)
    }

    @Test
    fun aPicturesFeaturesAreFingerprintedLikeTheIndexsOwn() {
        assertArrayEquals(byteArrayOf(127, 0), index.fingerprint(floatArrayOf(5f, 0f, 3f)))
        assertArrayEquals(byteArrayOf(90, 90), index.fingerprint(floatArrayOf(2f, 2f, 0f)))
    }

    @Test
    fun theNearestPicturesComeFirstAndTheBestOfSeveralLooksCounts() {
        val near = index.nearest(listOf(index.fingerprint(floatArrayOf(1f, 0.1f, 0f))), 2)
        assertEquals(listOf(id(1), id(3)), near.map { it.entry.id })
        assertTrue(near[0].score > 0.99f && near[0].score <= 1.01f)
        val two = index.nearest(listOf(index.fingerprint(floatArrayOf(1f, 0.1f, 0f)), index.fingerprint(floatArrayOf(0f, 1f, 0f))), 1)
        assertTrue(two[0].score > 0.99f)
    }

    @Test
    fun aSearchCanBeKeptToThePrintingsOfOneNameEitherFaceOfADoubleFacedCard() {
        assertArrayEquals(intArrayOf(0, 1), index.rowsNamed("sol ring"))
        assertArrayEquals(intArrayOf(3), index.rowsNamed("Insectile Aberration"))
        assertArrayEquals(intArrayOf(3), index.rowsNamed("Delver of Secrets // Insectile Aberration"))
        assertArrayEquals(intArrayOf(), index.rowsNamed("Nope"))
        val within = index.nearest(listOf(index.fingerprint(floatArrayOf(1f, 1f, 0f))), 1, index.rowsNamed("Sol Ring"))
        assertEquals("Sol Ring", within[0].entry.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun somethingThatIsNotACardIndexIsRefused() {
        CardIndex(ByteArray(40))
    }
}
