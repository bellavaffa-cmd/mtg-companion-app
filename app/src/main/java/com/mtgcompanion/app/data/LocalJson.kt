package com.mtgcompanion.app.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/** Shared Moshi instance for on-device JSON storage (collection, decks), separate from the network layer. */
internal val localMoshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
