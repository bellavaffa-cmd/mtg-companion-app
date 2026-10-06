package com.mtgcompanion.app.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.social.SocialApi
import com.mtgcompanion.app.data.supabase.QrSignInRequest
import com.mtgcompanion.app.data.supabase.SupabaseSync
import com.mtgcompanion.app.ui.social.QrCode
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.TextMuted
import kotlinx.coroutines.delay

/** How many codes in a row are shown before it stops and waits for "Show a new code". */
private const val MAX_CODES = 5

/**
 * Signing this phone in by showing a code: a phone already signed in scans it (Manabind's Scan, or
 * any camera opening manabind.com/login/…) and approves. The same requests as the web app's
 * "Sign in with your phone"; codes last two minutes and a new one replaces it until [MAX_CODES].
 */
@Composable
fun QrSignInDialog(sync: SupabaseSync, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    var request by remember { mutableStateOf<QrSignInRequest?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    // Bumped by "Show a new code" to start the loop again.
    var round by remember { mutableIntStateOf(0) }
    var stopped by remember { mutableStateOf(false) }

    LaunchedEffect(round) {
        stopped = false
        problem = null
        val name = "Manabind on ${Build.MODEL}".take(80)
        repeat(MAX_CODES) {
            val asked = try {
                sync.auth.startQrSignIn(name)
            } catch (e: Exception) {
                problem = "Can't reach the server — check your connection."
                request = null
                stopped = true
                return@LaunchedEffect
            }
            request = asked
            while (System.currentTimeMillis() < asked.expiresAt) {
                secondsLeft = ((asked.expiresAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                val signedIn = try {
                    sync.claimQrSignIn(asked)
                } catch (e: Exception) {
                    // A dropped connection between polls: keep waiting while the code lasts.
                    false
                }
                if (signedIn) {
                    onDismiss()
                    return@LaunchedEffect
                }
                delay(2_000)
            }
        }
        request = null
        stopped = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign in with a QR code") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "On a phone that's already signed in, open Manabind, tap Scan and point it at this code. " +
                        "Any phone's camera works too: open the link on manabind.com while signed in there.",
                    style = MaterialTheme.typography.bodySmall
                )
                val shown = request
                when {
                    shown != null -> {
                        QrCode(SocialApi.loginLink(shown.code), 220.dp, "Sign-in code for this phone")
                        val m = secondsLeft / 60
                        val s = secondsLeft % 60
                        Text(
                            "New code in $m:${s.toString().padStart(2, '0')} · waiting for approval…",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                    problem != null -> Text(problem!!, style = MaterialTheme.typography.bodySmall, color = colors.warning)
                    stopped -> Text("The code ran out.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    else -> CircularProgressIndicator(color = Gold)
                }
            }
        },
        confirmButton = {
            if (stopped) TextButton(onClick = { round++ }) { Text("Show a new code", color = Gold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Gold) }
        }
    )
}
