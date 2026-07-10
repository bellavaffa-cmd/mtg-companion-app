package com.mtgcompanion.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.ui.collection.CollectionScreen
import com.mtgcompanion.app.ui.collection.CollectionViewModel
import com.mtgcompanion.app.ui.decks.DeckDetailScreen
import com.mtgcompanion.app.ui.decks.DeckDetailViewModel
import com.mtgcompanion.app.ui.decks.DecksScreen
import com.mtgcompanion.app.ui.decks.DecksViewModel
import com.mtgcompanion.app.ui.detail.CardDetailScreen
import com.mtgcompanion.app.ui.detail.CardDetailViewModel
import com.mtgcompanion.app.ui.scan.ScanScreen
import com.mtgcompanion.app.ui.scan.ScanViewModel
import com.mtgcompanion.app.ui.search.SearchScreen
import com.mtgcompanion.app.ui.search.SearchViewModel
import com.mtgcompanion.app.ui.settings.SettingsScreen
import com.mtgcompanion.app.ui.settings.SettingsViewModel
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private object Routes {
    const val SEARCH = "search"
    const val COLLECTION = "collection"
    const val DECKS = "decks"
    const val SETTINGS = "settings"
    const val SCAN = "scan"
    const val DETAIL = "detail/{cardName}"
    const val DECK_DETAIL = "deck/{deckId}"
    fun detail(cardName: String) = "detail/" + URLEncoder.encode(cardName, StandardCharsets.UTF_8.name())
    fun deckDetail(deckId: String) = "deck/$deckId"
}

private val bottomNavRoutes = setOf(Routes.SEARCH, Routes.COLLECTION, Routes.DECKS)

@Composable
fun MtgNavGraph(
    settingsRepository: SettingsRepository,
    collectionRepository: CollectionRepository,
    deckRepository: DeckRepository
) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            if (currentRoute in bottomNavRoutes) {
                MtgBottomBar(currentRoute = currentRoute, navController = navController)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SEARCH,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.SEARCH) {
                val viewModel: SearchViewModel = viewModel()
                SearchScreen(
                    viewModel = viewModel,
                    onCardClick = { card -> navController.navigate(Routes.detail(card.name)) },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    onScanClick = { navController.navigate(Routes.SCAN) }
                )
            }

            composable(Routes.COLLECTION) {
                val viewModel: CollectionViewModel = viewModel(
                    factory = CollectionViewModel.Factory(collectionRepository)
                )
                CollectionScreen(viewModel = viewModel)
            }

            composable(Routes.DECKS) {
                val viewModel: DecksViewModel = viewModel(
                    factory = DecksViewModel.Factory(deckRepository)
                )
                DecksScreen(
                    viewModel = viewModel,
                    onDeckClick = { deckId -> navController.navigate(Routes.deckDetail(deckId)) }
                )
            }

            composable(
                route = Routes.DECK_DETAIL,
                arguments = listOf(navArgument("deckId") { type = NavType.StringType })
            ) { backStackEntry ->
                val deckId = backStackEntry.arguments?.getString("deckId").orEmpty()
                val viewModel: DeckDetailViewModel = viewModel(
                    factory = DeckDetailViewModel.Factory(deckId, deckRepository)
                )
                DeckDetailScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            composable(Routes.SCAN) {
                val viewModel: ScanViewModel = viewModel()
                ScanScreen(
                    viewModel = viewModel,
                    onCardFound = { card ->
                        navController.navigate(Routes.detail(card.name)) {
                            popUpTo(Routes.SCAN) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("cardName") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedName = backStackEntry.arguments?.getString("cardName").orEmpty()
                val cardName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name())
                val viewModel: CardDetailViewModel = viewModel(
                    factory = CardDetailViewModel.Factory(cardName, settingsRepository, collectionRepository, deckRepository)
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
}

@Composable
private fun MtgBottomBar(currentRoute: String?, navController: NavHostController) {
    NavigationBar(containerColor = Surface) {
        NavigationBarItem(
            selected = currentRoute == Routes.SEARCH,
            onClick = { navController.navigateToTab(Routes.SEARCH) },
            icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            label = { Text("Search") },
            colors = bottomItemColors()
        )
        NavigationBarItem(
            selected = currentRoute == Routes.COLLECTION,
            onClick = { navController.navigateToTab(Routes.COLLECTION) },
            icon = { Icon(Icons.Filled.Collections, contentDescription = "Collection") },
            label = { Text("Collection") },
            colors = bottomItemColors()
        )
        NavigationBarItem(
            selected = currentRoute == Routes.DECKS,
            onClick = { navController.navigateToTab(Routes.DECKS) },
            icon = { Icon(Icons.Filled.Style, contentDescription = "Decks") },
            label = { Text("Decks") },
            colors = bottomItemColors()
        )
    }
}

@Composable
private fun bottomItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Gold,
    selectedTextColor = Gold,
    unselectedIconColor = TextDim,
    unselectedTextColor = TextDim,
    indicatorColor = Bg
)

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
