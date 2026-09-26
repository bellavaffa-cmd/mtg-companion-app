package com.mtgcompanion.app.ui.badge

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.mtgcompanion.app.data.nfc.ArgbImage

/**
 * What a token looks like on the badge.
 *
 * Not the card. A badge is 240×416 in three colours, and a whole token card shrunk into that comes
 * out as grey mush — the rules text is unreadable at any size the panel can show. What a token
 * needs to say across a table is what it is, what type it is and how big it is, so that's what gets
 * drawn: the art, the name, the type line, and the power and toughness large enough to read from
 * the other side of the table.
 *
 * Everything except the art is drawn in pure black, white and red, which the dithering then leaves
 * alone — that's why the text stays crisp while the art goes grainy.
 */
/**
 * How hard to push the art before it's dithered.
 *
 * On a screen the lifted version looks right; on real e-paper the white is closer to newsprint grey
 * and the same picture reads washed out, so the useful control is how much of the art is allowed to
 * fall to black.
 */
enum class BadgeInk(val contrast: Float, val brightness: Float) {
    LIGHT(1.25f, 44f),
    NORMAL(1.25f, 26f),
    HEAVY(1.35f, -6f)
}

data class TokenFaceSpec(
    val name: String,
    val typeLine: String?,
    /** "1/1", or null for anything that isn't a creature. */
    val powerToughness: String?,
    /** Art crop, already decoded. A token with no picture yet still makes a usable badge. */
    val art: Bitmap?,
    /** Emblems and the like get a red band, so they're not mistaken for a creature at a glance. */
    val emblem: Boolean = false,
    /** How much of the art is allowed to fall to black. */
    val ink: BadgeInk = BadgeInk.NORMAL,
    /**
     * Reverse the writing: white text on a black band, art left alone.
     *
     * Only the band, because a negative of the art stops being a picture of anything — the point of
     * the art is recognising the token across a table, and inverting it works against that. The
     * writing is where reversing actually helps, since a solid black band is the strongest thing
     * three colours can do.
     */
    val invert: Boolean = false
)

private const val BLACK = Color.BLACK
private const val WHITE = Color.WHITE
private val RED = Color.rgb(255, 0, 0)

/** Draw [spec] at [width]×[height] — the badge's own size, which it reports rather than us assuming. */
fun renderTokenFace(spec: TokenFaceSpec, width: Int, height: Int): ArgbImage {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(WHITE)

    val margin = (width * 0.033f).coerceAtLeast(4f)
    val ptSize = height * 0.135f
    val artHeight = height * 0.60f

    // Art, cropped to fill rather than letterboxed: a band of white above a token reads as a mistake.
    val artRect = RectF(0f, 0f, width.toFloat(), artHeight)
    spec.art?.let { drawCropped(canvas, it, artRect, spec.ink) }

    // Under the art: either a rule so a pale picture doesn't bleed into the name, or — reversed —
    // a solid band, which separates the two by itself and needs no rule.
    val ink = if (spec.invert) WHITE else BLACK
    val paper = if (spec.invert) BLACK else WHITE
    val band = Paint().apply { color = paper; style = Paint.Style.FILL }
    if (spec.invert) {
        canvas.drawRect(0f, artHeight, width.toFloat(), height.toFloat(), band)
    } else {
        canvas.drawRect(0f, artHeight, width.toFloat(), artHeight + height * 0.007f, Paint().apply { color = BLACK })
    }

    val textTop = artHeight + height * 0.03f
    val textWidth = (width - margin * 2).toInt()
    // The power and toughness sits in the bottom corner, so the text above stops short of it.
    val textBottom = height - margin - if (spec.powerToughness != null) ptSize else 0f

    // Name: as large as three lines of it will allow, because a two-word token shouldn't be tiny.
    val namePaint = TextPaint().apply {
        isAntiAlias = true
        color = ink
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val nameLayout = fitText(spec.name, namePaint, textWidth, maxLines = 3, from = height * 0.075f, to = height * 0.035f)
    canvas.withTranslation(margin, textTop) { nameLayout.draw(canvas) }

    // Type line under it, smaller, and never more than two lines.
    var y = textTop + nameLayout.height + height * 0.018f
    val type = spec.typeLine?.substringAfter("Token ")?.trim().orEmpty()
    if (type.isNotEmpty()) {
        val typePaint = TextPaint().apply {
            isAntiAlias = true
            color = if (spec.emblem) RED else ink
            typeface = Typeface.DEFAULT
        }
        val typeLayout = fitText(type, typePaint, textWidth, maxLines = 2, from = height * 0.037f, to = height * 0.024f)
        if (y + typeLayout.height <= textBottom) {
            canvas.withTranslation(margin, y) { typeLayout.draw(canvas) }
            y += typeLayout.height
        }
    }

    // Power and toughness, reversed out of a black block so it carries across a table.
    spec.powerToughness?.let { pt ->
        val paint = Paint().apply {
            isAntiAlias = true
            color = paper
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = ptSize * 0.72f
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        paint.getTextBounds(pt, 0, pt.length, bounds)
        val boxW = (bounds.width() + ptSize * 0.6f).coerceAtMost(width - margin * 2)
        val boxH = ptSize
        val box = RectF(width - margin - boxW, height - margin - boxH, width - margin, height - margin)
        canvas.drawRoundRect(box, boxH * 0.22f, boxH * 0.22f, Paint().apply { color = ink })
        canvas.drawText(pt, box.centerX(), box.centerY() - (paint.descent() + paint.ascent()) / 2, paint)
    }

    // A hairline frame, which stops the badge looking like a misprint when the art is pale.
    val frame = Paint().apply {
        color = BLACK
        style = Paint.Style.STROKE
        strokeWidth = (width * 0.008f).coerceAtLeast(1f)
    }
    val inset = frame.strokeWidth / 2
    canvas.drawRect(inset, inset, width - inset, height - inset, frame)

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    bitmap.recycle()
    return ArgbImage(width, height, pixels)
}

/**
 * Lifted and hardened a little before it's dithered.
 *
 * Three colours have no midtones to spend, so dark art collapses into a solid black rectangle and
 * stops being a picture at all. Pulling the whole thing up and pushing contrast out gives the
 * dithering something to work with; card art is dark far more often than it is blown out, so the
 * lift is worth the occasional washed-out sky.
 */
private fun lift(ink: BadgeInk) = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            ink.contrast, 0f, 0f, 0f, ink.brightness,
            0f, ink.contrast, 0f, 0f, ink.brightness,
            0f, 0f, ink.contrast, 0f, ink.brightness,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

/** Fill [target] with [bitmap], cropping the overhang rather than squashing it. */
private fun drawCropped(canvas: Canvas, bitmap: Bitmap, target: RectF, ink: BadgeInk) {
    if (bitmap.width <= 0 || bitmap.height <= 0) return
    val scale = maxOf(target.width() / bitmap.width, target.height() / bitmap.height)
    val w = bitmap.width * scale
    val h = bitmap.height * scale
    val left = target.centerX() - w / 2
    val top = target.centerY() - h / 2
    canvas.save()
    canvas.clipRect(target)
    canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), Paint().apply {
        isFilterBitmap = true
        colorFilter = lift(ink)
    })
    canvas.restore()
}

/**
 * Lay [text] out as large as it goes without spilling past [maxLines], stepping down from [from] to
 * [to]. Below that it's ellipsised — a token with a paragraph for a name is not worth shrinking the
 * rest of the badge for.
 */
private fun fitText(text: String, paint: TextPaint, width: Int, maxLines: Int, from: Float, to: Float): StaticLayout {
    var size = from
    // Measured uncapped: a layout built with setMaxLines already stops counting at the cap, so
    // asking it how many lines it wanted would always answer "exactly as many as allowed".
    while (size > to && wantedLines(text, paint, width, size) > maxLines) size -= 1f
    return layout(text, paint, width, maxLines, size)
}

private fun wantedLines(text: String, paint: TextPaint, width: Int, size: Float): Int {
    paint.textSize = size
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, maxOf(1, width))
        .setIncludePad(false)
        .build()
        .lineCount
}

private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int, size: Float): StaticLayout {
    paint.textSize = size
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, maxOf(1, width))
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .setIncludePad(false)
        .build()
}

private inline fun Canvas.withTranslation(dx: Float, dy: Float, block: () -> Unit) {
    save()
    translate(dx, dy)
    block()
    restore()
}
