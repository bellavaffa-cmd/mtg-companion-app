package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.LocationChip
import com.mtgcompanion.app.data.LocationKind
import com.mtgcompanion.app.data.chipDescription
import com.mtgcompanion.app.data.chipText
import com.mtgcompanion.app.data.moreChipsDescription
import com.mtgcompanion.app.data.shownChips
import com.mtgcompanion.app.ui.theme.LocalAppColors

/*
 * Where a card's copies are, on its row: "Blue box › Instants ×2", "Talrand deck ×1", "Lent to Sam ×1",
 * at most two and then "+N", and an amber "No place yet · Put away" when some copies have none. The
 * words are data/CollectionLayout.kt's (chipText, chipDescription); the web app's LocationChips.tsx.
 */

/** What a chip's tap does. Null: the chip isn't a button (a "lent" tag with no loan). */
class ChipActions(
    val onPlace: ((placeId: String, section: String?, page: Int?) -> Unit)? = null,
    val onDeck: ((deckId: String) -> Unit)? = null,
    val onLent: (() -> Unit)? = null,
    val onPutAway: (() -> Unit)? = null,
    /** "+N": everything, on the card's page. */
    val onMore: (() -> Unit)? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocationChips(chips: List<LocationChip>, actions: ChipActions, modifier: Modifier = Modifier) {
    if (chips.isEmpty()) return
    val (shown, more) = shownChips(chips)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.padding(top = 4.dp)) {
        shown.forEach { chip ->
            val tap: (() -> Unit)? = when (chip.kind) {
                LocationKind.PLACE -> chip.placeId?.let { id -> actions.onPlace?.let { open -> { open(id, chip.section, chip.page) } } }
                LocationKind.DECK -> chip.deckId?.let { id -> actions.onDeck?.let { open -> { open(id) } } }
                LocationKind.LENT -> actions.onLent
                LocationKind.NONE -> actions.onPutAway
            }
            Chip(chipText(chip), chipDescription(chip), amber = chip.kind == LocationKind.NONE, onClick = tap)
        }
        if (more > 0) Chip("+$more", moreChipsDescription(more), amber = false, onClick = actions.onMore)
    }
}

@Composable
private fun Chip(text: String, description: String, amber: Boolean, onClick: (() -> Unit)?) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(12.dp)
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (amber) colors.warning else colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(shape)
            .background(if (amber) colors.warning.copy(alpha = 0.12f) else colors.surface2)
            .then(if (amber) Modifier.border(1.dp, colors.warning.copy(alpha = 0.45f), shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = description }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
