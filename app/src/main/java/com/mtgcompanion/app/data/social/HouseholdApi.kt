package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.ServerCard
import com.mtgcompanion.app.data.StoragePlace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sharing storage at home: calls to the server functions in
 * supabase/migrations/20261006050000_households.sql. The rules are Household.kt; the web app's twin
 * is src/social/household.ts.
 *
 * Until that migration is applied the functions aren't there: [check] says so, and the screens say
 * "Household sharing isn't available yet" (pull lists simply show no "ask" cards). One per
 * [SocialRepository]; forgets the answer when the account changes.
 */
class HouseholdApi(private val api: SocialApi) {
    private val _available = MutableStateFlow<Boolean?>(null)
    /** Null until asked (or while it can't be told — offline). */
    val available: StateFlow<Boolean?> = _available.asStateFlow()

    fun reset() { _available.value = null }

    /** Asks the server once whether the household functions are there. Throws when it can't be told (offline). */
    suspend fun check(): Boolean {
        _available.value?.let { return it }
        return try {
            val ok = (api.call("household_version").trim().toIntOrNull() ?: 0) >= 1
            _available.value = ok
            ok
        } catch (e: SocialException) {
            if (e.code == "unavailable") { _available.value = false; false } else throw e
        }
    }

    suspend fun mine(): MyHouseholds = parseMyHouseholds(api.call("my_households"))

    suspend fun create(name: String): String = api.call("create_household", JSONObject().put("p_name", name)).trim().trim('"')

    /** "invited", or "already" when they're in it or asked. */
    suspend fun invite(household: String, friend: String): String =
        api.call("invite_to_household", JSONObject().put("p_household", household).put("p_friend", friend)).trim().trim('"')

    suspend fun respond(household: String, accept: Boolean) {
        api.call("respond_household", JSONObject().put("p_household", household).put("p_accept", accept))
    }

    suspend fun leave(household: String) { api.call("leave_household", JSONObject().put("p_household", household)) }

    suspend fun cancelInvite(household: String, user: String) {
        api.call("cancel_household_invite", JSONObject().put("p_household", household).put("p_user", user))
    }

    suspend fun sharePlace(household: String, place: StoragePlace) {
        api.call(
            "share_household_place",
            JSONObject().put("p_household", household).put("p_place_id", place.id).put("p_name", place.name).put("p_kind", place.placeKind.name)
        )
    }

    suspend fun joinPlace(household: String, placeId: String) {
        api.call("join_household_place", JSONObject().put("p_household", household).put("p_place_id", placeId))
    }

    suspend fun unsharePlace(household: String, placeId: String) {
        api.call("unshare_household_place", JSONObject().put("p_household", household).put("p_place_id", placeId))
    }

    suspend fun cards(household: String): HouseholdCards = parseHouseholdCards(api.call("household_cards", JSONObject().put("p_household", household)))

    /** Records the cards as borrowed from [from] (a loan from them, under Loans → Borrowed). */
    suspend fun borrow(household: String, from: String, clientId: String, cards: List<ServerCard>, note: String? = null): String =
        api.call(
            "household_borrow",
            JSONObject().put("p_household", household).put("p_from", from).put("p_client_id", clientId)
                .put("p_cards", JSONArray().apply { cards.forEach { put(JSONObject().put("name", it.name).put("qty", it.qty).put("printingId", it.printingId)) } })
                .put("p_note", note ?: JSONObject.NULL)
        ).trim().trim('"')

    suspend fun loanReturned(loanId: String) { api.call("household_loan_returned", JSONObject().put("p_loan", loanId)) }

    /** Every household's shelves, for a pull list: null when there are none, or households aren't there (yet). Never throws. */
    suspend fun shelves(): List<Shelf>? = runCatching {
        if (!check()) return null
        val hs = mine().households
        if (hs.isEmpty()) return null
        hs.mapNotNull { h -> runCatching { Shelf(h, cards(h.id)) }.getOrNull() }
    }.getOrNull()
}
