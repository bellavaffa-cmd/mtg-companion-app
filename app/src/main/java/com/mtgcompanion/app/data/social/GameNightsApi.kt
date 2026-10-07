package com.mtgcompanion.app.data.social

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

// Game night invites and pod chat: calls to the server functions in
// supabase/migrations/20261006070000_game_nights_chat.sql. The rules behind the screens are in
// GameNights.kt and PodChat.kt. The web app's twin is src/social/nights.ts.
//
// Until that migration is applied the functions aren't there: [GameNightsApi.available] says so,
// and the screens say "Game night invites aren't available yet" / "Pod chat isn't available yet".

const val NIGHTS_UNAVAILABLE = "Game night invites aren't available yet."
const val CHAT_UNAVAILABLE = "Pod chat isn't available yet."

/** One per [SocialRepository]; forgets what it knows when the account changes. */
class GameNightsApi(private val api: SocialApi) {
    private val _available = MutableStateFlow<Boolean?>(null)
    /** Null until asked (or while it can't be told — offline). */
    val available: StateFlow<Boolean?> = _available.asStateFlow()

    private val _nights = MutableStateFlow<List<NightInvite>?>(null)
    /** The user's nights still to come, as last fetched (null before the first fetch). */
    val nights: StateFlow<List<NightInvite>?> = _nights.asStateFlow()

    fun reset() {
        _available.value = null
        _nights.value = null
    }

    /** Asks the server once whether these functions are there. */
    suspend fun check(): Boolean {
        _available.value?.let { return it }
        return try {
            val ok = (api.call("game_nights_version").trim().toIntOrNull() ?: 0) >= 1
            _available.value = ok
            ok
        } catch (e: SocialException) {
            if (e.code == "unavailable") _available.value = false
            false
        }
    }

    // ---- Game nights ----

    /** The user's nights still to come (in [podId] only, when given), soonest first. All of them are kept in [nights]. */
    suspend fun nights(podId: String? = null): List<NightInvite> {
        val list = parseNightInvites(api.call("game_nights", JSONObject().put("p_pod", podId ?: JSONObject.NULL)))
        if (podId == null) _nights.value = list
        return list
    }

    /** One night the user is invited to, or null. */
    suspend fun night(id: String): NightInvite? = parseNightInvite(api.call("game_night", JSONObject().put("p_night", id)))

    /** Plans a night in [podId] ([id] null) or changes one; answers it. */
    suspend fun save(id: String?, podId: String, startsAt: Long, place: String, note: String, guests: List<String>): NightInvite {
        val text = api.call(
            "save_game_night",
            JSONObject()
                .put("p_night", id ?: JSONObject.NULL)
                .put("p_pod", podId)
                .put("p_starts_at", Instant.ofEpochMilli(startsAt).toString())
                .put("p_tz", ZoneId.systemDefault().id)
                .put("p_place", place.trim())
                .put("p_note", note.trim().ifEmpty { null } ?: JSONObject.NULL)
                .put("p_guests", JSONArray(guests))
        )
        return parseNightInvite(text) ?: throw SocialException("bad_night", "Something went wrong.")
    }

    suspend fun cancel(id: String) { api.call("cancel_game_night", JSONObject().put("p_night", id)) }

    /** The user's answer, with the deck they'll bring; answers the night as it is now. */
    suspend fun rsvp(id: String, answer: RsvpAnswer, deck: String?): NightInvite? = parseNightInvite(
        api.call(
            "rsvp_game_night",
            JSONObject().put("p_night", id).put("p_answer", answer.wire).put("p_deck", deck?.trim()?.ifEmpty { null } ?: JSONObject.NULL)
        )
    )

    // ---- Pod chat ----

    suspend fun chats(): List<PodChat> = parsePodChats(api.call("pod_chats"))

    /** A page of [podId]'s chat, oldest first: the messages before [before] (null: the newest). */
    suspend fun messages(podId: String, before: Long? = null, limit: Int = 50): List<PodMessage> =
        parsePodMessages(api.call("pod_messages", JSONObject().put("p_pod", podId).put("p_before", before ?: JSONObject.NULL).put("p_limit", limit)))

    suspend fun send(podId: String, body: String): PodMessage? = parsePodMessage(
        api.call("send_pod_message", JSONObject().put("p_pod", podId).put("p_body", body).put("p_kind", "text").put("p_ref", JSONObject.NULL)).trim()
            .takeIf { it.startsWith("{") }?.let(::JSONObject)
    )

    /** Shares a game night, a deck or a card into the chat, with an optional caption. */
    suspend fun share(podId: String, ref: PodMessageRef, caption: String = ""): PodMessage? = parsePodMessage(
        api.call("send_pod_message", JSONObject().put("p_pod", podId).put("p_body", caption).put("p_kind", "share").put("p_ref", podRefJson(ref))).trim()
            .takeIf { it.startsWith("{") }?.let(::JSONObject)
    )

    suspend fun markRead(podId: String) { api.call("mark_pod_read", JSONObject().put("p_pod", podId)) }

    suspend fun unread(): Int = api.call("unread_pod_messages").trim().toIntOrNull() ?: 0
}
