package com.mtgcompanion.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The in-app GitHub updater stays off wherever Google Play does the updating. */
class InstallSourceTest {

    @Test
    fun theGithubBuildUpdatesItselfWhenSideloaded() {
        assertTrue(InstallSource.selfUpdateAllowed(playBuild = false, installer = null))
        assertTrue(InstallSource.selfUpdateAllowed(playBuild = false, installer = "com.google.android.packageinstaller"))
        assertTrue(InstallSource.selfUpdateAllowed(playBuild = false, installer = "com.android.chrome"))
    }

    @Test
    fun thePlayBuildNeverUpdatesItself() {
        assertFalse(InstallSource.selfUpdateAllowed(playBuild = true, installer = null))
        assertFalse(InstallSource.selfUpdateAllowed(playBuild = true, installer = "com.google.android.packageinstaller"))
        assertFalse(InstallSource.selfUpdateAllowed(playBuild = true, installer = InstallSource.PLAY_STORE))
    }

    @Test
    fun aCopyPlayInstalledNeverUpdatesItself() {
        assertFalse(InstallSource.selfUpdateAllowed(playBuild = false, installer = "com.android.vending"))
    }
}
