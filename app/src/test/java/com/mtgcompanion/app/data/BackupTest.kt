package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/**
 * Backup and restore: one file with everything, read back the same; a file from a newer version is
 * turned down; restoring merges with the sync's rules or puts the backup back. The web app has the
 * same checks — see MtgCompanionWeb/tests/sync/backupFile.test.ts.
 */
class BackupTest {
    private fun entry(id: String, quantity: Int = 1, places: List<CopyPlace>? = null) = CollectionEntry(id, id, null, quantity = quantity, places = places)
    private fun card(id: String) = DeckCardEntry(id, id, null)
    private fun binder(id: String, entries: List<CollectionEntry>) = Collection(id, id, entries, createdAt = 1L)
    private fun deck(id: String, cards: List<DeckCardEntry>, name: String = id) = Deck(id, name, cards = cards, createdAt = 1L)
    private fun move(at: Long, name: String) = CopyMove(at, "ADDED", name, title = "Added $name")
    private fun photo(key: String, front: String? = null) = CopyPhoto(key, key, key, front = front)

    private fun reread(b: BackupFile): BackupFile {
        val parsed = parseBackup(backupJson(b))
        assertTrue(parsed.toString(), parsed is ParsedBackup.Ok)
        return (parsed as ParsedBackup.Ok).backup
    }

    @Test fun `a backup reads back exactly as it was written - library, history, photos and settings`() {
        val pile = Collection(
            UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME,
            listOf(entry("bolt", 2, listOf(CopyPlace("box", 2, section = "Red")))), createdAt = 1L,
            storagePlaces = listOf(StoragePlace("box", "Red box", PlaceKind.BOX.name, createdAt = 1L)),
            loans = listOf(Loan("L1", "Sam", cards = listOf(LoanCard("bolt", "bolt", 1)), lentAt = 1L)),
            sealed = listOf(SealedProduct("s1", "Box", SealedKind.PLAY_BOX.name, count = 1, createdAt = 1L)),
            graded = listOf(GradedCard("g1", "bolt", "bolt", company = GradingCompany.PSA.name, grade = "10", createdAt = 1L)),
            gear = listOf(GearItem("k1", GearKind.SLEEVES.name, "Black", 100, createdAt = 1L))
        )
        val d = deck("d1", listOf(card("sol"))).copy(history = listOf(DeckHistoryEntry("h1", 5L, add = listOf(HistoryLine("sol", 1)))), cameFrom = emptyList())
        val b = buildBackup(
            listOf(d), listOf(pile), createdAt = 1_790_000_000_000L, copyHistory = listOf(move(1, "bolt")), photos = listOf(photo("bolt|x", "p1.jpg")),
            photoAskOver = 20.0, photoFiles = mapOf("p1.jpg" to "AAEC"), settings = mapOf("currency" to "EUR")
        )
        assertEquals(b, reread(b))
        assertEquals(BACKUP_VERSION, reread(b).version)
        assertEquals(mapOf("android" to mapOf("currency" to "EUR")), reread(b).settings)
    }

    @Test fun `a 25,000-copy collection survives the round trip, and restores into an empty library as it was`() {
        val lib = BigCollection.build()
        val b = reread(buildBackup(lib.decks, lib.collections, createdAt = 1L))
        assertEquals(lib.decks, b.decks)
        assertEquals(lib.collections, b.collections)
        assertEquals(25_000, backupSummary(b).copies)
        for (mode in RestoreMode.entries) {
            assertEquals(lib.decks, restoreDecks(emptyList(), b, mode))
            assertEquals(lib.collections, restoreCollections(emptyList(), b, mode))
        }
        // Merging a library with its own backup changes nothing — not one item, so nothing syncs.
        val same = restoreCollections(lib.collections, b, RestoreMode.MERGE)
        assertTrue(same.indices.all { same[it] === lib.collections[it] })
    }

    @Test fun `a backup from a newer version is turned down, and so is a file that is not a backup`() {
        val newer = backupJson(buildBackup(emptyList(), emptyList(), createdAt = 1L)).replace("\"version\":1", "\"version\":2")
        assertEquals(ParsedBackup.Refused("too-new", BACKUP_TOO_NEW), parseBackup(newer))
        assertEquals(ParsedBackup.Refused("not-backup", NOT_A_BACKUP), parseBackup("""{"decks": []}"""))
        assertEquals(ParsedBackup.Refused("not-backup", NOT_A_BACKUP), parseBackup("Sol Ring\n1 Lightning Bolt"))
        val cut = backupJson(buildBackup(listOf(deck("d", listOf(card("a")))), emptyList(), createdAt = 1L))
        assertEquals("damaged", (parseBackup(cut.dropLast(20)) as ParsedBackup.Refused).reason)
    }

    @Test fun `a backup made on the web reads here`() {
        val web = """{"format":"manabind-backup","version":1,"createdAt":5,"from":"web",
            "decks":[{"id":"d","name":"Krenko","commander":null,"partnerCommander":null,"cards":[{"scryfallId":"a","name":"A","imageUrl":null,"quantity":1,"canBeCommander":false,"typeLine":null,"partnerAbility":null}],"gameMode":"COMMANDER","createdAt":1,"tags":[],"gameResults":[],"ownership":"PHYSICAL"}],
            "collections":[{"id":"b","name":"Trade","entries":[{"scryfallId":"x","name":"X","imageUrl":null,"quantity":2,"foilQuantity":0}],"createdAt":1,"type":"OWNED"}],
            "copyHistory":[],"photos":[{"key":"x|","scryfallId":"x","name":"X","front":"copy_photo:1"}],"photoFiles":{"copy_photo:1":"AAEC"},
            "settings":{"web":{"mtgweb_currency":"\"EUR\""}}}"""
        val b = (parseBackup(web) as ParsedBackup.Ok).backup
        assertEquals("web", b.from)
        assertEquals(listOf("a"), b.decks.single().cards.map { it.scryfallId })
        assertEquals(2, b.collections.single().entries.single().quantity)
        // The web's picture ids aren't file names here.
        val photos = restoredPhotos(emptyList(), b, RestoreMode.MERGE, ::photoFileName)
        assertEquals("copy_photo_1.jpg", photos.photos.single().front)
    }

    @Test fun `merging keeps everything newer here and brings back what is only in the backup`() {
        val backup = reread(
            buildBackup(
                listOf(deck("kept", listOf(card("sol"), card("ring")), name = "Old name"), deck("gone", listOf(card("bolt")))),
                listOf(binder("b1", listOf(entry("a", 2), entry("b", 1), entry("c", 1)))),
                createdAt = 1L
            )
        )
        // Since the backup: renamed a deck, added a card to it, deleted a deck, sold a card, bought more
        // of another and made a new binder.
        val decks = restoreDecks(listOf(deck("kept", listOf(card("sol"), card("ring"), card("opt")), name = "New name")), backup, RestoreMode.MERGE)
        val kept = decks.first { it.id == "kept" }
        assertEquals("New name", kept.name)
        assertEquals(listOf("sol", "ring", "opt"), kept.cards.map { it.scryfallId })
        assertTrue("the deleted deck comes back", decks.any { it.id == "gone" })
        val binders = restoreCollections(listOf(binder("b1", listOf(entry("a", 5), entry("c", 1), entry("d", 1))), binder("b2", listOf(entry("x")))), backup, RestoreMode.MERGE)
        // Here's order first, then what only the backup has; the larger count where both have a card.
        assertEquals(listOf("a" to 5, "c" to 1, "d" to 1, "b" to 1), binders.first { it.id == "b1" }.entries.map { it.scryfallId to it.quantity })
        assertTrue("a binder made since stays", binders.any { it.id == "b2" })
    }

    @Test fun `merging keeps where copies are here, and adds the backup places and loans that are missing`() {
        fun place(id: String) = StoragePlace(id, id, PlaceKind.BOX.name, createdAt = 1L)
        val then = Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, listOf(entry("a", 1, listOf(CopyPlace("old", 1)))), createdAt = 1L,
            storagePlaces = listOf(place("old")), loans = listOf(Loan("L1", "Sam", lentAt = 1L)))
        val now = Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, listOf(entry("a", 1, listOf(CopyPlace("new", 1)))), createdAt = 1L,
            storagePlaces = listOf(place("old"), place("new")), loans = listOf(Loan("L2", "Jo", lentAt = 2L)))
        val pile = restoreCollections(listOf(now), reread(buildBackup(emptyList(), listOf(then), createdAt = 1L)), RestoreMode.MERGE).single()
        assertEquals(listOf(CopyPlace("new", 1)), pile.entries.single().places)
        assertEquals(listOf("new", "old"), pile.storagePlaces!!.map { it.id }.sorted())
        assertEquals(listOf("L1", "L2"), pile.loans!!.map { it.id }.sorted())
    }

    @Test fun `replacing puts each deck and binder back as it was in the backup, keeping ones made since`() {
        val backup = reread(buildBackup(listOf(deck("d", listOf(card("sol")), name = "Then")), listOf(binder("b", listOf(entry("a", 1)))), createdAt = 1L))
        val decks = restoreDecks(listOf(deck("d", listOf(card("opt")), name = "Now"), deck("new", emptyList())), backup, RestoreMode.REPLACE)
        assertEquals(listOf("d" to "Then", "new" to "new"), decks.map { it.id to it.name })
        assertEquals(1, restoreCollections(listOf(binder("b", listOf(entry("a", 4)))), backup, RestoreMode.REPLACE).single().entries.single().quantity)
    }

    @Test fun `copy history is both logs, each move once - photos merge by copy`() {
        assertEquals(listOf("a", "b", "c"), restoredHistory(listOf(move(1, "a"), move(3, "c")), listOf(move(2, "b"), move(1, "a")), 4).map { it.name })
        val backup = reread(buildBackup(emptyList(), emptyList(), createdAt = 1L,
            photos = listOf(photo("k1", "f1"), photo("k2", "f2"), photo("k3", "lost")), photoFiles = mapOf("f1" to "AA", "f2" to "BB")))
        val merged = restoredPhotos(listOf(photo("k1", "mine")), backup, RestoreMode.MERGE)
        assertEquals(listOf("k1" to "mine", "k2" to "f2", "k3" to null), merged.photos.map { it.key to it.front })
        assertEquals(listOf("k2", "k3"), merged.fromBackup.map { it.key })
        val replaced = restoredPhotos(listOf(photo("k1", "mine")), backup, RestoreMode.REPLACE)
        assertEquals(listOf("k1" to "f1", "k2" to "f2", "k3" to null), replaced.photos.map { it.key to it.front })
    }

    @Test fun `the preview says what the file holds, and the file is named for its day`() {
        val lib = BigCollection.build()
        val b = reread(buildBackup(lib.decks, lib.collections, createdAt = 1L, photoFiles = mapOf("p" to "AA")))
        assertEquals(
            listOf(
                "60 decks · 42 binders",
                "25,000 copies in 80 places",
                "25 loans · 60 sealed · 40 graded · 25 pieces of gear",
                "720 deck history entries · 1 photo"
            ),
            summaryLines(backupSummary(b))
        )
        assertEquals("Restored 60 decks and 42 binders, with 1 photo.", restoredMessage(backupSummary(b), 1))
        assertEquals("manabind-backup-2026-10-07.json", backupFileName(1_791_374_400_000L, ZoneOffset.UTC))
    }
}
