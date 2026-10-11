package com.mtgcompanion.app.ui.collection

import android.content.Context
import com.mtgcompanion.app.data.CollectionRoute
import com.mtgcompanion.app.data.collectionRoute
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Asks the Collection to open on a tab (and on Cards a grouping or quick filter): from a screen that
 * used to open one of the old home's tiles or tabs. The keys are the web app's ?tab= values, old ones
 * too — see collectionRoute (CollectionLayout.kt).
 */
object CollectionTabRequest {
    val route = MutableStateFlow<CollectionRoute?>(null)
    fun open(tab: String) { collectionRoute(tab)?.let { route.value = it } }
}

/** The Collection's last tab, kept on this phone, so it opens where it was left. */
object CollectionTabStore {
    private const val PREFS = "collection_layout"
    private const val KEY = "last_tab"

    fun last(context: Context): String? =
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) }.getOrNull()

    fun save(context: Context, key: String) {
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, key).apply() }
    }
}
