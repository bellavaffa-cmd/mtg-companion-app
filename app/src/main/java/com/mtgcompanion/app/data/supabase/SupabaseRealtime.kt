package com.mtgcompanion.app.data.supabase

import android.net.Uri
import com.mtgcompanion.app.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Live updates from Supabase Realtime: while the app is open, it hears as soon as another device saves
 * one of this account's decks or binders, and syncs then instead of at its next check. OkHttp's
 * WebSocket speaking Realtime's Phoenix protocol (JSON messages), no SDK — the web app does the same
 * in src/sync/realtime.ts.
 *
 * Realtime applies library_items' row-level security with this device's own sign-in, so only this
 * account's rows are ever heard about. Needs the table in the supabase_realtime publication
 * (supabase/migrations/20260918010000_library_realtime.sql).
 */
internal class SupabaseRealtime(private val auth: SupabaseAuth, private val scope: CoroutineScope) {

    private companion object {
        /** Phoenix drops a connection it hasn't heard from in a while. */
        const val HEARTBEAT_MS = 25_000L
        /** Waits before reconnecting after a drop, growing with each failed attempt. */
        val RETRY_MS = longArrayOf(2_000, 5_000, 15_000, 30_000)
    }

    private enum class Outcome { DROPPED, UNAVAILABLE }

    // No read timeout: the connection sits quiet between changes. WebSocket pings keep it open, and
    // notice (and close) one that died without closing.
    private val client = auth.http.newBuilder()
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(HEARTBEAT_MS, TimeUnit.MILLISECONDS)
        .build()

    private var job: Job? = null

    /** Whether it's connected, so the caller can check less often meanwhile. */
    @Volatile
    var live = false
        private set

    /**
     * Listens for changes to [userId]'s library rows until [stop], calling [onChange] for each (and
     * after a reconnect, as changes may have been missed while it was down). Called from both the
     * main thread and the account watcher, so it's synchronized: never two connections.
     */
    @Synchronized
    fun start(userId: String, onChange: () -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                listen(userId, onChange)
            } finally {
                live = false
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        live = false
    }

    private suspend fun listen(userId: String, onChange: () -> Unit) {
        var attempt = 0
        var everJoined = false
        while (currentCoroutineContext().isActive) {
            val token = try {
                auth.accessToken() ?: return // signed out: nothing to listen for
            } catch (e: Exception) {
                null // offline, or the sign-in server busy: try again shortly
            }
            val outcome = if (token == null) Outcome.DROPPED else connection(userId, token, onChange) {
                attempt = 0
                if (everJoined) onChange() // back after a drop: catch up on anything missed
                everJoined = true
            }
            live = false
            // Realtime can't subscribe to the table (it isn't published for it): stop trying; the
            // regular checks carry on.
            if (outcome == Outcome.UNAVAILABLE) return
            delay(RETRY_MS[minOf(attempt, RETRY_MS.size - 1)])
            attempt++
        }
    }

    /** One connection: join, then keep it alive (and its sign-in fresh) until it drops or is refused. */
    private suspend fun connection(userId: String, token: String, onChange: () -> Unit, onJoined: () -> Unit): Outcome {
        val changes = JSONObject().put("event", "*").put("schema", "public").put("table", "library_items")
            .put("filter", "user_id=eq.$userId")
        val config = JSONObject()
            .put("broadcast", JSONObject().put("ack", false).put("self", false))
            .put("presence", JSONObject().put("key", ""))
            .put("private", false)
            .put("postgres_changes", JSONArray().put(changes))
        val result = realtimeConnection(auth, client, "library-$userId", config, token,
            onJoined = { live = true; onJoined() },
            onMessage = { event, _ -> if (event == "postgres_changes") onChange() })
        return if (result == ChannelEnd.UNAVAILABLE) Outcome.UNAVAILABLE else Outcome.DROPPED
    }
}

internal enum class ChannelEnd { DROPPED, UNAVAILABLE }

/**
 * One Realtime connection to channel [topic] (without the "realtime:" prefix): joins with [config],
 * answers heartbeats, hands Realtime a renewed sign-in, and returns when it drops or is refused.
 * [onMessage] gets every other message for the channel: its event and payload.
 */
internal suspend fun realtimeConnection(
    auth: SupabaseAuth,
    client: OkHttpClient,
    topic: String,
    config: JSONObject,
    token: String,
    onJoined: () -> Unit,
    onMessage: (event: String, payload: JSONObject?) -> Unit
): ChannelEnd = coroutineScope {
    val fullTopic = "realtime:$topic"
    val ref = AtomicInteger(0)
    val closed = AtomicBoolean(false)
    val joined = AtomicBoolean(false)
    val done = CompletableDeferred<ChannelEnd>()
    val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val join = ref.incrementAndGet().toString()
            webSocket.send(
                JSONObject().put("topic", fullTopic).put("event", "phx_join").put("ref", join).put("join_ref", join)
                    .put("payload", JSONObject().put("config", config).put("access_token", token)).toString()
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (closed.get()) return // a late message from a connection being shut down
            val message = runCatching { JSONObject(text) }.getOrNull() ?: return
            if (message.optString("topic") != fullTopic) return
            val payload = message.optJSONObject("payload")
            val status = payload?.optString("status")
            when (val event = message.optString("event")) {
                "phx_reply" -> when {
                    status == "ok" && joined.compareAndSet(false, true) -> onJoined()
                    status == "error" -> done.complete(ChannelEnd.DROPPED) // refused: retry with a fresh sign-in
                }
                "system" -> if (status == "error") {
                    done.complete(if (payload.optString("extension") == "postgres_changes") ChannelEnd.UNAVAILABLE else ChannelEnd.DROPPED)
                }
                "phx_error", "phx_close" -> done.complete(ChannelEnd.DROPPED)
                else -> onMessage(event, payload)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { done.complete(ChannelEnd.DROPPED) }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { done.complete(ChannelEnd.DROPPED) }
    }
    val url = BuildConfig.SUPABASE_URL.trimEnd('/').replaceFirst("http", "ws") +
        "/realtime/v1/websocket?apikey=" + Uri.encode(BuildConfig.SUPABASE_ANON_KEY) + "&vsn=1.0.0"
    val socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
    val heartbeat = launch {
        while (isActive) {
            delay(REALTIME_HEARTBEAT_MS)
            socket.send(JSONObject().put("topic", "phoenix").put("event", "heartbeat").put("payload", JSONObject()).put("ref", ref.incrementAndGet().toString()).toString())
        }
    }
    val renewal = launch {
        var sent = token
        while (isActive) {
            delay(REALTIME_TOKEN_CHECK_MS)
            val fresh = runCatching { auth.accessToken() }.getOrNull() ?: continue
            if (fresh == sent) continue
            sent = fresh
            socket.send(
                JSONObject().put("topic", fullTopic).put("event", "access_token").put("ref", ref.incrementAndGet().toString())
                    .put("payload", JSONObject().put("access_token", fresh)).toString()
            )
        }
    }
    try {
        done.await()
    } finally {
        closed.set(true)
        heartbeat.cancel()
        renewal.cancel()
        socket.cancel()
    }
}

private const val REALTIME_HEARTBEAT_MS = 25_000L
private const val REALTIME_TOKEN_CHECK_MS = 30_000L
private val REALTIME_RETRY_MS = longArrayOf(2_000, 5_000, 15_000, 30_000)

/**
 * A life counter match's private channel ("match:<id>"): the table publishes the game ("state") and
 * seated players' remotes send requests ("action"). Only the table and its seated players may listen
 * (supabase/migrations/20260922000000_match_remote.sql); nobody broadcasts directly — the
 * publish_match_state / send_match_action functions do. The web app's twin is watchMatch in
 * src/sync/realtime.ts.
 */
class MatchChannel(private val auth: SupabaseAuth) {
    private val client = auth.http.newBuilder()
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(REALTIME_HEARTBEAT_MS, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Listens on [matchId]'s channel until the returned job (or [scope]) is cancelled, reconnecting
     * after drops. [onJoined] runs on each (re)join, so the caller can send or ask for the current
     * game; [onLive] says whether it's connected. Callbacks come on OkHttp's thread.
     */
    fun watch(
        scope: CoroutineScope,
        matchId: String,
        onEvent: (event: String, payload: JSONObject) -> Unit,
        onJoined: () -> Unit,
        onLive: (Boolean) -> Unit
    ): Job = scope.launch {
        var attempt = 0
        val config = JSONObject()
            .put("broadcast", JSONObject().put("ack", false).put("self", false))
            .put("presence", JSONObject().put("key", ""))
            .put("private", true)
        try {
            while (isActive) {
                val token = try {
                    auth.accessToken() ?: return@launch // signed out
                } catch (e: Exception) {
                    null
                }
                if (token != null) {
                    realtimeConnection(auth, client, "match:$matchId", config, token,
                        onJoined = { attempt = 0; onLive(true); onJoined() },
                        onMessage = { event, payload ->
                            if (event == "broadcast" && payload != null) {
                                val name = payload.optString("event")
                                val body = payload.optJSONObject("payload")
                                if (name.isNotEmpty() && body != null) onEvent(name, body)
                            }
                        })
                }
                onLive(false)
                delay(REALTIME_RETRY_MS[minOf(attempt, REALTIME_RETRY_MS.size - 1)])
                attempt++
            }
        } finally {
            onLive(false)
        }
    }
}
