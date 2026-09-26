package com.mtgcompanion.app.data.nfc

/**
 * What the badge says it is.
 *
 * Read with one APDU (00 D1 …), which answers a short run of tag/length/value records in a fixed
 * order, and refined by a second (F0 D8 …) whose status word carries the PIN flag. It matters
 * because the same firmware ships on panels from 1.5" to 7.5" in two, three and four colours, and
 * the two-bit codes that mean "red" on one are not the codes on another. Everything downstream
 * reads this rather than assuming the badge in the drawer.
 */
data class BadgeConfig(
    val manufacturer: String,
    /** 0x20 two-colour, 0x30 black/white/red, 0x31 black/white/yellow. */
    val colorCode: Int,
    val width: Int,
    val height: Int,
    /** How many pictures it can hold; slots are addressed by index when drawing. */
    val pictureCapacity: Int,
    /** Each colour the panel has, and the bit pattern that means it on the wire. */
    val palette: Map<BadgeColor, Int>,
    val bitsPerPixel: Int,
    /** Whether the panel wants the picture upside-down. Horizontal flip is always applied. */
    val flipVertical: Boolean = false,
    val pinRequired: Boolean = false,
    /** The rarer four-colour panel, which announces itself in the device check. */
    val fourColour: Boolean = false
) {
    /** How many bytes a full screen takes once packed. */
    val imageBytes: Int get() = (width * height * bitsPerPixel + 7) / 8

    override fun toString() =
        "$manufacturer ${width}x$height, ${palette.size} colours, $pictureCapacity slot(s)"
}

/** The colours these panels can show. Not every badge has all of them. */
enum class BadgeColor(val red: Int, val green: Int, val blue: Int) {
    BLACK(0, 0, 0),
    WHITE(255, 255, 255),
    RED(255, 0, 0),
    YELLOW(255, 255, 0)
}

/** What a badge looks like when it won't say — the 3.7" black/white/red badge, upright. */
val DEFAULT_BADGE = BadgeConfig(
    manufacturer = "unknown",
    colorCode = 0x30,
    width = 240,
    height = 416,
    pictureCapacity = 1,
    palette = mapOf(BadgeColor.BLACK to 0b00, BadgeColor.WHITE to 0b01, BadgeColor.RED to 0b11),
    bitsPerPixel = 2
)

private val MANUFACTURERS = mapOf(
    0x00 to "Jiaxian", 0x10 to "Yuantai", 0x20 to "Aoyi", 0x30 to "Weifeng",
    0x40 to "Pdi", 0x60 to "Jdf", 0x70 to "Dke", 0x80 to "Weixinnuo", 0xF0 to "Other"
)

/** The canonical four-colour encoding, used when the device check says this is one of those. */
private val FOUR_COLOUR = mapOf(
    BadgeColor.BLACK to 0b00, BadgeColor.WHITE to 0b01,
    BadgeColor.YELLOW to 0b10, BadgeColor.RED to 0b11
)

private val FOUR_COLOUR_MARKER = "4_color Screen".toByteArray(Charsets.US_ASCII)

/**
 * Read [tlv] (the answer to 00 D1) and [check] (the answer to F0 D8) into a [BadgeConfig].
 *
 * The records come in a known order, so this walks them positionally and stops caring the moment
 * one isn't where it should be — a badge that answers half of them still tells us enough to draw on.
 * Anything missing keeps its [DEFAULT_BADGE] value.
 */
fun parseBadgeConfig(tlv: ByteArray, check: ByteArray = ByteArray(0)): BadgeConfig {
    val data = tlv + check
    if (data.size < 2 || data[0] != 0xA0.toByte()) return DEFAULT_BADGE

    var config = DEFAULT_BADGE
    var pos = 0

    pos = readTag(data, pos, 0xA0) { v ->
        // Manufacturer, then colour code, then height and width — height first, both big-endian.
        var next = config
        if (v.isNotEmpty()) {
            next = next.copy(manufacturer = MANUFACTURERS[v[0].toInt() and 0xFF]
                ?: "unknown (0x%02X)".format(v[0].toInt() and 0xFF))
        }
        if (v.size >= 3) next = next.copy(colorCode = v[2].toInt() and 0xFF)
        if (v.size >= 5) next = next.copy(height = be16(v, 3))
        if (v.size >= 7) next = next.copy(width = be16(v, 5))
        config = next
    }

    pos = readTag(data, pos, 0xA1) { v ->
        if (v.size < 2) return@readTag
        val colourCount = v[1].toInt() and 0x0F
        // Two-colour panels pack one bit per pixel; everything else two.
        val bits = if (colourCount == 2) 1 else 2
        val shift = (3 - colourCount) + 3
        val mask = (1 shl bits) - 1
        val palette = LinkedHashMap<BadgeColor, Int>()
        for (i in 0 until colourCount) {
            val b = v.getOrNull(2 + i)?.toInt()?.and(0xFF) ?: break
            val colour = when ((b ushr 5) and 0x07) {
                0 -> BadgeColor.BLACK
                1 -> BadgeColor.WHITE
                2 -> BadgeColor.RED
                3 -> BadgeColor.YELLOW
                else -> null
            } ?: continue
            palette[colour] = (b ushr shift) and mask
        }
        if (palette.isNotEmpty()) config = config.copy(palette = palette, bitsPerPixel = bits)
    }

    pos = readTag(data, pos, 0xB1) { v -> if (v.isNotEmpty()) config = config.copy(pictureCapacity = v[0].toInt() and 0xFF) }
    pos = readTag(data, pos, 0xB2) { }   // user data area size; nothing here needs it
    pos = readTag(data, pos, 0xB3) { }   // battery flag; these badges have none
    pos = readTag(data, pos, 0xC0) { }   // app id
    pos = readTag(data, pos, 0xC1) { }   // uid
    readTag(data, pos, 0xD1) { }         // compression flag and COS version

    return applyTrailer(config, data)
}

/**
 * The last bytes of the device check say whether a PIN is set, and a four-colour panel spells that
 * out in ASCII just before its status word. Such a panel reports twice its real height in the A0
 * record, so finding the marker also halves it.
 */
private fun applyTrailer(config: BadgeConfig, data: ByteArray): BadgeConfig {
    if (data.size < 2) return config
    return when (statusOf(data)) {
        SW_OK -> {
            val marker = data.size >= 16 &&
                data.copyOfRange(data.size - 16, data.size - 2).contentEquals(FOUR_COLOUR_MARKER)
            if (marker) config.copy(fourColour = true, palette = FOUR_COLOUR, bitsPerPixel = 2, height = config.height / 2)
            else config
        }
        SW_NO_PIN -> config.copy(pinRequired = true)
        else -> config
    }
}

/** If [tag] is at [pos], hand its value to [parse] and return where the next record starts. */
private inline fun readTag(data: ByteArray, pos: Int, tag: Int, parse: (ByteArray) -> Unit): Int {
    if (pos + 1 >= data.size || (data[pos].toInt() and 0xFF) != tag) return pos
    val length = data[pos + 1].toInt() and 0xFF
    val end = pos + 2 + length
    if (end > data.size) return pos
    parse(data.copyOfRange(pos + 2, end))
    return end
}

private fun be16(v: ByteArray, at: Int) = ((v[at].toInt() and 0xFF) shl 8) or (v[at + 1].toInt() and 0xFF)
