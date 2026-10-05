package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.Loan
import com.mtgcompanion.app.data.isOpen
import com.mtgcompanion.app.data.serverCards

/**
 * A friend's loans, sent to the server so the friend sees what they've borrowed (SocialApi.kt,
 * supabase/migrations/20261006010000_loans.sql). Best effort, always: offline, signed out, or a
 * server without the loans functions yet, and nothing happens — the loans themselves are the
 * library's (Loans.kt) and work without it. Every call can be made again safely. The web app does
 * the same in src/collection/loanServer.ts.
 */
object LoanServer {
    /** How long after a loan came back the server is still told so (it may have missed it). */
    private const val TELL_RETURNED_MS = 30L * 24 * 60 * 60 * 1000

    /** Sends one loan as it stands: its cards still out, or that it's all back. */
    suspend fun send(api: SocialApi, loan: Loan): Boolean {
        val friend = loan.friendId ?: return false
        return runCatching {
            if (isOpen(loan)) api.upsertLoan(loan, friend, serverCards(loan)) else api.markLoanReturned(loan.id)
        }.isSuccess
    }

    /** Sends every friend's loan still out, and the ones back lately — when the Loans screen opens. */
    suspend fun sendAll(api: SocialApi, loans: List<Loan>, now: Long) {
        for (loan in loans) {
            if (loan.friendId == null) continue
            if (!isOpen(loan) && (loan.returnedAt ?: 0L) < now - TELL_RETURNED_MS) continue
            if (!send(api, loan)) return
        }
    }

    enum class Remind { SENT, ALREADY, FAILED }

    /** Asks a friend for their cards back: SENT, ALREADY (one went in the last 12 hours) or FAILED. */
    suspend fun remind(api: SocialApi, loans: List<Loan>): Remind {
        var sent = false
        var tried = false
        for (loan in loans) {
            val friend = loan.friendId ?: continue
            if (!isOpen(loan)) continue
            tried = true
            try {
                // Sent first, in case the server never had it.
                api.upsertLoan(loan, friend, serverCards(loan))
                if (api.remindLoan(loan.id)) sent = true
            } catch (e: Exception) {
                return Remind.FAILED
            }
        }
        return if (sent) Remind.SENT else if (tried) Remind.ALREADY else Remind.FAILED
    }
}
