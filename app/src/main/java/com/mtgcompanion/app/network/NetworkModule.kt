package com.mtgcompanion.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.mtgcompanion.app.network.edhrec.EdhrecApi
import com.mtgcompanion.app.network.scryfall.ScryfallApi
import com.mtgcompanion.app.network.spellbook.SpellbookApi
import com.mtgcompanion.app.network.tcgplayer.TcgPlayerApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object NetworkModule {

    private lateinit var appContext: Context

    /** Must be called once from Application.onCreate before any network or image request. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        // Only gate on having a network with internet capability. We deliberately don't require
        // NET_CAPABILITY_VALIDATED: a connected-but-unvalidated network (captive portal, emulator)
        // shouldn't force the offline-cache path — if the network is genuinely dead the request
        // fails with an IOException, which callers already handle as offline.
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // Scryfall (and good API etiquette generally) rejects the default OkHttp User-Agent with a
    // 400; both its API and its image CDN want a custom value identifying the app.
    private val userAgentInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", "MtgCompanionApp/1.0 (Android; +https://github.com/mtgcompanion)")
            .header("Accept", "application/json")
            .build()
        chain.proceed(request)
    }

    private fun baseBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(userAgentInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })

    /**
     * API client with an on-disk HTTP cache so card data fetched while online is still available
     * offline. GET responses are made cacheable for a day (see network interceptor); when the
     * device is offline, requests are rewritten to serve a cached copy even if stale rather than
     * failing outright. (POST endpoints like /cards/collection aren't HTTP-cacheable, so features
     * relying on them degrade gracefully offline.)
     */
    val okHttpClient: OkHttpClient by lazy {
        baseBuilder()
            .cache(Cache(File(appContext.cacheDir, "http_cache"), 25L * 1024 * 1024))
            .addInterceptor { chain ->
                val request = if (!isOnline()) {
                    chain.request().newBuilder()
                        .header("Cache-Control", "public, only-if-cached, max-stale=${60 * 60 * 24 * 30}")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .addNetworkInterceptor { chain ->
                chain.proceed(chain.request()).newBuilder()
                    .removeHeader("Pragma")
                    .header("Cache-Control", "public, max-age=${60 * 60 * 24}")
                    .build()
            }
            .build()
    }

    /**
     * Image client: carries the User-Agent Scryfall's CDN requires but leaves caching to Coil's own
     * DiskCache (see MtgCompanionApplication), which stores full images rather than sharing the
     * small JSON HTTP cache.
     */
    val imageOkHttpClient: OkHttpClient by lazy { baseBuilder().build() }

    private fun retrofitFor(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val scryfallApi: ScryfallApi by lazy {
        retrofitFor("https://api.scryfall.com/").create(ScryfallApi::class.java)
    }

    val spellbookApi: SpellbookApi by lazy {
        retrofitFor("https://backend.commanderspellbook.com/").create(SpellbookApi::class.java)
    }

    val edhrecApi: EdhrecApi by lazy {
        retrofitFor("https://json.edhrec.com/").create(EdhrecApi::class.java)
    }

    val tcgPlayerApi: TcgPlayerApi by lazy {
        retrofitFor("https://api.tcgplayer.com/").create(TcgPlayerApi::class.java)
    }
}
