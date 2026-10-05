package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** A deck's primer: its blocks, its spans and links, and what an export makes of it. The web app's primer.test.ts runs the same cases. */
class PrimerTest {
    private fun t(text: String, bold: Boolean = false, italic: Boolean = false, card: String? = null, url: String? = null) =
        PrimerSpan(text, bold, italic, card, url)

    @Test
    fun `headings, lists and paragraphs`() {
        val blocks = parsePrimer("# Plan\nRamp early.\nThen win.\n\n- [[Sol Ring]]\n- Arcane Signet\n1. Mulligan\n2) Keep\n\n### Notes\nDone")
        assertEquals(
            listOf(
                PrimerBlock.Heading(1, listOf(t("Plan"))),
                PrimerBlock.Paragraph(listOf(t("Ramp early.\nThen win."))),
                PrimerBlock.Bullets(false, listOf(listOf(t("Sol Ring", card = "Sol Ring")), listOf(t("Arcane Signet")))),
                PrimerBlock.Bullets(true, listOf(listOf(t("Mulligan")), listOf(t("Keep")))),
                PrimerBlock.Heading(3, listOf(t("Notes"))),
                PrimerBlock.Paragraph(listOf(t("Done")))
            ),
            blocks
        )
    }

    @Test
    fun `bold, italic, card links and web links`() {
        assertEquals(
            listOf(
                t("Cast "),
                t("Craterhoof Behemoth", bold = true, card = "Craterhoof Behemoth"),
                t(" last", bold = true),
                t(" and "),
                t("win", italic = true)
            ),
            parseSpans("Cast **[[Craterhoof Behemoth]] last** and *win*")
        )
        assertEquals(
            listOf(
                t("See "),
                t("the guide", url = "https://example.com/a"),
                t(" or "),
                t("https://edhrec.com/x", url = "https://edhrec.com/x"),
                t(".")
            ),
            parseSpans("See [the guide](https://example.com/a) or https://edhrec.com/x.")
        )
    }

    @Test
    fun `markers without a partner, and anything that is not a web address, stay text`() {
        assertEquals(listOf(t("2 * 3 = 6, snake_case_name, **open")), parseSpans("2 * 3 = 6, snake_case_name, **open"))
        assertEquals(listOf(t("[click](javascript:alert(1)) <b>hi</b>")), parseSpans("[click](javascript:alert(1)) <b>hi</b>"))
        assertEquals(listOf(t("[[ ]] and [[unclosed")), parseSpans("[[ ]] and [[unclosed"))
        assertEquals(listOf(t("quiet", italic = true), t(" words")), parseSpans("_quiet_ words"))
    }

    @Test
    fun `the cards a primer names, each once`() {
        assertEquals(listOf("Sol Ring", "Mana Crypt"), primerCardNames("[[Sol Ring]] then [[sol ring]]\n- [[Mana Crypt]]"))
    }

    @Test
    fun `kept tidy and capped`() {
        assertEquals("a\nb", tidyDescription("  a\r\nb \r\n"))
        assertEquals(MAX_DESCRIPTION, tidyDescription("x".repeat(MAX_DESCRIPTION + 10)).length)
    }

    @Test
    fun `as comments for an export`() {
        assertEquals(listOf("// # Plan", "//", "// Go wide"), primerComments("# Plan\n\nGo wide"))
        assertEquals(emptyList<String>(), primerComments(null))
        assertEquals(emptyList<String>(), primerComments("  "))
    }
}
