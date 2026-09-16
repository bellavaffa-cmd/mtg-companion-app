package com.mtgcompanion.app

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
                        driveSyncManager = app.driveSyncManager,
                        updateManager = app.updateManager,
                        offlineCardRepository = app.offlineCardRepository,
                        playerProfileRepository = app.playerProfileRepository,
                        lifeCounterSettingsRepository = app.lifeCounterSettingsRepository,
                        artIndexRepository = app.artIndexRepository
                    )
                }
            }
        }
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
