package com.mtgcompanion.app.ui.onboarding

import android.content.Context
import com.mtgcompanion.app.data.WelcomeState
import com.mtgcompanion.app.data.parseWelcomeState
import com.mtgcompanion.app.data.welcomeStateJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the welcome flow remembers on this phone (Onboarding.kt's WelcomeState): whether it was
 * finished or skipped, and whether it has opened by itself. One per app, so Home and the flow see the
 * same. The web app keeps the same JSON in localStorage (src/onboarding/useWelcome.ts).
 */
class WelcomeStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("welcome", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(parseWelcomeState(prefs.getString(KEY, null)))
    val state: StateFlow<WelcomeState> = _state.asStateFlow()

    fun update(change: (WelcomeState) -> WelcomeState) {
        val next = change(_state.value)
        if (next == _state.value) return
        prefs.edit().putString(KEY, welcomeStateJson(next)).apply()
        _state.value = next
    }

    companion object {
        private const val KEY = "state_json"
        @Volatile private var instance: WelcomeStore? = null
        fun get(context: Context): WelcomeStore =
            instance ?: synchronized(this) { instance ?: WelcomeStore(context.applicationContext).also { instance = it } }
    }
}
