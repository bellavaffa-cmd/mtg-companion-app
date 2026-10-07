package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.supabase.SupabaseAuth
import com.mtgcompanion.app.data.supabase.realtimeConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The signed-in user's private message channel ("dm:<user id>"), where send_message puts each new
 * direct message they send or receive (event "message"). Only that user may listen
 * (supabase/migrations/20261006020000_social_more.sql). The web app's twin is watchDm in
 * src/sync/realtime.ts; this works like [com.mtgcompanion.app.data.supabase.MatchChannel].
 */
class DmChannel(private val auth: SupabaseAuth) {
    private val client = auth.http.newBuilder()
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(HEARTBEAT_MS, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Listens until the returned job (or [scope]) is cancelled, reconnecting after drops. [onMessage]
     * gets each message; [onRejoined] runs after a drop, so the screen can reload what it missed.
     * Callbacks come on OkHttp's thread.
     */
    fun watch(
        scope: CoroutineScope,
        userId: String,
        onMessage: (DirectMessage) -> Unit,
        onRejoined: () -> Unit,
        /** Pod chat and game nights on the same channel (GameNightsApi.kt): "pod_message" and "game_night", with their payload. */
        onOther: ((String, JSONObject) -> Unit)? = null
    ): Job = scope.launch {
        var attempt = 0
        var joinedBefore = false
        val config = JSONObject()
            .put("broadcast", JSONObject().put("ack", false).put("self", false))
            .put("presence", JSONObject().put("key", ""))
            .put("private", true)
        while (isActive) {
            val token = try {
                auth.accessToken() ?: return@launch // signed out
            } catch (e: Exception) {
                null
            }
            if (token != null) {
                realtimeConnection(auth, client, dmTopic(userId), config, token,
                    onJoined = {
                        attempt = 0
                        if (joinedBefore) onRejoined()
                        joinedBefore = true
                    },
                    onMessage = { event, payload ->
                        if (event == "broadcast" && payload != null && payload.optString("event") == "message") {
                            payload.optJSONObject("payload")?.let { runCatching { parseDirectMessage(it) }.getOrNull() }?.let(onMessage)
                        } else if (event == "broadcast" && payload != null && onOther != null) {
                            payload.optJSONObject("payload")?.let { onOther(payload.optString("event"), it) }
                        }
                    })
            }
            delay(RETRY_MS[minOf(attempt, RETRY_MS.size - 1)])
            attempt++
        }
    }

    private companion object {
        const val HEARTBEAT_MS = 25_000L
        val RETRY_MS = longArrayOf(2_000, 5_000, 15_000, 30_000)
    }
}
