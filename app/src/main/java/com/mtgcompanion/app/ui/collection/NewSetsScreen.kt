package com.mtgcompanion.app.ui.collection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.saveable.rememberSaveable
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.DeckProfile
import com.mtgcompanion.app.data.RoleTags
import com.mtgcompanion.app.data.cardNameKeys
import com.mtgcompanion.app.data.fitsByCard
import com.mtgcompanion.app.data.galleryCards
import com.mtgcompanion.app.data.openingPacks
import com.mtgcompanion.app.data.releaseCountdown
import com.mtgcompanion.app.data.revealedLabel
import com.mtgcompanion.app.data.wantedCount
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckFits
import com.mtgcompanion.app.data.NewSetsStore
import com.mtgcompanion.app.data.SetCard
import com.mtgcompanion.app.data.SetInfo
import com.mtgcompanion.app.data.commanderDecks
import com.mtgcompanion.app.data.deckFits
import com.mtgcompanion.app.data.deckProfile
import com.mtgcompanion.app.data.fitReason
import com.mtgcompanion.app.data.isWishlist
import com.mtgcompanion.app.data.releaseLabel
import com.mtgcompanion.app.data.releaseSets
import com.mtgcompanion.app.data.wishlistReprints
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.EmptyAction
import com.mtgcompanion.app.ui.common.EmptyPrompt
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.theme.LocalAppColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// New sets: Scryfall's sets coming out soon and just out (data/NewSets.kt), each to follow — a
// followed set gets a notification the day it comes out (NewSetsStore.kt's SetReleaseCheck) — and
// one set's page: its spoilers — the cards revealed so far, newest first, each to want before release
// and with the decks it fits (SpoilersUi.kt, data/Spoilers.kt) — the cards that suit each of the
// user's Commander decks best, and the ones on their Wishlist. Reached from the Collection home. The
// web app's pages/NewSetsPage.tsx.

private val LONG_DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private fun longDay(date: String) = runCatching { LocalDate.parse(date).format(LONG_DAY) }.getOrDefault(date)

/** The bell: follow a set to hear when it's out. Asks to show notifications the first time. */
@Composable
private fun FollowButton(set: SetInfo) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val followed by NewSetsStore.followed.collectAsState()
    val on = set.code in followed
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    IconButton(onClick = {
        if (!on && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        NewSetsStore.setFollowed(context, set, !on)
    }) {
        Icon(
            if (on) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
            contentDescription = if (on) "Following ${set.name}" else "Follow ${set.name}",
            tint = if (on) colors.accent else colors.textDim
        )
    }
}

@Composable
private fun SetIcon(set: SetInfo) {
    val colors = LocalAppColors.current
    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        if (set.iconSvgUri != null) {
            AsyncImage(model = set.iconSvgUri, contentDescription = null, colorFilter = ColorFilter.tint(colors.textPrimary), modifier = Modifier.size(26.dp))
        } else {
            Icon(Icons.Filled.NewReleases, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSetsScreen(onBack: () -> Unit, onOpenSet: (String) -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var sets by remember { mutableStateOf<List<SetInfo>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        failed = false
        runCatching { NewSetsStore.releaseSets(context) }.onSuccess { sets = it }.onFailure { failed = true }
    }
    val today = NewSetsStore.today()
    val lists = remember(sets, today) { releaseSets(sets.orEmpty(), today) }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("New sets", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading()) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            when {
                failed && sets == null -> EmptyPrompt(
                    Icons.Filled.CloudOff, "Couldn't reach Scryfall for its sets.",
                    actions = listOf(EmptyAction("Try again", Icons.Filled.Refresh) { attempt++ })
                )
                sets == null -> Text("Asking Scryfall for its sets…", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                lists.upcoming.isEmpty() && lists.recent.isEmpty() -> EmptyPrompt(Icons.Filled.NewReleases, "No sets coming out or just out right now.")
                else -> {
                    Text(
                        "Open a set for its spoilers: the cards revealed so far, which of your decks each would fit, and Want to put one on your Wishlist before it's out. Follow a set with the bell to hear the day it's out and when cards for your decks are revealed.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted
                    )
                    if (lists.recent.isNotEmpty()) {
                        SectionTitle("Just out")
                        lists.recent.forEach { SetRow(it, today) { onOpenSet(it.code) } }
                    }
                    if (lists.upcoming.isNotEmpty()) {
                        SectionTitle("Coming soon")
                        lists.upcoming.forEach { SetRow(it, today) { onOpenSet(it.code) } }
                    }
                }
            }
            Text(
                "Sets and cards are Scryfall's, checked twice a day at most.",
                style = MaterialTheme.typography.labelMedium, color = colors.textDim,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val colors = LocalAppColors.current
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp).a11yHeading())
}

@Composable
private fun SetRow(set: SetInfo, today: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onClick).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
        ) {
            SetIcon(set)
            Column(Modifier.weight(1f)) {
                Text(set.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${releaseLabel(set.releasedAt.orEmpty(), today)} · ${longDay(set.releasedAt.orEmpty())}", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                Text(revealedLabel(set, today), style = MaterialTheme.typography.labelMedium, color = colors.textDim)
            }
        }
        FollowButton(set)
    }
}

/** Revealed cards shown at first, and how many more each "Show more" adds. */
private const val GALLERY_PAGE = 24

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSetScreen(
    code: String,
    collections: List<Collection>,
    decks: List<Deck>,
    onBack: () -> Unit,
    onOpenCard: (String) -> Unit,
    onOpenDeck: (String) -> Unit,
    onOpenNewSets: () -> Unit,
    /** Spoilers: want [Int] of a revealed card (0: not any more), with the set's release date. */
    onSetWant: (SetCard, Int, String?) -> Unit = { _, _, _ -> },
    /** Spoilers: put a revealed card on a deck's Considering list. */
    onConsider: (String, SetCard) -> Unit = { _, _ -> },
    /** Spoilers: the set's Opening packs list. */
    onOpenPacks: (String) -> Unit = {}
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val followed by NewSetsStore.followed.collectAsState()
    val roleVersion by RoleTags.version.collectAsState()
    var loaded by remember { mutableStateOf(false) }
    var set by remember { mutableStateOf<SetInfo?>(null) }
    var cards by remember { mutableStateOf<List<SetCard>?>(null) }
    var identities by remember { mutableStateOf<Map<String, List<String>>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var onlyMine by rememberSaveable { mutableStateOf(false) }
    var shown by rememberSaveable { mutableIntStateOf(GALLERY_PAGE) }
    val commander = remember(decks) { commanderDecks(decks) }
    val commanderKey = commander.joinToString(",") { "${it.id}:${it.commander?.scryfallId}:${it.partnerCommander?.scryfallId}" }
    val today = NewSetsStore.today()

    LaunchedEffect(code, attempt) {
        failed = false
        try {
            val found = NewSetsStore.releaseSets(context).firstOrNull { it.code == code.lowercase() }
            set = found
            loaded = true
            if (found != null && found.cardCount > 0) cards = NewSetsStore.setCards(found.code)
        } catch (e: Exception) {
            failed = true
        }
    }
    LaunchedEffect(commanderKey) {
        identities = if (commander.isEmpty()) emptyMap() else runCatching { NewSetsStore.commanderIdentities(commander) }.getOrDefault(emptyMap())
        // The decks' role tags (Mana ramp, Card draw…), for matching: looked up once a month at most.
        runCatching { RoleTags.ensure(commander.flatMap { d -> d.cards.map { it.name } }, CardRepository()) }
    }
    val profiles: List<DeckProfile> = remember(identities, commander, roleVersion) {
        val ids = identities ?: return@remember emptyList()
        commander.mapNotNull { d -> ids[d.id]?.let { deckProfile(d, it) { name -> RoleTags.tagsOf(name)?.map { id -> RoleTags.label(id) } } } }
    }
    val fits: List<DeckFits> = remember(cards, profiles) {
        val c = cards ?: return@remember emptyList()
        profiles.mapNotNull { p ->
            val found = deckFits(p, c)
            if (found.isEmpty()) null else DeckFits(p.deckId, p.deckName, found)
        }
    }
    val fitMap = remember(cards, profiles, today) { cards?.let { fitsByCard(it, profiles, today) }.orEmpty() }
    val gallery = remember(cards, fitMap, onlyMine) { cards?.let { galleryCards(it, fitMap, onlyMine) }.orEmpty() }
    val considering = remember(decks) {
        decks.associate { d -> d.id to d.considering.flatMap { cardNameKeys(it.name) }.toSet() }
    }
    val packs = remember(collections, cards) { cards?.let { openingPacks(collections, it) }.orEmpty() }
    val wanted = remember(cards, collections) {
        val names = collections.firstOrNull { it.isWishlist }?.entries.orEmpty().map { it.name.trim().lowercase() }.toSet()
        cards?.let { wishlistReprints(names, it) }.orEmpty()
    }
    // What fits now has been seen: the daily news only tells of cards revealed after this.
    LaunchedEffect(fitMap, followed) {
        if (code.lowercase() in followed && cards != null && identities != null) NewSetsStore.markRevealsSeen(context, code.lowercase(), fitMap.keys)
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(set?.name ?: "New set", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.a11yHeading()) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { set?.let { FollowButton(it) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val s = set
            when {
                failed -> EmptyPrompt(
                    Icons.Filled.CloudOff, "Couldn't reach Scryfall for this set.",
                    actions = listOf(EmptyAction("Try again", Icons.Filled.Refresh) { attempt++ })
                )
                !loaded -> Text("Asking Scryfall…", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                s == null -> EmptyPrompt(
                    Icons.Filled.NewReleases, "This set isn't coming out soon or just out.",
                    actions = listOf(EmptyAction("New sets", Icons.Filled.NewReleases, onOpenNewSets))
                )
                else -> {
                    Text(
                        "${releaseCountdown(s.releasedAt, today) ?: releaseLabel(s.releasedAt.orEmpty(), today)} · ${longDay(s.releasedAt.orEmpty())} · ${revealedLabel(s, today)}",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted
                    )
                    Text(
                        if (s.code in followed) "You're following it: you'll hear the day it's out, and (once a day at most) when cards that fit your decks are revealed."
                        else "Follow it with the bell to hear the day it's out, and when cards that fit your decks are revealed.",
                        style = MaterialTheme.typography.labelMedium, color = colors.textDim
                    )
                    val c = cards
                    when {
                        s.cardCount <= 0 -> Text(
                            "No cards revealed yet. Scryfall adds them as they're previewed — come back closer to the release.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(top = 12.dp)
                        )
                        c == null || identities == null -> Text("Looking through the set's cards…", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(top = 12.dp))
                        else -> {
                            if (packs.isNotEmpty()) {
                                OutlinedButton(onClick = { onOpenPacks(s.code) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("  Opening packs · ${packs.size} wanted ${if (packs.size == 1) "card" else "cards"}")
                                }
                            }
                            SectionTitle("Revealed so far")
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = onlyMine,
                                    onClick = { onlyMine = !onlyMine; shown = GALLERY_PAGE },
                                    enabled = profiles.isNotEmpty(),
                                    label = { Text("Only cards for my decks") }
                                )
                                Text("${gallery.size} ${if (gallery.size == 1) "card" else "cards"}", style = MaterialTheme.typography.labelMedium, color = colors.textDim)
                            }
                            if (commander.isEmpty()) Muted("No Commander decks yet: once you have one, each card says which decks it would fit.")
                            if (gallery.isEmpty()) Muted(if (onlyMine) "None of the cards revealed so far fit your Commander decks." else "No cards revealed yet.")
                            gallery.take(shown).chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    pair.forEach { card ->
                                        SpoilerTile(
                                            card = card, set = s, today = today,
                                            want = wantedCount(collections, card.id),
                                            fits = fitMap[card.id].orEmpty(),
                                            considering = fitMap[card.id].orEmpty().map { it.deckId }
                                                .filter { id -> cardNameKeys(card.name).any { it in considering[id].orEmpty() } }.toSet(),
                                            onOpen = { onOpenCard(card.name) },
                                            onWant = { n -> onSetWant(card, n, s.releasedAt) },
                                            onConsider = { m -> onConsider(m.deckId, card) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                            if (gallery.size > shown) {
                                TextButton(onClick = { shown += GALLERY_PAGE }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Show more (${gallery.size - shown} left)")
                                }
                            }
                            Text(
                                "Newest revealed first. A card fits a deck when it's in the commander's colours, legal in Commander once it's out, and shares a theme, role (Mana ramp, Removal…), category or creature type with at least four of the deck's cards. Tap a deck to put the card on its Considering list. Wanted cards go on your Wishlist and show \"Releases in …\" until the set is out; then their price fills in and your Wishlist price targets apply.",
                                style = MaterialTheme.typography.labelMedium, color = colors.textDim, modifier = Modifier.padding(top = 6.dp)
                            )
                            SectionTitle("Best for each deck")
                            when {
                                commander.isEmpty() -> Muted("No Commander decks yet: once you have one, the cards that suit it show here.")
                                fits.isEmpty() -> Muted("None of the cards shown so far suit your Commander decks.")
                                else -> fits.forEach { f ->
                                    TextButton(onClick = { onOpenDeck(f.deckId) }) {
                                        Text(f.deckName, color = colors.accent, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    f.fits.forEach { fit -> CardRow(fit.card, fitReason(fit)) { onOpenCard(fit.card.name) } }
                                }
                            }
                            SectionTitle("On your Wishlist")
                            if (wanted.isEmpty()) Muted("None of the cards shown so far are on your Wishlist.")
                            else wanted.forEach { card -> CardRow(card, "A new printing of a card on your Wishlist") { onOpenCard(card.name) } }
                        }
                    }
                }
            }
            Box(Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun Muted(text: String) {
    val colors = LocalAppColors.current
    Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
}

@Composable
private fun CardRow(card: SetCard, why: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(8.dp)
    ) {
        AsyncImage(
            model = card.imageUrl.toArtCropUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 52.dp, height = 38.dp).clip(RoundedCornerShape(8.dp)).background(colors.surface3)
        )
        Column(Modifier.weight(1f)) {
            Text(card.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(why, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        }
    }
}
