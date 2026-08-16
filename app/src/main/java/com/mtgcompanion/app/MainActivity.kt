package com.mtgcompanion.app

import android.os.Bundle
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
import com.mtgcompanion.app.ui.nav.MtgNavGraph
import com.mtgcompanion.app.ui.theme.MtgCompanionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Draw content behind the status/nav bars instead of the old opaque-bar look; the
        // SideEffect below keeps the system bar icons readable against whichever theme is active.
        enableEdgeToEdge()
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
                        playerProfileRepository = app.playerProfileRepository
                    )
                }
            }
        }
    }
}
