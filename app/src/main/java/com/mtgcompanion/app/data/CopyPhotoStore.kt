package com.mtgcompanion.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * Photos of your copies (CopyPhotos.kt), kept on this phone only — in the app's files, never synced or
 * uploaded. Each photo is a JPEG in files/copy_photos, made no bigger than [MAX_SIDE] on its long side;
 * the details are in copy_photos/index.json. Also the setting "ask for photos when adding a card worth
 * over $X". Writing is best effort, as the copy history's is. The web app keeps its own in the browser
 * (src/collection/copyPhotoStore.ts).
 */
object CopyPhotoStore {
    /** What's stored: every copy with photos or details. */
    data class Saved(val photos: List<CopyPhoto> = emptyList(), val askOver: Double? = null)

    private const val MAX_SIDE = 1600
    private val adapter by lazy { localMoshi.adapter(Saved::class.java) }
    private var dir: File? = null
    private val _saved = MutableStateFlow(Saved())
    val saved: StateFlow<Saved> = _saved.asStateFlow()

    /** Loads what's kept, once. */
    @Synchronized
    fun init(context: Context) {
        if (dir != null) return
        val d = File(context.applicationContext.filesDir, "copy_photos")
        dir = d
        val index = File(d, "index.json")
        _saved.value = runCatching { if (index.exists()) adapter.fromJson(index.readText()) else null }.getOrNull() ?: Saved()
    }

    private fun write(next: Saved) {
        _saved.value = next
        runCatching {
            val d = dir ?: return
            d.mkdirs()
            File(d, "index.json").writeText(adapter.toJson(next))
        }
    }

    fun photo(key: String): CopyPhoto? = _saved.value.photos.firstOrNull { it.key == key }

    /** Saves [photo] in place of the one with its key. */
    @Synchronized
    fun save(photo: CopyPhoto) {
        val rest = _saved.value.photos.filter { it.key != photo.key }
        write(_saved.value.copy(photos = rest + photo))
    }

    /** The setting: ask for photos when adding a card worth over this many dollars; null: never. */
    @Synchronized
    fun setAskOver(usd: Double?) = write(_saved.value.copy(askOver = usd?.takeIf { it >= 0 }))

    /** The file a photo is kept in. */
    fun file(name: String): File? = dir?.let { File(it, name) }

    /**
     * Copies the picture at [uri] in as [ref]'s front ([front] true) or back, scaled down and turned
     * upright, and notes when it was photographed. False when it couldn't be read or written.
     */
    fun setPhoto(context: Context, ref: CopyRef, front: Boolean, uri: Uri): Boolean {
        val d = dir ?: return false
        val bitmap = runCatching { readScaled(context, uri) }.getOrNull() ?: return false
        val name = "${UUID.randomUUID()}.jpg"
        val ok = runCatching {
            d.mkdirs()
            File(d, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }.getOrDefault(false)
        if (!ok) return false
        synchronized(this) {
            val had = photo(ref.key) ?: CopyPhoto(ref.key, ref.scryfallId, ref.name, ref.foil)
            val old = if (front) had.front else had.back
            old?.let { runCatching { File(d, it).delete() } }
            save(
                (if (front) had.copy(front = name) else had.copy(back = name))
                    .copy(name = ref.name, photographedAt = System.currentTimeMillis())
            )
        }
        return true
    }

    /** A photo as a data: URL for the printed report, or null when it's gone. */
    fun dataUrl(name: String?): String? {
        val f = name?.let { file(it) } ?: return null
        val bytes = runCatching { f.readBytes() }.getOrNull() ?: return null
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun readScaled(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        val long = maxOf(decoded.width, decoded.height)
        val scale = if (long > MAX_SIDE) MAX_SIDE.toFloat() / long else 1f
        if (rotation == 0f && scale == 1f) return decoded
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
    }
}
