package com.mtgcompanion.app.ui.lifecounter

// The counters with rules of their own: rad (no limit), speed (0 to 4) and the Ring tempting you
// (levels 1 to 4, each adding an ability to your Ring-bearer, who has a name). Pure. Mirrors the
// web app's src/lifecounter/counterRules.ts.
//
// Speed (Aetherdrift): it starts at 0 and goes up by 1 at most once each turn, when an opponent loses
// life during your turn; it never goes above 4, and 4 is "max speed". The app keeps it as a counter
// that stops at 4 — the once-a-turn rule is the players' to keep.

/** The highest a counter can go, by wire name, for those that stop somewhere. */
val COUNTER_MAX: Map<String, Int> = mapOf("speed" to 4, "ring" to 4)

/** [value] kept within what counter [kind] (a wire name) allows: never below 0, and at most its maximum. */
fun clampCounter(kind: String, value: Int): Int {
    val max = COUNTER_MAX[kind]
    return (if (max == null) value else minOf(max, value)).coerceAtLeast(0)
}

const val MAX_SPEED = 4
fun isMaxSpeed(speed: Int): Boolean = speed >= MAX_SPEED

/** What the Ring gives its bearer at each level, in order: level n has the first n. */
val RING_ABILITIES = listOf(
    "Legendary, and can't be blocked by creatures with greater power.",
    "Whenever it attacks, draw a card, then discard a card.",
    "Whenever it becomes blocked by a creature, that creature's controller sacrifices it at end of combat.",
    "Whenever it deals combat damage to a player, each opponent loses 3 life."
)

/** The Ring's abilities a player has after it has tempted them [level] times. */
fun ringAbilities(level: Int): List<String> = RING_ABILITIES.take(level.coerceIn(0, RING_ABILITIES.size))

/** A Ring-bearer's name as typed: trimmed and at most 60 characters; blank is nobody. */
fun cleanRingBearer(raw: String?): String? = raw?.trim()?.take(60)?.takeIf { it.isNotEmpty() }
