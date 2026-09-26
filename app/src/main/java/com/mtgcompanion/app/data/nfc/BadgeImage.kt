package com.mtgcompanion.app.data.nfc

/**
 * Turning a picture into the bytes a badge draws.
 *
 * Three colours and no greys, so everything between has to be faked by scattering the colours the
 * panel does have — Floyd–Steinberg, the same error diffusion the vendor app uses, which is why a
 * photograph comes out recognisable rather than as four flat blobs.
 *
 * Two details are easy to get wrong and impossible to spot without a badge in hand: the picture is
 * always flipped left-to-right before packing (the panel scans the other way), and the bit pattern
 * for each colour comes from the badge itself, not from a table here.
 */

/** A picture in plain ARGB, row by row from the top left. */
class ArgbImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(pixels.size == width * height) { "Expected ${width * height} pixels, got ${pixels.size}" }
    }
}

/**
 * Pack [image] into the bytes [config]'s badge expects.
 *
 * [dither] off gives flat nearest-colour, which is right for text and line art and wrong for
 * anything photographic.
 */
fun packForBadge(image: ArgbImage, config: BadgeConfig, dither: Boolean = true): ByteArray {
    val fitted = fitTo(image, config.width, config.height)
    val oriented = orient(fitted, flipVertical = config.flipVertical)
    val palette = config.palette.keys.toList()
    require(palette.isNotEmpty()) { "The badge reported no colours" }
    val indices = if (dither) diffuse(oriented, palette) else nearest(oriented, palette)
    return pack(indices, config)
}

/**
 * The same picture as [packForBadge] produces, back in ARGB so a screen can show it.
 *
 * Deliberately not mirrored: the flip is a quirk of how the panel scans its own memory, and showing
 * it to the user would be showing them a bug. What this does share is the dithering, which is the
 * part worth seeing before committing a thirty-second write to it.
 */
fun previewForBadge(image: ArgbImage, config: BadgeConfig, dither: Boolean = true): ArgbImage {
    val fitted = fitTo(image, config.width, config.height)
    val palette = config.palette.keys.toList()
    require(palette.isNotEmpty()) { "The badge reported no colours" }
    val chosen = if (dither) diffuse(fitted, palette) else nearest(fitted, palette)
    return ArgbImage(config.width, config.height, IntArray(chosen.size) { chosen[it].argb() })
}

private fun BadgeColor.argb() = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

// ---- Fitting ----------------------------------------------------------------------------------

/**
 * Scale [image] to sit inside [width]×[height] without distorting it, centred on white.
 *
 * The screens that compose a badge picture already work at the panel's size, so this normally does
 * nothing; it's here so that handing it a stray bitmap gives something sensible instead of a
 * diagonal smear.
 */
internal fun fitTo(image: ArgbImage, width: Int, height: Int): ArgbImage {
    if (image.width == width && image.height == height) return image
    val out = IntArray(width * height) { WHITE_ARGB }
    if (image.width <= 0 || image.height <= 0) return ArgbImage(width, height, out)

    val scale = minOf(width.toDouble() / image.width, height.toDouble() / image.height)
    val drawW = maxOf(1, (image.width * scale).toInt())
    val drawH = maxOf(1, (image.height * scale).toInt())
    val offX = (width - drawW) / 2
    val offY = (height - drawH) / 2

    for (y in 0 until drawH) {
        val srcY = (y.toLong() * image.height / drawH).toInt().coerceIn(0, image.height - 1)
        for (x in 0 until drawW) {
            val srcX = (x.toLong() * image.width / drawW).toInt().coerceIn(0, image.width - 1)
            out[(y + offY) * width + (x + offX)] = image.pixels[srcY * image.width + srcX]
        }
    }
    return ArgbImage(width, height, out)
}

/** Mirror left-to-right, which the panel always wants, and top-to-bottom when it asks. */
internal fun orient(image: ArgbImage, flipVertical: Boolean): ArgbImage {
    val w = image.width
    val h = image.height
    val out = IntArray(w * h)
    for (y in 0 until h) {
        val srcY = if (flipVertical) h - 1 - y else y
        for (x in 0 until w) {
            out[y * w + x] = image.pixels[srcY * w + (w - 1 - x)]
        }
    }
    return ArgbImage(w, h, out)
}

// ---- Quantising -------------------------------------------------------------------------------

private const val WHITE_ARGB = 0xFFFFFFFF.toInt()

/** Flatten transparency onto white — the panel has no way to show it. */
private fun channels(argb: Int): Triple<Double, Double, Double> {
    val a = (argb ushr 24) and 0xFF
    val r = (argb ushr 16) and 0xFF
    val g = (argb ushr 8) and 0xFF
    val b = argb and 0xFF
    if (a == 255) return Triple(r.toDouble(), g.toDouble(), b.toDouble())
    val f = a / 255.0
    val inv = 1.0 - f
    return Triple(r * f + 255 * inv, g * f + 255 * inv, b * f + 255 * inv)
}

private fun closest(r: Double, g: Double, b: Double, palette: List<BadgeColor>): BadgeColor {
    var best = palette[0]
    var bestDistance = Double.MAX_VALUE
    for (colour in palette) {
        val dr = r - colour.red
        val dg = g - colour.green
        val db = b - colour.blue
        val d = dr * dr + dg * dg + db * db
        if (d < bestDistance) { bestDistance = d; best = colour }
    }
    return best
}

private fun nearest(image: ArgbImage, palette: List<BadgeColor>): Array<BadgeColor> =
    Array(image.pixels.size) { i ->
        val (r, g, b) = channels(image.pixels[i])
        closest(r, g, b, palette)
    }

/**
 * Floyd–Steinberg: pick the nearest colour the badge has, then push the error it leaves onto the
 * neighbours that haven't been decided yet. Over a whole picture that reads as shading.
 */
private fun diffuse(image: ArgbImage, palette: List<BadgeColor>): Array<BadgeColor> {
    val w = image.width
    val h = image.height
    val red = DoubleArray(w * h)
    val green = DoubleArray(w * h)
    val blue = DoubleArray(w * h)
    for (i in image.pixels.indices) {
        val (r, g, b) = channels(image.pixels[i])
        red[i] = r; green[i] = g; blue[i] = b
    }

    val out = Array(w * h) { BadgeColor.WHITE }
    for (y in 0 until h) {
        for (x in 0 until w) {
            val i = y * w + x
            val r = red[i].coerceIn(0.0, 255.0)
            val g = green[i].coerceIn(0.0, 255.0)
            val b = blue[i].coerceIn(0.0, 255.0)
            val chosen = closest(r, g, b, palette)
            out[i] = chosen
            val er = r - chosen.red
            val eg = g - chosen.green
            val eb = b - chosen.blue
            spread(red, green, blue, w, h, x + 1, y, er, eg, eb, 7)
            spread(red, green, blue, w, h, x - 1, y + 1, er, eg, eb, 3)
            spread(red, green, blue, w, h, x, y + 1, er, eg, eb, 5)
            spread(red, green, blue, w, h, x + 1, y + 1, er, eg, eb, 1)
        }
    }
    return out
}

private fun spread(
    red: DoubleArray, green: DoubleArray, blue: DoubleArray,
    w: Int, h: Int, x: Int, y: Int,
    er: Double, eg: Double, eb: Double, weight: Int
) {
    if (x < 0 || x >= w || y < 0 || y >= h) return
    val i = y * w + x
    red[i] += er * weight / 16.0
    green[i] += eg * weight / 16.0
    blue[i] += eb * weight / 16.0
}

// ---- Packing ----------------------------------------------------------------------------------

/**
 * Squeeze the colour choices into bytes: each pixel becomes the badge's own bit pattern for its
 * colour, most significant bits first, four pixels to a byte on a colour panel and eight on a
 * black-and-white one.
 */
private fun pack(indices: Array<BadgeColor>, config: BadgeConfig): ByteArray {
    val bits = config.bitsPerPixel
    val perByte = 8 / bits
    val filler = config.palette[BadgeColor.WHITE] ?: 0
    val out = ByteArray((indices.size + perByte - 1) / perByte)
    var byteIndex = 0
    var i = 0
    while (i < indices.size) {
        var value = 0
        for (slot in 0 until perByte) {
            val code = indices.getOrNull(i + slot)?.let { config.palette[it] } ?: filler
            value = (value shl bits) or (code and ((1 shl bits) - 1))
        }
        out[byteIndex++] = value.toByte()
        i += perByte
    }
    return out
}
