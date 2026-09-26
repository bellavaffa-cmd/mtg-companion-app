package com.mtgcompanion.app.data.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading a badge's description, and turning a picture into the bytes it draws. */
class BadgeImageTest {

    // ---- What the badge says it is ------------------------------------------------------------

    @Test
    fun `a black-white-red badge reports its size and its own colour codes`() {
        val config = parseBadgeConfig(bwrTlv().fromHex(), "9000".fromHex())
        assertEquals(240, config.width)
        assertEquals(416, config.height)
        assertEquals(3, config.pictureCapacity)
        assertEquals(2, config.bitsPerPixel)
        // The codes come off the badge. Assuming them is how a picture comes out inverted.
        assertEquals(0b00, config.palette[BadgeColor.BLACK])
        assertEquals(0b01, config.palette[BadgeColor.WHITE])
        assertEquals(0b11, config.palette[BadgeColor.RED])
        assertEquals(null, config.palette[BadgeColor.YELLOW])
        assertEquals(24_960, config.imageBytes)
    }

    @Test
    fun `a four-colour badge announces itself and reports twice its real height`() {
        val tlv = buildString {
            append("A007").append("00").append("00").append("30").append("0340").append("00F0")  // 832 high
            append("A105").append("00").append("33").append("00").append("28").append("58")
            append("B101").append("01")
            append("9000")
        }
        val marker = "4_color Screen".toByteArray(Charsets.US_ASCII).toHex()
        val config = parseBadgeConfig(tlv.fromHex(), (marker + "9000").fromHex())
        assertTrue(config.fourColour)
        assertEquals(416, config.height)
        assertEquals(0b10, config.palette[BadgeColor.YELLOW])
        assertEquals(0b11, config.palette[BadgeColor.RED])
    }

    @Test
    fun `a badge that says nothing useful still gets sensible defaults`() {
        assertEquals(DEFAULT_BADGE, parseBadgeConfig(ByteArray(0)))
        assertEquals(DEFAULT_BADGE, parseBadgeConfig("6A829000".fromHex()))
    }

    @Test
    fun `a truncated record is ignored instead of throwing`() {
        // A0 claims seven value bytes and supplies three.
        val config = parseBadgeConfig("A007000030".fromHex())
        assertEquals(DEFAULT_BADGE.width, config.width)
    }

    // ---- Packing ------------------------------------------------------------------------------

    /** A tiny badge, so a packed row can be read by eye. */
    private val tiny = DEFAULT_BADGE.copy(width = 4, height = 1)

    private fun row(vararg argb: Int) = ArgbImage(4, 1, argb)

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val red = 0xFFFF0000.toInt()

    @Test
    fun `four pixels pack into one byte, most significant first`() {
        // Written left to right the row is black, white, red, white; the panel scans the other way,
        // so what goes on the wire is white, red, white, black: 01 11 01 00.
        val packed = packForBadge(row(black, white, red, white), tiny, dither = false)
        assertEquals(1, packed.size)
        assertEquals(0b01110100.toByte(), packed[0])
    }

    @Test
    fun `the picture is always mirrored, because the panel scans the other way`() {
        val packed = packForBadge(row(black, black, white, white), tiny, dither = false)
        // white white black black -> 01 01 00 00
        assertEquals(0b01010000.toByte(), packed[0])
    }

    @Test
    fun `a badge that wants the picture upside-down gets it that way`() {
        val image = ArgbImage(2, 2, intArrayOf(black, black, white, white))
        val config = DEFAULT_BADGE.copy(width = 2, height = 2, flipVertical = true)
        val packed = packForBadge(image, config, dither = false)
        // Rows swapped: white white then black black, each mirrored (which changes nothing here).
        assertEquals(1, packed.size)
        assertEquals(0b01010000.toByte(), packed[0])
    }

    @Test
    fun `colours the badge does not have are never chosen`() {
        // Yellow on a black-white-red panel has to become something the panel can show.
        val yellow = 0xFFFFFF00.toInt()
        val packed = packForBadge(row(yellow, yellow, yellow, yellow), tiny, dither = false)
        val codes = (0 until 4).map { (packed[0].toInt() shr (6 - it * 2)) and 0b11 }
        assertTrue(codes.all { it == 0b00 || it == 0b01 || it == 0b11 })
    }

    @Test
    fun `transparency is flattened onto white rather than going black`() {
        val clear = 0x00000000
        val packed = packForBadge(row(clear, clear, clear, clear), tiny, dither = false)
        assertEquals(0b01010101.toByte(), packed[0])
    }

    @Test
    fun `a two-colour badge packs eight pixels to a byte`() {
        val mono = DEFAULT_BADGE.copy(
            width = 8, height = 1, bitsPerPixel = 1,
            palette = mapOf(BadgeColor.BLACK to 0, BadgeColor.WHITE to 1)
        )
        val pixels = intArrayOf(black, black, black, black, white, white, white, white)
        val packed = packForBadge(ArgbImage(8, 1, pixels), mono, dither = false)
        assertEquals(1, packed.size)
        // Mirrored: white white white white black black black black.
        assertEquals(0b11110000.toByte(), packed[0])
    }

    @Test
    fun `a full screen packs to exactly what the badge expects`() {
        val pixels = IntArray(DEFAULT_BADGE.width * DEFAULT_BADGE.height) { white }
        val packed = packForBadge(ArgbImage(DEFAULT_BADGE.width, DEFAULT_BADGE.height, pixels), DEFAULT_BADGE)
        assertEquals(DEFAULT_BADGE.imageBytes, packed.size)
    }

    @Test
    fun `a picture of the wrong size is fitted and centred rather than stretched`() {
        // A wide strip on a tall panel: it lands in the middle with white above and below.
        val strip = ArgbImage(4, 1, intArrayOf(black, black, black, black))
        val fitted = fitTo(strip, 4, 3)
        assertEquals(12, fitted.pixels.size)
        assertEquals(0xFFFFFFFF.toInt(), fitted.pixels[0])
        assertEquals(black, fitted.pixels[4])
        assertEquals(0xFFFFFFFF.toInt(), fitted.pixels[8])
    }

    @Test
    fun `dithering fakes a shade the badge cannot show`() {
        // Mid grey has no code on a black-and-white-and-red panel, so it has to come out as a mix.
        val grey = 0xFF808080.toInt()
        val config = DEFAULT_BADGE.copy(width = 16, height = 16)
        val pixels = IntArray(16 * 16) { grey }
        val packed = packForBadge(ArgbImage(16, 16, pixels), config, dither = true)
        val codes = packed.flatMap { b -> (0 until 4).map { (b.toInt() shr (6 - it * 2)) and 0b11 } }
        assertTrue("expected some black", codes.any { it == 0b00 })
        assertTrue("expected some white", codes.any { it == 0b01 })
    }

    @Test
    fun `the preview shows what the badge will show, the right way round`() {
        // The mirroring is a quirk of how the panel reads its own memory. Showing it to the user
        // would just look like a bug, so the preview keeps the dithering and drops the flip.
        val image = row(black, white, white, white)
        val preview = previewForBadge(image, tiny, dither = false)
        assertEquals(black, preview.pixels[0])
        assertEquals(white, preview.pixels[1])
    }

    @Test
    fun `the preview only ever uses colours the badge has`() {
        val green = 0xFF00FF00.toInt()
        val preview = previewForBadge(row(green, green, green, green), tiny, dither = true)
        val allowed = setOf(black, white, 0xFFFF0000.toInt())
        assertTrue(preview.pixels.all { it in allowed })
    }
}
