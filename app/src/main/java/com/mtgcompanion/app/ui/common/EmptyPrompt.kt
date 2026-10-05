package com.mtgcompanion.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.ui.theme.LocalAppColors

/** One of an empty state's buttons. */
data class EmptyAction(val label: String, val icon: ImageVector? = null, val onClick: () -> Unit)

/**
 * What a list says when there's nothing in it yet: an icon, one plain sentence, and a button or two
 * for the obvious next step ("No decks yet" → Paste a list · Browse precons). The first action is the
 * main one. Every empty list in the app uses this, so they all look and read alike. Mirrors the web
 * app's src/components/EmptyState.tsx.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyPrompt(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    actions: List<EmptyAction> = emptyList()
) {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.textDim, modifier = Modifier.size(40.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, textAlign = TextAlign.Center)
        if (actions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                actions.take(2).forEachIndexed { i, action ->
                    val content: @Composable () -> Unit = {
                        action.icon?.let {
                            Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                        }
                        Text(action.label)
                    }
                    if (i == 0) {
                        Button(
                            onClick = action.onClick,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
                        ) { content() }
                    } else {
                        OutlinedButton(
                            onClick = action.onClick,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                        ) { content() }
                    }
                }
            }
        }
    }
}
