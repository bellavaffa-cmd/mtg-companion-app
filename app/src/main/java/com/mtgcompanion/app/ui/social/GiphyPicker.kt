package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.social.Giphy
import com.mtgcompanion.app.data.social.GiphyGif
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pick a GIF from Giphy without leaving the app: trending until you type, then search results. Tap
 * one (or paste a link) and [onPicked] gets its Giphy link to do the rest with — fetch it for a
 * profile picture, or show it as a life counter background. A problem [onPicked] throws is shown here.
 */
@Composable
fun GiphyPickerDialog(social: SocialRepository, onPicked: suspend (link: String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf("") }
    var gifs by remember { mutableStateOf<List<GiphyGif>>(emptyList()) }
    var next by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var link by remember { mutableStateOf(clipboard.getText()?.text?.takeIf { Giphy.id(it) != null }.orEmpty()) }

    // Typing settles for a moment before searching, and one letter isn't searched: every search
    // counts against Giphy's hourly limit.
    LaunchedEffect(query) {
        val text = query.trim()
        if (text.length == 1) return@LaunchedEffect
        delay(800)
        searched = text
    }
    LaunchedEffect(searched) {
        loading = true
        error = null
        try {
            val page = social.api.searchGiphy(searched, 0)
            gifs = page.gifs
            next = page.next
        } catch (e: Exception) {
            gifs = emptyList()
            next = null
            error = e.message
        } finally {
            loading = false
        }
    }

    fun pick(target: String, key: String) {
        if (picking != null) return
        picking = key
        error = null
        scope.launch {
            try {
                onPicked(target)
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                picking = null
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(50) },
                    placeholder = { Text("Search Giphy") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.textDim) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { searched = query.trim() }),
                    shape = RoundedCornerShape(24.dp),
                    colors = socialFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(if (searched.isEmpty()) "Trending" else "“$searched”", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.padding(4.dp))
                }
                error?.let { item(span = { GridItemSpan(maxLineSpan) }) { Notice(it, warn = true) } }
                items(gifs, key = { it.id }) { gif ->
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface2)
                            .clickable(enabled = picking == null) { pick(gif.link, gif.id) }
                            .semantics { contentDescription = gif.title.ifBlank { "GIF" } },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(model = gif.preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        if (picking == gif.id) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = colors.accent, modifier = Modifier.height(28.dp))
                            }
                        }
                    }
                }
                if (loading) item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) }
                }
                if (!loading && gifs.isEmpty() && error == null) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("No GIFs for “$searched”.", color = colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                }
                if (!loading && next != null) item(span = { GridItemSpan(maxLineSpan) }) {
                    LineButton("More GIFs", {
                        val from = next ?: return@LineButton
                        loading = true
                        scope.launch {
                            try {
                                val page = social.api.searchGiphy(searched, from)
                                gifs = gifs + page.gifs.filter { g -> gifs.none { it.id == g.id } }
                                next = page.next
                            } catch (e: Exception) {
                                error = e.message
                            } finally {
                                loading = false
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        OutlinedTextField(
                            value = link,
                            onValueChange = { link = it },
                            placeholder = { Text("Or paste a Giphy link") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            colors = socialFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        LineButton("Use", { pick(link, "link") }, enabled = link.isNotBlank() && picking == null)
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "POWERED BY GIPHY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = colors.textDim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                }
            }
        }
    }
}
