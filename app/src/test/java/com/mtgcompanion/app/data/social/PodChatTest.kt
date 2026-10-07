package com.mtgcompanion.app.data.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** A pod's chat (PodChat.kt). The web app runs the same cases in MtgCompanionWeb/tests/social/nights.test.ts. */
class PodChatTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private fun at(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun pm(id: Long, sender: String?, at: Long, kind: String = "text", body: String = "hi") = PodMessage(id, "p1", sender, kind, body, null, at)

    @Test
    fun `chat days and runs of messages`() {
        val now = at(2026, 10, 8, 15) // Thursday
        assertEquals("Today", chatDayLabel(at(2026, 10, 8, 1), now, utc))
        assertEquals("Yesterday", chatDayLabel(at(2026, 10, 7, 23), now, utc))
        assertEquals("Tuesday", chatDayLabel(at(2026, 10, 6, 9), now, utc))
        assertEquals("30 Sep", chatDayLabel(at(2026, 9, 30, 9), now, utc))
        assertEquals("30 Dec 2025", chatDayLabel(at(2025, 12, 30, 9), now, utc))

        val tue = at(2026, 10, 6, 18)
        val items = chatItems(
            listOf(
                pm(1, null, tue, "league", "Priya won with Atraxa."),
                pm(2, "priya", tue + 60_000),
                pm(3, "priya", tue + 2 * 60_000),
                pm(4, "priya", tue + 20 * 60_000),
                pm(5, "me", tue + 21 * 60_000),
                pm(6, "sam", at(2026, 10, 8, 9))
            ), "me", now, utc
        )
        assertEquals(
            listOf("day:Tuesday", "1:", "2:name", "3:", "4:name", "5:mine", "day:Today", "6:name"),
            items.map {
                when (it) {
                    is ChatItem.Day -> "day:${it.label}"
                    is ChatItem.Message -> "${it.message.id}:${if (it.showName) "name" else ""}${if (it.mine) "mine" else ""}"
                }
            }
        )
    }

    @Test
    fun `unread counts and the merged Chats list`() {
        val msgs = listOf(pm(1, "sam", 0), pm(2, "me", 0), pm(3, null, 0, "league"), pm(4, "jo", 0))
        assertEquals(2, unreadIn(msgs, 1, "me"))
        assertEquals(3, unreadIn(msgs, 0, "me"))
        assertEquals(
            listOf(1L to "a", 2L to "a", 3L to "b"),
            mergePodMessages(listOf(pm(3, "a", 0), pm(1, "a", 0)), listOf(pm(2, "a", 0), pm(3, "b", 0))).map { it.id to it.sender }
        )
        val rows = mergeChatRows(
            listOf(ChatRow("dm", "sam", 100, 1), ChatRow("dm", "jo", null, 0)),
            listOf(ChatRow("pod", "p1", 200, 3), ChatRow("pod", "p2", 50, 0))
        )
        assertEquals(listOf("p1", "sam", "p2", "jo"), rows.map { it.id })
        assertEquals(4, totalUnread(rows))
        val chats = parsePodChats(
            """[{"podId":"p1","name":"Thursday crew","members":5,"unread":2,"last":{"id":9,"podId":"p1","sender":"sam","kind":"text","body":"See you","ref":null,"createdAt":5,"senderName":"Sam"}},
               {"podId":"p2","name":"Quiet","members":2,"unread":0,"last":null}]"""
        )
        assertEquals("Sam: See you", podPreview(chats[0].last, "me"))
        assertEquals("No messages yet", podPreview(chats[1].last, "me"))
        assertEquals("You: " + "x".repeat(79) + "…", podPreview(PodMessage(1, "p", "me", "text", "x".repeat(100), null, 0), "me"))
        assertEquals("Priya won with Atraxa.", podPreview(PodMessage(1, "p", null, "league", "Priya won with Atraxa.", null, 0), "me"))
        assertEquals("Sam: Shared a deck: Krenko", podPreview(PodMessage(1, "p", "sam", "share", "", PodMessageRef(type = "deck", name = "Krenko"), 0, "Sam"), "me"))
        assertEquals("Priya: Planned a game night", podPreview(PodMessage(1, "p", "priya", "night", "", null, 0, "Priya"), "me"))
    }

    @Test
    fun `league posts and odds and ends`() {
        assertEquals(
            "Table: Priya 14 · you 11 · Sam 9",
            tableLine(listOf(Triple("priya", "Priya", 14), Triple("me", "Me", 11), Triple(null, "Sam", 9), Triple("jo", "Jo", 2)), "me")
        )
        assertNull(tableLine(emptyList(), "me"))
        assertEquals("LEAGUE · SEASON 2", leagueLabel(PodMessageRef(season = "Season 2")))
        assertEquals("GAME", leagueLabel(null))
        assertEquals("Priya, Sam, Alex, Jo and you", membersLine(listOf("Priya", "Sam", "Alex", "Jo")))
        assertEquals("Just you", membersLine(emptyList()))
    }

    @Test
    fun `parsing chat messages`() {
        val m = parsePodMessages(
            """[{"id":4,"podId":"p1","sender":null,"kind":"league","body":"Priya won.","ref":{"gameId":"g","seasonId":"s2","season":"Season 2"},"createdAt":7},
               {"id":5,"podId":"p1","sender":"sam","kind":"weird","body":"hey","ref":null,"createdAt":8}]"""
        )
        assertEquals("league", m[0].kind)
        assertNull(m[0].sender)
        assertEquals("Season 2", m[0].ref?.season)
        assertEquals("text", m[1].kind)
        assertEquals(emptyList<PodMessage>(), parsePodMessages("null"))
    }
}
