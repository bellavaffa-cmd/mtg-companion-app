package com.mtgcompanion.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.DriveSyncManager
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.SyncStateRepository
import com.mtgcompanion.app.data.offline.OfflineCardRepository
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
    val offlineCardRepository by lazy { OfflineCardRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // Give the network layer a Context so it can create its on-disk HTTP cache and check
        // connectivity. Must run before any repository/Coil request.
        NetworkModule.init(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            // Scryfall's card image CDN, like its API, rejects requests with a default
            // HTTP-library User-Agent (400 generic_user_agent) - use the client that sets a
            // custom one. This client leaves caching to Coil's DiskCache below.
            .okHttpClient(NetworkModule.imageOkHttpClient)
            // Mana symbols are served by Scryfall as SVGs.
            .components { add(SvgDecoder.Factory()) }
            // A generous, persistent disk cache so card art you've viewed stays available
            // offline. Scryfall image URLs are content-addressed (immutable), so we ignore
            // cache headers and never revalidate — a cached image is always current.
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
}
