package com.mtgcompanion.app.data

// Filing decks: folders on the decks list, and an Archived section for decks put away — kept, but out
// of the list and of every deck picker (game night, Add to…, the life counter's decks). A folder is
// only a name on its decks (Deck.folder), not a thing of its own: it exists while a deck is in it,
// renaming or deleting it rewrites its decks, and it syncs with them — so there's nothing new to merge
// and nothing an older app can lose but the name, which it keeps (see DeckExtras.kt). Pure, so it can
// be tested; the web app's src/decks/deckFolders.ts files decks the same way.

/** The longest folder name kept. */
const val MAX_FOLDER = 40

private fun folderKey(name: String) = name.lowercase()

/** A folder name as it's kept: spaces tidied, at most MAX_FOLDER characters. */
fun tidyFolder(name: String): String = name.replace(Regex("\\s+"), " ").trim().take(MAX_FOLDER).trim()

/** The folder [deck] is in; null for none. */
fun folderOf(deck: Deck): String? = deck.folder?.let(::tidyFolder)?.takeIf { it.isNotEmpty() }

val Deck.isArchived: Boolean get() = archived == true

/** The decks a picker offers: every one not archived. */
fun activeDecks(decks: List<Deck>): List<Deck> = decks.filterNot { it.isArchived }

/** This deck in [folder]; null or "" takes it out (kept as "" once it's had one — see Deck.folder). */
fun Deck.withFolder(folder: String?): Deck {
    val next = tidyFolder(folder.orEmpty())
    if (next.isEmpty() && this.folder == null) return this
    return copy(folder = next)
}

/** This deck archived, or back on the list (kept as false once it's been archived). */
fun Deck.withArchived(archived: Boolean): Deck {
    if (!archived && this.archived == null) return this
    return copy(archived = archived)
}

/** The folders on the decks list, A–Z: each once, whatever its case, as its first deck spells it. */
fun folderNames(decks: List<Deck>): List<String> {
    val out = mutableListOf<String>()
    activeDecks(decks).forEach { d ->
        val f = folderOf(d)
        if (f != null && out.none { folderKey(it) == folderKey(f) }) out += f
    }
    return out.sortedWith { a, b -> folderKey(a).compareTo(folderKey(b)) }
}

/** Every deck in folder [from] — archived ones too — moved to [to]; an empty [to] takes them out of it. */
fun renamedFolder(decks: List<Deck>, from: String, to: String): List<Deck> =
    decks.map { d -> if (folderOf(d)?.let { folderKey(it) == folderKey(tidyFolder(from)) } == true) d.withFolder(to) else d }

/** Folder [name] deleted: its decks stay, out of any folder. */
fun withoutFolder(decks: List<Deck>, name: String): List<Deck> = renamedFolder(decks, name, "")

data class DeckFolder(val name: String, val decks: List<Deck>)

data class DeckSections(
    /** Each folder with its decks, A–Z. */
    val folders: List<DeckFolder>,
    /** The decks in no folder. */
    val loose: List<Deck>,
    /** Every archived deck, folder or not. */
    val archived: List<Deck>
)

/** [decks] as the list shows them: folders first, then the rest, and archived decks apart. Order within each is kept. */
fun deckSections(decks: List<Deck>): DeckSections {
    val active = activeDecks(decks)
    return DeckSections(
        folders = folderNames(decks).map { name -> DeckFolder(name, active.filter { d -> folderOf(d)?.let { folderKey(it) == folderKey(name) } == true }) },
        loose = active.filter { folderOf(it) == null },
        archived = decks.filter { it.isArchived }
    )
}
