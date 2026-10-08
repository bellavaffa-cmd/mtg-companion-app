package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.ItemMerge
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scanner learning from corrections. The cases in scanCorrectionVectors.json are the web app's too
 * (MtgCompanionWeb's tests/scan/scanCorrections.test.ts, which keeps the file), so both apps learn,
 * apply and merge the same way.
 */
class ScanCorrectionsTest {

    private val v: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("scanCorrectionVectors.json")!!.bufferedReader().use { it.readText() }
    )

    private fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun reading(o: JSONObject) = ScanReading(o.getString("read"), o.str("set"), o.str("number"), o.getString("recognizedId"), o.getString("recognizedName"))
    private fun card(o: JSONObject) = CardRef(o.getString("id"), o.getString("name"), o.getString("set"), o.getString("collectorNumber"))
    private fun correction(o: JSONObject) = ScanCorrection(
        key = o.getString("key"), kind = o.getString("kind"), read = o.getString("read"), readSet = o.str("readSet"), readNumber = o.str("readNumber"),
        wrongId = o.getString("wrongId"), wrongName = o.getString("wrongName"), scryfallId = o.getString("scryfallId"), name = o.getString("name"),
        set = o.getString("set"), collectorNumber = o.getString("collectorNumber"), count = o.getInt("count"), used = o.getInt("used"), lastUsed = o.getLong("lastUsed")
    )
    private fun corrections(o: JSONObject, k: String): List<ScanCorrection>? = if (o.isNull(k)) null else o.getJSONArray(k).objects().map { correction(it) }

    @Test
    fun theKeysAReadMakes() {
        for (k in v.getJSONArray("keys").objects()) {
            val r = reading(k.getJSONObject("reading"))
            assertEquals(k.getString("misread"), misreadKey(r))
            assertEquals(k.getString("printing"), printingKey(r.recognizedName))
        }
    }

    @Test
    fun learningApplyingUncorrectingAndForgettingStepByStep() {
        for (s in v.getJSONArray("scenarios").objects()) {
            var list: List<ScanCorrection> = emptyList()
            s.getJSONArray("steps").objects().forEachIndexed { i, step ->
                val at = "${s.getString("name")}, step ${i + 1}"
                when (step.getString("op")) {
                    "lookup" -> {
                        val got = lookupCorrection(list, reading(step.getJSONObject("reading")))
                        val want = if (step.isNull("expect")) null else step.getJSONObject("expect").let { AppliedCorrection(it.getString("key"), it.getString("kind"), it.getString("scryfallId")) }
                        assertEquals(at, want, got)
                    }
                    "record" -> list = recordCorrection(list, reading(step.getJSONObject("reading")), card(step.getJSONObject("card")), step.getLong("now"), step.str("learned"))
                    "used" -> list = markUsed(list, step.getString("key"), step.getLong("now"))
                    "forget" -> list = forgetCorrection(list, step.getString("key"))
                    "expect" -> assertEquals(
                        at,
                        step.getJSONArray("entries").objects().map { listOf(it.getString("key"), it.getString("kind"), it.getString("scryfallId"), it.getInt("count"), it.getInt("used")) },
                        list.map { listOf(it.key, it.kind, it.scryfallId, it.count, it.used) }
                    )
                }
            }
        }
    }

    @Test
    fun twoDevicesCorrectionsMergeEntryByEntryTheOneUsedLastWinning() {
        for (m in v.getJSONArray("merge").objects()) {
            val got = mergeCorrections(corrections(m, "base"), corrections(m, "mine"), corrections(m, "theirs"), m.getBoolean("minePreferred"))
            val want = if (m.isNull("expect")) null else m.getJSONArray("expect").let { a ->
                (0 until a.length()).map { a.getJSONArray(it).let { e -> listOf<Any>(e.getString(0), e.getString(1), e.getLong(2)) } }
            }
            assertEquals(m.getString("name"), want, got?.map { listOf<Any>(it.key, it.scryfallId, it.lastUsed) })
        }
    }

    private fun gen(range: JSONArray): List<ScanCorrection> = (range.getInt(0) until range.getInt(1)).map { i ->
        ScanCorrection(key = "k" + i.toString().padStart(3, '0'), kind = KIND_MISREAD, read = "r", wrongId = "w", wrongName = "W", scryfallId = "s$i", name = "N", set = "s", collectorNumber = "1", count = 1, used = 0, lastUsed = i + 1L)
    }

    @Test
    fun atMost500AreKeptTheOnesUsedLongestAgoGo() {
        val cap = v.getJSONObject("cap")
        val merged = mergeCorrections(null, gen(cap.getJSONArray("mine")), gen(cap.getJSONArray("theirs")), true)!!
        assertEquals(cap.getInt("kept"), merged.size)
        assertEquals(MAX_CORRECTIONS, merged.size)
        assertEquals(cap.getString("newest"), merged.first().key)
        assertEquals(cap.getString("oldestKept"), merged.last().key)
        // Learning one more drops the oldest.
        val more = recordCorrection(merged, ScanReading("Opt", null, null, "o1", "Opt"), CardRef("o2", "Opt", "xln", "65"), 10_000)
        assertEquals(MAX_CORRECTIONS, more.size)
        assertEquals("~opt", more.first().key)
        assertFalse(more.any { it.key == cap.getString("oldestKept") })
        assertEquals("k002,k001,k000", capCorrections(gen(JSONArray("[0,3]"))).joinToString(",") { it.key })
    }

    @Test
    fun whatSettingsScannerSaysOfEach() {
        for (l in v.getJSONArray("lines").objects()) {
            val c = correction(l.getJSONObject("entry"))
            assertEquals(l.getString("read"), correctionReadLine(c))
            assertEquals(l.getString("corrected"), correctedLine(c))
            assertEquals(l.getString("used"), correctionUsedLine(c))
        }
    }

    // ---- On the Unsorted pile, in the sync and in a reset ----

    private fun pile(list: List<ScanCorrection>? = null) =
        Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, createdAt = 1, type = CollectionType.OWNED.name, scanCorrections = list)
    private val one = gen(JSONArray("[0,1]"))
    private val two = gen(JSONArray("[0,2]"))

    @Test
    fun keptOnTheUnsortedPileMadeIfItIsNotThere() {
        val cols = withCorrections(emptyList(), one)
        assertEquals(1, cols.size)
        assertEquals(listOf("k000"), correctionsOf(cols).map { it.key })
    }

    @Test
    fun thePileMergesThemAndASaveByAnOlderAppLeavesThemAsTheyWere() {
        val mine = pile(two)
        val older = pile()
        val merged = ItemMerge.mergeCollections(pile(one), mine, older, minePreferred = false)
        assertEquals(listOf("k001", "k000"), merged.scanCorrections?.map { it.key })
        assertSame(two, keepCorrectionsFromOlderApp(mine, older).scanCorrections)
        assertEquals(0, keepCorrectionsFromOlderApp(mine, pile(emptyList())).scanCorrections?.size)
        // Neither side has any: still left out.
        assertNull(ItemMerge.mergeCollections(pile(), pile(), pile(), minePreferred = true).scanCorrections)
    }

    @Test
    fun resetCollectionAndEverythingClearThemCardsOnlyKeepsThem() {
        fun after(scope: ResetScope) = resetLibrary(emptyList(), listOf(pile(two)), scope).collections.first { it.id == UNSORTED_COLLECTION_ID }.scanCorrections
        assertEquals(2, after(ResetScope.CARDS)?.size)
        assertEquals(emptyList<ScanCorrection>(), after(ResetScope.COLLECTION))
        assertEquals(emptyList<ScanCorrection>(), after(ResetScope.EVERYTHING))
        // ...and the emptied list, synced, clears them on the other device too.
        val merged = ItemMerge.mergeCollections(pile(two), pile(emptyList()), pile(two), minePreferred = true)
        assertTrue(merged.scanCorrections!!.isEmpty())
    }
}
