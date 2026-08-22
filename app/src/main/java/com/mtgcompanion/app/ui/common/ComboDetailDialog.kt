package com.mtgcompanion.app.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mtgcompanion.app.network.spellbook.ComboCardUsage
import com.mtgcompanion.app.network.spellbook.Variant
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

/** Full explanation of a combo — the cards involved, what it produces, and Commander Spellbook's own write-up. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComboDetailDialog(combo: Variant, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            containerColor = Bg,
            topBar = {
                TopAppBar(
                    title = { Text("COMBO", color = GoldLight, style = MaterialTheme.typography.labelLarge) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Gold)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val url = "https://commanderspellbook.com/combo/${combo.id}/"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = "View on Commander Spellbook", tint = Gold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CARDS USED", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        combo.uses.forEach { usage -> ComboCardThumb(usage) }
                    }
                }

                if (combo.produces.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("PRODUCES", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        combo.produces.forEach { produced ->
                            Text(produced.feature.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("HOW IT WORKS", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    // Commander Spellbook already writes its description as one step per line —
                    // numbering them instead of showing one prose blob makes the sequence explicit.
                    val steps = combo.description?.lines()?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                    if (steps.isEmpty()) {
                        Text("No write-up available for this combo yet.", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    } else {
                        steps.forEachIndexed { index, step ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier.size(22.dp).clip(CircleShape).background(Gold),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = Bg)
                                }
                                InlineManaText(
                                    step,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                combo.popularity?.let { popularity ->
                    Text(
                        "Used in $popularity decklist${if (popularity == 1) "" else "s"} on EDHREC.",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextDim
                    )
                }

                TextButton(onClick = {
                    val url = "https://commanderspellbook.com/combo/${combo.id}/"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }) {
                    Text("VIEW ON COMMANDER SPELLBOOK", color = Gold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** A tappable summary card for one combo — small art-crop thumbnails of the cards involved plus
 * what it produces. Shared by every screen that lists combos (Card Detail, Deck Detail's Analysis
 * tab, and the Rules screen's Combos search) so they read consistently and tapping always opens
 * the same [ComboDetailDialog]. */
@Composable
fun ComboSummaryRow(combo: Variant, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .elevatedCard(shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            combo.uses.forEach { usage ->
                AsyncImage(
                    model = usage.card.imageUriFrontArtCrop,
                    contentDescription = usage.card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 56.dp, height = 40.dp).clip(RoundedCornerShape(8.dp))
                )
            }
        }
        Text(
            combo.uses.joinToString(" + ") { it.card.name },
            style = MaterialTheme.typography.bodyMedium,
            color = GoldLight,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (combo.produces.isNotEmpty()) {
            Text(
                "Produces: " + combo.produces.joinToString(", ") { it.feature.name },
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** One card in a combo's "cards used" strip — its real Scryfall art (Commander Spellbook hands us
 * the image URL directly, no separate lookup needed) plus a ×qty badge when more than one copy is used.
 * Sized wide enough that the card's own rules text is actually legible, not just its art. */
@Composable
fun ComboCardThumb(usage: ComboCardUsage, width: Dp = 220.dp) {
    Column(modifier = Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.72f)) {
            AsyncImage(
                model = usage.card.imageUriFrontNormal,
                contentDescription = usage.card.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(10.dp))
            )
            usage.quantity?.takeIf { it > 1 }?.let { qty ->
                Text(
                    "×$qty",
                    style = MaterialTheme.typography.labelMedium,
                    color = Bg,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Gold)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            usage.card.name,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
