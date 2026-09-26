package com.mtgcompanion.app.data.nfc

/**
 * Talking to a passive NFC e-paper badge — the 240×416 black/white/red kind sold as a reusable
 * employee badge. It has no battery: it takes both its power and its picture from the phone held
 * against it, which is why a refresh takes the best part of half a minute and why letting the badge
 * slip mid-write loses everything sent so far.
 *
 * The command set isn't published. Compass Security reverse-engineered it for this exact panel
 * (github.com/CompassSecurity/nfcink) and this is a port of that work: the badge speaks ISO 14443-4,
 * so every exchange below is an ordinary APDU over [android.nfc.tech.IsoDep]. Their CLI needs a
 * desktop card reader; a phone is a card reader, so nothing here needs one.
 *
 * The parts worth knowing before changing anything:
 *
 *  - The badge reports its own geometry and colour encoding ([BadgeConfig]). Don't hard-code 240×416
 *    or "red is 11" — the same firmware ships on ten panel sizes and the wire codes move.
 *  - A refresh is a negotiation, not a command. The first attempt usually answers 68C6 twice before
 *    the badge will accept the slow form that actually computes the waveform. [refresh] is that
 *    state machine and the order of its steps is load-bearing.
 *  - The image lives in a buffer that RF loss clears, so a dropped tag means writing it all again.
 */

/** One APDU exchange. Wraps IsoDep in the app; tests hand back canned answers. */
fun interface BadgeLink {
    /**
     * Send [apdu] and return the whole response — data and status word together.
     * Throws [BadgeLinkLost] if the badge left the field.
     */
    fun transceive(apdu: ByteArray, timeoutMs: Int): ByteArray
}

/**
 * The badge stopped answering.
 *
 * [timedOut] separates the two very different reasons for that. Android reports both as a lost tag,
 * but a badge that was pulled away and a badge still sitting there thinking need opposite responses:
 * one wants the user to hold it steadier, the other wants us to wait longer.
 */
class BadgeLinkLost(cause: Throwable? = null, val timedOut: Boolean = false) :
    Exception(if (timedOut) "The badge didn't answer in time" else "The badge left the phone", cause)

// ---- Status words -----------------------------------------------------------------------------

internal const val SW_OK = 0x9000
/** Conditions not satisfied: on the device check it means no PIN is set. */
internal const val SW_NO_PIN = 0x6985
/** The badge wants the refresh escalated a stage. */
internal const val SW_ESCALATE = 0x68C6
/** Busy; the same command sent once more is usually taken. */
internal const val SW_BUSY = 0x68CA
/** Not allowed right now — start the refresh again from the top. */
internal const val SW_RETRY = 0x6986
/** Hardware fault. Nothing to do but stop. */
internal const val SW_HARDWARE = 0x698A

// ---- Timeouts ---------------------------------------------------------------------------------

/** Enough for a write chunk or a config read. */
private const val TIMEOUT_SHORT = 3_000

/**
 * The slow refresh blocks for ~14-15 s computing the e-ink waveform, and the poll afterwards can
 * wait almost as long. Anything under about 20 s drops the session mid-draw.
 */
private const val TIMEOUT_DRAW = 50_000

/**
 * Every refresh gets that same long timeout, including the first one.
 *
 * nfcink gives the first stage ten seconds because against a desk reader it answers 68C6 almost at
 * once. A four-colour badge on a phone doesn't: it goes straight into computing the waveform and
 * says nothing for far longer, and Android reports the resulting timeout as a lost tag. So ten
 * seconds looked exactly like the user moving the badge, every single time, and the picture -- which
 * had uploaded perfectly -- never got drawn.
 */
private const val TIMEOUT_REFRESH = TIMEOUT_DRAW

/** Largest payload the badge takes in one APDU. Phones that can't send that much shrink it. */
internal const val CHUNK_SIZE = 250

/** How many times a 6986 ("not now") is worth starting over for. */
private const val MAX_RETRIES = 5

// ---- Results ----------------------------------------------------------------------------------

/** Where a write has got to, for the screen holding the "keep it still" message. */
sealed interface BadgeProgress {
    data object Reading : BadgeProgress
    /** [done] of [total] chunks sent. */
    data class Sending(val done: Int, val total: Int) : BadgeProgress
    /** The image is there and the panel is being told to draw it. This is the slow part. */
    data object Drawing : BadgeProgress
}

/** Which part of the exchange was in progress. Worth carrying: "it moved" means something very
 * different at the first command than at the ninetieth. */
enum class BadgePhase(val label: String) {
    READING("while reading the badge"),
    SENDING("while sending the picture"),
    DRAWING("while it was drawing")
}

sealed interface BadgeOutcome {
    data object Done : BadgeOutcome
    /** The badge stopped answering. Everything has to be sent again, so it's worth saying so. */
    data class Moved(val phase: BadgePhase, val at: String, val timedOut: Boolean = false) : BadgeOutcome
    data class Failed(val reason: String) : BadgeOutcome
}

/** Somewhere to put a record of the exchange. Debug builds send it to logcat. */
fun interface BadgeTrace {
    fun log(message: String)
}

// ---- Session ----------------------------------------------------------------------------------

/**
 * One tap: read what the badge is, send a picture, make it draw.
 *
 * [nap] exists so the pause the badge needs between refresh stages doesn't make tests slow.
 */
class BadgeSession(
    private val link: BadgeLink,
    private val nap: (Long) -> Unit = { Thread.sleep(it) },
    private val trace: BadgeTrace = BadgeTrace { },
    /** Some phones refuse an APDU this long; the caller asks the radio and passes what fits. */
    private val maxChunk: Int = CHUNK_SIZE
) {

    private var phase = BadgePhase.READING
    private var step = "start"

    /** What the badge says it is: size, colours, how many pictures it can hold. */
    fun readConfig(): BadgeConfig {
        phase = BadgePhase.READING
        step = "config read"
        val tlv = send(Apdu.READ_CONFIG, TIMEOUT_SHORT)
        if (tlv.size < 4) throw IllegalStateException("The badge didn't answer the config read")
        // The device check carries the PIN flag in its status word and, on four-colour panels, a
        // marker in its data. A badge that refuses it is still usable, so a failure here isn't fatal.
        val check = runCatching { send(Apdu.DEVICE_CHECK, TIMEOUT_SHORT) }.getOrDefault(ByteArray(0))
        val config = parseBadgeConfig(tlv, check)
        // Which way up the panel wants the image is a separate question, and an old badge may not
        // answer it; upright is the right guess when it doesn't.
        val flip = runCatching { readImageFlip() }.getOrDefault(false to false)
        return config.copy(flipVertical = flip.second)
    }

    /** The two flip flags, horizontal then vertical. */
    private fun readImageFlip(): Pair<Boolean, Boolean> {
        val resp = send(Apdu.READ_IMAGE_INFO, TIMEOUT_SHORT)
        val data = resp.dropLast(2)
        return (data.getOrNull(0)?.toInt() == 1) to (data.getOrNull(1)?.toInt() == 1)
    }

    /**
     * Send [image] — already packed for this badge by [packForBadge] — into picture slot [section],
     * then draw it.
     */
    fun show(image: ByteArray, section: Int = 0, onProgress: (BadgeProgress) -> Unit = {}): BadgeOutcome {
        return try {
            val sent = write(image, section, onProgress)
            if (sent != null) return sent
            phase = BadgePhase.DRAWING
            onProgress(BadgeProgress.Drawing)
            refresh(section)
        } catch (lost: BadgeLinkLost) {
            trace.log("lost ${phase.name} at $step (timedOut=${lost.timedOut})")
            BadgeOutcome.Moved(phase, step, lost.timedOut)
        }
    }

    // ---- Writing ------------------------------------------------------------------------------

    /**
     * Push the picture up in 250-byte chunks, uncompressed. The badge also takes LZO-compressed
     * blocks, which would roughly halve the 100 chunks a full screen costs; plain chunks are a few
     * seconds slower and have nothing in them to get wrong.
     *
     * Returns null when every chunk landed, or the failure to report.
     */
    private fun write(image: ByteArray, section: Int, onProgress: (BadgeProgress) -> Unit): BadgeOutcome? {
        require(image.isNotEmpty()) { "Nothing to send" }
        phase = BadgePhase.SENDING
        val size = maxChunk.coerceIn(16, CHUNK_SIZE)
        val total = (image.size + size - 1) / size
        trace.log("sending ${image.size} bytes as $total chunks of $size")
        if (total > 256) return BadgeOutcome.Failed("That picture is too big to send in one go")
        for (seq in 0 until total) {
            step = "chunk ${seq + 1} of $total"
            val from = seq * size
            val chunk = image.copyOfRange(from, minOf(from + size, image.size))
            val resp = send(Apdu.writeRaw(section, seq, chunk), TIMEOUT_SHORT)
            if (statusOf(resp) != SW_OK) {
                return BadgeOutcome.Failed("The badge refused part ${seq + 1} of the picture (${swText(resp)})")
            }
            onProgress(BadgeProgress.Sending(seq + 1, total))
        }
        return null
    }

    // ---- Refreshing ---------------------------------------------------------------------------

    /**
     * Get the panel to actually draw what was sent.
     *
     * The badge answers 68C6 to say "not like that": once means send the same thing again after a
     * short pause, twice means escalate to the slow form that computes the waveform. That one blocks
     * for the better part of fifteen seconds and then either says it's drawing by itself (01 9000)
     * or wants a single poll. Anything shorter than this dance leaves the screen unchanged.
     */
    internal fun refresh(section: Int = 0): BadgeOutcome {
        phase = BadgePhase.DRAWING
        step = "refresh"
        return sendRefresh(section, retriedOnce = false, slow = false, retriesLeft = MAX_RETRIES)
    }

    private fun sendRefresh(section: Int, retriedOnce: Boolean, slow: Boolean, retriesLeft: Int): BadgeOutcome {
        val apdu = if (slow) Apdu.refreshSlow(section) else Apdu.refreshInit(section)
        val resp = try {
            send(apdu, if (slow) TIMEOUT_DRAW else TIMEOUT_REFRESH)
        } catch (lost: BadgeLinkLost) {
            // Silence is the badge's third way of saying "not like that". If the quick form runs out
            // of patience, escalate exactly as a 68C6 would rather than making the user start over.
            if (lost.timedOut && !slow) {
                trace.log("quick refresh timed out; escalating to the slow one")
                return sendRefresh(section, retriedOnce = true, slow = true, retriesLeft = retriesLeft)
            }
            throw lost
        }
        val sw = statusOf(resp)
        val data = resp.dropLast(2)

        return when {
            sw == SW_HARDWARE -> BadgeOutcome.Failed("The badge reported a hardware fault")

            sw == SW_RETRY ->
                if (retriesLeft > 0) sendRefresh(section, retriedOnce = false, slow = false, retriesLeft = retriesLeft - 1)
                else BadgeOutcome.Failed("The badge kept refusing to draw")

            sw == SW_ESCALATE -> when {
                // The badge goes quiet for a moment after the first refusal while it changes state.
                !retriedOnce -> { nap(250); sendRefresh(section, retriedOnce = true, slow = false, retriesLeft = retriesLeft) }
                !slow -> sendRefresh(section, retriedOnce = true, slow = true, retriesLeft = retriesLeft)
                else -> BadgeOutcome.Failed("The badge wouldn't start drawing")
            }

            // 01 9000: taken, and it will finish on its own.
            sw == SW_OK && data.firstOrNull()?.toInt() == 0x01 -> BadgeOutcome.Done

            sw == SW_OK -> poll(section, retriesLeft)

            sw == SW_BUSY -> {
                val again = send(apdu, if (slow) TIMEOUT_DRAW else TIMEOUT_REFRESH)
                if (again.size == 2 && statusOf(again) == SW_OK) BadgeOutcome.Done
                else BadgeOutcome.Failed("The badge stayed busy (${swText(again)})")
            }

            else -> BadgeOutcome.Failed("The badge answered something unexpected (${swText(resp)})")
        }
    }

    /**
     * Ask once whether the draw finished. One question is enough: the badge either says it's done
     * (00) or that it's drawing (01), and both mean the picture is on its way onto the panel.
     */
    private fun poll(section: Int, retriesLeft: Int): BadgeOutcome {
        val resp = try {
            send(Apdu.POLL, TIMEOUT_DRAW)
        } catch (_: BadgeLinkLost) {
            // Losing the badge after the refresh was accepted still leaves it drawing — e-paper
            // holds its picture without power, so this is a success, not a loss.
            return BadgeOutcome.Done
        }
        val sw = statusOf(resp)
        val data = resp.dropLast(2)
        return when {
            sw == SW_OK && (data.isEmpty() || data[0].toInt() == 0x00 || data[0].toInt() == 0x01) -> BadgeOutcome.Done
            sw == SW_ESCALATE -> sendRefresh(section, retriedOnce = false, slow = false, retriesLeft = retriesLeft)
            else -> BadgeOutcome.Failed("The badge stopped part-way through drawing (${swText(resp)})")
        }
    }

    // ---- Plumbing -----------------------------------------------------------------------------

    private fun send(apdu: ByteArray, timeoutMs: Int): ByteArray {
        // Only the head of a write is worth logging: the payload is a quarter of a screen of pixels.
        trace.log("-> ${apdu.take(8).toByteArray().hex()}${if (apdu.size > 8) " …${apdu.size}b" else ""}")
        val response = link.transceive(apdu, timeoutMs)
        trace.log("<- ${response.hex()}")
        return response
    }
}

// ---- APDUs ------------------------------------------------------------------------------------

internal object Apdu {
    /** Read the config TLV: what panel this is. */
    val READ_CONFIG = byteArrayOf(0x00, 0xD1.toByte(), 0x00, 0x00, 0x00)

    /** Two bytes of flip flags — which way up the panel wants the picture. */
    val READ_IMAGE_INFO = byteArrayOf(0x00, 0xEB.toByte(), 0x00, 0x00, 0x02)

    /** PIN status, and on four-colour panels a marker in the data. */
    val DEVICE_CHECK = byteArrayOf(0xF0.toByte(), 0xD8.toByte(), 0x00, 0x00, 0x05, 0x00, 0x00, 0x00, 0x00, 0x0E)

    /** Is it done drawing yet. */
    val POLL = byteArrayOf(0xF0.toByte(), 0xDE.toByte(), 0x00, 0x00, 0x01)

    /** One uncompressed slice of the picture. */
    fun writeRaw(section: Int, seq: Int, data: ByteArray): ByteArray {
        require(data.size in 1..CHUNK_SIZE) { "A slice is 1..$CHUNK_SIZE bytes, got ${data.size}" }
        return byteArrayOf(
            0xF0.toByte(), 0xD2.toByte(), (section and 0x7F).toByte(), (seq and 0xFF).toByte(), data.size.toByte()
        ) + data
    }

    /** Draw slot [section], quick form. */
    fun refreshInit(section: Int) =
        byteArrayOf(0xF0.toByte(), 0xD4.toByte(), 0x05, (section and 0x7F).toByte(), 0x00)

    /** Draw slot [section], the slow form that computes the waveform. */
    fun refreshSlow(section: Int) =
        byteArrayOf(0xF0.toByte(), 0xD4.toByte(), 0x85.toByte(), (section and 0x7F).toByte(), 0x00)
}

/** The status word at the end of a response, or -1 if there isn't one. */
internal fun statusOf(response: ByteArray): Int =
    if (response.size < 2) -1
    else ((response[response.size - 2].toInt() and 0xFF) shl 8) or (response[response.size - 1].toInt() and 0xFF)

private fun swText(response: ByteArray): String {
    val sw = statusOf(response)
    return if (sw < 0) "no answer" else String.format("%04X", sw)
}

internal fun ByteArray.hex() = joinToString("") { "%02X".format(it) }
