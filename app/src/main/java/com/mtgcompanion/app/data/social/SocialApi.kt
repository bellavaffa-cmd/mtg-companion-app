package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.BuildConfig
import com.mtgcompanion.app.data.Loan
import com.mtgcompanion.app.data.ServerCard
import com.mtgcompanion.app.data.PodGame
import com.mtgcompanion.app.data.PodPlayer
import com.mtgcompanion.app.data.parsePodGames
import com.mtgcompanion.app.data.podPlayersJson
import com.mtgcompanion.app.data.LeagueRules
import com.mtgcompanion.app.data.Season
import com.mtgcompanion.app.data.LeagueStanding
import com.mtgcompanion.app.data.parseSeasons
import com.mtgcompanion.app.data.rulesJson
import com.mtgcompanion.app.data.standingsJson
import com.mtgcompanion.app.data.supabase.JSON_MEDIA
import com.mtgcompanion.app.data.supabase.SupabaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
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

    internal suspend fun call(fn: String, args: JSONObject = JSONObject(), signedIn: Boolean = true): String = withContext(Dispatchers.IO) {
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
                // A function that isn't there yet (its migration not applied): see SocialMore.
                val pgCode = runCatching { JSONObject(text).optString("code") }.getOrNull()
                if (isMissingFunction(it.code, pgCode)) throw SocialException("unavailable", MESSAGES.getValue("unavailable"))
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

    /**
     * The best version of the Giphy GIF at [link] that fits the 2 MB picture limit, to upload as the
     * profile picture. Each version's size is asked first, so a huge original isn't downloaded for nothing.
     */
    suspend fun giphyGif(link: String): ByteArray = withContext(Dispatchers.IO) {
        val id = Giphy.id(link)
            ?: throw SocialException("not_giphy", "That isn't a Giphy link. Open the GIF on giphy.com and copy its link (Share → Copy link).")
        for (url in Giphy.renditions(id)) {
            val size = try {
                auth.http.newCall(Request.Builder().url(url).head().build()).execute().use { if (it.isSuccessful) it.header("Content-Length")?.toLongOrNull() ?: 0L else -1L }
            } catch (e: IOException) {
                throw SocialException("offline", "Couldn't reach Giphy — check your connection and try again.")
            }
            if (size in Giphy.NOT_AVAILABLE_SIZES) break
            if (size < 0 || size > MAX_AVATAR_BYTES) continue
            val bytes = try {
                auth.http.newCall(Request.Builder().url(url).get().build()).execute().use { if (it.isSuccessful) it.body?.bytes() else null }
            } catch (e: IOException) {
                throw SocialException("offline", "Couldn't reach Giphy — check your connection and try again.")
            } ?: continue
            if (bytes.size <= MAX_AVATAR_BYTES && bytes.size >= 6 && String(bytes, 0, 3, Charsets.US_ASCII) == "GIF") return@withContext bytes
        }
        throw SocialException("giphy_missing", "Giphy doesn't have that GIF (or it's no longer there).")
    }

    /** Trending GIFs (blank [query]) or a search, 24 at a time, through the giphy Edge Function (which holds the key). */
    suspend fun searchGiphy(query: String, offset: Int): GiphyPage = withContext(Dispatchers.IO) {
        val token = auth.accessToken() ?: throw SocialException("not_signed_in", MESSAGES.getValue("not_signed_in"))
        val url = (BuildConfig.SUPABASE_URL + "/functions/v1/giphy").toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .addQueryParameter("offset", offset.toString())
            .build()
        val request = Request.Builder().url(url)
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .get().build()
        val response = try {
            auth.http.newCall(request).execute()
        } catch (e: IOException) {
            throw SocialException("offline", "You're offline — try again when you're connected.")
        }
        response.use {
            val body = runCatching { JSONObject(it.body?.string().orEmpty()) }.getOrNull() ?: JSONObject()
            if (!it.isSuccessful) throw SocialException("giphy_search", body.optString("error").ifBlank { "GIF search failed (HTTP ${it.code})." })
            val gifs = body.optJSONArray("gifs") ?: JSONArray()
            GiphyPage(
                gifs = (0 until gifs.length()).map { i ->
                    gifs.getJSONObject(i).let { g -> GiphyGif(g.getString("id"), g.optString("title"), g.getString("preview"), g.optInt("width", 100), g.optInt("height", 100)) }
                },
                next = if (body.isNull("next")) null else body.optInt("next")
            )
        }
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

    // ---- A pod's games (supabase/migrations/20261005000000_pod_games.sql) ----

    /**
     * Records a game played in a pod; answers its id. [clientId] makes it safe to send again: the
     * same id updates the game instead of adding a second one.
     */
    suspend fun recordPodGame(
        podId: String,
        clientId: String,
        playedAt: Long,
        format: String,
        turns: Int?,
        minutes: Int?,
        players: List<PodPlayer>
    ): String =
        call(
            "record_pod_game",
            JSONObject().put("p_pod", podId).put("p_client_id", clientId)
                .put("p_played_at", java.time.Instant.ofEpochMilli(playedAt).toString())
                .put("p_format", format)
                .put("p_turns", turns ?: JSONObject.NULL).put("p_minutes", minutes ?: JSONObject.NULL)
                .put("p_players", podPlayersJson(players))
        ).trim().trim('"')

    /** A pod's games, newest first. */
    suspend fun podGames(podId: String, limit: Int = 1000): List<PodGame> =
        parsePodGames(call("pod_games", JSONObject().put("p_pod", podId).put("p_limit", limit)))

    /** Whoever recorded a game, or the pod's owner, deletes it. */
    suspend fun deletePodGame(gameId: String) { call("delete_pod_game", JSONObject().put("p_game", gameId)) }

    // ---- A pod's league seasons (supabase/migrations/20261006060000_pod_seasons.sql) ----
    // Until that migration is applied these throw SocialException("unavailable"), and the screens
    // say "Leagues aren't available yet" (League.kt, LeagueView.kt).

    /** A pod's seasons, newest first. */
    suspend fun podSeasons(podId: String): List<Season> = parseSeasons(call("pod_seasons", JSONObject().put("p_pod", podId)))

    private fun seasonArgs(name: String, startsOn: String, endsOn: String?, maxNights: Int?, rules: LeagueRules): JSONObject =
        JSONObject().put("p_name", name.trim()).put("p_starts_on", startsOn)
            .put("p_ends_on", endsOn ?: JSONObject.NULL).put("p_max_nights", maxNights ?: JSONObject.NULL)
            .put("p_rules", rulesJson(rules))

    /** Starts a season in a pod; answers its id. Days are "YYYY-MM-DD". */
    suspend fun createPodSeason(podId: String, name: String, startsOn: String, endsOn: String?, maxNights: Int?, rules: LeagueRules): String =
        call("create_pod_season", seasonArgs(name, startsOn, endsOn, maxNights, rules).put("p_pod", podId)).trim().trim('"')

    suspend fun updatePodSeason(seasonId: String, name: String, startsOn: String, endsOn: String?, maxNights: Int?, rules: LeagueRules) {
        call("update_pod_season", seasonArgs(name, startsOn, endsOn, maxNights, rules).put("p_season", seasonId))
    }

    /** Ends a season, keeping its final table and champion as they are now. */
    suspend fun endPodSeason(seasonId: String, endedAt: Long, champion: String?, standings: List<LeagueStanding>) {
        call(
            "end_pod_season",
            JSONObject().put("p_season", seasonId)
                .put("p_ended_at", java.time.Instant.ofEpochMilli(endedAt).toString())
                .put("p_champion", champion ?: JSONObject.NULL)
                .put("p_standings", standingsJson(standings))
        )
    }

    // ---- Loans to friends (supabase/migrations/20261006010000_loans.sql) ----
    // The loan itself is the library's (Loans.kt); these only let the friend see it. Every call is
    // best effort: the server may not have them yet, or the user may be offline.

    /** Sends a loan to a friend (again): [cards] are the copies still out. */
    suspend fun upsertLoan(loan: Loan, friendId: String, cards: List<ServerCard>): String =
        call(
            "upsert_loan",
            JSONObject().put("p_client_id", loan.id).put("p_borrower", friendId)
                .put("p_cards", JSONArray().apply { cards.forEach { put(JSONObject().put("name", it.name).put("qty", it.qty).put("printingId", it.printingId)) } })
                .put("p_back_by", loan.backBy ?: JSONObject.NULL)
                .put("p_game_night", loan.gameNight == true)
                .put("p_note", loan.note ?: JSONObject.NULL)
                .put("p_lent_at", java.time.Instant.ofEpochMilli(loan.lentAt).toString())
        ).trim().trim('"')

    /** Every card is back. */
    suspend fun markLoanReturned(clientId: String) { call("mark_loan_returned", JSONObject().put("p_client_id", clientId)) }

    /** What the user has borrowed from friends and not given back. */
    suspend fun myBorrowedLoans(): List<BorrowedLoan> {
        val text = call("my_borrowed_loans").trim()
        if (text.isEmpty() || text == "null") return emptyList()
        val a = JSONArray(text)
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val cards = o.optJSONArray("cards") ?: JSONArray()
            BorrowedLoan(
                id = o.getString("id"),
                clientId = o.optString("clientId"),
                lender = o.optJSONObject("lender")?.let { runCatching { parseProfile(it) }.getOrNull() },
                cards = (0 until cards.length()).map { j ->
                    val c = cards.getJSONObject(j)
                    ServerCard(c.optString("name"), c.optInt("qty", 1), if (c.isNull("printingId")) "" else c.optString("printingId"))
                },
                backBy = if (o.isNull("backBy")) null else o.optString("backBy"),
                gameNight = o.optBoolean("gameNight"),
                note = if (o.isNull("note")) null else o.optString("note"),
                lentAt = o.optLong("lentAt")
            )
        }
    }

    /** Asks the friend for the cards back, by a notification. False: one already went in the last 12 hours. */
    suspend fun remindLoan(clientId: String): Boolean = call("remind_loan", JSONObject().put("p_client_id", clientId)).trim() == "true"

    // ---- Sharing ----

    suspend fun setShare(kind: ShareKind, itemId: String, allFriends: Boolean, podIds: List<String>, link: Boolean): Share? =
        obj(call("set_library_share", JSONObject().put("p_kind", kind.wire).put("p_item_id", itemId).put("p_all_friends", allFriends).put("p_pod_ids", JSONArray(podIds)).put("p_link", link)))
            ?.let(::parseShare)

    /** Shares one deck or binder with one friend, or stops; the share, or null once nothing shares it. */
    suspend fun setItemFriendShare(kind: ShareKind, itemId: String, friend: String, on: Boolean): Share? =
        obj(call("set_item_friend_share", JSONObject().put("p_kind", kind.wire).put("p_item_id", itemId).put("p_friend", friend).put("p_on", on)))
            ?.let(::parseShare)

    /** Shares the whole collection ([ShareKind.COLLECTION]) or all decks with [viewer] — null: all friends — or stops. */
    suspend fun setShareAll(kind: ShareKind, viewer: String?, on: Boolean) {
        call("set_share_all", JSONObject().put("p_kind", kind.wire).put("p_viewer", viewer ?: JSONObject.NULL).put("p_on", on))
    }

    /** Every binder [owner] shares with the user, in full; null when none is. */
    suspend fun sharedCollection(owner: String): SharedCollection? =
        obj(call("get_shared_collection", JSONObject().put("p_owner", owner)))?.let { o ->
            val binders = o.optJSONArray("binders")
            SharedCollection(
                owner = parseProfile(o.getJSONObject("owner")),
                whole = o.optBoolean("whole"),
                binders = if (binders == null) emptyList() else (0 until binders.length()).map { binders.get(it).toString() }
            )
        }

    /** "Who has a card?": copies of cards named like [query] in friends' shared binders and decks. */
    suspend fun searchSharedCards(query: String): List<SharedCardHit> =
        parseCardHits(JSONArray(call("search_shared_cards", JSONObject().put("p_query", query))))

    /** Cards in friends' shared binders that are on one of the user's wishlists. */
    suspend fun wishlistMatches(): List<SharedCardHit> = parseCardHits(JSONArray(call("wishlist_matches")))

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

    /** The table shares the game with its players' remotes (match:<id> "state"). */
    suspend fun publishMatchState(matchId: String, state: JSONObject) {
        call("publish_match_state", JSONObject().put("p_match", matchId).put("p_state", state))
    }

    /** A seated player's remote asks the table to change their seat (match:<id> "action"). */
    suspend fun sendMatchAction(matchId: String, action: JSONObject) {
        call("send_match_action", JSONObject().put("p_match", matchId).put("p_action", action))
    }

    /** Friends' shared binder copies of these exact card names (a deck's missing cards). */
    suspend fun whoHasCards(names: List<String>): List<SharedCardHit> =
        parseCardHits(JSONArray(call("who_has_cards", JSONObject().put("p_names", JSONArray(names)))))

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

    // ---- Signing a browser in (see the web app's src/sync/qrLogin.ts) ----

    /** What a waiting browser looks like ("Chrome on Windows"), for the user to recognise before approving. */
    suspend fun webSignInRequest(code: String): String? {
        val rows = JSONArray(call("qr_login_request", JSONObject().put("p_code", code)).ifBlank { "[]" })
        val row = rows.optJSONObject(0) ?: return null
        return row.optString("browser").ifBlank { "A browser" }
    }

    /** Signs that browser in as this account. Throws when the code has run out or been used. */
    suspend fun approveWebSignIn(code: String): Unit = withContext(Dispatchers.IO) {
        val token = auth.accessToken() ?: throw SocialException("not_signed_in", MESSAGES.getValue("not_signed_in"))
        val request = Request.Builder()
            .url(BuildConfig.SUPABASE_URL + "/functions/v1/qr-login")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .post(JSONObject().put("code", code).toString().toRequestBody(JSON_MEDIA))
            .build()
        val response = try {
            auth.http.newCall(request).execute()
        } catch (e: IOException) {
            throw SocialException("offline", "You're offline — try again when you're connected.")
        }
        response.use {
            if (!it.isSuccessful) {
                val why = runCatching { JSONObject(it.body?.string().orEmpty()).optString("error") }.getOrNull()
                throw SocialException(
                    why.orEmpty(),
                    if (why == "done") "That code has already been used." else "That code has run out — show a new one."
                )
            }
        }
    }

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

    /**
     * Marks the caller's side of an accepted trade as applied. True: this call marked it, so move
     * the cards. False: that side was already done (or the trade isn't one to apply), so leave the
     * binders alone. Null: an older server that answers nothing — treated as "go ahead".
     */
    suspend fun markTradeApplied(tradeId: String): Boolean? =
        parseAppliedResult(call("mark_trade_applied", JSONObject().put("p_trade", tradeId)))

    companion object {
        /** Where a profile picture is served from (public, but only people who can see the profile learn its name). */
        fun avatarUrl(path: String?): String? =
            path?.let { BuildConfig.SUPABASE_URL + "/storage/v1/object/public/avatars/" + it.split('/').joinToString("/") { part -> URLEncoder.encode(part, "UTF-8") } }

        /** Profile pictures are at most this big. */
        const val MAX_AVATAR_BYTES = 2 * 1024 * 1024

        /** Links for QR codes and sharing always point at the live web app, which the Android app also understands. */
        const val PUBLIC_APP_URL = "https://manabind.com/"
        fun friendLink(username: String) = PUBLIC_APP_URL + "add/" + URLEncoder.encode(username, "UTF-8")
        fun seatLink(code: String, seat: Int) = PUBLIC_APP_URL + "join/$code/$seat"
        fun shareLink(token: String) = PUBLIC_APP_URL + "s/$token"
        fun loginLink(code: String) = PUBLIC_APP_URL + "login/$code"

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
            "trade_closed" to "This trade has already been answered.",
            "not_seated" to "You're no longer sitting at this table.",
            "not_host" to "Only the table can do that.",
            "not_in_pod" to "You're not in that pod any more.",
            "bad_players" to "Check the players: 2 to 10, each with a name, and one winner at most.",
            "too_many_games" to "This pod has 5,000 games recorded — delete some old ones first.",
            // supabase/migrations/20261006060000_pod_seasons.sql (League.kt)
            "season_running" to "This pod already has a season running — end it first.",
            "season_over" to "That season has ended.",
            "not_season_owner" to "Only whoever started the season, or the pod's owner, can change it.",
            "bad_season" to "Check the season: a name, the day it starts, and points from 0 to 10.",
            "too_many_seasons" to "This pod has 100 seasons already.",
            "too_many_loans" to "You have 500 loans open — get some cards back first.",
            // supabase/migrations/20261006020000_social_more.sql (see SocialMore.kt)
            "unavailable" to "Not available yet.",
            "blocked" to "You've blocked them — unblock them in Settings first.",
            "too_many_blocks" to "You've blocked a lot of people already.",
            "bad_reason" to "Pick a reason.",
            "note_too_long" to "Keep the note under 1,000 characters.",
            "too_many_reports" to "You've sent a lot of reports today — we'll look at those first.",
            "empty_message" to "Write something first.",
            "dm_too_long" to "Keep messages under 2,000 characters.",
            "cant_message" to "You can only message friends.",
            "slow_down" to "You're sending messages very fast — wait a minute.",
            "cant_rate" to "You can rate a trade once you've updated your binders for it.",
            // supabase/migrations/20261006070000_game_nights_chat.sql (GameNightsApi.kt)
            "bad_night" to "Check the night: a day and time within a year, and where (up to 80 characters).",
            "too_many_guests" to "Ask up to 20 friends from outside the pod.",
            "too_many_nights" to "This pod has a lot of game nights planned already.",
            "no_such_night" to "That game night isn't there any more.",
            "not_organiser" to "Only whoever planned the night, or the pod's owner, can change it.",
            "night_cancelled" to "That game night was called off.",
            "night_over" to "That game night is over.",
            "bad_answer" to "Pick Going, Maybe or Can't.",
            "bad_share" to "That can't be shared here.",
            // supabase/migrations/20261006080000_activity_comments.sql (see ActivityComments.kt)
            "bad_prefs" to "Those settings couldn't be saved.",
            "empty_comment" to "Write something first.",
            "comment_too_long" to "Keep comments under 1,000 characters.",
            "bad_comment_card" to "That card couldn't be added to the comment.",
            "cant_comment" to "Only friends the deck is shared with can comment.",
            "bad_parent" to "That comment isn't there any more.",
            "comment_slow_down" to "You're commenting very fast — wait a minute.",
            "too_many_comments" to "This deck has 1,000 comments already.",
            "not_your_comment" to "Only its author or the deck’s owner can do that.",
            // supabase/migrations/20261008100000_trade_nights.sql (TradeNightsApi.kt)
            "not_going" to "Answer Going first — trades are for the people going.",
            "cant_trade_here" to "You can trade here with people going who've put up their cards, or with friends.",
            "too_many_night_cards" to "A list for the night holds up to 500 lines.",
            "bad_sources" to "Those binders couldn't be used."
        )
    }
}

/** What a scanned QR code (or a pasted link) asks for. */
sealed interface AppLink {
    data class AddFriend(val username: String) : AppLink
    data class JoinSeat(val code: String, val seat: Int) : AppLink
    data class SharedLink(val token: String) : AppLink
    /** A browser waiting to be signed in, showing this code (see approveWebSignIn). */
    data class WebSignIn(val code: String) : AppLink

    companion object {
        /** Where the web app lived before manabind.com: codes and links made then still carry it. */
        private const val OLD_PATH = "/mtg-companion-web/"

        /** The web app's own addresses: its site, and a dev build's. */
        private val APP_HOST = Regex("""^https?://(?:(?:www\.)?manabind\.com|localhost(?::\d+)?)/""", RegexOption.IGNORE_CASE)

        /**
         * Reads one of the web app's links: on manabind.com, or from before it (any host serving the
         * app under /mtg-companion-web/, so an old dev build's links work too).
         */
        fun parse(text: String): AppLink? {
            val trimmed = text.trim()
            val rest = when {
                OLD_PATH in trimmed -> trimmed.substringAfter(OLD_PATH)
                else -> APP_HOST.find(trimmed)?.let { trimmed.substring(it.range.last + 1) } ?: return null
            }
            val path = rest.substringBefore('?').substringBefore('#').trimEnd('/')
            val parts = path.split('/').filter { it.isNotEmpty() }
            return when {
                parts.size == 2 && parts[0] == "add" && Regex("[a-zA-Z0-9_]{3,20}").matches(parts[1]) -> AddFriend(parts[1].lowercase())
                parts.size == 3 && parts[0] == "join" && Regex("[0-9a-f]{16}").matches(parts[1]) -> parts[2].toIntOrNull()?.let { JoinSeat(parts[1], it) }
                parts.size == 2 && parts[0] == "s" && Regex("[0-9a-f]{32}").matches(parts[1]) -> SharedLink(parts[1])
                parts.size == 2 && parts[0] == "login" && Regex("[0-9a-f]{32}").matches(parts[1]) -> WebSignIn(parts[1])
                else -> null
            }
        }
    }
}
