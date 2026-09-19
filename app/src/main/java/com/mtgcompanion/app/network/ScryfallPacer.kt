package com.mtgcompanion.app.network

import android.os.SystemClock
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Keeps the app within Scryfall's hard rate limits (scryfall.com/docs/api/rate-limits):
 * /cards/search, /cards/named, /cards/random and /cards/collection at most 2 a second, everything
 * else on api.scryfall.com 10 a second. A 429 locks the app out for 30 seconds — and ignoring it
 * risks a ban — so after one every request to the API waits the lockout out, and the refused
 * request is asked again once, after it.
 *
 * [pace] runs as a network interceptor, so answers served from the phone's HTTP cache don't wait.
 * The web app does the same in src/api/scryfall.ts.
 */
object ScryfallPacer {
    private const val HOST = "api.scryfall.com"
    private const val SLOW_GAP_MS = 550L
    private const val FAST_GAP_MS = 110L
    private const val LOCKOUT_MS = 31_000L
    private val SLOW_PATHS = listOf("/cards/search", "/cards/named", "/cards/random", "/cards/collection")

    private val lock = Any()
    private var nextSlow = 0L
    private var nextFast = 0L
    @Volatile private var blockedUntil = 0L

    private fun isSlow(path: String) = SLOW_PATHS.any { path.startsWith(it) }

    /** Sleeps until this request's turn: its endpoint's spacing, and any lockout. */
    private fun waitTurn(slow: Boolean) {
        val wait = synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            val start = maxOf(now, blockedUntil, if (slow) nextSlow else nextFast)
            if (slow) nextSlow = start + SLOW_GAP_MS else nextFast = start + FAST_GAP_MS
            start - now
        }
        if (wait > 0) Thread.sleep(wait)
    }

    /** Network interceptor: spaces out real requests to the API, and notes a lockout. */
    val pace = Interceptor { chain ->
        val request = chain.request()
        if (request.url.host != HOST) return@Interceptor chain.proceed(request)
        waitTurn(isSlow(request.url.encodedPath))
        val response = chain.proceed(request)
        if (response.code == 429) blockedUntil = SystemClock.elapsedRealtime() + LOCKOUT_MS
        response
    }

    /** Application interceptor: a request refused with 429 is asked once more, after the lockout. */
    val retryAfterLockout = Interceptor { chain ->
        val request = chain.request()
        val response: Response = chain.proceed(request)
        if (request.url.host != HOST || response.code != 429) return@Interceptor response
        response.close()
        val wait = blockedUntil - SystemClock.elapsedRealtime()
        if (wait > 0) Thread.sleep(wait)
        chain.proceed(request)
    }
}
