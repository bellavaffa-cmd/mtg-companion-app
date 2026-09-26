package com.mtgcompanion.app.ui.badge

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mtgcompanion.app.data.nfc.ArgbImage
import com.mtgcompanion.app.data.nfc.BadgeEvent
import com.mtgcompanion.app.data.nfc.BadgeProgress

/**
 * The bits every surface that writes a badge needs, kept out of any one of them.
 *
 * There are three now — the badge screen, the sheet from a deck's tokens, and the sheet on the
 * remote — and they say the same things in the same words because they share this, not because
 * three copies happen to agree today.
 */

/** What the screen says while a write is going, in one place so all three say it identically. */
internal fun badgeStatus(event: BadgeEvent?): String = when (event) {
    null -> "Hold the badge flat against the back of your phone when you're ready. It takes about half a minute."
    BadgeEvent.Waiting -> "Hold the badge against the back of your phone — and keep it there."
    is BadgeEvent.Found -> "Found it. Keep holding."
    is BadgeEvent.Working -> when (val p = event.progress) {
        BadgeProgress.Reading -> "Reading the badge…"
        is BadgeProgress.Sending -> "Sending the picture — ${p.done} of ${p.total}. Don't move it."
        BadgeProgress.Drawing -> "Drawing. This takes about fifteen seconds and the badge must stay put."
    }
    is BadgeEvent.Moved ->
        if (event.timedOut) "The badge is taking its time ${event.phase.label} — ${event.at}. Trying again (${event.attempt} of ${event.of}); keep it where it is."
        else "Lost it ${event.phase.label} — ${event.at} (try ${event.attempt} of ${event.of}). Keep it flat and still; it's going again."
    BadgeEvent.Done -> "Done. The badge keeps the picture with no power at all."
    is BadgeEvent.Failed -> event.reason
}

/**
 * The two notes that end a write: one for a badge that took the picture, one for a badge that
 * didn't. On the notification stream, so a phone on silent stays silent.
 *
 * The tone generator wants a live audio stream and will throw if it can't get one — a lost chime is
 * no reason to lose the result, so a failure here is swallowed.
 */
internal class BadgeChime {
    fun done() = play(ToneGenerator.TONE_PROP_BEEP2, 320)
    fun failed() = play(ToneGenerator.TONE_SUP_ERROR, 500)

    private fun play(tone: Int, millis: Int) {
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            generator.startTone(tone, millis)
            // Releasing before the tone has finished cuts it off, so let it run out first.
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { generator.release() } }, millis + 250L)
        }
    }
}

/** ARGB pixels straight into something Compose can draw. */
internal fun ArgbImage.toBadgeImageBitmap(): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

/** The activity behind a composable's context, which reader mode has to be attached to. */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
