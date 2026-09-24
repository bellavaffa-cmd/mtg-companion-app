package com.mtgcompanion.app.ui.lifecounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Passing the turn: someone who has lost is passed over, and a "turn" is a round of the table.
 * Mirrors the web app's tests/lifecounter/turns.test.ts.
 */
class NextTurnTest {

    private fun player(id: Int, killed: Boolean = false, life: Int = 40, poison: Int = 0, cmdr: Map<CommanderSource, Int> = emptyMap()) =
        PlayerLife(
            id = id,
            life = life,
            killed = killed,
            commanderDamage = cmdr,
            counters = if (poison > 0) mapOf(PlayerCounter.POISON to poison) else emptyMap()
        )

    private val four = listOf(player(1), player(2), player(3), player(4))

    @Test
    fun `the turn goes round the table, one seat at a time`() {
        assertEquals(NextTurn(2, false), nextTurnFrom(four, 1, 1, true))
        assertEquals(NextTurn(3, false), nextTurnFrom(four, 2, 1, true))
    }

    @Test
    fun `a round is only complete when play comes back to whoever started`() {
        assertEquals(false, nextTurnFrom(four, 3, 1, true)?.roundComplete)
        assertEquals(true, nextTurnFrom(four, 4, 1, true)?.roundComplete)
    }

    @Test
    fun `a round is measured from the player who actually started`() {
        assertEquals(false, nextTurnFrom(four, 4, 3, true)?.roundComplete)
        assertEquals(false, nextTurnFrom(four, 1, 3, true)?.roundComplete)
        assertEquals(true, nextTurnFrom(four, 2, 3, true)?.roundComplete)
    }

    @Test
    fun `a player who is out is passed over`() {
        val players = listOf(player(1), player(2, killed = true), player(3), player(4))
        assertEquals(3, nextTurnFrom(players, 1, 1, true)?.turnPlayerId)
    }

    @Test
    fun `out by life, poison or commander damage counts when the table is set that way`() {
        val byLife = listOf(player(1), player(2, life = 0), player(3))
        assertEquals(3, nextTurnFrom(byLife, 1, 1, true)?.turnPlayerId)
        assertEquals(2, nextTurnFrom(byLife, 1, 1, false)?.turnPlayerId)

        val byPoison = listOf(player(1), player(2, poison = 10), player(3))
        assertEquals(3, nextTurnFrom(byPoison, 1, 1, true)?.turnPlayerId)

        val byCommander = listOf(player(1), player(2, cmdr = mapOf(CommanderSource(1) to 21)), player(3))
        assertEquals(3, nextTurnFrom(byCommander, 1, 1, true)?.turnPlayerId)
    }

    @Test
    fun `passing over the starting player still completes the round`() {
        val players = listOf(player(1, killed = true), player(2), player(3), player(4))
        assertEquals(NextTurn(2, true), nextTurnFrom(players, 4, 1, true))
        assertEquals(false, nextTurnFrom(players, 2, 1, true)?.roundComplete)
    }

    @Test
    fun `the last player left keeps taking turns, and each one is a round`() {
        val players = listOf(player(1, killed = true), player(2), player(3, killed = true))
        assertEquals(NextTurn(2, true), nextTurnFrom(players, 2, 1, true))
    }

    @Test
    fun `nothing moves when there is nobody to move to`() {
        assertNull(nextTurnFrom(emptyList(), 1, 1, true))
        assertNull(nextTurnFrom(listOf(player(1, killed = true)), 1, 1, true))
    }
}
