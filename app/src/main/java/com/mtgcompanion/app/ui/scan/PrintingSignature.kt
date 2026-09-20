package com.mtgcompanion.app.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.mtgcompanion.app.data.Candidate
import com.mtgcompanion.app.data.PRINTING_INSET
import com.mtgcompanion.app.data.PrintingMatch
import com.mtgcompanion.app.data.ScanBox
import com.mtgcompanion.app.data.bestPrinting
import com.mtgcompanion.app.data.cardShaped
import com.mtgcompanion.app.data.signatureFromPixels
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Turning pictures into something [com.mtgcompanion.app.data.PrintingMatch] can compare: the card
 * in the camera's frame, and each printing's picture from Scryfall. The comparing itself is
 * arithmetic and lives there; this is the part that needs a screen and a network.
 */

/** How wide the picture is sampled at before it's boiled down. Past this nothing is gained. */
private const val SAMPLE_W = 64
private const val SAMPLE_H = 88

/** How many printings are worth fetching — enough to cover a basic land, which is the worst case. */
private const val MOST_PRINTINGS = 1000

/** How many pictures are fetched at once — enough to be quick, few enough to be a good guest. */
private const val AT_ONCE = 6

/**
 * A likeness this close is the card itself. The same printing photographed lands around 0.05, and
 * the nearest printing that isn't it rarely gets under 0.4, so there is a wide gap to stop in.
 */
private const val CERTAIN = 0.15f

/**
 * ...but only worth stopping for when there's real fetching left to save. Finishing the list is
 * what tells us whether any *other* printing shares that picture, and it's worth a few seconds in
 * the background to know. Only a basic land — hundreds of printings deep — is long enough that
 * walking all of it costs more than the answer is worth.
 */
private const val WORTH_STOPPING = 150

/** [box] with [PRINTING_INSET] of it taken off each edge. */
private fun ScanBox.inset(): ScanBox = grownBy(-PRINTING_INSET * 2)

/**
 * What the card in the frame looks like, or null when there's nothing worth looking at. [frame] is
 * the camera's picture and [rotation] how far it has to be turned to stand upright.
 */
fun cameraSignature(frame: Bitmap, rotation: Int, guide: ScanBox?): FloatArray? {
    val upright = if (rotation % 360 == 0) frame else {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        runCatching { Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true) }.getOrNull() ?: return null
    }
    val box = (guide ?: ScanBox(0, 0, upright.width, upright.height)).cardShaped().inset()
    val left = box.left.coerceIn(0, upright.width - 1)
    val top = box.top.coerceIn(0, upright.height - 1)
    val right = box.right.coerceIn(left + 1, upright.width)
    val bottom = box.bottom.coerceIn(top + 1, upright.height)
    if (right - left < 20 || bottom - top < 20) return null
    val card = runCatching { Bitmap.createBitmap(upright, left, top, right - left, bottom - top) }.getOrNull() ?: return null
    return signatureOf(card)
}

/** A picture boiled down to a signature, at the size everything is compared at. */
private fun signatureOf(bitmap: Bitmap): FloatArray? {
    val small = runCatching { Bitmap.createScaledBitmap(bitmap, SAMPLE_W, SAMPLE_H, true) }.getOrNull() ?: return null
    val px = IntArray(SAMPLE_W * SAMPLE_H)
    small.getPixels(px, 0, SAMPLE_W, 0, 0, SAMPLE_W, SAMPLE_H)
    return signatureFromPixels(px, SAMPLE_W, SAMPLE_H)
}

/** Signatures worked out already, so a card scanned twice doesn't measure its printings twice. */
private val known = mutableMapOf<String, FloatArray>()

/**
 * A printing's small picture from Scryfall, as a signature. Coil keeps the picture on disk, so a
 * second scan of the same card costs nothing.
 */
private suspend fun signatureOfUrl(context: Context, url: String): FloatArray? {
    known[url]?.let { return it }
    val result = runCatching {
        context.imageLoader.execute(
            // Hardware bitmaps live on the GPU and their pixels can't be read back out.
            ImageRequest.Builder(context).data(url).allowHardware(false).build()
        )
    }.getOrNull()
    val bitmap = (result as? SuccessResult)?.drawable?.let { runCatching { it.toBitmap() }.getOrNull() } ?: return null
    // The picture is trimmed the same way the camera's card is, so the two can be compared at all.
    val whole = ScanBox(0, 0, bitmap.width, bitmap.height).cardShaped().inset()
    val left = whole.left.coerceIn(0, bitmap.width - 1)
    val top = whole.top.coerceIn(0, bitmap.height - 1)
    val right = whole.right.coerceIn(left + 1, bitmap.width)
    val bottom = whole.bottom.coerceIn(top + 1, bitmap.height)
    val cropped = runCatching { Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top) }.getOrNull() ?: return null
    return signatureOf(cropped)?.also { known[url] = it }
}

/** The picture used for matching: small (about 10 KB), and it shows the frame as well as the art. */
private fun pictureOf(card: ScryfallCard): String? =
    card.imageUris?.small ?: card.cardFaces?.firstOrNull()?.imageUris?.small

/**
 * The printing among [printings] that the card the camera saw looks most like. Fetches each
 * printing's picture, so this is meant to run behind the scan rather than in front of it.
 */
suspend fun matchPrinting(
    context: Context,
    camera: FloatArray,
    printings: List<ScryfallCard>
): PrintingMatch<ScryfallCard>? = withContext(Dispatchers.IO) {
    val wanted = printings.take(MOST_PRINTINGS).filter { pictureOf(it) != null }
    val candidates = mutableListOf<Candidate<ScryfallCard>>()
    for (start in wanted.indices step AT_ONCE) {
        val batch = wanted.subList(start, minOf(start + AT_ONCE, wanted.size))
        val measured = coroutineScope {
            batch.map { card -> async { signatureOfUrl(context, pictureOf(card)!!)?.let { Candidate(card, it) } } }.awaitAll()
        }
        candidates += measured.filterNotNull()

        // A basic land has hundreds of printings and they're nearly all somebody's idea of a field
        // at dawn, so the list is walked newest first and dropped as soon as one of them is plainly
        // the card in hand. Stopping means the rest were never looked at, so the printing can't be
        // claimed as the only one with that picture — the scan says "best guess" and offers the rest.
        if (wanted.size - start - AT_ONCE > WORTH_STOPPING) {
            val sure = bestPrinting(camera, candidates)
            if (sure != null && sure.distance <= CERTAIN) return@withContext sure.copy(only = false)
        }
    }
    bestPrinting(camera, candidates)
}
