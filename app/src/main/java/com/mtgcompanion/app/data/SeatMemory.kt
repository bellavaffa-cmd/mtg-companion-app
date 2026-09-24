package com.mtgcompanion.app.data

/**
 * The seat you're sitting in at someone else's life counter table.
 *
 * Stored so the way back to your remote can be offered after you step off that screen — the QR that
 * got you there is on their phone. See SettingsRepository.remoteSeat; the web app's
 * src/lifecounter/seat.ts makes the same decisions.
 */
data class SeatMemory(val matchId: String, val seat: Int, val at: Long) {

    /** "matchId/seat/when" — one preference rather than three. */
    fun store(): String = "$matchId/$seat/$at"

    /** Whether this is still worth offering a way back to. */
    fun fresh(now: Long = System.currentTimeMillis()): Boolean = now - at <= REMOTE_SEAT_STALE_MS

    companion object {
        fun parse(raw: String?): SeatMemory? {
            val parts = raw?.split("/") ?: return null
            if (parts.size != 3) return null
            val seat = parts[1].toIntOrNull() ?: return null
            val at = parts[2].toLongOrNull() ?: return null
            val made = SeatMemory(parts[0], seat, at)
            return if (parts[0].isNotEmpty() && made.fresh()) made else null
        }
    }
}

/** Long enough for a game night, short enough that yesterday's table isn't still offered. */
const val REMOTE_SEAT_STALE_MS = 12L * 60 * 60 * 1000
