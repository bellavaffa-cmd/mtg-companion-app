package com.mtgcompanion.app.data

// Test hands by the thousand: shuffle the deck 10,000 times and count how the opening seven and the
// first turns go — lands in the opener, a land drop each turn, a two-drop to cast on turn 2, how often
// a simple keep rule sends the hand back. Played out, not worked out (HandOdds.kt has the exact sums
// for the opener), so the numbers wobble by a point or so; a seed makes them repeat. Colours aren't
// checked: a land is any land, a two-drop is any non-land with mana value 2.
//
// The random numbers are mulberry32 and the shuffle a partial Fisher–Yates, written the same way as
// the web app's src/decks/handSim.ts, so a seed gives both apps the very same numbers.

const val SIM_HANDS = 10_000
const val SIM_SEED = 7
/** The simple keep rule: a seven with this many lands is kept, any other goes back. */
val SIM_KEEP_LANDS = 2..5
/** Land drops are counted for turns 1 to this. */
const val SIM_TURNS = 4

/** Seeded random numbers from 0 (inclusive) to 1: mulberry32, the same sequence as the web app's. */
class SeededRandom(seed: Int) {
    private var a = seed

    fun next(): Double {
        a += 0x6d2b79f5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}

/** One card in the library, as far as the numbers care. [manaValue] is null for a land or an unknown card. */
data class SimCard(val land: Boolean, val manaValue: Double? = null)

/**
 * The deck's library for the numbers: its main deck less one copy of each commander (it starts in
 * the command zone, so it's never drawn). [typeLine] and [manaValue] say what's known of a card.
 */
fun simLibrary(deck: Deck, typeLine: (DeckCardEntry) -> String?, manaValue: (DeckCardEntry) -> Double?): List<SimCard> {
    val commanders = listOfNotNull(deck.commander, deck.partnerCommander).groupingBy { it.scryfallId }.eachCount()
    return deck.cards.flatMap { e ->
        val copies = (e.quantity - (commanders[e.scryfallId] ?: 0)).coerceAtLeast(0)
        val land = isLandType(typeLine(e) ?: e.typeLine)
        val card = SimCard(land, if (land) null else manaValue(e))
        List(copies) { card }
    }
}

/** On the play and on the draw. */
data class PlayDraw(val onThePlay: Double, val onTheDraw: Double)

data class HandStats(
    val hands: Int,
    val library: Int,
    val lands: Int,
    /** Non-lands with mana value 2. */
    val twoDrops: Int,
    /** Share of openers with exactly 0, 1, … 7 lands. */
    val landsInOpener: List<Double>,
    /** 2 to 4 lands in the opener. */
    val twoToFourLands: Double,
    val averageLands: Double,
    /** Turn 1 to 4 → a land to play every turn so far (n lands among the cards seen by turn n). */
    val landDrops: List<Pair<Int, PlayDraw>>,
    /** Two lands and a two-drop among the cards seen by turn 2. */
    val twoDropOnTurn2: PlayDraw,
    /** Openers the keep rule sends back. */
    val mulliganRate: Double
)

/** Cards seen by [turn]: the seven, plus a draw each turn — but none on turn 1 on the play. */
private fun seenBy(turn: Int, onThePlay: Boolean) = OPENING_HAND + turn - if (onThePlay) 1 else 0

/**
 * The numbers for [library], from [hands] shuffles with [seed]. Null for a library too small for an
 * opener and four draws.
 */
fun handStats(library: List<SimCard>, hands: Int = SIM_HANDS, seed: Int = SIM_SEED): HandStats? {
    val deepest = seenBy(SIM_TURNS, false)
    val n = library.size
    if (n < deepest || hands <= 0) return null
    val random = SeededRandom(seed)
    val cards = library.toTypedArray()
    val landsInOpener = IntArray(OPENING_HAND + 1)
    val play = IntArray(SIM_TURNS)
    val drawn = IntArray(SIM_TURNS)
    val landsSeen = IntArray(deepest)
    val twosSeen = IntArray(deepest)
    var twoDropPlay = 0
    var twoDropDraw = 0
    var landTotal = 0
    var mulligans = 0

    repeat(hands) {
        // Only the top cards matter: shuffle just those into place.
        for (i in 0 until deepest) {
            val j = i + (random.next() * (n - i)).toInt()
            val t = cards[i]
            cards[i] = cards[j]
            cards[j] = t
        }
        // Lands and two-drops among the top 1, 2, … cards.
        var lands = 0
        var twos = 0
        for (i in 0 until deepest) {
            val c = cards[i]
            if (c.land) lands++ else if (c.manaValue == 2.0) twos++
            landsSeen[i] = lands
            twosSeen[i] = twos
        }
        val opener = landsSeen[OPENING_HAND - 1]
        landsInOpener[opener]++
        landTotal += opener
        if (opener !in SIM_KEEP_LANDS) mulligans++
        for (turn in 1..SIM_TURNS) {
            if (landsSeen[seenBy(turn, true) - 1] >= turn) play[turn - 1]++
            if (landsSeen[seenBy(turn, false) - 1] >= turn) drawn[turn - 1]++
        }
        val p2 = seenBy(2, true) - 1
        val d2 = seenBy(2, false) - 1
        if (landsSeen[p2] >= 2 && twosSeen[p2] >= 1) twoDropPlay++
        if (landsSeen[d2] >= 2 && twosSeen[d2] >= 1) twoDropDraw++
    }

    fun share(k: Int) = k.toDouble() / hands
    return HandStats(
        hands = hands,
        library = n,
        lands = library.count { it.land },
        twoDrops = library.count { !it.land && it.manaValue == 2.0 },
        landsInOpener = landsInOpener.map { share(it) },
        twoToFourLands = share(landsInOpener[2] + landsInOpener[3] + landsInOpener[4]),
        averageLands = landTotal.toDouble() / hands,
        landDrops = (1..SIM_TURNS).map { it to PlayDraw(share(play[it - 1]), share(drawn[it - 1])) },
        twoDropOnTurn2 = PlayDraw(share(twoDropPlay), share(twoDropDraw)),
        mulliganRate = share(mulligans)
    )
}
