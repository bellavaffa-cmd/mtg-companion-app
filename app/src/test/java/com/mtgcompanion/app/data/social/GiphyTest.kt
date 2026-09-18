package com.mtgcompanion.app.data.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Reading Giphy links. The web app has the same checks — see MtgCompanionWeb/tests/social/giphy.test.ts. */
class GiphyTest {
    private val id = "3o7TKSjRrfIPjeiVyM"

    @Test
    fun `the GIF id is read from every kind of Giphy link`() {
        assertEquals(id, Giphy.id("https://giphy.com/gifs/happy-dance-$id"))
        assertEquals(id, Giphy.id("https://giphy.com/gifs/$id"))
        assertEquals(id, Giphy.id("https://giphy.com/stickers/cat-wave-$id?utm_source=share"))
        assertEquals(id, Giphy.id("https://giphy.com/embed/$id"))
        assertEquals(id, Giphy.id("https://media.giphy.com/media/$id/giphy.gif"))
        assertEquals(id, Giphy.id("https://media3.giphy.com/media/v1.Y2lkPTc5MGI3NjEx/$id/giphy.gif?cid=abc"))
        assertEquals(id, Giphy.id("https://i.giphy.com/$id.gif"))
        assertEquals(id, Giphy.id("https://i.giphy.com/media/$id/giphy.webp"))
        assertEquals(id, Giphy.id("  https://giphy.com/gifs/$id  "))
    }

    @Test
    fun `other links are not Giphy GIFs`() {
        assertNull(Giphy.id("https://giphy.com/"))
        assertNull(Giphy.id("https://giphy.com/search/cats"))
        assertNull(Giphy.id("https://notgiphy.com/gifs/x-$id"))
        assertNull(Giphy.id("https://example.com/media/$id/giphy.gif"))
        assertNull(Giphy.id("cats"))
    }

    @Test
    fun `a page link becomes the GIF itself, other links stay as they are`() {
        assertEquals("https://media.giphy.com/media/$id/giphy.gif", Giphy.directUrl("https://giphy.com/gifs/happy-dance-$id"))
        assertEquals("https://example.com/a.gif", Giphy.directUrl(" https://example.com/a.gif "))
    }
}
