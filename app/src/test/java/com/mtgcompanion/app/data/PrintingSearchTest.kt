package com.mtgcompanion.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The printing picker's search. The cases in printingSearchVectors.json are the web app's too
 * (MtgCompanionWeb's tests/scan/printingSearch.test.ts, which keeps the file), so both apps narrow
 * and rank a card's printings the same way.
 */
class PrintingSearchTest {

    private val v: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("printingSearchVectors.json")!!.bufferedReader().use { it.readText() }
    )

    private fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private val printings = v.getJSONArray("printings").objects().map {
        it.getString("id") to PrintingSearchFacts(it.str("set"), it.str("setName"), it.str("number"), it.str("released"))
    }

    @Test
    fun searchingACardsPrintingsBySetNameCodeNumberAndYearBestFirst() {
        for (s in v.getJSONArray("searches").objects()) {
            val query = s.getString("query")
            assertEquals("\"$query\"", s.getJSONArray("expect").strings(), searchPrintings(printings, query) { it.second }.map { it.first })
        }
    }

    @Test
    fun theSetCodeASearchCouldName() {
        for (c in v.getJSONArray("setCodes").objects()) {
            val query = c.getString("query")
            val want = c.str("set")?.let { SetCodeQuery(it, c.str("number")) }
            assertEquals("\"$query\"", want, setCodeQuery(query))
        }
    }

    @Test
    fun foldingTextCaseAccentsAndPunctuation() {
        assertEquals("lim dul s vault", foldPrintingText("Lim-Dûl’s Vault"))
        assertEquals("fin 306", foldPrintingText("  FIN·306 "))
        assertEquals("", foldPrintingText(null))
    }
}
