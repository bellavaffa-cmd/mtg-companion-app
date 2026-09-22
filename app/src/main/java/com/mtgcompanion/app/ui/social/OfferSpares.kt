package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.theme.LocalAppColors

/**
 * Offering spares to a friend: pick who, and the trade opens with the cards already on the user's
 * side of it (see offerCards). Mirrors the web app's TradeOfferSheet in src/social/TradeOffer.tsx.
 */
@Composable
fun OfferSparesDialog(
    social: SocialRepository,
    count: Int,
    onPick: (friendId: String) -> Unit,
    onAddFriend: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    val overview by social.overview.collectAsState()
    // Friends made since the app last looked should be there to pick.
    LaunchedEffect(Unit) { runCatching { social.refresh() } }
    val friends = overview?.acceptedFriends.orEmpty().mapNotNull { f -> overview?.person(f.userId)?.let { f.userId to it } }
        .sortedBy { it.second.displayName.lowercase() }

    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = onDismiss,
        title = { Text("Offer $count ${if (count == 1) "card" else "cards"} to\u2026", color = colors.accent) },
        text = {
            Column {
                Text(
                    "Your spares, dearest first \u2014 you can change the list in the trade.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
                when {
                    overview == null -> CircularProgressIndicator(Modifier.padding(16.dp), color = colors.accent)
                    friends.isEmpty() -> Text(
                        "No friends yet \u2014 add one first.",
                        color = colors.textPrimary,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                        items(friends, key = { it.first }) { (id, person) ->
                            Column(
                                Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 10.dp)
                            ) {
                                Text(person.displayName, color = colors.textPrimary, style = MaterialTheme.typography.bodyLarge)
                                Text("@${person.username}", color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (overview != null && friends.isEmpty()) TextButton(onClick = onAddFriend) { Text("Add a friend", color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}
