package com.mtgcompanion.app.data

import java.net.URLDecoder
import java.net.URLEncoder

/*
 * Box labels: a printable label for a storage place, with a QR code that names the place. The code is
 * a link, https://manabind.com/place/<id>, like the app's other codes (a friend's, a seat's, a share
 * link): the scanner in either app opens the place's sheet, and a phone's own camera opens the place
 * on manabind.com. It names the place only, so the label stays right as cards come and go.
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/placeLabel.ts, with the same tests
 * (PlaceLabelTest.kt ↔ tests/collection/placeLabel.test.ts).
 */

/** The start of a label's link; the place's id follows. */
const val PLACE_LINK_PREFIX = "https://manabind.com/place/"

/** What a label's QR code holds: the link to the place. */
fun placeLabelLink(placeId: String): String =
    PLACE_LINK_PREFIX + URLEncoder.encode(placeId, "UTF-8").replace("+", "%20")

/** The app's own addresses: its site, and a dev build's; and where it lived before manabind.com. */
private val APP_HOST = Regex("""^https?://(?:(?:www\.)?manabind\.com|localhost(?::\d+)?)/""", RegexOption.IGNORE_CASE)
private const val OLD_PATH = "/mtg-companion-web/"
private val PLACE_ID = Regex("^[A-Za-z0-9_-]{1,64}$")
/** A place's id alone, as the first labels held it: the ids both apps make. */
private val BARE_ID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

/**
 * The place a scanned code names. [bare]: the code was the place's id on its own, as the first labels
 * printed it — only worth acting on when it's one of the user's places, since any id looks like that.
 */
data class LabelScan(val id: String, val bare: Boolean)

/** The place a scanned code names, or null when it isn't a label. */
fun placeIdFromLabel(text: String): LabelScan? {
    val trimmed = text.trim()
    if (BARE_ID.matches(trimmed)) return LabelScan(trimmed.lowercase(), true)
    val rest = when {
        OLD_PATH in trimmed -> trimmed.substringAfter(OLD_PATH)
        else -> APP_HOST.find(trimmed)?.let { trimmed.substring(it.range.last + 1) } ?: return null
    }
    val parts = rest.substringBefore('?').substringBefore('#').trimEnd('/').split('/').filter { it.isNotEmpty() }
    if (parts.size != 2 || parts[0] != "place") return null
    val id = runCatching { URLDecoder.decode(parts[1].replace("+", "%2B"), "UTF-8") }.getOrNull() ?: return null
    return if (PLACE_ID.matches(id)) LabelScan(id, false) else null
}

/** How big a label prints: a small sticker, the end of a card box, or a divider standing in a box. */
enum class LabelSize(val label: String, val widthMm: Int, val heightMm: Int) {
    SMALL("Small", 62, 29),
    BOX_END("Box end", 90, 50),
    DIVIDER("Divider", 70, 100)
}

/** What a label shows beside its name and code. */
data class LabelShow(
    /** The places it sits in: "Shelf, study". */
    val where: Boolean = true,
    val sections: Boolean = true,
    val rule: Boolean = true,
    val count: Boolean = false
)

/** A label's lines, each null when it's hidden or there's nothing to say. */
data class LabelText(
    val where: String?,
    val name: String,
    val sections: String?,
    val rule: String?,
    val count: String?
)

/** The lines of [place]'s label: "Shelf, study", "Red box", "White · Blue · …", "By colour, then A–Z", "612 copies". */
fun labelText(place: StoragePlace, places: List<StoragePlace>, show: LabelShow, copies: Int): LabelText {
    val where = parentsOf(places, place.id).joinToString(" › ") { it.name }
    val sections = place.sections.orEmpty().filter { it.isNotBlank() }.joinToString(" · ")
    return LabelText(
        where = where.takeIf { show.where && it.isNotEmpty() },
        name = place.name,
        sections = sections.takeIf { show.sections && it.isNotEmpty() },
        rule = place.rule?.label?.takeIf { show.rule },
        count = if (show.count) "$copies ${if (copies == 1) "copy" else "copies"}" else null
    )
}

/** Every place, for "All labels": in the Storage tree's order. */
fun labelOrder(places: List<StoragePlace>): List<StoragePlace> = placeTree(places).map { it.place }
