package com.mtgcompanion.app.ui.collection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.UpkeepItem
import com.mtgcompanion.app.data.UpkeepKind
import com.mtgcompanion.app.data.UpkeepReport
import com.mtgcompanion.app.data.UpkeepStore
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.upkeepHeadline
import com.mtgcompanion.app.data.upkeepNow
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.theme.LocalAppColors

/*
 * Upkeep (data/Upkeep.kt), the web app's UpkeepPage (src/pages/UpkeepPage.tsx): the share of copies
 * with a place, the things worth doing this week — each opening the screen that does it — and the
 * weekly reminder (UpkeepReminder.kt).
 */

/** Upkeep as it stands: worked out from this phone's pull lists, last import, game nights and noted prices. */
@Composable
fun rememberUpkeep(collections: List<Collection>, decks: List<Deck>): UpkeepReport? {
    val context = LocalContext.current
    var report by remember { mutableStateOf<UpkeepReport?>(null) }
    LaunchedEffect(collections, decks) { report = upkeepNow(context, collections, decks) }
    return report
}

private fun upkeepIcon(kind: UpkeepKind): ImageVector = when (kind) {
    UpkeepKind.PUT_AWAY -> Icons.Filled.Inventory2
    UpkeepKind.CHECK -> Icons.Filled.QrCodeScanner
    UpkeepKind.REMIND -> Icons.Filled.Handshake
    UpkeepKind.SPLIT -> Icons.AutoMirrored.Filled.CallSplit
    UpkeepKind.CARRY_ON -> Icons.Filled.Checklist
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpkeepScreen(
    collections: List<Collection>,
    decks: List<Deck>,
    onBack: () -> Unit,
    /** The scanner putting cards away into a place. */
    onPutAway: (String) -> Unit,
    /** No places yet: Getting started (StorageSetupScreen). */
    onSetUp: () -> Unit,
    /** The scanner checking a place. */
    onCheck: (String) -> Unit,
    onOpenLoans: () -> Unit,
    onOpenSpace: () -> Unit,
    onOpenPullList: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val report = rememberUpkeep(collections, decks)
    val places = placesOf(collections)
    var choosing by remember { mutableStateOf(false) }
    var weekly by remember { mutableStateOf(UpkeepStore.weeklyOn(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun open(item: UpkeepItem) {
        when (item.kind) {
            UpkeepKind.PUT_AWAY -> if (places.isEmpty()) onSetUp() else choosing = true
            UpkeepKind.CHECK -> item.placeId?.let(onCheck)
            UpkeepKind.REMIND -> onOpenLoans()
            UpkeepKind.SPLIT -> onOpenSpace()
            UpkeepKind.CARRY_ON -> item.deckId?.let(onOpenPullList)
        }
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Upkeep", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading()) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(report?.let { "${it.percent}%" } ?: "…", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = colors.accentLight)
                        Text("of copies have a place", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                    }
                    val share = report?.let { if (it.total > 0) it.placed.toFloat() / it.total else 0f } ?: 0f
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.surface2)) {
                        Box(Modifier.fillMaxWidth(share).height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.accent))
                    }
                    if (report != null) Text(upkeepHeadline(report.items.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                }
            }
            items(report?.items.orEmpty(), key = { "${it.kind}:${it.placeId}:${it.personKey}:${it.deckId}" }) { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Icon(upkeepIcon(item.kind), contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (item.detail.isNotEmpty()) Text(item.detail, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    LoanButton(item.action, primary = true, modifier = Modifier.widthIn(min = 96.dp)) { open(item) }
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Weekly reminder", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Text("A notification once a week saying what's worth doing", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    }
                    Switch(
                        checked = weekly,
                        onCheckedChange = { on ->
                            if (on && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            weekly = on
                            UpkeepStore.setWeekly(context, on)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.onAccent, checkedTrackColor = colors.accent)
                    )
                }
            }
        }
    }

    if (choosing) {
        PlacePickerDialog("Put cards away into…", places, onDismiss = { choosing = false }) { id ->
            choosing = false
            onPutAway(id)
        }
    }
}
