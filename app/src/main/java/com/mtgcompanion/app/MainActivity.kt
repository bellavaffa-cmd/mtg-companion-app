package com.mtgcompanion.app

import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mtgcompanion.app.data.social.PushNotifications
import com.mtgcompanion.app.ui.nav.MtgNavGraph
import com.mtgcompanion.app.ui.theme.MtgCompanionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Draw content behind the status/nav bars instead of the old opaque-bar look; the
        // SideEffect below keeps the system bar icons readable against whichever theme is active.
        enableEdgeToEdge()
        // Fullscreen app-wide: draw into the camera cutout band too (screens pad around the cutout
        // themselves — see MtgNavGraph) and keep the status/nav bars hidden.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()
        val app = application as MtgCompanionApplication
        handleAuthLink(intent)
        handleNotificationTap(intent)

        setContent {
            MtgCompanionTheme(settingsRepository = app.settingsRepository) {
                val view = LocalView.current
                val background = MaterialTheme.colorScheme.background
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, view)
                    val isLight = background.luminance() > 0.5f
                    controller.isAppearanceLightStatusBars = isLight
                    controller.isAppearanceLightNavigationBars = isLight
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    MtgNavGraph(
                        settingsRepository = app.settingsRepository,
                        collectionRepository = app.collectionRepository,
                        deckRepository = app.deckRepository,
                        driveImporter = app.driveImporter,
                        supabaseSync = app.supabaseSync,
                        updateManager = app.updateManager,
                        offlineCardRepository = app.offlineCardRepository,
                        playerProfileRepository = app.playerProfileRepository,
                        lifeCounterSettingsRepository = app.lifeCounterSettingsRepository,
                        artIndexRepository = app.artIndexRepository,
                        socialRepository = app.socialRepository,
                        pendingOpen = app.pendingOpen
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthLink(intent)
        handleNotificationTap(intent)
    }

    /** A tapped notification opens the screen it's about (the nav graph picks this up). */
    private fun handleNotificationTap(intent: Intent?) {
        val open = intent?.getStringExtra(PushNotifications.EXTRA_OPEN) ?: return
        intent.removeExtra(PushNotifications.EXTRA_OPEN) // not again on a configuration change
        (application as MtgCompanionApplication).pendingOpen.value = open
    }

    /** mtgcompanion://auth-callback#access_token=… from a confirmation email: sign in and say so. */
    private fun handleAuthLink(intent: Intent?) {
        val link = intent?.data ?: return
        if (link.scheme != "mtgcompanion" || link.host != "auth-callback") return
        intent.data = null // don't re-handle it on a configuration change
        val sync = (application as MtgCompanionApplication).supabaseSync
        lifecycleScope.launch {
            val isRecovery = (link.fragment ?: link.query).orEmpty().contains("type=recovery")
            val message = try {
                val email = sync.completeLinkSignIn(link).email
                if (isRecovery) "Signed in as $email — choose a new password."
                else "Email confirmed — signed in as $email. Syncing your decks…"
            } catch (e: Exception) {
                if (e is java.io.IOException) "Couldn't reach the server to finish signing in. Check your connection, then sign in from Settings."
                else e.message ?: "Couldn't finish signing in."
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    // Coming back to the app is when another device's edits are most likely waiting.
    override fun onResume() {
        super.onResume()
        (application as MtgCompanionApplication).supabaseSync.onAppResumed()
        // Friend requests or trades may have come in meanwhile: the badge checks.
        (application as MtgCompanionApplication).socialRepository.refreshInboxInBackground()
    }

    override fun onPause() {
        super.onPause()
        (application as MtgCompanionApplication).supabaseSync.onAppPaused()
    }

    // Dialogs, the keyboard and other apps' windows can bring the bars back while they have focus;
    // re-hide whenever this window regains it.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** A swipe in from an edge still shows the bars briefly, then they hide again on their own. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
