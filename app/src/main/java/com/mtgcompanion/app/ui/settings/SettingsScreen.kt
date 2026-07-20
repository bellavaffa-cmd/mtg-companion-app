package com.mtgcompanion.app.ui.settings

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.mtgcompanion.app.BuildConfig
import com.mtgcompanion.app.data.CardViewMode
import com.mtgcompanion.app.data.DriveSyncManager
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.offline.OfflineCardRepository
import com.mtgcompanion.app.update.UpdateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldDim
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    syncManager: DriveSyncManager,
    updateManager: UpdateManager,
    offlineCardRepository: OfflineCardRepository,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("SETTINGS", color = GoldLight, style = MaterialTheme.typography.labelLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DriveSyncSection(syncManager)

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(BorderColor))

            CardDisplaySection(settingsRepository)

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(BorderColor))

            OfflineSearchSection(offlineCardRepository)

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(BorderColor))

            AppUpdatesSection(updateManager)
        }
    }
}

@Composable
private fun CardDisplaySection(settingsRepository: SettingsRepository) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Text("Card Display".uppercase(), style = MaterialTheme.typography.titleMedium)
    Text(
        "Choose list (detailed rows) or grid (compact card art) for each tab.",
        style = MaterialTheme.typography.bodySmall
    )

    CardViewModeRow(
        label = "Search results",
        modeFlow = settingsRepository.searchViewMode,
        onSelect = { mode -> scope.launch { settingsRepository.setSearchViewMode(mode) } }
    )
    CardViewModeRow(
        label = "Binder cards",
        modeFlow = settingsRepository.collectionViewMode,
        onSelect = { mode -> scope.launch { settingsRepository.setCollectionViewMode(mode) } }
    )
    CardViewModeRow(
        label = "Deck cards",
        modeFlow = settingsRepository.deckViewMode,
        onSelect = { mode -> scope.launch { settingsRepository.setDeckViewMode(mode) } }
    )
}

@Composable
private fun CardViewModeRow(label: String, modeFlow: Flow<CardViewMode>, onSelect: (CardViewMode) -> Unit) {
    val mode by modeFlow.collectAsState(initial = CardViewMode.DEFAULT)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        FilterChip(
            selected = mode == CardViewMode.LIST,
            onClick = { onSelect(CardViewMode.LIST) },
            leadingIcon = { Icon(Icons.Filled.ViewList, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = { Text("List", style = MaterialTheme.typography.labelMedium) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Gold,
                selectedLabelColor = Bg,
                selectedLeadingIconColor = Bg,
                labelColor = TextMuted,
                iconColor = TextMuted,
                containerColor = Bg
            )
        )
        Box(modifier = Modifier.padding(start = 8.dp)) {
            FilterChip(
                selected = mode == CardViewMode.GRID,
                onClick = { onSelect(CardViewMode.GRID) },
                leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Grid", style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Gold,
                    selectedLabelColor = Bg,
                    selectedLeadingIconColor = Bg,
                    labelColor = TextMuted,
                    iconColor = TextMuted,
                    containerColor = Bg
                )
            )
        }
    }
}

@Composable
private fun OfflineSearchSection(offlineCardRepository: OfflineCardRepository) {
    val status by offlineCardRepository.status.collectAsState()

    Text("Offline Search".uppercase(), style = MaterialTheme.typography.titleMedium)
    Text(
        "Download the full card database (~40 MB) so you can search any card — not just the ones " +
            "you've viewed — without an internet connection.",
        style = MaterialTheme.typography.bodySmall
    )

    if (status.hasData) {
        val updated = if (status.updatedAt > 0) {
            DateUtils.getRelativeTimeSpanString(status.updatedAt).toString()
        } else {
            "recently"
        }
        Text(
            "${status.cardCount} cards • updated $updated",
            style = MaterialTheme.typography.labelMedium,
            color = GoldLight
        )
    }

    val buttonLabel = if (status.hasData) "UPDATE DATABASE" else "DOWNLOAD DATABASE"
    if (status.hasData) {
        OutlinedButton(
            onClick = { offlineCardRepository.downloadDatabase() },
            enabled = !status.downloading,
            shape = RoundedCornerShape(2.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight)
        ) { DownloadButtonContent(status.downloading, buttonLabel, Gold) }
    } else {
        Button(
            onClick = { offlineCardRepository.downloadDatabase() },
            enabled = !status.downloading,
            shape = RoundedCornerShape(2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
        ) { DownloadButtonContent(status.downloading, buttonLabel, Bg) }
    }

    status.message?.let {
        Text(it, color = Gold, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DownloadButtonContent(downloading: Boolean, label: String, spinnerColor: androidx.compose.ui.graphics.Color) {
    if (downloading) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = spinnerColor)
    } else {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (spinnerColor == Bg) Bg else GoldLight)
    }
}

@Composable
private fun AppUpdatesSection(updateManager: UpdateManager) {
    val state by updateManager.state.collectAsState()

    Text("App Updates".uppercase(), style = MaterialTheme.typography.titleMedium)
    Text(
        "You're on version ${BuildConfig.VERSION_NAME}. Updates are delivered straight from the " +
            "project's GitHub releases.",
        style = MaterialTheme.typography.bodySmall
    )

    val available = state.available
    if (available != null) {
        Text(
            "Version ${available.versionName} is available.",
            style = MaterialTheme.typography.bodySmall,
            color = GoldLight
        )
        Button(
            onClick = { updateManager.startUpdate() },
            enabled = !state.downloading,
            shape = RoundedCornerShape(2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
        ) {
            if (state.downloading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Bg)
            } else {
                Text("DOWNLOAD & INSTALL", style = MaterialTheme.typography.labelLarge, color = Bg)
            }
        }
    } else {
        OutlinedButton(
            onClick = { updateManager.checkForUpdate(silent = false) },
            enabled = !state.checking,
            shape = RoundedCornerShape(2.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight)
        ) {
            if (state.checking) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Gold)
            } else {
                Text("CHECK FOR UPDATES", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    state.message?.let {
        Text(it, color = Gold, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DriveSyncSection(syncManager: DriveSyncManager) {
    val status by syncManager.status.collectAsState()
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        val result0 = runCatching { task.getResult(ApiException::class.java) }
        syncManager.reportSignIn(result0.getOrNull(), result0.exceptionOrNull())
    }

    Text("Google Drive Sync".uppercase(), style = MaterialTheme.typography.titleMedium)
    Text(
        "Back up your decks and collection to your Google Drive and keep them in sync across " +
            "devices. Once connected, changes sync automatically.",
        style = MaterialTheme.typography.bodySmall
    )

    if (status.connectedEmail == null) {
        Button(
            onClick = { signInLauncher.launch(syncManager.signInClient().signInIntent) },
            shape = RoundedCornerShape(2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
        ) { Text("CONNECT GOOGLE DRIVE", style = MaterialTheme.typography.labelLarge, color = Bg) }
    } else {
        Text(
            "Connected as ${status.connectedEmail}",
            style = MaterialTheme.typography.bodySmall,
            color = GoldLight
        )
        if (status.lastSyncedAt > 0) {
            Text(
                "Last synced ${DateUtils.getRelativeTimeSpanString(status.lastSyncedAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = TextDim
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { syncManager.syncNow() },
                enabled = !status.syncing,
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) {
                if (status.syncing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Bg)
                } else {
                    Text("SYNC NOW", style = MaterialTheme.typography.labelLarge, color = Bg)
                }
            }
            OutlinedButton(
                onClick = { syncManager.signOut() },
                shape = RoundedCornerShape(2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight)
            ) { Text("DISCONNECT", style = MaterialTheme.typography.labelLarge) }
        }
    }
    status.message?.let {
        Text(it, color = Gold, style = MaterialTheme.typography.bodySmall)
    }
}
