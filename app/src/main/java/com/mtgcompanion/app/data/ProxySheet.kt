package com.mtgcompanion.app.data

import kotlin.math.ceil
import kotlin.math.roundToInt

// Proxy sheets: the cards to print and how many of each, laid out nine to a page at real card size
// (63 × 88 mm) on A4 or Letter, with thin cut lines between them. Pure: ProxyPrintDialog.kt prints
// [proxySheetHtml] with Android's print framework, as box labels print. Mirrors the web app's
// src/decks/proxySheet.ts.

const val CARD_WIDTH_MM = 63.0
const val CARD_HEIGHT_MM = 88.0
const val SHEET_COLUMNS = 3
const val SHEET_ROWS = 3
const val CARDS_PER_PAGE = SHEET_COLUMNS * SHEET_ROWS
/** Never more copies of one card than this on a sheet. */
const val MAX_PROXY_COPIES = 99
const val PROXY_MARK = "PROXY — not for sale"

enum class PaperSize(val label: String, val widthMm: Double, val heightMm: Double) {
    A4("A4", 210.0, 297.0),
    LETTER("Letter", 215.9, 279.4)
}

/** Letter where it's the usual paper (the US, Canada, Mexico, the Philippines…), A4 everywhere else. */
fun defaultPaper(country: String?): PaperSize =
    if ((country ?: "").uppercase() in setOf("US", "CA", "MX", "PH", "CL", "CO", "VE", "GT", "PR")) PaperSize.LETTER else PaperSize.A4

/** One card spot on a page, in millimetres from its top left corner. */
data class SheetSlot(val xMm: Double, val yMm: Double)

/** Where things go on one page, in millimetres from its top left corner. */
data class SheetLayout(
    val widthMm: Double,
    val heightMm: Double,
    /** The card grid's top left corner: the grid sits in the middle of the page. */
    val leftMm: Double,
    val topMm: Double,
    /** Each of the nine card spots, row by row. */
    val slots: List<SheetSlot>,
    /** The cut lines: across the whole page, along every card edge. */
    val cutsXMm: List<Double>,
    val cutsYMm: List<Double>
)

private fun round2(n: Double): Double = (n * 100).roundToInt() / 100.0

fun sheetLayout(paper: PaperSize): SheetLayout {
    val left = round2((paper.widthMm - SHEET_COLUMNS * CARD_WIDTH_MM) / 2)
    val top = round2((paper.heightMm - SHEET_ROWS * CARD_HEIGHT_MM) / 2)
    val slots = (0 until SHEET_ROWS).flatMap { row ->
        (0 until SHEET_COLUMNS).map { col -> SheetSlot(round2(left + col * CARD_WIDTH_MM), round2(top + row * CARD_HEIGHT_MM)) }
    }
    return SheetLayout(
        widthMm = paper.widthMm,
        heightMm = paper.heightMm,
        leftMm = left,
        topMm = top,
        slots = slots,
        cutsXMm = (0..SHEET_COLUMNS).map { round2(left + it * CARD_WIDTH_MM) },
        cutsYMm = (0..SHEET_ROWS).map { round2(top + it * CARD_HEIGHT_MM) }
    )
}

/** One card that could be printed, and how many copies are picked (0 leaves it off the sheet). */
data class ProxyPick(
    val name: String,
    val scryfallId: String,
    val imageUrl: String?,
    val backImageUrl: String? = null,
    val copies: Int
)

/** How the sheet prints. */
data class ProxyOptions(
    val paper: PaperSize = PaperSize.A4,
    /** "PROXY — not for sale" across each card. */
    val marked: Boolean = true,
    /** Black and white, lighter: saves ink. */
    val lowInk: Boolean = false,
    /** A double-faced card's back as a card of its own. */
    val backs: Boolean = true
)

private fun key(name: String) = name.trim().lowercase()
private fun clampCopies(n: Int) = n.coerceIn(0, MAX_PROXY_COPIES)

/** The deck's printing of each card, by name: what a card on a list looks like on the sheet. */
private fun printingsByName(entries: List<DeckCardEntry?>): LinkedHashMap<String, DeckCardEntry> {
    val out = LinkedHashMap<String, DeckCardEntry>()
    for (e in entries) if (e != null && key(e.name) !in out) out[key(e.name)] = e
    return out
}

private fun pickOf(e: DeckCardEntry, copies: Int) = ProxyPick(e.name, e.scryfallId, e.imageUrl, e.backImageUrl, clampCopies(copies))

/**
 * The cards a pull list doesn't own, as they are in [deck]: the copies still to buy picked. [needs]
 * is the pull list's Not owned rows, as (name, scryfallId, copies).
 */
fun picksFromNeeds(deck: Deck, needs: List<Triple<String, String, Int>>): List<ProxyPick> {
    val printings = printingsByName(listOf(deck.commander, deck.partnerCommander) + deck.cards)
    val byName = LinkedHashMap<String, ProxyPick>()
    for ((name, scryfallId, qty) in needs) {
        val had = byName[key(name)]
        if (had != null) {
            byName[key(name)] = had.copy(copies = clampCopies(had.copies + qty))
            continue
        }
        val e = printings[key(name)]
        byName[key(name)] = if (e != null) pickOf(e, qty) else ProxyPick(name, scryfallId, null, null, clampCopies(qty))
    }
    return byName.values.toList()
}

/**
 * Every card in [deck], once by name: the ones it still needs picked first — the cards you don't own
 * for a deck on paper, its proxies for a deck you hold — then the rest at 0, A–Z, to pick by hand.
 */
fun picksForDeck(deck: Deck, collections: List<Collection>, decks: List<Deck>): List<ProxyPick> =
    picksForDeck(deck, if (deck.holdsCards) emptyList() else missingCards(deck, collections, decks))

/** [picksForDeck] with the deck's [missing] cards already worked out (missingCards). */
fun picksForDeck(deck: Deck, missing: List<MissingCard>): List<ProxyPick> {
    val needed = HashMap<String, Int>()
    if (deck.holdsCards) {
        for (e in deck.cards) needed[key(e.name)] = (needed[key(e.name)] ?: 0) + proxyCopies(deck, e)
    } else {
        for (m in missing) needed[key(m.entry.name)] = m.need
    }
    val printings = printingsByName(listOf(deck.commander, deck.partnerCommander) + deck.cards)
    return printings.values
        .map { pickOf(it, needed[key(it.name)] ?: 0) }
        .sortedWith(compareByDescending<ProxyPick> { it.copies }.thenBy { it.name })
}

/** Cards spread too thin, the copies each is short picked. */
fun picksFromThin(cards: List<ThinCard>): List<ProxyPick> =
    cards.map { ProxyPick(it.name, it.scryfallIds.firstOrNull() ?: "", it.imageUrl, null, clampCopies(it.short)) }

/** [picks] with the copies of [name] set to [copies] (0 to 99). */
fun withCopies(picks: List<ProxyPick>, name: String, copies: Int): List<ProxyPick> =
    picks.map { if (key(it.name) == key(name)) it.copy(copies = clampCopies(copies)) else it }

/**
 * The biggest picture Scryfall has of the card, for print: its 'large' size, from the address of any
 * other size. Anything that isn't a Scryfall card picture is left as it is.
 */
fun proxyImageUrl(url: String?): String? {
    if (url.isNullOrEmpty()) return null
    if (!url.startsWith("https://cards.scryfall.io/")) return url
    return url
        .replace(Regex("/(small|normal|art_crop|border_crop|png)/"), "/large/")
        .replace(Regex("\\.png(\\?|$)"), ".jpg\$1")
}

/** One card on the sheet. */
data class SheetCard(val name: String, val imageUrl: String?)

/** Every copy to print, in order, each double-faced card's back after its front when [backs]. */
fun sheetCards(picks: List<ProxyPick>, backs: Boolean): List<SheetCard> = buildList {
    for (p in picks) repeat(p.copies) {
        add(SheetCard(p.name, proxyImageUrl(p.imageUrl)))
        if (backs && p.backImageUrl != null) add(SheetCard("${p.name} (back)", proxyImageUrl(p.backImageUrl)))
    }
}

/** The cards split into pages of nine. */
fun sheetPages(cards: List<SheetCard>): List<List<SheetCard>> = cards.chunked(CARDS_PER_PAGE)

/** Copies picked. */
fun pickedCopies(picks: List<ProxyPick>): Int = picks.sumOf { it.copies }

/** "12 cards · 2 pages", or "Nothing picked". */
fun sheetSummary(picks: List<ProxyPick>, backs: Boolean): String {
    val cards = sheetCards(picks, backs).size
    if (cards == 0) return "Nothing picked"
    val pages = ceil(cards / CARDS_PER_PAGE.toDouble()).toInt()
    return "$cards ${if (cards == 1) "card" else "cards"} · $pages ${if (pages == 1) "page" else "pages"}"
}

/**
 * The printed copies marked as proxies in [deck]: each card's proxy count goes up by the copies
 * printed, never past the copies the deck plays. Only for a deck on paper — a deck you hold already
 * counts the cards it hasn't got as proxies (PullList.kt). The same deck otherwise.
 */
fun markPrintedAsProxies(deck: Deck, picks: List<ProxyPick>): Deck {
    if (deck.holdsCards) return deck
    val printed = HashMap<String, Int>()
    for (p in picks) if (p.copies > 0) printed[key(p.name)] = (printed[key(p.name)] ?: 0) + p.copies
    if (printed.isEmpty()) return deck
    var changed = false
    val cards = deck.cards.map { e ->
        val want = printed[key(e.name)] ?: 0
        val already = (e.proxyQuantity ?: 0).coerceAtLeast(0)
        val take = minOf(want, e.quantity - already)
        if (take <= 0) return@map e
        printed[key(e.name)] = want - take
        changed = true
        e.copy(proxyQuantity = already + take)
    }
    if (!changed) return deck
    fun fresh(c: DeckCardEntry?) = c?.let { cards.firstOrNull { e -> e.scryfallId == it.scryfallId } ?: it }
    return deck.copy(cards = cards, commander = fresh(deck.commander), partnerCommander = fresh(deck.partnerCommander))
}

private fun escapeHtml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun mm(n: Double): String = "${if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()}mm"

/**
 * The sheet as a page to print: one page per nine cards, each card at its spot, the cut lines across
 * the page, the "PROXY — not for sale" mark and low ink as [options] say. The same page the web app
 * prints (ProxyPrintDialog.tsx and proxyPrint.css).
 */
fun proxySheetHtml(picks: List<ProxyPick>, options: ProxyOptions): String {
    val layout = sheetLayout(options.paper)
    val pages = sheetPages(sheetCards(picks, options.backs))
    val cuts = layout.cutsXMm.joinToString("") { "<div class=\"cut v\" style=\"left:${mm(it)}\"></div>" } +
        layout.cutsYMm.joinToString("") { "<div class=\"cut h\" style=\"top:${mm(it)}\"></div>" }
    val body = pages.joinToString("") { page ->
        val cards = page.mapIndexed { i, card ->
            val slot = layout.slots[i]
            val img = card.imageUrl?.let { "<img src=\"${escapeHtml(it)}\" alt=\"\">" } ?: ""
            val mark = if (options.marked) "<span class=\"mark\">${escapeHtml(PROXY_MARK)}</span>" else ""
            "<div class=\"card\" style=\"left:${mm(slot.xMm)};top:${mm(slot.yMm)}\"><span class=\"name\">${escapeHtml(card.name)}</span>$img$mark</div>"
        }.joinToString("")
        "<div class=\"page\">$cuts$cards</div>"
    }
    val size = if (options.paper == PaperSize.LETTER) "letter" else "A4"
    return """<!doctype html><html><head><meta charset="utf-8"><style>
@page { size: $size portrait; margin: 0; }
html, body { margin: 0; padding: 0; background: #fff; }
.page { position: relative; overflow: hidden; width: ${mm(layout.widthMm)}; height: ${mm(layout.heightMm)}; page-break-after: always; break-after: page; }
.page:last-child { page-break-after: auto; break-after: auto; }
.card { position: absolute; width: ${mm(CARD_WIDTH_MM)}; height: ${mm(CARD_HEIGHT_MM)}; overflow: hidden; background: #fff; display: flex; align-items: center; justify-content: center; }
.card img { position: absolute; left: 0; top: 0; width: 100%; height: 100%; object-fit: cover; display: block; ${if (options.lowInk) "filter: grayscale(1) brightness(1.18) contrast(0.8);" else ""} }
.name { font: 600 4mm sans-serif; color: #14161c; text-align: center; padding: 4mm; }
.mark { position: absolute; left: 50%; bottom: 1.2mm; transform: translateX(-50%); padding: 0.4mm 1.6mm; border-radius: 1mm; background: rgba(255,255,255,0.85); color: #14161c; font: 700 2.2mm sans-serif; letter-spacing: 0.04em; white-space: nowrap; }
.cut { position: absolute; background: #9a9a9a; z-index: 1; }
.cut.v { top: 0; bottom: 0; width: 0.1mm; }
.cut.h { left: 0; right: 0; height: 0.1mm; }
</style></head><body>$body</body></html>"""
}
