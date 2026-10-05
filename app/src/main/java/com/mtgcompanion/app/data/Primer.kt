package com.mtgcompanion.app.data

// A deck's primer (Deck.description): a few lines of light markdown on how the deck plays. Read as
// blocks — headings ("#", "##", "###"), lists ("- " or "1. ") and paragraphs — each made of spans of
// text that may be bold (**…**), italic (*…* or _…_), a link ([words](https://…) or a bare
// https:// address) or a card ([[Card Name]], which opens the card). Only http and https links are
// made; everything else stays text. Pure, so it can be tested; the web app's src/decks/primer.ts
// reads it the same way.

/** The longest primer kept, in characters. */
const val MAX_DESCRIPTION = 20000

data class PrimerSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    /** A card's name: the span opens that card. */
    val card: String? = null,
    /** An http(s) address: the span opens it. */
    val url: String? = null
)

sealed class PrimerBlock {
    data class Heading(val level: Int, val spans: List<PrimerSpan>) : PrimerBlock()
    data class Paragraph(val spans: List<PrimerSpan>) : PrimerBlock()
    data class Bullets(val ordered: Boolean, val items: List<List<PrimerSpan>>) : PrimerBlock()
}

/** [text] as it's kept: Windows line ends made plain, blank edges trimmed, at most MAX_DESCRIPTION. */
fun tidyDescription(text: String): String =
    text.replace(Regex("\r\n?"), "\n").trim().take(MAX_DESCRIPTION)

private val HEADING = Regex("^(#{1,3})\\s+(.*)$")
private val BULLET = Regex("^\\s*[-*•]\\s+(.*)$")
private val NUMBERED = Regex("^\\s*\\d{1,3}[.)]\\s+(.*)$")
private val LINK = Regex("^\\[([^\\]\\n]+)\\]\\((https?://[^\\s)]+)\\)")
private val BARE_URL = Regex("^https?://[^\\s<>()]+")
private val TRAILING = Regex("[.,;:!?'\"]+$")
private val WORD_CHAR = Regex("[A-Za-z0-9]")

/** The primer's blocks, in order. */
fun parsePrimer(text: String): List<PrimerBlock> {
    val blocks = mutableListOf<PrimerBlock>()
    val paragraph = mutableListOf<String>()
    var listOrdered: Boolean? = null
    val items = mutableListOf<List<PrimerSpan>>()
    fun endParagraph() {
        if (paragraph.isNotEmpty()) blocks += PrimerBlock.Paragraph(parseSpans(paragraph.joinToString("\n")))
        paragraph.clear()
    }
    fun endList() {
        listOrdered?.let { blocks += PrimerBlock.Bullets(it, items.toList()) }
        listOrdered = null
        items.clear()
    }
    for (raw in text.replace(Regex("\r\n?"), "\n").split("\n")) {
        val line = raw.trimEnd()
        if (line.isBlank()) { endParagraph(); endList(); continue }
        val heading = HEADING.find(line)
        if (heading != null) {
            endParagraph(); endList()
            blocks += PrimerBlock.Heading(heading.groupValues[1].length, parseSpans(heading.groupValues[2].trim()))
            continue
        }
        val bullet = BULLET.find(line)
        val numbered = if (bullet == null) NUMBERED.find(line) else null
        if (bullet != null || numbered != null) {
            endParagraph()
            val ordered = numbered != null
            if (listOrdered != null && listOrdered != ordered) endList()
            if (listOrdered == null) listOrdered = ordered
            items += parseSpans((bullet ?: numbered)!!.groupValues[1].trim())
            continue
        }
        endList()
        paragraph += line.trim()
    }
    endParagraph(); endList()
    return blocks
}

/** One line (or paragraph) of a primer as spans. */
fun parseSpans(text: String): List<PrimerSpan> {
    val spans = mutableListOf<PrimerSpan>()
    val buffer = StringBuilder()
    var bold = false
    var italic = false
    var italicMark = ' '
    fun push(span: PrimerSpan) { spans += span }
    fun flush() {
        if (buffer.isNotEmpty()) {
            val last = spans.lastOrNull()
            // Text next to text in the same style is one span.
            if (last != null && last.card == null && last.url == null && last.bold == bold && last.italic == italic) {
                spans[spans.size - 1] = last.copy(text = last.text + buffer)
            } else {
                push(PrimerSpan(buffer.toString(), bold, italic))
            }
        }
        buffer.clear()
    }
    var i = 0
    while (i < text.length) {
        val rest = text.substring(i)
        if (rest.startsWith("[[")) {
            val end = text.indexOf("]]", i + 2)
            val name = if (end < 0) "" else text.substring(i + 2, end).trim()
            if (name.isNotEmpty() && name.length <= 150 && name.none { it == '[' || it == ']' || it == '\n' }) {
                flush()
                push(PrimerSpan(name, bold, italic, card = name))
                i = end + 2
                continue
            }
        }
        if (rest.startsWith("[")) {
            val link = LINK.find(rest)
            if (link != null) {
                flush()
                push(PrimerSpan(link.groupValues[1], bold, italic, url = link.groupValues[2]))
                i += link.value.length
                continue
            }
        }
        if ((rest.startsWith("http://") || rest.startsWith("https://")) && (i == 0 || text[i - 1].isWhitespace() || text[i - 1] == '(')) {
            val url = (BARE_URL.find(rest)?.value ?: "").replace(TRAILING, "")
            if (Regex("^https?://.").containsMatchIn(url)) {
                flush()
                push(PrimerSpan(url, bold, italic, url = url))
                i += url.length
                continue
            }
        }
        if (rest.startsWith("**")) {
            if (bold) { flush(); bold = false; i += 2; continue }
            if (text.indexOf("**", i + 2) > i + 2) { flush(); bold = true; i += 2; continue }
            buffer.append("**")
            i += 2
            continue
        }
        val c = text[i]
        if (c == '*' || c == '_') {
            if (italic && italicMark == c) { flush(); italic = false; i += 1; continue }
            val next = text.getOrNull(i + 1)
            val before = if (i > 0) text[i - 1].toString() else ""
            // An underscore inside a word (snake_case) is just an underscore.
            val opens = !italic && next != null && !next.isWhitespace() && text.indexOf(c, i + 1) > i + 1 &&
                !(c == '_' && WORD_CHAR.matches(before))
            if (opens) { flush(); italic = true; italicMark = c; i += 1; continue }
        }
        buffer.append(c)
        i += 1
    }
    flush()
    return spans
}

/** The cards a primer links to, each once, in the order they first appear. */
fun primerCardNames(text: String): List<String> {
    val seen = mutableSetOf<String>()
    val out = mutableListOf<String>()
    fun take(spans: List<PrimerSpan>) {
        spans.forEach { s -> if (s.card != null && seen.add(s.card.lowercase())) out += s.card }
    }
    parsePrimer(text).forEach { b ->
        when (b) {
            is PrimerBlock.Heading -> take(b.spans)
            is PrimerBlock.Paragraph -> take(b.spans)
            is PrimerBlock.Bullets -> b.items.forEach(::take)
        }
    }
    return out
}

/**
 * The primer as comment lines for a decklist ("// …"), which decklist readers — this app's own
 * importer too — skip. Empty when there's no primer.
 */
fun primerComments(text: String?): List<String> {
    val tidy = tidyDescription(text.orEmpty())
    if (tidy.isEmpty()) return emptyList()
    return tidy.split("\n").map { line -> if (line.isNotBlank()) "// ${line.trimEnd()}" else "//" }
}
