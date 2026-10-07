package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The friends' Activity feed, its privacy switches and comments on shared decks
 * (ActivityCommentsLogic.kt, ActivityComments.kt). The web app runs the same cases in
 * MtgCompanionWeb/tests/social/activity.test.ts.
 */
class ActivityCommentsLogicTest {

    private val priya = Profile("p", "priya", "Priya", null)
    private val sam = Profile("s", "sam", "Sam", null)

    private fun item(kind: String, actor: Profile? = priya) = FeedItem(kind = kind, actor = actor, at = 1000)

    private fun text(l: FeedLine) = l.parts.joinToString("") { if (it.bold) "*${it.text}*" else it.text }

    @Test
    fun `privacy defaults - decks, trade and leagues on, selling off`() {
        assertEquals(ActivityPrefs(decks = true, forTrade = true, selling = false, leagues = true), ActivityPrefs())
        assertEquals(ActivityPrefs(), parsePrefs("null"))
        assertEquals(ActivityPrefs(decks = false, forTrade = true, selling = true, leagues = true), parsePrefs("""{"decks": false, "selling": true}"""))
        val p = ActivityPrefs()
        assertEquals(listOf(true, true, false, true), ACTIVITY_PREF_ROWS.map { p.isOn(it.key) })
        assertTrue(p.withPref("selling", true).selling)
        assertFalse(p.withPref("decks", false).decks)
    }

    @Test
    fun `a card on the user wishlist marked for trade asks for it`() {
        val one = feedLine(item("for_trade").copy(count = 3, cards = listOf("Rhystic Study" to null), wanted = listOf("Rhystic Study")))
        assertEquals("*Priya* added *Rhystic Study* to their trade binder", text(one))
        assertEquals("It's on your wishlist", one.sub)
        assertEquals(FeedAction("Ask Priya for it", FeedActionKind.ASK), one.action)
        val two = feedLine(item("for_trade").copy(wanted = listOf("Rhystic Study", "Sol Ring")))
        assertEquals("*Priya* added *Rhystic Study and Sol Ring* to their trade binder", text(two))
        assertEquals("They're on your wishlist", two.sub)
        assertEquals("Ask Priya for them", two.action?.label)
        val none = feedLine(item("for_trade").copy(count = 3, cards = listOf("Ponder" to null, "Brainstorm" to null)))
        assertEquals("*Priya* marked 3 cards for trade", text(none))
        assertEquals("Ponder, Brainstorm and 1 more", none.sub)
        assertNull(none.action)
    }

    @Test
    fun `a new or shared deck opens its comments`() {
        val built = feedLine(item("shared").copy(itemKind = "deck", itemId = "d1", name = "Meren of Clan Nel Toth", newDeck = true))
        assertEquals("*Priya* built a new deck: *Meren of Clan Nel Toth*", text(built))
        assertEquals("Shared with friends", built.sub)
        assertEquals(FeedAction("Look and comment", FeedActionKind.COMMENTS), built.action)
        assertEquals("*Priya* shared a deck: *Meren*", text(feedLine(item("shared").copy(itemKind = "deck", itemId = "d1", name = "Meren"))))
        assertEquals("*Priya* shared all their decks", text(feedLine(item("shared").copy(itemKind = "deck"))))
        assertNull(feedLine(item("shared").copy(itemKind = "collection", itemId = "b", name = "Trade binder")).action)
        assertEquals("*Priya* updated a deck: *Meren*", text(feedLine(item("deck_updated").copy(itemKind = "deck", itemId = "d1", name = "Meren"))))
        assertEquals(FeedDeck("p", "d1"), feedDeck(item("shared").copy(itemKind = "deck", itemId = "d1")))
        assertNull(feedDeck(item("shared").copy(itemKind = "collection", itemId = "b")))
        assertEquals("/shared/p/deck/d%201?tab=comments", commentsPath("p", "d 1"))
    }

    @Test
    fun `selling says how many are on the wishlist`() {
        val l = feedLine(item("selling", priya.copy(displayName = "Jo")).copy(count = 12, wantedCount = 2, wanted = listOf("A", "B")))
        assertEquals("*Jo* is selling 12 cards", text(l))
        assertEquals("2 are on your wishlist", l.sub)
        assertEquals(FeedAction("See them", FeedActionKind.SELLING), l.action)
        assertEquals("1 is on your wishlist", feedLine(item("selling").copy(count = 1, wantedCount = 1)).sub)
        assertNull(feedLine(item("selling").copy(count = 1, wantedCount = 0)).sub)
    }

    private fun table(vararg rows: LeagueRow, nights: Int = 3) = LeagueSnapshot(rows.toList(), nights)

    @Test
    fun `league news names the leader, unless they keep it quiet`() {
        val season = item("league", null).copy(podName = "Thursday crew", name = "Season 2", seasonId = "s2", ended = false, maxNights = 5)
        val t = table(LeagueRow("p", "Priya", 12, 1), LeagueRow("s", "Sam", 9, 2))
        val l = feedLine(season, t)
        assertEquals("*Thursday crew*: Priya leads Season 2 by 3 points", text(l))
        assertEquals("2 game nights left", l.sub)
        val close = table(LeagueRow("p", "Priya", 10, 1), LeagueRow("s", "Sam", 9, 2), nights = 4)
        assertEquals("Priya leads Season 2 by 1 point", leagueText(season, close).text)
        assertEquals("1 game night left", leagueText(season, close).sub)
        assertNull(leagueText(season, close.copy(nights = 5)).sub)
        assertEquals("Priya leads Season 2", leagueText(season, table(LeagueRow("p", "Priya", 10, 1), LeagueRow("s", "Sam", 10, 2))).text)
        assertEquals("Priya leads Season 2", leagueText(season, table(LeagueRow("p", "Priya", 10, 1))).text)
        assertEquals("Priya and Sam share the lead in Season 2", leagueText(season, table(LeagueRow("p", "Priya", 10, 1), LeagueRow("s", "Sam", 10, 1))).text)
        assertEquals(
            "3 players share the lead in Season 2",
            leagueText(season, table(LeagueRow("p", "A", 10, 1), LeagueRow("s", "B", 10, 1), LeagueRow(null, "C", 10, 1))).text
        )
        assertEquals("Season 2 has started", leagueText(season, table(nights = 0)).text)
        assertEquals("New results in Season 2", leagueText(season, null).text)
        assertEquals("New results in Season 2", leagueText(season.copy(quiet = listOf("p")), t).text)
        assertEquals("Priya leads Season 2 by 3 points", leagueText(season.copy(quiet = listOf("s")), t).text)
    }

    @Test
    fun `a season over names its champion, unless they keep it quiet`() {
        val over = item("league", null).copy(podName = "Thursday crew", name = "Season 1", ended = true, champion = "Priya", championIds = listOf("p"))
        assertEquals("*Thursday crew*: Season 1 champion: Priya", text(feedLine(over)))
        assertEquals("Season 1 champions: Priya & Sam", leagueText(over.copy(champion = "Priya & Sam", championIds = listOf("p", "s")), null).text)
        assertEquals("Season 1 is over", leagueText(over.copy(quiet = listOf("p")), null).text)
        assertEquals("Season 1 is over", leagueText(over.copy(champion = null), null).text)
    }

    @Test
    fun `comments on the user decks, and replies to theirs`() {
        val onCard = feedLine(item("comment").copy(itemKind = "deck", itemId = "d1", name = "Meren", itemOwner = "me", onMine = true, cardName = "Gray Merchant of Asphodel", body = "Cut this?"))
        assertEquals("*Priya* commented on *Gray Merchant of Asphodel* in *Meren*", text(onCard))
        assertEquals("“Cut this?”", onCard.sub)
        assertEquals(FeedAction("Reply", FeedActionKind.COMMENTS), onCard.action)
        assertEquals("*Priya* commented on *Meren*", text(feedLine(item("comment").copy(name = "Meren", onMine = true))))
        assertEquals("*Priya* replied on *Meren*", text(feedLine(item("comment").copy(name = "Meren", onMine = true, reply = true))))
        assertEquals("*Priya* replied to your comment on *Meren*", text(feedLine(item("comment").copy(name = "Meren", onMine = false, reply = true))))
        assertEquals(FeedDeck("me", "d1"), feedDeck(item("comment").copy(itemKind = "deck", itemId = "d1", itemOwner = "me")))
        assertEquals("a b c", cut("a  b\n c", 10))
        assertEquals("abcd…", cut("abcdefghij", 5))
    }

    @Test
    fun `the older feed items still read`() {
        assertEquals("*Priya* recorded a game in *Thursday crew*", text(feedLine(item("pod_game").copy(podName = "Thursday crew", winner = "Sam", players = 4))))
        assertEquals("No winner · 4 players", feedLine(item("pod_game").copy(players = 4)).sub)
        assertEquals("*Priya* did something new", text(feedLine(item("mystery"))))
    }

    @Test
    fun `taps go where the item is about`() {
        assertEquals(ActivityTarget.SharedItem("me", "deck", "d1", comments = true), activityTarget(item("comment").copy(itemKind = "deck", itemId = "d1", itemOwner = "me")))
        assertNull(activityTarget(item("selling")))
        assertEquals(ActivityTarget.Playgroup, activityTarget(item("league", null)))
        assertEquals(ActivityTarget.Playgroup, activityTarget(item("pod_game")))
        assertEquals(ActivityTarget.SharedItem("p", "deck", "d1"), activityTarget(item("shared").copy(itemKind = "deck", itemId = "d1")))
        assertEquals(ActivityTarget.FriendShared("p"), activityTarget(item("shared").copy(itemKind = "deck")))
        assertEquals(ActivityTarget.Friend("p"), activityTarget(item("for_trade")))
    }

    private fun ft(name: String, quantity: Int = 1, id: String = name) = ForTradeCard("b1", "Trades", id, name, null, 1, quantity, if (quantity == 0) 1 else 0, null)

    @Test
    fun `asking for wanted cards takes one copy of each`() {
        val cards = askCards(listOf(ft("Rhystic Study"), ft("rhystic study", 1, "x"), ft("Ponder"), ft("Sol Ring", 0)), listOf("Rhystic Study", "Sol Ring", "Mana Crypt"))
        assertEquals(listOf(listOf("Rhystic Study", 1, false, "b1"), listOf("Sol Ring", 1, true, "b1")), cards.map { listOf(it.name, it.quantity, it.foil, it.collectionId) })
        fun sell(name: String, wanted: Boolean) = SellingCard("b", null, name, name, null, 2, 2, 0, null, wanted)
        assertEquals(listOf("A"), sellingAsk(listOf(sell("A", true), sell("B", false), sell("A", true))).map { it.name })
    }

    private fun comment(id: String, author: Profile = priya, parent: String? = null, cardName: String? = null, hidden: Boolean = false) =
        DeckComment(id, parent, author, "hi", cardName, null, hidden, id.filter { it.isDigit() }.toLongOrNull() ?: 0, mine = false)

    @Test
    fun `comments thread one level deep, oldest first`() {
        val list = listOf(
            comment("c3", sam, parent = "c1"),
            comment("c2"),
            comment("c1"),
            comment("c4", priya, parent = "c9"), // its comment is gone
            comment("c5", sam, parent = "c3") // a reply to a reply
        )
        val threads = threadComments(list)
        assertEquals(listOf("c1" to listOf("c3"), "c2" to emptyList<String>()), threads.map { t -> t.comment.id to t.replies.map { it.id } })
        assertEquals(3, commentCount(threads))
        assertEquals("Comments · 3", commentsTabLabel(3))
        assertEquals("Comments", commentsTabLabel(0))
    }

    @Test
    fun `comment headers say who and what about`() {
        val deck = listOf("Gray Merchant of Asphodel", "Sol Ring")
        assertEquals("Priya · on Gray Merchant of Asphodel", commentHeader(comment("c1", cardName = "Gray Merchant of Asphodel"), "me", "s", deck))
        assertEquals("Sam · owner", commentHeader(comment("c2", sam, parent = "c1"), "me", "s", deck))
        assertEquals("You · on the deck", commentHeader(comment("c3", priya.copy(userId = "me")), "me", "s", deck))
        assertEquals("Priya · on the deck · suggests Skullclamp", commentHeader(comment("c4", cardName = "Skullclamp"), "me", "s", deck))
        assertEquals("You · on the deck", commentHeader(comment("c5", sam), "s", "s", deck))
        assertEquals("Priya · on the deck · hidden", commentHeader(comment("c6", hidden = true), "s", "s", deck))
    }

    @Test
    fun `who may do what with a comment`() {
        val deck = listOf("Gray Merchant of Asphodel")
        val onCard = comment("c1", cardName = "Gray Merchant of Asphodel")
        val suggest = comment("c2", priya.copy(userId = "me"), cardName = "Skullclamp")
        // A friend looking at Priya's comment on a card in the deck.
        assertEquals(CommentActions(reply = true, swap = true, offer = false, remove = false, hide = false, report = true), commentActions(onCard, "me", "s", true, deck, false))
        // The owner on the same comment: may hide and delete it.
        assertEquals(CommentActions(reply = true, swap = true, offer = false, remove = true, hide = true, report = true), commentActions(onCard, "s", "s", true, deck, false))
        // The user's own suggestion of a card they have: offer it, delete it.
        assertEquals(CommentActions(reply = true, swap = false, offer = true, remove = true, hide = false, report = false), commentActions(suggest, "me", "s", true, deck, true))
        assertFalse(commentActions(suggest, "me", "s", true, deck, false).offer)
        assertFalse(commentActions(suggest, "s", "s", true, deck, true).offer)
        // A reply: no replying to it, no swap.
        val reply = comment("c3", sam, parent = "c1", cardName = "Gray Merchant of Asphodel")
        assertEquals(CommentActions(reply = false, swap = false, offer = false, remove = false, hide = false, report = true), commentActions(reply, "me", "s", true, deck, false))
        // Someone who can't comment (a pod-mate who isn't a friend) only reads and reports.
        assertEquals(CommentActions(reply = false, swap = false, offer = false, remove = false, hide = false, report = true), commentActions(onCard, "x", "s", false, deck, false))
        // The owner's own comment: delete, not hide or report.
        assertEquals(CommentActions(reply = true, swap = false, offer = false, remove = true, hide = false, report = false), commentActions(comment("c4", sam), "s", "s", true, deck, false))
    }

    @Test
    fun `comment wording`() {
        assertEquals("Instead of Gray Merchant of Asphodel: ", swapReply("Gray Merchant of Asphodel"))
        assertEquals("Comment on Sam's deck", composerPlaceholder("Sam", false, null))
        assertEquals("Comment on your deck", composerPlaceholder("Sam", true, null))
        assertEquals("Reply to Priya", composerPlaceholder("Sam", false, "Priya"))
        assertEquals("Only friends Sam shares the deck with can comment. Sam can hide or delete comments.", commentsNote("Sam", false))
        assertEquals("Only friends you share the deck with can comment. You can hide or delete comments.", commentsNote("Sam", true))
        assertEquals("comment:abc", commentReportId("abc"))
    }

    @Test
    fun `offering a card takes one copy, marked for trade first`() {
        fun binder(id: String, entries: List<CollectionEntry>, type: String = "OWNED") = Collection(id, id, entries, createdAt = 0, type = type)
        fun e(name: String, q: Int, f: Int, forTrade: Int? = null) = CollectionEntry("$name-$q-$f", name, null, quantity = q, foilQuantity = f, forTrade = forTrade)
        val cols = listOf(
            binder("wish", listOf(e("Skullclamp", 4, 0)), "WISHLIST"),
            binder("a", listOf(e("Skullclamp", 3, 0))),
            binder("b", listOf(e("skullclamp", 1, 0, 1))),
            binder("c", listOf(e("Sol Ring", 0, 1)))
        )
        val card = offerCard(cols, "Skullclamp")
        assertEquals("b", card?.collectionId)
        assertEquals(1, card?.quantity)
        assertEquals("a", offerCard(cols.take(2), "Skullclamp")?.collectionId)
        assertEquals(true, offerCard(cols, "Sol Ring")?.foil)
        assertNull(offerCard(cols, "Mana Crypt"))
        assertNull(offerCard(listOf(binder("wish", listOf(e("Skullclamp", 4, 0)), "WISHLIST")), "Skullclamp"))
    }

    @Test
    fun `the server's answers parse, league news without a person included`() {
        val feed = parseFeed(
            """[
              {"kind": "league", "at": 5, "name": "Season 2", "pod_id": "pod", "pod_name": "Thursday crew", "season_id": "s2", "ended": false, "quiet": ["p"], "max_nights": 5},
              {"kind": "for_trade", "at": 4, "actor": {"user_id": "p", "username": "priya", "display_name": "Priya"}, "count": 2,
               "cards": [{"name": "Rhystic Study"}], "wanted": ["Rhystic Study"]},
              {"kind": "shared", "at": 3, "item_kind": "deck", "item_id": "d1"}
            ]"""
        )
        assertEquals(listOf("league", "for_trade"), feed.map { it.kind })
        assertEquals(listOf("p"), feed[0].quiet)
        assertEquals(5, feed[0].maxNights)
        assertNull(feed[0].actor)
        assertEquals(listOf("Rhystic Study"), feed[1].wanted)
        assertEquals(emptyList<FeedItem>(), parseFeed("null"))
        val comments = parseDeckComments(
            """{"is_owner": false, "can_comment": true, "comments": [
              {"id": "c1", "parent": null, "author": {"user_id": "p", "username": "priya", "display_name": "Priya"}, "body": "Cut this?",
               "card_name": "Gray Merchant of Asphodel", "card_image": null, "hidden": false, "created_at": 10, "mine": true}
            ]}"""
        )
        assertTrue(comments!!.canComment)
        assertEquals("Gray Merchant of Asphodel", comments.comments.single().cardName)
        assertNull(comments.comments.single().parent)
        assertNull(parseDeckComments("null"))
        assertNull(parseSelling("null"))
        assertEquals(true, parseSelling("""[{"item_id": "b", "scryfall_id": "s", "name": "Sol Ring", "for_sale": 2, "quantity": 3, "foil_quantity": 0, "wanted": true}]""")?.single()?.wanted)
    }
}
