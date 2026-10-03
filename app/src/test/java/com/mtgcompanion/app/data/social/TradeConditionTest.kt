package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.Collection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** A trade line carries the giver's condition (JSON key "condition"), and the cards arrive in it. */
class TradeConditionTest {

    @Test
    fun theConditionGoesBothWaysAndIsLeftOutWhenNotSet() {
        val cards = listOf(TradeCard("a", "Sol Ring", condition = "LP"), TradeCard("b", "Arcane Signet"))
        val json = tradeCardsJson(cards)
        assertEquals("LP", json.getJSONObject(0).getString("condition"))
        assertFalse(json.getJSONObject(1).has("condition"))
        assertEquals(cards, parseTradeCards(json))
    }

    @Test
    fun cardsATradeBringsInArriveInTheirCondition() {
        val binder = Collection("b", "Binder")
        val result = applyCollectionChanges(listOf(binder), listOf(CollectionChange("b", TradeCard("a", "Sol Ring", condition = "MP"), 1, 0)))
        assertEquals("MP", result.collections.single().entries.single().condition)
        val plain = applyCollectionChanges(listOf(binder), listOf(CollectionChange("b", TradeCard("a", "Sol Ring"), 1, 0)))
        assertNull(plain.collections.single().entries.single().condition)
    }
}
