package com.mtgcompanion.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.ui.detail.CardDetailScreen
import com.mtgcompanion.app.ui.detail.CardDetailViewModel
import com.mtgcompanion.app.ui.search.SearchScreen
import com.mtgcompanion.app.ui.search.SearchViewModel
import com.mtgcompanion.app.ui.settings.SettingsScreen
import com.mtgcompanion.app.ui.settings.SettingsViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private object Routes {
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{cardName}"
    fun detail(cardName: String) = "detail/" + URLEncoder.encode(cardName, StandardCharsets.UTF_8.name())
}

@Composable
fun MtgNavGraph(settingsRepository: SettingsRepository) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.SEARCH) {
        composable(Routes.SEARCH) {
            val viewModel: SearchViewModel = viewModel()
            SearchScreen(
                viewModel = viewModel,
                onCardClick = { card -> navController.navigate(Routes.detail(card.name)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("cardName") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedName = backStackEntry.arguments?.getString("cardName").orEmpty()
            val cardName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name())
            val viewModel: CardDetailViewModel = viewModel(
                factory = CardDetailViewModel.Factory(cardName, settingsRepository)
            )
            CardDetailScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(settingsRepository)
            )
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
