package com.mtgcompanion.app.ui.scan

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.Money
import com.mtgcompanion.app.data.ScanHow
import com.mtgcompanion.app.data.ScanRow
import com.mtgcompanion.app.data.cameraReadsLine
import com.mtgcompanion.app.data.copiesInScan
import com.mtgcompanion.app.data.ownedLine
import com.mtgcompanion.app.data.ownedSummary
import com.mtgcompanion.app.data.panelPrice
import com.mtgcompanion.app.data.printingMismatch
import com.mtgcompanion.app.data.printingOf
import com.mtgcompanion.app.data.scanDetailsLine
import com.mtgcompanion.app.data.scanHowLabel
import com.mtgcompanion.app.data.scanIsGuess
import com.mtgcompanion.app.data.scanPanelSpoken
import com.mtgcompanion.app.data.useCameraLabel
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.canBeFoil
import com.mtgcompanion.app.ui.common.rememberReduceMotion
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/*
 * The card scanner's "Last scanned" panel: the card just scanned, over the camera until the next one —
 * its picture, name and the details at its bottom left (set code · collector number · rarity ·
 * language), how the scanner knew it, its price and copies, what you already own, and Change
 * printing, Foil and Undo. The words are data/ScanCardPanel.kt's; the web app's half is
 * src/components/LastScannedPanel.tsx.
 */

/** Settings › Scanner › Show last scanned card: on unless turned off. Kept on this phone only. */
object LastScannedSetting {
    private const val PREFS = "scan_panel"
    private const val KEY = "show_last_scanned"
    private val on = MutableStateFlow(true)
    @Volatile private var loaded = false

    private fun prefs(context: Context) =
        runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }.getOrNull()

    fun flow(context: Context): StateFlow<Boolean> {
        if (!loaded) {
            loaded = true
            on.value = prefs(context)?.getBoolean(KEY, true) ?: true
        }
        return on.asStateFlow()
    }

    fun set(context: Context, value: Boolean) {
        flow(context)
        on.value = value
        prefs(context)?.edit()?.putBoolean(KEY, value)?.apply()
    }
}

/** What the panel shows: the scan, the copies of its card in the pile, how it was identified. */
data class LastScanned(val row: ScanRow, val copies: Int, val how: ScanHow?, val cameraReads: Pair<String, String>?)

/** The newest scan of this session still in the pile, or null before the first (or once all are undone). */
fun lastScanned(panel: ScanViewModel.PanelState, pile: List<ScanRow>): LastScanned? {
    val byId = pile.associateBy { it.id }
    val row = panel.rows.asReversed().firstNotNullOfOrNull { byId[it] } ?: return null
    return LastScanned(
        row,
        pile.count { it.card.id == row.card.id },
        panel.how[row.id],
        panel.cameraReads?.takeIf { it.first == row.id }?.second
    )
}

/** The plain card scanner's panel, when Settings › Scanner says to show it and something's been scanned. */
@Composable
fun LastScannedOverlay(
    viewModel: ScanViewModel,
    pile: List<ScanRow>,
    collections: List<Collection>,
    decks: List<Deck>,
    money: Money,
    onOpen: (ScryfallCard) -> Unit,
    onChangePrinting: (ScanRow) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val show by remember { LastScannedSetting.flow(context) }.collectAsState()
    val panel by viewModel.panel.collectAsState()
    val last = lastScanned(panel, pile)
    if (!show || last == null) return
    val reduceMotion = rememberReduceMotion()
    AnimatedContent(
        targetState = last,
        contentKey = { it.row.id },
        transitionSpec = {
            if (reduceMotion) ContentTransform(EnterTransition.None, ExitTransition.None)
            else (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 }) togetherWith fadeOut(tween(120))
        },
        label = "last scanned",
        modifier = modifier
    ) { shown ->
        LastScannedPanel(
            shown,
            collections, decks, money, reduceMotion,
            onOpen = { onOpen(shown.row.card) },
            onChangePrinting = { onChangePrinting(shown.row) },
            onUndo = { viewModel.undoScan(shown.row.id) },
            onFoil = if (shown.row.card.canBeFoil) ({ foil -> viewModel.setFoil(shown.row.id, foil) }) else null,
            onUseCamera = { viewModel.useCameraPrinting(shown.row.id) }
        )
    }
}

@Composable
private fun LastScannedPanel(
    last: LastScanned,
    collections: List<Collection>,
    decks: List<Deck>,
    money: Money,
    reduceMotion: Boolean,
    onOpen: () -> Unit,
    onChangePrinting: () -> Unit,
    onUndo: () -> Unit,
    onFoil: ((Boolean) -> Unit)?,
    onUseCamera: () -> Unit
) {
    val row = last.row
    val card = row.card
    val amber = LocalAppColors.current.warning
    val guess = scanIsGuess(last.how, row.exact)
    val howLabel = scanHowLabel(last.how, row.exact)
    val price = money.format(panelPrice(card.prices?.usd, card.prices?.usdFoil, row.foil))
    val details = scanDetailsLine(card.set, card.collectorNumber, card.rarity, card.typeLine, card.lang, row.foil)
    val spoken = scanPanelSpoken(card.name, card.set, card.collectorNumber, card.rarity, card.typeLine, card.lang, row.foil, price, guess)
    val mismatch = printingMismatch(printingOf(card), last.cameraReads)
    // You own N: worked out off the main thread, once per card and library change.
    val owned by produceState<String?>(null, card.id, card.name, collections, decks) {
        value = withContext(Dispatchers.Default) { runCatching { ownedLine(ownedSummary(collections, decks, card.id, card.name)) }.getOrNull() }
    }
    // The camera reads another printing: the details line pulses amber (held still with reduced motion).
    val pulse = if (mismatch != null && !reduceMotion) {
        rememberInfiniteTransition(label = "mismatch").animateFloat(
            0.35f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "mismatch pulse"
        ).value
    } else 1f
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Bg.copy(alpha = 0.86f))
            .border(BorderStroke(if (guess || mismatch != null) 2.dp else 1.dp, if (guess || mismatch != null) amber else BorderColor.copy(alpha = 0.6f)), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(
                model = card.imageUris?.small ?: card.cardFaces?.firstOrNull()?.imageUris?.small ?: card.displayImageUrl,
                contentDescription = null,
                modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(4.dp)).clickable(onClickLabel = "Card details", onClick = onOpen)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = "Card details", onClick = onOpen)
                    .semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = spoken
                    }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        card.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    howLabel?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (guess) amber else GoldLight,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(BorderStroke(1.dp, if (guess) amber else Gold.copy(alpha = 0.5f)), RoundedCornerShape(50))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                // The bottom-left details, big: the reason the panel is there.
                Text(
                    details,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (mismatch != null) amber.copy(alpha = pulse) else Gold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (mismatch != null) Modifier
                        .border(BorderStroke(1.dp, amber.copy(alpha = pulse)), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp) else Modifier
                )
                Text(
                    listOfNotNull(card.setName, price, copiesInScan(last.copies)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                owned?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (onFoil != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (row.foil) Gold.copy(alpha = 0.2f) else Color.Transparent)
                        .toggleable(value = row.foil, role = Role.Switch, onValueChange = onFoil)
                        .semantics { contentDescription = "Foil" }
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = if (row.foil) Gold else TextDim, modifier = Modifier.size(20.dp))
                }
            }
            IconButton(onClick = onChangePrinting, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = "Change printing", tint = Gold)
            }
            IconButton(onClick = onUndo, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo this scan", tint = TextMuted)
            }
        }
        if (mismatch != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            ) {
                Text(
                    cameraReadsLine(mismatch),
                    style = MaterialTheme.typography.labelMedium,
                    color = amber,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    useCameraLabel(mismatch),
                    style = MaterialTheme.typography.labelLarge,
                    color = Bg,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(amber)
                        .clickable(role = Role.Button, onClick = onUseCamera)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
