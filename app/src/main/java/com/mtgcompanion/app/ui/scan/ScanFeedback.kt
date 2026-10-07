package com.mtgcompanion.app.ui.scan

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.mtgcompanion.app.data.CueDecision
import com.mtgcompanion.app.data.CueLimiter
import com.mtgcompanion.app.data.Prices
import com.mtgcompanion.app.data.SOUND_TABLE_SIGNATURE
import com.mtgcompanion.app.data.ScanCue
import com.mtgcompanion.app.data.ScanSoundSettings
import com.mtgcompanion.app.data.ScanTier
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.VIBRATION
import com.mtgcompanion.app.data.cueFor
import com.mtgcompanion.app.data.onlyFoilFinish
import com.mtgcompanion.app.data.priceUsd
import com.mtgcompanion.app.data.renderCue
import com.mtgcompanion.app.data.wavBytes
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The scanner's feedback as it plays: a recognised card's sound and buzz (Settings › Scanner). The
 * sounds are synthesised from ScanSounds.kt's table when the app starts — each tier, plain and with
 * the foil sparkle mixed in, written to the cache as short WAVs and loaded into a SoundPool, so a cue
 * plays at once. They play as media, so they follow the media volume, and stay quiet while the
 * phone is on silent or vibrate unless "Play in silent mode" is on. The web app's scanFeedback.ts.
 */
object ScanFeedback {
    @Volatile private var settings = ScanSoundSettings()
    @Volatile private var app: Context? = null
    @Volatile private var pool: SoundPool? = null
    private val soundIds = ConcurrentHashMap<ScanCue, Int>()
    private val loaded: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val limiter = CueLimiter()
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Follows the settings and makes the sounds. Called once, from the Application. */
    fun init(context: Context, settingsRepository: SettingsRepository, scope: CoroutineScope) {
        app = context.applicationContext
        scope.launch { settingsRepository.scanSound.collect { settings = it } }
        scope.launch(Dispatchers.IO) { runCatching { prepare(context.applicationContext) } }
    }

    private fun prepare(context: Context) {
        val attributes = AudioAttributes.Builder()
            // Media, not sonification: sonification follows the system volume, and these should
            // follow the volume people turn up and down.
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sounds = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build()
        sounds.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        val dir = File(context.cacheDir, "scan-sounds").apply { mkdirs() }
        // Made again whenever the table changes: the file names carry its signature.
        dir.listFiles()?.filter { !it.name.startsWith(SOUND_TABLE_SIGNATURE) }?.forEach { it.delete() }
        for (tier in ScanTier.entries) {
            for (foil in listOf(false, true)) {
                val cue = ScanCue(tier, foil)
                val file = File(dir, "$SOUND_TABLE_SIGNATURE-${tier.key}${if (foil) "-foil" else ""}.wav")
                if (!file.exists() || file.length() < 44) file.writeBytes(wavBytes(renderCue(cue)))
                soundIds[cue] = sounds.load(file.path, 1)
            }
        }
        pool = sounds
    }

    /** A card's cue under the settings now: [foil] when the copy is known to be foil, or the printing only comes in foil. */
    fun cueOf(card: ScryfallCard, foil: Boolean = false): ScanCue {
        val shiny = foil || onlyFoilFinish(card.finishes)
        val s = settings
        return cueFor(card.rarity, priceUsd(card.prices?.usd, card.prices?.usdFoil, shiny), shiny, s.mode, s.threshold, Prices.money.value.rate)
    }

    /** The scanner recognised [card]: its sound and buzz. */
    fun cardRecognised(card: ScryfallCard, foil: Boolean = false) = play(cueOf(card, foil))

    /** Plays [cue] — or, inside the 250 ms after the last, waits for its end if it ranks higher, or drops it. */
    fun play(cue: ScanCue) {
        val s = settings
        if (!s.on && !s.vibrate) return
        when (val decision = limiter.offer(cue, SystemClock.elapsedRealtime())) {
            is CueDecision.Play -> perform(decision.cue, s)
            is CueDecision.Later -> main.postDelayed({
                limiter.due(SystemClock.elapsedRealtime())?.let { perform(it, settings) }
            }, (decision.at - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 1)
            CueDecision.Drop -> Unit
        }
    }

    /** Settings' Play all: one tier's sound at [volume] (0–100), as it would play, whatever the switches say. */
    fun preview(cue: ScanCue, volume: Int, vibrate: Boolean) {
        sound(cue, volume)
        if (vibrate) buzz(cue)
    }

    private fun perform(cue: ScanCue, s: ScanSoundSettings) {
        if (s.on && (s.silent || !phoneSilenced())) sound(cue, s.volume)
        if (s.vibrate) buzz(cue)
    }

    /** Whether the ringer is on silent or vibrate. */
    private fun phoneSilenced(): Boolean {
        val audio = app?.getSystemService(AudioManager::class.java) ?: return false
        return audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
    }

    private fun sound(cue: ScanCue, volume: Int) {
        val sounds = pool ?: return
        val id = soundIds[cue] ?: return
        if (id !in loaded || volume <= 0) return
        val v = volume.coerceIn(0, 100) / 100f
        sounds.play(id, v, v, 1, 0, 1f)
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Vibrator::class.java)

    private fun buzz(cue: ScanCue) {
        val context = app ?: return
        val vibrator = vibrator(context)?.takeIf { it.hasVibrator() } ?: return
        runCatching { vibrator.vibrate(effectFor(cue.tier, vibrator)) }
    }

    /** Tick, click, double click, then a heavier rising pattern for a mythic and a longer one for value. */
    private fun effectFor(tier: ScanTier, vibrator: Vibrator): VibrationEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val composed = when (tier) {
                ScanTier.MYTHIC -> composition(vibrator, listOf(
                    Triple(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.7f, 0),
                    Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 30)
                ))
                ScanTier.VALUE -> composition(vibrator, listOf(
                    Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 0),
                    Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 60),
                    Triple(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 1f, 60),
                    Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 20)
                ))
                else -> null
            }
            if (composed != null) return composed
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return VibrationEffect.createPredefined(
                when (tier) {
                    ScanTier.COMMON -> VibrationEffect.EFFECT_TICK
                    ScanTier.UNCOMMON -> VibrationEffect.EFFECT_CLICK
                    ScanTier.RARE -> VibrationEffect.EFFECT_DOUBLE_CLICK
                    ScanTier.MYTHIC, ScanTier.VALUE -> VibrationEffect.EFFECT_HEAVY_CLICK
                }
            )
        }
        val pattern = VIBRATION.getValue(tier)
        return VibrationEffect.createWaveform(longArrayOf(0) + pattern, -1)
    }

    /** [parts] (primitive, scale, delay in ms) as one effect, when this phone has every primitive. */
    private fun composition(vibrator: Vibrator, parts: List<Triple<Int, Float, Int>>): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (!vibrator.areAllPrimitivesSupported(*parts.map { it.first }.toIntArray())) return null
        val c = VibrationEffect.startComposition()
        for ((primitive, scale, delay) in parts) c.addPrimitive(primitive, scale, delay)
        return c.compose()
    }
}
