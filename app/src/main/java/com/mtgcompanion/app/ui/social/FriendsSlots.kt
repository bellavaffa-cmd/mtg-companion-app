package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mtgcompanion.app.data.social.Overview
import com.mtgcompanion.app.data.social.SocialRepository

/*
 * Places on the Friends and Play tabs kept for work that comes next. Each draws nothing yet; fill the
 * body here and every screen that shows it picks it up. The web app's twin is
 * src/social/friendsSlots.tsx, with the same three names.
 */

/**
 * The next game night card: at the top of Friends › People and under Start a game on Play. Draws
 * nothing while there's no game night to show (and, for now, always). [onOpen] opens Game night.
 */
@Composable
fun NextGameNightCard(social: SocialRepository, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    // Filled by the game night invites work: the date, who's going, and Going? / Open.
}

/**
 * Pod chats, at the top of Friends › Chats above the direct messages (ConversationList's [header]).
 * Adds no rows for now. [onOpenPod]: a pod's chat, by pod id.
 */
fun LazyListScope.podChats(social: SocialRepository, overview: Overview, onOpenPod: (podId: String) -> Unit) {
    // Filled by the pod chat work.
}

/** New kinds of item at the top of Friends › Activity, above friends' feed. Draws nothing for now. */
@Composable
fun ActivityExtras(social: SocialRepository, overview: Overview) {
    // Filled by the activity work (new items: invites, comments…).
}
