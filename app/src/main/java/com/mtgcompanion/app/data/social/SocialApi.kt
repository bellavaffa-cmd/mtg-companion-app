package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.BuildConfig
import com.mtgcompanion.app.data.supabase.JSON_MEDIA
import com.mtgcompanion.app.data.supabase.SupabaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID

/** What the server said no to, in words for the screen. */
class SocialException(val code: String, message: String) : Exception(message)

/**
 * Calls to the social server functions. Every call goes through a function that checks who is
 * asking (auth.uid()); nothing here reads a table directly. Mirrors the web app's social/api.ts.
 */
class SocialApi(private val auth: SupabaseAuth) {

    private suspend fun call(fn: String, args: JSONObject = JSONObject(), signedIn: Boolean = true): String = withContext(Dispatchers.IO) {
        val token = auth.accessToken()
        if (signedIn && token == null) throw SocialException("not_signed_in", MESSAGES.getValue("not_signed_in"))
        val request = Request.Builder()
            .url(BuildConfig.SUPABASE_URL + "/rest/v1/rpc/$fn")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .post(args.toString().toRequestBody(JSON_MEDIA))
            .build()
        val response = try {
            auth.http.newCall(request).execute()
        } catch (e: IOException) {
            throw SocialException("offline", "You're offline — try again when you're connected.")
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val code = runCatching { JSONObject(text).optString("message") }.getOrNull().orEmpty()
                throw SocialException(code, MESSAGES[code] ?: "Something went wrong (HTTP ${it.code}).")
            }
            text
        }
    }

    private fun obj(text: String): JSONObject? = text.trim().takeIf { it.isNotEmpty() && it != "null" }?.let(::JSONObject)

    // ---- Profile ----

    /** [avatarPath]: null keeps the picture, "" removes it. */
    suspend fun saveProfile(username: String, displayName: String, avatarPath: String?): Profile =
        parseProfile(JSONObject(call("save_profile", JSONObject().put("p_username", username).put("p_display_name", displayName).put("p_avatar_path", avatarPath ?: JSONObject.NULL))))

    suspend fun usernameAvailable(username: String): Boolean = call("username_available", JSONObject().put("p_username", username)).trim() == "true"

    /** Uploads a picture ([bytes] of [mimeType]) to the user's folder and answers its path. */
    suspend fun uploadAvatar(userId: String, bytes: ByteArray, mimeType: String): String = withContext(Dispatchers.IO) {
        val token = auth.accessToken() ?: throw SocialException("not_signed_in", MESSAGES.getValue("not_signed_in"))
        val ext = when (mimeType) { "image/gif" -> "gif"; "image/webp" -> "webp"; "image/png" -> "png"; else -> "jpg" }
        val path = "$userId/${UUID.randomUUID()}.$ext"
        val request = Request.Builder()
            .url(BuildConfig.SUPABASE_URL + "/storage/v1/object/avatars/$path")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .header("cache-control", "max-age=31536000")
            .post(bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val response = try {
            auth.http.newCall(request).execute()
        } catch (e: IOException) {
            throw SocialException("offline", "You're offline — try again when you're connected.")
        }
        response.use {
            if (!it.isSuccessful) {
                throw SocialException("upload_failed", if (it.code == 413) "That picture is too big (2 MB at most)." else "The picture didn't upload (HTTP ${it.code}).")
            }
        }
        path
    }

    /** Deletes an old picture; failing just leaves an unused file behind. */
    suspend fun deleteAvatar(path: String) = withContext(Dispatchers.IO) {
        val token = runCatching { auth.accessToken() }.getOrNull() ?: return@withContext
        val request = Request.Builder()
            .url(BuildConfig.SUPABASE_URL + "/storage/v1/object/avatars")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .delete(JSONObject().put("prefixes", JSONArray().put(path)).toString().toRequestBody(JSON_MEDIA))
            .build()
        runCatching { auth.http.newCall(request).execute().close() }
    }

    // ---- Overview ----

    suspend fun overview(): Overview = parseOverview(JSONObject(call("social_overview")))

    suspend fun inbox(): Inbox = JSONObject(call("social_inbox")).let { Inbox(it.optInt("friend_requests"), it.optInt("trades")) }

    // ---- Friends ----

    /** "requested", "accepted" (they'd asked the user already) or "already". */
    suspend fun requestFriend(username: String): String =
        call("request_friend", JSONObject().put("p_username", username)).trim().trim('"')

    suspend fun respondFriend(userId: String, accept: Boolean) { call("respond_friend", JSONObject().put("p_user", userId).put("p_accept", accept)) }

    suspend fun removeFriend(userId: String) { call("remove_friend", JSONObject().put("p_user", userId)) }

    // ---- Pods ----

    suspend fun savePod(podId: String?, name: String, members: List<String>): String =
        call("save_pod", JSONObject().put("p_pod", podId ?: JSONObject.NULL).put("p_name", name).put("p_members", JSONArray(members))).trim().trim('"')

    suspend fun leavePod(podId: String) { call("leave_pod", JSONObject().put("p_pod", podId)) }

    // ---- Sharing ----

    suspend fun setShare(kind: ShareKind, itemId: String, allFriends: Boolean, podIds: List<String>, link: Boolean): Share? =
        obj(call("set_library_share", JSONObject().put("p_kind", kind.wire).put("p_item_id", itemId).put("p_all_friends", allFriends).put("p_pod_ids", JSONArray(podIds)).put("p_link", link)))
            ?.let(::parseShare)

    suspend fun sharedItem(owner: String, kind: ShareKind, itemId: String): SharedItem? =
        obj(call("get_shared_item", JSONObject().put("p_owner", owner).put("p_kind", kind.wire).put("p_item_id", itemId)))?.let(::parseSharedItem)

    suspend fun sharedByLink(token: String): SharedItem? =
        obj(call("get_shared_by_link", JSONObject().put("p_token", token), signedIn = false))?.let(::parseSharedItem)

    private fun parseSharedItem(o: JSONObject) = SharedItem(parseProfile(o.getJSONObject("owner")), ShareKind.of(o.getString("kind")), o.get("data").toString())

    // ---- Life counter seats ----

    suspend fun startMatch(seats: Int): Match = JSONObject(call("start_match", JSONObject().put("p_seats", seats))).let { Match(it.getString("id"), it.getString("code")) }

    /** Sits the user at [seat]; answers the host's profile. */
    suspend fun joinMatch(code: String, seat: Int): Pair<String, Profile> =
        JSONObject(call("join_match", JSONObject().put("p_code", code).put("p_seat", seat))).let { it.getString("match_id") to parseProfile(it.getJSONObject("host")) }

    suspend fun matchSeats(matchId: String): List<MatchSeat> {
        val a = JSONArray(call("match_seats", JSONObject().put("p_match", matchId)))
        return (0 until a.length()).map { i -> a.getJSONObject(i).let { MatchSeat(it.getInt("seat"), parseProfile(it.getJSONObject("profile"))) } }
    }

    suspend fun clearMatchSeat(matchId: String, seat: Int) { call("clear_match_seat", JSONObject().put("p_match", matchId).put("p_seat", seat)) }

    suspend fun endMatch(matchId: String) { call("end_match", JSONObject().put("p_match", matchId)) }

    // ---- Notifications ----

    /** This device gets the signed-in account's notifications at [token] (its FCM registration token). */
    suspend fun registerPushToken(token: String) {
        call("register_push_token", JSONObject().put("p_platform", "fcm").put("p_token", token).put("p_subscription", JSONObject.NULL))
    }

    suspend fun unregisterPushToken(token: String) { call("unregister_push_token", JSONObject().put("p_token", token)) }

    /** Which kinds of notification the account gets, on every device: friend requests, trades. */
    suspend fun notificationPrefs(): Pair<Boolean, Boolean> =
        JSONObject(call("notification_prefs")).let { it.optBoolean("friends", true) to it.optBoolean("trades", true) }

    suspend fun setNotificationPrefs(friends: Boolean, trades: Boolean): Pair<Boolean, Boolean> =
        JSONObject(call("set_notification_prefs", JSONObject().put("p_friends", friends).put("p_trades", trades)))
            .let { it.optBoolean("friends", true) to it.optBoolean("trades", true) }

    // ---- Trades ----

    suspend fun proposeTrade(to: String, want: List<TradeCard>, give: List<TradeCard>, message: String, replyTo: String?): String =
        call(
            "propose_trade",
            JSONObject().put("p_to", to).put("p_want", tradeCardsJson(want)).put("p_give", tradeCardsJson(give))
                .put("p_message", message.ifBlank { null } ?: JSONObject.NULL).put("p_reply_to", replyTo ?: JSONObject.NULL)
        ).trim().trim('"')

    /** [action]: "accept" / "decline" (the recipient) or "cancel" (the sender). */
    suspend fun respondTrade(tradeId: String, action: String, reply: String = "") {
        call("respond_trade", JSONObject().put("p_trade", tradeId).put("p_action", action).put("p_reply", reply.ifBlank { null } ?: JSONObject.NULL))
    }

    suspend fun markTradeApplied(tradeId: String) { call("mark_trade_applied", JSONObject().put("p_trade", tradeId)) }

    companion object {
        /** Where a profile picture is served from (public, but only people who can see the profile learn its name). */
        fun avatarUrl(path: String?): String? =
            path?.let { BuildConfig.SUPABASE_URL + "/storage/v1/object/public/avatars/" + it.split('/').joinToString("/") { part -> URLEncoder.encode(part, "UTF-8") } }

        /** Links for QR codes and sharing always point at the live web app, which the Android app also understands. */
        const val PUBLIC_APP_URL = "https://bellavaffa-cmd.github.io/mtg-companion-web/"
        fun friendLink(username: String) = PUBLIC_APP_URL + "add/" + URLEncoder.encode(username, "UTF-8")
        fun seatLink(code: String, seat: Int) = PUBLIC_APP_URL + "join/$code/$seat"
        fun shareLink(token: String) = PUBLIC_APP_URL + "s/$token"

        private val MESSAGES = mapOf(
            "not_signed_in" to "Sign in first.",
            "no_profile" to "Make your profile first.",
            "bad_username" to "Usernames are 3–20 letters, numbers or _.",
            "bad_display_name" to "Your name needs 1–40 characters.",
            "username_taken" to "That username is taken.",
            "bad_avatar" to "That picture couldn't be used.",
            "no_such_user" to "Nobody has that username.",
            "self" to "That's you!",
            "too_many_requests" to "You've asked a lot of people already — wait for some answers first.",
            "not_a_friend" to "Only friends can be added.",
            "not_yours" to "Only the pod's owner can change it.",
            "too_many_members" to "A pod holds up to 50 people.",
            "too_many_pods" to "You can have up to 30 pods.",
            "bad_name" to "Give it a name (up to 40 characters).",
            "no_such_item" to "That deck or binder hasn't synced yet — check your connection and try again.",
            "match_over" to "That table has ended. Ask for a new code.",
            "bad_seat" to "That seat isn't at this table.",
            "seat_taken" to "Someone is already in that seat.",
            "too_many_matches" to "Too many tables started — wait a little.",
            "too_many_cards" to "A trade holds up to 100 different cards.",
            "bad_card" to "One of the cards in this trade is not valid.",
            "no_cards" to "Pick at least one card.",
            "message_too_long" to "Keep the message under 500 characters.",
            "too_many_trades" to "You have a lot of open trades — wait for some answers first.",
            "trade_closed" to "This trade has already been answered."
        )
    }
}

/** What a scanned QR code (or a pasted link) asks for. */
sealed interface AppLink {
    data class AddFriend(val username: String) : AppLink
    data class JoinSeat(val code: String, val seat: Int) : AppLink
    data class SharedLink(val token: String) : AppLink

    companion object {
        /** Reads one of the web app's links (any host serving it, so a dev build's links work too). */
        fun parse(text: String): AppLink? {
            val path = text.trim().substringAfter("/mtg-companion-web/", missingDelimiterValue = "").substringBefore('?').substringBefore('#').trimEnd('/')
            val parts = path.split('/').filter { it.isNotEmpty() }
            return when {
                parts.size == 2 && parts[0] == "add" && Regex("[a-zA-Z0-9_]{3,20}").matches(parts[1]) -> AddFriend(parts[1].lowercase())
                parts.size == 3 && parts[0] == "join" && Regex("[0-9a-f]{16}").matches(parts[1]) -> parts[2].toIntOrNull()?.let { JoinSeat(parts[1], it) }
                parts.size == 2 && parts[0] == "s" && Regex("[0-9a-f]{32}").matches(parts[1]) -> SharedLink(parts[1])
                else -> null
            }
        }
    }
}
