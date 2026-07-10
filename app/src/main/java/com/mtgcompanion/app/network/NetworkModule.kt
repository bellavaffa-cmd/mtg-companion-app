package com.mtgcompanion.app.network

import com.mtgcompanion.app.network.edhrec.EdhrecApi
import com.mtgcompanion.app.network.scryfall.ScryfallApi
import com.mtgcompanion.app.network.spellbook.SpellbookApi
import com.mtgcompanion.app.network.tcgplayer.TcgPlayerApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    /** Shared with Coil (see MtgCompanionApplication) so image requests carry the same User-Agent. */
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // Scryfall (and good API etiquette generally) rejects the default OkHttp
            // User-Agent with a 400; it wants a custom value identifying the app.
            val request = chain.request().newBuilder()
                .header("User-Agent", "MtgCompanionApp/1.0 (Android; +https://github.com/mtgcompanion)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

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
