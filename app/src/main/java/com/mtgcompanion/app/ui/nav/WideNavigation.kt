package com.mtgcompanion.app.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.supabase.CloudSyncStatus
import com.mtgcompanion.app.data.supabase.SupabaseAccount
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.ArtImage
import com.mtgcompanion.app.ui.common.RAIL_WIDTH
import com.mtgcompanion.app.ui.common.SIDEBAR_WIDTH
import com.mtgcompanion.app.ui.common.pressScale
import com.mtgcompanion.app.ui.theme.BebasNumbers
import com.mtgcompanion.app.ui.theme.LocalAppColors

/** The top-level places the rail and sidebar link to. */
enum class NavDestination { HOME, SEARCH, SCAN, DECKS, COLLECTION, LIFE_COUNTER, RULES, SETTINGS }

/** The app mark: a gold rounded square with the numbers face's "M", as on the web app. */
@Composable
private fun AppMark(onClick: () -> Unit) {
    val app = LocalAppColors.current
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(app.accent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("M", fontFamily = BebasNumbers, fontSize = 22.sp, color = app.onAccent, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * Tablet navigation: a slim rail down the left edge with the same destinations as the phone's
 * bottom bar (Scan keeps its raised gold button), and Rules, Life counter and Settings at the foot.
 */
@Composable
fun NavRail(selected: NavDestination?, onNavigate: (NavDestination) -> Unit) {
    val app = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(RAIL_WIDTH)
            .fillMaxHeight()
            .background(app.surface.copy(alpha = 0.55f))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 18.dp)
    ) {
        AppMark { onNavigate(NavDestination.HOME) }
        Spacer(Modifier.height(18.dp))
        RailItem(Icons.Filled.Home, "Home", selected == NavDestination.HOME) { onNavigate(NavDestination.HOME) }
        RailItem(Icons.Filled.Search, "Search", selected == NavDestination.SEARCH) { onNavigate(NavDestination.SEARCH) }
        Box(Modifier.padding(vertical = 6.dp)) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .pressScale(interaction)
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(app.accent)
                    .clickable(interactionSource = interaction, indication = null) { onNavigate(NavDestination.SCAN) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Scan a card", tint = app.onAccent, modifier = Modifier.size(25.dp))
            }
        }
        RailItem(Icons.Filled.Style, "Decks", selected == NavDestination.DECKS) { onNavigate(NavDestination.DECKS) }
        RailItem(Icons.Filled.Collections, "Collection", selected == NavDestination.COLLECTION) { onNavigate(NavDestination.COLLECTION) }
        Spacer(Modifier.height(20.dp))
        RailItem(Icons.Filled.Favorite, "Life", false) { onNavigate(NavDestination.LIFE_COUNTER) }
        RailItem(Icons.Filled.MenuBook, "Rules", selected == NavDestination.RULES) { onNavigate(NavDestination.RULES) }
        RailItem(Icons.Filled.Settings, "Settings", selected == NavDestination.SETTINGS) { onNavigate(NavDestination.SETTINGS) }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val app = LocalAppColors.current
    val tint by animateColorAsState(if (selected) app.accent else app.textDim, label = "railTint")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp)
    ) {
        Box(
            Modifier.size(width = 54.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(if (selected) app.accentGlow else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) app.textPrimary else app.textDim, maxLines = 1)
    }
}

/**
 * Desktop-width navigation: a sidebar with the app name, every destination, your most recent decks
 * and your account's sync status — the web app's sidebar.
 */
@Composable
fun NavSidebar(
    selected: NavDestination?,
    selectedDeckId: String?,
    recentDecks: List<Deck>,
    account: SupabaseAccount?,
    accountsAvailable: Boolean,
    syncStatus: CloudSyncStatus,
    onNavigate: (NavDestination) -> Unit,
    onOpenDeck: (String) -> Unit
) {
    val app = LocalAppColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .width(SIDEBAR_WIDTH)
            .fillMaxHeight()
            .background(app.surface.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier.padding(start = 8.dp, bottom = 20.dp)
        ) {
            AppMark { onNavigate(NavDestination.HOME) }
            Text("MTG Companion", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SideItem(Icons.Filled.Home, "Home", selected == NavDestination.HOME) { onNavigate(NavDestination.HOME) }
            SideItem(Icons.Filled.Search, "Search", selected == NavDestination.SEARCH) { onNavigate(NavDestination.SEARCH) }
            SideItem(Icons.Filled.CameraAlt, "Scan a card", false, accent = true) { onNavigate(NavDestination.SCAN) }
            SideItem(Icons.Filled.Style, "Decks", selected == NavDestination.DECKS && selectedDeckId == null) { onNavigate(NavDestination.DECKS) }
            SideItem(Icons.Filled.Collections, "Collection", selected == NavDestination.COLLECTION) { onNavigate(NavDestination.COLLECTION) }
            SideItem(Icons.Filled.Favorite, "Life counter", false) { onNavigate(NavDestination.LIFE_COUNTER) }
            SideItem(Icons.Filled.MenuBook, "Rules", selected == NavDestination.RULES) { onNavigate(NavDestination.RULES) }

            if (recentDecks.isNotEmpty()) {
                Text(
                    "RECENT DECKS",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold),
                    color = app.textDim,
                    modifier = Modifier.padding(start = 12.dp, top = 22.dp, bottom = 8.dp)
                )
                recentDecks.forEach { deck ->
                    val active = deck.id == selectedDeckId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) app.surface else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onOpenDeck(deck.id) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        ArtImage(
                            model = deck.commander?.imageUrl.toArtCropUrl(),
                            seed = deck.name,
                            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                        )
                        Text(
                            deck.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) app.textPrimary else app.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        SideItem(Icons.Filled.Settings, "Settings", selected == NavDestination.SETTINGS) { onNavigate(NavDestination.SETTINGS) }
        if (accountsAvailable) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(app.surface)
                    .clickable { onNavigate(NavDestination.SETTINGS) }
                    .padding(12.dp)
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(app.accentGlow), contentAlignment = Alignment.Center) {
                    if (account != null) {
                        Text(account.email.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = app.accent)
                    } else {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = app.accent, modifier = Modifier.size(20.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(account?.email ?: "Sign in", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val (icon, label, tint) = when {
                        account == null -> Triple(Icons.Filled.Sync, "Sync your decks", app.textMuted)
                        syncStatus.syncing -> Triple(Icons.Filled.Sync, "Syncing…", app.textMuted)
                        syncStatus.failed -> Triple(Icons.Filled.CloudOff, "Not synced", app.warning)
                        else -> Triple(Icons.Filled.CloudDone, "Synced", app.success)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun SideItem(icon: ImageVector, label: String, selected: Boolean, accent: Boolean = false, onClick: () -> Unit) {
    val app = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) app.accentGlow else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (selected || accent) app.accent else app.textMuted, modifier = Modifier.size(21.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) app.textPrimary else app.textMuted)
    }
}
