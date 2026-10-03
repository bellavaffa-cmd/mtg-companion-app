package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallCardFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which two commanders may lead a deck together (CommanderPairing.kt), and how a card says so. */
class CommanderPairingTest {

    private val creature = "Legendary Creature — Human Warrior"

    private fun card(name: String, text: String?, type: String = creature, keywords: List<String>? = null) =
        ScryfallCard(id = name, name = name, typeLine = type, oracleText = text, keywords = keywords)

    private fun pc(name: String, ability: String?, type: String = creature) = PairCard(name, type, ability)

    // Reading the ability from the card.

    @Test
    fun plainPartnerIsReadWithOrWithoutReminderText() {
        assertEquals(PARTNER, card("Tymna", "Partner (You can have two commanders if both have partner.)").partnerAbility)
        assertEquals(PARTNER, card("Thrasios", "Flying\nPartner").partnerAbility)
    }

    @Test
    fun partnerWithKeepsTheNamedCard() {
        val pir = card("Pir, Imaginative Rascal", "Partner with Toothy, Imaginary Friend (When this creature enters, ...)\nIf one or more counters would be put on a permanent your team controls, that many plus one are put on it instead.")
        assertEquals("Toothy, Imaginary Friend", pir.partnerAbility)
    }

    @Test
    fun friendsForeverInEitherWording() {
        assertEquals(FRIENDS_FOREVER, card("Will", "Friends forever (You can have two commanders if both have friends forever.)").partnerAbility)
        assertEquals(FRIENDS_FOREVER, card("Eleven", "Partner—Friends forever (You can have two commanders if both have this ability.)").partnerAbility)
    }

    @Test
    fun otherPartnerVariantsAreKeptByName() {
        assertEquals("Partner—Survivors", card("Tyler", "Partner—Survivors (You can have two commanders if both have this ability.)").partnerAbility)
    }

    @Test
    fun chooseABackgroundAndDoctorsCompanion() {
        assertEquals(CHOOSE_A_BACKGROUND, card("Wilson", "Trample\nChoose a Background (You can have a Background as a second commander.)").partnerAbility)
        assertEquals(DOCTORS_COMPANION, card("Rose Tyler", "Doctor’s companion (You can have two commanders if the other is the Doctor.)").partnerAbility)
    }

    @Test
    fun keywordsBackUpTheText() {
        assertEquals(CHOOSE_A_BACKGROUND, card("Odd", null, keywords = listOf("Choose a Background")).partnerAbility)
    }

    @Test
    fun aDoubleFacedCardIsReadOnEveryFace() {
        val dfc = ScryfallCard(
            id = "x", name = "A // B", typeLine = "$creature // Legendary Creature — Elf",
            cardFaces = listOf(ScryfallCardFace(oracleText = "Flying"), ScryfallCardFace(oracleText = "Partner"))
        )
        assertEquals(PARTNER, dfc.partnerAbility)
    }

    @Test
    fun noAbilityAndTextThatOnlyMentionsPartner() {
        assertNull(card("Atraxa", "Flying, vigilance, deathtouch, lifelink").partnerAbility)
        assertNull(card("Odd", "Target partner of yours draws a card.").partnerAbility)
    }

    // Types.

    @Test
    fun backgroundsAndTimeLordDoctors() {
        assertTrue(isBackground("Legendary Enchantment — Background"))
        assertFalse(isBackground("Enchantment — Background"))
        assertFalse(isBackground("Legendary Enchantment — Aura"))
        assertTrue(isTimeLordDoctor("Legendary Creature — Time Lord Doctor"))
        assertFalse(isTimeLordDoctor("Legendary Creature — Human Time Lord Doctor"))
        assertFalse(isTimeLordDoctor("Creature — Time Lord Doctor"))
        assertFalse(isTimeLordDoctor("Legendary Creature — Time Lord"))
    }

    // Pairing, both ways round.

    private fun pairsBothWays(a: PairCard, b: PairCard) {
        assertTrue(canPair(a, b))
        assertTrue(canPair(b, a))
    }

    private fun neverPairs(a: PairCard, b: PairCard) {
        assertFalse(canPair(a, b))
        assertFalse(canPair(b, a))
    }

    @Test
    fun plainPartners() {
        pairsBothWays(pc("Tymna", PARTNER), pc("Thrasios", PARTNER))
        neverPairs(pc("Tymna", PARTNER), pc("Atraxa", null))
        neverPairs(pc("Tymna", PARTNER), pc("Will", FRIENDS_FOREVER))
        neverPairs(pc("Tymna", PARTNER), pc("Tymna", PARTNER))
    }

    @Test
    fun partnerWithOnlyTheNamedCard() {
        val pir = pc("Pir, Imaginative Rascal", "Toothy, Imaginary Friend")
        val toothy = pc("Toothy, Imaginary Friend", "Pir, Imaginative Rascal")
        pairsBothWays(pir, toothy)
        neverPairs(pir, pc("Tymna", PARTNER))
        neverPairs(pir, pc("Someone", "Somebody else"))
    }

    @Test
    fun friendsForeverAndVariants() {
        pairsBothWays(pc("Will", FRIENDS_FOREVER), pc("Eleven", FRIENDS_FOREVER))
        pairsBothWays(pc("Tyler", "Partner—Survivors"), pc("Zoey", "Partner—Survivors"))
        neverPairs(pc("Tyler", "Partner—Survivors"), pc("Will", FRIENDS_FOREVER))
        neverPairs(pc("Tyler", "Partner—Survivors"), pc("Tymna", PARTNER))
    }

    @Test
    fun chooseABackground() {
        val wilson = pc("Wilson", CHOOSE_A_BACKGROUND)
        pairsBothWays(wilson, pc("Raised by Giants", null, "Legendary Enchantment — Background"))
        neverPairs(wilson, pc("Oblivion Ring", null, "Enchantment"))
        neverPairs(wilson, pc("Tymna", PARTNER))
        neverPairs(wilson, pc("Abdel", CHOOSE_A_BACKGROUND))
        neverPairs(pc("Tymna", PARTNER), pc("Raised by Giants", null, "Legendary Enchantment — Background"))
    }

    @Test
    fun doctorsCompanion() {
        val rose = pc("Rose Tyler", DOCTORS_COMPANION)
        pairsBothWays(rose, pc("The Tenth Doctor", null, "Legendary Creature — Time Lord Doctor"))
        neverPairs(rose, pc("The Human Doctor", null, "Legendary Creature — Human Time Lord Doctor"))
        neverPairs(rose, pc("Clara", DOCTORS_COMPANION))
        neverPairs(pc("The Tenth Doctor", null, "Legendary Creature — Time Lord Doctor"), pc("Atraxa", null))
    }

    @Test
    fun deckEntriesPairTheSameWay() {
        val a = DeckCardEntry("a", "Tymna", null, partnerAbility = PARTNER, typeLine = creature)
        val b = DeckCardEntry("b", "Thrasios", null, partnerAbility = PARTNER, typeLine = creature)
        assertTrue(canPair(a, b))
        assertFalse(canPair(a, b.copy(partnerAbility = null)))
    }

    @Test
    fun whatKindOfSecondCommander() {
        assertEquals(SecondCommanderKind.PARTNER, secondCommanderKind(pc("Tymna", PARTNER)))
        assertEquals(SecondCommanderKind.PARTNER, secondCommanderKind(pc("Will", FRIENDS_FOREVER)))
        assertEquals(SecondCommanderKind.PARTNER, secondCommanderKind(pc("Pir", "Toothy")))
        assertEquals(SecondCommanderKind.BACKGROUND, secondCommanderKind(pc("Wilson", CHOOSE_A_BACKGROUND)))
        assertEquals(SecondCommanderKind.DOCTOR, secondCommanderKind(pc("Rose", DOCTORS_COMPANION)))
        assertEquals(SecondCommanderKind.COMPANION, secondCommanderKind(pc("Tenth", null, "Legendary Creature — Time Lord Doctor")))
        assertNull(secondCommanderKind(pc("Atraxa", null)))
        assertEquals("Add a Background", SecondCommanderKind.BACKGROUND.action)
    }

    @Test
    fun backgroundsOnlyInCommander() {
        assertTrue(allowsSecondCommander(GameMode.COMMANDER, SecondCommanderKind.BACKGROUND))
        assertTrue(allowsSecondCommander(GameMode.BRAWL, SecondCommanderKind.PARTNER))
        assertFalse(allowsSecondCommander(GameMode.BRAWL, SecondCommanderKind.BACKGROUND))
        assertTrue(allowsSecondCommander(GameMode.BRAWL, SecondCommanderKind.DOCTOR))
        assertFalse(allowsSecondCommander(GameMode.MODERN, SecondCommanderKind.PARTNER))
    }

    @Test
    fun anOldEntryTakesTheAbilityReadFromItsCard() {
        val old = DeckCardEntry("w", "Wilson", null, typeLine = creature)
        assertEquals(CHOOSE_A_BACKGROUND, old.withPairingFrom(mapOf("w" to CHOOSE_A_BACKGROUND)).partnerAbility)
        assertEquals(old, old.withPairingFrom(emptyMap()))
    }

    @Test
    fun legalityAcceptsABackgroundAndUnionsIdentity() {
        val wilson = DeckCardEntry("w", "Wilson", null, typeLine = creature)
        val giants = DeckCardEntry("g", "Raised by Giants", null, typeLine = "Legendary Enchantment — Background")
        val blue = DeckCardEntry("u", "Blue Card", null, typeLine = "Instant")
        val deck = Deck(id = "d", name = "d", commander = wilson, partnerCommander = giants, cards = listOf(wilson, giants, blue))
        val cards = mapOf(
            // The stored entry predates the ability; the card itself has it.
            "w" to ScryfallCard(id = "w", name = "Wilson", typeLine = creature, oracleText = "Choose a Background", colorIdentity = listOf("G")),
            "g" to ScryfallCard(id = "g", name = "Raised by Giants", typeLine = "Legendary Enchantment — Background", colorIdentity = listOf("U")),
            "u" to ScryfallCard(id = "u", name = "Blue Card", typeLine = "Instant", colorIdentity = listOf("U"))
        )
        val issues = evaluateLegality(deck, cards).issues
        assertTrue(issues.none { it.kind == LegalityIssueKind.COMMANDER })
        assertTrue(issues.none { it.kind == LegalityIssueKind.COLOR_IDENTITY })
    }
}
