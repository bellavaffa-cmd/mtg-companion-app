package com.mtgcompanion.app.data

import java.net.URLEncoder

/**
 * Where to buy cards. A whole list goes to TCGplayer's mass entry, which fills a basket in one go —
 * what a deck's missing cards and the Wishlist both need. Mirrors the web app's src/api/buy.ts.
 */

/** A card to buy and how many copies. */
data class BuyLine(val name: String, val quantity: Int)

/**
 * A basket of [cards] at TCGplayer: "2 Sol Ring||1 Cultivate", the format its mass entry page
 * reads. Null with nothing to buy.
 */
fun buyListUrl(cards: List<BuyLine>): String? {
    val lines = cards.filter { it.name.isNotBlank() }.map { "${maxOf(1, it.quantity)} ${it.name.trim()}" }
    if (lines.isEmpty()) return null
    return "https://store.tcgplayer.com/massentry?c=" + URLEncoder.encode(lines.joinToString("||"), "UTF-8")
}

/** Where to buy the card named [name], when there's no printing to link to. */
fun buyCardUrl(name: String): String =
    "https://www.tcgplayer.com/search/magic/product?q=" + URLEncoder.encode(name.trim(), "UTF-8") + "&productLineName=magic"
