package com.mtgcompanion.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.MAX_USER_TAG_LENGTH
import com.mtgcompanion.app.data.tidyUserTags
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.Surface3
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

/**
 * The user's own tags on the copy they own: chips with a cross, and a box to add one. [known] are
 * tags used on other cards, offered as one-tap additions so a second card doesn't mean typing again.
 *
 * A tag belongs to the copy rather than the card, which is what the wording here has to carry: it
 * follows the card into any deck or binder (see DeckCardEntry.userTags).
 */
@Composable
fun UserTagsSection(
    tags: List<String>,
    known: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var typed by remember { mutableStateOf("") }
    val add: (String) -> Unit = { raw ->
        val next = tidyUserTags(tags + raw)
        if (next.size != tags.size) onChange(next)
        typed = ""
    }
    val has = tags.map { it.trim().lowercase() }.toSet()
    val offer = known.filterNot { it.trim().lowercase() in has }.take(6)

    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
        Text("Your tags", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Text(
            "Yours to write — \"proxy\", \"signed\", \"lent to Sam\". They follow this copy into any deck or binder.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        if (tags.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(tags) { tag ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Surface3)
                            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(50))
                            .padding(start = 10.dp, end = 2.dp)
                    ) {
                        Text(tag, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                        IconButton(onClick = { onChange(tags - tag) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Take off $tag", tint = TextMuted, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = typed,
                onValueChange = { if (it.length <= MAX_USER_TAG_LENGTH) typed = it },
                placeholder = { Text("Add a tag, e.g. proxy", color = TextMuted) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add(typed) })
            )
            IconButton(onClick = { add(typed) }, enabled = typed.isNotBlank()) {
                Icon(Icons.Filled.Add, contentDescription = "Add this tag", tint = if (typed.isNotBlank()) Gold else TextMuted)
            }
        }
        if (offer.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                items(offer) { tag ->
                    Text(
                        "+ $tag",
                        style = MaterialTheme.typography.labelMedium,
                        color = Gold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Surface3)
                            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(50))
                            .clickable { add(tag) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
