package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The way back to a seat — mirrors the web app's tests/lifecounter/seat.test.ts. */
class SeatMemoryTest {

    @Test
    fun `a seat survives being written and read back`() {
        val seat = SeatMemory("match-1", 3, System.currentTimeMillis())
        val read = SeatMemory.parse(seat.store())
        assertEquals("match-1", read?.matchId)
        assertEquals(3, read?.seat)
    }

    @Test
    fun `yesterday's table is not still offered`() {
        val old = SeatMemory("match-1", 3, System.currentTimeMillis() - REMOTE_SEAT_STALE_MS - 1)
        assertNull(SeatMemory.parse(old.store()))
    }

    @Test
    fun `nonsense is ignored rather than thrown`() {
        assertNull(SeatMemory.parse(null))
        assertNull(SeatMemory.parse(""))
        assertNull(SeatMemory.parse("match-1"))
        assertNull(SeatMemory.parse("match-1/notaseat/123"))
        assertNull(SeatMemory.parse("/2/${System.currentTimeMillis()}"))
    }
}
