package com.mtgcompanion.app.data

import kotlin.random.Random

// Solo playtesting ("goldfishing") a deck: shuffle, draw seven, mulligan the London way, then play
// turns — draw, put cards onto the battlefield, tap and untap them, send them to the graveyard, make
// the deck's tokens. Plain state and actions, so the rules are tested here rather than on a screen;
// every shuffle takes a Random, so a test can seed it. OPENING_HAND (seven) is HandOdds.kt's. Nothing is saved: closing the playtest ends it.

/** One physical card in the game. [id] tells copies of the same card apart. */
data class PlayCard(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val backImageUrl: String? = null,
    val typeLine: String? = null,
    /** A token made during the game: it ceases to exist when it leaves the battlefield. */
    val isToken: Boolean = false
)

/** A card on the battlefield, and whether it's tapped. */
data class Permanent(val card: PlayCard, val tapped: Boolean = false)

data class PlaytestState(
    val library: List<PlayCard>,
    val hand: List<PlayCard> = emptyList(),
    val battlefield: List<Permanent> = emptyList(),
    val graveyard: List<PlayCard> = emptyList(),
    /** Commanders, castable from here at any time. */
    val commandZone: List<PlayCard> = emptyList(),
    /** 0 while choosing an opening hand; 1 and up once it's kept. */
    val turn: Int = 0,
    /** On the play there's no draw on turn 1. */
    val onThePlay: Boolean = true,
    val mulligans: Int = 0,
    /** Cards still to put on the bottom of the library before the hand can be kept (London mulligan). */
    val toBottom: Int = 0,
    /** Commander's house rule: the first mulligan puts no card on the bottom. */
    val freeMulligan: Boolean = false,
    /** Tokens made so far, for their ids. */
    val tokensMade: Int = 0
) {
    val choosingHand: Boolean get() = turn == 0
    val canKeep: Boolean get() = choosingHand && toBottom == 0
}

/**
 * Every card of [deck]'s main deck as a game card — less one copy of each commander, which starts in
 * the command zone. The sideboard and the Considering list aren't played.
 */
fun playCards(deck: Deck): Pair<List<PlayCard>, List<PlayCard>> {
    val commanders = listOfNotNull(deck.commander, deck.partnerCommander)
    val commanderCopies = commanders.groupingBy { it.scryfallId }.eachCount()
    val library = deck.cards.flatMap { entry ->
        (0 until (entry.quantity - (commanderCopies[entry.scryfallId] ?: 0)).coerceAtLeast(0)).map { i ->
            PlayCard("${entry.scryfallId}#$i", entry.name, entry.imageUrl, entry.backImageUrl, entry.typeLine)
        }
    }
    val zone = commanders.map { PlayCard("${it.scryfallId}#cmd", it.name, it.imageUrl, it.backImageUrl, it.typeLine) }
    return library to zone
}

/**
 * A new game: [library] shuffled and seven drawn. [freeMulligan] is the Commander rule that the
 * first mulligan is free.
 */
fun newGame(
    library: List<PlayCard>,
    commandZone: List<PlayCard> = emptyList(),
    random: Random = Random.Default,
    onThePlay: Boolean = true,
    freeMulligan: Boolean = false
): PlaytestState {
    val shuffled = library.shuffled(random)
    return PlaytestState(
        library = shuffled.drop(OPENING_HAND),
        hand = shuffled.take(OPENING_HAND),
        commandZone = commandZone,
        onThePlay = onThePlay,
        freeMulligan = freeMulligan
    )
}

/** Every card back from hand, battlefield and graveyard (tokens gone), and a fresh game. */
fun PlaytestState.reset(random: Random = Random.Default): PlaytestState {
    val all = (library + hand + battlefield.map { it.card } + graveyard).filterNot { it.isToken }
    val commanders = all.filter { it.id.endsWith("#cmd") } + commandZone
    return newGame(all.filterNot { it.id.endsWith("#cmd") }, commanders.distinctBy { it.id }.sortedBy { it.id }, random, onThePlay, freeMulligan)
}

/** How many cards a hand after [mulligans] mulligans puts on the bottom. */
fun cardsToBottom(mulligans: Int, freeMulligan: Boolean): Int =
    (mulligans - if (freeMulligan) 1 else 0).coerceAtLeast(0)

/**
 * London mulligan: the hand goes back, the library is shuffled and seven are drawn again; then one
 * card per mulligan taken goes on the bottom ([putOnBottom]) before the hand can be kept. Only while
 * choosing an opening hand.
 */
fun PlaytestState.mulligan(random: Random = Random.Default): PlaytestState {
    if (!choosingHand) return this
    val shuffled = (library + hand).shuffled(random)
    val taken = mulligans + 1
    return copy(
        library = shuffled.drop(OPENING_HAND),
        hand = shuffled.take(OPENING_HAND),
        mulligans = taken,
        toBottom = cardsToBottom(taken, freeMulligan).coerceAtMost(OPENING_HAND)
    )
}

/** Puts a hand card on the bottom of the library, while the mulligan still asks for one. */
fun PlaytestState.putOnBottom(cardId: String): PlaytestState {
    if (toBottom <= 0) return this
    val card = hand.firstOrNull { it.id == cardId } ?: return this
    return copy(hand = hand - card, library = library + card, toBottom = toBottom - 1)
}

/** On the play or on the draw — chosen before the hand is kept. */
fun PlaytestState.withOnThePlay(play: Boolean): PlaytestState = if (choosingHand) copy(onThePlay = play) else this

/** Whether the first mulligan is free — chosen before any mulligan is taken. */
fun PlaytestState.withFreeMulligan(free: Boolean): PlaytestState =
    if (choosingHand) copy(freeMulligan = free, toBottom = cardsToBottom(mulligans, free).coerceAtMost(OPENING_HAND)) else this

/** Keeps the hand and starts turn 1, drawing for it when on the draw. */
fun PlaytestState.keep(): PlaytestState {
    if (!canKeep) return this
    val started = copy(turn = 1)
    return if (onThePlay) started else started.draw()
}

/** Draws the top card, if there is one. */
fun PlaytestState.draw(): PlaytestState {
    val top = library.firstOrNull() ?: return this
    return copy(library = library.drop(1), hand = hand + top)
}

/** The next turn: untap everything, then draw. */
fun PlaytestState.nextTurn(): PlaytestState {
    if (choosingHand) return this
    return copy(turn = turn + 1, battlefield = battlefield.map { it.copy(tapped = false) }).draw()
}

/** A card from the hand or the command zone onto the battlefield — a land or a spell alike. */
fun PlaytestState.play(cardId: String): PlaytestState {
    hand.firstOrNull { it.id == cardId }?.let { return copy(hand = hand - it, battlefield = battlefield + Permanent(it)) }
    commandZone.firstOrNull { it.id == cardId }?.let { return copy(commandZone = commandZone - it, battlefield = battlefield + Permanent(it)) }
    return this
}

/** Taps an untapped permanent, untaps a tapped one. */
fun PlaytestState.toggleTap(cardId: String): PlaytestState =
    copy(battlefield = battlefield.map { if (it.card.id == cardId) it.copy(tapped = !it.tapped) else it })

/**
 * A card from the hand or the battlefield to where it goes when it dies: a commander back to the
 * command zone, a token nowhere, anything else the graveyard.
 */
fun PlaytestState.toGraveyard(cardId: String): PlaytestState {
    val card = hand.firstOrNull { it.id == cardId }
        ?: battlefield.firstOrNull { it.card.id == cardId }?.card
        ?: return this
    val left = copy(hand = hand.filterNot { it.id == cardId }, battlefield = battlefield.filterNot { it.card.id == cardId })
    return when {
        card.isToken -> left
        card.id.endsWith("#cmd") -> left.copy(commandZone = left.commandZone + card)
        else -> left.copy(graveyard = left.graveyard + card)
    }
}

/** A permanent back to its owner's hand. A token just goes. */
fun PlaytestState.toHand(cardId: String): PlaytestState {
    val permanent = battlefield.firstOrNull { it.card.id == cardId } ?: return this
    val left = copy(battlefield = battlefield - permanent)
    return if (permanent.card.isToken) left else left.copy(hand = left.hand + permanent.card)
}

/** One of the deck's tokens onto the battlefield, untapped. */
fun PlaytestState.createToken(name: String, imageUrl: String?, typeLine: String? = null): PlaytestState {
    val made = tokensMade + 1
    val token = PlayCard("token#$made", name, imageUrl, typeLine = typeLine, isToken = true)
    return copy(battlefield = battlefield + Permanent(token), tokensMade = made)
}
