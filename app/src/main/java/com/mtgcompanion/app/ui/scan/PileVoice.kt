package com.mtgcompanion.app.ui.scan

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Sorting with a recipe, heard: each card's pile said out loud ("Seven, blue") with the phone's
 * text-to-speech, cutting off the one before — and a buzz, twice for a smart pile, so a card for a
 * deck or a friend isn't missed with the phone face up on the table. The web app uses the browser's
 * speechSynthesis (recipeSession.ts's sayOutLoud). The engine starts the first time it's needed.
 */
class PileVoice(context: Context) {
    private val app = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    /** Said before the engine was ready: said once it is. */
    private var waiting: String? = null

    fun say(text: String) {
        if (tts == null) tts = TextToSpeech(app) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                val uk = (tts?.isLanguageAvailable(Locale.UK) ?: -1) >= TextToSpeech.LANG_AVAILABLE
                tts?.language = if (uk) Locale.UK else Locale.getDefault()
                tts?.setSpeechRate(1.1f)
                waiting?.let { speak(it) }
            }
            waiting = null
        }
        if (!ready) { waiting = text; return }
        speak(text)
    }

    private fun speak(text: String) {
        runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pile") }
    }

    /** A short buzz for a pile; a double one for a smart pile (a deck, a friend, a binder, a trade). */
    fun buzz(smart: Boolean) {
        val vibrator = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else app.getSystemService(Vibrator::class.java))?.takeIf { it.hasVibrator() } ?: return
        runCatching {
            vibrator.vibrate(
                if (smart) VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40), -1)
                else VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    }

    fun shutdown() {
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }
}
