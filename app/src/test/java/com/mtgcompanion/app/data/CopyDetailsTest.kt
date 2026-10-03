package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A binder card's condition and language: read from other apps' CSVs, written back out, and kept
 * under fixed JSON keys. The web app has the same checks — see tests/collection/copyDetails.test.ts.
 */
class CopyDetailsTest {

    @Test
    fun conditionsReadFromEveryAppsWords() {
        assertEquals("NM", conditionCode("Near Mint"))
        assertEquals("NM", conditionCode("near_mint"))
        assertEquals("NM", conditionCode("Near Mint Foil"))
        assertEquals("LP", conditionCode("Lightly Played"))
        assertEquals("LP", conditionCode("Good (Lightly Played)"))
        assertEquals("LP", conditionCode("excellent"))
        assertEquals("MP", conditionCode("Played"))
        assertEquals("MP", conditionCode("Moderately Played"))
        assertEquals("HP", conditionCode("heavily_played"))
        assertEquals("DMG", conditionCode("Damaged"))
        assertEquals("DMG", conditionCode("poor"))
        assertEquals("LP", conditionCode("lp"))
        assertNull(conditionCode(""))
        assertNull(conditionCode("Mostly fine"))
    }

    @Test
    fun languagesReadFromNamesAndCodes() {
        assertEquals("en", languageCode("English"))
        assertEquals("ja", languageCode("Japanese"))
        assertEquals("ja", languageCode("JP"))
        assertEquals("zhs", languageCode("Chinese Simplified"))
        assertEquals("zht", languageCode("zh-TW"))
        assertEquals("pt", languageCode("Portuguese (Brazil)"))
        assertEquals("ko", languageCode("ko"))
        assertNull(languageCode("Klingon"))
        assertNull(languageCode(" "))
    }

    @Test
    fun aRowShowsBadgesOnlyForWhatsSet() {
        val plain = CollectionEntry("a", "Sol Ring", null, quantity = 1)
        assertEquals(emptyList<String>(), copyBadges(plain))
        assertEquals(listOf("LP", "JA"), copyBadges(plain.copy(condition = "LP", language = "ja")))
        assertEquals(listOf("ZHS"), copyBadges(plain.copy(language = "zhs")))
    }

    @Test
    fun moxfieldCsvBringsConditionAndLanguage() {
        val csv = """
            "Count","Tradelist Count","Name","Edition","Condition","Language","Foil","Tags","Last Modified","Collector Number"
            "2","0","Sol Ring","cmr","Lightly Played","Japanese","","","2024-01-01","472"
            "1","0","Arcane Signet","cmr","Near Mint","English","foil","","2024-01-01","297"
        """.trimIndent()
        val lines = parseCardList(csv).lines
        assertEquals(listOf("LP" to "ja", "NM" to "en"), lines.map { it.condition to it.language })
        assertEquals(listOf(false, true), lines.map { it.foil })
    }

    @Test
    fun manaboxDeckboxAndTcgplayerCsvsToo() {
        val manabox = "Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,ManaBox ID,Scryfall ID,Purchase price,Misprint,Altered,Condition,Language\n" +
            "Sol Ring,cmr,Commander Legends,472,normal,uncommon,1,1,0afa0e33-4804-4b00-b625-c2d6b61090fc,1.0,false,false,near_mint,de"
        assertEquals("NM" to "de", parseCardList(manabox).lines.single().let { it.condition to it.language })

        val deckbox = "Count,Tradelist Count,Name,Edition,Card Number,Condition,Language,Foil\n1,0,Sol Ring,Commander Legends,472,Good (Lightly Played),French,"
        assertEquals("LP" to "fr", parseCardList(deckbox).lines.single().let { it.condition to it.language })

        val tcgplayer = "Quantity,Name,Simple Name,Set,Card Number,Set Code,Printing,Condition,Language\n1,Sol Ring,Sol Ring,Commander Legends,472,CMR,Foil,Near Mint Foil,English"
        val line = parseCardList(tcgplayer).lines.single()
        assertEquals("NM" to "en", line.condition to line.language)
        assertTrue(line.foil)

        // No such columns: nothing said.
        val plain = parseCardList("Count,Name\n1,Sol Ring").lines.single()
        assertNull(plain.condition)
        assertNull(plain.language)
    }

    @Test
    fun theCsvExportReadsBackTheSame() {
        val entries = listOf(
            CollectionEntry("0afa0e33-4804-4b00-b625-c2d6b61090fc", "Sol Ring", null, quantity = 2, foilQuantity = 1, condition = "LP", language = "ja"),
            CollectionEntry("11111111-2222-3333-4444-555555555555", "Kozilek, Butcher of Truth", null, quantity = 1)
        )
        val csv = buildCardListCsv(entries, mapOf("0afa0e33-4804-4b00-b625-c2d6b61090fc" to ("cmr" to "472")))
        assertTrue(csv.startsWith(CARD_LIST_CSV_HEADER))
        assertTrue("a name with a comma is quoted", "\"Kozilek, Butcher of Truth\"" in csv)
        val back = parseCardList(csv).lines
        assertEquals(3, back.size)
        val sol = back.filter { it.name == "Sol Ring" }
        assertEquals(listOf(2 to false, 1 to true), sol.map { it.quantity to it.foil })
        assertTrue(sol.all { it.condition == "LP" && it.language == "ja" && it.set == "cmr" && it.number == "472" })
        val kozilek = back.single { it.name == "Kozilek, Butcher of Truth" }
        assertNull(kozilek.condition)
        assertFalse(kozilek.foil)
        assertEquals("11111111-2222-3333-4444-555555555555", kozilek.scryfallId)
    }

    @Test
    fun theEntrysJsonKeysAreFixed() {
        val adapter = localMoshi.adapter(CollectionEntry::class.java)
        val json = adapter.toJson(CollectionEntry("a", "Sol Ring", null, quantity = 1, priceAlertAbove = 40.0, condition = "HP", language = "ko"))
        assertTrue(json, "\"condition\":\"HP\"" in json)
        assertTrue(json, "\"language\":\"ko\"" in json)
        assertTrue(json, "\"priceAlertAbove\":40.0" in json)
        // Not set: left out, so older apps and the web read the entry as before.
        val bare = adapter.toJson(CollectionEntry("a", "Sol Ring", null, quantity = 1))
        assertFalse(bare, "condition" in bare || "language" in bare || "priceAlertAbove" in bare)
        val read = adapter.fromJson("""{"scryfallId":"a","name":"Sol Ring","quantity":1,"condition":"MP","language":"zht"}""")!!
        assertEquals("MP" to "zht", read.condition to read.language)
    }

    @Test
    fun addingCopiesKeepsTheEntrysOwnSay() {
        val had = CollectionEntry("a", "Sol Ring", null, quantity = 1, condition = "NM")
        val merged = had.withCopiesOf(CollectionEntry("a", "Sol Ring", null, quantity = 2, foilQuantity = 1, condition = "HP", language = "de"))
        assertEquals(Triple(3, 1, "NM"), Triple(merged.quantity, merged.foilQuantity, merged.condition))
        assertEquals("de", merged.language)
    }
}
