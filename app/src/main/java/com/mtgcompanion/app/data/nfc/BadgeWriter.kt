package com.mtgcompanion.app.data.nfc

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.mtgcompanion.app.BuildConfig
import java.io.IOException

/**
 * Holding a badge against the phone and putting a picture on it.
 *
 * Reader mode rather than the usual tag dispatch, for two reasons: it keeps the picture-writing
 * screen in charge while it's open instead of handing the tag to whatever else claims it, and it
 * lets us stop Android quietly pinging the badge to see if it's still there. That poll is harmless
 * against a normal tag and fatal here — the badge stops answering for fifteen seconds while it
 * computes the waveform, and a presence check in the middle of that ends the session.
 *
 * The badge is powered by the phone's field, so it has to stay put for the whole thing: about ten
 * seconds of sending followed by fifteen of drawing. Moving it loses the buffer, which is why
 * [BadgeEvent.Moved] asks for the same tap again rather than reporting a failure.
 */

sealed interface BadgeEvent {
    /** Nothing against the phone yet. */
    data object Waiting : BadgeEvent
    data class Found(val config: BadgeConfig) : BadgeEvent
    data class Working(val progress: BadgeProgress) : BadgeEvent
    /** It stopped answering before the picture was through; it'll go again by itself. */
    data class Moved(val attempt: Int, val of: Int, val phase: BadgePhase, val at: String, val timedOut: Boolean) : BadgeEvent
    data object Done : BadgeEvent
    data class Failed(val reason: String) : BadgeEvent
}

/** How many times a badge that slips is worth waiting for before giving up on it. */
private const val MAX_ATTEMPTS = 3

/**
 * Long enough that Android won't check on the badge while it's drawing. The platform rounds this
 * and won't honour silly values, but anything in the tens of seconds keeps it quiet long enough.
 */
private const val PRESENCE_CHECK_DELAY_MS = 30_000

class BadgeWriter(private val context: Context) {

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    /** This phone has NFC at all. */
    val isSupported: Boolean get() = adapter != null

    /** NFC is on. It can be off while still being supported, and the user has to turn it on. */
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    /**
     * Whether a badge arriving should be written to.
     *
     * Reader mode and "are we writing" are deliberately separate. While reader mode is on, this app
     * owns every tag the phone sees and the platform never dispatches one — which is what stops
     * Android announcing "New tag collected" over a badge that is simply still lying on the phone
     * after a write finished. So the write disarms and reader mode stays up until the screen closes.
     */
    private var armed = false


    /**
     * Start listening for a badge and write a picture to it when one arrives.
     *
     * [picture] is asked for the badge's own size rather than handed a fixed bitmap, so a panel that
     * turns out not to be the 240×416 one gets something drawn for it instead of letterboxed into it.
     *
     * [onEvent] is called from NFC's own thread, not the main one — callers marshal it themselves.
     * Call [stop] when the screen goes away, or the phone keeps reading badges.
     */
    fun start(activity: Activity, picture: (BadgeConfig) -> ArgbImage, slot: Int = 0, onEvent: (BadgeEvent) -> Unit) {
        val nfc = adapter ?: run { onEvent(BadgeEvent.Failed("This phone doesn't have NFC")); return }
        if (!nfc.isEnabled) { onEvent(BadgeEvent.Failed("NFC is switched off")); return }

        armed = true
        onEvent(BadgeEvent.Waiting)

        val extras = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, PRESENCE_CHECK_DELAY_MS)
        }
        nfc.enableReaderMode(
            activity,
            { tag -> if (armed) onTag(tag, picture, slot, onEvent) else trace("tag ignored: not armed") },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                // No chirp when the badge lands; this screen says what is happening in words.
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            extras
        )
    }

    /**
     * Finish writing but keep owning the radio.
     *
     * Called instead of [stop] when a write ends, because the badge is still on the phone at that
     * moment: handing the radio back would have the platform find it, fail to match an app to it,
     * and announce it.
     */
    fun disarm() {
        armed = false
    }

    /** Hand the radio back. The screen does this on its way out. */
    fun stop(activity: Activity) {
        armed = false
        runCatching { adapter?.disableReaderMode(activity) }
    }

    private fun onTag(tag: Tag, picture: (BadgeConfig) -> ArgbImage, slot: Int, onEvent: (BadgeEvent) -> Unit) {
        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            onEvent(BadgeEvent.Failed("That isn't a badge this can write to"))
            return
        }
        try {
            isoDep.connect()
            // A full screen is a hundred 255-byte commands. Not every radio will carry one that
            // long, and one that won't fails the write rather than saying so, so ask first.
            val room = isoDep.maxTransceiveLength - APDU_HEADER
            val chunk = room.coerceIn(16, CHUNK_SIZE)
            trace("connected: maxTransceive=${isoDep.maxTransceiveLength} chunk=$chunk extended=${isoDep.isExtendedLengthApduSupported}")

            onEvent(BadgeEvent.Working(BadgeProgress.Reading))
            val session = BadgeSession(IsoDepLink(isoDep), trace = ::trace, maxChunk = chunk)
            val config = session.readConfig()
            trace("config: $config flipVertical=${config.flipVertical} pin=${config.pinRequired}")
            onEvent(BadgeEvent.Found(config))

            val target = if (slot in 0 until maxOf(1, config.pictureCapacity)) slot else 0
            val packed = packForBadge(picture(config), config)

            // Losing the field clears the badge's buffer, so a drop means sending the whole picture
            // again — but not necessarily a fresh tap. If the badge is still physically there, a
            // reconnect picks up where the user already has it and saves them fumbling for the spot.
            for (attempt in 1..MAX_ATTEMPTS) {
                when (val outcome = session.show(packed, target) { onEvent(BadgeEvent.Working(it)) }) {
                    BadgeOutcome.Done -> { disarm(); onEvent(BadgeEvent.Done); return }
                    is BadgeOutcome.Failed -> { disarm(); onEvent(BadgeEvent.Failed(outcome.reason)); return }
                    is BadgeOutcome.Moved -> {
                        trace("dropped on attempt $attempt ${outcome.phase.name} at ${outcome.at} timedOut=${outcome.timedOut}")
                        if (attempt == MAX_ATTEMPTS) {
                            val why = if (outcome.timedOut) "never answered" else "kept dropping out"
                            disarm()
                            onEvent(BadgeEvent.Failed("The badge $why ${outcome.phase.label} (${outcome.at})"))
                            return
                        }
                        onEvent(BadgeEvent.Moved(attempt, MAX_ATTEMPTS, outcome.phase, outcome.at, outcome.timedOut))
                        val back = runCatching { isoDep.close(); isoDep.connect(); isoDep.isConnected }.getOrDefault(false)
                        trace("reconnect after drop: $back")
                        // Gone for good: leave reader mode running so the next tap starts over.
                        if (!back) return
                    }
                }
            }
        } catch (e: TagLostException) {
            trace("lost before anything: ${e.message}")
            onEvent(BadgeEvent.Moved(1, MAX_ATTEMPTS, BadgePhase.READING, "first contact", false))
        } catch (e: BadgeLinkLost) {
            trace("lost before anything: ${e.message}")
            onEvent(BadgeEvent.Moved(1, MAX_ATTEMPTS, BadgePhase.READING, "first contact", false))
        } catch (e: IOException) {
            trace("io error: ${e.message}")
            onEvent(BadgeEvent.Failed(e.message ?: "The badge stopped answering"))
        } catch (e: IllegalStateException) {
            trace("bad answer: ${e.message}")
            onEvent(BadgeEvent.Failed(e.message ?: "The badge answered something we didn't understand"))
        } finally {
            runCatching { isoDep.close() }
        }
    }

    private fun trace(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}

private const val TAG = "ManabindBadge"

/** Class, instruction, two parameters and a length, in front of every payload. */
private const val APDU_HEADER = 5

/** [BadgeLink] over a connected [IsoDep]. */
private class IsoDepLink(private val isoDep: IsoDep) : BadgeLink {
    override fun transceive(apdu: ByteArray, timeoutMs: Int): ByteArray {
        // The drawing commands block far longer than the default, and the timeout is per-exchange.
        runCatching { isoDep.timeout = timeoutMs }
        val start = SystemClock.elapsedRealtime()
        return try {
            isoDep.transceive(apdu)
        } catch (e: TagLostException) {
            // Android raises the same exception whether the badge was taken away or simply never
            // answered, and the two want opposite responses. How long we waited tells them apart:
            // a badge lifted off the phone fails early, a badge still thinking fails on the clock.
            val waited = SystemClock.elapsedRealtime() - start
            throw BadgeLinkLost(e, timedOut = waited >= timeoutMs * 0.9)
        }
    }
}
