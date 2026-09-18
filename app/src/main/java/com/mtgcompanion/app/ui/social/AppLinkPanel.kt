package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.social.AppLink
import com.mtgcompanion.app.data.social.Profile
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What's being done with the last code a camera read. */
sealed interface LinkResult {
    data object None : LinkResult
    data class Unknown(val text: String) : LinkResult
    data class AddFriend(val username: String, val message: String? = null, val ok: Boolean = true, val busy: Boolean = false) : LinkResult
    data object Joining : LinkResult
    data class Joined(val host: Profile, val matchId: String, val seat: Int) : LinkResult
    data class Failed(val message: String) : LinkResult
}

/**
 * Acts on the app's QR codes read by a camera: a friend's code asks to add them, a life counter
 * seat's code sits the user there, a share link opens what was shared. Used by the QR scanner and
 * by the card scanner, which also notices these codes.
 */
@Stable
class AppLinkHandler internal constructor(
    private val social: SocialRepository,
    private val scope: CoroutineScope,
    private val openSharedLink: () -> ((String) -> Unit)
) {
    var result by mutableStateOf<LinkResult>(LinkResult.None)
        private set

    /** Something is showing: the camera stays out of the way until it's dismissed. */
    val showing: Boolean get() = result != LinkResult.None

    // The code just dismissed is ignored for a moment, so it doesn't pop straight back up while
    // it's still in front of the camera.
    private var dismissedText: String? = null
    private var dismissedAt = 0L

    /**
     * A code the camera read. [ignoreOthers]: codes that aren't the app's are passed over quietly
     * (the card scanner) instead of saying so (the QR scanner).
     */
    fun handle(text: String, ignoreOthers: Boolean = false) {
        if (showing) return
        if (text == dismissedText && System.currentTimeMillis() - dismissedAt < REPEAT_PAUSE_MS) return
        when (val link = AppLink.parse(text)) {
            null -> if (!ignoreOthers) result = LinkResult.Unknown(text)
            is AppLink.SharedLink -> openSharedLink()(link.token)
            is AppLink.AddFriend -> result = LinkResult.AddFriend(link.username)
            is AppLink.JoinSeat -> {
                result = LinkResult.Joining
                scope.launch {
                    result = try {
                        val (matchId, host) = social.api.joinMatch(link.code, link.seat)
                        LinkResult.Joined(host, matchId, link.seat)
                    } catch (e: Exception) {
                        LinkResult.Failed(e.message ?: "Something went wrong.")
                    }
                }
            }
        }
        if (showing) dismissedText = text
    }

    fun dismiss() {
        dismissedAt = System.currentTimeMillis()
        result = LinkResult.None
    }

    internal fun sendFriendRequest(r: LinkResult.AddFriend) {
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
    }

    internal fun leaveSeat(r: LinkResult.Joined) {
        scope.launch { runCatching { social.api.clearMatchSeat(r.matchId, r.seat) } }
        dismiss()
    }

    private companion object {
        const val REPEAT_PAUSE_MS = 4_000L
    }
}

@Composable
fun rememberAppLinkHandler(social: SocialRepository, onOpenSharedLink: (String) -> Unit): AppLinkHandler {
    val scope = rememberCoroutineScope()
    val open by rememberUpdatedState(onOpenSharedLink)
    return remember(social) { AppLinkHandler(social, scope) { open } }
}

/** The panel for what [handler] is doing; [onDone] closes the scanner after joining a seat. */
@Composable
fun AppLinkPanel(handler: AppLinkHandler, profile: Profile?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val panel = modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surface).padding(18.dp)
    when (val r = handler.result) {
        LinkResult.None -> Unit
        is LinkResult.Unknown -> Column(panel, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.QrCode2, contentDescription = null, tint = colors.textDim)
                Text("That isn't an MTG Companion code.", color = colors.textPrimary)
            }
            LineButton("Scan again", handler::dismiss)
        }
        is LinkResult.AddFriend -> Column(panel, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = colors.accent)
                Text("@${r.username}", style = MaterialTheme.typography.titleMedium)
            }
            Text(r.message ?: "Ask them to be friends? You'll be able to see what each of you shares, and trade.", color = if (r.ok) colors.textMuted else colors.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (r.message == null) GoldButton("Send friend request", { handler.sendFriendRequest(r) }, enabled = !r.busy)
                LineButton(if (r.message == null) "Cancel" else "Done", handler::dismiss)
            }
        }
        LinkResult.Joining -> Row(panel, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(22.dp))
            Text("Taking your seat…", color = colors.textPrimary)
        }
        is LinkResult.Joined -> Column(panel, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(profile, 72.dp)
            Text("You're in seat ${r.seat}", style = MaterialTheme.typography.titleLarge)
            Text("at ${r.host.displayName}'s table (${r.host.handle}). Your name and picture show on their life counter.", color = colors.textMuted, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LineButton("Leave this seat", { handler.leaveSeat(r) })
                GoldButton("Done", { handler.dismiss(); onDone() })
            }
        }
        is LinkResult.Failed -> Column(panel, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.EventBusy, contentDescription = null, tint = colors.error)
                Text("Couldn't join", style = MaterialTheme.typography.titleMedium)
            }
            Text(r.message, color = colors.textMuted)
            LineButton("Scan again", handler::dismiss)
        }
    }
}
