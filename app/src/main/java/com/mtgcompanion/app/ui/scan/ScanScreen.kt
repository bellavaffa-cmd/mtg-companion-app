package com.mtgcompanion.app.ui.scan

import com.mtgcompanion.app.ui.theme.LocalAppColors
import androidx.activity.compose.BackHandler
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.UNSORTED_COLLECTION_NAME
import com.mtgcompanion.app.data.UNSORTED_COLLECTION_ID
import com.mtgcompanion.app.data.GUIDE_WIDTH
import com.mtgcompanion.app.data.GUIDE_HEIGHT
import androidx.compose.ui.layout.onSizeChanged
import com.mtgcompanion.app.data.scannedTwiceOver
import com.mtgcompanion.app.data.repeatedCards
import com.mtgcompanion.app.data.onlyRepeats
import com.mtgcompanion.app.data.copyNumber
import com.mtgcompanion.app.data.ScanRow
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.mtgcompanion.app.BuildConfig
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldDim
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import com.mtgcompanion.app.data.social.AppLink
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.social.AppLinkPanel
import com.mtgcompanion.app.ui.social.qrScanner
import com.mtgcompanion.app.ui.social.rememberAppLinkHandler

/** The framing guide's brief "got it" flash color on a successful scan. */
private val SuccessGreen = Color(0xFF4CAF50)

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    social: SocialRepository,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit = {},
    onOpenSharedLink: (String) -> Unit = {},
    onOpenRemote: ((matchId: String, seat: Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsState()
    val decks by viewModel.decks.collectAsState()
    val collections by viewModel.collections.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var deckPickerCard by remember { mutableStateOf<ScanRow?>(null) }
    var collectionPickerCard by remember { mutableStateOf<ScanRow?>(null) }
    // The whole pile at once, rather than a card at a time.
    var deckPickerForAll by remember { mutableStateOf(false) }
    var collectionPickerForAll by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    // Cards scanned but not put away yet: leaving would throw them away, so it asks first.
    var confirmLeave by remember { mutableStateOf(false) }
    val leave = {
        if (state.scannedCards.isEmpty()) onBack() else confirmLeave = true
    }
    BackHandler(enabled = state.scannedCards.isNotEmpty() && !showList) { confirmLeave = true }
    var showManualAdd by remember { mutableStateOf(false) }

    // Bound once the camera provider resolves, so the torch button has something to control.
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    // A separate still-capture use case from the continuous analysis stream — "identify by art"
    // wants one real, full-quality photo, not a YUV analysis frame.
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // A brief "got it" flash on the framing guide + a haptic buzz on every successful add,
    // alongside the existing shutter sound — successToken only changes on a real success (not on
    // a failed lookup, which also uses state.status), so this can't misfire on those.
    val haptic = LocalHapticFeedback.current
    val successFlash = remember { Animatable(0f) }
    LaunchedEffect(state.successToken) {
        if (state.successToken > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            successFlash.snapTo(1f)
            successFlash.animateTo(0f, animationSpec = tween(500))
        }
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    // The app's own QR codes (a friend, a life counter seat, a share link) work here too, so there's
    // no need to find the QR scanner. Every third frame is enough to catch one.
    val links = rememberAppLinkHandler(social, onOpenSharedLink, onOpenRemote)
    val overview by social.overview.collectAsState()
    val qrReader = remember { qrScanner() }
    val frameCount = remember { AtomicInteger() }
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown(); qrReader.close() } }

    // The preview's size in pixels: with it the scanner can place the framing guide in the
    // camera's own picture and leave the next card along unread.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .onSizeChanged { viewModel.previewSized(it.width, it.height) }
    ) {
        if (!hasCameraPermission) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Camera access is needed to scan a card. Grant it to continue.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            OverlayBackButton(leave)
            return@Box
        }

        // Full-screen camera preview.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        // ~1080p: enough detail to read the tiny set code + collector number at the
                        // card's bottom edge (for exact-edition detection), while KEEP_ONLY_LATEST and
                        // the ViewModel's stability/dedupe gating keep the workload in check.
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(1920, 1080),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                                    )
                                )
                                .build()
                        )
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage == null) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                // While a code's panel is up, cards wait.
                                val readCard = {
                                    if (links.showing) imageProxy.close()
                                    else viewModel.onFrame(inputImage, onProcessed = { imageProxy.close() })
                                }
                                if (frameCount.incrementAndGet() % 3 == 0) {
                                    qrReader.process(inputImage).addOnCompleteListener { task ->
                                        val text = if (task.isSuccessful) task.result.firstNotNullOfOrNull { it.rawValue } else null
                                        if (text != null && AppLink.parse(text) != null) {
                                            ContextCompat.getMainExecutor(ctx).execute { links.handle(text, ignoreOthers = true) }
                                            imageProxy.close()
                                        } else {
                                            readCard()
                                        }
                                    }
                                } else {
                                    readCard()
                                }
                            }
                        }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    // The screen may already be gone by the time the camera is ready: binding to a
                    // finished screen crashes.
                    if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) return@addListener
                    cameraProvider.unbindAll()
                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                        capture
                    )
                    imageCapture = capture
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // Framing guide so the user knows to fill the frame with the card title — briefly
        // flashes green with a checkmark on a successful add (successFlash, above).
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(GUIDE_WIDTH)
                .fillMaxHeight(GUIDE_HEIGHT)
                .border(
                    BorderStroke(
                        (2 + 3 * successFlash.value).dp,
                        if (successFlash.value > 0f) {
                            SuccessGreen.copy(alpha = 0.5f + 0.5f * successFlash.value)
                        } else {
                            Gold.copy(alpha = 0.5f)
                        }
                    ),
                    RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (successFlash.value > 0f) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = SuccessGreen.copy(alpha = successFlash.value),
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        // Top overlay: back + torch/capture/manual-add + status pill + (debug) test button.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScrimIconButton(onClick = leave, icon = Icons.Filled.ArrowBack, desc = "Back")
                Box(modifier = Modifier.weight(1f))
                if (camera?.cameraInfo?.hasFlashUnit() == true) {
                    ScrimIconButton(
                        onClick = {
                            torchOn = !torchOn
                            camera?.cameraControl?.enableTorch(torchOn)
                        },
                        icon = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        desc = if (torchOn) "Turn off flashlight" else "Turn on flashlight",
                        tint = if (torchOn) Gold else TextMuted
                    )
                }
                ScrimIconButton(
                    onClick = { viewModel.captureNow() },
                    icon = Icons.Filled.PhotoCamera,
                    desc = "Scan now"
                )
                ScrimIconButton(
                    onClick = { showManualAdd = true },
                    icon = Icons.Filled.Keyboard,
                    desc = "Type a card name"
                )
                ScrimIconButton(
                    onClick = {
                        imageCapture?.takePicture(
                            cameraExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bitmap = imageProxyToBitmap(image)
                                    image.close()
                                    if (bitmap != null) viewModel.matchByArt(bitmap)
                                }

                                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                                    // Swallowed — matchByArt's own "couldn't match" status covers the
                                    // user-facing failure case; a capture error is rare and transient.
                                }
                            }
                        )
                    },
                    icon = Icons.Filled.ImageSearch,
                    desc = "Identify by art"
                )
                if (BuildConfig.DEBUG) {
                    ScrimIconButton(
                        onClick = {
                            Thread {
                                runCatching {
                                    val bitmap = context.assets.open("test_card.png").use { BitmapFactory.decodeStream(it) }
                                    viewModel.debugScan(InputImage.fromBitmap(bitmap, 0))
                                }
                            }.start()
                        },
                        icon = Icons.Filled.Science,
                        desc = "Scan test card"
                    )
                }
            }
            state.status?.let { status ->
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldLight,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Bg.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Bottom overlay: view-list button.
        Button(
            onClick = { showList = true },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
        ) {
            Text(
                "View list (${state.scannedCards.size})",
                style = MaterialTheme.typography.labelLarge,
                color = Bg
            )
        }

        // A friend's code, a life counter seat or a share link the camera just read.
        if (links.showing) {
            AppLinkPanel(links, overview?.me, onDone = links::dismiss, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }

        // Slide-up list panel.
        if (showList) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(onClick = { showList = false })
            )
            ScannedListPanel(
                cards = state.scannedCards,
                onClose = { showList = false },
                onCardClick = { showList = false; onCardClick(it.card.name) },
                onAddToCollection = { collectionPickerCard = it },
                onAddToDeck = { deckPickerCard = it },
                onScanAgain = { viewModel.scanAgain(it.card) },
                onRemove = { viewModel.removeScan(it.id) },
                onAllToDeck = { deckPickerForAll = true },
                onAllToCollection = { collectionPickerForAll = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (confirmLeave) {
        val waiting = state.scannedCards.size
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = Surface,
            title = { Text("Leave $waiting ${if (waiting == 1) "scan" else "scans"} behind?", color = GoldLight) },
            text = {
                Text(
                    "They haven't been put into a deck or binder yet, and leaving throws them away.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; showList = true }) { Text("Put them away", color = Gold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false; viewModel.clearScanned(); onBack() }) {
                    Text("Leave", color = LocalAppColors.current.error)
                }
            }
        )
    }

    if (deckPickerForAll) {
        DeckPickerDialog(
            decks = decks,
            onDismiss = { deckPickerForAll = false },
            onPickDeck = { deckId ->
                deckPickerForAll = false
                showList = false
                viewModel.addAllToDeck(deckId)
            },
            onCreateDeck = { name ->
                deckPickerForAll = false
                showList = false
                viewModel.createDeckAndAddAll(name)
            }
        )
    }

    if (collectionPickerForAll) {
        CollectionPickerDialog(
            // Cards you own but haven't sorted: offered even before the pile exists.
            collections = if (collections.any { it.isUnsorted }) collections
            else listOf(Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME)) + collections,
            onDismiss = { collectionPickerForAll = false },
            onPickCollection = { collectionId ->
                collectionPickerForAll = false
                showList = false
                viewModel.addAllToCollection(collectionId)
            },
            onCreateCollection = { name ->
                collectionPickerForAll = false
                showList = false
                viewModel.createCollectionAndAddAll(name)
            }
        )
    }

    deckPickerCard?.let { scanned ->
        // Filing a card files every copy of it in the pile, however many rows that is.
        val copies = state.scannedCards.count { it.card.id == scanned.card.id }
        DeckPickerDialog(
            decks = decks,
            onDismiss = { deckPickerCard = null },
            onPickDeck = { deckId ->
                deckPickerCard = null
                viewModel.addToDeck(scanned.card, copies, deckId)
            },
            onCreateDeck = { name ->
                deckPickerCard = null
                viewModel.createDeckAndAdd(scanned.card, copies, name)
            }
        )
    }

    collectionPickerCard?.let { scanned ->
        val copies = state.scannedCards.count { it.card.id == scanned.card.id }
        CollectionPickerDialog(
            collections = collections,
            onDismiss = { collectionPickerCard = null },
            onPickCollection = { collectionId ->
                collectionPickerCard = null
                viewModel.addToCollection(scanned.card, copies, collectionId)
            },
            onCreateCollection = { name ->
                collectionPickerCard = null
                viewModel.createCollectionAndAdd(scanned.card, copies, name)
            }
        )
    }

    if (showManualAdd) {
        ManualAddDialog(
            onDismiss = { showManualAdd = false },
            onAdd = { name ->
                showManualAdd = false
                viewModel.manualAdd(name)
            }
        )
    }
}

/** Type-and-add fallback for when the camera keeps missing a card or grabs the wrong one. */
@Composable
private fun ManualAddDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Type a card name", color = GoldLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("e.g. Sol Ring", color = TextDim) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Gold
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onAdd(name.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Add", color = Bg) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

@Composable
private fun CollectionPickerDialog(
    collections: List<com.mtgcompanion.app.data.Collection>,
    onDismiss: () -> Unit,
    onPickCollection: (String) -> Unit,
    onCreateCollection: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Add to binder", color = GoldLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                collections.forEach { collection ->
                    Text(
                        collection.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickCollection(collection.id) }
                            .padding(vertical = 10.dp)
                    )
                }
                if (collections.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(BorderColor))
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New binder name", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Gold
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (newName.isNotBlank()) onCreateCollection(newName.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Create & add", color = Bg) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

/** ImageCapture's default output format is JPEG — decode straight from the single plane's bytes
 * and correct for sensor rotation, same as every other capture-to-Bitmap conversion on Android. */
private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

@Composable
private fun OverlayBackButton(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(12.dp)
    ) {
        ScrimIconButton(onClick = onBack, icon = Icons.Filled.ArrowBack, desc = "Back")
    }
}

@Composable
private fun ScrimIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tint: Color? = null
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Bg.copy(alpha = 0.6f))
    ) {
        Icon(icon, contentDescription = desc, tint = tint ?: Gold)
    }
}

@Composable
private fun ScannedListPanel(
    cards: List<ScanRow>,
    onClose: () -> Unit,
    onCardClick: (ScanRow) -> Unit,
    onAddToCollection: (ScanRow) -> Unit,
    onAddToDeck: (ScanRow) -> Unit,
    onScanAgain: (ScanRow) -> Unit,
    onRemove: (ScanRow) -> Unit,
    onAllToDeck: () -> Unit,
    onAllToCollection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.6f)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Surface)
            .padding(16.dp)
    ) {
        // Cards read more than once: what a double scan or a wrong read looks like.
        val repeats = repeatedCards(cards)
        var repeatsOnly by remember { mutableStateOf(false) }
        // With nothing scanned twice the filter has nothing to hide, and its chip is gone.
        val filtered = repeatsOnly && repeats.isNotEmpty()
        val shown = if (filtered) onlyRepeats(cards) else cards

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Scanned (${cards.size})",
                style = MaterialTheme.typography.titleMedium,
                color = GoldLight
            )
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close list", tint = TextDim)
            }
        }
        if (repeats.isNotEmpty()) {
            Text(
                "${repeats.size} ${if (repeats.size == 1) "card" else "cards"} scanned more than once" +
                    if (filtered) " · showing those" else " · tap to show those",
                style = MaterialTheme.typography.labelMedium,
                color = Gold,
                modifier = Modifier.clickable { repeatsOnly = !repeatsOnly }.padding(vertical = 4.dp)
            )
        }
        if (cards.isEmpty()) {
            Text(
                "No cards scanned yet. Point the camera at a card.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "add-all") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    ) {
                        Button(
                            onClick = onAllToDeck,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("All to a deck", style = MaterialTheme.typography.labelLarge, color = Bg)
                        }
                        OutlinedButton(
                            onClick = onAllToCollection,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderColor),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("All to a binder", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        }
                    }
                }
                items(shown, key = { it.id }) { scanned ->
                    ScannedCardRow(
                        scanned = scanned,
                        copy = copyNumber(cards, scanned),
                        justNow = scannedTwiceOver(cards, scanned),
                        onClick = { onCardClick(scanned) },
                        onAddToCollection = { onAddToCollection(scanned) },
                        onAddToDeck = { onAddToDeck(scanned) },
                        onScanAgain = { onScanAgain(scanned) },
                        onRemove = { onRemove(scanned) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannedCardRow(
    scanned: ScanRow,
    /** Which copy of its card this row is: 1 the first time, 2 the next… */
    copy: Int,
    /** Whether the copy before it was scanned seconds ago — the camera catching one card twice. */
    justNow: Boolean,
    onClick: () -> Unit,
    onAddToCollection: () -> Unit,
    onAddToDeck: () -> Unit,
    onScanAgain: () -> Unit,
    onRemove: () -> Unit
) {
    val card = scanned.card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Bg)
            .border(BorderStroke(1.dp, if (justNow) Gold else BorderColor), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(
                model = card.displayImageUrl,
                contentDescription = card.name,
                modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(8.dp))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    card.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (copy > 1) {
                    Text(
                        if (justNow) "copy $copy · scanned just now" else "copy $copy",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (justNow) Gold else TextMuted
                    )
                }
            }
            // One more copy of this card: another row, as if it went past the camera again.
            IconButton(onClick = onScanAgain, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "One more copy", tint = Gold, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Take off this scan", tint = TextDim, modifier = Modifier.size(18.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(
                onClick = onAddToCollection,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("+ BINDER", style = MaterialTheme.typography.labelMedium, color = Bg) }
            OutlinedButton(
                onClick = onAddToDeck,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                border = BorderStroke(1.dp, BorderColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight)
            ) { Text("+ DECK", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun DeckPickerDialog(
    decks: List<Deck>,
    onDismiss: () -> Unit,
    onPickDeck: (String) -> Unit,
    onCreateDeck: (String) -> Unit
) {
    var newDeckName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Add to deck", color = GoldLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                decks.forEach { deck ->
                    Text(
                        deck.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickDeck(deck.id) }
                            .padding(vertical = 10.dp)
                    )
                }
                if (decks.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(BorderColor))
                }
                OutlinedTextField(
                    value = newDeckName,
                    onValueChange = { newDeckName = it },
                    label = { Text("New deck name", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Gold
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (newDeckName.isNotBlank()) onCreateDeck(newDeckName.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Create & add", color = Bg) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}
