package com.mtgcompanion.app.ui.scan

import android.content.Context
import android.content.SharedPreferences
import com.mtgcompanion.app.data.ScannerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The scanner's mode last picked on its switch, and its one-time tips — kept on this phone only, not
 * synced: which job the scanner opens in is a habit of the device in hand. The web app keeps the same
 * in the browser (src/scan/scannerModes.ts).
 */
object ScannerModeSetting {
    private const val PREFS = "scanner_modes"
    private const val LAST = "last_mode"
    private const val SORT_TIP = "sort_tip_seen"

    private fun prefs(context: Context): SharedPreferences? =
        runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }.getOrNull()

    fun last(context: Context): ScannerMode? = ScannerMode.of(prefs(context)?.getString(LAST, null))

    fun setLast(context: Context, mode: ScannerMode) {
        prefs(context)?.edit()?.putString(LAST, mode.key)?.apply()
    }

    private val sortTip = MutableStateFlow(false)
    @Volatile private var tipLoaded = false

    /** Whether "New: sort a pile while you scan" has been seen (and so isn't shown again). */
    fun sortTipSeen(context: Context): StateFlow<Boolean> {
        if (!tipLoaded) {
            tipLoaded = true
            sortTip.value = prefs(context)?.getBoolean(SORT_TIP, false) ?: false
        }
        return sortTip.asStateFlow()
    }

    fun dismissSortTip(context: Context) {
        sortTipSeen(context)
        sortTip.value = true
        prefs(context)?.edit()?.putBoolean(SORT_TIP, true)?.apply()
    }

    /** Settings › Scanner › Show scanner tips again. */
    fun resetTips(context: Context) {
        sortTipSeen(context)
        sortTip.value = false
        prefs(context)?.edit()?.putBoolean(SORT_TIP, false)?.apply()
    }
}
