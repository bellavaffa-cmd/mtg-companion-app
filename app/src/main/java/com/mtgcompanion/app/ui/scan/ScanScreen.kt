package com.mtgcompanion.app.ui.scan

import com.mtgcompanion.app.data.grouped
import androidx.compose.material3.minimumInteractiveComponentSize
import com.mtgcompanion.app.ui.common.toAddItem
import com.mtgcompanion.app.ui.common.AddCheck
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.AutoAwesome
import com.mtgcompanion.app.ui.common.AddToPicker
import com.mtgcompanion.app.ui.common.AddVerb
import com.mtgcompanion.app.ui.common.LocalAddToFeedback
import com.mtgcompanion.app.ui.common.addToMessage
import com.mtgcompanion.app.ui.common.asTarget
import com.mtgcompanion.app.ui.common.cardsSubject
import com.mtgcompanion.app.network.scryfall.canBeFoil
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Verified
import com.mtgcompanion.app.data.ScanMode
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.platform.LocalView
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
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.CheckScope
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.cardsIn
import com.mtgcompanion.app.data.looseCopies
import com.mtgcompanion.app.data.placePath
import com.mtgcompanion.app.data.reconcile
import com.mtgcompanion.app.ui.collection.PlacePickerDialog
import com.mtgcompanion.app.ui.collection.PlaceLabelPanel
import com.mtgcompanion.app.data.placeIdFromLabel
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
import kotlinx.coroutines.delay
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.resolutionselector.AspectRatioStrategy
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.SideEffect
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import com.mtgcompanion.app.data.AUTO_SETTLE_MS
import com.mtgcompanion.app.data.Refocus
import com.mtgcompanion.app.data.movedToRemeter
import com.mtgcompanion.app.data.AutoZoom
import com.mtgcompanion.app.data.CardSighting
import com.mtgcompanion.app.data.PreviewPoint
import com.mtgcompanion.app.data.REFOCUS_SETTLE_MS
import com.mtgcompanion.app.data.cardMeteringPoints
import com.mtgcompanion.app.data.guideToPreview
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.mtgcompanion.app.ui.theme.Surface2
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    onOpenRemote: ((matchId: String, seat: Int) -> Unit)? = null,
    /** A scanned box label's "Open box". */
    onOpenPlace: (String) -> Unit = {},
    /** A scanned box label's "Pull from here": the open pull list, only what's in that place. */
    onPullFrom: (deckId: String, placeId: String) -> Unit = { _, _ -> },
    /** Check mode's Finish check: the results (CheckResultsScreen). */
    onFinishCheck: (String) -> Unit = {},
    /** Put-away into a binder in order: Add cards in order (BinderFitScreen). */
    onFit: (String) -> Unit = {},
    /** Sorting with a recipe: Done, or Finish check — what went where (SortRecipesScreen's summary). */
    onRecipeDone: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsState()
    val decks by viewModel.decks.collectAsState()
    val collections by viewModel.collections.collectAsState()
    // Put-away mode: each card goes straight into this storage place (PutAwayPanel.kt).
    val putAwayTarget by viewModel.putAwayTarget.collectAsState()
    val session by viewModel.session.collectAsState()
    val putAwayPlace = putAwayTarget?.let { id -> placesOf(collections).firstOrNull { it.id == id } }
    var choosingPlace by remember { mutableStateOf(false) }
    // Scan-to-tick mode: each card ticks its row on a deck's pull or put-back list (PullList.kt).
    val tickList by viewModel.tickList.collectAsState()
    val tickCount by viewModel.tickCount.collectAsState()
    val tickDeck = tickList?.let { t -> decks.firstOrNull { it.id == t.deckId } }
    // A box label the camera read: its sheet (PlaceLabelPanel).
    val labelPlace by viewModel.labelPlace.collectAsState()
    // Check mode: each card is matched against what's listed in a place (CheckPanel.kt).
    val check by viewModel.check.collectAsState()
    // Sort mode: each card goes in a pile by the piles' rules (SortPanel.kt).
    val sort by viewModel.sort.collectAsState()
    // Recipe mode: each card goes in its pile by the sort's recipe (RecipeScanPanel.kt).
    val recipe by viewModel.recipe.collectAsState()
    val recipeVoice by viewModel.recipeVoice.collectAsState()
    // Scans a learned correction put right (ScanCorrections.kt), and the one whose "Learned" was tapped.
    val learned by viewModel.learned.collectAsState()
    var learnedOpen by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    // "It's a different card": the scan being fixed (its id, and whether it's the sort's newest), then
    // the printings of the card searched for, to pick from.
    var differentFor by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    var differentPick by remember { mutableStateOf<Triple<Long, Boolean, List<ScryfallCard>>?>(null) }
    val scope = rememberCoroutineScope()
    val money by com.mtgcompanion.app.data.Prices.money.collectAsState()
    val recipeOn = recipe != null
    LaunchedEffect(recipeOn) {
        if (!recipeOn) return@LaunchedEffect
        if (social.overview.value == null) runCatching { social.refresh() }
        runCatching { social.more.tradeMatches() }.onSuccess { matches ->
            viewModel.setTradeMatches(matches) { id -> social.overview.value?.person(id)?.let { it.displayName.ifEmpty { it.username } } }
        }
    }
    var recipeWrong by remember { mutableStateOf(false) }
    var recipePrinting by remember { mutableStateOf<ScryfallCard?>(null) }
    val checkPlace = check?.let { c -> placesOf(collections).firstOrNull { it.id == c.placeId } }
    val checkResult = remember(check, collections, decks) {
        check?.let { c -> reconcile(collections, decks, CheckScope(c.placeId, c.section), c.scans) }
    }

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

    // The scan whose Add to… picker is open — every copy of its card in the pile goes.
    var addingRow by remember { mutableStateOf<ScanRow?>(null) }
    val addTo = LocalAddToFeedback.current
    // The whole pile at once, rather than a card at a time.
    var addingAll by remember { mutableStateOf(false) }
    // The row picking the printing it's really holding, when the set code couldn't be read.
    var artPickerRow by remember { mutableStateOf<ScanRow?>(null) }
    var showList by remember { mutableStateOf(false) }
    // Scanning a pile is minutes of not touching the screen: don't let it dim and lock.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // Cards scanned but not put away yet: leaving would throw them away, so it asks first.
    var confirmLeave by remember { mutableStateOf(false) }
    val leave = {
        if (putAwayTarget != null || tickList != null || check != null || sort != null || recipe != null || state.scannedCards.isEmpty()) onBack() else confirmLeave = true
    }
    BackHandler(enabled = putAwayTarget == null && tickList == null && check == null && sort == null && recipe == null && state.scannedCards.isNotEmpty() && !showList) { confirmLeave = true }
    var showManualAdd by remember { mutableStateOf(false) }

    // Bound once the camera provider resolves, so the torch button has something to control.
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    // A separate still-capture use case from the continuous analysis stream — "identify by art"
    // wants one real, full-quality photo, not a YUV analysis frame.
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    /** The preview, once made: focus and metering points are placed in its pixels. */
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    // The zoom: SCAN_ZOOM to start with, then whatever was last chosen on this phone — pinch on the
    // preview, or − 1.8× + under the top bar (ScanZoomControl.kt). Moving it counts as the card moving.
    val zoom = rememberScanZoom("card", onMoved = viewModel::zoomMoved)
    DisposableEffect(Unit) { onDispose { viewModel.setPinching(false); viewModel.autoCamera = false } }

    // Auto zoom and focus (Settings › Scanner; ScanAutoCamera.kt): the card's edges and crispness,
    // looked at a few times a second, zoom the card to fill the guide and refocus it when it goes soft.
    val autoOn by remember { AutoCameraSetting.flow(context) }.collectAsState()
    val autoLatest by androidx.compose.runtime.rememberUpdatedState(autoOn)
    SideEffect {
        zoom.autoEnabled = autoOn
        viewModel.autoCamera = autoOn
    }
    val autoZoom = remember { AutoZoom() }
    val autoFocus = remember { Refocus() }
    // Where focus was last aimed on the card, in guide fractions; re-aimed once the card moves off it.
    var aimedAt by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    // The card as last seen, for the periodic refocus to aim at; null when it wasn't found.
    var recentCard by remember { mutableStateOf<CardSighting?>(null) }

    /** Focus at [af] and meter the exposure at [ae] (or both at [af]), in the preview's pixels. */
    fun meter(af: PreviewPoint, ae: PreviewPoint?) {
        val pv = previewViewRef ?: return
        val cam = camera ?: return
        runCatching {
            val points = pv.meteringPointFactory
            val action = FocusMeteringAction.Builder(
                points.createPoint(af.x, af.y),
                if (ae == null) FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE else FocusMeteringAction.FLAG_AF
            )
            if (ae != null) action.addPoint(points.createPoint(ae.x, ae.y), FocusMeteringAction.FLAG_AE)
            // Left to settle rather than cancelling back to continuous drift.
            cam.cameraControl.startFocusAndMetering(action.disableAutoCancel().build())
        }
    }

    /**
     * Focus on the card — its middle, with the exposure metered on its title bar so a foil's glare
     * doesn't darken it — or on the middle of the guide when it hasn't been found.
     */
    fun focusOnCard(card: CardSighting?) {
        val pv = previewViewRef ?: return
        aimedAt = card?.let { it.centreX to it.centreY }
        if (card == null) {
            meter(PreviewPoint(pv.width / 2f, pv.height / 2f), null)
        } else {
            val (focus, exposure) = cardMeteringPoints(card)
            meter(
                guideToPreview(focus.first, focus.second, pv.width, pv.height),
                guideToPreview(exposure.first, exposure.second, pv.width, pv.height)
            )
        }
    }

    // A tap on the preview focuses there, and auto refocus leaves it be for a few seconds.
    val tapToFocus = remember<(Offset) -> Unit> {
        { at: Offset ->
            autoFocus.tapped(SystemClock.elapsedRealtime())
            meter(PreviewPoint(at.x, at.y), null)
        }
    }

    // Focus drifts off a card held in a bright, featureless box, and a soft frame is the one thing
    // the card index cannot survive. Asking again every few seconds costs nothing visible — aimed at
    // the card when auto focus has found it, at the guide's middle otherwise; not after a tap.
    LaunchedEffect(previewViewRef, camera) {
        if (previewViewRef == null || camera == null) return@LaunchedEffect
        while (true) {
            val now = SystemClock.elapsedRealtime()
            if (!autoFocus.paused(now)) {
                focusOnCard(if (autoLatest) recentCard else null)
                if (autoLatest) autoFocus.focused(now)
            }
            delay(3_000)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.cardLooks.collect { look ->
            if (!autoLatest) return@collect
            recentCard = look.card
            val now = SystemClock.elapsedRealtime()
            if (zoom.auto && zoom.range.canZoom) {
                autoZoom.next(look.card, zoom.ratio, zoom.range, now)?.let { target ->
                    zoom.setAuto(target)
                    viewModel.holdStill(AUTO_SETTLE_MS)
                    autoFocus.reset()
                }
            } else {
                autoZoom.reset()
            }
            val card = look.card
            val aimed = aimedAt
            if (autoFocus.onLook(look.sharp, card != null, now, look.titleRead)) {
                // Soft for a while, and the title won't read: focus again, on the card.
                focusOnCard(card)
                viewModel.holdStill(REFOCUS_SETTLE_MS)
            } else if (card != null && (aimed == null || movedToRemeter(card, aimed.first, aimed.second)) && autoFocus.mayFocus(now)) {
                // The card has moved off where focus was aimed: aim at it again.
                autoFocus.focused(now)
                focusOnCard(card)
                viewModel.holdStill(REFOCUS_SETTLE_MS)
            }
        }
    }

    // A brief "got it" flash on the framing guide on every successful add — successToken only
    // changes on a real success (not on a failed lookup, which also uses state.status), so this can't
    // misfire on those. The sound and buzz come from ScanFeedback, as the card is recognised.
    val successFlash = remember { Animatable(0f) }
    LaunchedEffect(state.successToken) {
        if (state.successToken > 0) {
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
                        // card's bottom edge, while KEEP_ONLY_LATEST and the ViewModel's gating keep
                        // the workload in check.
                        //
                        // This was briefly raised to 1440p, to give the card index more pixels to
                        // work from. Measured on the phone, that took a sight lookup from ~200 ms to
                        // ~14 s — seventy times worse for 1.8x the pixels, so not the model's own
                        // cost but the churn of copying and flattening frames that size. A lookup
                        // that slow doesn't merely feel broken: the camera carries on, the card in
                        // front of it changes, and the answer arrives against the wrong one.
                        //
                        // The aspect ratio has to be asked for as well, and this is not a detail:
                        // without it the selector defaults to 4:3 and quietly hands back 1080x1440,
                        // whatever size is requested. Measured on the phone, that put the card at
                        // 1440 px of frame and the set line's letters at 14 px even with the guide
                        // filled — under the reader's floor, so the set code could never read. At
                        // 16:9 the same framing gives 1920 px of frame and about 18 px of letter.
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
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
                                    if (links.showing || viewModel.labelPlace.value != null) imageProxy.close()
                                    else viewModel.onFrame(
                                        inputImage,
                                        // Only taken when the small print needs a second, closer look.
                                        frame = { runCatching { imageProxy.toBitmap() }.getOrNull() },
                                        onProcessed = { imageProxy.close() }
                                    )
                                }
                                if (frameCount.incrementAndGet() % 3 == 0) {
                                    qrReader.process(inputImage).addOnCompleteListener { task ->
                                        val text = if (task.isSuccessful) task.result.firstNotNullOfOrNull { it.rawValue } else null
                                        if (text != null && AppLink.parse(text) != null) {
                                            ContextCompat.getMainExecutor(ctx).execute { links.handle(text, ignoreOthers = true) }
                                            imageProxy.close()
                                        } else if (text != null && placeIdFromLabel(text) != null) {
                                            // A box label: its place's sheet (PlaceLabel.kt).
                                            ContextCompat.getMainExecutor(ctx).execute { viewModel.onLabel(text) }
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
                    // Zoomed in so the card fills the frame from where it is comfortable to hold it
                    // (see SCAN_ZOOM), or to the zoom last chosen here. The default stays under the
                    // ratio where the phone switches to a telephoto lens, which could not focus this
                    // close, and everything is clamped to what this phone actually offers — a device
                    // with no zoom to give simply stays where it is, and shows no zoom control.
                    camera?.let { bound -> runCatching { zoom.bind(bound, lifecycleOwner) } }
                    // Focus on the middle of the guide, which is where the card is — a white box at
                    // arm's length gives continuous autofocus almost nothing to lock onto, and a soft
                    // frame is the one thing the card index cannot survive. Re-asked for periodically
                    // rather than once (above): the card moves, and focus drifts back off it.
                    previewViewRef = previewView
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // Two fingers on the preview zoom it; a tap focuses where it lands. Under every other overlay,
        // so their own taps are theirs.
        Box(
            Modifier
                .fillMaxSize()
                .pinchToZoom(zoom, onPinching = viewModel::setPinching, onTap = tapToFocus)
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
                ScrimIconButton(onClick = leave, icon = Icons.AutoMirrored.Filled.ArrowBack, desc = "Back")
                if (putAwayPlace != null) {
                    PutAwayTarget(putAwayPlace.name, onClick = { choosingPlace = true }, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                } else if (check != null) {
                    val c = check
                    val what = when (checkPlace?.placeKind) { PlaceKind.BINDER -> "binder"; PlaceKind.BOX -> "box"; else -> "place" }
                    CheckTarget(
                        "Checking: " + listOfNotNull(checkPlace?.let { placePath(placesOf(collections), it.id) } ?: "a place", c?.section).joinToString(" › "),
                        wholeLabel = if (c?.section != null) "Whole $what" else null,
                        onWhole = viewModel::checkWholePlace,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                } else if (tickList != null) {
                    TickTarget(
                        (if (tickList?.pull == true) "Ticking off: pull list for " else "Ticking off: put back list for ") + (tickDeck?.name ?: "a deck") +
                            (tickCount?.let { " · ${it.first} of ${it.second}" } ?: ""),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                } else {
                    Box(modifier = Modifier.weight(1f))
                }
                // Everything else lives behind one button. The camera wants the screen, not a row
                // of icons over it, and all of these are things you reach for occasionally.
                Box {
                    ScrimIconButton(onClick = { menuOpen = true }, icon = Icons.Filled.MoreVert, desc = "Scanner options")
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = Surface
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (state.scanMode == ScanMode.FAST) "Fast scanning" else "Accurate scanning", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Filled.Verified, null, tint = Gold) },
                            trailingIcon = { Text(if (state.scanMode == ScanMode.FAST) "switch to accurate" else "switch to fast", style = MaterialTheme.typography.labelSmall, color = TextMuted) },
                            onClick = {
                                viewModel.setScanMode(if (state.scanMode == ScanMode.FAST) ScanMode.ACCURATE else ScanMode.FAST)
                                menuOpen = false
                            }
                        )
                        if (camera?.cameraInfo?.hasFlashUnit() == true) {
                            DropdownMenuItem(
                                text = { Text(if (torchOn) "Turn off the light" else "Turn on the light", color = TextPrimary) },
                                leadingIcon = { Icon(if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff, null, tint = if (torchOn) Gold else TextMuted) },
                                onClick = {
                                    torchOn = !torchOn
                                    camera?.cameraControl?.enableTorch(torchOn)
                                    menuOpen = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Scan now", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Filled.PhotoCamera, null, tint = TextMuted) },
                            onClick = { viewModel.captureNow(); menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Identify by art", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Filled.ImageSearch, null, tint = TextMuted) },
                            onClick = {
                                menuOpen = false
                                imageCapture?.takePicture(
                                    cameraExecutor,
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            val bitmap = imageProxyToBitmap(image)
                                            image.close()
                                            if (bitmap != null) viewModel.matchByArt(bitmap)
                                        }

                                        override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                                            // Swallowed — matchByArt's own "couldn't match" status covers
                                            // the user-facing failure; a capture error is rare and transient.
                                        }
                                    }
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Type a card name", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Filled.Keyboard, null, tint = TextMuted) },
                            onClick = { showManualAdd = true; menuOpen = false }
                        )
                    }
                }
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
            // The status line on the left, the zoom on the right — both above the framing guide.
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    state.status?.let { status ->
                        // TalkBack reads each new line as it comes, a recognised card's with its rarity ("Rare").
                        val spoken = state.statusRarity?.takeIf { it.first == status }?.let { "$status, ${it.second}" } ?: status
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldLight,
                            modifier = Modifier
                                .semantics {
                                    liveRegion = LiveRegionMode.Polite
                                    contentDescription = spoken
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(Bg.copy(alpha = 0.7f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    // "Goal: Duskmourn uncommons 41/92" — small, under the card that moved a goal on.
                    state.goalNote?.takeIf { it.first == state.status }?.let { (_, line) ->
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldLight,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Bg.copy(alpha = 0.6f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                    ZoomControl(zoom)
                    // Past the lens switch the phone may be on a telephoto that can't focus close.
                    FartherHint(zoom, "card", modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        // Put-away mode: the card just put away and the session, instead of the pile.
        if (putAwayTarget != null) {
            // A binder in order: the cards wait beside it, to be fitted in (BinderFitScreen).
            val waiting = putAwayPlace?.takeIf { it.placeKind == PlaceKind.BINDER && it.rule != null }
                ?.let { looseCopies(it, cardsIn(collections, it.id)).size } ?: 0
            PutAwayPanel(
                session = session,
                onUndoLast = viewModel::undoLastPutAway,
                onAnotherCopy = viewModel::anotherCopy,
                waitingToFit = waiting,
                onFit = { putAwayPlace?.let { onFit(it.id) } },
                // A place with a size that's full, or nearly (BoxSpace.kt).
                spaceWarning = putAwayPlace?.let { p ->
                    com.mtgcompanion.app.data.spaceOf(p, collections)?.let { space ->
                        when {
                            space.room <= 0 -> com.mtgcompanion.app.data.overflowLine(com.mtgcompanion.app.data.Overflow(p.id, p.name, 1, space.room))
                            space.nearlyFull -> "${p.name}: " + com.mtgcompanion.app.data.roomLine(space).replaceFirstChar { it.lowercase() }
                            else -> null
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Check mode: how far it's got, the last card and the ones that don't belong.
        val checking = check
        if (checking != null && checkResult != null && putAwayTarget == null) {
            CheckPanel(
                result = checkResult,
                scanned = checking.scans.size,
                onUndoLast = viewModel::undoLastCheckScan,
                onFinish = { onFinishCheck(checking.placeId) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Sort mode: the card's pile, big, and the piles so far.
        sort?.let { sorting ->
            SortPanel(
                session = sorting,
                collections = collections,
                onChange = viewModel::setSort,
                onDone = viewModel::fileSort,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Recipe mode: the card's pile, big, said out loud — the Scan and Smart mockups.
        recipe?.let { sorting ->
            val derived = remember(sorting.recipe, money) { viewModel.recipePiles(sorting) }
            val ctx = remember(sorting.scans.size, collections, decks) { viewModel.smartContext() }
            RecipeScanPanel(
                session = sorting,
                derived = derived,
                ctx = ctx,
                voice = recipeVoice,
                money = money,
                onUndo = viewModel::undoRecipe,
                onWrong = { recipeWrong = true },
                learned = sorting.scans.lastOrNull()?.id?.let { it in learned } == true,
                onLearned = { sorting.scans.lastOrNull()?.let { learnedOpen = it.id to true } },
                onSend = viewModel::sendRecipeTo,
                onPutInDeck = viewModel::putRecipeCardInDeck,
                onApart = viewModel::toggleRecipeApart,
                onDone = onRecipeDone,
                onFinishCheck = { viewModel.finishRecipeCheck(); onRecipeDone() },
                onScanNow = viewModel::captureNow,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Bottom overlay: view-list button.
        if (putAwayTarget == null && tickList == null && check == null && sort == null && recipe == null) Button(
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

        // A box label the camera just read: put cards away here, open the box, or pull from it.
        labelPlace?.let { id ->
            PlaceLabelPanel(
                placeId = id,
                collections = collections,
                decks = decks,
                onPutAway = { place -> viewModel.closeLabel(); viewModel.setPutAwayTarget(place) },
                onOpen = { place -> viewModel.closeLabel(); onOpenPlace(place) },
                onPull = { deckId, place -> viewModel.closeLabel(); onPullFrom(deckId, place) },
                onDismiss = viewModel::closeLabel,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
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
                onAddTo = { addingRow = it },
                onScanAgain = { viewModel.scanAgain(it.card) },
                onRemove = { viewModel.removeScan(it.id) },
                onPickArt = { artPickerRow = it },
                learned = learned.keys,
                onLearned = { learnedOpen = it.id to false },
                onFoil = { row, foil -> viewModel.setFoil(row.id, foil) },
                onAllTo = { addingAll = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (choosingPlace) {
        PlacePickerDialog("Put cards away into…", placesOf(collections), onDismiss = { choosingPlace = false }) { id ->
            choosingPlace = false
            viewModel.setPutAwayTarget(id)
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

    if (recipeWrong) recipe?.scans?.lastOrNull()?.let { last ->
        AlertDialog(
            onDismissRequest = { recipeWrong = false },
            containerColor = Surface,
            title = { Text("Not ${last.name}?", color = GoldLight) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Right card, wrong printing: pick the one you're holding. A different card: rescan it — it's taken off its pile first — or say which card it is, and the scanner learns it.", color = TextMuted)
                    TextButton(onClick = { recipeWrong = false; differentFor = last.id to true }) { Text("It's a different card", color = Gold) }
                }
            },
            confirmButton = {
                TextButton(onClick = { recipeWrong = false; viewModel.rescanRecipe() }) { Text("Rescan it", color = Gold) }
            },
            dismissButton = {
                TextButton(onClick = { recipeWrong = false; recipePrinting = viewModel.lastRecipeCard() }) { Text("Pick the printing", color = Gold) }
            }
        )
    }
    learnedOpen?.let { (rowId, inRecipe) ->
        val name = if (inRecipe) recipe?.scans?.lastOrNull { it.id == rowId }?.name else state.scannedCards.firstOrNull { it.id == rowId }?.card?.name
        AlertDialog(
            onDismissRequest = { learnedOpen = null },
            containerColor = Surface,
            title = { Text("Learned", color = GoldLight) },
            text = { Text("You corrected this before, so it went in as ${name ?: "the card you picked"}. Forget it, and the scanner goes by what it reads again.", color = TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    learnedOpen = null
                    if (inRecipe) recipePrinting = viewModel.lastRecipeCard()
                    else artPickerRow = state.scannedCards.firstOrNull { it.id == rowId }
                }) { Text("Pick another printing", color = Gold) }
            },
            dismissButton = {
                TextButton(onClick = { learnedOpen = null; viewModel.forgetLearned(rowId) }) { Text("Forget it", color = Gold) }
            }
        )
    }
    recipePrinting?.let { card ->
        ArtPickerDialog(
            row = ScanRow(-1, card, System.currentTimeMillis()),
            load = { viewModel.printingsOf(card) },
            onPick = { viewModel.setRecipePrinting(it); recipePrinting = null },
            onDismiss = { recipePrinting = null },
            onDifferent = { recipe?.scans?.lastOrNull()?.let { differentFor = it.id to true }; recipePrinting = null }
        )
    }
    artPickerRow?.let { row ->
        ArtPickerDialog(
            row = row,
            load = { viewModel.printingsOf(row.card) },
            onPick = { viewModel.setPrinting(row.id, it); artPickerRow = null },
            onDismiss = { artPickerRow = null },
            onDifferent = { differentFor = row.id to false; artPickerRow = null }
        )
    }
    differentFor?.let { (rowId, inRecipe) ->
        DifferentCardDialog(
            suggest = viewModel::suggestNames,
            onPick = { name ->
                differentFor = null
                scope.launch {
                    val printings = viewModel.printingsNamed(name)
                    if (printings.isNotEmpty()) differentPick = Triple(rowId, inRecipe, printings)
                }
            },
            onDismiss = { differentFor = null }
        )
    }
    differentPick?.let { (rowId, inRecipe, printings) ->
        ArtPickerDialog(
            row = ScanRow(-2, printings.first(), 0L),
            load = { printings },
            ringCurrent = false,
            onPick = { card ->
                differentPick = null
                if (inRecipe) viewModel.setRecipePrinting(card) else viewModel.setPrinting(rowId, card)
            },
            onDismiss = { differentPick = null }
        )
    }

    // Cards you own but haven't sorted: offered even before the pile exists.
    val binders = if (collections.any { it.isUnsorted }) collections
    else listOf(Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME)) + collections
    val places = decks.map { it.asTarget() } + binders.map { it.asTarget() }

    if (addingAll) {
        val rows = state.scannedCards
        val label = cardsSubject(rows.size, rows.singleOrNull()?.card?.name)
        AddToPicker(
            verb = AddVerb.ADD,
            subject = label,
            targets = places,
            // Scans are cards in hand: they go into a deck or binder, and the pile says how many.
            considering = null,
            quantity = null,
            // A deck's sideboard too: a draft or sealed pool is scanned straight in.
            offerSideboard = true,
            onPick = { pick ->
                addingAll = false
                showList = false
                val check = AddCheck(pick, grouped(rows).map { it.card.toAddItem(it.quantity, pick.sideboard) })
                addTo.perform(
                    addToMessage(AddVerb.ADD, label, pick, quantity = 1),
                    onUndone = { viewModel.restoreScans(rows) },
                    check = check,
                    fewer = { kept -> addToMessage(AddVerb.ADD, cardsSubject(kept, null), pick, quantity = 1) }
                ) {
                    viewModel.putAllAway(pick, this)
                }
            },
            onDismiss = { addingAll = false }
        )
    }

    addingRow?.let { scanned ->
        // Filing a card files every copy of it in the pile, however many rows that is.
        val rows = state.scannedCards.filter { it.card.id == scanned.card.id }
        val copies = rows.size
        AddToPicker(
            verb = AddVerb.ADD,
            subject = if (copies > 1) "$copies × ${scanned.card.name}" else scanned.card.name,
            imageUrl = scanned.card.displayImageUrl,
            targets = places,
            considering = null,
            quantity = null,
            canBeFoil = scanned.card.canBeFoil,
            offerSideboard = true,
            onPick = { pick ->
                addingRow = null
                addTo.perform(
                    addToMessage(AddVerb.ADD, scanned.card.name, pick, quantity = copies),
                    onUndone = { viewModel.restoreScans(rows) },
                    check = AddCheck(pick, listOf(scanned.card.toAddItem(copies, pick.sideboard)))
                ) {
                    viewModel.putAway(scanned.card, copies, pick, this)
                }
            },
            onDismiss = { addingRow = null }
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
        ScrimIconButton(onClick = onBack, icon = Icons.AutoMirrored.Filled.ArrowBack, desc = "Back")
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
    onAddTo: (ScanRow) -> Unit,
    onScanAgain: (ScanRow) -> Unit,
    onRemove: (ScanRow) -> Unit,
    onPickArt: (ScanRow) -> Unit,
    /** The scans a learned correction put right. */
    learned: Set<Long>,
    onLearned: (ScanRow) -> Unit,
    onFoil: (ScanRow, Boolean) -> Unit,
    onAllTo: () -> Unit,
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
                    Button(
                        onClick = onAllTo,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    ) {
                        Text("Add all to…", style = MaterialTheme.typography.labelLarge, color = Bg)
                    }
                }
                items(shown, key = { it.id }) { scanned ->
                    ScannedCardRow(
                        scanned = scanned,
                        copy = copyNumber(cards, scanned),
                        justNow = scannedTwiceOver(cards, scanned),
                        onClick = { onCardClick(scanned) },
                        onAddTo = { onAddTo(scanned) },
                        onScanAgain = { onScanAgain(scanned) },
                        onRemove = { onRemove(scanned) },
                        onPickArt = { onPickArt(scanned) },
                        onLearned = if (scanned.id in learned) ({ onLearned(scanned) }) else null,
                        // Only a printing that comes in foil can be a foil copy.
                        onFoil = if (scanned.card.canBeFoil) ({ foil -> onFoil(scanned, foil) }) else null
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
    onAddTo: () -> Unit,
    onScanAgain: () -> Unit,
    onRemove: () -> Unit,
    onPickArt: () -> Unit,
    /** Given when a learned correction put this scan right: its "Learned" tag. */
    onLearned: (() -> Unit)? = null,
    /** Marks this copy foil or not; null for a printing that doesn't come in foil. */
    onFoil: ((Boolean) -> Unit)? = null
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
                // Which printing it is and what it's worth, right where the scan lands — the whole
                // point of reading the set code is wasted if you have to open the card to see it.
                // The set's name gives way before its number does: "Avatar: The Last Airbender" can
                // be shortened and still read, but the number is the half that says which printing
                // this is, so it keeps its room.
                // Tapping it changes the printing — on any row, so a set code misread as another can be put right too.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable(onClickLabel = "Change printing", onClick = onPickArt)
                ) {
                    Text(
                        card.setName ?: card.set?.uppercase() ?: "Unknown set",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    card.collectorNumber?.let {
                        Text("#$it", style = MaterialTheme.typography.labelMedium, color = TextMuted, maxLines = 1)
                    }
                    card.prices?.usd?.let { usd ->
                        Text("·", style = MaterialTheme.typography.labelMedium, color = TextDim)
                        Text("$$usd", style = MaterialTheme.typography.labelMedium, color = GoldLight, maxLines = 1)
                    }
                }
                if (onLearned != null) {
                    // Corrected before, so put right this time (ScanCorrections.kt).
                    Text(
                        "Learned",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldLight,
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Gold.copy(alpha = 0.16f))
                            .clickable(onClickLabel = "Learned from your correction", onClick = onLearned)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (!scanned.exact) {
                    // The set code couldn't be read, so this is the card's usual printing.
                    Text(
                        "Best guess · pick art",
                        style = MaterialTheme.typography.labelMedium,
                        color = Gold,
                        modifier = Modifier.clickable(onClick = onPickArt)
                    )
                }
                // What the card does, in a word or two, worked out from its rules text.
                val tags = card.tags.take(3)
                if (tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                        tags.forEach { tag ->
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Surface2)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            // One more copy of this card: another row, as if it went past the camera again.
            IconButton(onClick = onScanAgain, modifier = Modifier.minimumInteractiveComponentSize().size(30.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "One more copy", tint = Gold, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.minimumInteractiveComponentSize().size(30.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Take off this scan", tint = TextDim, modifier = Modifier.size(18.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(
                onClick = onAddTo,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Add to…", style = MaterialTheme.typography.labelMedium, color = Bg) }
            // The camera can't see foiling: the user says, per copy, and it goes in as a foil copy.
            if (onFoil != null) {
                val foil = scanned.foil
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, if (foil) Gold else BorderColor), RoundedCornerShape(8.dp))
                        .background(if (foil) Gold.copy(alpha = 0.16f) else Bg)
                        .toggleable(value = foil, role = Role.Switch, onValueChange = onFoil)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = if (foil) Gold else TextDim, modifier = Modifier.size(14.dp))
                    Text("Foil", style = MaterialTheme.typography.labelMedium, color = if (foil) Gold else TextMuted)
                }
            }
        }
    }
}

/**
 * Which printing is in your hand. The camera reads a card's name easily; the tiny set code that
 * says *which* printing often can't be read at all, and then the card comes in as its usual
 * printing. This shows every printing there is, so the right art is a tap away.
 */
@Composable
private fun ArtPickerDialog(
    row: ScanRow,
    load: suspend () -> List<ScryfallCard>,
    onPick: (ScryfallCard) -> Unit,
    onDismiss: () -> Unit,
    /** Given where a scan is being fixed: "It's a different card" — search for the card it really is. */
    onDifferent: (() -> Unit)? = null,
    /** Whether [row]'s card is ringed as the one it is now (not for a different card just searched for). */
    ringCurrent: Boolean = true
) {
    var printings by remember(row.id) { mutableStateOf<List<ScryfallCard>?>(null) }
    LaunchedEffect(row.id) { printings = load() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(row.card.name, color = GoldLight) },
        text = {
            val found = printings
            when {
                found == null -> Text("Looking up printings…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                found.isEmpty() -> Text("Only one printing of this card.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(420.dp)
                ) {
                    items(found, key = { it.id }) { card ->
                        Column(modifier = Modifier.clickable { onPick(card) }) {
                            val picked = ringCurrent && card.id == row.card.id
                            AsyncImage(
                                model = card.displayImageUrl,
                                contentDescription = card.printingLabel,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.72f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        BorderStroke(if (picked) 2.dp else 1.dp, if (picked) Gold else BorderColor),
                                        RoundedCornerShape(10.dp)
                                    )
                            )
                            Text(
                                card.printingLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Gold) } },
        dismissButton = onDifferent?.let { different -> { TextButton(onClick = different) { Text("It's a different card", color = Gold) } } }
    )
}

/**
 * "It's a different card": the card's name, searched as you type (Scryfall's autocomplete, as elsewhere
 * in the app), to fix a scan that read as another card altogether. [onPick] gets the name chosen.
 */
@Composable
private fun DifferentCardDialog(suggest: suspend (String) -> List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(query) {
        if (query.isBlank()) { names = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(250)
        names = suggest(query.trim()).take(8)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Which card is it?", color = GoldLight) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Card name", color = TextDim) },
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
                names.forEach { name ->
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(name) }.padding(vertical = 10.dp)
                    )
                }
                Text("Pick it, then its printing. The scanner remembers, and gets this read right next time.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}
