package com.mtgcompanion.app.ui.nav

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.mtgcompanion.app.data.DriveSyncManager
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.offline.OfflineCardRepository
import com.mtgcompanion.app.ui.collection.CollectionDetailScreen
import com.mtgcompanion.app.ui.collection.CollectionDetailViewModel
import com.mtgcompanion.app.ui.collection.CollectionsScreen
import com.mtgcompanion.app.ui.collection.CollectionsViewModel
import com.mtgcompanion.app.ui.decks.DeckDetailScreen
import com.mtgcompanion.app.ui.decks.DeckDetailViewModel
import com.mtgcompanion.app.ui.decks.DecksScreen
import com.mtgcompanion.app.ui.decks.DecksViewModel
import com.mtgcompanion.app.ui.detail.CardDetailScreen
import com.mtgcompanion.app.ui.detail.CardDetailViewModel
import com.mtgcompanion.app.ui.home.HomeScreen
import com.mtgcompanion.app.ui.home.HomeViewModel
import com.mtgcompanion.app.ui.rules.RulesScreen
import com.mtgcompanion.app.ui.rules.RulesViewModel
import com.mtgcompanion.app.ui.scan.ScanScreen
import com.mtgcompanion.app.ui.scan.ScanViewModel
import com.mtgcompanion.app.ui.search.SearchScreen
import com.mtgcompanion.app.ui.search.SearchViewModel
import com.mtgcompanion.app.ui.settings.SettingsScreen
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary
import com.mtgcompanion.app.update.UpdateInfo
import com.mtgcompanion.app.update.UpdateManager
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val COLLECTION = "collection"
    const val DECKS = "decks"
    const val SETTINGS = "settings"
    const val SCAN = "scan"
    const val RULES = "rules"
    const val DETAIL = "detail/{cardName}"
    const val DECK_DETAIL = "deck/{deckId}"
    const val COLLECTION_DETAIL = "collection/{collectionId}"
    fun detail(cardName: String) = "detail/" + URLEncoder.encode(cardName, StandardCharsets.UTF_8.name())
    fun deckDetail(deckId: String) = "deck/$deckId"
    fun collectionDetail(collectionId: String) = "collection/$collectionId"
}

// Routes that show the bottom nav bar. Scan is excluded so its camera runs full-screen (it has its
// own back button); Settings shows the bar so you can jump to another tab from it.
private val bottomNavRoutes = setOf(
    Routes.HOME, Routes.SEARCH, Routes.COLLECTION, Routes.DECKS, Routes.DECK_DETAIL, Routes.SETTINGS, Routes.RULES
)

@Composable
fun MtgNavGraph(
    settingsRepository: SettingsRepository,
    collectionRepository: CollectionRepository,
    deckRepository: DeckRepository,
    driveSyncManager: DriveSyncManager,
    updateManager: UpdateManager,
    offlineCardRepository: OfflineCardRepository
) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // Check GitHub for a newer release once on launch; the dialog below shows if one is found.
    val updateState by updateManager.state.collectAsState()
    LaunchedEffect(Unit) { updateManager.checkForUpdate() }

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
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                val viewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(deckRepository, collectionRepository)
                )
                HomeScreen(
                    viewModel = viewModel,
                    onOpenSearch = { navController.navigateToTab(Routes.SEARCH) },
                    onOpenCollection = { navController.navigateToTab(Routes.COLLECTION) },
                    onOpenDecks = { navController.navigateToTab(Routes.DECKS) },
                    onOpenScan = { navController.navigateToTab(Routes.SCAN) },
                    onOpenRules = { navController.navigateToTab(Routes.RULES) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }

            composable(Routes.SEARCH) {
                val viewModel: SearchViewModel = viewModel(
                    factory = SearchViewModel.Factory(offlineCardRepository)
                )
                SearchScreen(
                    viewModel = viewModel,
                    onCardClick = { card -> navController.navigate(Routes.detail(card.name)) }
                )
            }

            composable(Routes.COLLECTION) {
                val viewModel: CollectionsViewModel = viewModel(
                    factory = CollectionsViewModel.Factory(collectionRepository, deckRepository)
                )
                CollectionsScreen(
                    viewModel = viewModel,
                    onCollectionClick = { id -> navController.navigate(Routes.collectionDetail(id)) }
                )
            }

            composable(
                route = Routes.COLLECTION_DETAIL,
                arguments = listOf(navArgument("collectionId") { type = NavType.StringType })
            ) { backStackEntry ->
                val collectionId = backStackEntry.arguments?.getString("collectionId").orEmpty()
                val viewModel: CollectionDetailViewModel = viewModel(
                    factory = CollectionDetailViewModel.Factory(collectionId, collectionRepository, deckRepository)
                )
                CollectionDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
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
                    factory = DeckDetailViewModel.Factory(deckId, deckRepository, collectionRepository)
                )
                DeckDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SCAN) {
                val viewModel: ScanViewModel = viewModel(
                    factory = ScanViewModel.Factory(collectionRepository, deckRepository)
                )
                ScanScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onCardClick = { name -> navController.navigate(Routes.detail(name)) }
                )
            }

            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("cardName") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedName = backStackEntry.arguments?.getString("cardName").orEmpty()
                val cardName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name())
                val viewModel: CardDetailViewModel = viewModel(
                    factory = CardDetailViewModel.Factory(
                        cardName, settingsRepository, collectionRepository, deckRepository, offlineCardRepository
                    )
                )
                CardDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.RULES) {
                val viewModel: RulesViewModel = viewModel()
                RulesScreen(viewModel = viewModel)
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    syncManager = driveSyncManager,
                    updateManager = updateManager,
                    offlineCardRepository = offlineCardRepository,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    val update = updateState.available
    if (update != null && !updateState.dismissed) {
        UpdateDialog(
            info = update,
            downloading = updateState.downloading,
            onUpdate = { updateManager.startUpdate() },
            onDismiss = { updateManager.dismiss() }
        )
    }
}

@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    downloading: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        containerColor = Surface,
        title = { Text("Update available", color = GoldLight) },
        text = {
            Column {
                Text(
                    "Version ${info.versionName} is available. Download and install it now?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        info.notes.trim().take(300),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (downloading) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Gold)
                        Spacer(Modifier.width(10.dp))
                        Text("Downloading…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                enabled = !downloading,
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("UPDATE", color = Bg) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !downloading) { Text("LATER", color = TextMuted) }
        }
    )
}

@Composable
private fun MtgBottomBar(currentRoute: String?, navController: NavHostController) {
    NavigationBar(containerColor = Surface) {
        BarItem(Icons.Filled.Home, "Home", currentRoute == Routes.HOME) {
            navController.navigateToTab(Routes.HOME)
        }
        BarItem(Icons.Filled.Search, "Search", currentRoute == Routes.SEARCH) {
            navController.navigateToTab(Routes.SEARCH)
        }
        BarItem(Icons.Filled.Collections, "Collection", currentRoute == Routes.COLLECTION) {
            navController.navigateToTab(Routes.COLLECTION)
        }
        BarItem(
            Icons.Filled.Style, "Decks",
            currentRoute == Routes.DECKS || currentRoute == Routes.DECK_DETAIL
        ) {
            navController.navigateToTab(Routes.DECKS)
        }
        BarItem(Icons.Filled.CameraAlt, "Scan", currentRoute == Routes.SCAN) {
            navController.navigateToTab(Routes.SCAN)
        }
        BarItem(Icons.Filled.MenuBook, "Rules", currentRoute == Routes.RULES) {
            navController.navigateToTab(Routes.RULES)
        }
    }
}

@Composable
private fun RowScope.BarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        // Small single-line label so all five fit without wrapping.
        label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = bottomItemColors()
    )
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
