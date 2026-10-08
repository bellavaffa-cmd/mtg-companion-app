package com.mtgcompanion.app.ui.scan

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.ZoomState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.mtgcompanion.app.data.SCAN_ZOOM
import com.mtgcompanion.app.data.ZoomRange
import com.mtgcompanion.app.data.defaultZoom
import com.mtgcompanion.app.data.fartherHint
import com.mtgcompanion.app.data.pinchZoom
import com.mtgcompanion.app.data.showFartherHint
import com.mtgcompanion.app.data.startingZoom
import com.mtgcompanion.app.data.zoomIn
import com.mtgcompanion.app.data.zoomLabel
import com.mtgcompanion.app.data.zoomOut
import com.mtgcompanion.app.data.zoomSpoken
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/*
 * The scanner's zoom on screen: what the camera is set to, pinch on the preview, and the − 1.8× +
 * control. The arithmetic (steps, range, label, the farther-away hint) is data/ScanZoom.kt's.
 */

/** Where the last zoom chosen is kept: on this phone only, never synced or backed up with settings. */
private const val PREFS = "scan_zoom"

private fun prefsOf(context: Context): SharedPreferences? =
    runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }.getOrNull()

/**
 * Settings › Scanner › Auto zoom and focus: on unless turned off. Kept on this phone only, and read
 * straight away, so the scanner knows before the camera is bound whether to start on auto.
 */
object AutoCameraSetting {
    private const val KEY = "auto_zoom_focus"
    private val on = MutableStateFlow(true)
    @Volatile private var loaded = false

    fun flow(context: Context): StateFlow<Boolean> {
        if (!loaded) {
            loaded = true
            on.value = prefsOf(context)?.getBoolean(KEY, true) ?: true
        }
        return on.asStateFlow()
    }

    fun set(context: Context, value: Boolean) {
        flow(context)
        on.value = value
        prefsOf(context)?.edit()?.putBoolean(KEY, value)?.apply()
    }
}

/**
 * One scanner's zoom. [key] keeps each scanner's zoom apart ("card", "page"); [preferred] is where it
 * starts and resets to. [onMoved] runs on every change, so the scanner can count it as movement.
 *
 * With [autoEnabled] (the card scanner, with Auto zoom and focus on) the zoom starts on auto at
 * [preferred] and auto zoom moves it ([setAuto]); pinching or − / + takes it over by hand until the
 * chip is tapped, which goes back to [preferred] on auto. That choice is remembered too.
 */
@Stable
class ScanZoom internal constructor(
    private val prefs: SharedPreferences?,
    private val key: String,
    private val preferred: Float,
    private val onMoved: () -> Unit
) {
    /** The zoom the camera is set to. */
    var ratio by mutableFloatStateOf(1f)
        private set

    /** What this camera offers; [ZoomRange.NONE] until it's bound. */
    var range by mutableStateOf(ZoomRange.NONE)
        private set

    /** Whether auto zoom may run here at all (the card scanner, with the setting on). */
    var autoEnabled by mutableStateOf(false)

    /** The zoom was last set by hand, so auto zoom leaves it alone until the chip is tapped. */
    private var manual by mutableStateOf(prefs?.getBoolean("$key.manual", false) ?: false)

    /** Whether auto zoom is in charge of the zoom now. */
    val auto: Boolean get() = autoEnabled && !manual

    /** Where a reset goes: [preferred], under the lens switch and inside [range]. */
    val default: Float get() = defaultZoom(range, preferred)

    private var camera: Camera? = null
    private var watching: Pair<LiveData<ZoomState>, Observer<ZoomState>>? = null

    /** The camera just bound: learn its range and put it at the zoom last chosen (or the default). */
    fun bind(camera: Camera, owner: LifecycleOwner) {
        watching?.let { (data, observer) -> data.removeObserver(observer) }
        this.camera = camera
        var started = false
        val observer = Observer<ZoomState> { zs ->
            if (zs == null) return@Observer
            val r = ZoomRange(zs.minZoomRatio, zs.maxZoomRatio)
            if (r != range) range = r
            if (!started) {
                started = true
                apply(if (auto) defaultZoom(r, preferred) else startingZoom(saved(), r, preferred))
                Log.d("ScanTiming", "zoom $ratio x (phone offers ${zs.minZoomRatio}..${zs.maxZoomRatio})")
            }
        }
        val data = camera.cameraInfo.zoomState
        data.observe(owner, observer)
        watching = data to observer
    }

    /** Sets the zoom (kept in range); counts as movement when it actually changes. */
    fun set(target: Float) {
        val r = range.clamp(target)
        if (abs(r - ratio) < 0.001f) return
        apply(r)
        onMoved()
    }

    /** A pinch's step: the fingers spread by [factor] since the last one. Takes the zoom over by hand. */
    fun pinch(factor: Float) {
        val before = ratio
        set(pinchZoom(ratio, factor, range))
        if (ratio != before) manual = true
    }

    fun stepIn() { manual = true; set(zoomIn(ratio, range)); save() }
    fun stepOut() { manual = true; set(zoomOut(ratio, range)); save() }

    /** The chip: back to the default, and to auto zoom where it's on. */
    fun reset() { manual = false; set(default); save() }

    /** Auto zoom's step; ignored once the zoom has been taken over by hand. */
    fun setAuto(target: Float) {
        if (auto) set(target)
    }

    /** Kept for next time this scanner opens. */
    fun save() {
        if (!range.canZoom) return
        prefs?.edit()?.putFloat(key, ratio)?.putBoolean("$key.manual", manual)?.apply()
    }

    private fun saved(): Float? = prefs?.takeIf { it.contains(key) }?.let { runCatching { it.getFloat(key, 0f) }.getOrNull() }

    private fun apply(r: Float) {
        ratio = r
        runCatching { camera?.cameraControl?.setZoomRatio(r) }
    }
}

/** A scanner's zoom, remembered for the screen; see [ScanZoom]. */
@Composable
fun rememberScanZoom(key: String, preferred: Float = SCAN_ZOOM, onMoved: () -> Unit = {}): ScanZoom {
    val context = LocalContext.current
    val moved by rememberUpdatedState(onMoved)
    return remember(key, preferred) { ScanZoom(prefsOf(context), key, preferred) { moved() } }
}

/**
 * Two fingers on the preview zoom it. One finger is left alone entirely — nothing is consumed — so a
 * tap or a drag still reaches whatever it was for. [onPinching] is true from the second finger down
 * until every finger is up: the scanner doesn't take a card meanwhile. A single finger that goes
 * down and up without moving is a tap at that point ([onTap], tap to focus); a pinch never is.
 */
fun Modifier.pinchToZoom(
    zoom: ScanZoom,
    onPinching: (Boolean) -> Unit = {},
    onTap: ((Offset) -> Unit)? = null
): Modifier = pointerInput(zoom) {
    // Keyed on the zoom only, so a recomposition doesn't cut a pinch short: pass lambdas that stay put.
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        var pinching = false
        var tap = onTap != null
        var at = first.position
        try {
            while (true) {
                val event = awaitPointerEvent()
                val down = event.changes.count { it.pressed }
                if (tap) {
                    val mine = event.changes.firstOrNull { it.id == first.id }
                    if (mine != null) at = mine.position
                    if (event.changes.size > 1 || mine == null || mine.isConsumed ||
                        (mine.position - first.position).getDistance() > viewConfiguration.touchSlop
                    ) tap = false
                }
                if (down == 0) {
                    if (tap) onTap?.invoke(at)
                    break
                }
                if (down >= 2) {
                    if (!pinching) {
                        pinching = true
                        onPinching(true)
                    }
                    val factor = event.calculateZoom()
                    if (factor != 1f) zoom.pinch(factor)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } finally {
            if (pinching) {
                onPinching(false)
                zoom.save()
            }
        }
    }
}

/**
 * − 1.8× + : zoom out, the zoom (a tap goes back to the default), zoom in. Each part at least 48 dp,
 * for one thumb and for TalkBack, which reads the chip as "Zoom 1.8 times". "Auto" under the number
 * while auto zoom is in charge. Not shown when the camera has no zoom to give.
 */
@Composable
fun ZoomControl(zoom: ScanZoom, modifier: Modifier = Modifier) {
    val range = zoom.range
    if (!range.canZoom) return
    val colors = LocalAppColors.current
    val ratio = zoom.ratio
    val auto = zoom.auto
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clip(RoundedCornerShape(50)).background(colors.bg.copy(alpha = 0.6f))
    ) {
        val canOut = range.canZoomOut(ratio)
        val canIn = range.canZoomIn(ratio)
        IconButton(onClick = zoom::stepOut, enabled = canOut, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "Zoom out", tint = if (canOut) colors.accent else colors.textMuted)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 56.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClickLabel = "Reset to ${zoomLabel(zoom.default)}", role = Role.Button, onClick = zoom::reset)
                .semantics {
                    contentDescription = zoomSpoken(ratio) + if (auto) ", automatic" else ""
                    liveRegion = LiveRegionMode.Polite
                }
                .padding(horizontal = 6.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(zoomLabel(ratio), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                if (auto) Text("Auto", style = MaterialTheme.typography.labelSmall, color = colors.accent)
            }
        }
        IconButton(onClick = zoom::stepIn, enabled = canIn, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Zoom in", tint = if (canIn) colors.accent else colors.textMuted)
        }
    }
}

/** "Hold the card farther away", while the zoom is past where the phone may switch lenses. */
@Composable
fun FartherHint(zoom: ScanZoom, what: String, modifier: Modifier = Modifier) {
    if (!showFartherHint(zoom.ratio)) return
    val colors = LocalAppColors.current
    Text(
        fartherHint(what),
        style = MaterialTheme.typography.bodySmall,
        color = colors.warning,
        modifier = modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bg.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
