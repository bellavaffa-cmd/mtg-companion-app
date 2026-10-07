package com.mtgcompanion.app.data

/**
 * A synthetic big collection for tests and benchmarks — never shipped, never user data. Exactly
 * [COPIES] owned copies (binders and the real cards in decks), over thousands of distinct printings,
 * in many storage places, with decks, loans, sealed product, graded copies, gear, deck history and
 * tags. Deterministic, and the same library as the web app's tests/perf/bigCollection.ts, random draw
 * for random draw, so timings compare run to run and app to app.
 */
object BigCollection {
    const val COPIES = 25_000
    const val PRINTINGS = 18_000
    private const val NAMES = 11_000
    private const val BINDERS = 40
    private const val DECKS = 60
    private const val PHYSICAL_DECKS = 45
    private const val DECK_SIZE = 99
    private const val T0 = 1_780_000_000_000L

    /** A small, seedable random source (mulberry32, as the web app's), so runs make the same library. */
    fun random(seed: Int): () -> Double {
        var a = seed
        return {
            a += 0x6d2b79f5
            var t = a
            t = (t xor (t ushr 15)) * (t or 1)
            t = t xor (t + (t xor (t ushr 7)) * (t or 61))
            ((t xor (t ushr 14)).toLong() and 0xffffffffL).toDouble() / 4294967296.0
        }
    }

    private val SYLLABLES = listOf("ka", "zor", "mi", "thal", "ven", "dru", "syl", "ar", "gol", "eth", "ri", "mon", "qua", "bel", "tor", "ny")
    private val WORDS = listOf("Bolt", "Ring", "Angel", "Dragon", "Growth", "Counsel", "Tutor", "Wall", "Elf", "Sphinx", "Golem", "Rite", "Gate", "Oath", "Storm")
    private val TYPES = listOf("Creature — Elf", "Instant", "Sorcery", "Artifact", "Enchantment", "Legendary Creature — Dragon", "Land", "Planeswalker — Jace")
    private val SETS = (0 until 60).map { "s" + it.toString().padStart(2, '0') }

    fun printingId(i: Int): String = "p-${i.toString().padStart(5, '0')}-${((i.toLong() * 2654435761L) and 0xffffffffL).toString(16)}"

    fun cardName(n: Int): String {
        val a = SYLLABLES[n % 16]
        val b = SYLLABLES[(n shr 4) % 16]
        val c = SYLLABLES[(n shr 8) % 16]
        return "${a[0].uppercaseChar()}${a.drop(1)}$b$c ${WORDS[n % 15]}"
    }

    private fun nameOf(i: Int) = cardName(i % NAMES)

    private fun deckEntry(i: Int) = DeckCardEntry(
        scryfallId = printingId(i), name = nameOf(i), imageUrl = "https://img.example/$i.jpg", quantity = 1,
        canBeCommander = i % 50 == 0, typeLine = TYPES[i % TYPES.size], tags = if (i % 7 == 0) listOf("ramp") else emptyList()
    )

    data class Library(val decks: List<Deck>, val collections: List<Collection>)

    /** The big collection. */
    fun build(seed: Int = 8): Library {
        val rnd = random(seed)
        fun <T> pick(list: List<T>): T = list[(rnd() * list.size).toInt()]

        // Places: shelves holding boxes (with sections) and binders, and a few deck boxes on their own.
        val places = mutableListOf<StoragePlace>()
        for (s in 0 until 8) {
            val shelf = "shelf-$s"
            places += StoragePlace(shelf, "Shelf ${s + 1}", PlaceKind.SHELF.name, createdAt = T0 + s)
            for (b in 0 until 6) places += StoragePlace(
                "box-$s-$b", "Box ${s + 1}.${b + 1}", PlaceKind.BOX.name, parentId = shelf,
                sections = listOf("White", "Blue", "Black", "Red", "Green", "Multi"), sortRule = SortRule.COLOUR.name, capacity = 800,
                createdAt = T0 + 100 + s * 10 + b, lastChecked = T0 + s * 86_400_000L
            )
            for (b in 0 until 2) places += StoragePlace(
                "binder-$s-$b", "Binder ${s + 1}.${b + 1}", PlaceKind.BINDER.name, parentId = shelf, pocketsPerPage = 9, pages = 40,
                sortRule = SortRule.NAME.name, createdAt = T0 + 200 + s * 10 + b
            )
        }
        for (d in 0 until 8) places += StoragePlace("deckbox-$d", "Deck box ${d + 1}", PlaceKind.DECK_BOX.name, createdAt = T0 + 300 + d)
        val boxes = places.filter { it.kind == PlaceKind.BOX.name }
        val binderPlaces = places.filter { it.kind == PlaceKind.BINDER.name }
        val pockets = HashMap<String, Int>()
        fun spot(qty: Int, foil: Boolean): CopyPlace {
            if (rnd() < 0.8) {
                val box = pick(boxes)
                return CopyPlace(box.id, qty, if (foil) true else null, section = pick(box.sections!!))
            }
            val binder = pick(binderPlaces)
            val n = pockets[binder.id] ?: 0
            pockets[binder.id] = n + 1
            return CopyPlace(binder.id, qty, if (foil) true else null, page = 1 + n / 9, slot = 1 + n % 9)
        }

        // Decks first: the physical ones' cards count toward the copies.
        val decks = mutableListOf<Deck>()
        var deckCopies = 0
        for (d in 0 until DECKS) {
            val ownership = when {
                d < PHYSICAL_DECKS -> DeckOwnership.PHYSICAL.name
                d < PHYSICAL_DECKS + 5 -> DeckOwnership.PROXY.name
                else -> DeckOwnership.VIRTUAL.name
            }
            val cards = mutableListOf<DeckCardEntry>()
            val seen = HashSet<Int>()
            while (cards.size < DECK_SIZE) {
                val i = (rnd() * PRINTINGS).toInt()
                if (!seen.add(i)) continue
                cards += deckEntry(i)
            }
            if (ownership == DeckOwnership.PHYSICAL.name) deckCopies += cards.size
            val history = (0 until 12).map { h ->
                DeckHistoryEntry(
                    id = "h-$d-$h", at = T0 + h * 3_600_000L, from = if (h % 2 == 1) "web" else "android", dev = "dev-${h % 3}",
                    add = listOf(HistoryLine(cards[h].name, 1)), cut = if (h > 0) listOf(HistoryLine(cards[h - 1].name, 1)) else emptyList(),
                    kind = if (h == 0) "start" else null,
                    list = if (h == 0) cards.take(20).associate { it.name to 1 } else null
                )
            }
            decks += Deck(
                id = "deck-$d", name = "Deck ${d + 1}", commander = deckEntry(d * 50), cards = cards, gameMode = GameMode.COMMANDER.name,
                createdAt = T0 + d, tags = if (d % 3 == 0) listOf("cEDH") else emptyList(), ownership = ownership,
                gameResults = (0 until 5).map { g -> GameResult("g-$d-$g", if (g % 2 == 1) "WIN" else "LOSS", "Sam", T0 + g) },
                history = history, folder = if (d % 4 == 0) "Commander" else ""
            )
        }

        // Binders: every printing once somewhere, then extra copies until the total is [COPIES].
        val homes = (0..BINDERS).map { mutableListOf<CollectionEntry>() } // 0: the Unsorted pile
        val all = ArrayList<IntArray>() // home, index — entries are rebuilt as copies are added
        for (i in 0 until PRINTINGS) {
            val home = if (i % 3 == 0) 0 else 1 + (i % BINDERS)
            homes[home] += CollectionEntry(
                scryfallId = printingId(i), name = nameOf(i), imageUrl = "https://img.example/$i.jpg", quantity = 1,
                condition = if (i % 11 == 0) "NM" else null, language = if (i % 23 == 0) "ja" else null,
                userTags = if (i % 45 == 0) listOf("signed") else emptyList(), forSale = if (i % 97 == 0) 1 else null
            )
            all += intArrayOf(home, homes[home].size - 1)
        }
        var copies = deckCopies + PRINTINGS
        while (copies < COPIES) {
            val (home, at) = pick(all).let { it[0] to it[1] }
            val e = homes[home][at]
            homes[home][at] = if (rnd() < 0.25) e.copy(foilQuantity = e.foilQuantity + 1) else e.copy(quantity = e.quantity + 1)
            copies++
        }
        // Most copies have a place.
        for (ref in all) {
            if (rnd() < 0.3) continue
            val e = homes[ref[0]][ref[1]]
            val out = mutableListOf<CopyPlace>()
            if (e.quantity > 0) out += spot(e.quantity, false)
            if (e.foilQuantity > 0) out += spot(e.foilQuantity, true)
            homes[ref[0]][ref[1]] = e.copy(places = out)
        }

        val pile = homes[0]
        val loans = (0 until 25).map { l ->
            Loan(
                "loan-$l", listOf("Sam", "Alex", "Robin", "Jo")[l % 4], lentAt = T0 + l * 86_400_000L, backBy = if (l % 2 == 1) "2026-11-01" else null,
                cards = (0 until 4).map { k ->
                    val e = pile[(l * 4 + k) * 7]
                    LoanCard(e.name, e.scryfallId, 1, collectionId = UNSORTED_COLLECTION_ID, back = if (l % 5 == 0) 1 else null)
                }
            )
        }
        val sealed = (0 until 30).map { s ->
            SealedProduct(
                "sealed-$s", "Sealed ${s + 1}", if (s % 5 == 0) SealedKind.PRECON.name else SealedKind.PLAY_BOX.name, setCode = SETS[s],
                count = 1 + s % 3, placeId = "shelf-${s % 8}", paidUsd = 100.0 + s, valueUsd = 120.0 + s, createdAt = T0 + s
            )
        }
        val graded = (0 until 40).map { g ->
            GradedCard("graded-$g", printingId(g * 13), nameOf(g * 13), company = GradingCompany.PSA.name, grade = (7 + g % 4).toString(), valueUsd = 50.0 + g, placeId = "deckbox-0", createdAt = T0 + g)
        }
        val gear = (0 until 25).map { g ->
            GearItem(
                "gear-$g", if (g % 2 == 1) GearKind.SLEEVES.name else GearKind.DECK_BOX.name, "Gear ${g + 1}", if (g % 2 == 1) 100 else 1,
                usedBy = if (g % 2 == 1) listOf("deck-$g") else null, holds = if (g % 2 == 1) null else "deck-$g", createdAt = T0 + g
            )
        }
        val unsorted = Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, pile.toList(), T0, CollectionType.OWNED.name, storagePlaces = places, loans = loans, sealed = sealed, graded = graded, gear = gear)
        val wishlist = Collection(
            WISHLIST_ID, "Wishlist",
            (0 until 200).map { w -> CollectionEntry("wish-$w", cardName(NAMES + w), null, quantity = 1, priceAlert = if (w % 4 == 0) 5.0 else null) },
            T0, CollectionType.WISHLIST.name
        )
        val binders = (0 until BINDERS).map { b -> Collection("binder-col-$b", "Binder ${b + 1}", homes[b + 1].toList(), T0 + b, CollectionType.OWNED.name) }
        return Library(decks, listOf(unsorted, wishlist) + binders)
    }

    /** Owned copies: binders (not the Wishlist) and the real cards in physical decks. */
    fun ownedCopies(lib: Library): Int =
        lib.collections.filter { it.kind != CollectionType.WISHLIST }.sumOf { c -> c.entries.sumOf { it.quantity + it.foilQuantity } } +
            lib.decks.filter { it.ownership == DeckOwnership.PHYSICAL.name }.sumOf { d -> d.cards.sumOf { it.quantity } }

    /** Milliseconds [fn] takes, the best of [runs] (the first run warms up). */
    fun <T> timed(runs: Int = 3, fn: () -> T): Pair<Long, T> {
        var best = Long.MAX_VALUE
        var value: T? = null
        repeat(runs) {
            val t = System.nanoTime()
            value = fn()
            best = minOf(best, (System.nanoTime() - t) / 1_000_000)
        }
        @Suppress("UNCHECKED_CAST")
        return best to (value as T)
    }
}
