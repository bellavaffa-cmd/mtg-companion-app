package com.mtgcompanion.app.data

/**
 * How crisp a picture is, so the scanner can look at the card's best frame rather than whichever
 * one happened to arrive.
 *
 * This turned out to be the whole ballgame. The card index scores a printing's own picture at 1.000
 * against itself, but two pixels of blur drops that to 0.55 — below where unrelated cards sit — and
 * the phone's frames measured about half as sharp as the pictures the index was built from. Colour
 * casts, dim light and low contrast cost almost nothing by comparison; softness costs everything.
 * Sharpening afterwards makes it worse, because it amplifies noise into detail the model believes.
 *
 * The measure is the variance of a Laplacian: flat areas contribute nothing, edges contribute a
 * lot, so a crisp card scores high and a soft one low. It's computed on a small grey copy, which
 * costs a fraction of a millisecond and is all the precision that choosing between frames needs.
 */

/** The side of the grey copy every picture is measured at, so the numbers can be compared. */
private const val MEASURE_AT = 192

/**
 * How crisp [px] is — bigger is crisper. Only ever compare numbers from this function to each
 * other: the scale has no meaning of its own, and it shifts with the subject.
 */
fun sharpness(px: IntArray, width: Int, height: Int): Float {
    if (width < 8 || height < 8 || px.size < width * height) return 0f
    // A fixed-size grey copy, so a big frame and a small one can be compared.
    val w = MEASURE_AT
    val h = maxOf(8, height * MEASURE_AT / width)
    val grey = FloatArray(w * h)
    for (y in 0 until h) {
        val sy = y.toLong() * height / h
        for (x in 0 until w) {
            val sx = x.toLong() * width / w
            val c = px[(sy * width + sx).toInt()]
            // The usual luma weights; the card's detail is mostly in green anyway.
            grey[y * w + x] = 0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
        }
    }
    var sum = 0.0
    var squares = 0.0
    var n = 0
    for (y in 1 until h - 1) {
        for (x in 1 until w - 1) {
            val i = y * w + x
            val lap = -4f * grey[i] + grey[i - 1] + grey[i + 1] + grey[i - w] + grey[i + w]
            sum += lap
            squares += lap.toDouble() * lap
            n++
        }
    }
    if (n == 0) return 0f
    val mean = sum / n
    return ((squares / n) - mean * mean).toFloat().coerceAtLeast(0f)
}
