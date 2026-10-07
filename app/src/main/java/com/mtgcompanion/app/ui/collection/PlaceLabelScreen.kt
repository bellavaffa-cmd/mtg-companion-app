package com.mtgcompanion.app.ui.collection

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.LabelShow
import com.mtgcompanion.app.data.LabelSize
import com.mtgcompanion.app.data.LabelText
import com.mtgcompanion.app.data.StoragePlace
import com.mtgcompanion.app.data.copiesWithin
import com.mtgcompanion.app.data.labelOrder
import com.mtgcompanion.app.data.labelText
import com.mtgcompanion.app.data.placeLabelLink
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.storageSummary
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.social.QrCode
import com.mtgcompanion.app.ui.theme.LocalAppColors

private val LabelPaper = Color(0xFFFBFAF6)
private val LabelInk = Color(0xFF14161C)
private val LabelSoft = Color(0xFF4A4538)
private val LabelFaint = Color(0xFF6A6355)

/**
 * A storage place's label, the web app's PlaceLabelPage (src/pages/PlaceLabelPage.tsx): a preview with
 * its QR code (a link to the place — PlaceLabel.kt), where it lives, its sections and sorting rule, in
 * one of three sizes. "Print or save PDF" hands the labels to Android's print framework, drawn at their
 * size in millimetres by a WebView (printLabels below), so any printer or "Save as PDF" takes them.
 * "All labels" puts every place's label on one sheet. [placeId] null: all of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceLabelScreen(
    placeId: String?,
    collections: List<Collection>,
    decks: List<Deck>,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val places = placesOf(collections)
    val place = placeId?.let { id -> places.firstOrNull { it.id == id } }
    val summary = remember(collections, decks) { storageSummary(collections, decks) }
    var size by remember { mutableStateOf(LabelSize.BOX_END) }
    var show by remember { mutableStateOf(LabelShow()) }
    var all by remember { mutableStateOf(placeId == null) }
    val printed = if (all) labelOrder(places) else listOfNotNull(place)
    fun textOf(p: StoragePlace) = labelText(p, places, show, copiesWithin(summary, places, p.id))

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(if (all) "All labels" else "Label: ${place?.name ?: ""}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            if (printed.isNotEmpty()) Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
            ) {
                if (!all || place != null) Button(
                    onClick = { all = !all },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                    modifier = Modifier.height(48.dp)
                ) { Text(if (all) "Just this one" else "All labels", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = {
                        val name = if (all) "Manabind labels" else "Label ${place?.name.orEmpty()}"
                        printLabels(context, labelsHtml(printed.map { placeLabelLink(it.id) to textOf(it) }, size), name)
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Print or save PDF", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    ) { padding ->
        if (printed.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(if (all) "No places yet: make one on the Storage page." else "This place isn't here any more.", color = colors.textMuted)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(printed, key = { it.id }) { p -> LabelPreview(p.id, textOf(p), size) }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SIZE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.textMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabelSize.entries.forEach { s -> PillChip(s.label, size == s, { size = s }) }
                    }
                    Text("SHOW ON IT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.textMuted, modifier = Modifier.padding(top = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillChip("Where it lives", show.where, { show = show.copy(where = !show.where) })
                        PillChip("Sections", show.sections, { show = show.copy(sections = !show.sections) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillChip("Sorting rule", show.rule, { show = show.copy(rule = !show.rule) })
                        PillChip("Card count", show.count, { show = show.copy(count = !show.count) })
                    }
                    Text(
                        "The code only names the place, so the label stays right as cards come and go.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/** One label as it will print: dark on paper, in the shape of its size. */
@Composable
private fun LabelPreview(placeId: String, text: LabelText, size: LabelSize) {
    val divider = size == LabelSize.DIVIDER
    val box = Modifier
        .widthIn(max = if (divider) 240.dp else 340.dp)
        .fillMaxWidth()
        .aspectRatio(size.widthMm.toFloat() / size.heightMm)
        .clip(RoundedCornerShape(8.dp))
        .background(LabelPaper)
        .border(1.dp, Color(0xFFD8D4C8), RoundedCornerShape(8.dp))
        .padding(12.dp)
    val lines: @Composable () -> Unit = {
        text.where?.let { Text(it.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LabelFaint, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Text(text.name, fontSize = if (size == LabelSize.SMALL) 18.sp else 26.sp, fontWeight = FontWeight.ExtraBold, color = LabelInk, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 28.sp)
        if (size != LabelSize.SMALL) {
            text.sections?.let { Text(it, fontSize = 11.sp, color = LabelSoft, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            text.rule?.let { Text(it, fontSize = 11.sp, color = LabelSoft) }
            text.count?.let { Text(it, fontSize = 11.sp, color = LabelSoft) }
        }
        Text("Manabind", fontSize = 10.sp, color = LabelFaint)
    }
    if (divider) {
        Column(box, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            QrCode(placeLabelLink(placeId), 140.dp, "QR code for ${text.name}")
            Column(horizontalAlignment = Alignment.CenterHorizontally) { lines() }
        }
    } else {
        Row(box, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxHeight().aspectRatio(1f)) { QrCode(placeLabelLink(placeId), 200.dp, "QR code for ${text.name}", Modifier.fillMaxSize()) }
            Column(Modifier.weight(1f)) { lines() }
        }
    }
}

// ---- Printing ----

private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** A QR code for [text] as SVG: dark modules on white, with a quiet margin. */
internal fun qrSvg(text: String): String {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2))
    val path = StringBuilder()
    for (y in 0 until matrix.height) for (x in 0 until matrix.width) if (matrix[x, y]) path.append("M$x,${y}h1v1h-1z")
    return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${matrix.width} ${matrix.height}" shape-rendering="crispEdges">""" +
        """<rect width="${matrix.width}" height="${matrix.height}" fill="#fff"/><path d="$path" fill="#000"/></svg>"""
}

/** The labels as a page to print: each at its size in millimetres, as the web app prints them. */
internal fun labelsHtml(labels: List<Pair<String, LabelText>>, size: LabelSize): String {
    val divider = size == LabelSize.DIVIDER
    val small = size == LabelSize.SMALL
    val body = labels.joinToString("") { (link, t) ->
        val lines = buildString {
            t.where?.let { append("<span class=\"where\">${escape(it)}</span>") }
            append("<span class=\"name\">${escape(t.name)}</span>")
            if (!small) {
                t.sections?.let { append("<span class=\"line\">${escape(it)}</span>") }
                t.rule?.let { append("<span class=\"line\">${escape(it)}</span>") }
                t.count?.let { append("<span class=\"line\">${escape(it)}</span>") }
            }
            append("<span class=\"brand\">Manabind</span>")
        }
        "<div class=\"label\"><div class=\"qr\">${qrSvg(link)}</div><div class=\"text\">$lines</div></div>"
    }
    return """<!doctype html><html><head><meta charset="utf-8"><style>
@page { margin: 10mm; }
body { margin: 0; font-family: sans-serif; color: #14161c; }
.sheet { display: flex; flex-wrap: wrap; gap: 4mm; }
.label { box-sizing: border-box; width: ${size.widthMm}mm; height: ${size.heightMm}mm; padding: 3mm; border: 0.3mm dashed #999;
  display: flex; ${if (divider) "flex-direction: column; text-align: center;" else "align-items: center;"} gap: 3mm; overflow: hidden; page-break-inside: avoid; break-inside: avoid; }
.qr { flex: none; ${if (divider) "width: 45mm; height: 45mm; margin: 0 auto;" else "height: 100%; aspect-ratio: 1; width: ${size.heightMm - 6}mm;"} }
.qr svg { width: 100%; height: 100%; display: block; }
.text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 0.8mm; ${if (divider) "align-items: center;" else ""} }
.where { font-size: 2.6mm; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: #6a6355; }
.name { font-size: ${if (small) "5.5mm" else "8mm"}; font-weight: 800; line-height: 1; overflow-wrap: anywhere; }
.line { font-size: 2.8mm; color: #4a4538; }
.brand { font-size: 2.4mm; color: #6a6355; }
</style></head><body><div class="sheet">$body</div></body></html>"""
}

/** Kept until the page has loaded and gone to the print framework, so it isn't collected first. */
private var printing: WebView? = null

/**
 * Prints [html] with Android's print framework — any printer, or "Save as PDF" — by loading it into a
 * WebView off screen and handing its print adapter to the PrintManager, with [attributes] (paper size,
 * margins) as the print dialog's starting point. [context] must be the screen's
 * (an Activity), as printing needs one.
 */
internal fun printLabels(context: Context, html: String, jobName: String, attributes: PrintAttributes = PrintAttributes.Builder().build()) {
    val view = WebView(context)
    var sent = false
    view.webViewClient = object : WebViewClient() {
        override fun onPageFinished(page: WebView, url: String?) {
            if (sent) return
            sent = true
            val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            manager?.print(jobName, page.createPrintDocumentAdapter(jobName), attributes)
            printing = null
        }
    }
    printing = view
    view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}
