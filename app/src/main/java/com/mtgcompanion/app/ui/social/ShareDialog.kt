package com.mtgcompanion.app.ui.social

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.social.ShareKind
import com.mtgcompanion.app.data.social.SocialApi
import com.mtgcompanion.app.data.social.SocialException
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.supabase.SupabaseSync
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/** Who can see one of the user's decks or binders: all friends, some pods, and/or anyone with a link. View only. */
@Composable
fun ShareDialog(
    social: SocialRepository,
    sync: SupabaseSync,
    kind: ShareKind,
    itemId: String,
    name: String,
    onOpenFriends: () -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account by social.accountFlow.collectAsState()
    val overview by social.overview.collectAsState()
    val loadError by social.error.collectAsState()
    LaunchedEffect(account?.userId) { if (account != null) social.refresh() }
    val current = overview?.myShares?.firstOrNull { it.kind == kind && it.itemId == itemId }
    var allFriends by remember { mutableStateOf<Boolean?>(null) }
    var pods by remember { mutableStateOf<List<String>?>(null) }
    var link by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val what = if (kind == ShareKind.DECK) "deck" else "binder"

    // Until the user changes something, the switches show what's saved.
    val friendsOn = allFriends ?: current?.allFriends ?: false
    val podsOn = pods ?: current?.podIds.orEmpty()
    val linkOn = link ?: (current?.linkToken != null)
    val changed = allFriends != null || pods != null || link != null
    val o = overview

    if (account == null || o == null || o.me == null) {
        AlertDialog(
            onDismissRequest = onClose,
            containerColor = colors.surface,
            title = { Text("Share this $what") },
            text = {
                Text(
                    when {
                        account == null -> "Sign in to share decks and binders with friends."
                        o == null -> loadError ?: "Loading…"
                        else -> "Make your profile first — friends see your name on what you share."
                    },
                    color = colors.textMuted
                )
            },
            confirmButton = {
                if (account != null && o != null) TextButton(onClick = { onClose(); onOpenFriends() }) { Text("Make my profile", color = colors.accent) }
                else TextButton(onClick = onClose) { Text("Close", color = colors.accent) }
            }
        )
        return
    }

    val url = current?.linkToken?.takeIf { linkOn && !changed }?.let(SocialApi::shareLink)
    val friendCount = o.acceptedFriends.size
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = colors.surface,
        title = { Text("Share “$name”") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("People you share with can look, not change anything. It stays up to date as you edit.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                ShareSwitch("All my friends", if (friendCount == 0) "You have no friends added yet" else "$friendCount ${if (friendCount == 1) "friend" else "friends"}, and anyone you add later", friendsOn) { allFriends = it }
                o.pods.forEach { pod ->
                    ShareSwitch(pod.name, "Pod · ${pod.members.size} ${if (pod.members.size == 1) "person" else "people"}", pod.id in podsOn) { on ->
                        pods = if (on) podsOn + pod.id else podsOn - pod.id
                    }
                }
                ShareSwitch("Anyone with the link", "Works without an account — turn it off to stop the link working", linkOn) { link = it }
                // Friends who see it anyway: everything of this kind is shared with them, or this one by one.
                val everything = o.myShareAll.filter { it.kind == kind }
                val alsoWith = (everything.mapNotNull { it.viewer } + current?.friendIds.orEmpty()).distinct().map { o.person(it)?.displayName ?: "a friend" }
                when {
                    everything.any { it.viewer == null } -> Text(
                        "${if (kind == ShareKind.DECK) "All your decks are" else "Your whole collection is"} shared with all your friends, so they see this $what anyway.",
                        style = MaterialTheme.typography.bodySmall, color = colors.textDim
                    )
                    alsoWith.isNotEmpty() -> Text(
                        "Also shared with ${alsoWith.joinToString()} — change that on their page in Friends.",
                        style = MaterialTheme.typography.bodySmall, color = colors.textDim
                    )
                }
                if (url != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        QrCode(url, 160.dp, "QR code for the link to $name")
                        LineButton("Share link", {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url).putExtra(Intent.EXTRA_SUBJECT, name), "Share link"))
                        }, icon = { Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp)) })
                    }
                }
                error?.let { Notice(it, warn = true) }
            }
        },
        confirmButton = {
            if (changed) TextButton(enabled = !busy, onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        // A new deck or binder has to reach the server before it can be shared.
                        sync.refresh()
                        social.api.setShare(kind, itemId, friendsOn, podsOn, linkOn)
                        social.refresh()
                        allFriends = null
                        pods = null
                        link = null
                        if (!linkOn) onClose()
                    } catch (e: SocialException) {
                        error = if (e.code == "no_such_item") "This $what hasn't synced yet — check your connection and try again." else e.message
                    } catch (e: Exception) {
                        error = e.message ?: "Something went wrong."
                    } finally {
                        busy = false
                    }
                }
            }) { Text(if (busy) "Saving…" else "Save", color = colors.accent) }
            else TextButton(onClick = onClose) { Text("Close", color = colors.accent) }
        },
        dismissButton = { if (changed) TextButton(onClick = onClose) { Text("Cancel", color = colors.textMuted) } }
    )
}

@Composable
internal fun ShareSwitch(label: String, detail: String, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!on) }.padding(vertical = 8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Switch(
            checked = on,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.accent,
                checkedThumbColor = colors.onAccent,
                disabledCheckedTrackColor = colors.accent.copy(alpha = 0.45f),
                disabledCheckedThumbColor = colors.onAccent
            )
        )
    }
}
