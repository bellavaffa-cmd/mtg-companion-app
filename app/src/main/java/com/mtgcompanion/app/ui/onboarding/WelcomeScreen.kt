package com.mtgcompanion.app.ui.onboarding

import androidx.compose.foundation.background
import com.mtgcompanion.app.ui.common.a11yHeading
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.data.WelcomeFacts
import com.mtgcompanion.app.data.WelcomeStep
import com.mtgcompanion.app.data.nextStep
import com.mtgcompanion.app.data.parseCardList
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.stepDone
import com.mtgcompanion.app.data.supabase.SupabaseSync
import com.mtgcompanion.app.ui.collection.ImportCardsDialog
import com.mtgcompanion.app.ui.settings.AccountSyncSection
import com.mtgcompanion.app.ui.social.ProfileEditor
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.EyebrowStyle
import com.mtgcompanion.app.ui.theme.LocalAppColors

private fun titleOf(step: WelcomeStep) = when (step) {
    WelcomeStep.COLLECTION -> "Bring your collection"
    WelcomeStep.DECK -> "Your first deck"
    WelcomeStep.ACCOUNT -> "Play and friends"
    WelcomeStep.DONE -> "You're all set"
}

/**
 * The welcome flow: bring your cards in, make a first deck, sign in — each skippable — then Home.
 * Opens by itself once on a first launch with an empty library; Settings › Getting started and
 * Home's "Get started" card open it again at any step. Every option reuses what the app already has:
 * the binder import, the scanner, the precons screen, Account & sync and the profile editor. With
 * [startPasting], the deck step opens with its paste dialog (the empty Decks tab's "Paste a list").
 * The web app's src/onboarding/WelcomePage.tsx.
 */
@Composable
fun WelcomeScreen(
    startStep: WelcomeStep,
    viewModel: WelcomeViewModel,
    facts: WelcomeFacts,
    supabaseSync: SupabaseSync,
    socialRepository: SocialRepository,
    /** Leaves the flow for Home: "done" or "skipped". */
    onFinish: (how: String) -> Unit,
    /** The last step was reached: remembered as done, however the flow is left from there. */
    onReachedDone: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenPrecons: () -> Unit,
    onOpenDeck: (String) -> Unit,
    startPasting: Boolean = false
) {
    val colors = LocalAppColors.current
    var step by rememberSaveable { mutableStateOf(startStep) }
    var importing by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(startPasting) }
    var deckName by rememberSaveable { mutableStateOf("My deck") }
    val importProgress by viewModel.importProgress.collectAsState()
    val pasteProgress by viewModel.pasteProgress.collectAsState()
    val adding by viewModel.addingSamples.collectAsState()
    val samplesError by viewModel.samplesError.collectAsState()
    LaunchedEffect(step) { if (step == WelcomeStep.DONE) onReachedDone() }

    val done = stepDone(step, facts)
    val index = step.ordinal
    val total = WelcomeStep.entries.size

    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.TopCenter) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    WelcomeStep.entries.forEach { s ->
                        val on = s == step
                        val tint = when {
                            on -> colors.accent
                            s != WelcomeStep.DONE && stepDone(s, facts) -> colors.success
                            else -> colors.border
                        }
                        Box(
                            Modifier.height(10.dp).width(if (on) 26.dp else 10.dp).clip(RoundedCornerShape(5.dp)).background(tint)
                                .clickable { step = s }
                        )
                    }
                }
                if (step != WelcomeStep.DONE) TextButton(onClick = { onFinish("skipped") }) { Text("Skip", color = colors.accent) }
            }

            Text(
                (if (step == WelcomeStep.COLLECTION) "Welcome to Manabind" else "Step ${index + 1} of $total").uppercase(),
                style = EyebrowStyle,
                color = colors.textMuted
            )
            Text(titleOf(step), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.a11yHeading())

            when (step) {
                WelcomeStep.COLLECTION -> {
                    Lead("Already keep your cards in another app? Bring them in. Or scan a few to start.")
                    if (done) DoneNote("${facts.cards} ${if (facts.cards == 1) "card is" else "cards are"} in your collection.")
                    Option(Icons.AutoMirrored.Filled.PlaylistAdd, "Import a list or file", "A .csv or .txt export from ManaBox, Moxfield, Archidekt, Deckbox, TCGplayer or Dragon Shield, or a pasted list.") { importing = true }
                    Option(Icons.Filled.PhotoCamera, "Scan a few cards", "Point your camera at a card to add it.", onClick = onOpenScan)
                    SampleOption(facts.samples, adding, samplesError, onAdd = { viewModel.addSamples(onOpenDeck) }, onRemove = viewModel::removeSamples)
                }
                WelcomeStep.DECK -> {
                    Lead("Paste a list from anywhere, or start from an official precon.")
                    if (done) DoneNote("You have ${facts.decks} ${if (facts.decks == 1) "deck" else "decks"}.")
                    OutlinedTextField(value = deckName, onValueChange = { deckName = it.take(60) }, label = { Text("Deck name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Option(Icons.Filled.Edit, "Paste a list", "On Moxfield or Archidekt, use Export › Copy as plain text, then paste it here.") { pasting = true }
                    Option(Icons.Filled.Inventory2, "Start from a precon", "Every official Commander precon, ready to copy.", onClick = onOpenPrecons)
                }
                WelcomeStep.ACCOUNT -> AccountStep(facts, supabaseSync, socialRepository, onNext = { step = WelcomeStep.DONE })
                WelcomeStep.DONE -> {
                    Lead("Home is where your decks, binders and games come together.")
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).padding(8.dp)
                    ) {
                        listOf(WelcomeStep.COLLECTION, WelcomeStep.DECK, WelcomeStep.ACCOUNT).forEach { s ->
                            val ok = stepDone(s, facts)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { step = s }.padding(10.dp)
                            ) {
                                Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, contentDescription = null, tint = if (ok) colors.success else colors.textDim)
                                Text(titleOf(s), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                Text(if (ok) "Done" else "Later, from Home", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                if (index > 0) {
                    OutlinedButton(onClick = { step = WelcomeStep.entries[index - 1] }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Back", color = colors.textPrimary)
                    }
                }
                if (step == WelcomeStep.DONE) {
                    Button(onClick = { onFinish("done") }, colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)) {
                        Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Go to Home")
                    }
                } else if (done) {
                    Button(onClick = { step = nextStep(step) }, colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)) {
                        Text("Next  ")
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                } else {
                    OutlinedButton(onClick = { step = nextStep(step) }) {
                        Text("Later  ", color = colors.textPrimary)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (importing) {
        ImportCardsDialog(
            title = "Import list",
            askName = true,
            progress = importProgress,
            onImport = viewModel::importBinder,
            onDismiss = { importing = false; viewModel.resetImport() }
        )
    }
    if (pasting) {
        PasteDeckDialog(
            name = deckName,
            progress = pasteProgress,
            onPaste = { text -> viewModel.pasteDeck(deckName, text) },
            onDismiss = { pasting = false; viewModel.resetPaste() },
            onOpenDeck = { id -> pasting = false; viewModel.resetPaste(); onOpenDeck(id) }
        )
    }
}

@Composable
private fun Lead(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalAppColors.current.textMuted, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun DoneNote(text: String) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
    }
}

@Composable
private fun Option(icon: ImageVector, title: String, text: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.accent.copy(alpha = 0.14f)).clickable(onClick = onClick).padding(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textDim)
    }
}

/** Step 3: sign in or make an account, then pick a username — Account & sync and the profile editor. */
@Composable
private fun AccountStep(facts: WelcomeFacts, supabaseSync: SupabaseSync, socialRepository: SocialRepository, onNext: () -> Unit) {
    val colors = LocalAppColors.current
    val overview by socialRepository.overview.collectAsState()
    val loading by socialRepository.loading.collectAsState()
    Lead("Optional. An account keeps your phone and the web in sync, and lets you add friends and trade cards.")
    val current = overview
    when {
        !facts.accountsAvailable -> Text("Accounts aren't set up in this build. Everything stays on this phone.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        !facts.signedIn -> AccountSyncSection(supabaseSync)
        current == null -> Text(if (loading) "Loading your profile…" else "Couldn't load your profile. Try again from Friends.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        current.me == null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pick a username", style = MaterialTheme.typography.titleMedium)
            Text("Friends find you by it, and see it on shared decks, trades and the life counter.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            ProfileEditor(social = socialRepository, onDone = onNext)
        }
        else -> DoneNote("Signed in as ${current.me.handle}.")
    }
}

/**
 * "Paste a list" for a deck that doesn't exist yet: the deck is made, named [name], once its cards
 * are found; then it opens. The web app's src/onboarding/PasteDeckDialog.tsx.
 */
@Composable
private fun PasteDeckDialog(
    name: String,
    progress: PasteProgress,
    onPaste: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenDeck: (String) -> Unit
) {
    val colors = LocalAppColors.current
    var text by remember { mutableStateOf("") }
    val parsed = remember(text) { parseCardList(text) }
    val working = progress is PasteProgress.Working

    if (progress is PasteProgress.Done) {
        AlertDialog(
            onDismissRequest = { onOpenDeck(progress.deckId) },
            containerColor = colors.surface,
            title = { Text("Deck made") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Added ${progress.added} ${if (progress.added == 1) "card" else "cards"} to $name.")
                    if (progress.missing.isNotEmpty()) {
                        Text("Couldn't find ${if (progress.missing.size == 1) "this one" else "these ${progress.missing.size}"}:", color = colors.textMuted)
                        Text(
                            progress.missing.take(25).joinToString("\n"),
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            color = colors.textPrimary,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface2).verticalScroll(rememberScrollState()).padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onOpenDeck(progress.deckId) }) { Text("Open deck", color = colors.accent) } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        containerColor = colors.surface,
        title = { Text("Paste a list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "One card per line, like \"1 Sol Ring\". Sideboard and maybeboard cards go to Considering.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Cards") },
                    placeholder = { Text("1 Sol Ring\n1 Arcane Signet") },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = colors.textPrimary),
                    enabled = !working,
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )
                if (parsed.lines.isNotEmpty()) {
                    Text(
                        "${parsed.lines.size} lines · ${parsed.cardCount} cards" + if (parsed.skipped.isNotEmpty()) " · ${parsed.skipped.size} unreadable" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim
                    )
                }
                if (progress is PasteProgress.Working) {
                    Text("Finding cards… ${progress.done}/${progress.total}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    LinearProgressIndicator(
                        progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                        color = colors.accent,
                        trackColor = colors.border,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                (progress as? PasteProgress.Failed)?.let { Text(it.message, style = MaterialTheme.typography.bodySmall, color = colors.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !working && parsed.lines.isNotEmpty(), onClick = { onPaste(text) }) {
                Text(if (parsed.cardCount > 0) "Make deck with ${parsed.cardCount} ${if (parsed.cardCount == 1) "card" else "cards"}" else "Make deck", color = colors.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel", color = colors.textMuted) } }
    )
}
