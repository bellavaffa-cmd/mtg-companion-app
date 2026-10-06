package com.mtgcompanion.app.ui

import com.mtgcompanion.app.update.TesterBuildId
import com.mtgcompanion.app.update.UpdateManager.Companion.newestTesterTag
import com.mtgcompanion.app.update.UpdateManager.Companion.parseTesterTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which tester build the tester app offers to update to. */
class TesterUpdateTest {

    private val old = listOf("v3.5.14", "tester-3", "tester-1", "v3.5.13", "tester-2")

    @Test
    fun theHighestOldTesterBuildAboveThisOneIsOffered() {
        assertEquals("tester-3", newestTesterTag(old, TesterBuildId(0, 1)))
        assertEquals("tester-3", newestTesterTag(old, TesterBuildId(0, 2)))
    }

    @Test
    fun nothingIsOfferedOnTheNewestBuild() {
        assertNull(newestTesterTag(old, TesterBuildId(0, 3)))
        assertNull(newestTesterTag(old, TesterBuildId(30600, 1)))
    }

    @Test
    fun buildsAreCountedAgainAfterEachRelease() {
        val tags = old + listOf("tester-25", "v3.6.0", "tester-3.6.0-1", "tester-3.6.0-2", "tester-3.6.1-1")
        // Build 1 after 3.6.0 comes after build 25 before it, and 3.6.1's first after 3.6.0's second.
        assertEquals("tester-3.6.1-1", newestTesterTag(tags, TesterBuildId(30600, 1)))
        assertEquals("tester-3.6.0-2", newestTesterTag(tags - "tester-3.6.1-1", TesterBuildId(30600, 1)))
        assertNull(newestTesterTag(tags, TesterBuildId(30601, 1)))
        assertEquals(TesterBuildId(31000, 12, "3.10.0"), parseTesterTag("tester-3.10.0-12"))
        assertEquals("3.6.0 tester build 2", parseTesterTag("tester-3.6.0-2")?.label)
    }

    @Test
    fun realReleasesAndOddTagsAreNotTesterBuilds() {
        assertNull(newestTesterTag(listOf("v3.5.15", "v4.0.0", "tester-", "tester-x", "tester-2-fix", "tester-3.6-1"), TesterBuildId(0, 0)))
    }
}
