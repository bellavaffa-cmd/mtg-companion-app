package com.mtgcompanion.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.PutAwayResult
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.ArtImage
import com.mtgcompanion.app.ui.theme.LocalAppColors

/*
 * The scanner's put-away mode: the place cards are going into, at the top (tap to change), and at the
 * bottom the card just put away — where to file it and what happened to it — with this session's
 * cards and Undo last; for a binder in order, how many cards wait to be fitted in (BinderPages.kt). The
 * logic is putAway() in data/StoragePlaces.kt; the web app's ScanPage.tsx shows the same.
 */

/** "Putting away into: Red box", the button that changes the place. */
@Composable
fun PutAwayTarget(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
    ) {
        Text(
            "Putting away into: $name",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Filled.ExpandMore, contentDescription = "Change the place", tint = colors.onAccent)
    }
}

/** Scan-to-tick mode's banner: which list the cards tick, and how far it's got. */
@Composable
fun TickTarget(text: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.accent)
            .padding(horizontal = 14.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onAccent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val Moved = Color(0xFF5BCB8F)

/** The card just put away, and the session so far. */
@Composable
fun PutAwayPanel(
    session: List<PutAwayRow>,
    onUndoLast: () -> Unit,
    onAnotherCopy: (PutAwayRow) -> Unit,
    modifier: Modifier = Modifier,
    /** A binder in order: how many cards wait to be fitted in, and Add cards in order. */
    waitingToFit: Int = 0,
    onFit: () -> Unit = {}
) {
    val colors = LocalAppColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(colors.bg.copy(alpha = 0.94f))
            .padding(16.dp)
    ) {
        if (waitingToFit > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("$waitingToFit ${if (waitingToFit == 1) "card" else "cards"} to fit in order", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("Keep them beside the binder — the steps say where each goes.", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                }
                Text(
                    "Fit in order",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onAccent,
                    modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(16.dp)).background(colors.accent).clickable(onClick = onFit).padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        val last = session.firstOrNull()
        if (last == null) {
            Text("Scan a card and it's put away here — given its place, moved from another, or added to your collection.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            return@Column
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            ArtImage(last.card.displayImageUrl.toArtCropUrl(), last.card.name, Modifier.width(36.dp).height(50.dp).clip(RoundedCornerShape(4.dp)))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(last.card.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (last.result == PutAwayResult.HERE) {
                    Text(
                        "It's another copy — add it",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accentLight,
                        modifier = Modifier.clickable { onAnotherCopy(last) }.padding(vertical = 2.dp)
                    )
                } else if (last.hint != null) {
                    Text("File in: ${last.hint}", style = MaterialTheme.typography.labelLarge, color = colors.accentLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val (fill, ink) = when (last.result) {
                PutAwayResult.PLACED, PutAwayResult.MOVED -> Moved.copy(alpha = 0.16f) to Moved
                PutAwayResult.NEW -> colors.accent.copy(alpha = 0.16f) to colors.accentLight
                PutAwayResult.HERE -> colors.surface2 to colors.textMuted
            }
            Text(
                last.label.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = ink,
                modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(10.dp)).background(fill).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                "This session: ${session.size} ${if (session.size == 1) "card" else "cards"}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Undo last",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onUndoLast).padding(8.dp)
            )
        }
        // The newest few; the rest of the session is counted above.
        session.drop(1).take(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(row.card.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    "${row.where} · ${row.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (row.result) {
                        PutAwayResult.PLACED, PutAwayResult.MOVED -> Moved
                        PutAwayResult.NEW -> colors.accentLight
                        PutAwayResult.HERE -> colors.textMuted
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
