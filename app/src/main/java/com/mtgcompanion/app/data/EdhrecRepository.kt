package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.NetworkModule
import com.mtgcompanion.app.network.edhrec.EdhrecCardList
import com.mtgcompanion.app.network.edhrec.edhrecPairSlug
import com.mtgcompanion.app.network.edhrec.edhrecSlug
import retrofit2.HttpException

class EdhrecRepository {
    private val api = NetworkModule.edhrecApi

    /**
     * Returns null if this card has no EDHREC commander page (i.e. it isn't a commander). With a
     * [partnerName], asks for the pair's own page first and falls back to [cardName]'s alone when
     * EDHREC has none (or it can't be read).
     */
    suspend fun getRecommendationsForCommander(cardName: String, partnerName: String? = null): List<EdhrecCardList>? {
        if (partnerName != null) {
            val pair = try {
                commanderPage(edhrecPairSlug(cardName, partnerName))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
            if (!pair.isNullOrEmpty()) return pair
        }
        return commanderPage(edhrecSlug(cardName))
    }

    private suspend fun commanderPage(slug: String): List<EdhrecCardList>? =
        try {
            api.getCommanderPage(slug).container?.jsonDict?.cardlists
        } catch (e: HttpException) {
            if (e.code() == 404) null else throw e
        }

    /**
     * EDHREC's per-card page: which commanders run this card and what's played alongside it.
     * Returns null if EDHREC has no page for the card (e.g. unplayed/too new).
     */
    suspend fun getCardPage(cardName: String): List<EdhrecCardList>? {
        val slug = edhrecSlug(cardName)
        return try {
            api.getCardPage(slug).container?.jsonDict?.cardlists
        } catch (e: HttpException) {
            if (e.code() == 404) null else throw e
        }
    }
}
