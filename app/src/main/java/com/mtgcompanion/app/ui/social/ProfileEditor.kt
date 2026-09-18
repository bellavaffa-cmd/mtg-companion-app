package com.mtgcompanion.app.ui.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.social.Profile
import com.mtgcompanion.app.data.social.SocialException
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private val USERNAME = Regex("[a-z0-9_]{3,20}")
private const val MAX_AVATAR_BYTES = 2 * 1024 * 1024
/** Photos are made this size (square, cropped to the middle) before uploading. */
private const val AVATAR_SIZE = 512

/** A username from an email, as a starting suggestion: "Jane.Doe+mtg@…" → "janedoe". */
private fun suggestUsername(email: String?): String =
    email.orEmpty().substringBefore('@').lowercase().substringBefore('+').filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(20)

/**
 * Makes or edits the user's profile: a unique username (how friends find them), the name shown to
 * others, and a picture — a photo, or a GIF that keeps moving on the life counter.
 */
@Composable
fun ProfileEditor(social: SocialRepository, onDone: (() -> Unit)?) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val overview by social.overview.collectAsState()
    val me: Profile? = overview?.me
    var username by remember { mutableStateOf(me?.username ?: suggestUsername(social.email)) }
    var name by remember { mutableStateOf(me?.displayName ?: "") }
    var picture by remember { mutableStateOf<Uri?>(null) }
    var removePicture by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val clean = username.trim().lowercase()
    LaunchedEffect(clean) {
        available = null
        if (!USERNAME.matches(clean) || clean == me?.username) return@LaunchedEffect
        delay(400)
        available = runCatching { social.api.usernameAvailable(clean) }.getOrNull()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { picture = uri; removePicture = false; error = null }
    }

    val problem = when {
        clean.isNotEmpty() && !USERNAME.matches(clean) -> "3–20 letters, numbers or _"
        available == false -> "Taken — try another"
        else -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (picture != null) {
                AsyncImage(model = picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp).clip(CircleShape))
            } else {
                Avatar(
                    profile = Profile("", clean, name.ifBlank { clean.ifBlank { "?" } }, if (removePicture) null else me?.avatarPath),
                    size = 88.dp
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LineButton(
                    if (me?.avatarPath != null || picture != null) "Change picture" else "Add a picture",
                    { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    icon = { Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                if (picture != null || (me?.avatarPath != null && !removePicture)) {
                    TextButton(onClick = { picture = null; removePicture = true }) { Text("Remove picture", color = colors.textMuted) }
                }
                Text("A photo, or a GIF (up to 2 MB) — it plays on the life counter too.", style = MaterialTheme.typography.bodySmall, color = colors.textDim)
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text("Your name") },
            singleLine = true,
            colors = socialFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it.lowercase().filterNot(Char::isWhitespace).take(20) },
            label = { Text("Username") },
            prefix = { Text("@", color = colors.textDim) },
            singleLine = true,
            isError = problem != null,
            supportingText = { Text(problem ?: if (available == true) "Available" else "Friends add you by this name.") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            colors = socialFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Notice(it, warn = true) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            if (onDone != null && me != null) LineButton("Cancel", onDone, enabled = !busy)
            GoldButton(
                if (busy) "Saving…" else if (me != null) "Save profile" else "Create profile",
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val old = me?.avatarPath
                            val path: String? = when {
                                picture != null -> {
                                    val (bytes, type) = avatarBytes(context, picture!!)
                                    social.api.uploadAvatar(social.userId ?: throw SocialException("not_signed_in", "Sign in first."), bytes, type)
                                }
                                removePicture -> ""
                                else -> null
                            }
                            social.api.saveProfile(clean, name.trim(), path)
                            if (path != null && old != null) social.api.deleteAvatar(old)
                            social.refresh()
                            onDone?.invoke()
                        } catch (e: Exception) {
                            error = e.message ?: "Something went wrong."
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && name.isNotBlank() && USERNAME.matches(clean) && available != false
            )
        }
    }
}

/**
 * The picture to upload: a GIF as it is (so it still moves, and only if under 2 MB), anything else
 * cropped square from the middle, shrunk and saved as a JPEG.
 */
private suspend fun avatarBytes(context: Context, uri: Uri): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val type = resolver.getType(uri).orEmpty()
    if (type == "image/gif") {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw SocialException("not_image", "That picture couldn't be opened.")
        if (bytes.size > MAX_AVATAR_BYTES) throw SocialException("too_big", "That GIF is over 2 MB — pick a smaller one.")
        return@withContext bytes to "image/gif"
    }
    val source: Bitmap = try {
        if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder turns the photo the right way up and can shrink it while decoding.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val side = minOf(info.size.width, info.size.height)
                if (side > AVATAR_SIZE * 2) {
                    val scale = AVATAR_SIZE * 2f / side
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: throw SocialException("not_image", "That picture couldn't be opened.")
        }
    } catch (e: SocialException) {
        throw e
    } catch (e: Exception) {
        throw SocialException("not_image", "That picture couldn't be opened.")
    }
    val side = minOf(source.width, source.height)
    val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    val size = minOf(AVATAR_SIZE, side)
    val scaled = if (side == size) square else Bitmap.createScaledBitmap(square, size, size, true)
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
    out.toByteArray() to "image/jpeg"
}
