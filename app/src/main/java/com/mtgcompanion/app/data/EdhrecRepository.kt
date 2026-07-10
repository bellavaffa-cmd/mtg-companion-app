package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.NetworkModule
import com.mtgcompanion.app.network.edhrec.EdhrecCardList
import com.mtgcompanion.app.network.edhrec.edhrecSlug
import retrofit2.HttpException

class EdhrecRepository {
    private val api = NetworkModule.edhrecApi

    /** Returns null if this card has no EDHREC commander page (i.e. it isn't a commander). */
    suspend fun getRecommendationsForCommander(cardName: String): List<EdhrecCardList>? {
        val slug = edhrecSlug(cardName)
        return try {
            api.getCommanderPage(slug).container?.jsonDict?.cardlists
        } catch (e: HttpException) {
            if (e.code() == 404) null else throw e
        }
    }
}
