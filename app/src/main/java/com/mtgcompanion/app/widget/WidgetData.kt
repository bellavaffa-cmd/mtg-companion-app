package com.mtgcompanion.app.widget

import com.mtgcompanion.app.data.AlertDirection
import com.mtgcompanion.app.data.Money
import com.mtgcompanion.app.data.ValueChange
import com.mtgcompanion.app.data.ValuePoint
import java.time.LocalDate
import kotlin.math.abs

// What the home-screen widget shows, worked out from what the app keeps on the phone: the
// collection's value and its move over the last week (ValueHistory), and the price alerts that went
// off lately. Plain logic, apart from the widget itself (CollectionWidget), so it can be tested.

/** A price alert that went off: [name] reached [usd] at [at], dropping under or rising over its alert. */
data class FiredAlert(val name: String, val usd: Double, val direction: AlertDirection, val at: Long)

/** How many fired alerts the widget shows, and for how long. */
const val WIDGET_ALERTS_SHOWN = 3
const val WIDGET_ALERT_DAYS = 7L

/** The value's move over the last [days] days: from the first point in them to the newest. Null under two points. */
fun weekChangeOf(points: List<ValuePoint>, days: Long = 7): ValueChange? {
    val last = points.lastOrNull() ?: return null
    val since = LocalDate.parse(last.date).minusDays(days).toString()
    val first = points.firstOrNull { it.date >= since } ?: return null
    return if (first == last) null else ValueChange(first, last)
}

/** "+$12.30 (+2.1%) this week" / "−₱450 (−0.4%) this week". */
fun weekChangeText(change: ValueChange?, money: Money): String {
    if (change == null) return "Its change shows from tomorrow"
    val sign = if (change.usd < 0) "−" else "+"
    val amount = money.format(abs(change.usd), whole = abs(money.toLocal(change.usd)) >= 100)
    val percent = change.percent?.let { " ($sign${String.format(java.util.Locale.US, "%.1f", abs(it))}%)" }.orEmpty()
    return "$sign$amount$percent this week"
}

/** [old] with [fired] added: newest first, one per card, at most [WIDGET_ALERTS_SHOWN], none older than [WIDGET_ALERT_DAYS]. */
fun withFiredAlerts(old: List<FiredAlert>, fired: List<FiredAlert>, now: Long): List<FiredAlert> {
    val since = now - WIDGET_ALERT_DAYS * 24 * 60 * 60 * 1000
    return (fired + old)
        .filter { it.at >= since }
        .sortedByDescending { it.at }
        .distinctBy { it.name.lowercase() }
        .take(WIDGET_ALERTS_SHOWN)
}

/** "Sol Ring ↓ $1.20" — the arrow says which way it went past its alert. */
fun firedAlertText(alert: FiredAlert, money: Money): String =
    "${alert.name}  ${if (alert.direction == AlertDirection.ABOVE) "↑" else "↓"} ${money.format(alert.usd)}"
