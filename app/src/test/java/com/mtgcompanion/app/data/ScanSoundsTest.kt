package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10

/**
 * The scan sounds: which cue a card gets, the rate limiter and the sound table. The web app has the
 * same checks — see tests/scan/scanSounds.test.ts.
 */
class ScanSoundsTest {

    private fun cue(tier: ScanTier, foil: Boolean = false) = ScanCue(tier, foil)

    @Test
    fun rarityPicksTheSound() {
        assertEquals(ScanTier.COMMON, rarityTier("common"))
        assertEquals(ScanTier.UNCOMMON, rarityTier("uncommon"))
        assertEquals(ScanTier.RARE, rarityTier("rare"))
        assertEquals(ScanTier.MYTHIC, rarityTier("mythic"))
        assertEquals(ScanTier.MYTHIC, rarityTier("special"))
        assertEquals(ScanTier.MYTHIC, rarityTier("bonus"))
        assertEquals(ScanTier.COMMON, rarityTier(null))
        assertEquals(ScanTier.COMMON, rarityTier("weird"))
        assertEquals("Mythic rare", rarityLabel("mythic"))
        assertEquals("Rare", rarityLabel("rare"))
        assertNull(rarityLabel(null))
    }

    @Test
    fun byRarityThePriceMakesNoDifference() {
        assertEquals(cue(ScanTier.COMMON), cueFor("common", 80.0, false, ScanSoundMode.RARITY, 10.0, 1.0))
        assertEquals(cue(ScanTier.RARE, true), cueFor("rare", 0.1, true, ScanSoundMode.RARITY, 10.0, 1.0))
    }

    @Test
    fun byValueAValuableCardGetsTheStingWhateverItsRarity() {
        assertEquals(cue(ScanTier.VALUE), cueFor("common", 12.0, false, ScanSoundMode.VALUE, 10.0, 1.0))
        assertEquals(cue(ScanTier.VALUE), cueFor("common", 10.0, false, ScanSoundMode.VALUE, 10.0, 1.0))
        assertEquals(cue(ScanTier.COMMON), cueFor("mythic", 2.0, false, ScanSoundMode.VALUE, 10.0, 1.0))
        assertEquals(cue(ScanTier.COMMON), cueFor("mythic", null, false, ScanSoundMode.VALUE, 10.0, 1.0))
    }

    @Test
    fun bothPlaysTheStingOverTheThresholdElseTheRarity() {
        assertEquals(cue(ScanTier.VALUE, true), cueFor("uncommon", 25.0, true, ScanSoundMode.BOTH, 10.0, 1.0))
        assertEquals(cue(ScanTier.UNCOMMON), cueFor("uncommon", 3.0, false, ScanSoundMode.BOTH, 10.0, 1.0))
    }

    @Test
    fun theThresholdIsInTheUsersCurrency() {
        // ₱500 at 56 pesos to the dollar is about $8.93.
        assertTrue(overThreshold(9.0, 500.0, 56.0))
        assertFalse(overThreshold(8.9, 500.0, 56.0))
        // ¥1,500 at 150 yen to the dollar is $10.
        assertTrue(overThreshold(10.0, 1500.0, 150.0))
        assertFalse(overThreshold(9.99, 1500.0, 150.0))
        assertFalse(overThreshold(null, 10.0, 1.0))
    }

    @Test
    fun thePriceIsThePrintingsForItsFinish() {
        assertEquals(1.5, priceUsd("1.50", "12.00", false)!!, 1e-9)
        assertEquals(12.0, priceUsd("1.50", "12.00", true)!!, 1e-9)
        assertEquals(7.25, priceUsd(null, "7.25", false)!!, 1e-9)
        assertEquals(3.0, priceUsd("3", null, true)!!, 1e-9)
        assertNull(priceUsd(null, null, false))
        assertTrue(onlyFoilFinish(listOf("foil")))
        assertTrue(onlyFoilFinish(listOf("etched")))
        assertFalse(onlyFoilFinish(listOf("nonfoil", "foil")))
        assertFalse(onlyFoilFinish(null))
    }

    @Test
    fun aPageScanPlaysItsBestCard() {
        assertEquals(cue(ScanTier.MYTHIC), bestCue(listOf(cue(ScanTier.COMMON), cue(ScanTier.MYTHIC), cue(ScanTier.RARE, true))))
        assertEquals(cue(ScanTier.RARE, true), bestCue(listOf(cue(ScanTier.RARE), cue(ScanTier.RARE, true))))
        assertEquals(cue(ScanTier.VALUE), bestCue(listOf(cue(ScanTier.MYTHIC), cue(ScanTier.VALUE))))
        assertNull(bestCue(emptyList()))
    }

    @Test
    fun atMostOneCueEveryQuarterSecond() {
        val l = CueLimiter()
        assertEquals(CueDecision.Play(cue(ScanTier.COMMON)), l.offer(cue(ScanTier.COMMON), 0))
        assertEquals(CueDecision.Drop, l.offer(cue(ScanTier.COMMON), 100))
        assertEquals(CueDecision.Later(CUE_GAP_MS), l.offer(cue(ScanTier.RARE), 120))
        assertEquals(CueDecision.Drop, l.offer(cue(ScanTier.UNCOMMON), 150))
        assertEquals(CueDecision.Later(CUE_GAP_MS), l.offer(cue(ScanTier.MYTHIC), 200))
        assertNull(l.due(240))
        assertEquals(cue(ScanTier.MYTHIC), l.due(250))
        assertNull(l.due(260))
        assertEquals(CueDecision.Drop, l.offer(cue(ScanTier.RARE), 400))
        assertEquals(CueDecision.Play(cue(ScanTier.COMMON)), l.offer(cue(ScanTier.COMMON), 500))
    }

    @Test
    fun nineCardsAtOnceMakeOneCueNotNine() {
        val l = CueLimiter()
        val played = mutableListOf<ScanCue>()
        var wake: Long? = null
        listOf(ScanTier.COMMON, ScanTier.RARE, ScanTier.COMMON, ScanTier.UNCOMMON, ScanTier.MYTHIC, ScanTier.COMMON, ScanTier.RARE, ScanTier.COMMON, ScanTier.UNCOMMON)
            .forEachIndexed { i, t ->
                val d = l.offer(cue(t), i * 5L)
                if (d is CueDecision.Play) played.add(d.cue)
                if (d is CueDecision.Later) wake = d.at
            }
        val at = wake
        if (at != null) l.due(at)?.let { played.add(it) }
        assertEquals(listOf(cue(ScanTier.COMMON), cue(ScanTier.MYTHIC)), played)
    }

    @Test
    fun aCueLeftWaitingPlaysWithTheNextTheHigherOfTheTwo() {
        val l = CueLimiter()
        l.offer(cue(ScanTier.COMMON), 0)
        l.offer(cue(ScanTier.MYTHIC), 100)
        assertEquals(CueDecision.Play(cue(ScanTier.MYTHIC)), l.offer(cue(ScanTier.UNCOMMON), 1000))
    }

    @Test
    fun everySoundIs450MsOrLess() {
        for (s in SOUND_TABLE) assertTrue("${s.id} runs ${s.ms} ms", s.ms <= MAX_SOUND_MS)
        for (t in ScanTier.entries) for (foil in listOf(false, true)) assertTrue(cueMs(cue(t, foil)) <= MAX_SOUND_MS)
        for (s in SOUND_TABLE) for (note in s.notes) {
            assertTrue("${s.id}: ${note.hz} Hz", note.hz in 200..8000)
            assertTrue(note.gain in 1..1000)
            assertTrue("${s.id}: an instant attack clicks", note.attack >= 1)
            assertTrue(note.release >= 10)
        }
    }

    @Test
    fun theSoundsAreNormalisedAndNothingClips() {
        fun db(x: Double) = 20 * log10(x)
        val levels = ScanTier.entries.map { db(loudness(renderSounds(listOf(soundSpec(it))))) }
        val top = levels.max()
        levels.forEachIndexed { i, l -> assertTrue("${ScanTier.entries[i]} is ${top - l} dB quieter", top - l <= 1.5) }
        assertTrue("the sparkle sits under the tier", db(loudness(renderSounds(listOf(soundSpec(FOIL_SOUND))))) < top - 6)
        for (t in ScanTier.entries) for (foil in listOf(false, true)) assertTrue(peak(renderCue(cue(t, foil))) < 0.8)
        assertTrue(peak(renderCue(cue(ScanTier.RARE), volume = 0.6)) < peak(renderCue(cue(ScanTier.RARE))))
    }

    @Test
    fun theTableIsTheSameAsTheWebApps() {
        // tests/scan/scanSounds.test.ts checks its own table against the same signature.
        assertEquals(SOUND_TABLE_SIGNATURE, fnv1a(tableText()))
        assertEquals("1a47e90b", fnv1a("abc"))
    }

    @Test
    fun aWavFileIs16BitMonoPcm() {
        val bytes = wavBytes(floatArrayOf(0f, 1f, -1f), 8000)
        fun u16(at: Int) = (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(8000, u16(24) or (u16(26) shl 16))
        assertEquals(16, u16(34))
        assertEquals(32767, u16(46))
        assertEquals(-32767, u16(48).toShort().toInt())
    }

    @Test
    fun settingsDefaultsAndWhatWasKept() {
        assertEquals(ScanSoundSettings(), parseScanSound { null })
        assertEquals(ScanSoundSettings(on = true, volume = 60, mode = ScanSoundMode.RARITY, threshold = 10.0, vibrate = true, silent = false), ScanSoundSettings())
        val kept = mapOf(
            ScanSoundKeys.ON to "false", ScanSoundKeys.VOLUME to "140", ScanSoundKeys.MODE to "both",
            ScanSoundKeys.THRESHOLD to "-3", ScanSoundKeys.SILENT to "true"
        )
        assertEquals(
            ScanSoundSettings(on = false, volume = 100, mode = ScanSoundMode.BOTH, threshold = 10.0, vibrate = true, silent = true),
            parseScanSound { kept[it] }
        )
        assertEquals(12.5, parseThreshold("12,50")!!, 1e-9)
        assertNull(parseThreshold("0"))
        assertNull(parseThreshold("lots"))
        assertEquals("10", thresholdText(10.0))
        assertEquals("12.5", thresholdText(12.5))
        assertEquals("Sounds by rarity · 60%", scanSoundSummary(ScanSoundSettings()))
        assertEquals("Sounds off · vibration on", scanSoundSummary(ScanSoundSettings(on = false)))
    }
}
