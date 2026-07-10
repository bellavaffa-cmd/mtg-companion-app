package com.mtgcompanion.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.mtgcompanion.app.network.NetworkModule

class MtgCompanionApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            // Scryfall's card image CDN, like its API, rejects requests with a default
            // HTTP-library User-Agent (400 generic_user_agent) - reuse the client that
            // already sets a custom one instead of Coil's own default client.
            .okHttpClient(NetworkModule.okHttpClient)
            .build()
}
