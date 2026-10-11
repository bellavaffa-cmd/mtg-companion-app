package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.gradeLabel
import com.mtgcompanion.app.data.gradedOf
import com.mtgcompanion.app.data.gradedWhereLabel
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.EmptyPrompt
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.theme.LocalAppColors

/**
 * Every graded copy (Graded.kt), from the Collection's Storage tab: the card, its slab ("PSA 10"), what
 * it's worth as the user entered it and where it's kept, the dearest first. A slab opens its card's
 * page (where it can be changed). The web app's GradedListPage.tsx.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradedListScreen(collections: List<Collection>, onBack: () -> Unit, onOpenCard: (String) -> Unit) {
    val colors = LocalAppColors.current
    val money = rememberMoney()
    val graded = remember(collections) {
        gradedOf(collections).sortedWith(compareByDescending<com.mtgcompanion.app.data.GradedCard> { it.valueUsd ?: -1.0 }.thenBy { it.name.lowercase() })
    }
    val total = graded.sumOf { it.valueUsd ?: 0.0 }
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Graded cards", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading())
                        if (graded.isNotEmpty()) Text(
                            "${graded.size} ${if (graded.size == 1) "slab" else "slabs"}" + if (total > 0) " · ${money.format(total, whole = true)}" else "",
                            style = MaterialTheme.typography.labelMedium, color = colors.textMuted
                        )
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        if (graded.isEmpty()) {
            EmptyPrompt(Icons.Filled.Verified, "No graded cards yet. On a card's page, Where it is › Mark a copy as graded.", modifier = Modifier.padding(padding))
        } else LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(graded, key = { it.id }) { g ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surface)
                        .clickable(role = Role.Button) { onOpenCard(g.name) }
                        .padding(12.dp)
                ) {
                    AsyncImage(
                        model = g.imageUrl.toArtCropUrl(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 64.dp, height = 46.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Column(Modifier.weight(1f)) {
                        Text(g.name + if (g.isFoil) " · foil" else "", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(gradeLabel(g).ifEmpty { null }, g.cert?.let { "cert $it" }).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium, color = colors.accent, maxLines = 1
                        )
                        Text(gradedWhereLabel(collections, g.placeId, g.section), style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(g.valueUsd?.let { money.format(it, whole = true) } ?: "—", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                }
            }
        }
    }
}
