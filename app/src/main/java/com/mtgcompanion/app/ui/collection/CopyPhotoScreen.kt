package com.mtgcompanion.app.ui.collection

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CARD_CONDITIONS
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CopyPhoto
import com.mtgcompanion.app.data.CopyPhotoStore
import com.mtgcompanion.app.data.CopyRef
import com.mtgcompanion.app.data.boughtLabel
import com.mtgcompanion.app.data.conditionName
import com.mtgcompanion.app.data.copiesOfCard
import com.mtgcompanion.app.data.dayOf
import com.mtgcompanion.app.data.photoDayLabel
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/*
 * Photos of your copy, the web app's CopyPhotoPage (src/pages/CopyPhotoPage.tsx): front and back
 * pictures of one particular copy of a card — picked from its copies when there are several — with
 * its condition (the binder entry's, which syncs), when it was photographed, what it was bought for
 * and where, and what it's worth today. The photos stay on this phone (CopyPhotoStore.kt); they go in
 * the Value by place PDF report. Also the setting to ask for photos when adding a dear card. The logic
 * is data/CopyPhotos.kt.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyPhotoScreen(
    cardName: String,
    collections: List<Collection>,
    onBack: () -> Unit,
    onChange: (StorageChange) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val money = rememberMoney()
    remember { CopyPhotoStore.init(context) }
    val saved by CopyPhotoStore.saved.collectAsState()
    val copies = remember(collections, cardName) { copiesOfCard(collections, cardName) }
    var chosen by remember { mutableStateOf<String?>(null) }
    // The copy shown: the one picked, else the first with photos, else the first.
    val copy = copies.firstOrNull { it.key == chosen } ?: copies.firstOrNull { c -> saved.photos.any { it.key == c.key && it.hasPhotos } } ?: copies.firstOrNull()
    val photo = copy?.let { c -> saved.photos.firstOrNull { it.key == c.key } }
    var worth by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(copy?.scryfallId, copy?.foil) {
        val c = copy ?: return@LaunchedEffect
        worth = runCatching {
            CardRepository().getCardsByIds(listOf(c.scryfallId)).firstOrNull()?.prices?.let { p ->
                val plain = p.usd?.toDoubleOrNull()
                val foil = p.usdFoil?.toDoubleOrNull()
                if (c.foil) foil ?: plain else plain ?: foil
            }
        }.getOrNull()
    }
    // Which side the camera or picker is getting, and the file the camera writes to.
    var side by remember { mutableStateOf(true) }
    var thenBack by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf<Boolean?>(null) }
    var capture by remember { mutableStateOf<Uri?>(null) }
    var editing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    fun take(uri: Uri) {
        val c = copy ?: return
        val front = side
        scope.launch {
            val ok = withContext(Dispatchers.IO) { CopyPhotoStore.setPhoto(context, c, front, uri) }
            message = if (ok) null else "That picture couldn't be read."
            // Retake photos: the back straight after the front.
            if (ok && front && thenBack) { thenBack = false; side = false; asking = false }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = capture
        if (ok && uri != null) take(uri) else thenBack = false
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) take(uri) else thenBack = false
    }
    fun openCamera() {
        val dir = File(context.cacheDir, "photo_capture").apply { mkdirs() }
        val file = File(dir, "capture.jpg")
        val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }.getOrNull()
        if (uri == null) { message = "The camera couldn't be opened."; return }
        capture = uri
        runCatching { camera.launch(uri) }.onFailure { message = "The camera couldn't be opened." }
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        if (copy != null) Text("Your copy · ${copy.where}", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(copy?.name ?: cardName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        if (copy == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) { Text("You don't own a copy of $cardName.", color = colors.textMuted) }
            return@Scaffold
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            if (copies.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                copies.forEach { c ->
                    val on = c.key == copy.key
                    val has = saved.photos.any { it.key == c.key && it.hasPhotos }
                    Text(
                        "Copy ${c.n}${if (c.foil) " foil" else ""} · ${c.where}" + if (has) " · photos" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) colors.onAccent else colors.textMuted,
                        maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) colors.accent else colors.surface2).clickable { chosen = c.key }.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PhotoTile("Front", photo?.front, Modifier.weight(1f)) { side = true; asking = true }
                PhotoTile("Back", photo?.back, Modifier.weight(1f)) { side = false; asking = false }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp)
            ) {
                DetailLine("Condition", copy.condition?.let { conditionName(it) } ?: "Not said")
                DetailLine("Photographed", photo?.photographedAt?.let { photoDayLabel(dayOf(it)) } ?: "Not yet")
                DetailLine("Bought for", boughtLabel(photo) { money.format(it) }.ifEmpty { "Not said" })
                DetailLine("Worth today", worth?.let { money.format(it, whole = it >= 10) } ?: "—", gold = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { side = true; thenBack = true; asking = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent.copy(alpha = 0.16f), contentColor = colors.accentLight),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) { Text(if (photo?.hasPhotos == true) "Retake photos" else "Take photos", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { editing = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) { Text("Edit details", fontWeight = FontWeight.Bold) }
            }
            message?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.warning) }
            Text(
                "Photos stay on this device: they aren't synced or uploaded. They go in the Value by place PDF report, for insurance. " +
                    "For cards worth over a set amount, the app can ask for photos when you add them.",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted
            )
            AskSetting(saved.askOver, money.currency.symbol, toLocal = { money.toLocal(it) }, toUsd = { money.toUsd(it) })
        }
    }

    asking?.let { front ->
        AlertDialog(
            onDismissRequest = { asking = null; thenBack = false },
            containerColor = colors.surface,
            title = { Text(if (front) "Photo of the front" else "Photo of the back", color = colors.accentLight) },
            text = { Text("Lay the card flat in good light, filling the frame.", color = colors.textMuted) },
            confirmButton = { TextButton(onClick = { asking = null; side = front; openCamera() }) { Text("Take photo", color = colors.accent) } },
            dismissButton = {
                TextButton(onClick = {
                    asking = null
                    side = front
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Choose a photo", color = colors.textPrimary) }
            }
        )
    }
    if (editing && copy != null) {
        DetailsDialog(copy, photo, money.currency.symbol, { money.toLocal(it) }, { money.toUsd(it) }, onDismiss = { editing = false }) { condition, usd, where ->
            val had = photo ?: CopyPhoto(copy.key, copy.scryfallId, copy.name, copy.foil)
            CopyPhotoStore.save(had.copy(boughtUsd = usd, boughtWhere = where))
            // The condition is the binder entry's, for every copy in it — and it syncs.
            if (condition != copy.condition) onChange { current ->
                current.map { c ->
                    if (c.id != copy.collectionId) c
                    else c.copy(entries = c.entries.map { if (it.scryfallId == copy.scryfallId) it.copy(condition = condition) else it })
                }
            }
            editing = false
        }
    }
}

@Composable
private fun PhotoTile(label: String, file: String?, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val f = file?.let { CopyPhotoStore.file(it) }
    Box(
        modifier.aspectRatio(63f / 88f).clip(RoundedCornerShape(10.dp)).background(colors.surface3).clickable(onClick = onClick),
        contentAlignment = Alignment.BottomStart
    ) {
        if (f != null) AsyncImage(model = f, contentDescription = "$label of your copy", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text("Tap to add", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.align(Alignment.Center))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(8.dp)).background(colors.bg.copy(alpha = 0.8f)).padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String, gold: Boolean = false) {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (gold) colors.accent else colors.textPrimary)
    }
}

private fun amountText(local: Double): String =
    if (local == Math.floor(local)) local.toLong().toString() else String.format(Locale.US, "%.2f", local)

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalAppColors.current.accent,
    unfocusedBorderColor = LocalAppColors.current.border,
    focusedTextColor = LocalAppColors.current.textPrimary,
    unfocusedTextColor = LocalAppColors.current.textPrimary,
    cursorColor = LocalAppColors.current.accent
)

/** Ask for photos when adding a card worth over an amount (in the user's currency; blank: never). */
@Composable
private fun AskSetting(askOver: Double?, symbol: String, toLocal: (Double) -> Double, toUsd: (Double) -> Double) {
    val colors = LocalAppColors.current
    var text by remember(askOver) { mutableStateOf(askOver?.let { amountText(toLocal(it)) } ?: "") }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp)
    ) {
        Text("Ask for photos when I add a card worth over", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter { it.isDigit() || it == '.' }.take(8)
                CopyPhotoStore.setAskOver(text.toDoubleOrNull()?.let(toUsd))
            },
            prefix = { Text(symbol.trim(), color = colors.textMuted) },
            placeholder = { Text("Never", color = colors.textDim) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = fieldColors()
        )
        Text("Kept on this device. Leave it empty never to ask.", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
    }
}

/** Edit details: the copies' condition, and what this copy was bought for and where. */
@Composable
private fun DetailsDialog(
    copy: CopyRef,
    photo: CopyPhoto?,
    symbol: String,
    toLocal: (Double) -> Double,
    toUsd: (Double) -> Double,
    onDismiss: () -> Unit,
    onSave: (condition: String?, boughtUsd: Double?, where: String?) -> Unit
) {
    val colors = LocalAppColors.current
    var condition by remember { mutableStateOf(copy.condition) }
    var price by remember { mutableStateOf(photo?.boughtUsd?.let { amountText(toLocal(it)) } ?: "") }
    var where by remember { mutableStateOf(photo?.boughtWhere ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Details of your copy", color = colors.accentLight) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Condition", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf<String?>(null) + CARD_CONDITIONS).forEach { c ->
                        val on = c == condition
                        Text(
                            c ?: "Not said",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) colors.onAccent else colors.textMuted,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) colors.accent else colors.surface2).clickable { condition = c }.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
                Text("Every copy of this printing in the binder has the same condition; it syncs.", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                OutlinedTextField(
                    value = price,
                    onValueChange = { v -> price = v.filter { it.isDigit() || it == '.' }.take(8) },
                    label = { Text("Bought for", color = colors.textMuted) },
                    prefix = { Text(symbol.trim(), color = colors.textMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = where,
                    onValueChange = { where = it.take(60) },
                    label = { Text("Where", color = colors.textMuted) },
                    placeholder = { Text("Card shop, a trade with Sam…", color = colors.textDim) },
                    singleLine = true,
                    colors = fieldColors()
                )
                Text("What it cost and where stay on this device, with the photos.", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(condition, price.toDoubleOrNull()?.let(toUsd), where.trim().ifEmpty { null }) }) { Text("Save", color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}
