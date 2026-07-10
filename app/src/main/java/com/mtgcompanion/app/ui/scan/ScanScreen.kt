package com.mtgcompanion.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsState()
    val decks by viewModel.decks.collectAsState()

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

    var deckPickerCard by remember { mutableStateOf<ScryfallCard?>(null) }
    var showList by remember { mutableStateOf(false) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
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
            OverlayBackButton(onBack)
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
                                viewModel.onFrame(inputImage, onProcessed = { imageProxy.close() })
                            }
                        }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // Framing guide so the user knows to fill the frame with the card title.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.55f)
                .border(BorderStroke(2.dp, Gold.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
        )

        // Top overlay: back + status pill + (debug) test button.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScrimIconButton(onClick = onBack, icon = Icons.Filled.ArrowBack, desc = "Back")
                Box(modifier = Modifier.weight(1f))
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
                        .clip(RoundedCornerShape(4.dp))
                        .background(Bg.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Bottom overlay: view-list button.
        Button(
            onClick = { showList = true },
            shape = RoundedCornerShape(2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
        ) {
            Text(
                "VIEW LIST (${state.scannedCards.size})",
                style = MaterialTheme.typography.labelLarge,
                color = Bg
            )
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
                onAddToCollection = { viewModel.addToCollection(it) },
                onAddToDeck = { deckPickerCard = it },
                onRemove = { viewModel.removeFromList(it) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    deckPickerCard?.let { card ->
        DeckPickerDialog(
            decks = decks,
            onDismiss = { deckPickerCard = null },
            onPickDeck = { deckId ->
                deckPickerCard = null
                viewModel.addToDeck(card, deckId)
            },
            onCreateDeck = { name ->
                deckPickerCard = null
                viewModel.createDeckAndAdd(card, name)
            }
        )
    }
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
private fun ScrimIconButton(onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Bg.copy(alpha = 0.6f))
    ) {
        Icon(icon, contentDescription = desc, tint = Gold)
    }
}

@Composable
private fun ScannedListPanel(
    cards: List<ScryfallCard>,
    onClose: () -> Unit,
    onAddToCollection: (ScryfallCard) -> Unit,
    onAddToDeck: (ScryfallCard) -> Unit,
    onRemove: (ScryfallCard) -> Unit,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "SCANNED (${cards.size})",
                style = MaterialTheme.typography.titleMedium,
                color = GoldLight
            )
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close list", tint = TextDim)
            }
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
                items(cards, key = { it.id }) { card ->
                    ScannedCardRow(
                        card = card,
                        onAddToCollection = { onAddToCollection(card) },
                        onAddToDeck = { onAddToDeck(card) },
                        onRemove = { onRemove(card) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannedCardRow(
    card: ScryfallCard,
    onAddToCollection: () -> Unit,
    onAddToDeck: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Bg)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(4.dp))
            .padding(10.dp)
    ) {
        AsyncImage(
            model = card.displayImageUrl,
            contentDescription = card.name,
            modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(3.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(card.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Button(
                    onClick = onAddToCollection,
                    shape = RoundedCornerShape(2.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
                ) { Text("+ COLLECTION", style = MaterialTheme.typography.labelMedium, color = Bg) }
                OutlinedButton(
                    onClick = onAddToDeck,
                    shape = RoundedCornerShape(2.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, BorderColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight)
                ) { Text("+ DECK", style = MaterialTheme.typography.labelMedium) }
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove from list", tint = TextDim)
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
                    label = { Text("New deck name", color = GoldDim) },
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
            ) { Text("CREATE & ADD", color = Bg) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = TextMuted) }
        }
    )
}
