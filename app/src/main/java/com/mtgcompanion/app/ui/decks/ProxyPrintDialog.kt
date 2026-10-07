package com.mtgcompanion.app.ui.decks

import android.print.PrintAttributes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.PROXY_MARK
import com.mtgcompanion.app.data.PaperSize
import com.mtgcompanion.app.data.ProxyOptions
import com.mtgcompanion.app.data.ProxyPick
import com.mtgcompanion.app.data.defaultPaper
import com.mtgcompanion.app.data.pickedCopies
import com.mtgcompanion.app.data.proxySheetHtml
import com.mtgcompanion.app.data.sheetCards
import com.mtgcompanion.app.data.sheetSummary
import com.mtgcompanion.app.data.withCopies
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.collection.printLabels
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.theme.LocalAppColors
import java.util.Locale

/**
 * Print proxies: pick the cards and how many of each, then print them (or save a PDF) nine to a page
 * at real card size with thin cut lines — on A4 or Letter, marked "PROXY — not for sale" or not, in
 * colour or black and white to save ink. The page is ProxySheet.kt's proxySheetHtml, printed with
 * Android's print framework as box labels are (printLabels). Opened from a deck's menu, its pull list
 * and Spread thin. The web app's ProxyPrintDialog.tsx is the same. [onMark], with [markLabel], marks
 * what was printed as the deck's proxies.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProxyPrintDialog(
    title: String,
    initial: List<ProxyPick>,
    markLabel: String? = null,
    onMark: ((List<ProxyPick>) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var picks by remember { mutableStateOf(initial) }
    var options by remember { mutableStateOf(ProxyOptions(paper = defaultPaper(Locale.getDefault().country))) }
    var mark by remember { mutableStateOf(false) }
    val hasBacks = picks.any { it.backImageUrl != null }
    val cardCount = sheetCards(picks, options.backs).size

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            containerColor = colors.bg,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Print proxies", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(title, color = colors.textPrimary, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.accent) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
                )
            },
            bottomBar = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().background(colors.bg).padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
                ) {
                    Text(sheetSummary(picks, options.backs), color = colors.textMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            val media = if (options.paper == PaperSize.LETTER) PrintAttributes.MediaSize.NA_LETTER else PrintAttributes.MediaSize.ISO_A4
                            val attributes = PrintAttributes.Builder()
                                .setMediaSize(media)
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .setColorMode(if (options.lowInk) PrintAttributes.COLOR_MODE_MONOCHROME else PrintAttributes.COLOR_MODE_COLOR)
                                .build()
                            printLabels(context, proxySheetHtml(picks, options), "Manabind proxies", attributes)
                            if (mark && onMark != null) {
                                onMark(picks.filter { it.copies > 0 })
                                mark = false
                            }
                        },
                        enabled = cardCount > 0,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Print or save PDF", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(colors.bg).padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProxySectionLabel("Paper")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PaperSize.entries.forEach { p -> PillChip(p.label, options.paper == p, { options = options.copy(paper = p) }) }
                        }
                        ProxySectionLabel("On the sheet")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillChip(PROXY_MARK, options.marked, { options = options.copy(marked = !options.marked) })
                            PillChip("Low ink", options.lowInk, { options = options.copy(lowInk = !options.lowInk) })
                            if (hasBacks) PillChip("Back faces too", options.backs, { options = options.copy(backs = !options.backs) })
                            if (onMark != null && markLabel != null) PillChip(markLabel, mark, { mark = !mark })
                        }
                        Text(
                            "Nine cards a page at real size, 63 × 88 mm. Print at 100% (not \"fit to page\"), then cut along the lines." +
                                if (options.lowInk) " Low ink prints in black and white, lighter." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { ProxySectionLabel("Cards") }
                            if (pickedCopies(picks) > 0) {
                                TextButton(onClick = { picks = picks.map { it.copy(copies = 0) } }) { Text("Clear", color = colors.accent) }
                            }
                        }
                        if (picks.isEmpty()) Text("No cards here to print.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                }
                items(picks, key = { it.name.lowercase() }) { p ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(
                            model = p.imageUrl.toArtCropUrl(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(48.dp).height(36.dp).clip(RoundedCornerShape(6.dp)).background(colors.surface2)
                        )
                        Text(
                            p.name,
                            color = if (p.copies == 0) colors.textMuted else colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { picks = withCopies(picks, p.name, p.copies - 1) }, enabled = p.copies > 0) {
                            Icon(Icons.Filled.Remove, contentDescription = "One ${p.name} fewer", tint = if (p.copies > 0) colors.accent else colors.textDim)
                        }
                        Text("${p.copies}", fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                        IconButton(onClick = { picks = withCopies(picks, p.name, p.copies + 1) }) {
                            Icon(Icons.Filled.Add, contentDescription = "One ${p.name} more", tint = colors.accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxySectionLabel(text: String) {
    val colors = LocalAppColors.current
    Text(text, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.padding(top = 8.dp))
}
