package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallCardFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ten companions' conditions, and the companion in a deck's legality. The web app's companion.test.ts runs the same cases. */
class CompanionTest {
    private fun c(name: String, cmc: Double?, typeLine: String?, manaCost: String? = null, oracleText: String? = "", quantity: Int = 1) =
        CompanionCard(name, quantity, cmc, manaCost, typeLine, oracleText)
    private val forest = c("Forest", 0.0, "Basic Land — Forest", "", "({T}: Add {G}.)", 20)
    private fun check(name: String, cards: List<CompanionCard>, min: Int = 60) = checkCompanion(name, cards, min)

    @Test
    fun `Gyruda - even mana values only (lands are 0)`() {
        assertEquals(listOf("Bolt"), check("Gyruda, Doom of Depths", listOf(forest, c("Ox", 2.0, "Creature — Ox"), c("Bolt", 1.0, "Instant"))).offenders)
    }

    @Test
    fun `Jegantha - no mana symbol twice in a cost`() {
        assertFalse(repeatsManaSymbol("{2}{R}{G}"))
        assertTrue(repeatsManaSymbol("{R}{R}"))
        assertTrue(repeatsManaSymbol("{1}{R}{1}{U}"))
        assertTrue(repeatsManaSymbol("{G/W}{G/W}"))
        assertEquals(
            listOf("Ball Lightning"),
            check("Jegantha, the Wellspring", listOf(forest, c("Goblin Guide", 1.0, "Creature — Goblin", "{R}"), c("Lava Spike", 1.0, "Sorcery", "{R}"), c("Ball Lightning", 3.0, "Creature — Elemental", "{R}{R}{R}"))).offenders
        )
    }

    @Test
    fun `Kaheera - creatures are Cats, Elementals, Nightmares, Dinosaurs or Beasts`() {
        val r = check("kaheera, the orphanguard", listOf(forest, c("Lion", 2.0, "Creature — Cat Soldier"), c("Bear", 2.0, "Creature — Bear"), c("Shapeshifter", 2.0, "Creature — Shapeshifter", "{1}{U}", "Changeling"), c("Bolt", 1.0, "Instant")))
        assertEquals(listOf("Bear"), r.offenders)
    }

    @Test
    fun `Keruga - mana value 3 or more, lands aside`() {
        assertEquals(listOf("Signet"), check("Keruga, the Macrosage", listOf(forest, c("Ox", 3.0, "Creature — Ox"), c("Signet", 2.0, "Artifact"), c("Unknown", null, null))).offenders)
    }

    @Test
    fun `Lurrus - permanents of mana value 2 or less, instants and sorceries may cost more`() {
        assertEquals(listOf("Titan"), check("Lurrus of the Dream-Den", listOf(forest, c("Ring", 1.0, "Artifact"), c("Titan", 6.0, "Creature — Giant"), c("Fireball", 3.0, "Sorcery"))).offenders)
    }

    @Test
    fun `Lutri - every nonland card a different name`() {
        val r = check("Lutri, the Spellchaser", listOf(forest, c("Bolt", 1.0, "Instant", "{R}", "", 2), c("Shock", 1.0, "Instant"), c("Shock", 1.0, "Instant")))
        assertEquals(listOf("Bolt", "Shock"), r.offenders)
    }

    @Test
    fun `Obosh - odd mana values, lands aside`() {
        assertEquals(listOf("Bear", "Zero"), check("Obosh, the Preypiercer", listOf(forest, c("Bolt", 1.0, "Instant"), c("Bear", 2.0, "Creature — Bear"), c("Zero", 0.0, "Artifact"))).offenders)
    }

    @Test
    fun `Umori - nonland cards share a card type, the most common one`() {
        val r = check("Umori, the Collector", listOf(forest, c("A", 1.0, "Creature — Elf"), c("B", 2.0, "Artifact Creature — Golem"), c("C", 1.0, "Instant"), c("D", 1.0, "Kindred Instant — Elf")))
        assertEquals(listOf("C", "D"), r.offenders)
        assertTrue(check("Umori, the Collector", listOf(forest, c("A", 1.0, "Instant"), c("B", 2.0, "Tribal Instant — Elf"))).met)
    }

    @Test
    fun `Yorion - twenty cards over the minimum`() {
        assertEquals(CompanionResult(false, emptyList(), 79, 80), check("Yorion, Sky Nomad", listOf(c("Island", 0.0, "Basic Land — Island", "", "", 79))))
        assertTrue(check("Yorion, Sky Nomad", listOf(c("Island", 0.0, "Basic Land — Island", "", "", 60)), 40).met)
    }

    @Test
    fun `Zirda - permanents with an activated ability`() {
        assertTrue(hasActivatedAbility("({T}: Add {G}.)"))
        assertTrue(hasActivatedAbility("Equip {2}"))
        assertTrue(hasActivatedAbility("Basic landcycling {1}"))
        assertTrue(hasActivatedAbility("Swampcycling {2}"))
        assertFalse(hasActivatedAbility("Creatures you control have \"{T}: Add {G}.\""))
        assertTrue(hasActivatedAbility("+1: Draw a card."))
        val r = check("Zirda, the Dawnwaker", listOf(forest, c("Elf", 1.0, "Creature — Elf", "{G}", "{T}: Add {G}."), c("Bear", 2.0, "Creature — Bear", "{1}{G}", ""), c("Bolt", 1.0, "Instant", "{R}", "Deal 3."), c("Unknown", null, "Creature", null, null)))
        assertEquals(listOf("Bear"), r.offenders)
    }

    @Test
    fun `not a companion - nothing to meet`() {
        assertEquals(CompanionResult(true, emptyList()), check("Sol Ring", listOf(c("Bear", 2.0, "Creature — Bear"))))
    }

    @Test
    fun `names in a sentence`() {
        assertEquals("A", nameList(listOf("A")))
        assertEquals("A and B", nameList(listOf("A", "B")))
        assertEquals("A, B and C", nameList(listOf("A", "B", "C")))
        assertEquals("A, B, C and 2 more", nameList(listOf("A", "B", "C", "D", "E")))
    }

    private fun entry(id: String, name: String, quantity: Int = 1, typeLine: String? = null) =
        DeckCardEntry(scryfallId = id, name = name, imageUrl = null, quantity = quantity, typeLine = typeLine)
    private val legal = mapOf("modern" to "legal", "commander" to "legal")
    private fun sc(id: String, name: String, cmc: Double, typeLine: String) =
        ScryfallCard(id = id, name = name, cmc = cmc, typeLine = typeLine, oracleText = "", manaCost = "", legalities = legal, colorIdentity = emptyList())

    @Test
    fun `a double-faced card is checked by its front, a split card by both halves`() {
        val dfc = companionCard(
            entry("x", "Delver of Secrets // Insectile Aberration"),
            ScryfallCard(
                id = "x", name = "Delver", cmc = 1.0, typeLine = "Creature — Human Wizard // Creature — Human Insect", oracleText = "", layout = "transform",
                cardFaces = listOf(ScryfallCardFace(manaCost = "{U}", typeLine = "Creature — Human Wizard", oracleText = "Flip"), ScryfallCardFace(manaCost = "", typeLine = "Creature — Human Insect", oracleText = "Flying"))
            )
        )
        assertEquals(CompanionCard("Delver of Secrets // Insectile Aberration", 1, 1.0, "{U}", "Creature — Human Wizard", "Flip\nFlying"), dfc)
        val split = companionCard(
            entry("y", "Fire // Ice"),
            ScryfallCard(
                id = "y", name = "Fire // Ice", cmc = 4.0, typeLine = "Instant // Instant", manaCost = "{1}{R} // {1}{U}", oracleText = "", layout = "split",
                cardFaces = listOf(ScryfallCardFace(manaCost = "{1}{R}"), ScryfallCardFace(manaCost = "{1}{U}"))
            )
        )
        assertEquals("{1}{R}{1}{U}", split.manaCost)
        assertEquals("Instant", split.typeLine)
    }

    @Test
    fun `the companion in a deck's legality - in the sideboard, and its condition`() {
        val lurrus = entry("lur", "Lurrus of the Dream-Den", 1, "Legendary Creature — Cat Nightmare")
        val cards = listOf(entry("f", "Forest", 58), entry("t", "Titan", 2))
        val byId = listOf(sc("f", "Forest", 0.0, "Basic Land — Forest"), sc("t", "Titan", 6.0, "Creature — Giant"), sc("lur", "Lurrus of the Dream-Den", 3.0, "Legendary Creature — Cat Nightmare")).associateBy { it.id }
        val modern = Deck("d", "D", gameMode = GameMode.MODERN.name, cards = cards, sideboard = listOf(lurrus)).withCompanion("Lurrus of the Dream-Den")
        val issues = evaluateLegality(modern, byId).issues.filter { it.kind == LegalityIssueKind.COMPANION }
        assertEquals(listOf("Every permanent must have mana value 2 or less. Not met by Titan."), issues.map { it.reason })
        val away = evaluateLegality(modern.copy(sideboard = emptyList()), byId).issues.filter { it.kind == LegalityIssueKind.COMPANION }.map { it.reason }
        assertEquals(listOf("The companion isn't in the sideboard.", "Every permanent must have mana value 2 or less. Not met by Titan."), away)
        assertTrue(evaluateLegality(modern.withCompanion(null), byId).issues.none { it.kind == LegalityIssueKind.COMPANION })
        assertEquals("", modern.withCompanion(null).companion)
        assertNull(Deck("d", "D").withCompanion(null).companion)
    }

    @Test
    fun `in Commander the companion waits outside the 100 without counting as a sideboard`() {
        val lurrus = entry("lur", "Lurrus of the Dream-Den", 1)
        val deck = Deck("d", "D", gameMode = GameMode.COMMANDER.name, cards = listOf(entry("f", "Forest", 100)), sideboard = listOf(lurrus)).withCompanion("Lurrus of the Dream-Den")
        assertTrue(evaluateLegality(deck, emptyMap()).issues.none { it.reason.contains("has no sideboard") })
        val two = evaluateLegality(deck.copy(sideboard = listOf(lurrus, entry("x", "Extra"))), emptyMap())
        assertTrue(two.issues.any { it.reason == "Commander has no sideboard — 2 cards still there." })
    }

    @Test
    fun `adding a card that breaks the condition says so`() {
        val deck = Deck("d", "D", gameMode = GameMode.MODERN.name, cards = listOf(entry("b", "Bolt", 4, "Instant"))).withCompanion("Lurrus of the Dream-Den")
        assertEquals("Breaks Lurrus's companion condition", companionAddProblem(deck, c("Titan", 6.0, "Creature — Giant")))
        assertNull(companionAddProblem(deck, c("Fireball", 5.0, "Sorcery")))
        assertEquals("Breaks Umori's companion condition", companionAddProblem(deck.withCompanion("Umori, the Collector"), c("Bear", 2.0, "Creature — Bear")))
        assertEquals("Breaks Lutri's companion condition", companionAddProblem(deck.withCompanion("Lutri, the Spellchaser"), c("Bolt", 1.0, "Instant")))
        assertNull(companionAddProblem(deck.withCompanion("Yorion, Sky Nomad"), c("Bear", 2.0, "Creature — Bear")))
    }
}
