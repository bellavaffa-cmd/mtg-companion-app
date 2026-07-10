package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.NetworkModule
import com.mtgcompanion.app.network.spellbook.Variant

class ComboRepository {
    private val api = NetworkModule.spellbookApi

    suspend fun findCombosUsing(cardName: String): List<Variant> {
        val query = "card=\"$cardName\""
        return api.findCombosForCard(query).results
    }
}
