package com.mtgcompanion.app.ui.social

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mtgcompanion.app.data.social.PushNotifications
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/**
 * Notifications: whether this phone gets them, and which kinds (friend requests, trades) the
 * account gets on every device.
 */
@Composable
fun NotificationsSection(social: SocialRepository) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by PushNotifications.enabled.collectAsState()
    var permitted by remember { mutableStateOf(PushNotifications.permissionGranted(context)) }
    var asked by remember { mutableStateOf(false) }
    var prefs by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Coming back from the system's settings: the permission may have changed there.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permitted = PushNotifications.permissionGranted(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { prefs = runCatching { social.api.notificationPrefs() }.getOrNull() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        asked = true
        if (granted) PushNotifications.setEnabled(context, true, social)
    }
    // Android stops showing the question after it's been refused; then only the system's settings can turn it on.
    val blocked = asked && !permitted && Build.VERSION.SDK_INT >= 33 &&
        (context as? Activity)?.let { !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS) } == true

    fun setKinds(friends: Boolean, trades: Boolean) {
        prefs = friends to trades
        scope.launch {
            error = null
            prefs = try { social.api.setNotificationPrefs(friends, trades) } catch (e: Exception) { error = e.message; prefs }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Notifications", style = MaterialTheme.typography.titleSmall)
        if (!PushNotifications.available) {
            Text("This build of the app can't get notifications.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.padding(vertical = 6.dp))
        } else {
            SwitchRow("On this phone", if (blocked) "Blocked — allow notifications in the phone's settings" else "Friend requests and trades, even when the app is closed", enabled && permitted) { on ->
                when {
                    !on -> PushNotifications.setEnabled(context, false, social)
                    permitted -> PushNotifications.setEnabled(context, true, social)
                    blocked -> context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                    Build.VERSION.SDK_INT >= 33 -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (blocked) {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text("Open the phone's notification settings", color = colors.accent) }
            }
        }
        prefs?.let { (friends, trades) ->
            SwitchRow("Friend requests", "Someone asks to be friends, or says yes", friends) { setKinds(it, trades) }
            SwitchRow("Trades", "A trade arrives, or yours is answered", trades) { setKinds(friends, it) }
            Text("These two apply to all your devices.", style = MaterialTheme.typography.bodySmall, color = colors.textDim)
        }
        error?.let { Notice(it, warn = true) }
    }
}

@Composable
private fun SwitchRow(label: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent))
    }
}
