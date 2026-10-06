package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.CopyPlace
import com.mtgcompanion.app.data.ServerCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Sharing storage at home: grouping each person's copies by place, the screen's lines, and a pull
 * list's "ask Alex" cards. The web app has the same checks — see MtgCompanionWeb/tests/social/household.test.ts.
 */
class HouseholdTest {

    private val me = "me"
    private val alex = "alex"
    private fun profile(id: String, name: String) = Profile(id, id, name, null)

    private fun home(
        members: List<HouseholdMember> = listOf(
            HouseholdMember(profile(alex, "Alex"), "member"),
            HouseholdMember(profile(me, "Me"), "member"),
            HouseholdMember(profile("sam", "Sam"), "invited")
        ),
        places: List<HouseholdPlace> = listOf(
            HouseholdPlace("red", "Red box", "BOX", me, listOf(me)),
            HouseholdPlace("blue", "Blue box", "BOX", me, listOf(me, alex)),
            HouseholdPlace("abinder", "Alex's binder", "BINDER", alex, listOf(alex))
        )
    ) = Household("h1", "Shared shelf", 1, members, places)

    private fun copy(userId: String, placeId: String, name: String, qty: Int, foil: Boolean = false) =
        ShelfCopy(userId, placeId, "$name-id", name, null, qty, foil)

    @Test
    fun myCopiesInTheSharedPlacesComeFromMyOwnLibraryWishlistsLeftOut() {
        val cols = listOf(
            Collection("b1", "b1", listOf(
                CollectionEntry("s1", "Sol Ring", null, quantity = 3, foilQuantity = 1,
                    places = listOf(CopyPlace("red", 2), CopyPlace("red", 1, foil = true), CopyPlace("private", 1)))
            ), createdAt = 1),
            Collection("b2", "b2", listOf(CollectionEntry("s1", "Sol Ring", null, quantity = 1, places = listOf(CopyPlace("red", 1)))), createdAt = 1),
            Collection("w", "w", listOf(CollectionEntry("s9", "Wish", null, quantity = 1, places = listOf(CopyPlace("red", 1)))), createdAt = 1, type = "WISHLIST")
        )
        val mine = myShelfCopies(cols, listOf("red"), me)
        assertEquals(
            listOf(listOf("red", "Sol Ring", "1", "true"), listOf("red", "Sol Ring", "3", "false")),
            mine.map { listOf(it.placeId, it.name, it.qty.toString(), it.foil.toString()) }.sortedBy { it.joinToString() }
        )
        assertTrue(mine.all { it.userId == me })
    }

    @Test
    fun theScreenIntroTotalsPerPersonAndEachPlacePerPerson() {
        val h = home()
        val copies = listOf(copy(me, "red", "Sol Ring", 612), copy(me, "blue", "Island", 210), copy(alex, "blue", "Forest", 188), copy(alex, "abinder", "Opt", 342))
        assertEquals("You and Alex keep cards on the same shelf. Each of you still owns your own cards.", householdIntro(h, me))
        assertEquals("Pull lists can include Alex's cards, marked \"ask Alex\", and the borrowed cards show under Loans.", deckNote(h, me))
        val totals = peopleTotals(h, me, copies) { if (it.name == "Sol Ring") 2.0 else null }
        assertEquals(listOf(Triple("You", 822, 1224.0), Triple("Alex", 530, null)), totals.map { Triple(it.label, it.copies, it.usd) })
        val lines = placeLines(h, me, copies)
        assertEquals(listOf("Yours 612 · Alex 0", "Yours 210 · Alex 188", "Alex 342 · you can see, not change"), lines.map { it.line })
        assertEquals(listOf(listOf(false, true, false), listOf(false, true, false), listOf(true, false, false)), lines.map { listOf(it.readOnly, it.mine, it.canJoin) })
        // Someone else's box, not a binder: the user may keep cards there too.
        val box = placeLines(home(places = listOf(HouseholdPlace("g", "Green box", "BOX", alex, listOf(alex)))), me, emptyList())
        assertEquals("Alex 0 · you can see, not change" to true, box[0].line to box[0].canJoin)
        assertEquals(listOf("You" to listOf("Island"), "Alex" to listOf("Forest")), placeContents(h, me, "blue", copies).map { g -> g.name to g.copies.map { it.name } })
        assertEquals("h1", householdOfPlace(listOf(h), "blue")?.id)
        assertNull(householdOfPlace(listOf(h), "nowhere"))
    }

    @Test
    fun threePeopleReadAsAList() {
        val h = home(members = home().members.take(2) + HouseholdMember(profile("sam", "Sam"), "member"))
        assertEquals("You, Alex and Sam keep cards on the same shelf. Each of you still owns your own cards.", householdIntro(h, me))
        assertEquals("Pull lists can include Alex's and Sam's cards, marked \"ask Alex\" or \"ask Sam\", and the borrowed cards show under Loans.", deckNote(h, me))
    }

    @Test
    fun aPullListAsksThePeopleAtHomeForWhatTheDeckIsShortOf() {
        val h = home()
        val cards = HouseholdCards(
            copies = listOf(copy(alex, "blue", "Sol Ring", 1), copy(alex, "abinder", "Opt", 4), copy(alex, "unshared", "Brainstorm", 1), copy(me, "blue", "Ponder", 1))
        )
        val r = pullAsks(listOf(PullShort("Sol Ring", "x", 2), PullShort("opt", "y", 1), PullShort("Brainstorm", "z", 1)), listOf(Shelf(h, cards)), me)
        assertEquals(1, r.groups.size)
        assertEquals("Ask Alex", r.groups[0].title)
        assertEquals(
            listOf(listOf("opt", "1", "ask Alex · Alex's binder", "Opt-id"), listOf("Sol Ring", "1", "ask Alex · Blue box", "Sol Ring-id")),
            r.groups[0].rows.map { listOf(it.name, it.qty.toString(), it.hint, it.printingId) }
        )
        // Not on a shared shelf, or more than Alex has: still to buy.
        assertEquals(listOf(PullShort("Sol Ring", "x", 1), PullShort("Brainstorm", "z", 1)), r.stillMissing)
        assertEquals(listOf(ServerCard("opt", 1, "Opt-id"), ServerCard("Sol Ring", 1, "Sol Ring-id")), borrowCards(r.groups[0]))
    }

    @Test
    fun cardsBorrowedAlreadyShowAsBorrowedAndCopiesLentFromTheShelfAreNotAskedForAgain() {
        val h = home()
        val cards = HouseholdCards(
            copies = listOf(copy(alex, "blue", "Sol Ring", 2), copy(alex, "blue", "Opt", 1)),
            loans = listOf(
                ShelfLoan("l1", "hh-1", alex, me, listOf(ServerCard("Sol Ring", 1, "p"))),
                ShelfLoan("l2", "hh-2", alex, "sam", listOf(ServerCard("Opt", 1, "")))
            )
        )
        val r = pullAsks(listOf(PullShort("Sol Ring", "x", 2), PullShort("Opt", "y", 1)), listOf(Shelf(h, cards)), me)
        assertEquals(
            listOf(listOf("Sol Ring", "1", "false", "ask Alex · Blue box"), listOf("Sol Ring", "1", "true", "borrowed from Alex")),
            r.groups[0].rows.map { listOf(it.name, it.qty.toString(), it.borrowed.toString(), it.hint) }
        )
        assertEquals(listOf(PullShort("Opt", "y", 1)), r.stillMissing)
        assertEquals(listOf(ServerCard("Sol Ring", 1, "Sol Ring-id")), borrowCards(r.groups[0]))
        assertEquals("You have 1 card of Alex's", shelfLoanLine(h, me, cards.loans[0]))
        assertEquals("Me has 1 of your cards", shelfLoanLine(h, alex, cards.loans[0]))
    }

    @Test
    fun withoutHouseholdsOrBeforeTheServerHasThemNothingIsAskedAndEverythingStaysMissing() {
        val missing = listOf(PullShort("Sol Ring", "x", 2), PullShort("Opt", "y", 0))
        assertEquals(PullAsks(emptyList(), listOf(missing[0])), pullAsks(missing, null, me))
        assertEquals(PullAsks(emptyList(), listOf(missing[0])), pullAsks(missing, emptyList(), me))
        assertEquals("Household sharing isn't available yet", HOUSEHOLD_UNAVAILABLE)
        assertEquals("Household sharing isn't available yet", SocialException("unavailable", HOUSEHOLD_UNAVAILABLE).let { householdError(it) })
        assertEquals("Those cards aren't on the shelf any more.", householdError(SocialException("not_on_shelf", "x")))
    }

    @Test
    fun aShelfLoanIdIsHhAnd20LettersOrDigits() {
        assertTrue(Regex("^hh-[A-Za-z0-9]{20}$").matches(shelfLoanId()))
        assertTrue(Regex("^hh-[A-Za-z0-9]{20}$").matches(shelfLoanId(Random(7))))
    }

    @Test
    fun theServersAnswersAreRead() {
        val mine = parseMyHouseholds(
            """{"households":[{"id":"h1","name":"Shared shelf","createdAt":5,
              "members":[{"profile":{"user_id":"alex","username":"alex","display_name":"Alex","avatar_path":null},"status":"member"},
                         {"profile":null,"status":"member"}],
              "places":[{"placeId":"red","name":"Red box","kind":"WEIRD","sharedBy":"alex","users":["alex"]}]}],
              "invites":[{"id":"h2","name":"Flat","invitedBy":{"user_id":"sam","username":"sam","display_name":"Sam","avatar_path":null},"members":2,"at":9}]}"""
        )
        assertEquals(1, mine.households[0].members.size)
        assertEquals("OTHER", mine.households[0].places[0].kind)
        assertEquals(listOf("alex"), mine.households[0].places[0].users)
        assertEquals("Sam", mine.invites[0].invitedBy?.displayName)
        val cards = parseHouseholdCards(
            """{"copies":[{"userId":"alex","placeId":"red","scryfallId":"s1","name":"Sol Ring","imageUrl":null,"qty":2,"foil":true}],
              "loans":[{"id":"l1","clientId":"hh-1","lender":"alex","borrower":"me","cards":[{"name":"Sol Ring","qty":1,"printingId":null}],"note":null,"lentAt":3}]}"""
        )
        assertEquals(ShelfCopy("alex", "red", "s1", "Sol Ring", null, 2, true), cards.copies[0])
        assertEquals(ServerCard("Sol Ring", 1, ""), cards.loans[0].cards[0])
        assertEquals(MyHouseholds(), parseMyHouseholds("null"))
        assertEquals(HouseholdCards(), parseHouseholdCards(""))
    }
}
