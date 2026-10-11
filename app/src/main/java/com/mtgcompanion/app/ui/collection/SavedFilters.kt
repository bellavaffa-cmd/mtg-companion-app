package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.usage.Usage
import com.mtgcompanion.app.data.usage.UsageAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The filters saved by name on Advanced filters ("Save as…"), kept on this device as JSON
 * (AdvancedFilter.kt savedFiltersToJson) — one list for the Cards tab, every place's page and the
 * Storage tab. The web app's src/collection/savedFilters.ts.
 */
class SavedFilters(private val settings: SettingsRepository, private val scope: CoroutineScope) {
    val list: Flow<List<SavedFilter>> = settings.savedFiltersJson.map { savedFiltersFromJson(it) }

    /** Saves the filters under [name]; one already called that is replaced. */
    fun save(name: String, basic: CollectionFilter, advanced: AdvancedFilter) {
        val n = name.trim()
        if (n.isEmpty()) return
        Usage.action(UsageAction.FILTER_SAVED)
        scope.launch {
            settings.updateSavedFiltersJson { json ->
                val list = savedFiltersFromJson(json)
                val same = list.firstOrNull { it.name.equals(n, ignoreCase = true) }
                val item = SavedFilter(same?.id ?: java.util.UUID.randomUUID().toString(), n, basic, advanced)
                savedFiltersToJson(if (same != null) list.map { if (it.id == same.id) item else it } else list + item)
            }
        }
    }

    fun rename(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        scope.launch {
            settings.updateSavedFiltersJson { json -> savedFiltersToJson(savedFiltersFromJson(json).map { if (it.id == id) it.copy(name = n) else it }) }
        }
    }

    fun delete(id: String) {
        scope.launch {
            settings.updateSavedFiltersJson { json -> savedFiltersToJson(savedFiltersFromJson(json).filterNot { it.id == id }) }
        }
    }
}
