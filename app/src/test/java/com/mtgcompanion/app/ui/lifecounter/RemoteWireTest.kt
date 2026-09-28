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

    // ---- The table's monarch, initiative, day/night, hold, plane and announcements ----

    private fun state(players: List<RemoteSeat> = listOf(seat(1, null), seat(2, null))) = RemoteState(
        v = REMOTE_VERSION, gameId = "g1", remotes = true, turn = RemoteTurn(2, 3), startedAt = 1, longPress = 10,
        players = players, shownCard = null, over = null
    )

    @Test
    fun theNewTableFieldsGoThereAndBack() {
        val announce = RemoteAnnounce("a1", 1, "roll", 1_700_000_000_000, sides = 20, value = "14")
        val sent = state(listOf(seat(1, null).copy(commanderCasts = 3), seat(2, null))).copy(
            monarch = 1, initiative = 2, dayNight = "NIGHT", hold = 2,
            plane = RemotePlane("Tazeem", "https://cards.scryfall.io/normal/front/x.jpg", 41), announce = announce
        )
        val back = RemoteState.parse(JSONObject(sent.toJson().toString()))!!
        assertEquals(sent, back)
        assertEquals(3, back.players[0].commanderCasts)
    }

    @Test
    fun everyKindOfAnnouncementGoesThereAndBack() {
        listOf(
            RemoteAnnounce("1", 1, "coin", 5, value = "Heads"),
            RemoteAnnounce("2", 1, "planar", 5, value = "CHAOS"),
            RemoteAnnounce("3", 2, "emote", 5, emote = "gg"),
            RemoteAnnounce("4", 2, "target", 5, to = 1)
        ).forEach { a ->
            val back = RemoteState.parse(JSONObject(state().copy(announce = a).toJson().toString()))!!
            assertEquals(a, back.announce)
        }
        // Only the fields a kind uses are sent.
        val target = RemoteAnnounce("4", 2, "target", 5, to = 1).toJson()
        assertEquals(setOf("id", "seat", "kind", "at", "to"), target.keys().asSequence().toSet())
    }

    @Test
    fun aTableFromBeforeTheNewFieldsReadsAsNothing() {
        val old = JSONObject(state().toJson().toString())
        listOf("monarch", "initiative", "dayNight", "hold", "plane", "announce").forEach { old.remove(it) }
        old.getJSONArray("players").getJSONObject(0).remove("commanderCasts")
        val back = RemoteState.parse(old)!!
        assertNull(back.monarch)
        assertNull(back.initiative)
        assertNull(back.dayNight)
        assertNull(back.hold)
        assertNull(back.plane)
        assertNull(back.announce)
        assertEquals(0, back.players[0].commanderCasts)
    }

    @Test
    fun nullsReadAsNothingToo() {
        val back = RemoteState.parse(JSONObject(state().toJson().toString()))!!
        assertNull(back.monarch)
        assertNull(back.hold)
        assertNull(back.plane)
        assertNull(back.announce)
        val noPicture = RemoteState.parse(JSONObject(state().copy(plane = RemotePlane("Eloren Wilds", null, 3)).toJson().toString()))!!
        assertEquals(RemotePlane("Eloren Wilds", null, 3), noPicture.plane)
    }

    @Test
    fun theNewActionsHaveTheAgreedShapes() {
        // Field order isn't part of JSON, so compare what's in them.
        fun json(o: JSONObject) = JSONObject(o.toString()).let { r -> r.keys().asSequence().associateWith { r.get(it) } }
        fun json(s: String) = json(JSONObject(s))
        assertEquals(json("""{"type":"monarch","take":true}"""), json(RemoteActions.monarch(true)))
        assertEquals(json("""{"type":"initiative","take":false}"""), json(RemoteActions.initiative(false)))
        assertEquals(json("""{"type":"dayNight","value":"DAY"}"""), json(RemoteActions.dayNight("DAY")))
        assertEquals(json("""{"type":"dayNight","value":null}"""), json(RemoteActions.dayNight(null)))
        assertEquals(json("""{"type":"roll","sides":20}"""), json(RemoteActions.roll(20)))
        assertEquals(json("""{"type":"planar","what":"planeswalk"}"""), json(RemoteActions.planar("planeswalk")))
        assertEquals(json("""{"type":"commanderCast","delta":-1}"""), json(RemoteActions.commanderCast(-1)))
        assertEquals(json("""{"type":"hold","on":true}"""), json(RemoteActions.hold(true)))
        assertEquals(json("""{"type":"emote","emote":"wow"}"""), json(RemoteActions.emote("wow")))
        assertEquals(json("""{"type":"target","to":3}"""), json(RemoteActions.target(3)))
        assertEquals(json("""{"type":"concede"}"""), json(RemoteActions.concede()))
    }

    // ---- What the table allows ----

    @Test
    fun onlyTheHolderCanLetGo() {
        assertEquals(2, claimedBy(null, 2, take = true))
        assertEquals(2, claimedBy(1, 2, take = true))
        assertNull(claimedBy(2, 2, take = false))
        assertEquals(1, claimedBy(1, 2, take = false))
        assertNull(claimedBy(null, 2, take = false))
    }

    @Test
    fun theTableRollsOnlyDiceItHas() {
        val random = kotlin.random.Random(7)
        REMOTE_DICE.forEach { sides ->
            repeat(50) {
                val a = remoteRoll(3, sides, random, "id", 1)!!
                assertEquals("roll", a.kind)
                assertEquals(sides, a.sides)
                assert(a.value!!.toInt() in 1..sides)
            }
        }
        val coin = remoteRoll(3, 2, random, "id", 1)!!
        assertEquals("coin", coin.kind)
        assert(coin.value == "Heads" || coin.value == "Tails")
        assertNull(coin.sides)
        listOf(0, 1, 3, 7, 100, -6).forEach { assertNull(remoteRoll(3, it, random, "id", 1)) }
    }

    @Test
    fun thePlanarDieIsForPlanechaseOnYourOwnTurn() {
        assert(planarAllowed(planechase = true, hasPlane = true, turnTracker = false, turnSeat = 1, seat = 2))
        assert(planarAllowed(planechase = true, hasPlane = true, turnTracker = true, turnSeat = 2, seat = 2))
        assert(!planarAllowed(planechase = true, hasPlane = true, turnTracker = true, turnSeat = 1, seat = 2))
        assert(!planarAllowed(planechase = true, hasPlane = false, turnTracker = false, turnSeat = 1, seat = 2))
        assert(!planarAllowed(planechase = false, hasPlane = true, turnTracker = false, turnSeat = 2, seat = 2))
    }

    @Test
    fun announcementsReadAsSentences() {
        val names = mapOf(1 to "Ana", 2 to "Ben")
        fun say(a: RemoteAnnounce) = announceText(a) { names.getValue(it) }
        assertEquals("Ana rolled a d20: 14", say(RemoteAnnounce("1", 1, "roll", 0, sides = 20, value = "14")))
        assertEquals("Ben flipped a coin: Tails", say(RemoteAnnounce("1", 2, "coin", 0, value = "Tails")))
        assertEquals("Ana planeswalks", say(RemoteAnnounce("1", 1, "planar", 0, value = "PLANESWALK")))
        assertEquals("Ana: 🤝 GG", say(RemoteAnnounce("1", 1, "emote", 0, emote = "gg")))
        assertEquals("Ben points at Ana", say(RemoteAnnounce("1", 2, "target", 0, to = 1)))
        assertEquals(listOf("gg", "thinking", "wait", "laugh", "wow", "sorry"), REMOTE_EMOTES.keys.toList())
    }
}
