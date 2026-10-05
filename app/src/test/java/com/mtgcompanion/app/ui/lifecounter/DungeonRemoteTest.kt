package com.mtgcompanion.app.ui.lifecounter

import com.mtgcompanion.app.data.GameResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Dungeons, rad / speed / the Ring and mulligans over the remote protocol, and a table owner's
 * mulligans saved with their result. The web app has the same checks — see
 * MtgCompanionWeb/tests/lifecounter/dungeonRemote.test.ts, whose fixture is the state below.
 */
class DungeonRemoteTest {

    private val fixture = """{"v":1,"gameId":"g1","remotes":true,"turn":{"seat":1,"number":2},"startedAt":1,"longPress":10,"players":[{"seat":1,"name":"P1","color":"#112233","ink":"#ffffff","life":40,"out":null,"poison":0,"counters":{"speed":4,"ring":2,"rad":3},"commanderDamage":[],"background":null,"deck":null,"commander":null,"userId":null,"avatarPath":null,"canUndo":false,"partner":false,"commanderCasts":0,"partnerCasts":0,"tokens":[{"id":"g","name":"Goblin","pt":"1/1","count":3},{"id":"t","name":"Treasure","pt":null,"count":0}],"dungeon":{"id":"undercity","room":"forge"},"dungeonsCompleted":1,"ringBearer":"Frodo","mulligans":1},{"seat":2,"name":"P2","color":"#112233","ink":"#ffffff","life":40,"out":null,"poison":0,"counters":{},"commanderDamage":[],"background":null,"deck":null,"commander":null,"userId":null,"avatarPath":null,"canUndo":false,"partner":false,"commanderCasts":0,"partnerCasts":0,"tokens":[],"dungeon":null,"dungeonsCompleted":0,"ringBearer":null,"mulligans":null},{"seat":3,"name":"P3","color":"#112233","ink":"#ffffff","life":40,"out":null,"poison":0,"counters":{},"commanderDamage":[],"background":null,"deck":null,"commander":null,"userId":null,"avatarPath":null,"canUndo":false,"partner":false,"commanderCasts":0,"partnerCasts":0,"dungeon":null,"dungeonsCompleted":0,"ringBearer":null,"mulligans":null}],"shownCard":null,"over":null,"monarch":null,"initiative":null,"dayNight":null,"hold":null,"plane":null,"announce":null,"clock":{"elapsedMs":754000,"paused":true},"turnTimer":{"seconds":120,"leftMs":-4000}}"""

    private fun seat(n: Int) = RemoteSeat(
        seat = n, name = "P$n", color = "#112233", ink = "#ffffff", life = 40, out = null, poison = 0,
        counters = emptyMap(), commanderDamage = emptyList(), background = null, deck = null, commander = null,
        userId = null, avatarPath = null, canUndo = false, partner = false
    )

    @Test
    fun theNewSeatKeysReadFromTheSharedState() {
        val s = RemoteState.parse(JSONObject(fixture))!!
        val p = s.players[0]
        assertEquals(DungeonState("undercity", "forge"), p.dungeon)
        assertEquals(1, p.dungeonsCompleted)
        assertEquals("Frodo", p.ringBearer)
        assertEquals(1, p.mulligans)
        assertEquals(mapOf("speed" to 4, "ring" to 2, "rad" to 3), p.counters)
        assertEquals(listOf(null, 0, null, null), s.players[1].let { listOf(it.dungeon, it.dungeonsCompleted, it.ringBearer, it.mulligans) })
    }

    @Test
    fun aTableFromBeforeHasNoneOfIt() {
        val old = JSONObject(fixture)
        val players = old.getJSONArray("players")
        for (i in 0 until players.length()) listOf("dungeon", "dungeonsCompleted", "ringBearer", "mulligans").forEach { players.getJSONObject(i).remove(it) }
        val p = RemoteState.parse(old)!!.players[0]
        assertEquals(listOf(null, null, null, null), listOf(p.dungeon, p.dungeonsCompleted, p.ringBearer, p.mulligans))
        // Nonsense from a newer table reads as nothing.
        val odd = JSONObject(fixture)
        odd.getJSONArray("players").getJSONObject(1).put("dungeon", JSONObject().put("id", "tomb").put("room", "nowhere")).put("mulligans", 9).put("dungeonsCompleted", -3)
        val o = RemoteState.parse(odd)!!.players[1]
        assertEquals(listOf(null, 0, null), listOf(o.dungeon, o.dungeonsCompleted, o.mulligans))
    }

    @Test
    fun theNewSeatKeysGoThereAndBack() {
        val mine = seat(1).copy(dungeon = DungeonState("tomb", "trapped-entry"), dungeonsCompleted = 0, mulligans = 0, counters = mapOf("speed" to 2))
        val state = RemoteState(v = REMOTE_VERSION, gameId = "g", remotes = true, turn = null, startedAt = 0, longPress = 10, players = listOf(mine, seat(2)), shownCard = null, over = null)
        val json = JSONObject(state.toJson().toString())
        val sent = json.getJSONArray("players").getJSONObject(0)
        assertEquals("tomb", sent.getJSONObject("dungeon").getString("id"))
        assertEquals("trapped-entry", sent.getJSONObject("dungeon").getString("room"))
        assertEquals(setOf("dungeon", "dungeonsCompleted", "ringBearer", "mulligans"), sent.keys().asSequence().toSet().intersect(setOf("dungeon", "dungeonsCompleted", "ringBearer", "mulligans")))
        // A seat built without them (an older table's shape) sends none.
        assertEquals(false, json.getJSONArray("players").getJSONObject(1).has("dungeonsCompleted"))
        val back = RemoteState.parse(json)!!.players[0]
        assertEquals(mine.dungeon, back.dungeon)
        assertEquals(0, back.mulligans)
    }

    @Test
    fun theNewActionsHaveTheAgreedShapes() {
        fun json(o: JSONObject) = JSONObject(o.toString()).let { r -> r.keys().asSequence().associateWith { r.get(it) } }
        fun json(s: String) = json(JSONObject(s))
        assertEquals(json("""{"type":"venture","to":"undercity","undercity":true}"""), json(RemoteActions.venture("undercity", true)))
        assertEquals(json("""{"type":"leaveDungeon"}"""), json(RemoteActions.leaveDungeon()))
        assertEquals(json("""{"type":"ringBearer","name":null}"""), json(RemoteActions.ringBearer(null)))
        assertEquals(json("""{"type":"mulligan","value":2}"""), json(RemoteActions.mulligan(2)))
        assertEquals(json("""{"type":"counter","counter":"speed","delta":1}"""), json(RemoteActions.counter(PlayerCounter.SPEED.wire(), 1)))
        assertEquals(listOf("rad", "speed", "ring"), listOf(PlayerCounter.RAD, PlayerCounter.SPEED, PlayerCounter.RING).map { it.wire() })
        assertNull(counterOfWire("doom"))
    }

    @Test
    fun mulligansAreSavedWithTheOwnersResult() {
        val game = TableGame(
            id = "g", endedAt = 5, turns = 7, minutes = 30, winnerSeat = 1,
            players = listOf(TableGamePlayer(1, "Me", me = true, mulligans = 2), TableGamePlayer(2, "Bob", out = "LIFE"))
        )
        assertEquals(2, meResultOf(game, 1, false)!!.mulligans)
        val none: GameResult = meResultOf(game.copy(players = game.players.map { it.copy(mulligans = null) }), 1, false)!!
        assertNull("none recorded: none saved", none.mulligans)
    }
}
