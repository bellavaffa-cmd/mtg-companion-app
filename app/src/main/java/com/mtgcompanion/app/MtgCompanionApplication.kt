package com.mtgcompanion.app

import com.mtgcompanion.app.data.supabase.SupabaseSync
import com.mtgcompanion.app.data.supabase.SupabaseAuth
import com.mtgcompanion.app.data.social.SocialRepository
import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.DriveImporter
import com.mtgcompanion.app.data.PlayerProfileRepository
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.SyncStateRepository
import com.mtgcompanion.app.data.artrecognition.ArtIndexRepository
import com.mtgcompanion.app.data.offline.OfflineCardRepository
import com.mtgcompanion.app.ui.lifecounter.LifeCounterSettingsRepository
import com.mtgcompanion.app.network.NetworkModule
import com.mtgcompanion.app.update.UpdateManager

class MtgCompanionApplication : Application(), ImageLoaderFactory {

    // App-wide singletons. The repos are backed by process-singleton DataStores, and the sync
    // manager must observe the same instances, so they're created once here.
    val settingsRepository by lazy { SettingsRepository(this) }
    val collectionRepository by lazy { CollectionRepository(this) }
    val deckRepository by lazy { DeckRepository(this) }
    /** One-time import from the retired Google Drive sync. */
    val driveImporter by lazy {
        DriveImporter(this, deckRepository, collectionRepository, SyncStateRepository(this))
    }
    /** Account + per-deck cloud sync (Supabase). Inert when this build has no Supabase project configured. */
    val supabaseSync by lazy { SupabaseSync(this, SupabaseAuth(this), deckRepository, collectionRepository) }
    val updateManager by lazy { UpdateManager(this) }
    val offlineCardRepository by lazy { OfflineCardRepository(this) }
    val playerProfileRepository by lazy { PlayerProfileRepository(this) }
    val lifeCounterSettingsRepository by lazy { LifeCounterSettingsRepository(this) }
    val artIndexRepository by lazy { ArtIndexRepository(this) }
    /** Friends, pods, sharing, trades and life counter seats (Supabase). */
    val socialRepository by lazy { SocialRepository(supabaseSync.auth) }

    override fun onCreate() {
        super.onCreate()
        // Give the network layer a Context so it can create its on-disk HTTP cache and check
        // connectivity. Must run before any repository/Coil request.
        NetworkModule.init(this)
        // Start cloud sync (restores the session and syncs if signed in).
        supabaseSync
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            // Scryfall's card image CDN, like its API, rejects requests with a default
            // HTTP-library User-Agent (400 generic_user_agent) - use the client that sets a
            // custom one (but without forcing the JSON API's Accept header, since this client
            // also fetches user-supplied image/GIF URLs from arbitrary hosts). This client
            // leaves caching to Coil's DiskCache below.
            .okHttpClient(NetworkModule.imageOkHttpClient)
            .components {
                add(SvgDecoder.Factory()) // Mana symbols are served by Scryfall as SVGs.
                // Animated GIF support, for custom Life Counter background images.
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
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
            // Smooths out every card image loading in, instead of popping in abruptly.
            .crossfade(true)
            .build()
}
