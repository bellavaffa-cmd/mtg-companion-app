package com.mtgcompanion.app.data.usage

import android.content.Context
import android.content.SharedPreferences
import com.mtgcompanion.app.BuildConfig
import com.mtgcompanion.app.network.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Anonymous usage counts on this phone: kept in SharedPreferences, sent to the owner's Supabase
 * project (record_usage) once a day is over. Best effort and silent — a send that fails (offline, or
 * the function not there yet) keeps the counts for tomorrow's try, until they're a week old. The
 * rules are in [UsageCounts]; Settings › Privacy turns it off. The web app does the same.
 */
object Usage {
    private const val PREFS = "usage_counts"
    private const val KEY_STATE = "state"
    private const val KEY_OFF = "off"
    private val JSON = "application/json".toMediaType()

    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val sending = AtomicBoolean(false)

    private val _enabled = MutableStateFlow(true)
    /** Settings › Privacy's "Share anonymous usage counts". On unless turned off. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _enabled.value = !p.getBoolean(KEY_OFF, false)
        sendDue()
    }

    /** Turning it off forgets everything kept so far. */
    fun setEnabled(on: Boolean) {
        val p = prefs ?: return
        _enabled.value = on
        synchronized(lock) {
            val edit = p.edit().putBoolean(KEY_OFF, !on)
            if (!on) edit.putString(KEY_STATE, UsageCounts.toJson(UsageCounts.optedOut()))
            edit.apply()
        }
    }

    /** Counts a feature action. */
    fun action(action: UsageAction) = count(action.id)

    /** Counts a screen opened, by its nav route pattern (arguments aren't kept). */
    fun screen(route: String?) {
        UsageCounts.screenOfRoute(route)?.let { count(UsageCounts.screenEvent(it)) }
    }

    private fun today() = LocalDate.now().toString()

    private fun load(p: SharedPreferences) = UsageCounts.parse(p.getString(KEY_STATE, null))
    private fun save(p: SharedPreferences, s: UsageState) { p.edit().putString(KEY_STATE, UsageCounts.toJson(s)).apply() }

    private fun count(event: String) {
        val p = prefs ?: return
        if (!_enabled.value) return
        runCatching {
            synchronized(lock) { save(p, UsageCounts.record(load(p), event, today(), true) { UsageCounts.newInstallId() }) }
        }
        sendDue()
    }

    /** Sends finished days, at most one try a day. Called at start and after each count. */
    private fun sendDue() {
        val p = prefs ?: return
        if (!_enabled.value || BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) return
        val day = today()
        val batches = synchronized(lock) { UsageCounts.dueBatches(load(p), day, true) }
        if (batches.isEmpty() || !sending.compareAndSet(false, true)) return
        scope.launch {
            try {
                synchronized(lock) { save(p, UsageCounts.markTried(load(p), day)) }
                for (b in batches) {
                    val body = JSONObject()
                        .put("p_install", b.install)
                        .put("p_platform", "android")
                        .put("p_version", BuildConfig.VERSION_NAME)
                        .put("p_day", b.day)
                        .put("p_counts", JSONObject(b.counts))
                    val request = Request.Builder()
                        .url(BuildConfig.SUPABASE_URL + "/rest/v1/rpc/record_usage")
                        .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .post(body.toString().toRequestBody(JSON))
                        .build()
                    val ok = NetworkModule.noCacheOkHttpClient.newCall(request).execute().use { it.isSuccessful }
                    if (!ok) break
                    synchronized(lock) { save(p, UsageCounts.markSent(load(p), b)) }
                }
            } catch (_: Exception) {
                // Offline: tomorrow.
            } finally {
                sending.set(false)
            }
        }
    }
}
