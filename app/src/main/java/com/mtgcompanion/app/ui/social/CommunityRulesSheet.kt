package com.mtgcompanion.app.ui.social

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mtgcompanion.app.data.social.CommunityRulesPolicy
import com.mtgcompanion.app.data.social.CommunityRulesStore
import com.mtgcompanion.app.data.supabase.SupabaseAuth
import com.mtgcompanion.app.ui.common.KeepSystemBarsHidden
import com.mtgcompanion.app.ui.common.LocalLayoutSize
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/** The app-wide community rules store; `rememberCommunityRules().require { post() }` gates a first post. */
@Composable
fun rememberCommunityRules(): CommunityRulesStore {
    val context = LocalContext.current
    return remember(context) { CommunityRulesStore.get(context) }
}

/**
 * The one-time community rules sheet, shown over everything whenever [CommunityRulesStore.require]
 * or [CommunityRulesStore.show] asks for it. Lives once in MtgNavGraph. Agree remembers it on the
 * device and, signed in, on the account; then whatever was waiting (saving a profile, sending a
 * message…) goes ahead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityRulesHost(auth: SupabaseAuth) {
    val rules = rememberCommunityRules()
    val pending by rules.pending.collectAsState()
    if (pending == null) return
    val deviceVersion by rules.deviceVersion.collectAsState()
    val alreadyAgreed = deviceVersion >= CommunityRulesPolicy.VERSION
    val scope = rememberCoroutineScope()
    val agree: () -> Unit = {
        val action = rules.agree()
        if (auth.account.value != null) scope.launch { runCatching { auth.saveCommunityRulesVersion(CommunityRulesPolicy.VERSION) } }
        action?.invoke()
    }
    val app = LocalAppColors.current

    if (LocalLayoutSize.current.isWide) {
        Dialog(onDismissRequest = rules::dismiss) {
            KeepSystemBarsHidden()
            Column(
                Modifier
                    .width(480.dp)
                    .heightIn(max = 680.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(app.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                CommunityRulesContent(alreadyAgreed, onAgree = agree, onDismiss = rules::dismiss)
            }
        }
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = rules::dismiss,
        sheetState = sheetState,
        containerColor = app.surface,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 40.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(app.surface3))
        }
    ) {
        KeepSystemBarsHidden()
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            CommunityRulesContent(alreadyAgreed, onAgree = agree, onDismiss = rules::dismiss)
        }
    }
}

@Composable
private fun CommunityRulesContent(alreadyAgreed: Boolean, onAgree: () -> Unit, onDismiss: () -> Unit) {
    val app = LocalAppColors.current
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Community rules",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = app.textPrimary,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            "Your profile, messages, trades and what you share are seen by other players. Keep it friendly:",
            style = MaterialTheme.typography.bodyMedium,
            color = app.textMuted
        )
        CommunityRulesPolicy.RULES.forEach { (title, detail) ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.padding(top = 7.dp).size(6.dp).clip(RoundedCornerShape(50)).background(app.accent))
                Column {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = app.textPrimary)
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = app.textMuted)
                }
            }
        }
        Text(CommunityRulesPolicy.ENFORCEMENT, style = MaterialTheme.typography.bodyMedium, color = app.textPrimary)
        Text(CommunityRulesPolicy.HOW_TO_REPORT, style = MaterialTheme.typography.bodyMedium, color = app.textMuted)
        TextButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CommunityRulesPolicy.FULL_RULES_URL))) }
        }) { Text("Read the full rules on manabind.com", color = app.accent) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            if (alreadyAgreed) {
                GoldButton("Close", onDismiss)
            } else {
                LineButton("Not now", onDismiss)
                GoldButton("Agree", onAgree)
            }
        }
    }
}
