package com.mtgcompanion.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mtgcompanion.app.data.CardIndexRepository
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.CellCard
import com.mtgcompanion.app.data.CellState
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.PageCell
import com.mtgcompanion.app.data.ScanBox
import com.mtgcompanion.app.data.chooseCell
import com.mtgcompanion.app.data.clampedTo
import com.mtgcompanion.app.data.couldBe
import com.mtgcompanion.app.data.guideInImage
import com.mtgcompanion.app.data.pageAspect
import com.mtgcompanion.app.data.pageCellBoxes
import com.mtgcompanion.app.data.pageDiff
import com.mtgcompanion.app.data.pageDiffSummary
import com.mtgcompanion.app.data.pageDiffText
import com.mtgcompanion.app.data.pageGrid
import com.mtgcompanion.app.data.pageScanHint
import com.mtgcompanion.app.data.pageScanSummary
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.pockets
import com.mtgcompanion.app.data.readCell
import com.mtgcompanion.app.data.recordPage
import com.mtgcompanion.app.data.recordedLine
import com.mtgcompanion.app.data.recordedOnPage
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.data.bestCue
import com.mtgcompanion.app.ui.collection.StorageChange
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/*
 * Scan a whole binder page (data/PageScan.kt): one photo of the page, held inside a guide the
 * binder's pocket grid, read pocket by pocket with the same recogniser a single scan uses — each
 * pocket's card found by its edges and known by sight, and its title read off the same photo for the
 * pockets the picture alone can't settle. Then Record this page, Check against record, Retake and
 * Save · next page. The web app's PageScanPage.tsx.
 */

/** The longest side the photo is shrunk to: a 3 × 3 page leaves each card about 600 px across — plenty. */
private const val PHOTO_SIDE = 2400

/** Reads every pocket of a binder page from one photo. */
class PageReader(private val cardIndexRepository: CardIndexRepository, private val cardRepository: CardRepository) {
    private val textReader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Whether cards can be known by sight yet (the card index is downloaded). */
    val canSee: Boolean get() = cardIndexRepository.recognizer() != null

    fun close() = runCatching { textReader.close() }

    private suspend fun lines(photo: Bitmap): List<Text.Line> = suspendCancellableCoroutine { cont ->
        textReader.process(InputImage.fromBitmap(photo, 0))
            .addOnSuccessListener { r -> if (cont.isActive) cont.resume(r.textBlocks.flatMap { it.lines }) }
            .addOnFailureListener { if (cont.isActive) cont.resume(emptyList()) }
    }

    /** The pockets of [pockets] a page laid over [area] of the upright [photo], slot by slot. */
    suspend fun read(photo: Bitmap, area: ScanBox, pockets: Int): List<PageCell> {
        val recognizer = cardIndexRepository.recognizer()
        val text = runCatching { lines(photo) }.getOrDefault(emptyList())
        return pageCellBoxes(area, pockets).mapIndexed { i, box ->
            // A card's title is along its top; the top third of the pocket is room enough for a card held a little off.
            val titleArea = ScanBox(box.left, box.top, box.right, box.top + (box.bottom - box.top) / 3)
            val title = extractCardName(text.filter { l -> l.boundingBox?.let { b -> titleArea.holdsCentreOf(b.left, b.top, b.right, b.bottom) } == true })
            readPocket(photo, box, i + 1, recognizer, title)
        }
    }

    private suspend fun readPocket(photo: Bitmap, box: ScanBox, slot: Int, recognizer: CardRecognizer?, title: String?): PageCell = withContext(Dispatchers.Default) {
        // The pocket and a margin round it, as a picture of its own: finding the card's edges needs no more.
        val grown = box.grownBy(0.3f).clampedTo(photo.width, photo.height)
        val cut = runCatching { Bitmap.createBitmap(photo, grown.left, grown.top, grown.right - grown.left, grown.bottom - grown.top) }.getOrNull()
        val inCut = ScanBox(box.left - grown.left, box.top - grown.top, box.right - grown.left, box.bottom - grown.top)
        val flat = if (recognizer != null && cut != null) runCatching { FlatCard.find(cut, inCut) }.getOrNull() else null
        val seen = if (recognizer != null && flat != null) runCatching { recognizer.recognize(flat) }.getOrNull() else null
        var cell = readCell(slot, found = flat != null, anywhere = seen?.anywhere.orEmpty())
        if (cell.state != CellState.READ && title != null) {
            // The title read off the photo, looked up by name: its printings by sight, or the card to confirm.
            val named = runCatching { cardRepository.getByFuzzyName(title) }.getOrNull()
            if (named != null) {
                val byName = if (recognizer != null && flat != null) runCatching { recognizer.recognize(flat, named.name).named }.getOrNull().orEmpty() else emptyList()
                cell = if (byName.isNotEmpty()) readCell(slot, true, seen?.anywhere.orEmpty(), byName)
                else PageCell(slot, CellState.CHOOSE, options = listOf(CellCard(named.id, named.name, named.set.orEmpty(), named.collectorNumber.orEmpty())))
            }
        }
        cell
    }
}

private enum class Phase { CAMERA, READING, RESULTS }

/** ImageCapture's JPEG, decoded and turned upright, shrunk to [PHOTO_SIDE]. */
private fun photoOf(image: ImageProxy): Bitmap? = runCatching {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!
    val scale = minOf(1f, PHOTO_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
    val matrix = Matrix().apply {
        postRotate(image.imageInfo.rotationDegrees.toFloat())
        postScale(scale, scale)
    }
    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}.getOrNull()

@Composable
fun PageScanScreen(
    placeId: String,
    startPage: Int,
    collections: List<Collection>,
    cardIndexRepository: CardIndexRepository,
    onChange: (StorageChange) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val place = placesOf(collections).firstOrNull { it.id == placeId }
    val pockets = place?.pockets ?: 9
    val cardRepository = remember { CardRepository() }
    val reader = remember { PageReader(cardIndexRepository, cardRepository) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { reader.close(); cameraExecutor.shutdown() } }
    LaunchedEffect(Unit) { cardIndexRepository.refresh() }

    var page by remember { mutableIntStateOf(startPage.coerceAtLeast(1)) }
    var phase by remember { mutableStateOf(Phase.CAMERA) }
    var cells by remember { mutableStateOf<List<PageCell>>(emptyList()) }
    var cardData by remember { mutableStateOf<Map<String, ScryfallCard>>(emptyMap()) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var choosing by remember { mutableStateOf<PageCell?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    LaunchedEffect(Unit) { if (!hasCamera) permission.launch(Manifest.permission.CAMERA) }

    // The guide: the page's shape, as wide as it fits.
    val aspect = pageAspect(pockets)
    val guideW = minOf(previewSize.width * 0.92f, previewSize.height * 0.7f * aspect)
    val guideH = if (aspect > 0f) guideW / aspect else 0f

    fun entryOf(card: CellCard): CollectionEntry {
        val data = cardData[card.scryfallId]
        return if (data != null) CollectionEntry(data.id, data.name, data.displayImageUrl, backImageUrl = data.backImageUrl, tags = data.tags)
        else CollectionEntry(card.scryfallId, card.name, null)
    }

    /** Fetches the cards [list] shows that aren't here yet; [onLoaded] gets every card known once they're in. */
    fun loadCards(list: List<PageCell>, onLoaded: (Map<String, ScryfallCard>) -> Unit = {}) {
        val ids = list.flatMap { listOfNotNull(it.card?.scryfallId) + it.options.map { o -> o.scryfallId } }.distinct().filter { it !in cardData }
        if (ids.isEmpty()) {
            onLoaded(cardData)
            return
        }
        scope.launch {
            val got = runCatching { cardRepository.getCardsByIds(ids) }.getOrDefault(emptyList())
            cardData = cardData + got.associateBy { it.id }
            onLoaded(cardData)
        }
    }

    /** One sound for the page, not one a pocket: the best card read on it (Settings › Scanner). */
    fun cuePage(list: List<PageCell>, known: Map<String, ScryfallCard>) {
        bestCue(list.mapNotNull { cell -> cell.card?.let { known[it.scryfallId] }?.let { ScanFeedback.cueOf(it) } })?.let { ScanFeedback.play(it) }
    }

    fun record(): String? {
        val p = place ?: return null
        val shown = cells
        val onPage = page
        val result = recordPage(collections, p, onPage, shown, ::entryOf)
        onChange { recordPage(it, p, onPage, shown, ::entryOf).collections }
        return recordedLine(onPage, result)
    }

    fun takePhoto() {
        val capture = imageCapture ?: return
        val size = previewSize
        if (size.width <= 0 || size.height <= 0 || guideW <= 0f) return
        val wFrac = guideW / size.width
        val hFrac = guideH / size.height
        phase = Phase.READING
        message = null
        capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val photo = photoOf(image)
                image.close()
                scope.launch {
                    val area = photo?.let { guideInImage(it.width, it.height, size.width, size.height, wFrac, hFrac) }
                    if (photo == null || area == null) {
                        message = "Couldn't take the photo — try again."
                        phase = Phase.CAMERA
                        return@launch
                    }
                    val read = runCatching { reader.read(photo, area, pockets) }.getOrNull()
                    if (read == null) {
                        message = "Couldn't read the page — try again."
                        phase = Phase.CAMERA
                        return@launch
                    }
                    cells = read
                    checking = false
                    if (!reader.canSee) message = "Card recognition is still downloading, so only titles were read. Tap a pocket to fix it."
                    loadCards(read) { known -> cuePage(read, known) }
                    phase = Phase.RESULTS
                }
            }

            override fun onError(exception: ImageCaptureException) {
                scope.launch {
                    message = "Couldn't take the photo — try again."
                    phase = Phase.CAMERA
                }
            }
        })
    }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Column(Modifier.fillMaxSize()) {
            // Close, the binder's name and the page.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(colors.surface)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.textPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(place?.name ?: "Binder", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Page $page · whole page", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                }
            }
            if (phase != Phase.RESULTS) {
                Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { previewSize = it }) {
                    if (!hasCamera) {
                        Text("Camera access is needed to scan a page. Grant it to continue.", color = colors.textMuted, modifier = Modifier.padding(24.dp))
                    } else {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                                providerFuture.addListener({
                                    val provider = providerFuture.get()
                                    // Preview and photo the same shape, so the guide drawn on one is where it is in the other.
                                    val shape = ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
                                    val preview = Preview.Builder().setResolutionSelector(shape).build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                                    val capture = ImageCapture.Builder()
                                        .setResolutionSelector(shape)
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                        .build()
                                    if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) return@addListener
                                    provider.unbindAll()
                                    runCatching { provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture) }
                                    imageCapture = capture
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            }
                        )
                        // The page's pockets, drawn where the page should be held.
                        if (guideW > 0f) {
                            val (cols, rows) = pageGrid(pockets)
                            val line = colors.accent
                            Canvas(
                                Modifier
                                    .align(Alignment.Center)
                                    .size(with(density) { guideW.toDp() }, with(density) { guideH.toDp() })
                                    .semantics { contentDescription = "Hold the page inside the frame, one card in each box" }
                            ) {
                                val stroke = 2.dp.toPx()
                                drawRect(color = line, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                                for (c in 1 until cols) {
                                    val x = size.width * c / cols
                                    drawLine(line.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), stroke / 2)
                                }
                                for (r in 1 until rows) {
                                    val y = size.height * r / rows
                                    drawLine(line.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), stroke / 2)
                                }
                            }
                        }
                        if (phase == Phase.READING) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(colors.bg.copy(alpha = 0.85f)).padding(20.dp)
                            ) {
                                CircularProgressIndicator(color = colors.accent)
                                Text("Reading the pockets…", color = colors.textPrimary, modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        message ?: "Lay the page flat and fill the frame with it, one card in each box.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message != null) colors.warning else colors.textMuted
                    )
                    Button(
                        onClick = ::takePhoto,
                        enabled = hasCamera && imageCapture != null && phase == Phase.CAMERA,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Scan this page", fontWeight = FontWeight.ExtraBold) }
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    PageGrid(cells, pockets, cardData) { choosing = it }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(pageScanSummary(cells), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        pageScanHint(cells)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
                        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.accentLight) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                        Button(
                            onClick = { message = record() },
                            enabled = place != null && cells.any { it.state != CellState.CHOOSE },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                            modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                        ) { Text("Record this page", fontWeight = FontWeight.Bold, maxLines = 1) }
                        Button(
                            onClick = { checking = !checking },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = if (checking) colors.textPrimary else colors.textMuted),
                            modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                        ) { Text("Check against record", maxLines = 1) }
                    }
                    if (checking && place != null) {
                        val diff = pageDiff(recordedOnPage(place, collections, page), cells)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(pageDiffSummary(diff), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            diff.mapNotNull(::pageDiffText).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
                            if (diff.any { it.kind != com.mtgcompanion.app.data.PageDiffKind.SAME }) {
                                Text("Record this page to make the binder match the photo.", style = MaterialTheme.typography.labelMedium, color = colors.textDim)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().background(colors.bg).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
                    Button(
                        onClick = { phase = Phase.CAMERA; message = null; checking = false },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                        modifier = Modifier.height(48.dp)
                    ) { Text("Retake", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = {
                            val done = record()
                            page += 1
                            cells = emptyList()
                            checking = false
                            message = done
                            phase = Phase.CAMERA
                        },
                        enabled = place != null,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Save · next page", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }

    choosing?.let { cell ->
        ChooseDialog(
            cell = cell,
            cardData = cardData,
            lookUp = { name -> runCatching { cardRepository.getByFuzzyName(name) }.getOrNull() },
            onPick = { picked, data ->
                if (data != null) cardData = cardData + (data.id to data)
                cells = cells.map { if (it.slot == cell.slot) chooseCell(it, picked) else it }
                choosing = null
            },
            onDismiss = { choosing = null }
        )
    }
}

/** The page as read: each pocket's card (green), "Which one?" (amber) or Empty. */
@Composable
private fun PageGrid(cells: List<PageCell>, pockets: Int, cardData: Map<String, ScryfallCard>, onTap: (PageCell) -> Unit) {
    val colors = LocalAppColors.current
    val (cols, _) = pageGrid(pockets)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface).padding(12.dp)
    ) {
        cells.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { cell ->
                    val shape = RoundedCornerShape(6.dp)
                    val tint = when (cell.state) {
                        CellState.READ -> colors.success
                        CellState.CHOOSE -> colors.warning
                        CellState.EMPTY -> colors.textDim
                    }
                    val label = when (cell.state) {
                        CellState.READ -> cell.card?.name.orEmpty()
                        CellState.CHOOSE -> "Which one?"
                        CellState.EMPTY -> "Empty"
                    }
                    val what = when (cell.state) {
                        CellState.READ -> "Slot ${cell.slot}: ${label}, ${cell.card?.let { "${it.set.uppercase()} ${it.number}".trim() }.orEmpty()}"
                        CellState.CHOOSE -> "Slot ${cell.slot}: could be ${couldBe(cell)}. Tap to choose."
                        CellState.EMPTY -> "Slot ${cell.slot}: empty"
                    }
                    Box(
                        contentAlignment = if (cell.state == CellState.EMPTY) Alignment.Center else Alignment.BottomStart,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(63f / 88f)
                            .clip(shape)
                            .background(if (cell.state == CellState.EMPTY) Color.Transparent else colors.surface3)
                            .border(2.dp, if (cell.state == CellState.EMPTY) colors.border else tint, shape)
                            .clickable { onTap(cell) }
                            .semantics { contentDescription = what }
                            .padding(4.dp)
                    ) {
                        Text(label, fontSize = 10.sp, fontWeight = if (cell.state == CellState.EMPTY) FontWeight.Normal else FontWeight.Bold, color = tint, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Settling one pocket: one of the candidates, empty, or a card typed by name. */
@Composable
private fun ChooseDialog(
    cell: PageCell,
    cardData: Map<String, ScryfallCard>,
    lookUp: suspend (String) -> ScryfallCard?,
    onPick: (CellCard?, ScryfallCard?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val options = (listOfNotNull(cell.card) + cell.options).distinctBy { it.scryfallId }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Slot ${cell.slot}", color = colors.accentLight) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (cell.state == CellState.CHOOSE) Text("It could be ${couldBe(cell)}. Which one is it?", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                options.forEach { o ->
                    val data = cardData[o.scryfallId]
                    val printing = listOfNotNull(data?.setName ?: o.set.uppercase().takeIf { it.isNotEmpty() }, (data?.collectorNumber ?: o.number).takeIf { it.isNotEmpty() }?.let { "#$it" }).joinToString(" · ")
                    TextButton(onClick = { onPick(o, data) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(o.name, color = colors.textPrimary, fontWeight = if (o == cell.card) FontWeight.Bold else FontWeight.Normal)
                            if (printing.isNotEmpty()) Text(printing, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        }
                    }
                }
                TextButton(onClick = { onPick(null, null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Empty pocket", color = colors.textPrimary, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it; error = null },
                    label = { Text("Or type the card's name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = typed.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val found = lookUp(typed.trim())
                        busy = false
                        if (found == null) error = "No card called \"${typed.trim()}\"."
                        else onPick(CellCard(found.id, found.name, found.set.orEmpty(), found.collectorNumber.orEmpty()), found)
                    }
                }
            ) { Text(if (busy) "Looking…" else "Use this name", color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}
