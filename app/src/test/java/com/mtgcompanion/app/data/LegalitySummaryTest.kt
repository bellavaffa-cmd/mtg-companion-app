package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Why a deck isn't legal. The cases in legalitySummaryVectors.json are the web app's too
 * (tests/decks/legalitySummary.test.ts, which keeps the file), so both apps say the same words.
 */
class LegalitySummaryTest {

    private val v: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("legalitySummaryVectors.json")!!.bufferedReader().use { it.readText() }
    )

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)

    private fun report(c: JSONObject): LegalityReport {
        val issues = c.getJSONArray("issues").objects().map {
            LegalityIssue(
                card = it.str("card"), reason = it.getString("reason"), kind = LegalityIssueKind.valueOf(it.getString("kind")),
                sideboard = it.optBoolean("sideboard", false)
            )
        }
        return LegalityReport(
            mode = GameMode.valueOf(c.getString("mode")), totalCards = c.getInt("totalCards"),
            legal = issues.isEmpty(), issues = issues, unchecked = c.getInt("unchecked")
        )
    }

    @Test
    fun `the shared cases`() {
        val cases = v.getJSONArray("cases").objects()
        assertTrue(cases.size > 10)
        for (c in cases) {
            val name = c.getString("name")
            val r = report(c)
            assertEquals(name, c.getString("summary"), legalitySummary(r, loading = c.getBoolean("loading")))
            val groups = groupLegalityIssues(r).map { g -> listOf(g.kind.name, g.heading, g.issues.map { it.card }) }
            val want = (0 until c.getJSONArray("groups").length()).map { i ->
                val g = c.getJSONArray("groups").getJSONArray(i)
                val cards = g.getJSONArray(2)
                listOf(g.getString(0), g.getString(1), (0 until cards.length()).map { if (cards.isNull(it)) null else cards.getString(it) })
            }
            assertEquals(name, want, groups)
            assertEquals(name, c.getInt("sizeFix"), deckSizeFix(r))
            assertEquals(name, c.str("sizeFixLabel"), deckSizeFixLabel(r))
        }
    }

    @Test
    fun `no report yet is still checking`() {
        assertEquals(LEGALITY_CHECKING, legalitySummary(null))
    }

    private fun entry(id: String, name: String = id, qty: Int = 1) = DeckCardEntry(scryfallId = id, name = name, imageUrl = null, quantity = qty, typeLine = "Instant")

    private fun scry(id: String, legal: Map<String, String>?) = ScryfallCard(id = id, name = id, legalities = legal)

    @Test
    fun `cards without card data are counted as not checked`() {
        val deck = Deck("d", "Burn", gameMode = GameMode.MODERN.name, cards = listOf(entry("bolt", qty = 4), entry("shock", qty = 4)), sideboard = listOf(entry("oko")))
        val none = evaluateLegality(deck, emptyMap())
        assertEquals(3, none.unchecked)
        // A card with no legality for the format isn't checked either.
        val partly = evaluateLegality(deck, mapOf("bolt" to scry("bolt", mapOf("modern" to "legal")), "shock" to scry("shock", null)))
        assertEquals(2, partly.unchecked)
        assertNull(uncheckedNote(evaluateLegality(deck, mapOf(
            "bolt" to scry("bolt", mapOf("modern" to "legal")), "shock" to scry("shock", mapOf("modern" to "legal")), "oko" to scry("oko", mapOf("modern" to "legal"))
        ))))
        assertTrue(uncheckedNote(none)!!.startsWith("3 cards couldn't be checked yet"))
    }

    @Test
    fun `a banned sideboard card says so and can be opened`() {
        val deck = Deck("d", "Burn", gameMode = GameMode.MODERN.name, cards = listOf(entry("mountain", "Mountain", 60)), sideboard = listOf(entry("oko", "Oko")))
        val r = evaluateLegality(deck, mapOf("oko" to scry("oko", mapOf("modern" to "banned"))))
        val issue = r.issues.single { it.kind == LegalityIssueKind.LEGALITY }
        assertTrue(issue.sideboard)
        assertEquals("oko", issue.scryfallId)
        assertEquals("Banned in Modern — it's in the sideboard.", issue.reason)
        assertEquals("Not legal · 1 banned", legalitySummary(r))
    }
}
