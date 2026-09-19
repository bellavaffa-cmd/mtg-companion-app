package com.mtgcompanion.app.ui.social

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.theme.LocalAppColors
import java.util.concurrent.Executors

/**
 * Scans the app's QR codes: a friend's code (add them), a seat on someone's life counter (sit there
 * with the user's profile), or a share link (open what was shared). The card scanner notices these
 * codes too; this screen is for when that's all you want.
 */
@Composable
fun QrScanScreen(
    social: SocialRepository,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onOpenSharedLink: (String) -> Unit,
    onOpenRemote: (matchId: String, seat: Int) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }
    val account by social.accountFlow.collectAsState()
    val overview by social.overview.collectAsState()
    val links = rememberAppLinkHandler(social, onOpenSharedLink, onOpenRemote)

    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { qrScanner() }
    DisposableEffect(Unit) { onDispose { executor.shutdown(); scanner.close() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission && account != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        analysis.setAnalyzer(executor) { proxy ->
                            val media = proxy.image
                            if (media == null) { proxy.close(); return@setAnalyzer }
                            scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                                .addOnSuccessListener { codes ->
                                    codes.firstNotNullOfOrNull { it.rawValue }?.let { text -> ContextCompat.getMainExecutor(ctx).execute { links.handle(text) } }
                                }
                                .addOnCompleteListener { proxy.close() }
                        }
                        // The screen may already be gone (backed out of, or a code acted on) by the time
                        // the camera is ready: binding to a finished screen crashes.
                        if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) return@addListener
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
            Box(Modifier.align(Alignment.Center).size(250.dp).border(BorderStroke(3.dp, colors.accent.copy(alpha = 0.8f)), RoundedCornerShape(24.dp)))
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Scan a QR code", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(start = 12.dp))
        }

        val bottom = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        val panel = bottom.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surface).padding(18.dp)
        when {
            account == null -> Column(panel, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sign in first — friends and life counter seats go with your account.", color = colors.textPrimary)
                GoldButton("Sign in", onSignIn)
            }
            !hasPermission -> Column(panel, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Camera access is needed to scan a code.", color = colors.textPrimary)
                GoldButton("Allow the camera", { permissionLauncher.launch(Manifest.permission.CAMERA) })
            }
            links.showing -> AppLinkPanel(links, overview?.me, onDone = onBack, modifier = bottom)
            else -> Text(
                "Point at a friend's code, a seat's code on a life counter, or a share link.",
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth()
            )
        }
    }
}

/** An ML Kit reader for QR codes only. */
fun qrScanner() = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
