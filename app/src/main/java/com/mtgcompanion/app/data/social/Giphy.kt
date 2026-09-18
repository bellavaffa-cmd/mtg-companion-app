package com.mtgcompanion.app.data.social

import java.net.URI

/**
 * GIFs from Giphy by link: its page or the GIF itself. No Giphy account or key needed — these are
 * the public files Giphy serves for every GIF. Mirrors the web app's social/giphy.ts.
 */
object Giphy {
    const val SITE = "https://giphy.com/"

    private val ID = Regex("[A-Za-z0-9]{6,40}")

    /**
     * The GIF's id in a Giphy link, or null if it isn't one:
     *   https://giphy.com/gifs/happy-dance-3o7TKSjRrfIPjeiVyM   (a GIF's page; also /stickers/, /clips/)
     *   https://giphy.com/embed/3o7TKSjRrfIPjeiVyM
     *   https://media.giphy.com/media/3o7TKSjRrfIPjeiVyM/giphy.gif   (media0–4, and /media/v1.…/ID/)
     *   https://i.giphy.com/3o7TKSjRrfIPjeiVyM.gif
     */
    fun id(link: String): String? {
        val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "giphy.com" && !host.endsWith(".giphy.com")) return null
        val parts = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        fun valid(s: String?) = s?.takeIf { ID.matches(it) }
        return when {
            host == "i.giphy.com" -> if (parts.firstOrNull() == "media") valid(parts.getOrNull(1)) else valid(parts.firstOrNull()?.removeSuffix(".gif")?.removeSuffix(".webp"))
            Regex("media\\d?\\.giphy\\.com").matches(host) ->
                if (parts.firstOrNull() == "media") valid(if (parts.getOrNull(1)?.startsWith("v1.") == true) parts.getOrNull(2) else parts.getOrNull(1)) else null
            parts.firstOrNull() in setOf("gifs", "stickers", "clips", "embed") -> valid(parts.getOrNull(1)?.substringAfterLast('-'))
            else -> null
        }
    }

    /** Versions of the GIF to try, best first: the original, Giphy's under-2 MB one, then smaller ones. */
    fun renditions(id: String): List<String> =
        listOf("giphy.gif", "giphy-downsized.gif", "200.gif", "100.gif").map { "https://media.giphy.com/media/$id/$it" }

    /** A link a picture viewer can load: a Giphy page link becomes the GIF itself; anything else stays. */
    fun directUrl(link: String): String = id(link)?.let { renditions(it).first() } ?: link.trim()

    /**
     * Giphy answers a GIF that doesn't exist (a mistyped link, a deleted GIF) with its "This content
     * is not available" GIF rather than an error. It's the same file every time, so its size gives it away.
     */
    val NOT_AVAILABLE_SIZES = setOf(239321L, 158134L, 65583L)
}
