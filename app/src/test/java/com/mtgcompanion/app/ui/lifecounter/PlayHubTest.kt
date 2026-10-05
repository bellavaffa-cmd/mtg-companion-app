package com.mtgcompanion.app.ui.lifecounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Play tab's status lines. The web app has the same checks — see tests/lifecounter/playHub.test.ts. */
class PlayHubTest {

    @Test
    fun theStartCardNamesTheTableAGameStartsWith() {
        assertEquals("4 players · 40 life", startGameLine(4, 40))
        assertEquals("1 player · 20 life", startGameLine(1, 20))
    }

    @Test
    fun theLastGameNamesUpToThreePlayersThenCountsTheRest() {
        assertNull(lastPlayersLine(emptyList()))
        assertNull(lastPlayersLine(listOf("  ")))
        assertEquals("Last game: Sam", lastPlayersLine(listOf("Sam")))
        assertEquals("Last game: Sam and Alex", lastPlayersLine(listOf("Sam", "Alex")))
        assertEquals("Last game: Sam, Alex and Jo", lastPlayersLine(listOf("Sam", "Alex", "Jo")))
        assertEquals("Last game: Sam, Alex and 3 more", lastPlayersLine(listOf("Sam", "Alex", "Jo", "Kim", "Lee")))
    }

    @Test
    fun gameNightSaysTonightsPlayersAndPodsOrLastTimes() {
        assertEquals("Fair pods by power", gameNightStatus(0, 0, false))
        assertEquals("Fair pods by power", gameNightStatus(0, 0, true))
        assertEquals("Tonight: 5 players", gameNightStatus(5, 0, false))
        assertEquals("Tonight: 7 players · 2 pods", gameNightStatus(7, 2, false))
        assertEquals("Tonight: 1 player · 1 pod", gameNightStatus(1, 1, false))
        assertEquals("Last time: 7 players", gameNightStatus(7, 2, true))
    }

    @Test
    fun playgroupCountsGamesAndNamesTheNemesis() {
        assertEquals("No games yet", playgroupStatus(0, null))
        assertEquals("1 game", playgroupStatus(1, null))
        assertEquals("12 games · nemesis Sam", playgroupStatus(12, "Sam"))
    }

    @Test
    fun eventsSayHowManyAreRunning() {
        assertEquals("Swiss or Commander pods", eventsStatus(0, 0))
        assertEquals("1 event running", eventsStatus(1, 3))
        assertEquals("2 events running", eventsStatus(2, 2))
        assertEquals("1 event", eventsStatus(0, 1))
        assertEquals("3 events", eventsStatus(0, 3))
    }
}
