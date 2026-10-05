package com.mtgcompanion.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.usage.Usage
import com.mtgcompanion.app.ui.social.rememberCommunityRules
import com.mtgcompanion.app.ui.theme.LocalAppColors

/** The one sentence Settings › Privacy says about it, the same as the web app's. */
internal const val USAGE_NOTE = "Manabind counts which screens and features get used each day, under a random id that " +
    "changes every 90 days and never with card names, decks, messages or your account; turned off, nothing is counted or sent."

/** Settings → Privacy: the anonymous usage counts (data/usage), on unless turned off. */
@Composable
internal fun PrivacySection() {
    val colors = LocalAppColors.current
    val on by Usage.enabled.collectAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable { Usage.setEnabled(!on) }.padding(vertical = 8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("Share anonymous usage counts", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(USAGE_NOTE, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Switch(
            checked = on,
            onCheckedChange = { Usage.setEnabled(it) },
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent)
        )
    }
    // What's allowed in profiles, messages, trades and sharing, and how to report or block.
    val communityRules = rememberCommunityRules()
    Column(Modifier.fillMaxWidth().clickable { communityRules.show() }.padding(vertical = 8.dp)) {
        Text("Community rules", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
        Text(
            "Be respectful; no hate, harassment, spam, scams or explicit content. How to report or block someone.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted
        )
    }
}
