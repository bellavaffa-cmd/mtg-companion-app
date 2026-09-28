package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.edhrec.edhrecSlug
import org.junit.Assert.assertEquals
import org.junit.Test

/** EDHREC's page names for a card. */
class EdhrecSlugTest {

    @Test
    fun punctuationGoesAndSpacesBecomeHyphens() {
        assertEquals("yuriko-the-tigers-shadow", edhrecSlug("Yuriko, the Tiger's Shadow"))
    }

    @Test
    fun aTwoFacedCommanderIsFoundByItsFrontFace() {
        assertEquals("katilda-dawnhart-martyr", edhrecSlug("Katilda, Dawnhart Martyr // Katilda's Rising Dawn"))
    }
}
