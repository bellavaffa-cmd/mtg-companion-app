package com.mtgcompanion.app.ui.lifecounter

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a life counter table and its remotes say to each other. A seat's commander travels with its
 * deck, so the other players' game records can say who they faced; tables and remotes from before
 * it simply leave it out. The web app speaks the same (src/lifecounter/remote.ts).
 */
class RemoteWireTest {

    private fun seat(n: Int, commander: String?) = RemoteSeat(
        seat = n, name = "P$n", color = "#112233", ink = "#ffffff", life = 40, out = null, poison = 0,
        counters = emptyMap(), commanderDamage = emptyList(), background = null, deck = "Deck $n", commander = commander,
        userId = null, avatarPath = null, canUndo = false, partner = false
    )

    @Test
    fun aSeatsCommanderGoesThereAndBack() {
        val state = RemoteState(
            v = REMOTE_VERSION, gameId = "g1", remotes = true, turn = null, startedAt = 1, longPress = 10,
            players = listOf(seat(1, "Atraxa, Praetors' Voice"), seat(2, null)), shownCard = null, over = RemoteOver(1, 9, 42)
        )
        val back = RemoteState.parse(JSONObject(state.toJson().toString()))!!
        assertEquals("Atraxa, Praetors' Voice", back.players[0].commander)
        assertNull(back.players[1].commander)
        assertEquals(RemoteOver(1, 9, 42), back.over)
    }

    @Test
    fun aTableFromBeforeCommandersStillReads() {
        val old = JSONObject(RemoteState(
            v = REMOTE_VERSION, gameId = "g1", remotes = true, turn = null, startedAt = 1, longPress = 10,
            players = listOf(seat(1, "Krenko, Mob Boss")), shownCard = null, over = null
        ).toJson().toString())
        old.getJSONArray("players").getJSONObject(0).remove("commander")
        assertNull(RemoteState.parse(old)!!.players[0].commander)
    }

    @Test
    fun theBackgroundActionCarriesTheCommander() {
        val a = RemoteActions.background(null, "Goblins", "Krenko, Mob Boss")
        assertEquals("Krenko, Mob Boss", a.getString("commander"))
        assert(RemoteActions.background(null, null, null).isNull("commander"))
    }
}
