package com.mtgcompanion.app.ui

import com.mtgcompanion.app.ui.common.SeatDamage
import com.mtgcompanion.app.ui.common.colourIdentityName
import com.mtgcompanion.app.ui.common.manaCostName
import com.mtgcompanion.app.ui.common.manaSymbolName
import com.mtgcompanion.app.ui.common.recordWords
import com.mtgcompanion.app.ui.common.seatDescription
import org.junit.Assert.assertEquals
import org.junit.Test

/** What TalkBack reads for symbols, colours and seats. The web app has the same cases — tests/a11y/descriptions.test.ts. */
class A11yTextTest {

    @Test
    fun `mana symbols read as words`() {
        assertEquals("white mana", manaSymbolName("W"))
        assertEquals("blue mana", manaSymbolName("U"))
        assertEquals("black mana", manaSymbolName("B"))
        assertEquals("red mana", manaSymbolName("R"))
        assertEquals("green mana", manaSymbolName("G"))
        assertEquals("colourless mana", manaSymbolName("C"))
        assertEquals("colourless mana", manaSymbolName("Colorless"))
        assertEquals("white mana", manaSymbolName("{W}"))
        assertEquals("white mana", manaSymbolName("w"))
        assertEquals("2 generic mana", manaSymbolName("2"))
        assertEquals("X mana", manaSymbolName("X"))
        assertEquals("tap", manaSymbolName("T"))
        assertEquals("untap", manaSymbolName("Q"))
        assertEquals("snow mana", manaSymbolName("S"))
        assertEquals("energy", manaSymbolName("E"))
    }

    @Test
    fun `hybrid and Phyrexian symbols`() {
        assertEquals("white or blue mana", manaSymbolName("W/U"))
        assertEquals("2 generic or white mana", manaSymbolName("2/W"))
        assertEquals("Phyrexian green mana", manaSymbolName("G/P"))
        assertEquals("Phyrexian green or blue mana", manaSymbolName("{G/U/P}"))
    }

    @Test
    fun `unknown symbols are read as written`() {
        assertEquals("HW", manaSymbolName("HW"))
        assertEquals("chaos", manaSymbolName("CHAOS"))
    }

    @Test
    fun `a whole cost`() {
        assertEquals("2 generic mana, white mana, white mana", manaCostName("{2}{W}{W}"))
        assertEquals("", manaCostName(""))
    }

    @Test
    fun `colour identities`() {
        assertEquals("colourless", colourIdentityName(emptyList()))
        assertEquals("colourless", colourIdentityName(listOf("C")))
        assertEquals("white", colourIdentityName(listOf("W")))
        assertEquals("white and blue", colourIdentityName(listOf("W", "U")))
        assertEquals("white, blue and black", colourIdentityName(listOf("W", "U", "B")))
    }

    @Test
    fun `a seat read as a whole`() {
        assertEquals(
            "Seat 2, Sam, 34 life, 6 commander damage from Atraxa",
            seatDescription(seat = 2, name = "Sam", life = 34, commanderDamage = listOf(SeatDamage("Atraxa", 6)))
        )
        assertEquals("Seat 1, 40 life", seatDescription(seat = 1, name = null, life = 40))
        assertEquals("Seat 1, 40 life", seatDescription(seat = 1, name = "  ", life = 40))
        assertEquals("Seat 3, Kim, 0 life, 10 poison, out of the game", seatDescription(seat = 3, name = "Kim", life = 0, poison = 10, out = true))
        assertEquals(
            "Seat 4, Jo, 21 life, 6 commander damage from Atraxa, 3 commander damage from Kim's partner",
            seatDescription(
                seat = 4, name = "Jo", life = 21,
                commanderDamage = listOf(SeatDamage("Atraxa", 6), SeatDamage("Sam", 0), SeatDamage("Kim's partner", 3))
            )
        )
    }

    @Test
    fun `match records in words`() {
        assertEquals("3 wins, 2 losses", recordWords(3, 2))
        assertEquals("1 win, 1 loss, 1 draw", recordWords(1, 1, 1))
        assertEquals("0 wins, 0 losses", recordWords(0, 0))
    }
}
