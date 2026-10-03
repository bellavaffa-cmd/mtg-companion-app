package com.mtgcompanion.app.ui.lifecounter

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deck tokens and trigger reminders at the table, the game clock and turn timer, and the protocol
 * messages that carry them between the table and players' remotes (mirrored in the web app's
 * src/lifecounter/remote.ts).
 */
class TableExtrasTest {

    // ---- Trigger reminders ----

    @Test
    fun startOfTurnTriggersAreFound() {
        assertEquals(setOf(TriggerStep.UPKEEP), triggerStepsOf("At the beginning of your upkeep, you draw a card and you lose 1 life."))
        assertEquals(setOf(TriggerStep.END), triggerStepsOf("Flying\nAt the beginning of your end step, create a 1/1 token."))
        assertEquals(setOf(TriggerStep.COMBAT), triggerStepsOf("At the beginning of combat on your turn, put a +1/+1 counter on target creature."))
        assertEquals(setOf(TriggerStep.DRAW), triggerStepsOf("At the beginning of your draw step, draw an additional card."))
    }

    @Test
    fun otherTextIsNotATrigger() {
        assertTrue(triggerStepsOf("At the beginning of each opponent's upkeep, they lose 1 life.").isEmpty())
        assertTrue(triggerStepsOf("Draw a card.").isEmpty())
        assertTrue(triggerStepsOf(null).isEmpty())
        // Reminder text in brackets doesn't count.
        assertTrue(triggerStepsOf("Cumulative upkeep {1} (At the beginning of your upkeep, put an age counter on this permanent.)").isEmpty())
    }

    @Test
    fun aCardWithTwoTriggersCountsForBoth() {
        val text = "At the beginning of your upkeep, scry 1.\nAt the beginning of your end step, gain 1 life."
        assertEquals(setOf(TriggerStep.UPKEEP, TriggerStep.END), triggerStepsOf(text))
    }

    @Test
    fun deckTriggersGoInStepOrderOnceEach() {
        val cards = listOf(
            "Bitterblossom" to "At the beginning of your upkeep, you lose 1 life and create a 1/1 Faerie.",
            "Llanowar Elves" to "{T}: Add {G}.",
            "Phyrexian Arena" to "At the beginning of your upkeep, you draw a card and you lose 1 life.",
            "Ophiomancer" to "At the beginning of each upkeep, if you control no Snakes, create a 1/1 Snake.",
            "Midnight Reaper" to null,
            "Twilight Prophet" to "At the beginning of your upkeep, if you have the city's blessing, reveal…",
            "Sword of the Meek" to "at the beginning of your end step, untap it."
        )
        val triggers = deckTriggers(cards + ("Phyrexian Arena" to "At the beginning of your upkeep, you draw a card."))
        assertEquals(
            listOf(
                TriggerCard("Bitterblossom", TriggerStep.UPKEEP),
                TriggerCard("Phyrexian Arena", TriggerStep.UPKEEP),
                TriggerCard("Twilight Prophet", TriggerStep.UPKEEP),
                TriggerCard("Sword of the Meek", TriggerStep.END)
            ),
            triggers
        )
        assertEquals(listOf("Upkeep: Bitterblossom, Phyrexian Arena, Twilight Prophet", "End step: Sword of the Meek"), reminderLines(triggers))
        assertTrue(reminderLines(emptyList()).isEmpty())
    }

    // ---- Tokens ----

    @Test
    fun aTokenChipSaysWhatItIsAndHowMany() {
        assertEquals("Goblin 1/1 ×3", tokenChipText(SeatToken("g", "Goblin", "1/1"), 3))
        assertEquals("Treasure ×0", tokenChipText(SeatToken("t", "Treasure", null), 0))
    }

    @Test
    fun tokenCountsNeverGoBelowZeroAndOnlyForTheDecksTokens() {
        val tokens = listOf(SeatToken("g", "Goblin", "1/1"))
        assertEquals(mapOf("g" to 2), changedTokenCounts(mapOf("g" to 1), tokens, "g", 1))
        assertEquals(mapOf("g" to 0), changedTokenCounts(mapOf("g" to 1), tokens, "g", -5))
        assertEquals(mapOf("g" to 1), changedTokenCounts(mapOf("g" to 1), tokens, "nope", 1))
    }

    // ---- The clock ----

    @Test
    fun theClockLeavesOutPausedTime() {
        var clock = GameClock(startedAt = 1_000)
        assertEquals(9_000, clock.elapsed(10_000))
        clock = clock.pause(10_000)
        assertTrue(clock.paused)
        assertEquals(9_000, clock.elapsed(50_000))
        assertEquals(clock, clock.pause(60_000))
        clock = clock.resume(70_000)
        assertFalse(clock.paused)
        assertEquals(19_000, clock.elapsed(80_000))
        assertEquals(clock, clock.resume(90_000))
    }

    @Test
    fun clockTimesRead() {
        assertEquals("0:00", formatClock(0))
        assertEquals("4:05", formatClock(245_000))
        assertEquals("1:02:09", formatClock(3_729_000))
        assertEquals("−0:12", formatClock(-12_000))
        assertEquals(1, gameMinutes(10_000))
        assertEquals(42, gameMinutes(42 * 60_000L + 59_000))
    }

    @Test
    fun theTurnTimerCountsDownOnTheGameClock() {
        assertNull(turnTimeLeft(0, 0, 100_000))
        assertEquals(120_000L - 30_000, turnTimeLeft(2, 60_000, 90_000))
        assertEquals(-10_000L, turnTimeLeft(1, 0, 70_000))
    }

    // ---- On the wire ----

    @Test
    fun deckInfoGoesThereAndBack() {
        val info = SeatDeckInfo(
            "Krenko Goblins",
            listOf(SeatToken("g", "Goblin", "1/1", art = "https://cards.scryfall.io/x.jpg"), SeatToken("t", "Treasure")),
            listOf(TriggerCard("Krenko, Tin Street Kingpin", TriggerStep.COMBAT))
        )
        val sent = JSONObject(RemoteActions.deckInfo(info).toString())
        assertEquals("deckInfo", sent.getString("type"))
        val back = parseDeckInfo(sent)!!
        // The art stays on the phone.
        assertEquals(info.copy(tokens = info.tokens.map { it.copy(art = null) }), back)
    }

    @Test
    fun theTableTrustsADeckInfoOnlySoFar() {
        val many = JSONArray((1..60).map { JSONObject().put("id", "id$it").put("name", "Token $it") })
        val o = JSONObject().put("type", "deckInfo").put("deck", "x".repeat(300)).put("tokens", many)
            .put("triggers", JSONArray().put(JSONObject().put("name", "A").put("step", "upkeep")).put(JSONObject().put("name", "B").put("step", "precombat")).put(JSONObject().put("step", "end")))
        val info = parseDeckInfo(o)!!
        assertEquals(DECK_INFO_MAX_ITEMS, info.tokens.size)
        assertEquals(DECK_INFO_MAX_NAME, info.deck!!.length)
        assertEquals(listOf(TriggerCard("A", TriggerStep.UPKEEP)), info.triggers)
        assertNull(parseDeckInfo(JSONObject().put("type", "token")))
        // Missing lists read as none.
        assertEquals(SeatDeckInfo(null, emptyList(), emptyList()), parseDeckInfo(JSONObject().put("type", "deckInfo")))
    }

    @Test
    fun tokenAndHoldOkMessages() {
        val t = RemoteActions.token("g", -1)
        assertEquals("token", t.getString("type"))
        assertEquals("g", t.getString("id"))
        assertEquals(-1, t.getInt("delta"))
        assertEquals("holdOk", RemoteActions.holdOk().getString("type"))
        assertNull(holdAfterOk(2, 3))
        assertEquals(2, holdAfterOk(2, 2))
        assertNull(holdAfterOk(null, 1))
    }

    private fun seat(n: Int, tokens: List<RemoteToken>? = null) = RemoteSeat(
        seat = n, name = "P$n", color = "#112233", ink = "#ffffff", life = 40, out = null, poison = 0,
        counters = emptyMap(), commanderDamage = emptyList(), background = null, deck = null, commander = null,
        userId = null, avatarPath = null, canUndo = false, partner = false, tokens = tokens
    )

    private fun state(players: List<RemoteSeat>, clock: RemoteClock? = null, timer: RemoteTurnTimer? = null) = RemoteState(
        v = REMOTE_VERSION, gameId = "g1", remotes = true, turn = RemoteTurn(1, 2), startedAt = 1, longPress = 10,
        players = players, shownCard = null, over = null, clock = clock, turnTimer = timer
    )

    @Test
    fun seatTokensClockAndTimerGoThereAndBack() {
        val sent = state(
            listOf(seat(1, listOf(RemoteToken("g", "Goblin", "1/1", 3), RemoteToken("t", "Treasure", null, 0))), seat(2, emptyList()), seat(3)),
            RemoteClock(754_000, paused = true), RemoteTurnTimer(120, -4_000)
        )
        val back = RemoteState.parse(JSONObject(sent.toJson().toString()))!!
        assertEquals(sent.players.map { it.tokens }, back.players.map { it.tokens })
        assertEquals(RemoteClock(754_000, true), back.clock)
        assertEquals(RemoteTurnTimer(120, -4_000), back.turnTimer)
    }

    @Test
    fun aTableFromBeforeHasNoneOfIt() {
        val old = JSONObject(state(listOf(seat(1))).toJson().toString())
        old.remove("clock")
        old.remove("turnTimer")
        val back = RemoteState.parse(old)!!
        assertNull(back.clock)
        assertNull(back.turnTimer)
        assertNull(back.players[0].tokens)
        // And no timer reads as none.
        assertFalse(JSONObject(state(listOf(seat(1))).toJson().toString()).has("clock"))
    }
}
