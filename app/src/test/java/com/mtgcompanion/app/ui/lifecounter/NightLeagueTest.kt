package com.mtgcompanion.app.ui.lifecounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A game night's results sent to a pod's league. The web app has the same checks — see
 * MtgCompanionWeb/tests/decks/league.test.ts.
 */
class NightLeagueTest {

    private val me = "11111111-1111-1111-1111-111111111111"
    private val sam = "22222222-2222-2222-2222-222222222222"
    private val players = listOf(
        NightPlayer("p1", "Priya", NightPlayerKind.ME, deck = "Atraxa deck", commander = "Atraxa"),
        NightPlayer("p2", "Sam", NightPlayerKind.FRIEND, userId = sam),
        NightPlayer("p3", " Dan ", NightPlayerKind.GUEST, commander = "Krenko")
    )
    private val pod = NightPod("1-1", listOf("p1", "p2", "p3"))

    @Test
    fun aPodsResultBecomesAPodGame() {
        val seats = nightPodPlayers(pod, players, PodResult("p2", fromTable = false), me)!!
        assertEquals(listOf(me, sam, null), seats.map { it.userId })
        assertEquals(listOf("Priya", "Sam", "Dan"), seats.map { it.name })
        assertEquals(listOf("LOSS", "WIN", "LOSS"), seats.map { it.result })
        assertEquals(listOf("Atraxa", null, "Krenko"), seats.map { it.commander })
        assertEquals(listOf("Atraxa deck", null, null), seats.map { it.deck })
    }

    @Test
    fun nobodyLeftStandingIsADrawAndNoResultSendsNothing() {
        assertEquals(listOf("DRAW", "DRAW", "DRAW"), nightPodPlayers(pod, players, PodResult(null, fromTable = true), me)!!.map { it.result })
        assertNull(nightPodPlayers(pod, players, null, me))
        assertNull(nightPodPlayers(NightPod("x", listOf("p1")), players, PodResult("p1", false), me))
        assertEquals("night-n1-1-1", nightResultId("n1", pod.id))
    }
}
