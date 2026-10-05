package com.mtgcompanion.app.ui.settings

import com.mtgcompanion.app.data.usage.Usage
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Block
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.social.BlockedPeopleSection
import com.mtgcompanion.app.ui.common.BackButton
import androidx.compose.foundation.layout.fillMaxHeight
import com.mtgcompanion.app.ui.common.readableWidth
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
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Sell
import androidx.compose.ui.graphics.vector.ImageVector
import com.mtgcompanion.app.data.Currencies
import com.mtgcompanion.app.data.Prices
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import com.mtgcompanion.app.data.DriveImporter
import com.mtgcompanion.app.data.GRID_COLUMNS_DEFAULT
import com.mtgcompanion.app.data.GRID_COLUMNS_RANGE
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.CardIndexRepository
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

/** Settings' sections. The Settings screen lists them; each opens as a screen of its own. */
enum class SettingsSection(val id: String, val title: String, val icon: ImageVector) {
    ACCOUNT("account", "Account & sync", Icons.Filled.Person),
    APPEARANCE("appearance", "Appearance", Icons.Filled.DarkMode),
    CARD_DISPLAY("card-display", "Card Display", Icons.Filled.GridView),
    PRICES("prices", "Prices", Icons.Filled.Sell),
    OFFLINE_SEARCH("offline-search", "Offline Search", Icons.Filled.CloudOff),
    CARD_RECOGNITION("card-recognition", "Card Recognition", Icons.Filled.CameraAlt),
    APP_UPDATES("app-updates", "App Updates", Icons.Filled.Autorenew),
    BLOCKED("blocked", "Blocked people", Icons.Filled.Block),
    PRIVACY("privacy", "Privacy", Icons.Filled.Shield);

    companion object {
        fun fromId(id: String?): SettingsSection? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Settings: one row per section — its icon, its name and a line on how it's set now — each opening
 * the section on a screen of its own ([SettingsSectionScreen]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    supabaseSync: SupabaseSync,
    updateManager: UpdateManager,
    offlineCardRepository: OfflineCardRepository,
    cardIndexRepository: CardIndexRepository,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
    /** Only in the tester app: opens its tools. */
    onOpenTesterTools: (() -> Unit)? = null,
    /** Getting started: the welcome flow again (ui/onboarding/WelcomeScreen.kt). */
    onOpenGettingStarted: (() -> Unit)? = null,
    /** Given while the sample deck and binder are in the library: takes them out. */
    onRemoveSamples: (() -> Unit)? = null
) {
    val account by supabaseSync.auth.account.collectAsState()
    val brightness by settingsRepository.appBrightness.collectAsState(initial = AppBrightness.DEFAULT)
    val accent by settingsRepository.accentTheme.collectAsState(initial = AccentTheme.DEFAULT)
    val modes = listOf(
        settingsRepository.searchViewMode.collectAsState(initial = CardViewMode.DEFAULT).value,
        settingsRepository.collectionViewMode.collectAsState(initial = CardViewMode.DEFAULT).value,
        settingsRepository.deckViewMode.collectAsState(initial = CardViewMode.DEFAULT).value,
        settingsRepository.allCardsViewMode.collectAsState(initial = CardViewMode.DEFAULT).value,
        settingsRepository.recViewMode.collectAsState(initial = CardViewMode.DEFAULT).value
    )
    val chosenCurrency by Prices.chosen.collectAsState()
    val offline by offlineCardRepository.status.collectAsState()
    val recognition by cardIndexRepository.status.collectAsState()
    val update by updateManager.state.collectAsState()
    val usageOn by Usage.enabled.collectAsState()

    fun summaryOf(section: SettingsSection): String = when (section) {
        SettingsSection.ACCOUNT -> when {
            !supabaseSync.auth.configured -> "Cloud sync isn't set up in this build"
            account != null -> "Signed in as ${account?.email}"
            else -> "Not signed in — sign in to sync decks and binders"
        }
        SettingsSection.APPEARANCE -> {
            val mode = when (brightness) {
                AppBrightness.DARK -> "Dark"
                AppBrightness.LIGHT -> "Light"
                AppBrightness.SYSTEM -> "Follows the system"
            }
            "$mode · ${accent.label}"
        }
        SettingsSection.CARD_DISPLAY -> {
            val grids = modes.count { it == CardViewMode.GRID }
            when (grids) {
                0 -> "Every tab shows a list"
                modes.size -> "Every tab shows a grid"
                else -> "$grids of ${modes.size} tabs show a grid"
            }
        }
        SettingsSection.PRICES -> Currencies.of(chosenCurrency).let { "${it.name} (${it.code})" }
        SettingsSection.OFFLINE_SEARCH -> if (offline.hasData) "${offline.cardCount} cards downloaded" else "Not downloaded"
        SettingsSection.CARD_RECOGNITION -> if (recognition.ready) "${recognition.cardCount} card pictures" else "Downloads the first time you scan"
        SettingsSection.APP_UPDATES -> update.available?.let { "${it.headline} is available" } ?: "Version ${BuildConfig.VERSION_NAME}"
        SettingsSection.BLOCKED -> "People who can't see your things or contact you"
        SettingsSection.PRIVACY -> if (usageOn) "Sharing anonymous usage counts" else "Not sharing usage counts"
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    BackButton(onClick = onBack)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        // On a wide window the settings stay a comfortable reading width, centred.
        Box(Modifier.fillMaxSize().background(Bg).padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .readableWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            SettingsSection.entries.forEach { section ->
                SettingsSectionRow(section.icon, section.title, summaryOf(section)) { onOpenSection(section) }
            }
            if (onOpenGettingStarted != null) {
                SettingsSectionRow(Icons.Filled.Flag, "Getting started", "Bring in your cards, make a first deck, sign in", onOpenGettingStarted)
            }
            if (onRemoveSamples != null) {
                SettingsSectionRow(Icons.Filled.Science, "Remove samples", "The sample deck and binder go; nothing else changes", onRemoveSamples)
            }
            if (onOpenTesterTools != null) {
                SettingsSectionRow(Icons.Filled.Science, "Tester tools", "Reports, checklists and scan logs", onOpenTesterTools)
            }
        }
        }
    }
}

@Composable
private fun SettingsSectionRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    val app = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(app.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(app.surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = app.accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = app.textPrimary)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = app.textMuted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = app.textDim)
    }
}

/** One settings section on a screen of its own, opened from [SettingsScreen]'s list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSectionScreen(
    section: SettingsSection,
    driveImporter: DriveImporter,
    supabaseSync: SupabaseSync,
    updateManager: UpdateManager,
    offlineCardRepository: OfflineCardRepository,
    cardIndexRepository: CardIndexRepository,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onOpenFriends: (() -> Unit)? = null,
    socialRepository: SocialRepository? = null
) {
    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text(section.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    BackButton(onClick = onBack)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(Bg).padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .readableWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            when (section) {
                SettingsSection.ACCOUNT -> {
                    AccountSyncSection(supabaseSync)
                    if (onOpenFriends != null && supabaseSync.auth.configured) {
                        OutlinedButton(onClick = onOpenFriends, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Icon(Icons.Filled.Group, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Friends, sharing and trades", color = TextPrimary)
                        }
                    }
                    DriveImportSection(driveImporter, supabaseSync)
                }
                SettingsSection.APPEARANCE -> AppearanceSection(settingsRepository)
                SettingsSection.CARD_DISPLAY -> CardDisplaySection(settingsRepository)
                SettingsSection.PRICES -> PricesSection(settingsRepository)
                SettingsSection.OFFLINE_SEARCH -> OfflineSearchSection(offlineCardRepository)
                SettingsSection.CARD_RECOGNITION -> CardRecognitionSection(cardIndexRepository)
                SettingsSection.APP_UPDATES -> AppUpdatesSection(updateManager)
                SettingsSection.BLOCKED -> if (socialRepository != null) BlockedPeopleSection(socialRepository) else Text("Not available yet.", color = TextMuted)
                SettingsSection.PRIVACY -> PrivacySection()
            }
        }
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
private fun CardRecognitionSection(cardIndexRepository: CardIndexRepository) {
    val status by cardIndexRepository.status.collectAsState()

    Text(
        "The scanner knows cards by sight — the art and frame of every English printing — so it can " +
            "tell a full-art or borderless version from the usual one, and still recognize a card " +
            "whose name it can't read. The data (~26 MB) downloads the first time you scan; Update " +
            "picks up newly released sets.",
        style = MaterialTheme.typography.bodySmall
    )

    if (status.ready) {
        Text("${status.cardCount} card pictures", style = MaterialTheme.typography.labelMedium, color = GoldLight)
    }

    val label = if (status.ready) "Update" else "Download now"
    OutlinedButton(
        onClick = { cardIndexRepository.download(force = status.ready) },
        enabled = !status.downloading,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight)
    ) { DownloadButtonContent(status.downloading, label, Gold) }

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

    if (!updateManager.selfUpdates) {
        Text(
            "You're on version ${BuildConfig.VERSION_NAME}. Google Play keeps Manabind up to date.",
            style = MaterialTheme.typography.bodySmall
        )
        return
    }

    Text(
        "You're on version ${BuildConfig.VERSION_NAME}. Updates are delivered straight from the " +
            "project's GitHub releases.",
        style = MaterialTheme.typography.bodySmall
    )

    val available = state.available
    if (available != null) {
        Text(
            "${available.headline} is available.",
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
 * Sign in with email + password to sync decks and binders through Supabase. Signing out removes
 * them from this device (they stay in the account). Also the welcome flow's account step
 * (ui/onboarding/WelcomeScreen.kt).
 */
@Composable
internal fun AccountSyncSection(sync: SupabaseSync) {
    val app = LocalAppColors.current
    val account by sync.auth.account.collectAsState()
    val signedOutNotice by sync.auth.signedOutNotice.collectAsState()
    val status by sync.status.collectAsState()
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    // Set after creating an account, or when sign-in says the email isn't confirmed yet.
    var awaitingConfirmation by rememberSaveable { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    // Changes that couldn't be synced before signing out, which signing out would lose.
    var unsyncedWarning by remember { mutableStateOf<Int?>(null) }

    if (!sync.auth.configured) {
        Text("Cloud sync isn't set up in this build.", style = MaterialTheme.typography.bodySmall)
        return
    }

    val signedIn = account
    if (signedIn == null) {
        // An unexpected sign-out says what the server told us, rather than just showing the form again.
        signedOutNotice?.let { notice ->
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(app.surface2)
                    .padding(14.dp)
            ) {
                Text("You were signed out", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(notice.reason, style = MaterialTheme.typography.bodySmall)
                Text(
                    "On " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                        .format(java.util.Date(notice.atMillis)) + ". Your decks and binders were removed from this phone; " +
                            "sign in to get them back from your account.",
                    style = MaterialTheme.typography.labelMedium
                )
                TextButton(onClick = { sync.auth.dismissSignedOutNotice() }, modifier = Modifier.align(Alignment.End)) {
                    Text("Dismiss", color = Gold)
                }
            }
        }
        Text(
            "Sign in to keep your decks and binders in sync across your devices. Each deck syncs on its " +
                "own and are merged card by card, so edits on two phones don't overwrite each other. Everything still works offline.",
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
            TextButton(
                onClick = {
                    signingOut = true
                    scope.launch {
                        val unsynced = sync.signOut()
                        signingOut = false
                        if (unsynced > 0) unsyncedWarning = unsynced
                    }
                },
                enabled = !signingOut
            ) {
                Text(if (signingOut) "Syncing first…" else "Sign out", style = MaterialTheme.typography.labelLarge, color = TextMuted)
            }
        }
        unsyncedWarning?.let { count ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { unsyncedWarning = null },
                title = { Text(if (count == 1) "1 change hasn't synced" else "$count changes haven't synced", color = GoldLight) },
                text = {
                    Text(
                        "Signing out removes your decks and binders from this phone, so " +
                            (if (count == 1) "it" else "they") + " would be lost. Check your connection and sync again, or sign out anyway.",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        unsyncedWarning = null
                        scope.launch { sync.signOut(force = true) }
                    }) { Text("Sign out anyway", color = app.warning) }
                },
                dismissButton = {
                    TextButton(onClick = { unsyncedWarning = null }) { Text("Cancel", color = Gold) }
                }
            )
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
            "Signing out removes your decks and binders from this phone — they stay in your account and come back when you sign in.",
            style = MaterialTheme.typography.labelMedium,
            color = TextDim
        )
        DeleteAccountButton(sync, signedIn.email)
    }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    TextButton(onClick = { runCatching { uriHandler.openUri(PRIVACY_URL) } }) {
        Text("Privacy policy", style = MaterialTheme.typography.labelLarge, color = TextMuted)
    }
}

private const val PRIVACY_URL = "https://manabind.com/privacy"

/**
 * Delete my account: asks once, plainly, then deletes the account on the server and signs out
 * (SupabaseSync.deleteAccount). A server without the function says so and deletes nothing.
 */
@Composable
private fun DeleteAccountButton(sync: SupabaseSync, email: String) {
    val app = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var asking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    TextButton(onClick = { problem = null; asking = true }) {
        Text("Delete my account", style = MaterialTheme.typography.labelLarge, color = app.warning)
    }
    problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = app.warning) }
    if (!asking) return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) asking = false },
        title = { Text("Delete your account?", color = GoldLight) },
        text = {
            Text(
                "This deletes $email and everything synced to it — decks, binders, friends, trades, messages, " +
                    "loans and your profile — from Manabind's server, for good. It can't be undone. " +
                    "This phone is signed out and its copy of your library removed too.",
                style = MaterialTheme.typography.bodySmall
            )
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        problem = try {
                            when (sync.deleteAccount()) {
                                com.mtgcompanion.app.data.supabase.AccountDeletion.DELETED -> {
                                    android.widget.Toast.makeText(context, "Your account has been deleted.", android.widget.Toast.LENGTH_LONG).show()
                                    null
                                }
                                com.mtgcompanion.app.data.supabase.AccountDeletion.UNAVAILABLE ->
                                    "Deleting accounts from the app isn't switched on yet. Nothing was deleted — see manabind.com/delete-account for another way."
                                com.mtgcompanion.app.data.supabase.AccountDeletion.FAILED ->
                                    "The server couldn't delete your account. Nothing was deleted — try again later."
                            }
                        } catch (e: java.io.IOException) {
                            "Can't reach the server — check your connection. Nothing was deleted."
                        } catch (e: Exception) {
                            e.message ?: "Couldn't delete your account."
                        }
                        busy = false
                        asking = false
                    }
                }
            ) { Text(if (busy) "Deleting…" else "Delete for good", color = app.warning) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { asking = false }) { Text("Cancel", color = Gold) }
        }
    )
}

/**
 * Only for people who used the retired Google Drive sync: imports their last Drive backup once
 * (adding what's missing on this phone), then disconnects Google.
 */
@Composable
private fun DriveImportSection(importer: DriveImporter, sync: SupabaseSync) {
    val usedDrive by importer.usedDrive.collectAsState()
    val account by sync.auth.account.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    // Google may need the user to approve Drive access again mid-import; the launcher is created
    // below, after the function that needs it.
    val launchGoogle = remember { arrayOfNulls<(android.content.Intent) -> Unit>(1) }

    fun startImport() {
        busy = true
        notice = null
        scope.launch {
            notice = try {
                val result = importer.import { key -> sync.isDeletedInCloud(key) }
                val added = listOfNotNull(
                    result.addedDecks.takeIf { it > 0 }?.let { if (it == 1) "1 deck" else "$it decks" },
                    result.addedCollections.takeIf { it > 0 }?.let { if (it == 1) "1 binder" else "$it binders" }
                )
                when {
                    !result.foundBackup -> "No Google Drive backup found, so there was nothing to import. Google Drive is disconnected."
                    added.isEmpty() -> "Everything in your Google Drive backup is already on this phone. Google Drive is disconnected."
                    else -> "Imported ${added.joinToString(" and ")} from Google Drive. " +
                        if (account != null) "They'll sync to your account." else "Sign in above to sync them to your account."
                }
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                e.intent?.let { intent -> launchGoogle[0]?.invoke(intent) }
                null
            } catch (e: java.io.IOException) {
                "Can't reach Google Drive — check your connection."
            } catch (e: Exception) {
                e.message ?: "Import failed."
            }
            busy = false
        }
    }

    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && importer.isGoogleSignedIn) {
            startImport()
        } else if (result.data != null) {
            val error = runCatching { GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java) }.exceptionOrNull()
            notice = error?.let { importer.signInError(it) }
        }
    }
    launchGoogle[0] = { intent -> googleLauncher.launch(intent) }

    if (!usedDrive && notice == null) return

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(BorderColor))
    if (usedDrive) {
        Text("Moving from Google Drive sync", style = MaterialTheme.typography.titleSmall)
        Text(
            "Google Drive sync has been replaced by account sync. Import your last Google Drive backup once: " +
                "decks and binders that aren't on this phone are added, and everything already here stays as it is. " +
                "The app then disconnects from Google Drive.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (importer.isGoogleSignedIn) startImport()
                    else googleLauncher.launch(importer.signInClient().signInIntent)
                },
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = OnGold)
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = OnGold)
                else Text("Import from Google Drive", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(
                onClick = {
                    scope.launch {
                        importer.disconnect()
                        notice = "Google Drive disconnected without importing. Your old backup stays in your Google Drive."
                    }
                },
                enabled = !busy
            ) { Text("Skip", style = MaterialTheme.typography.labelLarge, color = TextMuted) }
        }
    }
    notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted) }
}
