package com.mtgcompanion.app.data.social

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one-time community rules before a first post. The web app has the same checks — tests/social/communityRules.test.ts. */
class CommunityRulesTest {

    private val v = CommunityRulesPolicy.VERSION

    @Test
    fun agreementFromTheDeviceOrTheAccountCounts() {
        assertTrue(CommunityRulesPolicy.needsAgreement(0, null))
        assertTrue(CommunityRulesPolicy.needsAgreement(0, 0))
        assertFalse(CommunityRulesPolicy.needsAgreement(v, null))
        assertFalse(CommunityRulesPolicy.needsAgreement(0, v))
        // Rules that changed since (a lower version) are asked again.
        assertTrue(CommunityRulesPolicy.needsAgreement(v - 1, v - 1))
    }

    @Test
    fun theAccountsVersionComesFromUserMetadata() {
        assertEquals(0, CommunityRulesPolicy.versionFromUser(null))
        assertEquals(0, CommunityRulesPolicy.versionFromUser(JSONObject()))
        assertEquals(0, CommunityRulesPolicy.versionFromUser(JSONObject("""{"user_metadata":{}}""")))
        assertEquals(1, CommunityRulesPolicy.versionFromUser(JSONObject("""{"user_metadata":{"community_rules_version":1}}""")))
        assertEquals(2, CommunityRulesPolicy.versionFromUser(JSONObject("""{"user_metadata":{"community_rules_version":"2"}}""")))
        assertEquals(0, CommunityRulesPolicy.versionFromUser(JSONObject("""{"user_metadata":{"community_rules_version":"yes"}}""")))
        assertEquals(0, CommunityRulesPolicy.versionFromUser(JSONObject("""{"user_metadata":{"community_rules_version":-3}}""")))
    }

    @Test
    fun aFirstPostWaitsForAgreeThenGoesAhead() {
        val saved = mutableListOf<Int>()
        val store = CommunityRulesStore(0) { saved += it }
        var posted = 0
        store.require { posted++ }
        assertEquals(0, posted)
        assertNotNull(store.pending.value)

        store.agree()?.invoke()
        assertEquals(1, posted)
        assertNull(store.pending.value)
        assertEquals(listOf(v), saved)

        // From then on, posting goes straight through without the sheet.
        store.require { posted++ }
        assertEquals(2, posted)
        assertNull(store.pending.value)
    }

    @Test
    fun notNowPostsNothing() {
        val store = CommunityRulesStore(0) { }
        var posted = false
        store.require { posted = true }
        store.dismiss()
        assertFalse(posted)
        assertNull(store.pending.value)
        assertFalse(store.agreed)
    }

    @Test
    fun agreedOnAnotherDeviceIsntAskedAgain() {
        val saved = mutableListOf<Int>()
        val store = CommunityRulesStore(0) { saved += it }
        store.setAccountVersion(v)
        assertTrue(store.agreed)
        // Remembered on this device too, so signing out later doesn't ask again.
        assertEquals(listOf(v), saved)
        store.setAccountVersion(null)
        assertTrue(store.agreed)
    }

    @Test
    fun alreadyAgreedOnThisDevice() {
        val store = CommunityRulesStore(v) { error("nothing new to save") }
        var posted = false
        store.require { posted = true }
        assertTrue(posted)
        // Settings can still open the rules to read.
        store.show()
        assertNotNull(store.pending.value)
        store.dismiss()
    }
}
