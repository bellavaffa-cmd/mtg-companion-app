package com.mtgcompanion.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.DriveSyncManager
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.SyncStateRepository
import com.mtgcompanion.app.network.NetworkModule
import com.mtgcompanion.app.update.UpdateManager

class MtgCompanionApplication : Application(), ImageLoaderFactory {

    // App-wide singletons. The repos are backed by process-singleton DataStores, and the sync
    // manager must observe the same instances, so they're created once here.
    val settingsRepository by lazy { SettingsRepository(this) }
    val collectionRepository by lazy { CollectionRepository(this) }
    val deckRepository by lazy { DeckRepository(this) }
    val driveSyncManager by lazy {
        DriveSyncManager(this, deckRepository, collectionRepository, SyncStateRepository(this))
    }
    val updateManager by lazy { UpdateManager(this) }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            // Scryfall's card image CDN, like its API, rejects requests with a default
            // HTTP-library User-Agent (400 generic_user_agent) - reuse the client that
            // already sets a custom one instead of Coil's own default client.
            .okHttpClient(NetworkModule.okHttpClient)
            // Mana symbols are served by Scryfall as SVGs.
            .components { add(SvgDecoder.Factory()) }
            .build()
}
