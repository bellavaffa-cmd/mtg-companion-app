package com.mtgcompanion.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.WelcomeFacts
import com.mtgcompanion.app.data.WelcomeStep
import com.mtgcompanion.app.data.libraryFacts
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.stepsToDo
import com.mtgcompanion.app.data.supabase.SupabaseSync
import com.mtgcompanion.app.ui.theme.EyebrowStyle
import com.mtgcompanion.app.ui.theme.LocalAppColors

/**
 * The facts the welcome steps look at, kept current; null until the decks and binders have loaded,
 * so nothing decides on an empty library that's only still being read. Loads the social profile
 * when signed in. The web app's useWelcomeFacts (src/onboarding/useWelcome.ts).
 */
@Composable
fun rememberWelcomeFacts(
    deckRepository: DeckRepository,
    collectionRepository: CollectionRepository,
    supabaseSync: SupabaseSync,
    socialRepository: SocialRepository
): WelcomeFacts? {
    val decks by deckRepository.decksFlow.collectAsState(initial = null as List<Deck>?)
    val collections by collectionRepository.collectionsFlow.collectAsState(initial = null as List<Collection>?)
    val account by supabaseSync.auth.account.collectAsState()
    val overview by socialRepository.overview.collectAsState()
    LaunchedEffect(account?.userId) {
        if (account != null && socialRepository.overview.value == null) runCatching { socialRepository.refresh() }
    }
    val d = decks
    val c = collections
    if (d == null || c == null) return null
    return libraryFacts(
        d, c,
        WelcomeFacts(
            accountsAvailable = supabaseSync.auth.configured,
            signedIn = account != null,
            hasProfile = overview?.me != null
        )
    )
}

private fun rowFor(step: WelcomeStep): Pair<ImageVector, String>? = when (step) {
    WelcomeStep.COLLECTION -> Icons.Filled.CollectionsBookmark to "Bring your collection"
    WelcomeStep.DECK -> Icons.Filled.Style to "Make your first deck"
    WelcomeStep.ACCOUNT -> Icons.Filled.Group to "Sign in to sync and add friends"
    WelcomeStep.DONE -> null
}

/**
 * Home with nothing of the user's own in it: the welcome steps not done yet, each opening the flow at
 * that step, and the samples to try it with. In place of Home's empty widgets. The web app's
 * GetStartedCard (src/onboarding/GetStarted.tsx).
 */
@Composable
fun GetStartedCard(
    facts: WelcomeFacts,
    addingSamples: Boolean,
    samplesError: String?,
    onOpenStep: (WelcomeStep) -> Unit,
    onAddSamples: () -> Unit,
    onRemoveSamples: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).padding(16.dp)
    ) {
        Text("GET STARTED", style = EyebrowStyle, color = colors.textMuted)
        Text("Make Manabind yours", style = MaterialTheme.typography.titleLarge)
        stepsToDo(facts).forEach { step ->
            val (icon, label) = rowFor(step) ?: return@forEach
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface2)
                    .clickable { onOpenStep(step) }.padding(horizontal = 12.dp, vertical = 11.dp)
            ) {
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textDim)
            }
        }
        SampleOption(facts.samples, addingSamples, samplesError, onAddSamples, onRemoveSamples)
    }
}

/** "Try it with a sample deck and binder", or — once they're in — where they are and how to remove them. */
@Composable
fun SampleOption(
    has: Boolean,
    adding: Boolean,
    error: String?,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.accent.copy(alpha = 0.14f))
                .then(if (!has && !adding) Modifier.clickable(onClick = onAdd) else Modifier)
                .padding(14.dp)
        ) {
            Icon(Icons.Filled.Science, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (has) "Samples added" else if (adding) "Adding samples…" else "Try it with a sample deck and binder",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    if (has) "A sample deck and binder are in Decks and Collection. They don't sync."
                    else "A real precon and a dozen of its cards, labelled Sample. They stay on this phone and go in one tap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
            if (has) {
                OutlinedButton(onClick = onRemove) { Text("Remove samples", color = colors.textPrimary) }
            } else {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textDim)
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
    }
}

/** While samples are in the library, wherever they show: what they are, and "Remove samples". */
@Composable
fun SamplesBar(onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.accent.copy(alpha = 0.14f)).padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(Icons.Filled.Science, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Text(
            "The sample deck and binder are only on this phone and don't sync.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = onRemove) { Text("Remove samples", color = colors.textPrimary) }
    }
}
