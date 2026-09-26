package com.mtgcompanion.app.data.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The badge protocol, against a stand-in badge.
 *
 * Nothing here needs hardware, which is the point: the refresh negotiation is several exchanges
 * deep and answers differently each time, so the only way to be sure the order is right without a
 * badge on the desk is to script the answers and check what went out.
 */
class BadgeProtocolTest {

    /** A badge that replies from a script and records what it was asked. */
    private class FakeBadge(private vararg val replies: String) : BadgeLink {
        val sent = mutableListOf<String>()
        var at = 0
        override fun transceive(apdu: ByteArray, timeoutMs: Int): ByteArray {
            sent += apdu.toHex()
            val reply = replies.getOrElse(at) { "9000" }
            at++
            if (reply == "LOST") throw BadgeLinkLost()
            if (reply == "SILENT") throw BadgeLinkLost(timedOut = true)
            return reply.fromHex()
        }
    }

    private fun session(vararg replies: String) = FakeBadge(*replies).let { it to BadgeSession(it, nap = {}) }

    // ---- Refresh negotiation ------------------------------------------------------------------

    @Test
    fun `a badge that takes the first refresh needs one poll`() {
        val (badge, s) = session("9000", "009000")
        assertEquals(BadgeOutcome.Done, s.refresh())
        assertEquals(listOf("F0D4050000", "F0DE000001"), badge.sent)
    }

    @Test
    fun `a badge that says it will draw on its own is not polled`() {
        val (badge, s) = session("019000")
        assertEquals(BadgeOutcome.Done, s.refresh())
        assertEquals(listOf("F0D4050000"), badge.sent)
    }

    @Test
    fun `two refusals escalate to the slow refresh`() {
        // 68C6 means "not like that": once is a retry, twice means use the form that computes the
        // waveform. Getting this order wrong leaves the screen unchanged with no error anywhere.
        val (badge, s) = session("68C6", "68C6", "019000")
        assertEquals(BadgeOutcome.Done, s.refresh())
        assertEquals(listOf("F0D4050000", "F0D4050000", "F0D4850000"), badge.sent)
    }

    @Test
    fun `a refusal after the slow refresh gives up rather than looping`() {
        val (_, s) = session("68C6", "68C6", "68C6")
        assertTrue(s.refresh() is BadgeOutcome.Failed)
    }

    @Test
    fun `not-now is retried from the top, five times`() {
        val replies = Array(6) { "6986" }
        val (badge, s) = session(*replies)
        assertTrue(s.refresh() is BadgeOutcome.Failed)
        assertEquals(6, badge.sent.size)
        assertTrue(badge.sent.all { it == "F0D4050000" })
    }

    @Test
    fun `a busy badge is asked once more`() {
        val (badge, s) = session("68CA", "9000")
        assertEquals(BadgeOutcome.Done, s.refresh())
        assertEquals(listOf("F0D4050000", "F0D4050000"), badge.sent)
    }

    @Test
    fun `a hardware fault stops immediately`() {
        val (badge, s) = session("698A")
        assertTrue(s.refresh() is BadgeOutcome.Failed)
        assertEquals(1, badge.sent.size)
    }

    @Test
    fun `losing the badge while it draws still counts as done`() {
        // E-paper holds its picture without power, so a badge pulled away after the refresh was
        // accepted finishes drawing by itself. Calling that a failure would send the user round again.
        val (_, s) = session("9000", "LOST")
        assertEquals(BadgeOutcome.Done, s.refresh())
    }

    @Test
    fun `the slot to draw is carried in every refresh`() {
        val (badge, s) = session("68C6", "68C6", "019000")
        s.refresh(section = 2)
        assertEquals(listOf("F0D4050200", "F0D4050200", "F0D4850200"), badge.sent)
    }

    // ---- Sending the picture ------------------------------------------------------------------

    @Test
    fun `a picture goes up in numbered slices of 250 bytes`() {
        val image = ByteArray(600) { 0x2A }
        val badge = FakeBadge(*Array(10) { "9000" })
        val progress = mutableListOf<Int>()
        val outcome = BadgeSession(badge, nap = {}).show(image, section = 0) {
            if (it is BadgeProgress.Sending) progress += it.done
        }
        assertEquals(BadgeOutcome.Done, outcome)
        // Three slices: 250, 250, 100. The header is F0 D2 <slot> <sequence> <length>.
        assertTrue(badge.sent[0].startsWith("F0D20000FA"))
        assertTrue(badge.sent[1].startsWith("F0D20001FA"))
        assertTrue(badge.sent[2].startsWith("F0D2000264"))
        assertEquals(100 * 2, badge.sent[2].length - 10)
        assertEquals(listOf(1, 2, 3), progress)
    }

    @Test
    fun `a slice the badge refuses stops the write there`() {
        val badge = FakeBadge("9000", "6A80")
        val outcome = BadgeSession(badge, nap = {}).show(ByteArray(400) { 1 })
        assertTrue(outcome is BadgeOutcome.Failed)
        // Two slices attempted, and nothing tried to draw afterwards.
        assertEquals(2, badge.sent.size)
    }

    @Test
    fun `losing the badge part-way through says so, because it all has to go again`() {
        val badge = FakeBadge("9000", "LOST")
        val outcome = BadgeSession(badge, nap = {}).show(ByteArray(400) { 1 })
        assertTrue(outcome is BadgeOutcome.Moved)
        // Where it died is carried out, because "it moved" at chunk 2 and at chunk 99 want
        // different advice — and on real hardware it's the only clue there is.
        assertEquals(BadgePhase.SENDING, (outcome as BadgeOutcome.Moved).phase)
        assertEquals("chunk 2 of 2", outcome.at)
    }

    @Test
    fun `a full screen is a hundred slices`() {
        val badge = FakeBadge(*Array(120) { "9000" })
        BadgeSession(badge, nap = {}).show(ByteArray(DEFAULT_BADGE.imageBytes) { 0 })
        assertEquals(24_960, DEFAULT_BADGE.imageBytes)
        assertEquals(100, badge.sent.count { it.startsWith("F0D2") })
    }

    // ---- Reading what the badge is ------------------------------------------------------------

    @Test
    fun `the config read asks both questions`() {
        val badge = FakeBadge(bwrTlv(), "9000", "00009000")
        val config = BadgeSession(badge, nap = {}).readConfig()
        assertEquals("F0D8000005000000000E", badge.sent[1])
        assertEquals(240, config.width)
        assertEquals(416, config.height)
    }

    @Test
    fun `silence from the quick refresh escalates instead of failing`() {
        // A four-colour badge answers the first refresh by saying nothing at all for longer than
        // any sane timeout -- which Android reports as a lost tag. Treating that as the user moving
        // the badge meant the picture uploaded perfectly and then was never drawn.
        val (badge, s) = session("SILENT", "019000")
        assertEquals(BadgeOutcome.Done, s.refresh())
        assertEquals(listOf("F0D4050000", "F0D4850000"), badge.sent)
    }

    @Test
    fun `silence from the slow refresh is reported, not escalated forever`() {
        // The first reply answers the write, so the two silences land on the two refresh stages.
        val (badge, s) = session("9000", "SILENT", "SILENT")
        val outcome = s.show(ByteArray(10) { 0 })
        assertTrue(outcome is BadgeOutcome.Moved)
        assertTrue((outcome as BadgeOutcome.Moved).timedOut)
        assertEquals(BadgePhase.DRAWING, outcome.phase)
        // One write, then the two refresh attempts -- it does not keep escalating.
        assertEquals(3, badge.sent.size)
    }

    @Test
    fun `a badge taken off the phone is not confused with one that is thinking`() {
        val (_, s) = session("9000", "LOST")
        val outcome = s.show(ByteArray(10) { 0 })
        assertTrue(outcome is BadgeOutcome.Moved)
        assertEquals(BadgePhase.DRAWING, (outcome as BadgeOutcome.Moved).phase)
        assertEquals(false, outcome.timedOut)
    }
}

internal fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }

internal fun String.fromHex() = ByteArray(length / 2) { ((this[it * 2].digit() shl 4) or this[it * 2 + 1].digit()).toByte() }

private fun Char.digit() = Character.digit(this, 16)

/** A plausible answer from a 3.7" black/white/red badge. */
internal fun bwrTlv(): String = buildString {
    append("A007").append("00").append("00").append("30").append("01A0").append("00F0")  // maker, colour code, 416 high, 240 wide
    append("A105").append("00").append("33").append("00").append("28").append("58")      // 3 colours: black 00, white 01, red 11
    append("B101").append("03")                                                          // three picture slots
    append("B201").append("14")
    append("B301").append("00")
    append("C004").append("11223344")
    append("C104").append("55667788")
    append("D102").append("01").append("20")
    append("9000")
}
