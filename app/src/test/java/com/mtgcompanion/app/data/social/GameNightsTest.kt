package com.mtgcompanion.app.data.social

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Game night invites (GameNights.kt). The web app runs the same cases in
 * MtgCompanionWeb/tests/social/nights.test.ts.
 */
class GameNightsTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private fun at(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** Fri 9 Oct 2026, 19:00 UTC. */
    private val fri7pm = at(2026, 10, 9, 19)
    private val hour = 3_600_000L

    private fun person(id: String, name: String) = Profile(id, name.lowercase(), name, null)
    private fun inv(id: String, name: String, answer: RsvpAnswer?, deck: String? = null) =
        NightInvitee(person(id, name), true, answer, deck, if (answer != null) 1L else null)

    private val everyone = listOf(
        inv("priya", "Priya", RsvpAnswer.GOING), inv("me", "Me", RsvpAnswer.GOING, "Krenko"), inv("alex", "Alex", RsvpAnswer.GOING),
        inv("jo", "Jo", RsvpAnswer.GOING), inv("sam", "Sam", RsvpAnswer.MAYBE), inv("max", "Max", null)
    )

    private fun night(
        id: String = "n1",
        startsAt: Long = fri7pm,
        podId: String = "p1",
        cancelled: Boolean = false,
        seasonName: String? = "Season 2",
        note: String? = null,
        invitees: List<NightInvitee> = everyone
    ) = NightInvite(
        id = id, podId = podId, podName = "Thursday crew", organiser = person("priya", "Priya"), startsAt = startsAt, tz = "UTC", place = "Priya's",
        note = note, cancelled = cancelled, createdAt = 0, updatedAt = 0, seasonId = seasonName?.let { "s2" }, seasonName = seasonName, invitees = invitees
    )

    @Test
    fun `dates read the same in both apps`() {
        assertEquals("Fri 9 Oct · 7pm", nightWhen(fri7pm, utc))
        assertEquals("Fri 9 Oct · 7:30pm", nightWhen(fri7pm + 30 * 60_000, utc))
        assertEquals("Sat 10 Oct · 12:05am", nightWhen(at(2026, 10, 10, 0, 5), utc))
        assertEquals("Sat 10 Oct · 12pm", nightWhen(at(2026, 10, 10, 12), utc))
        assertEquals("Fri 9 Oct · 8pm", nightWhen(fri7pm, ZoneId.of("Europe/London")))
        assertEquals("FRI" to "9", dateTile(fri7pm, utc))
    }

    @Test
    fun `the RSVP summaries`() {
        val unanswered = night(invitees = everyone.map { if (it.user.userId == "me") it.copy(answer = null, deck = null) else it })
        assertEquals("3 going · Sam maybe · you haven't answered", rsvpLine(unanswered, "me"))
        assertEquals("4 going · Sam maybe · you're going", rsvpLine(night(), "me"))
        assertEquals("Called off", rsvpLine(night(cancelled = true), "me"))
        assertEquals("Priya's · 4 going, Sam maybe", chatCardLine(night(), "me"))
        assertEquals("Priya's · you're going · Season 2", playLine(night(), "me"))
        assertEquals("Priya's · you're going", playLine(night(seasonName = null), "me"))
        assertEquals("Thursday crew · counts for Season 2 · asked by Priya", headerLine(night(), "me"))
        assertEquals("Thursday crew · asked by you", headerLine(night(seasonName = null), "priya"))
        assertEquals("WHO'S COMING · 5 OF 6", whoHeader(night()))
        val many = night(invitees = listOf(inv("priya", "Priya", RsvpAnswer.GOING), inv("a", "A", RsvpAnswer.MAYBE), inv("b", "B", RsvpAnswer.MAYBE), inv("c", "C", RsvpAnswer.MAYBE)))
        assertEquals("1 going · 3 maybe · you haven't answered", rsvpLine(many, "me"))
        assertEquals("Nobody going yet · you can't make it", rsvpLine(night(invitees = listOf(inv("priya", "Priya", RsvpAnswer.CANT), inv("me", "Me", RsvpAnswer.CANT))), "me"))
    }

    @Test
    fun `who's coming - host, you, named decks, then grouped by answer`() {
        assertEquals(
            listOf(
                Triple("Priya", "Going · hosting", WhoTone.GOING),
                Triple("You", "Going · Krenko", WhoTone.GOING),
                Triple("Alex, Jo", "Going", WhoTone.GOING),
                Triple("Sam", "Maybe", WhoTone.MAYBE),
                Triple("Max", "No answer yet", WhoTone.NONE)
            ),
            whoRows(night(), "me").map { Triple(it.names, it.status, it.tone) }
        )
        val withDeck = night(invitees = listOf(inv("priya", "Priya", RsvpAnswer.GOING), inv("sam", "Sam", RsvpAnswer.MAYBE, "Atraxa"), inv("jo", "Jo", RsvpAnswer.CANT, "Ur-Dragon")))
        assertEquals(listOf("You" to "Going · hosting", "Sam" to "Maybe · Atraxa", "Jo" to "Can't"), whoRows(withDeck, "priya").map { it.names to it.status })
    }

    @Test
    fun `the next night, and who comes along`() {
        val a = night(id = "a", startsAt = fri7pm + 48 * hour)
        val b = night(id = "b", startsAt = fri7pm, podId = "p2")
        val c = night(id = "c", startsAt = fri7pm - hour, cancelled = true)
        assertEquals("b", nextNight(listOf(a, b, c), fri7pm - 10 * hour)?.id)
        assertEquals("a", nextNight(listOf(a, b, c), fri7pm - 10 * hour, "p1")?.id)
        assertEquals("b", nextNight(listOf(a, b), fri7pm + 5 * hour)?.id) // under way
        assertEquals("a", nextNight(listOf(a, b), fri7pm + 7 * hour)?.id)
        assertNull(nextNight(emptyList(), 0))
        assertEquals(listOf("Priya", "Alex", "Jo", "Sam"), attendeesOf(night(), "me").map { it.name })
    }

    @Test
    fun `the reminder comes a day before, for those coming`() {
        assertEquals(fri7pm - 24 * hour, reminderAt(night(), "me", fri7pm - 48 * hour))
        assertNull(reminderAt(night(), "me", fri7pm - 20 * hour)) // already past
        assertEquals(fri7pm - 24 * hour, reminderAt(night(), "max", fri7pm - 48 * hour)) // no answer yet: still reminded
        assertNull(reminderAt(night(invitees = listOf(inv("me", "Me", RsvpAnswer.CANT))), "me", 0))
        assertNull(reminderAt(night(cancelled = true), "me", 0))
        assertNull(reminderAt(night(), "stranger", 0))
        assertEquals("Game night tomorrow" to "7pm at Priya's · 4 going", reminderText(night(), utc))
    }

    @Test
    fun `an ics file for the calendar`() {
        val ics = nightIcs(night(note = "Bring tokens, dice; and snacks\nsee you"), at(2026, 10, 7, 12))
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertTrue(ics.contains("\r\nUID:n1@manabind.com\r\n"))
        assertTrue(ics.contains("\r\nDTSTAMP:20261007T120000Z\r\n"))
        assertTrue(ics.contains("\r\nDTSTART:20261009T190000Z\r\n"))
        assertTrue(ics.contains("\r\nDTEND:20261009T230000Z\r\n"))
        assertTrue(ics.contains("\r\nSUMMARY:Game night · Thursday crew\r\n"))
        assertTrue(ics.contains("\r\nLOCATION:Priya's\r\n"))
        assertTrue(ics.contains("\r\nDESCRIPTION:Bring tokens\\, dice\\; and snacks\\nsee you\r\n"))
        assertTrue(ics.contains("\r\nSTATUS:CONFIRMED\r\n"))
        assertTrue(nightIcs(night(cancelled = true), 0).contains("STATUS:CANCELLED"))
        val long = "DESCRIPTION:" + "é".repeat(80)
        val parts = foldIcsLine(long).split("\r\n")
        assertEquals(3, parts.size)
        parts.forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 75) }
        assertEquals(long, parts.mapIndexed { i, p -> if (i == 0) p else p.drop(1) }.joinToString(""))
        assertEquals("SHORT:x", foldIcsLine("SHORT:x"))
    }

    @Test
    fun `make pods on the night - the user and everyone coming`() {
        var n = 0
        val id = { "p${++n}" }
        val players = playersFromInvite(emptyList(), night(), "me", "Me", MyNightDeck("d1", "Krenko", "Krenko, Mob Boss"), id)
        assertEquals(
            listOf(
                listOf("Me", "ME", null, "Krenko"), listOf("Priya", "FRIEND", "priya", null), listOf("Alex", "FRIEND", "alex", null),
                listOf("Jo", "FRIEND", "jo", null), listOf("Sam", "FRIEND", "sam", null)
            ),
            players.map { listOf(it.name, it.kind.name, it.userId, it.deck) }
        )
        assertEquals("d1", players[0].deckId)
        // Already there: kept, not doubled.
        assertEquals(players.size, playersFromInvite(players, night(), "me", "Me", null, id).size)
        data class D(val id: String, val name: String)
        assertEquals("d1", deckNamed(listOf(D("d1", "Krenko ")), "krenko") { it.name }?.id)
        assertNull(deckNamed(listOf(D("d1", "Krenko")), null) { it.name })
    }

    @Test
    fun `parsing a night from the server`() {
        val parsed = parseNightInvite(
            JSONObject(
                """{"id":"n1","podId":"p1","podName":"Thursday crew","organiser":{"user_id":"priya","username":"priya","display_name":"Priya","avatar_path":null},
                "startsAt":$fri7pm,"tz":"Europe/London","place":"Priya's","note":null,"cancelled":false,"createdAt":1,"updatedAt":2,"season":null,
                "invitees":[{"user":{"user_id":"priya","username":"priya","display_name":"Priya","avatar_path":null},"member":true,"answer":"going","deck":null,"answeredAt":3},
                {"user":{"user_id":"x","username":"x","display_name":"X","avatar_path":null},"member":false,"answer":"nope","deck":null,"answeredAt":null}]}"""
            )
        )!!
        assertNull(parsed.invitees[1].answer)
        assertFalse(parsed.invitees[1].member)
        assertNull(parsed.seasonName)
        assertNull(parsed.note)
        assertEquals(RsvpAnswer.GOING, parsed.invitees[0].answer)
        assertNull(parseNightInvite(null as JSONObject?))
    }

    @Test
    fun `notification targets`() {
        assertEquals("night" to "abc", notificationTarget("night:abc"))
        assertEquals("pod" to "p1", notificationTarget("pod:p1"))
        assertNull(notificationTarget("messages"))
    }
}
