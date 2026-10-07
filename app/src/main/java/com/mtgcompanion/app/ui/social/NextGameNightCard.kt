package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.data.social.GameNightReminders
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.dateTile
import com.mtgcompanion.app.data.social.myAnswer
import com.mtgcompanion.app.data.social.nextNight
import com.mtgcompanion.app.data.social.nightDay
import com.mtgcompanion.app.data.social.playLine
import com.mtgcompanion.app.data.social.rsvpLine
import com.mtgcompanion.app.ui.theme.LocalAppColors

/** Where the card sits: People (date tile, who's going, Going?) or Play (one line, Open). */
enum class NightCardVariant { PEOPLE, PLAY }

/**
 * The next game night the user is invited to, as a card that opens its invite
 * (GameNightInviteScreen) with [onOpen] and the night's id. On People ([variant] PEOPLE): the date
 * tile, "Game night at Priya's", "4 going · Sam maybe · you haven't answered" and Going?; on Play:
 * "Next game night · Fri 10 Oct" over "Priya's · you're going · Season 2". [podId]: that pod's next
 * night only. Fetching the nights also sets the day-before reminders (GameNightReminders). Shows
 * nothing when signed out, before the server has invites, or with no night coming. The web app's
 * NextGameNightCard.tsx.
 */
@Composable
fun NextGameNightCard(
    social: SocialRepository,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    variant: NightCardVariant = NightCardVariant.PEOPLE,
    podId: String? = null
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val account by social.accountFlow.collectAsState()
    val available by social.nights.available.collectAsState()
    val nights by social.nights.nights.collectAsState()
    val me = account?.userId
    LaunchedEffect(me) {
        if (me == null || !social.nights.check()) return@LaunchedEffect
        runCatching { social.nights.nights() }.onSuccess { GameNightReminders.schedule(context, it, me) }
    }
    if (me == null || available != true) return
    val night = remember(nights, podId) { nights?.let { nextNight(it, System.currentTimeMillis(), podId) } } ?: return

    if (variant == NightCardVariant.PLAY) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface)
                .clickable(role = Role.Button) { onOpen(night.id) }.padding(14.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("Next game night · ${nightDay(night.startsAt)}", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                Text(playLine(night, me), style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("Open", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        return
    }

    val (weekday, day) = dateTile(night.startsAt)
    val answered = myAnswer(night, me) != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.accentGlow)
            .clickable(role = Role.Button) { onOpen(night.id) }.padding(14.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.size(width = 48.dp, height = 52.dp).clip(RoundedCornerShape(12.dp)).background(colors.accent)
        ) {
            Text(weekday, color = colors.onAccent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            Text(day, color = colors.onAccent, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 24.sp)
        }
        Column(Modifier.weight(1f)) {
            Text("Game night at ${night.place}", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(rsvpLine(night, me), style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (answered) {
            Text("Open", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(colors.accent).padding(horizontal = 14.dp)
            ) {
                Text("Going?", color = colors.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
        }
    }
}
