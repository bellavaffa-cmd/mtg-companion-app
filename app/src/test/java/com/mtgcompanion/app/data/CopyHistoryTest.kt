package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A copy's history, kept on the device for a year — the same on both apps. The web app has the same
 * checks — see MtgCompanionWeb/tests/collection/copyHistory.test.ts.
 */
class CopyHistoryTest {

    private val now = 1_790_000_000_000L
    private val day = 86_400_000L
    private val ring = MoveCard("Sol Ring", "sol")
    private val red = MoveSpot("red", "Red box")
    private val rares = MoveSpot("rares", "Rares binder")

    private fun words(m: CopyMove) = "${m.title} | ${m.detail ?: ""} | ${m.places.orEmpty().joinToString(",")}"

    @Test
    fun theWordsForEachMove() {
        assertEquals("Added to your collection | from a trade with Priya | ", words(addedMove(now, ring, 1, null, "from a trade with Priya")))
        assertEquals("Added to your collection ×2 | into Red box · Booster box, Duskmourn | red", words(addedMove(now, ring, 2, red, "Booster box, Duskmourn")))
        assertEquals("Put away in Red box | from no place, by scanning | red", words(putAwayMove(now, ring, 1, red, null, "by scanning")))
        assertEquals("Put away in Red box | from Unsorted | red", words(putAwayMove(now, ring, 1, red, MoveSpot("", "Unsorted"))))
        assertEquals("Moved to Rares binder | from Red box | red,rares", words(movedMove(now, ring, 1, red, rares)))
        assertEquals("Taken off its place ×3 | from Red box | red", words(movedMove(now, ring, 3, red, null)))
        assertEquals("Pulled into Atraxa deck | from Red box › Colourless | red", words(pulledMove(now, ring, 1, "Atraxa", MoveSpot("red", "Red box › Colourless"))))
        assertEquals("Put back in Red box | from Atraxa deck | red", words(putBackMove(now, ring, 1, "Atraxa", red)))
        assertEquals("Taken out of Atraxa deck | from Atraxa deck | ", words(putBackMove(now, ring, 1, "Atraxa", null)))
        assertEquals("Lent to Sam | from Atraxa deck · back by next game night | ", words(lentMove(now, ring, 1, "Sam", "Atraxa deck", null, "Back by next game night")))
        assertEquals("Back from Sam | into Red box | red", words(returnedMove(now, ring, 1, "Sam", "Red box", "red")))
        assertEquals("Checked in Red box | where it should be | red", words(checkedMove(now, ring, red, "where it should be")))
    }

    @Test
    fun movesAreKeptAYearAndNoMoreThanTheNewestFewThousand() {
        val old = putAwayMove(now - KEEP_MOVES_MS - 1, ring, 1, red, null)
        val recent = putAwayMove(now - KEEP_MOVES_MS, ring, 1, red, null)
        assertEquals(listOf(recent), pruneMoves(listOf(old, recent), now))
        val many = (0 until MAX_MOVES + 3).map { putAwayMove(now - 1000 + it, ring, 1, red, null) }
        val kept = pruneMoves(many, now)
        assertEquals(MAX_MOVES, kept.size)
        assertEquals(now - 1000 + 3, kept[0].at)
    }

    @Test
    fun aCardsMovesAndAPlacesNewestFirst() {
        val log = appendMoves(
            emptyList(),
            listOf(
                putAwayMove(now - 3 * day, ring, 1, red, null),
                pulledMove(now - 2 * day, ring, 1, "Atraxa", red),
                // Made earlier, written later: it still goes in order.
                addedMove(now - 4 * day, MoveCard("Delver of Secrets // Insectile Aberration"), 1, null)
            ),
            now
        )
        val more = appendMoves(log, listOf(lentMove(now, ring, 1, "Sam", "Atraxa deck", null, null), movedMove(now, MoveCard("Opt"), 1, rares, red)), now)
        assertEquals(listOf("Lent to Sam", "Pulled into Atraxa deck", "Put away in Red box"), movesOfCard(more, "sol ring").map { it.title })
        assertEquals(listOf("Added to your collection"), movesOfCard(more, "Delver of Secrets").map { it.title })
        assertEquals(
            listOf("Opt: Moved to Red box", "Sol Ring: Pulled into Atraxa deck", "Sol Ring: Put away in Red box"),
            movesOfPlace(more, setOf("red")).map { "${it.name}: ${it.title}" }
        )
        assertEquals(1, movesOfPlace(more, setOf("red"), 1).size)
    }

    @Test
    fun theDayOfAMove() {
        assertEquals("Today", moveDay("2026-10-05", "2026-10-05"))
        assertEquals("Yesterday", moveDay("2026-10-04", "2026-10-05"))
        assertEquals("Yesterday", moveDay("2026-09-30", "2026-10-01"))
        assertEquals("12 Sep", moveDay("2026-09-12", "2026-10-05"))
        assertEquals("31 Dec 2025", moveDay("2025-12-31", "2026-01-02"))
    }
}
