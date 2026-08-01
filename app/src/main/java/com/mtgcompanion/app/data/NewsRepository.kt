package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** One headline from an RSS feed. [publishedAt] is epoch millis, or null if the feed's date didn't parse. */
data class NewsItem(
    val title: String,
    val link: String,
    val source: String,
    val publishedAt: Long?
)

/**
 * Latest MTG news for the Home screen, pulled from a couple of sites' plain RSS 2.0 feeds and merged
 * newest-first. Parsed by hand with `XmlPullParser` (built into Android) rather than pulling in an XML
 * library — RSS's `<item>/<title>/<link>/<pubDate>` shape is simple enough not to need one.
 *
 * Reddit's r/magicTCG JSON API and Wizards' own site were the first choice, but both turned out
 * unusable: Reddit's public endpoints now return a Cloudflare-style 403 challenge to any non-browser
 * request, and Wizards doesn't appear to publish a working RSS URL for magic.wizards.com news anymore.
 * These two were verified live (plain `curl`, no bot-blocking) before being wired in.
 */
class NewsRepository {
    private val client = NetworkModule.noCacheOkHttpClient

    private val sources = listOf(
        "https://mtgazone.com/news/feed/" to "MTG Arena Zone",
        "https://articles.starcitygames.com/feed/" to "Star City Games"
    )

    /** Fetches every source in parallel, merges, and sorts newest-first. A source that fails is just omitted. */
    suspend fun fetchLatest(perSource: Int = 8): List<NewsItem> = withContext(Dispatchers.IO) {
        coroutineScope {
            sources.map { (url, name) -> async { runCatching { fetchFeed(url, name, perSource) }.getOrDefault(emptyList()) } }
                .awaitAll()
                .flatten()
                .sortedByDescending { it.publishedAt ?: 0L }
        }
    }

    private fun fetchFeed(url: String, sourceName: String, limit: Int): List<NewsItem> {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body ?: return emptyList()
            return parseRss(body.byteStream(), sourceName).take(limit)
        }
    }

    private fun parseRss(input: InputStream, sourceName: String): List<NewsItem> {
        val items = mutableListOf<NewsItem>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, null)

        var inItem = false
        var title: String? = null
        var link: String? = null
        var pubDate: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> {
                        inItem = true
                        title = null
                        link = null
                        pubDate = null
                    }
                    "title" -> if (inItem) title = parser.nextText()
                    "link" -> if (inItem) link = parser.nextText()
                    "pubDate" -> if (inItem) pubDate = parser.nextText()
                }
                XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                    val t = title?.trim()
                    val l = link?.trim()
                    if (!t.isNullOrEmpty() && !l.isNullOrEmpty()) {
                        items += NewsItem(t, l, sourceName, parsePubDate(pubDate))
                    }
                    inItem = false
                }
            }
            eventType = parser.next()
        }
        return items
    }

    private fun parsePubDate(raw: String?): Long? {
        if (raw == null) return null
        return runCatching {
            ZonedDateTime.parse(raw.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
