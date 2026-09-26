package com.mtgcompanion.app.ui.nav

import com.mtgcompanion.app.ui.social.OfferSparesDialog
import com.mtgcompanion.app.data.social.TradeCard
import com.mtgcompanion.app.ui.collection.TagBinderScreen
import com.mtgcompanion.app.ui.collection.ValueHistoryScreen
import com.mtgcompanion.app.ui.collection.TagBinderViewModel
import com.mtgcompanion.app.ui.common.SyncPullResult
import com.mtgcompanion.app.ui.common.PullToSyncBox
import com.mtgcompanion.app.ui.common.CardZoomHost
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.mtgcompanion.app.ui.common.SetPasswordDialog
import com.mtgcompanion.app.data.supabase.SupabaseSync
import com.mtgcompanion.app.ui.common.LocalNavAnimatedScope
import com.mtgcompanion.app.ui.common.LayoutSize
import com.mtgcompanion.app.ui.common.LocalLayoutSize
import com.mtgcompanion.app.ui.common.currentLayoutSize
import com.mtgcompanion.app.ui.common.LocalSharedTransitionScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavGraphBuilder
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import com.mtgcompanion.app.ui.common.LocalSyncControl
import com.mtgcompanion.app.ui.common.SyncControl
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import com.mtgcompanion.app.ui.common.pressScale
import com.mtgcompanion.app.ui.common.popSpring
import com.mtgcompanion.app.ui.theme.LocalAppColors
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
import com.mtgcompanion.app.data.DriveImporter
import com.mtgcompanion.app.data.PlayerProfileRepository
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.social.ShareKind
import com.mtgcompanion.app.data.social.SharedSummary
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.social.FriendScreen
import com.mtgcompanion.app.ui.social.FriendsScreen
import com.mtgcompanion.app.ui.social.QrScanScreen
import com.mtgcompanion.app.ui.social.ShareCollectionDialog
import com.mtgcompanion.app.ui.social.ShareDialog
import com.mtgcompanion.app.ui.social.SharedCollectionScreen
import com.mtgcompanion.app.ui.social.SharedFriendsPage
import com.mtgcompanion.app.ui.social.FriendSharedScreen
import com.mtgcompanion.app.ui.social.SharedItemScreen
import com.mtgcompanion.app.ui.social.SharedSource
import com.mtgcompanion.app.ui.social.TradeComposerScreen
import com.mtgcompanion.app.ui.social.TradesScreen
import com.mtgcompanion.app.data.CardIndexRepository
import com.mtgcompanion.app.data.offline.OfflineCardRepository
import com.mtgcompanion.app.ui.collection.CollectionDetailScreen
import com.mtgcompanion.app.ui.collection.CollectionDetailViewModel
import com.mtgcompanion.app.ui.collection.CollectionsScreen
import com.mtgcompanion.app.ui.collection.CollectionsViewModel
import com.mtgcompanion.app.ui.decks.DeckDetailScreen
import com.mtgcompanion.app.ui.decks.DeckDetailViewModel
import com.mtgcompanion.app.ui.decks.DecksScreen
import com.mtgcompanion.app.ui.decks.DecksViewModel
import com.mtgcompanion.app.ui.decks.PreconsScreen
import com.mtgcompanion.app.ui.decks.PreconsViewModel
import com.mtgcompanion.app.ui.detail.CardDetailScreen
import com.mtgcompanion.app.ui.detail.CardDetailViewModel
import com.mtgcompanion.app.ui.home.HomeScreen
import com.mtgcompanion.app.ui.home.HomeViewModel
import com.mtgcompanion.app.ui.lifecounter.LifeCounterScreen
import com.mtgcompanion.app.ui.lifecounter.RemoteScreen
import com.mtgcompanion.app.ui.social.WhoHasItDialog
import com.mtgcompanion.app.ui.lifecounter.RemoteViewModel
import com.mtgcompanion.app.ui.lifecounter.LifeCounterSettingsRepository
import com.mtgcompanion.app.ui.lifecounter.LifeCounterViewModel
import com.mtgcompanion.app.ui.rules.RulesScreen
import com.mtgcompanion.app.ui.rules.RulingsRequest
import com.mtgcompanion.app.ui.rules.RulesViewModel
import com.mtgcompanion.app.ui.scan.ScanScreen
import com.mtgcompanion.app.ui.scan.ScanViewModel
import com.mtgcompanion.app.ui.search.SearchResultsScreen
import com.mtgcompanion.app.ui.search.SearchScreen
import com.mtgcompanion.app.ui.search.SearchViewModel
import com.mtgcompanion.app.ui.settings.SettingsScreen
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldGlow
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary
import com.mtgcompanion.app.update.UpdateInfo
import com.mtgcompanion.app.update.UpdateManager
import com.mtgcompanion.app.ui.badge.BadgeScreen
import com.mtgcompanion.app.ui.badge.BadgeViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SEARCH_RESULTS = "search_results"
    const val COLLECTION = "collection"
    const val DECKS = "decks"
    const val PRECONS = "precons"
    const val SETTINGS = "settings"
    const val SCAN = "scan"
    const val RULES = "rules"
    const val LIFE_COUNTER = "life_counter"
    const val VALUE_HISTORY = "value_history"
    const val FRIENDS = "friends"
    const val FRIEND = "friend/{userId}"
    const val TRADES = "trades"
    const val TRADE_NEW = "trade_new/{userId}"
    const val SHARED = "shared/{owner}/{kind}/{itemId}"
    const val SHARED_LINK = "shared_link/{token}"
    const val SHARED_COLLECTION = "shared_collection/{owner}"
    const val FRIEND_SHARED = "friend_shared/{owner}"
    fun friendShared(owner: String) = "friend_shared/$owner"
    fun sharedCollection(owner: String) = "shared_collection/$owner"
    const val QR_SCAN = "qr_scan"
    /** A player's phone as the remote for their seat at a life counter table. */
    const val REMOTE = "remote/{matchId}/{seat}"
    fun remote(matchId: String, seat: Int) = "remote/$matchId/$seat"
    fun friend(userId: String) = "friend/$userId"
    fun tradeNew(userId: String) = "trade_new/$userId"
    fun shared(owner: String, kind: String, itemId: String) = "shared/$owner/$kind/" + URLEncoder.encode(itemId, StandardCharsets.UTF_8.name())
    fun sharedLink(token: String) = "shared_link/$token"
    const val DETAIL = "detail/{cardName}"
    const val DECK_DETAIL = "deck/{deckId}"
    const val COLLECTION_DETAIL = "collection/{collectionId}"
    /** A tag's automatic binder: every owned card with that tag. */
    const val TAG_BINDER = "tag_binder/{tagId}"
    fun tagBinder(tagId: String) = "tag_binder/$tagId"
    fun detail(cardName: String) = "detail/" + URLEncoder.encode(cardName, StandardCharsets.UTF_8.name())
    fun deckDetail(deckId: String) = "deck/$deckId"
    /** Putting one of a deck's tokens onto an NFC e-paper badge. */
    const val TOKEN_BADGE = "token_badge/{deckId}"
    fun tokenBadge(deckId: String) = "token_badge/$deckId"
    fun collectionDetail(collectionId: String) = "collection/$collectionId"
}

// Routes that show the bottom nav bar. Scan is excluded so its camera runs full-screen (it has its
// own back button); Settings shows the bar so you can jump to another tab from it.
private val bottomNavRoutes = setOf(
    Routes.HOME, Routes.SEARCH, Routes.COLLECTION, Routes.DECKS, Routes.DECK_DETAIL, Routes.SETTINGS, Routes.RULES, Routes.FRIENDS
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MtgNavGraph(
    settingsRepository: SettingsRepository,
    collectionRepository: CollectionRepository,
    deckRepository: DeckRepository,
    driveImporter: DriveImporter,
    supabaseSync: SupabaseSync,
    updateManager: UpdateManager,
    offlineCardRepository: OfflineCardRepository,
    playerProfileRepository: PlayerProfileRepository,
    lifeCounterSettingsRepository: LifeCounterSettingsRepository,
    cardIndexRepository: CardIndexRepository,
    socialRepository: SocialRepository,
    pendingOpen: MutableStateFlow<String?>
) {
    val navController = rememberNavController()
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = backStackEntry?.destination?.route
    // Phone, tablet or desktop-width layout, following the window as it rotates or resizes.
    val layoutSize = currentLayoutSize()
    // Scan's camera and the life counter's table run edge to edge, without the rail or sidebar.
    val showWideNav = layoutSize.isWide && currentRoute != Routes.SCAN && currentRoute != Routes.LIFE_COUNTER && currentRoute != Routes.REMOTE

    // A tapped notification: open Friends, or Trades on top of it.
    val openRequest by pendingOpen.collectAsState()
    LaunchedEffect(openRequest) {
        val open = openRequest ?: return@LaunchedEffect
        pendingOpen.value = null
        // A price alert: the wishlist it's on.
        if (open.startsWith("binder:")) {
            navController.navigateToTab(Routes.COLLECTION)
            navController.navigate(Routes.collectionDetail(open.removePrefix("binder:"))) { launchSingleTop = true }
            return@LaunchedEffect
        }
        navController.navigateToTab(Routes.FRIENDS)
        if (open == "trades") navController.navigate(Routes.TRADES) { launchSingleTop = true }
    }

    // Check GitHub for a newer release once on launch; the dialog below shows if one is found.
    val updateState by updateManager.state.collectAsState()
    LaunchedEffect(Unit) { updateManager.checkForUpdate() }

    // Enlarged cards draw above everything here, bars included, so they can grow out of their thumbnails.
    CardZoomHost(onOpenRulings = { name ->
        RulingsRequest.card.value = name
        navController.navigateToTab(Routes.RULES)
    }) {
    Scaffold(
        containerColor = Bg,
        // The system bars are hidden app-wide (MainActivity), so these are normally zero — but a
        // camera cutout still needs clearing on regular screens. The life counter's tiles are meant
        // to run edge to edge, so it gets none.
        contentWindowInsets = if (currentRoute == Routes.LIFE_COUNTER || currentRoute == Routes.REMOTE) WindowInsets(0) else WindowInsets.systemBars.union(WindowInsets.displayCutout),
        bottomBar = {
            if (layoutSize == LayoutSize.PHONE && currentRoute in bottomNavRoutes) {
                MtgBottomBar(currentRoute = currentRoute, navController = navController)
            }
        }
    ) { padding ->
        CompositionLocalProvider(LocalLayoutSize provides layoutSize) {
        Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
        if (showWideNav) {
            val destination = when (currentRoute) {
                Routes.HOME, Routes.VALUE_HISTORY -> NavDestination.HOME
                Routes.SEARCH, Routes.SEARCH_RESULTS -> NavDestination.SEARCH
                Routes.DECKS, Routes.DECK_DETAIL, Routes.PRECONS -> NavDestination.DECKS
                Routes.COLLECTION, Routes.COLLECTION_DETAIL, Routes.FRIEND_SHARED, Routes.TAG_BINDER -> NavDestination.COLLECTION
                Routes.RULES -> NavDestination.RULES
                Routes.SETTINGS -> NavDestination.SETTINGS
                Routes.FRIENDS, Routes.FRIEND, Routes.TRADES, Routes.TRADE_NEW, Routes.SHARED, Routes.SHARED_COLLECTION -> NavDestination.FRIENDS
                else -> null
            }
            val onNavigate: (NavDestination) -> Unit = { target ->
                when (target) {
                    NavDestination.HOME -> navController.navigateToTab(Routes.HOME)
                    NavDestination.SEARCH -> navController.navigateToTab(Routes.SEARCH)
                    NavDestination.SCAN -> navController.navigateToTab(Routes.SCAN)
                    NavDestination.DECKS -> navController.navigateToTab(Routes.DECKS)
                    NavDestination.COLLECTION -> navController.navigateToTab(Routes.COLLECTION)
                    NavDestination.LIFE_COUNTER -> navController.navigate(Routes.LIFE_COUNTER)
                    NavDestination.RULES -> navController.navigateToTab(Routes.RULES)
                    NavDestination.FRIENDS -> navController.navigateToTab(Routes.FRIENDS)
                    NavDestination.SETTINGS -> navController.navigateToTab(Routes.SETTINGS)
                }
            }
            if (layoutSize == LayoutSize.DESKTOP) {
                val decks by deckRepository.decksFlow.collectAsState(initial = emptyList())
                val lastDeckId by settingsRepository.lastOpenedDeckId.collectAsState(initial = null)
                val account by supabaseSync.auth.account.collectAsState()
                val syncStatus by supabaseSync.status.collectAsState()
                NavSidebar(
                    selected = destination,
                    selectedDeckId = if (currentRoute == Routes.DECK_DETAIL) backStackEntry?.arguments?.getString("deckId") else null,
                    recentDecks = decks.sortedByDescending { it.id == lastDeckId }.take(6),
                    account = account,
                    accountsAvailable = supabaseSync.auth.configured,
                    syncStatus = syncStatus,
                    onNavigate = onNavigate,
                    onOpenDeck = { id -> navController.navigate(Routes.deckDetail(id)) { launchSingleTop = true } }
                )
            } else {
                NavRail(selected = destination, onNavigate = onNavigate)
            }
        }
        // The sync button in screen headers asks the pull-to-sync indicator to run.
        val syncRequests = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
        val syncAccount by supabaseSync.auth.account.collectAsState()
        val syncState by supabaseSync.status.collectAsState()
        val syncControl = if (!supabaseSync.auth.configured) null else SyncControl(
            signedIn = syncAccount != null,
            syncing = syncState.syncing,
            failed = syncState.failed,
            onSync = { syncRequests.tryEmit(Unit) },
            onSignIn = { navController.navigateToTab(Routes.SETTINGS) }
        )
        CompositionLocalProvider(LocalSyncControl provides syncControl) {
        // Pull down at the top of a library screen to sync decks and binders with the account.
        PullToSyncBox(
            enabled = supabaseSync.auth.configured && currentRoute in pullToSyncRoutes,
            onSync = { pullToSync(supabaseSync) },
            modifier = Modifier.weight(1f),
            requests = syncRequests
        ) {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            // The Row above already applied this padding (it clears the status bar and the bottom bar)
            // and marked those insets consumed — otherwise every screen's own Scaffold/TopAppBar pads
            // for the status bar a second time, leaving an empty band above each title.
            modifier = Modifier.fillMaxSize(),
            // A hard cut between screens reads as unfinished; a quick fade+slide gives every
            // push/pop (tab switches included) the same lightweight "moving deeper" feel.
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 } },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { it / 10 } }
        ) {
            destination(Routes.HOME) {
                val viewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(
                        deckRepository, collectionRepository, settingsRepository,
                        offlineCardRepository, driveImporter
                    )
                )
                HomeScreen(
                    viewModel = viewModel,
                    onOpenSearch = { navController.navigateToTab(Routes.SEARCH) },
                    onOpenCollection = { navController.navigateToTab(Routes.COLLECTION) },
                    onOpenDecks = { navController.navigateToTab(Routes.DECKS) },
                    onOpenScan = { navController.navigateToTab(Routes.SCAN) },
                    onOpenRules = { navController.navigateToTab(Routes.RULES) },
                    onOpenLifeCounter = { navController.navigate(Routes.LIFE_COUNTER) },
                    onOpenRemote = { matchId, seat -> navController.navigate(Routes.remote(matchId, seat)) },
                    onOpenSettings = { navController.navigateToTab(Routes.SETTINGS) },
                    onOpenValue = { navController.navigate(Routes.VALUE_HISTORY) },
                    onOpenDeck = { deckId -> navController.navigate(Routes.deckDetail(deckId)) },
                    onViewCard = { name -> navController.navigate(Routes.detail(name)) },
                    onOpenFriends = if (supabaseSync.auth.configured) ({ navController.navigateToTab(Routes.FRIENDS) }) else null,
                    friendsWaiting = socialRepository.inbox.collectAsState().value.total
                )
            }

            destination(Routes.VALUE_HISTORY) {
                ValueHistoryScreen(onBack = { navController.popBackStack() })
            }

            destination(Routes.SEARCH) {
                val viewModel: SearchViewModel = viewModel(
                    factory = SearchViewModel.Factory(offlineCardRepository, settingsRepository, collectionRepository, deckRepository)
                )
                SearchScreen(
                    viewModel = viewModel,
                    onCardClick = { card -> navController.navigate(Routes.detail(card.name)) },
                    onOpenResults = { navController.navigate(Routes.SEARCH_RESULTS) },
                    onOpenRules = { navController.navigateToTab(Routes.RULES) }
                )
            }

            destination(Routes.SEARCH_RESULTS) { backStackEntry ->
                // Shares the Search tab's ViewModel (via its still-live back-stack entry) so this
                // shows results for the same query/filters the user just built.
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(Routes.SEARCH) }
                val viewModel: SearchViewModel = viewModel(
                    viewModelStoreOwner = parentEntry,
                    factory = SearchViewModel.Factory(offlineCardRepository, settingsRepository, collectionRepository, deckRepository)
                )
                SearchResultsScreen(
                    viewModel = viewModel,
                    onCardClick = { card -> navController.navigate(Routes.detail(card.name)) },
                    onBack = { navController.popBackStack() }
                )
            }

            destination(Routes.TAG_BINDER, arguments = listOf(navArgument("tagId") { type = NavType.StringType })) { entry ->
                val tagId = entry.arguments?.getString("tagId").orEmpty()
                val viewModel: TagBinderViewModel = viewModel(key = "tag-$tagId", factory = TagBinderViewModel.Factory(tagId, collectionRepository, deckRepository))
                TagBinderScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenTag = { id -> navController.navigate(Routes.tagBinder(id)) },
                    onOpenDeck = { id -> navController.navigate(Routes.deckDetail(id)) }
                )
            }

            destination(Routes.COLLECTION) {
                val viewModel: CollectionsViewModel = viewModel(
                    factory = CollectionsViewModel.Factory(collectionRepository, deckRepository, settingsRepository)
                )
                var sharingAll by remember { mutableStateOf(false) }
                // Spares being offered in a trade, while the user picks who to.
                var offering by remember { mutableStateOf<List<TradeCard>?>(null) }
                // Friends' "See what friends share" asks for the Shared page.
                var openShared by remember { mutableStateOf(socialRepository.openSharedTab) }
                CollectionsScreen(
                    viewModel = viewModel,
                    onCollectionClick = { id -> navController.navigate(Routes.collectionDetail(id)) },
                    onViewDetails = { name -> navController.navigate(Routes.detail(name)) },
                    onShareCollection = if (supabaseSync.auth.configured) ({ sharingAll = true }) else null,
                    sharedPage = if (!supabaseSync.auth.configured) null else ({
                        SharedFriendsPage(
                            social = socialRepository,
                            collectionRepository = collectionRepository,
                            onSignIn = { navController.navigateToTab(Routes.SETTINGS) },
                            onOpenFriend = { owner -> navController.navigate(Routes.friendShared(owner)) },
                            onOpenItem = { owner, kind, id -> navController.navigate(Routes.shared(owner, kind.wire, id)) },
                            onAddFriend = { navController.navigateToTab(Routes.FRIENDS) }
                        )
                    }),
                    openShared = openShared,
                    onSharedOpened = { openShared = false; socialRepository.openSharedTab = false },
                    onOpenTag = { id -> navController.navigate(Routes.tagBinder(id)) },
                    onOfferSpares = if (supabaseSync.auth.configured) ({ cards -> offering = cards }) else null
                )
                offering?.let { cards ->
                    OfferSparesDialog(
                        social = socialRepository,
                        count = cards.size,
                        onPick = { friendId ->
                            offering = null
                            // The trade opens with the spares already on the user's side.
                            socialRepository.draft = SocialRepository.TradeDraft(to = friendId, give = cards)
                            navController.navigate(Routes.tradeNew(friendId))
                        },
                        onAddFriend = { offering = null; navController.navigateToTab(Routes.FRIENDS) },
                        onDismiss = { offering = null }
                    )
                }
                if (sharingAll) {
                    ShareCollectionDialog(
                        social = socialRepository,
                        sync = supabaseSync,
                        onOpenFriends = { navController.navigateToTab(Routes.FRIENDS) },
                        onClose = { sharingAll = false }
                    )
                }
            }

            destination(
                route = Routes.COLLECTION_DETAIL,
                arguments = listOf(navArgument("collectionId") { type = NavType.StringType })
            ) { backStackEntry ->
                val collectionId = backStackEntry.arguments?.getString("collectionId").orEmpty()
                val viewModel: CollectionDetailViewModel = viewModel(
                    factory = CollectionDetailViewModel.Factory(collectionId, collectionRepository, deckRepository, settingsRepository)
                )
                var sharing by remember { mutableStateOf(false) }
                val binder by viewModel.collection.collectAsState()
                CollectionDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onViewDetails = { name -> navController.navigate(Routes.detail(name)) },
                    onShare = if (supabaseSync.auth.configured) ({ sharing = true }) else null
                )
                if (sharing) {
                    ShareDialog(
                        social = socialRepository,
                        sync = supabaseSync,
                        kind = ShareKind.COLLECTION,
                        itemId = collectionId,
                        name = binder?.name ?: "Binder",
                        onOpenFriends = { navController.navigateToTab(Routes.FRIENDS) },
                        onClose = { sharing = false }
                    )
                }
            }

            destination(Routes.DECKS) {
                val viewModel: DecksViewModel = viewModel(
                    factory = DecksViewModel.Factory(deckRepository)
                )
                DecksScreen(
                    viewModel = viewModel,
                    onDeckClick = { deckId -> navController.navigate(Routes.deckDetail(deckId)) },
                    onBrowsePrecons = { navController.navigate(Routes.PRECONS) }
                )
            }

            destination(Routes.PRECONS) {
                val viewModel: PreconsViewModel = viewModel(
                    factory = PreconsViewModel.Factory(deckRepository)
                )
                PreconsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onImported = { deckId ->
                        navController.navigate(Routes.deckDetail(deckId)) { popUpTo(Routes.DECKS) }
                    }
                )
            }

            destination(
                route = Routes.DECK_DETAIL,
                arguments = listOf(navArgument("deckId") { type = NavType.StringType })
            ) { backStackEntry ->
                val deckId = backStackEntry.arguments?.getString("deckId").orEmpty()
                val viewModel: DeckDetailViewModel = viewModel(
                    factory = DeckDetailViewModel.Factory(deckId, deckRepository, collectionRepository, settingsRepository)
                )
                // Remembered for Home's "continue where you left off" tile.
                LaunchedEffect(deckId) { settingsRepository.setLastOpenedDeckId(deckId) }
                var sharing by remember { mutableStateOf(false) }
                var whoHas by remember { mutableStateOf<List<String>?>(null) }
                val sharedDeck by viewModel.deck.collectAsState()
                DeckDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onViewDetails = { name -> navController.navigate(Routes.detail(name)) },
                    onShare = if (supabaseSync.auth.configured) ({ sharing = true }) else null,
                    onWhoHasIt = if (supabaseSync.auth.configured) ({ names -> whoHas = names }) else null,
                    onOpenDeck = { id -> navController.navigate(Routes.deckDetail(id)) },
                    onOpenBadge = { navController.navigate(Routes.tokenBadge(deckId)) }
                )
                whoHas?.let { names ->
                    WhoHasItDialog(
                        social = socialRepository,
                        names = names,
                        onAsk = { owner -> whoHas = null; navController.navigate(Routes.tradeNew(owner)) },
                        onDismiss = { whoHas = null }
                    )
                }
                if (sharing) {
                    ShareDialog(
                        social = socialRepository,
                        sync = supabaseSync,
                        kind = ShareKind.DECK,
                        itemId = deckId,
                        name = sharedDeck?.name ?: "Deck",
                        onOpenFriends = { navController.navigateToTab(Routes.FRIENDS) },
                        onClose = { sharing = false }
                    )
                }
            }

            destination(Routes.SCAN) {
                val viewModel: ScanViewModel = viewModel(
                    factory = ScanViewModel.Factory(
                        LocalContext.current.applicationContext,
                        collectionRepository,
                        deckRepository,
                        cardIndexRepository,
                        settingsRepository
                    )
                )
                ScanScreen(
                    viewModel = viewModel,
                    social = socialRepository,
                    onBack = { navController.popBackStack() },
                    onCardClick = { name -> navController.navigate(Routes.detail(name)) },
                    onOpenSharedLink = { token -> navController.navigate(Routes.sharedLink(token)) },
                    onOpenRemote = { matchId, seat -> navController.navigate(Routes.remote(matchId, seat)) }
                )
            }

            destination(
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
                    onBack = { navController.popBackStack() },
                    onViewDetails = { name -> navController.navigate(Routes.detail(name)) }
                )
            }

            destination(
                route = Routes.TOKEN_BADGE,
                arguments = listOf(navArgument("deckId") { type = NavType.StringType })
            ) { backStackEntry ->
                val deckId = backStackEntry.arguments?.getString("deckId").orEmpty()
                val viewModel: BadgeViewModel = viewModel(factory = BadgeViewModel.Factory(deckId, deckRepository))
                BadgeScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            destination(Routes.RULES) {
                val viewModel: RulesViewModel = viewModel()
                RulesScreen(viewModel = viewModel)
            }

            destination(Routes.LIFE_COUNTER) {
                val viewModel: LifeCounterViewModel = viewModel(factory = LifeCounterViewModel.Factory(playerProfileRepository, lifeCounterSettingsRepository, socialRepository, deckRepository))
                LifeCounterScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            destination(Routes.SETTINGS) {
                SettingsScreen(
                    driveImporter = driveImporter,
                    supabaseSync = supabaseSync,
                    updateManager = updateManager,
                    offlineCardRepository = offlineCardRepository,
                    cardIndexRepository = cardIndexRepository,
                    settingsRepository = settingsRepository,
                    onBack = { navController.popBackStack() },
                    onOpenFriends = { navController.navigateToTab(Routes.FRIENDS) }
                )
            }

            val signIn = { navController.navigateToTab(Routes.SETTINGS) }
            val openShared = { s: SharedSummary -> navController.navigate(Routes.shared(s.owner, s.kind.wire, s.itemId)) }

            destination(Routes.FRIENDS) {
                FriendsScreen(
                    social = socialRepository,
                    onBack = { navController.popBackStack() },
                    onSignIn = signIn,
                    onScanQr = { navController.navigate(Routes.QR_SCAN) },
                    onOpenFriend = { id -> navController.navigate(Routes.friend(id)) },
                    onOpenShared = openShared,
                    onOpenSharedCollection = { owner -> navController.navigate(Routes.sharedCollection(owner)) },
                    onOpenTrades = { navController.navigate(Routes.TRADES) },
                    onOpenSharedTab = { socialRepository.openSharedTab = true; navController.navigateToTab(Routes.COLLECTION) }
                )
            }

            destination(Routes.FRIEND, arguments = listOf(navArgument("userId") { type = NavType.StringType })) { entry ->
                FriendScreen(
                    social = socialRepository,
                    sync = supabaseSync,
                    collectionRepository = collectionRepository,
                    deckRepository = deckRepository,
                    friendId = entry.arguments?.getString("userId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onSignIn = signIn,
                    onOpenShared = openShared,
                    onOpenSharedCollection = { owner -> navController.navigate(Routes.sharedCollection(owner)) },
                    onProposeTrade = { id -> navController.navigate(Routes.tradeNew(id)) }
                )
            }

            destination(Routes.FRIEND_SHARED, arguments = listOf(navArgument("owner") { type = NavType.StringType })) { entry ->
                val owner = entry.arguments?.getString("owner").orEmpty()
                FriendSharedScreen(
                    social = socialRepository,
                    collectionRepository = collectionRepository,
                    owner = owner,
                    onBack = { navController.popBackStack() },
                    onSignIn = signIn,
                    onOpenCollection = { navController.navigate(Routes.sharedCollection(it)) },
                    onOpenItem = { o, kind, id -> navController.navigate(Routes.shared(o, kind.wire, id)) },
                    onProposeTrade = { id -> navController.navigate(Routes.tradeNew(id)) }
                )
            }

            destination(Routes.SHARED_COLLECTION, arguments = listOf(navArgument("owner") { type = NavType.StringType })) { entry ->
                val owner = entry.arguments?.getString("owner").orEmpty()
                SharedCollectionScreen(
                    social = socialRepository,
                    owner = owner,
                    onBack = { navController.popBackStack() },
                    onOpenBinder = { id -> navController.navigate(Routes.shared(owner, ShareKind.COLLECTION.wire, id)) },
                    onProposeTrade = { id -> navController.navigate(Routes.tradeNew(id)) }
                )
            }

            destination(Routes.TRADES) {
                TradesScreen(
                    social = socialRepository,
                    collectionRepository = collectionRepository,
                    onBack = { navController.popBackStack() },
                    onSignIn = signIn,
                    onCounter = { id -> navController.navigate(Routes.tradeNew(id)) }
                )
            }

            destination(Routes.TRADE_NEW, arguments = listOf(navArgument("userId") { type = NavType.StringType })) { entry ->
                TradeComposerScreen(
                    social = socialRepository,
                    collectionRepository = collectionRepository,
                    friendId = entry.arguments?.getString("userId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onSignIn = signIn,
                    onSent = {
                        navController.navigate(Routes.TRADES) { popUpTo(Routes.FRIENDS) }
                    }
                )
            }

            destination(
                Routes.SHARED,
                arguments = listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("itemId") { type = NavType.StringType }
                )
            ) { entry ->
                val args = entry.arguments
                SharedItemScreen(
                    social = socialRepository,
                    deckRepository = deckRepository,
                    source = SharedSource.FromFriend(
                        owner = args?.getString("owner").orEmpty(),
                        kind = ShareKind.of(args?.getString("kind").orEmpty()),
                        itemId = URLDecoder.decode(args?.getString("itemId").orEmpty(), StandardCharsets.UTF_8.name())
                    ),
                    onBack = { navController.popBackStack() },
                    onOpenDeck = { id -> navController.navigate(Routes.deckDetail(id)) },
                    onProposeTrade = { id -> navController.navigate(Routes.tradeNew(id)) }
                )
            }

            destination(Routes.SHARED_LINK, arguments = listOf(navArgument("token") { type = NavType.StringType })) { entry ->
                SharedItemScreen(
                    social = socialRepository,
                    deckRepository = deckRepository,
                    source = SharedSource.FromLink(entry.arguments?.getString("token").orEmpty()),
                    onBack = { navController.popBackStack() },
                    onOpenDeck = { id -> navController.navigate(Routes.deckDetail(id)) },
                    onProposeTrade = { id -> navController.navigate(Routes.tradeNew(id)) }
                )
            }

            destination(Routes.QR_SCAN) {
                QrScanScreen(
                    social = socialRepository,
                    onBack = { navController.popBackStack() },
                    onSignIn = signIn,
                    onOpenSharedLink = { token -> navController.navigate(Routes.sharedLink(token)) { popUpTo(Routes.QR_SCAN) { inclusive = true } } },
                    onOpenRemote = { matchId, seat -> navController.navigate(Routes.remote(matchId, seat)) { popUpTo(Routes.QR_SCAN) { inclusive = true } } }
                )
            }

            destination(
                Routes.REMOTE,
                arguments = listOf(
                    navArgument("matchId") { type = NavType.StringType },
                    navArgument("seat") { type = NavType.IntType }
                )
            ) { entry ->
                val context = LocalContext.current
                val matchId = entry.arguments?.getString("matchId").orEmpty()
                val seat = entry.arguments?.getInt("seat") ?: 0
                val viewModel: RemoteViewModel = viewModel(factory = RemoteViewModel.Factory(socialRepository, deckRepository, context, matchId, seat))
                RemoteScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }
        }
        }
        }
        }
        }
        }
    }
    }

    val passwordRecovery by supabaseSync.auth.passwordRecovery.collectAsState()
    if (passwordRecovery) {
        val context = LocalContext.current
        SetPasswordDialog(
            auth = supabaseSync.auth,
            title = "Choose a new password",
            explanation = "You're signed in from the reset link. Set a new password to use next time.",
            onDismiss = { supabaseSync.auth.dismissPasswordRecovery() },
            onDone = { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
        )
    }

    val update = updateState.available
    if (update != null && !updateState.dismissed) {
        UpdateDialog(
            info = update,
            downloading = updateState.downloading,
            downloadProgress = updateState.downloadProgress,
            installing = updateState.installing,
            message = updateState.message,
            onUpdate = { updateManager.startUpdate() },
            onDismiss = { updateManager.dismiss() }
        )
    }
}

@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    downloading: Boolean,
    downloadProgress: Float?,
    installing: Boolean,
    message: String?,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!downloading && !installing) onDismiss() },
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
                    if (downloadProgress != null) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            color = Gold,
                            trackColor = BorderColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Downloading… ${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    } else {
                        LinearProgressIndicator(color = Gold, trackColor = BorderColor, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text("Downloading…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                } else if (installing && message != null) {
                    // Brief handoff to the installer — the actual install screen after this is the
                    // system package installer's own UI, outside the app's control.
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Gold)
                        Spacer(Modifier.width(10.dp))
                        Text(message, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                } else if (message != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall, color = Color(0xFFD3402F))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                enabled = !downloading && !installing,
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Update", color = Bg) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !downloading && !installing) { Text("Later", color = TextMuted) }
        }
    )
}

/** A nav destination that also exposes its enter/exit animation scope for [sharedArt] transitions. */
/** Library screens where pulling down past the top syncs with the account. */
private val pullToSyncRoutes = setOf(
    Routes.HOME, Routes.DECKS, Routes.DECK_DETAIL, Routes.COLLECTION, Routes.COLLECTION_DETAIL, Routes.SETTINGS
)

private suspend fun pullToSync(sync: SupabaseSync): SyncPullResult {
    val status = sync.refresh()
        ?: return SyncPullResult(SyncPullResult.Kind.SIGNED_OUT, "Sign in under Settings to sync")
    return when {
        status.failed && status.message?.startsWith("Offline") == true ->
            SyncPullResult(SyncPullResult.Kind.FAILED, "You're offline")
        status.failed -> SyncPullResult(SyncPullResult.Kind.FAILED, status.message ?: "Couldn't sync")
        status.pulled > 0 ->
            SyncPullResult(SyncPullResult.Kind.OK, "${status.pulled} ${if (status.pulled == 1) "change" else "changes"} from your other devices")
        status.pushed > 0 -> SyncPullResult(SyncPullResult.Kind.OK, "Synced")
        else -> SyncPullResult(SyncPullResult.Kind.OK, "Up to date")
    }
}

private fun NavGraphBuilder.destination(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit
) {
    composable(route, arguments) { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
    }
}

/**
 * Floating bottom bar: four destinations around a raised Scan button, with a highlight pill that
 * springs to the selected tab. Rules moved out of the bar (it's on Home and in Search's toolbar).
 */
@Composable
private fun MtgBottomBar(currentRoute: String?, navController: NavHostController) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val selected = when (currentRoute) {
        Routes.HOME -> 0
        Routes.SEARCH -> 1
        Routes.DECKS, Routes.DECK_DETAIL -> 3
        Routes.COLLECTION -> 4
        else -> -1
    }
    fun go(route: String) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); navController.navigateToTab(route) }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Bg)
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(colors.surface)
        ) {
            val slot = maxWidth / 5
            val pillX by animateDpAsState(slot * selected.coerceAtLeast(0) + (slot - 52.dp) / 2, popSpring(), label = "barPill")
            if (selected >= 0) {
                Box(Modifier.offset(x = pillX, y = 8.dp).size(width = 52.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(colors.accentGlow))
            }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                BarItem(Icons.Filled.Home, "Home", selected == 0) { go(Routes.HOME) }
                BarItem(Icons.Filled.Search, "Search", selected == 1) { go(Routes.SEARCH) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .pressScale(interaction)
                            .size(54.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.accent)
                            .clickable(interactionSource = interaction, indication = null) { go(Routes.SCAN) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "Scan a card", tint = colors.onAccent, modifier = Modifier.size(26.dp))
                    }
                }
                BarItem(Icons.Filled.Style, "Decks", selected == 3) { go(Routes.DECKS) }
                BarItem(Icons.Filled.Collections, "Collection", selected == 4) { go(Routes.COLLECTION) }
            }
        }
    }
}

@Composable
private fun RowScope.BarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val tint by animateColorAsState(if (selected) colors.accent else colors.textDim, label = "barTint")
    val labelColor by animateColorAsState(if (selected) colors.textPrimary else colors.textDim, label = "barLabel")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The top-level screens: each keeps its own history, restored when you come back to it. Settings is
 * one too — opened on top of another tab instead, it became part of that tab's history, and tapping
 * that tab brought Settings back rather than the tab (only restarting the app got out of it).
 */
private val tabRoutes = setOf(
    Routes.HOME, Routes.SEARCH, Routes.SCAN, Routes.DECKS, Routes.COLLECTION, Routes.RULES, Routes.FRIENDS, Routes.SETTINGS
)

private fun NavHostController.navigateToTab(route: String) {
    // The tab you're already in goes back to its first screen (a deck's page -> the deck list),
    // rather than restoring where it was.
    val currentTab = currentBackStack.value.lastOrNull { it.destination.route in tabRoutes }?.destination?.route
    if (route == currentTab) {
        popBackStack(route, inclusive = false) // nothing to pop when already there
        return
    }
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
