package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Folders and the Archived section on the decks list. The web app's deckFolders.test.ts runs the same cases. */
class DeckFoldersTest {
    private fun deck(id: String, folder: String? = null, archived: Boolean? = null) = Deck(id, id, folder = folder, archived = archived)

    @Test
    fun `putting a deck in a folder and taking it out`() {
        val d = deck("a").withFolder("  Modern   decks ")
        assertEquals("Modern decks", d.folder)
        assertEquals("Modern decks", folderOf(d))
        val out = d.withFolder(null)
        assertEquals("", out.folder)
        assertNull(folderOf(out))
        assertNull(deck("b").withFolder("").folder)
        assertEquals(40, tidyFolder("x".repeat(60)).length)
    }

    @Test
    fun `archiving keeps the flag once set`() {
        val a = deck("a").withArchived(true)
        assertEquals(true, a.archived)
        assertEquals(false, a.withArchived(false).archived)
        assertNull(deck("b").withArchived(false).archived)
        assertEquals(listOf("b"), activeDecks(listOf(a, deck("b"))).map { it.id })
    }

    @Test
    fun `the list - folders A to Z, loose decks, archived apart`() {
        val decks = listOf(deck("a", "modern"), deck("b"), deck("c", "Cube"), deck("d", "Modern"), deck("e", "Old", true), deck("f", ""))
        assertEquals(listOf("Cube", "modern"), folderNames(decks))
        val s = deckSections(decks)
        assertEquals(listOf("Cube" to listOf("c"), "modern" to listOf("a", "d")), s.folders.map { it.name to it.decks.map { d -> d.id } })
        assertEquals(listOf("b", "f"), s.loose.map { it.id })
        assertEquals(listOf("e"), s.archived.map { it.id })
    }

    @Test
    fun `renaming and deleting a folder rewrites its decks, archived ones too`() {
        val decks = listOf(deck("a", "Modern"), deck("b", "modern", true), deck("c", "Cube"))
        assertEquals(listOf("Pioneer", "Pioneer", "Cube"), renamedFolder(decks, "MODERN", "Pioneer").map { it.folder })
        assertEquals(listOf("", "", "Cube"), withoutFolder(decks, "Modern").map { it.folder })
    }
}
