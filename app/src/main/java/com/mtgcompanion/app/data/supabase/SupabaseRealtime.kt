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
        /**
         * How often the sign-in is checked. accessToken() renews it in its last minute, and a renewed
         * one is handed to Realtime right away — otherwise Realtime ends the subscription when the old
         * one expires.
         */
        const val TOKEN_CHECK_MS = 30_000L
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
    private suspend fun connection(userId: String, token: String, onChange: () -> Unit, onJoined: () -> Unit): Outcome = coroutineScope {
        val topic = "realtime:library-$userId"
        val ref = AtomicInteger(0)
        val closed = AtomicBoolean(false)
        val done = CompletableDeferred<Outcome>()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val join = ref.incrementAndGet().toString()
                val changes = JSONObject().put("event", "*").put("schema", "public").put("table", "library_items")
                    .put("filter", "user_id=eq.$userId")
                val config = JSONObject()
                    .put("broadcast", JSONObject().put("ack", false).put("self", false))
                    .put("presence", JSONObject().put("key", ""))
                    .put("private", false)
                    .put("postgres_changes", JSONArray().put(changes))
                webSocket.send(
                    JSONObject().put("topic", topic).put("event", "phx_join").put("ref", join).put("join_ref", join)
                        .put("payload", JSONObject().put("config", config).put("access_token", token)).toString()
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (closed.get()) return // a late message from a connection being shut down
                val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (message.optString("topic") != topic) return
                val payload = message.optJSONObject("payload")
                val status = payload?.optString("status")
                when (message.optString("event")) {
                    "phx_reply" -> when {
                        status == "ok" && !live -> { live = true; onJoined() }
                        status == "error" -> done.complete(Outcome.DROPPED) // refused: retry with a fresh sign-in
                    }
                    "postgres_changes" -> onChange()
                    "system" -> if (status == "error") {
                        done.complete(if (payload.optString("extension") == "postgres_changes") Outcome.UNAVAILABLE else Outcome.DROPPED)
                    }
                    "phx_error", "phx_close" -> done.complete(Outcome.DROPPED)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { done.complete(Outcome.DROPPED) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { done.complete(Outcome.DROPPED) }
        }
        val url = BuildConfig.SUPABASE_URL.trimEnd('/').replaceFirst("http", "ws") +
            "/realtime/v1/websocket?apikey=" + Uri.encode(BuildConfig.SUPABASE_ANON_KEY) + "&vsn=1.0.0"
        val socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
        val heartbeat = launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                socket.send(JSONObject().put("topic", "phoenix").put("event", "heartbeat").put("payload", JSONObject()).put("ref", ref.incrementAndGet().toString()).toString())
            }
        }
        val renewal = launch {
            var sent = token
            while (isActive) {
                delay(TOKEN_CHECK_MS)
                val fresh = runCatching { auth.accessToken() }.getOrNull() ?: continue
                if (fresh == sent) continue
                sent = fresh
                socket.send(
                    JSONObject().put("topic", topic).put("event", "access_token").put("ref", ref.incrementAndGet().toString())
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
}
