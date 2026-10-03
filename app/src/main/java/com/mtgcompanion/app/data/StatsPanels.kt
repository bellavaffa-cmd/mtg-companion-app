package com.mtgcompanion.app.data

/**
 * Which panels on a deck's Stats tab are open. Each panel has a short id ("curve", "roles"…); only
 * the ones the user has opened or closed are stored, as "id=1" / "id=0", so a panel added later
 * starts in its default state.
 */
object StatsPanels {
    /** Open until the user closes them: the summary strip, the mana curve and the deck's roles. */
    val DEFAULT_OPEN = setOf("summary", "curve", "roles")

    fun isOpen(id: String, stored: Map<String, Boolean>): Boolean = stored[id] ?: (id in DEFAULT_OPEN)

    fun decode(raw: Set<String>?): Map<String, Boolean> = raw.orEmpty().mapNotNull { entry ->
        val split = entry.lastIndexOf('=')
        if (split <= 0) null else entry.substring(0, split) to (entry.substring(split + 1) == "1")
    }.toMap()

    fun encode(stored: Map<String, Boolean>): Set<String> =
        stored.map { (id, open) -> "$id=${if (open) 1 else 0}" }.toSet()
}
