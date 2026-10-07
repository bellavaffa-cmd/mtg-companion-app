package com.mtgcompanion.app.data

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Scan sounds: what the scanner plays (and how the phone buzzes) when it recognises a card — a
// sound for the card's rarity, or a "jackpot" sting for a card worth more than the user's threshold.
// Everything here is pure: the sound table, which cue a card gets, the rate limiter, and the
// renderer that turns a sound into samples (ScanFeedback.kt plays them).
//
// The web app's src/scan/scanSounds.ts holds the same table, number for number, and the same logic:
// SOUND_TABLE_SIGNATURE below is checked by the tests on both sides, so the two can't drift apart.

/** What the scan sounds like: by rarity, by value, or both (a valuable card's sting wins, else its rarity). */
enum class ScanSoundMode(val key: String, val label: String) {
    RARITY("rarity", "By rarity"),
    VALUE("value", "By value"),
    BOTH("both", "Both");

    companion object {
        fun fromKey(key: String?): ScanSoundMode? = entries.firstOrNull { it.key == key }
    }
}

/** A card's cue, quietest first. [VALUE] is the sting for a card worth the threshold or more. */
enum class ScanTier(val key: String) { COMMON("common"), UNCOMMON("uncommon"), RARE("rare"), MYTHIC("mythic"), VALUE("value") }

/** What plays for one recognised card: its tier, and a foil sparkle over it. */
data class ScanCue(val tier: ScanTier, val foil: Boolean = false)

// ---- The sound table ----

enum class Wave { SINE, TRIANGLE }

/** One tone: a sine or triangle wave with an attack–hold–release envelope. Times in ms, gain in thousandths. */
data class ToneNote(
    /** When it starts, from the start of the sound. */
    val at: Int,
    val hz: Int,
    val wave: Wave,
    /** Peak gain, in thousandths of full scale (before the sound's level and the volume). */
    val gain: Int,
    /** Linear rise from silence to the peak. */
    val attack: Int,
    /** Held at the peak. */
    val hold: Int,
    /** Exponential fall from the peak to 1/10,000 (as Web Audio's exponentialRampToValueAtTime), then silence. */
    val release: Int
) {
    val endMs: Int get() = at + attack + hold + release
}

/** A tier's sound, or [FOIL_SOUND]: the sparkle layered over a foil card's tier. */
data class SoundSpec(val id: String, /** The whole sound's gain in thousandths: sets the sounds to the same loudness. */ val level: Int, val notes: List<ToneNote>) {
    val ms: Int get() = notes.maxOf { it.endMs }
}

const val FOIL_SOUND = "foil"

private fun n(at: Int, hz: Int, wave: Wave, gain: Int, attack: Int, hold: Int, release: Int) = ToneNote(at, hz, wave, gain, attack, hold, release)
private val S = Wave.SINE
private val T = Wave.TRIANGLE

/**
 * The sounds, all original and synthesised. Pitches are equal-tempered notes rounded to the hertz.
 *
 * - common — a soft tick: a short sine click with a triangle body (~45 ms).
 * - uncommon — a two-note blip, C6 then G6 (~210 ms).
 * - rare — a bright chime: E6 with its overtones, and B6 struck just after (~400 ms).
 * - mythic — a short rising flourish: C6 E6 G6 arpeggio landing on C7 (~450 ms).
 * - value — the jackpot sting: two quick D6 taps, then a G major chord (~445 ms).
 * - foil — a tiny sparkle, four high glints from 40 ms, layered over the card's tier (~210 ms).
 *
 * The web app's scanSounds.ts has the same table; keep them identical (SOUND_TABLE_SIGNATURE).
 */
val SOUND_TABLE: List<SoundSpec> = listOf(
    SoundSpec("common", 490, listOf(
        n(0, 1400, S, 700, 1, 0, 30),
        n(0, 700, T, 400, 1, 2, 40),
        n(0, 2800, S, 150, 1, 0, 14)
    )),
    SoundSpec("uncommon", 405, listOf(
        n(0, 1047, T, 600, 3, 25, 70),
        n(0, 2094, S, 150, 3, 25, 50),
        n(85, 1568, T, 600, 3, 30, 90),
        n(85, 3136, S, 120, 3, 30, 60)
    )),
    SoundSpec("rare", 465, listOf(
        n(0, 1319, S, 700, 2, 10, 380),
        n(0, 2638, S, 250, 2, 0, 220),
        n(0, 3957, S, 120, 2, 0, 140),
        n(45, 1976, S, 380, 2, 10, 340)
    )),
    SoundSpec("mythic", 445, listOf(
        n(0, 1047, T, 450, 3, 10, 160),
        n(55, 1319, T, 450, 3, 10, 160),
        n(110, 1568, T, 450, 3, 10, 170),
        n(165, 2093, S, 650, 3, 30, 250),
        n(165, 1047, S, 220, 3, 30, 250),
        n(165, 4186, S, 110, 3, 10, 150)
    )),
    SoundSpec("value", 465, listOf(
        n(0, 1175, T, 450, 2, 15, 50),
        n(75, 1175, T, 450, 2, 15, 50),
        n(150, 784, S, 300, 3, 40, 250),
        n(150, 1568, T, 420, 3, 40, 250),
        n(150, 1976, S, 350, 3, 40, 250),
        n(150, 2349, S, 300, 3, 40, 250)
    )),
    SoundSpec(FOIL_SOUND, 530, listOf(
        n(40, 3951, S, 300, 1, 0, 50),
        n(75, 4699, S, 280, 1, 0, 50),
        n(110, 5274, S, 250, 1, 0, 55),
        n(145, 6272, S, 220, 1, 0, 60)
    ))
)

/** No sound runs longer than this. */
const val MAX_SOUND_MS = 450
/** Where a release ends (Web Audio can't ramp exponentially to zero, and the two apps sound the same). */
const val RELEASE_FLOOR = 0.0001

fun soundSpec(id: String): SoundSpec = SOUND_TABLE.first { it.id == id }
fun soundSpec(tier: ScanTier): SoundSpec = soundSpec(tier.key)

/** How long a cue runs: its tier, and the sparkle over it. */
fun cueMs(cue: ScanCue): Int = max(soundSpec(cue.tier).ms, if (cue.foil) soundSpec(FOIL_SOUND).ms else 0)

/** The table as one line of text, the same on both apps — see [SOUND_TABLE_SIGNATURE]. */
fun tableText(table: List<SoundSpec> = SOUND_TABLE): String =
    table.joinToString("/") { s ->
        "${s.id}:${s.level}|" + s.notes.joinToString(";") { x ->
            listOf(x.at, x.hz, if (x.wave == Wave.SINE) "s" else "t", x.gain, x.attack, x.hold, x.release).joinToString(",")
        }
    }

/** FNV-1a, 32 bits, as eight hex digits. */
fun fnv1a(text: String): String {
    var h = 0x811c9dc5.toInt()
    for (c in text) {
        h = h xor c.code
        h *= 0x01000193
    }
    return String.format(Locale.US, "%08x", h.toLong() and 0xffffffffL)
}

/** The table's fingerprint. Change the table here and in scanSounds.ts together, then this on both sides. */
const val SOUND_TABLE_SIGNATURE = "c7a6e2b6"

// ---- Rendering ----

/** One tone's envelope at [ms] into it (0 before and after). */
fun envelope(note: ToneNote, gain: Double, ms: Double): Double {
    if (ms < 0) return 0.0
    if (ms < note.attack) return gain * (ms / note.attack)
    if (ms <= note.attack + note.hold) return gain
    val x = ms - note.attack - note.hold
    if (x >= note.release) return 0.0
    return gain * (RELEASE_FLOOR / gain).pow(x / note.release)
}

/** One cycle of [wave] at phase [p] (in cycles): both start at zero, rising. */
fun waveAt(wave: Wave, p: Double): Double {
    if (wave == Wave.SINE) return sin(2 * PI * p)
    val f = (((p + 0.25) % 1.0) + 1.0) % 1.0
    return 1 - 4 * abs(f - 0.5)
}

/** [specs] mixed, at [volume] (0–1), as samples from -1 to 1. */
fun renderSounds(specs: List<SoundSpec>, sampleRate: Int = 44100, volume: Double = 1.0): FloatArray {
    val ms = specs.maxOf { it.ms }
    val out = FloatArray(ceil(ms / 1000.0 * sampleRate).toInt() + 1)
    for (spec in specs) {
        for (note in spec.notes) {
            val peak = note.gain / 1000.0 * (spec.level / 1000.0) * volume
            if (peak <= RELEASE_FLOOR) continue
            val start = (note.at / 1000.0 * sampleRate).roundToInt()
            val length = ceil((note.attack + note.hold + note.release) / 1000.0 * sampleRate).toInt()
            var i = 0
            while (i < length && start + i < out.size) {
                val t = i.toDouble() / sampleRate
                out[start + i] += (waveAt(note.wave, note.hz * t) * envelope(note, peak, t * 1000)).toFloat()
                i++
            }
        }
    }
    return out
}

/** A cue's sound: its tier, with the sparkle mixed in for a foil. */
fun renderCue(cue: ScanCue, sampleRate: Int = 44100, volume: Double = 1.0): FloatArray =
    renderSounds(if (cue.foil) listOf(soundSpec(cue.tier), soundSpec(FOIL_SOUND)) else listOf(soundSpec(cue.tier)), sampleRate, volume)

/** Root-mean-square of [samples], over the part that's sounding (up to its last sample above 1/1000). */
fun loudness(samples: FloatArray): Double {
    var end = samples.size
    while (end > 0 && abs(samples[end - 1]) < 0.001f) end--
    if (end == 0) return 0.0
    var sum = 0.0
    for (i in 0 until end) sum += samples[i].toDouble() * samples[i]
    return sqrt(sum / end)
}

fun peak(samples: FloatArray): Double = samples.maxOfOrNull { abs(it).toDouble() } ?: 0.0

/** [samples] as a 16-bit mono WAV file. */
fun wavBytes(samples: FloatArray, sampleRate: Int = 44100): ByteArray {
    val out = ByteArray(44 + samples.size * 2)
    fun text(at: Int, s: String) = s.forEachIndexed { i, c -> out[at + i] = c.code.toByte() }
    fun u32(at: Int, v: Int) { for (i in 0..3) out[at + i] = (v ushr (8 * i)).toByte() }
    fun u16(at: Int, v: Int) { out[at] = v.toByte(); out[at + 1] = (v ushr 8).toByte() }
    text(0, "RIFF"); u32(4, 36 + samples.size * 2); text(8, "WAVE")
    text(12, "fmt "); u32(16, 16); u16(20, 1); u16(22, 1)
    u32(24, sampleRate); u32(28, sampleRate * 2); u16(32, 2); u16(34, 16)
    text(36, "data"); u32(40, samples.size * 2)
    for (i in samples.indices) u16(44 + i * 2, (max(-1f, min(1f, samples[i])) * 32767).roundToInt())
    return out
}

// ---- Which cue a card gets ----

/** A Scryfall rarity's tier: special and bonus count as mythic; anything unknown as common. */
fun rarityTier(rarity: String?): ScanTier = when (rarity?.lowercase()) {
    "uncommon" -> ScanTier.UNCOMMON
    "rare" -> ScanTier.RARE
    "mythic", "special", "bonus" -> ScanTier.MYTHIC
    else -> ScanTier.COMMON
}

/** A rarity as a screen reader says it: "Rare", "Mythic rare". Null when there's none. */
fun rarityLabel(rarity: String?): String? = when (rarity?.lowercase()) {
    "common" -> "Common"
    "uncommon" -> "Uncommon"
    "rare" -> "Rare"
    "mythic" -> "Mythic rare"
    "special" -> "Special"
    "bonus" -> "Bonus"
    else -> null
}

/** Whether a printing only comes in foil (or etched) — then a scanned copy is a foil one. */
fun onlyFoilFinish(finishes: List<String>?): Boolean =
    !finishes.isNullOrEmpty() && "nonfoil" !in finishes && finishes.any { it == "foil" || it == "etched" }

/** The printing's US dollar price for its finish: a foil's foil price, else the plain one — each falling back to the other. */
fun priceUsd(usd: String?, usdFoil: String?, foil: Boolean): Double? {
    val plain = usd?.toDoubleOrNull()?.takeIf { it.isFinite() }
    val shiny = usdFoil?.toDoubleOrNull()?.takeIf { it.isFinite() }
    return if (foil) shiny ?: plain else plain ?: shiny
}

/** Whether [usd] is worth [threshold] or more in the user's currency ([rate] of it to the dollar). */
fun overThreshold(usd: Double?, threshold: Double, rate: Double): Boolean =
    usd != null && threshold > 0 && usd * rate >= threshold - 1e-9

/**
 * The cue for a recognised card. By rarity: its rarity's sound. By value: the sting when it's worth
 * the threshold or more, else the soft tick. Both: the sting when it's worth it, else its rarity.
 */
fun cueFor(rarity: String?, usd: Double?, foil: Boolean, mode: ScanSoundMode, threshold: Double, rate: Double): ScanCue {
    val valuable = mode != ScanSoundMode.RARITY && overThreshold(usd, threshold, rate)
    val tier = when {
        valuable -> ScanTier.VALUE
        mode == ScanSoundMode.VALUE -> ScanTier.COMMON
        else -> rarityTier(rarity)
    }
    return ScanCue(tier, foil)
}

/** How a cue ranks against another: its tier, then a foil over a plain card. */
fun cueRank(cue: ScanCue): Int = cue.tier.ordinal * 2 + if (cue.foil) 1 else 0

/** The cue that ranks highest of [cues] — a page scan's one cue — or null for none. */
fun bestCue(cues: List<ScanCue>): ScanCue? = cues.maxByOrNull { cueRank(it) }

// ---- Rate limiting ----

/** At most one cue in this long. */
const val CUE_GAP_MS = 250L

/** Play [Play.cue] now (a waiting cue that outranks the one offered plays in its place), wait until [Later.at] and call due(), or drop it. */
sealed interface CueDecision {
    data class Play(val cue: ScanCue) : CueDecision
    data class Later(val at: Long) : CueDecision
    data object Drop : CueDecision
}

/**
 * At most one cue per [gap] ms, so cards recognised in a quick run don't pile up into a wall of
 * sound. A cue inside the gap waits for its end if it outranks the one just played (and anything
 * already waiting, which it replaces); otherwise it's dropped. Call [due] at the time [offer] gave.
 */
class CueLimiter(private val gap: Long = CUE_GAP_MS) {
    private var lastAt = Long.MIN_VALUE / 2
    private var lastRank = -1
    private var waiting: ScanCue? = null

    @Synchronized
    fun offer(cue: ScanCue, now: Long): CueDecision {
        if (now - lastAt >= gap) {
            // A cue left waiting past its time goes with this one: whichever ranks higher plays.
            val held = waiting
            waiting = null
            val play = if (held != null && cueRank(held) > cueRank(cue)) held else cue
            played(play, now)
            return CueDecision.Play(play)
        }
        val rank = cueRank(cue)
        val held = waiting
        if (rank > lastRank && (held == null || rank > cueRank(held))) {
            waiting = cue
            return CueDecision.Later(lastAt + gap)
        }
        return CueDecision.Drop
    }

    /** The waiting cue, once its time has come — it's played now. Null if there's none, or not yet. */
    @Synchronized
    fun due(now: Long): ScanCue? {
        val cue = waiting ?: return null
        if (now - lastAt < gap) return null
        waiting = null
        played(cue, now)
        return cue
    }

    private fun played(cue: ScanCue, now: Long) {
        lastAt = now
        lastRank = cueRank(cue)
    }
}

// ---- Haptics ----

/** The buzz for each tier as on/off timings in ms (the web app's navigator.vibrate patterns; the fallback waveforms here). */
val VIBRATION: Map<ScanTier, LongArray> = mapOf(
    ScanTier.COMMON to longArrayOf(10),
    ScanTier.UNCOMMON to longArrayOf(20),
    ScanTier.RARE to longArrayOf(20, 60, 20),
    ScanTier.MYTHIC to longArrayOf(15, 40, 15, 40, 45),
    ScanTier.VALUE to longArrayOf(30, 50, 30, 50, 80)
)

// ---- Settings ----

/** The scanner's sound settings' keys (the web app keeps the same, as mtgweb_<key>). */
object ScanSoundKeys {
    const val ON = "scan_sound_on"
    const val VOLUME = "scan_sound_volume"
    const val MODE = "scan_sound_mode"
    const val THRESHOLD = "scan_sound_threshold"
    const val VIBRATE = "scan_vibrate"
    const val SILENT = "scan_sound_silent"
    val ALL = listOf(ON, VOLUME, MODE, THRESHOLD, VIBRATE, SILENT)
}

/**
 * Settings › Scanner. The defaults — on, by rarity, at 60% — let the sound say what was just scanned
 * without looking up from the pile, and rarity needs no prices; 60% sits under music and calls.
 * $10 (in the user's currency) for value; vibrate on; quiet while the phone is on silent.
 */
data class ScanSoundSettings(
    val on: Boolean = true,
    /** 0–100. */
    val volume: Int = 60,
    val mode: ScanSoundMode = ScanSoundMode.RARITY,
    /** In the user's currency. */
    val threshold: Double = 10.0,
    val vibrate: Boolean = true,
    /** Play even when the phone is set to silent or vibrate. */
    val silent: Boolean = false
)

private fun bool(s: String?, fallback: Boolean) = when (s) {
    "true" -> true
    "false" -> false
    else -> fallback
}

/** The settings from their stored text, each falling back to its default. */
fun parseScanSound(get: (String) -> String?): ScanSoundSettings {
    val d = ScanSoundSettings()
    val volume = get(ScanSoundKeys.VOLUME)?.toDoubleOrNull()?.takeIf { it.isFinite() }
    val threshold = get(ScanSoundKeys.THRESHOLD)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    return ScanSoundSettings(
        on = bool(get(ScanSoundKeys.ON), d.on),
        volume = volume?.roundToInt()?.coerceIn(0, 100) ?: d.volume,
        mode = ScanSoundMode.fromKey(get(ScanSoundKeys.MODE)) ?: d.mode,
        threshold = threshold ?: d.threshold,
        vibrate = bool(get(ScanSoundKeys.VIBRATE), d.vibrate),
        silent = bool(get(ScanSoundKeys.SILENT), d.silent)
    )
}

/** A threshold typed in: a positive amount, or null. "12,50" reads as 12.50. */
fun parseThreshold(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

/** A threshold as it's kept and shown in the box: "10", "12.5" (the web app's String(number)). */
fun thresholdText(v: Double): String = if (v == Math.floor(v) && abs(v) < 1e15) v.toLong().toString() else v.toString()

/** Settings › Scanner's summary line: "Sounds by rarity · 60%", "Sounds off · vibration on". */
fun scanSoundSummary(s: ScanSoundSettings): String {
    if (!s.on) return if (s.vibrate) "Sounds off · vibration on" else "Sounds and vibration off"
    val how = when (s.mode) {
        ScanSoundMode.RARITY -> "by rarity"
        ScanSoundMode.VALUE -> "by value"
        ScanSoundMode.BOTH -> "by rarity and value"
    }
    return "Sounds $how · ${s.volume}%"
}
