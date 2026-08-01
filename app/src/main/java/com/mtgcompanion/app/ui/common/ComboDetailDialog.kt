package com.mtgcompanion.app.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("CARDS USED", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    combo.uses.forEach { usage ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            usage.quantity?.takeIf { it > 1 }?.let {
                                Text("×$it", style = MaterialTheme.typography.bodyMedium, color = GoldLight)
                            }
                            Text(usage.card.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
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
                        .clip(RoundedCornerShape(6.dp))
                        .background(Surface)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("HOW IT WORKS", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Text(
                        combo.description?.takeIf { it.isNotBlank() } ?: "No write-up available for this combo yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
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
