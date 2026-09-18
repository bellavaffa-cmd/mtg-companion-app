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
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.mtgcompanion.app.data.social.AppLink
import com.mtgcompanion.app.data.social.Profile
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** What the scanner is doing with the last code it read. */
private sealed interface ScanResult {
    data object None : ScanResult
    data class Unknown(val text: String) : ScanResult
    data class AddFriend(val username: String, val message: String? = null, val ok: Boolean = true, val busy: Boolean = false) : ScanResult
    data object Joining : ScanResult
    data class Joined(val host: Profile, val matchId: String, val seat: Int) : ScanResult
    data class Failed(val message: String) : ScanResult
}

/**
 * Scans the app's QR codes: a friend's code (add them), a seat on someone's life counter (sit there
 * with the user's profile), or a share link (open what was shared).
 */
@Composable
fun QrScanScreen(social: SocialRepository, onBack: () -> Unit, onSignIn: () -> Unit, onOpenSharedLink: (String) -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }
    var result by remember { mutableStateOf<ScanResult>(ScanResult.None) }
    val signedIn = social.userId != null

    fun handle(text: String) {
        when (val link = AppLink.parse(text)) {
            null -> result = ScanResult.Unknown(text)
            is AppLink.SharedLink -> onOpenSharedLink(link.token)
            is AppLink.AddFriend -> result = ScanResult.AddFriend(link.username)
            is AppLink.JoinSeat -> {
                result = ScanResult.Joining
                scope.launch {
                    result = try {
                        val (matchId, host) = social.api.joinMatch(link.code, link.seat)
                        ScanResult.Joined(host, matchId, link.seat)
                    } catch (e: Exception) {
                        ScanResult.Failed(e.message ?: "Something went wrong.")
                    }
                }
            }
        }
    }
    // The analyzer runs on its own thread; it only hands over a code while nothing is showing.
    val current by rememberUpdatedState(result)
    val onCode by rememberUpdatedState<(String) -> Unit>({ text -> if (current == ScanResult.None) handle(text) })

    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
    DisposableEffect(Unit) { onDispose { executor.shutdown(); scanner.close() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission && signedIn) {
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
                                    codes.firstNotNullOfOrNull { it.rawValue }?.let { text -> ContextCompat.getMainExecutor(ctx).execute { onCode(text) } }
                                }
                                .addOnCompleteListener { proxy.close() }
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
            Box(
                Modifier.align(Alignment.Center).size(250.dp).border(BorderStroke(3.dp, colors.accent.copy(alpha = 0.8f)), RoundedCornerShape(24.dp))
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Scan a QR code", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(start = 12.dp))
        }

        val panel = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surface).padding(18.dp)
        when {
            !signedIn -> Column(panel, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sign in first — friends and life counter seats go with your account.", color = colors.textPrimary)
                GoldButton("Sign in", onSignIn)
            }
            !hasPermission -> Column(panel, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Camera access is needed to scan a code.", color = colors.textPrimary)
                GoldButton("Allow the camera", { permissionLauncher.launch(Manifest.permission.CAMERA) })
            }
            else -> when (val r = result) {
                ScanResult.None -> Text(
                    "Point at a friend's code, a seat's code on a life counter, or a share link.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth()
                )
                is ScanResult.Unknown -> Column(panel, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null, tint = colors.textDim)
                        Text("That isn't an MTG Companion code.", color = colors.textPrimary)
                    }
                    LineButton("Scan again", { result = ScanResult.None })
                }
                is ScanResult.AddFriend -> Column(panel, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = colors.accent)
                        Text("@${r.username}", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(r.message ?: "Ask them to be friends? You'll be able to see what each of you shares, and trade.", color = if (r.ok) colors.textMuted else colors.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (r.message == null) {
                            GoldButton("Send friend request", {
                                result = r.copy(busy = true)
                                scope.launch {
                                    result = try {
                                        val answer = social.api.requestFriend(r.username)
                                        social.refresh()
                                        r.copy(busy = false, message = when (answer) {
                                            "accepted" -> "You're now friends."
                                            "already" -> "Already asked — waiting for their answer (or you're already friends)."
                                            else -> "Asked! They'll see your request."
                                        })
                                    } catch (e: Exception) {
                                        r.copy(busy = false, ok = false, message = e.message ?: "Something went wrong.")
                                    }
                                }
                            }, enabled = !r.busy)
                        }
                        LineButton(if (r.message == null) "Cancel" else "Scan another", { result = ScanResult.None })
                    }
                }
                ScanResult.Joining -> Row(panel, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(22.dp))
                    Text("Taking your seat…", color = colors.textPrimary)
                }
                is ScanResult.Joined -> Column(panel, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Avatar(social.overview.value?.me, 72.dp)
                    Text("You're in seat ${r.seat}", style = MaterialTheme.typography.titleLarge)
                    Text("at ${r.host.displayName}'s table (${r.host.handle}). Your name and picture show on their life counter.", color = colors.textMuted, textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LineButton("Leave this seat", {
                            scope.launch { runCatching { social.api.clearMatchSeat(r.matchId, r.seat) } }
                            result = ScanResult.None
                        })
                        GoldButton("Done", onBack)
                    }
                }
                is ScanResult.Failed -> Column(panel, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.EventBusy, contentDescription = null, tint = colors.error)
                        Text("Couldn't join", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(r.message, color = colors.textMuted)
                    LineButton("Scan again", { result = ScanResult.None })
                }
            }
        }
    }
}
