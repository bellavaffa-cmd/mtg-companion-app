package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Settings › Data and speed says each figure the same way as the web app (tests/settings/dataAndSpeed.test.ts). */
class DataAndSpeedTest {
    @Test fun `times read in seconds, short ones to the hundredth`() {
        assertEquals("0.4 s", secondsLabel(400))
        assertEquals("0.04 s", secondsLabel(42))
        assertEquals("0.01 s", secondsLabel(3))
        assertEquals("1.3 s", secondsLabel(1250))
        assertEquals("Not opened yet", openLabel(null))
        assertEquals("0.4 s", openLabel(DataAndSpeed.OpenTiming(380, 18_000, 1L)))
    }

    @Test fun `card data is counted against the printings owned, and the last sync says how long ago`() {
        assertEquals("18,402 of 18,402", offlineLabel(18_402, 18_402))
        val now = 1_790_000_000_000L
        assertEquals("2 min ago", lastSyncedLabel(true, now - 2 * 60_000, now))
        assertEquals("Just now", lastSyncedLabel(true, now - 5_000, now))
        assertEquals("Not yet", lastSyncedLabel(true, 0L, now))
        assertEquals("Not signed in", lastSyncedLabel(false, now, now))
    }
}
