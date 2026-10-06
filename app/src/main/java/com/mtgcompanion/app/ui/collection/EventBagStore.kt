package com.mtgcompanion.app.ui.collection

import android.content.Context
import android.content.SharedPreferences
import com.mtgcompanion.app.data.MAX_BAGS
import com.mtgcompanion.app.data.PackingBag
import com.mtgcompanion.app.data.localMoshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// The bags being packed (data/EventBag.kt), kept on this phone only: a bag is for one trip and its
// ticks aren't worth syncing — the web app keeps its own in the browser (src/collection/bagStore.ts).
// The newest MAX_BAGS are kept.
object EventBagStore {
    private const val PREFS = "packing_bags"
    private const val KEY = "bags_json"

    private val adapter by lazy { localMoshi.adapter<List<PackingBag>>(Types.newParameterizedType(List::class.java, PackingBag::class.java)) }
    private var prefs: SharedPreferences? = null

    private val _bags = MutableStateFlow<List<PackingBag>>(emptyList())
    val bags: StateFlow<List<PackingBag>> = _bags.asStateFlow()

    /** Loads what's kept, once. */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        p.getString(KEY, null)?.let { json -> runCatching { adapter.fromJson(json) }.getOrNull() }?.let { _bags.value = it }
    }

    private fun set(next: List<PackingBag>) {
        val kept = next.sortedByDescending { it.createdAt }.take(MAX_BAGS)
        _bags.value = kept
        runCatching { prefs?.edit()?.putString(KEY, adapter.toJson(kept))?.apply() }
    }

    fun save(bag: PackingBag) = set(if (_bags.value.any { it.id == bag.id }) _bags.value.map { if (it.id == bag.id) bag else it } else _bags.value + bag)

    fun delete(id: String) = set(_bags.value.filter { it.id != id })
}
