package com.mtgcompanion.app.ui.settings

import com.mtgcompanion.app.ui.common.SetPasswordDialog
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.data.supabase.SupabaseSync
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.ui.theme.OnGold
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.mtgcompanion.app.BuildConfig
import com.mtgcompanion.app.data.AccentTheme
import com.mtgcompanion.app.data.AppBrightness
import com.mtgcompanion.app.data.CardViewMode
import com.mtgcompanion.app.data.DriveSyncManager
import com.mtgcompanion.app.data.GRID_COLUMNS_DEFAULT
import com.mtgcompanion.app.data.GRID_COLUMNS_RANGE
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.artrecognition.ArtIndexRepository
import com.mtgcompanion.app.data.offline.OfflineCardRepository
import com.mtgcompanion.app.update.UpdateManager
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
import com.mtgcompanion.app.ui.theme.accentPreviewColor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    syncManager: DriveSyncManager,
    supabaseSync: SupabaseSync,
    updateManager: UpdateManager,
    offlineCardRepository: OfflineCardRepository,
    artIndexRepository: ArtIndexRepository,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
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
                .padding(20.dp)
        ) {
            SettingsCategory("Account & sync") { AccountSyncSection(supabaseSync) }

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(BorderColor))

            SettingsCategory("Appearance") { AppearanceSection(settingsRepository) }

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(BorderColor))

            SettingsCategory("Google Drive Sync") { DriveSyncSection(syncManager) }

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(BorderColor))

            SettingsCategory("Card Display") { CardDisplaySection(settingsRepository) }

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(BorderColor))

            SettingsCategory("Offline Search") { OfflineSearchSection(offlineCardRepository) }

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(BorderColor))

            SettingsCategory("Scanner Art Recognition") { ArtRecognitionSection(artIndexRepository) }

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(BorderColor))

            SettingsCategory("App Updates") { AppUpdatesSection(updateManager) }
        }
    }
}

/** One collapsible settings category: a tap-to-expand header with a chevron, collapsed by default. */
@Composable
private fun SettingsCategory(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = GoldDim
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) { content() }
        }
    }
}

@Composable
private fun AppearanceSection(settingsRepository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val brightness by settingsRepository.appBrightness.collectAsState(initial = AppBrightness.DEFAULT)
    val accent by settingsRepository.accentTheme.collectAsState(initial = AccentTheme.DEFAULT)

    Text(
        "Choose a brightness mode and an accent color, themed on Magic's five colors of mana.",
        style = MaterialTheme.typography.bodySmall
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Brightness", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        FilterChip(
            selected = brightness == AppBrightness.DARK,
            onClick = { scope.launch { settingsRepository.setAppBrightness(AppBrightness.DARK) } },
            label = { Text("Dark", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.Unspecified)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Gold,
                selectedLabelColor = OnGold,
                labelColor = TextMuted,
                containerColor = Surface
            )
        )
        Box(modifier = Modifier.padding(start = 8.dp)) {
            FilterChip(
                selected = brightness == AppBrightness.LIGHT,
                onClick = { scope.launch { settingsRepository.setAppBrightness(AppBrightness.LIGHT) } },
                label = { Text("Light", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.Unspecified)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Gold,
                    selectedLabelColor = OnGold,
                    labelColor = TextMuted,
                    containerColor = Surface
                )
            )
        }
        Box(modifier = Modifier.padding(start = 8.dp)) {
            FilterChip(
                selected = brightness == AppBrightness.SYSTEM,
                onClick = { scope.launch { settingsRepository.setAppBrightness(AppBrightness.SYSTEM) } },
                label = { Text("System", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.Unspecified)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Gold,
                    selectedLabelColor = OnGold,
                    labelColor = TextMuted,
                    containerColor = Surface
                )
            )
        }
    }

    Column {
        Text("Accent color", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AccentTheme.entries.forEach { theme ->
                AccentSwatch(
                    theme = theme,
                    selected = theme == accent,
                    onClick = { scope.launch { settingsRepository.setAccentTheme(theme) } }
                )
            }
        }
    }
}

@Composable
private fun AccentSwatch(theme: AccentTheme, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accentPreviewColor(theme))
                .border(
                    BorderStroke(if (selected) 2.dp else 1.dp, if (selected) TextPrimary else BorderColor),
                    CircleShape
                )
        )
        Text(
            theme.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) GoldLight else TextMuted,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun CardDisplaySection(settingsRepository: SettingsRepository) {
    val scope = rememberCoroutineScope()

    val searchMode by settingsRepository.searchViewMode.collectAsState(initial = CardViewMode.DEFAULT)
    val collectionMode by settingsRepository.collectionViewMode.collectAsState(initial = CardViewMode.DEFAULT)
    val deckMode by settingsRepository.deckViewMode.collectAsState(initial = CardViewMode.DEFAULT)
    val allCardsMode by settingsRepository.allCardsViewMode.collectAsState(initial = CardViewMode.DEFAULT)
    val recMode by settingsRepository.recViewMode.collectAsState(initial = CardViewMode.DEFAULT)

    Text(
        "Choose list (detailed rows) or grid (compact card art) for each tab.",
        style = MaterialTheme.typography.bodySmall
    )

    CardViewModeRow(
        label = "Search results",
        mode = searchMode,
        onSelect = { mode -> scope.launch { settingsRepository.setSearchViewMode(mode) } }
    )
    CardViewModeRow(
        label = "Binder cards",
        mode = collectionMode,
        onSelect = { mode -> scope.launch { settingsRepository.setCollectionViewMode(mode) } }
    )
    CardViewModeRow(
        label = "Deck cards",
        mode = deckMode,
        onSelect = { mode -> scope.launch { settingsRepository.setDeckViewMode(mode) } }
    )
    CardViewModeRow(
        label = "All Cards",
        mode = allCardsMode,
        onSelect = { mode -> scope.launch { settingsRepository.setAllCardsViewMode(mode) } }
    )
    CardViewModeRow(
        label = "Deck suggestions (REC)",
        mode = recMode,
        onSelect = { mode -> scope.launch { settingsRepository.setRecViewMode(mode) } }
    )

    val storedColumns by settingsRepository.gridColumns.collectAsState(initial = GRID_COLUMNS_DEFAULT)
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).height(1.dp).background(BorderColor))
    GridColumnsSlider(
        storedColumns = storedColumns,
        onChangeFinished = { columns -> scope.launch { settingsRepository.setGridColumns(columns) } }
    )
}

@Composable
private fun CardViewModeRow(label: String, mode: CardViewMode, onSelect: (CardViewMode) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        FilterChip(
            selected = mode == CardViewMode.LIST,
            onClick = { onSelect(CardViewMode.LIST) },
            leadingIcon = { Icon(Icons.Filled.ViewList, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = { Text("List", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.Unspecified)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Gold,
                selectedLabelColor = OnGold,
                selectedLeadingIconColor = OnGold,
                labelColor = TextMuted,
                iconColor = TextMuted,
                containerColor = Surface
            )
        )
        Box(modifier = Modifier.padding(start = 8.dp)) {
            FilterChip(
                selected = mode == CardViewMode.GRID,
                onClick = { onSelect(CardViewMode.GRID) },
                leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Grid", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.Unspecified)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Gold,
                    selectedLabelColor = OnGold,
                    selectedLeadingIconColor = OnGold,
                    labelColor = TextMuted,
                    iconColor = TextMuted,
                    containerColor = Surface
                )
            )
        }
    }
}

/**
 * Slider from [GRID_COLUMNS_RANGE].first to .last columns, with a live preview row of placeholder
 * tiles that resizes as the thumb drags. The DataStore write only happens once the drag finishes,
 * so dragging doesn't spam writes — [sliderValue] alone drives the preview in the meantime.
 */
@Composable
private fun GridColumnsSlider(storedColumns: Int, onChangeFinished: (Int) -> Unit) {
    var sliderValue by remember(storedColumns) { mutableFloatStateOf(storedColumns.toFloat()) }
    val previewColumns = sliderValue.roundToInt()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Grid tile size", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
            Text("$previewColumns columns", style = MaterialTheme.typography.labelMedium, color = GoldLight)
        }

        GridColumnsPreview(columns = previewColumns, modifier = Modifier.padding(top = 10.dp))

        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChangeFinished(sliderValue.roundToInt()) },
            valueRange = GRID_COLUMNS_RANGE.first.toFloat()..GRID_COLUMNS_RANGE.last.toFloat(),
            steps = (GRID_COLUMNS_RANGE.last - GRID_COLUMNS_RANGE.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = Gold,
                activeTrackColor = Gold,
                inactiveTrackColor = BorderColor
            ),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** A row of [columns] card-shaped placeholder tiles, sized exactly as the real grids size theirs. */
@Composable
private fun GridColumnsPreview(columns: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(columns) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface)
                    .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Style, contentDescription = null, tint = GoldDim, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun OfflineSearchSection(offlineCardRepository: OfflineCardRepository) {
    val status by offlineCardRepository.status.collectAsState()

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

    val buttonLabel = if (status.hasData) "Update database" else "Download database"
    if (status.hasData) {
        OutlinedButton(
            onClick = { offlineCardRepository.downloadDatabase() },
            enabled = !status.downloading,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight)
        ) { DownloadButtonContent(status.downloading, buttonLabel, Gold) }
    } else {
        Button(
            onClick = { offlineCardRepository.downloadDatabase() },
            enabled = !status.downloading,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
        ) { DownloadButtonContent(status.downloading, buttonLabel, Bg) }
    }

    status.message?.let {
        Text(it, color = Gold, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ArtRecognitionSection(artIndexRepository: ArtIndexRepository) {
    val status by artIndexRepository.status.collectAsState()

    Text(
        "Download a visual-fingerprint database and recognition model (~75 MB) so the scanner can " +
            "identify a card by its art when the printed text is hard to read (glare, damage, an " +
            "unusual frame).",
        style = MaterialTheme.typography.bodySmall
    )

    if (status.hasData) {
        Text("${status.cardCount} cards recognized", style = MaterialTheme.typography.labelMedium, color = GoldLight)
    }

    val buttonLabel = if (status.hasData) "Update data" else "Download data"
    if (status.hasData) {
        OutlinedButton(
            // Only the explicit update re-fetches what's already on disk; a first-time download
            // picks up whatever half is missing.
            onClick = { artIndexRepository.downloadData(force = true) },
            enabled = !status.downloading,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight)
        ) { DownloadButtonContent(status.downloading, buttonLabel, Gold) }
    } else {
        Button(
            onClick = { artIndexRepository.downloadData() },
            enabled = !status.downloading,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
        ) { DownloadButtonContent(status.downloading, buttonLabel, Bg) }
    }

    if (status.downloading) {
        LinearProgressIndicator(
            progress = { status.progress },
            color = Gold,
            trackColor = BorderColor,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
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
            enabled = !state.downloading && !state.installing,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
        ) {
            if (state.downloading || state.installing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Bg)
            } else {
                Text("Download & install", style = MaterialTheme.typography.labelLarge, color = Bg)
            }
        }
        if (state.downloading) {
            val progress = state.downloadProgress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = Gold,
                    trackColor = BorderColor,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            } else {
                LinearProgressIndicator(color = Gold, trackColor = BorderColor, modifier = Modifier.fillMaxWidth())
            }
        }
    } else {
        OutlinedButton(
            onClick = { updateManager.checkForUpdate(silent = false) },
            enabled = !state.checking,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight)
        ) {
            if (state.checking) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Gold)
            } else {
                Text("Check for updates", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    state.message?.let {
        Text(it, color = Gold, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Sign in with email + password to sync decks and binders through Supabase. Signing out keeps
 * everything on this device; it only stops syncing.
 */
@Composable
private fun AccountSyncSection(sync: SupabaseSync) {
    val app = LocalAppColors.current
    val account by sync.auth.account.collectAsState()
    val status by sync.status.collectAsState()
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    // Set after creating an account, or when sign-in says the email isn't confirmed yet.
    var awaitingConfirmation by rememberSaveable { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }

    if (!sync.auth.configured) {
        Text("Cloud sync isn't set up in this build.", style = MaterialTheme.typography.bodySmall)
        return
    }

    val signedIn = account
    if (signedIn == null) {
        Text(
            "Sign in to keep your decks and binders in sync across your devices. Each deck syncs on its " +
                "own, so edits on two phones don't overwrite each other. Everything still works offline.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; notice = null },
            label = { Text("Email", color = TextMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; notice = null },
            label = { Text("Password", color = TextMuted) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        val canSubmit = !busy && email.contains("@") && password.length >= 6
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        notice = try {
                            sync.signIn(email, password)
                            password = ""
                            awaitingConfirmation = false
                            null
                        } catch (e: Exception) {
                            if (e.message?.contains("Confirm your email", ignoreCase = true) == true) awaitingConfirmation = true
                            if (e is java.io.IOException) "Can't reach the server — check your connection." else e.message
                        }
                        busy = false
                    }
                },
                enabled = canSubmit,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = OnGold)
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = OnGold)
                else Text("Sign in", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(
                onClick = {
                    busy = true
                    scope.launch {
                        notice = try {
                            if (sync.signUp(email, password)) {
                                password = ""
                                null
                            } else {
                                awaitingConfirmation = true
                                "Account created. Open the confirmation link we emailed to $email on this phone — it brings you straight back here, signed in."
                            }
                        } catch (e: Exception) {
                            if (e is java.io.IOException) "Can't reach the server — check your connection." else e.message
                        }
                        busy = false
                    }
                },
                enabled = canSubmit
            ) { Text("Create account", style = MaterialTheme.typography.labelLarge, color = if (canSubmit) Gold else TextDim) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Passwords need at least 6 characters.", style = MaterialTheme.typography.labelMedium, color = TextDim, modifier = Modifier.weight(1f))
            TextButton(
                onClick = {
                    if (!email.contains("@")) {
                        notice = "Enter your email above first, then tap Forgot password."
                    } else {
                        busy = true
                        scope.launch {
                            notice = try {
                                sync.sendPasswordReset(email)
                                "If $email has an account, a reset link is on its way. Open it on this phone to choose a new password."
                            } catch (e: Exception) {
                                if (e is java.io.IOException) "Can't reach the server — check your connection." else e.message
                            }
                            busy = false
                        }
                    }
                },
                enabled = !busy
            ) { Text("Forgot password?", style = MaterialTheme.typography.labelLarge, color = Gold) }
        }
        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = app.warning) }
        if (awaitingConfirmation && email.contains("@")) {
            TextButton(
                onClick = {
                    busy = true
                    scope.launch {
                        notice = try {
                            sync.resendConfirmation(email)
                            "Sent a new confirmation email to $email. Older links won't work."
                        } catch (e: Exception) {
                            if (e is java.io.IOException) "Can't reach the server — check your connection." else e.message
                        }
                        busy = false
                    }
                },
                enabled = !busy
            ) { Text("Resend confirmation email", style = MaterialTheme.typography.labelLarge, color = Gold) }
        }
    } else {
        Text("Signed in as ${signedIn.email}", style = MaterialTheme.typography.bodyMedium)
        val line = when {
            status.syncing -> "Syncing…"
            status.failed -> status.message ?: "Sync failed"
            status.lastSyncedAt > 0 && System.currentTimeMillis() - status.lastSyncedAt < 60_000 -> "Synced just now"
            status.lastSyncedAt > 0 -> "Synced ${DateUtils.getRelativeTimeSpanString(status.lastSyncedAt)}"
            else -> "Not synced yet"
        }
        Text(line, style = MaterialTheme.typography.bodySmall, color = if (status.failed) app.warning else TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { sync.syncNow() },
                enabled = !status.syncing,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = OnGold)
            ) {
                if (status.syncing) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = OnGold)
                else Text("Sync now", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { changingPassword = true }) {
                Text("Change password", style = MaterialTheme.typography.labelLarge, color = Gold)
            }
            TextButton(onClick = { scope.launch { sync.signOut() } }) {
                Text("Sign out", style = MaterialTheme.typography.labelLarge, color = TextMuted)
            }
        }
        if (changingPassword) {
            val context = androidx.compose.ui.platform.LocalContext.current
            SetPasswordDialog(
                auth = sync.auth,
                title = "Change password",
                explanation = "Choose a new password for ${signedIn.email}.",
                onDismiss = { changingPassword = false },
                onDone = { message ->
                    changingPassword = false
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                }
            )
        }
        Text(
            "Signing out keeps your decks and binders on this phone; it only stops syncing.",
            style = MaterialTheme.typography.labelMedium,
            color = TextDim
        )
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

    Text(
        "Back up your decks and collection to your Google Drive and keep them in sync across " +
            "devices. Once connected, changes sync automatically.",
        style = MaterialTheme.typography.bodySmall
    )

    if (status.connectedEmail == null) {
        Button(
            onClick = { signInLauncher.launch(syncManager.signInClient().signInIntent) },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
        ) { Text("Connect Google Drive", style = MaterialTheme.typography.labelLarge, color = Bg) }
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
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) {
                if (status.syncing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Bg)
                } else {
                    Text("Sync now", style = MaterialTheme.typography.labelLarge, color = Bg)
                }
            }
            OutlinedButton(
                onClick = { syncManager.signOut() },
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight)
            ) { Text("Disconnect", style = MaterialTheme.typography.labelLarge) }
        }
    }
    status.message?.let {
        Text(it, color = Gold, style = MaterialTheme.typography.bodySmall)
    }
}
