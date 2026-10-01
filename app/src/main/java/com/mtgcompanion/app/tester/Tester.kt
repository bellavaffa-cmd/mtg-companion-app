package com.mtgcompanion.app.tester

import android.content.Context
import android.os.Build
import com.mtgcompanion.app.BuildConfig
import com.mtgcompanion.app.MtgCompanionApplication
import com.mtgcompanion.app.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Everything the tester app has that the real one doesn't: a trail of what the app just did, reports
 * and crash reports sent to the developer, switches, and a second account to test with.
 *
 * All of it hangs off [on], which is only true in the tester build (see the beta build type). In the
 * real app nothing here is installed, drawn or recorded — every entry point returns at once.
 */
object Tester {

    /** True only in the tester app. */
    val on: Boolean = UpdateManager.IS_TESTER

    const val BUILD: Int = BuildConfig.TESTER_BUILD

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var flags: TesterFlags
        private set
    lateinit var reports: TesterReports
        private set
    lateinit var accounts: TesterAccounts
        private set

    /** The screen in front, as the nav graph names it. */
    @Volatile var screen: String = "start"
        private set

    /** Something asked for the tester tools screen; the nav graph goes there and clears it. */
    private val _openTools = MutableStateFlow(false)
    val openTools: StateFlow<Boolean> = _openTools.asStateFlow()

    /** The feature a "how was that?" prompt is waiting to ask about, if any. */
    private val _feedbackFor = MutableStateFlow<String?>(null)
    val feedbackFor: StateFlow<String?> = _feedbackFor.asStateFlow()

    /** The last card the scanner settled on, for the scanner's debug strip. */
    private val _lastScan = MutableStateFlow<ScanOutcome?>(null)
    val lastScan: StateFlow<ScanOutcome?> = _lastScan.asStateFlow()

    fun init(app: MtgCompanionApplication) {
        if (!on) return
        flags = TesterFlags(app)
        reports = TesterReports(app, app.supabaseSync.auth)
        accounts = TesterAccounts(app, app.supabaseSync)
        TesterCrashes.install(app)
        TesterLog.add("app", "Started tester build $BUILD (${BuildConfig.VERSION_NAME}) on ${device()}")
        // What sync made of each pass: the status is all it tells anyone, so that's what's kept.
        scope.launch {
            var last: String? = null
            app.supabaseSync.status.collect { s ->
                if (s.syncing) return@collect
                val line = when {
                    s.failed -> "Sync failed: ${s.message}"
                    s.lastSyncedAt == 0L -> return@collect
                    else -> "Synced: ${s.pulled} pulled, ${s.pushed} pushed"
                }
                val stamped = "$line@${s.lastSyncedAt}"
                if (stamped != last) { last = stamped; TesterLog.add("sync", line) }
            }
        }
        scope.launch {
            app.supabaseSync.auth.account.collect { account ->
                TesterLog.add("app", if (account == null) "Signed out" else "Signed in as ${account.email}")
                // Reports written while signed out (a crash on the sign-in screen, say) go now.
                if (account != null) reports.flush()
            }
        }
    }

    /** The nav graph says which screen is in front. Leaving the scanner or a remote asks how it went. */
    fun onScreen(route: String?) {
        if (!on || route == null || route == screen) return
        val left = screen
        screen = route
        TesterLog.add("screen", route)
        if (!flags.feedbackPrompts) return
        val feature = when {
            left == "scan" -> "scanner"
            left.startsWith("remote/") -> "remote"
            left == "life_counter" -> "life counter"
            else -> null
        }
        if (feature != null && flags.askedAbout(feature) < BUILD) _feedbackFor.value = feature
    }

    fun feedbackDone(feature: String) {
        flags.markAsked(feature, BUILD)
        _feedbackFor.value = null
    }

    fun showTools() { if (on) _openTools.value = true }
    fun toolsShown() { _openTools.value = false }

    /** The tester tools asked for this build's "what's new" again; the overlay shows it and clears this. */
    private val _notesRequested = MutableStateFlow(false)
    val notesRequested: StateFlow<Boolean> = _notesRequested.asStateFlow()
    fun showNotes() { _notesRequested.value = true }
    fun notesShown() { _notesRequested.value = false }

    /** The tester tools asked for a report of this kind ("bug" or "idea") to be started. */
    private val _reportRequested = MutableStateFlow<String?>(null)
    val reportRequested: StateFlow<String?> = _reportRequested.asStateFlow()
    fun startReport(kind: String) { _reportRequested.value = kind }
    fun reportStarted() { _reportRequested.value = null }

    fun scanDone(outcome: ScanOutcome) {
        if (!on) return
        _lastScan.value = outcome
        TesterLog.add("scan", outcome.line())
    }

    fun device(): String = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"

    /** What every report carries, so nobody has to be asked which phone or build it was. */
    fun context(app: Context): JSONObject {
        val a = app.applicationContext as MtgCompanionApplication
        val account = a.supabaseSync.auth.account.value
        val sync = a.supabaseSync.status.value
        return JSONObject()
            .put("build", BUILD)
            .put("version", BuildConfig.VERSION_NAME)
            .put("device", device())
            .put("sdk", Build.VERSION.SDK_INT)
            .put("screen", screen)
            .put("signedIn", account != null)
            .put("lastSyncedAt", sync.lastSyncedAt)
            .put("syncMessage", sync.message ?: JSONObject.NULL)
            .put("syncFailed", sync.failed)
    }
}

/** How one scan ended, as the scanner saw it. */
data class ScanOutcome(
    /** The id the scanner's own capture gave this attempt (its pictures are filed under it); 0 without one. */
    val captureId: Int,
    val titleRead: String?,
    val name: String?,
    val set: String?,
    val number: String?,
    /** Whether the printing is known rather than guessed. */
    val certain: Boolean,
    /** How the printing was settled: read in the frame, off the strip, by sight, or the name alone. */
    val how: String,
    val tookMs: Long
) {
    fun line(): String =
        if (name == null) "Not added: read \"${titleRead.orEmpty()}\" ($how, $tookMs ms)"
        else "$name (${set?.uppercase()} #$number) ${if (certain) "certain" else "guessed"}, $how, $tookMs ms; read \"${titleRead.orEmpty()}\""
}

/**
 * The tester's own switches, and what it has already shown or asked. Kept on the phone; a switch a
 * future feature hides behind is just another name here (see [isOn]).
 */
class TesterFlags(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tester", Context.MODE_PRIVATE)

    /** Goes up whenever a switch is flipped, for the screen to notice. */
    val changes = MutableStateFlow(0)

    var banner: Boolean
        get() = prefs.getBoolean("banner", true)
        set(v) { prefs.edit().putBoolean("banner", v).apply(); changes.value++ }
    var bugButton: Boolean
        get() = prefs.getBoolean("bug_button", true)
        set(v) { prefs.edit().putBoolean("bug_button", v).apply(); changes.value++ }
    var shakeToReport: Boolean
        get() = prefs.getBoolean("shake", true)
        set(v) { prefs.edit().putBoolean("shake", v).apply(); changes.value++ }
    var scanDebug: Boolean
        get() = prefs.getBoolean("scan_debug", false)
        set(v) { prefs.edit().putBoolean("scan_debug", v).apply(); changes.value++ }
    var feedbackPrompts: Boolean
        get() = prefs.getBoolean("feedback_prompts", true)
        set(v) { prefs.edit().putBoolean("feedback_prompts", v).apply(); changes.value++ }
    var logTaps: Boolean
        get() = prefs.getBoolean("log_taps", true)
        set(v) { prefs.edit().putBoolean("log_taps", v).apply(); changes.value++ }

    /** The build whose "what's new" has been seen. */
    var seenNotesFor: Int
        get() = prefs.getInt("seen_notes", 0)
        set(v) { prefs.edit().putInt("seen_notes", v).apply() }

    /** The build in which [feature] was last asked about, so it's asked once a build. */
    fun askedAbout(feature: String): Int = prefs.getInt("asked_$feature", 0)
    fun markAsked(feature: String, build: Int) { prefs.edit().putInt("asked_$feature", build).apply() }

    /** A switch for something unfinished, off unless turned on from the tester tools. */
    fun isOn(feature: String): Boolean = prefs.getBoolean("feature_$feature", false)
    fun set(feature: String, on: Boolean) { prefs.edit().putBoolean("feature_$feature", on).apply(); changes.value++ }
}

/**
 * Unfinished features the tester can turn on from the tester tools, by the name the code asks
 * [TesterFlags.isOn] about. Empty until something ships half-done.
 */
val TESTER_FEATURES: List<Pair<String, String>> = emptyList()
