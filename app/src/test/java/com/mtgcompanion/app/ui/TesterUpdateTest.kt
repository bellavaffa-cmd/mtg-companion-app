package com.mtgcompanion.app.ui

import com.mtgcompanion.app.update.UpdateManager.Companion.newestTesterBuild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which tester build the tester app offers to update to. */
class TesterUpdateTest {

    private val tags = listOf("v3.5.14", "tester-3", "tester-1", "v3.5.13", "tester-2")

    @Test
    fun theHighestTesterBuildAboveThisOneIsOffered() {
        assertEquals(3, newestTesterBuild(tags, current = 1))
        assertEquals(3, newestTesterBuild(tags, current = 2))
    }

    @Test
    fun nothingIsOfferedOnTheNewestBuild() {
        assertNull(newestTesterBuild(tags, current = 3))
        assertNull(newestTesterBuild(tags, current = 9))
    }

    @Test
    fun realReleasesAndOddTagsAreNotTesterBuilds() {
        assertNull(newestTesterBuild(listOf("v3.5.15", "v4.0.0", "tester-", "tester-x", "tester-2-fix"), current = 0))
    }
}
