package com.mtgcompanion.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.ui.nav.MtgNavGraph
import com.mtgcompanion.app.ui.theme.MtgCompanionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = SettingsRepository(applicationContext)
        val collectionRepository = CollectionRepository(applicationContext)
        val deckRepository = DeckRepository(applicationContext)

        setContent {
            MtgCompanionTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MtgNavGraph(
                        settingsRepository = settingsRepository,
                        collectionRepository = collectionRepository,
                        deckRepository = deckRepository
                    )
                }
            }
        }
    }
}
