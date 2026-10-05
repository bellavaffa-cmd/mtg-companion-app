package com.mtgcompanion.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.mtgcompanion.app.MainActivity
import com.mtgcompanion.app.MtgCompanionApplication
import com.mtgcompanion.app.R
import com.mtgcompanion.app.data.AlertDirection
import com.mtgcompanion.app.data.Money
import com.mtgcompanion.app.data.PriceAlerts
import com.mtgcompanion.app.data.Prices
import com.mtgcompanion.app.data.ValueHistory
import com.mtgcompanion.app.data.social.PushNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * The home-screen widget: the collection's value and how it moved this week, the price alerts that
 * went off lately (a tap opens the card), and buttons into the life counter and the scanner. It only
 * reads what the app keeps on the phone — no network — and is redrawn when the app notes a new value
 * or an alert goes off, and otherwise every few hours (res/xml/collection_widget_info.xml). The
 * taps open MainActivity with the same "open" extra a notification uses (see MtgNavGraph).
 */
class CollectionWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                draw(context, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private const val PREFS = "widget_alerts"
        private const val KEY = "fired"
        private val alertRows = intArrayOf(R.id.widget_alert_1, R.id.widget_alert_2, R.id.widget_alert_3)

        /** Redraws every widget on the home screen, if there are any. */
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CollectionWidget::class.java))
            if (ids.isNotEmpty()) draw(context, manager, ids)
        }

        /** [refresh] from outside a coroutine. */
        fun refreshInBackground(context: Context) {
            val app = context.applicationContext
            scope.launch { runCatching { refresh(app) } }
        }

        /** Notes price alerts that just went off, for the widget to show. */
        fun noteAlerts(context: Context, hits: List<PriceAlerts.Hit>) {
            if (hits.isEmpty()) return
            val now = System.currentTimeMillis()
            val fired = hits.map { FiredAlert(it.entry.name, it.price, it.direction, now) }
            saveAlerts(context, withFiredAlerts(loadAlerts(context), fired, now))
        }

        private fun loadAlerts(context: Context): List<FiredAlert> = runCatching {
            val a = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: "[]")
            (0 until a.length()).map { i ->
                a.getJSONObject(i).let {
                    FiredAlert(it.getString("name"), it.getDouble("usd"), if (it.optString("dir") == "ABOVE") AlertDirection.ABOVE else AlertDirection.BELOW, it.getLong("at"))
                }
            }
        }.getOrDefault(emptyList())

        private fun saveAlerts(context: Context, alerts: List<FiredAlert>) {
            val a = JSONArray()
            alerts.forEach { a.put(JSONObject().put("name", it.name).put("usd", it.usd).put("dir", it.direction.name).put("at", it.at)) }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply()
        }

        /**
         * How prices show. Prices follows the chosen currency a moment after the app process starts
         * (which a widget update can be the reason for), so it's given a moment to catch up.
         */
        private suspend fun money(context: Context): Money {
            val settings = (context.applicationContext as? MtgCompanionApplication)?.settingsRepository ?: return Prices.money.value
            val code = runCatching { settings.currency.first() }.getOrDefault("USD")
            return withTimeoutOrNull(1_500) { Prices.money.first { it.currency.code == code } } ?: Prices.money.value
        }

        private suspend fun draw(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val money = money(context)
            val points = ValueHistory.points.value
            val views = RemoteViews(context.packageName, R.layout.widget_collection)
            val last = points.lastOrNull()
            views.setTextViewText(R.id.widget_value, last?.let { money.format(it.usd) } ?: "—")
            val change = weekChangeOf(points)
            views.setTextViewText(
                R.id.widget_change,
                if (last == null) context.getString(R.string.widget_no_value) else weekChangeText(change, money)
            )
            views.setTextColor(
                R.id.widget_change,
                context.getColor(
                    when {
                        change == null || change.usd == 0.0 -> R.color.widget_text_muted
                        change.usd > 0 -> R.color.widget_up
                        else -> R.color.widget_down
                    }
                )
            )
            views.setOnClickPendingIntent(R.id.widget_value_area, open(context, "value", 0))

            val alerts = withFiredAlerts(loadAlerts(context), emptyList(), System.currentTimeMillis())
            alertRows.forEachIndexed { i, row ->
                val alert = alerts.getOrNull(i)
                if (alert == null) {
                    views.setViewVisibility(row, View.GONE)
                } else {
                    views.setViewVisibility(row, View.VISIBLE)
                    views.setTextViewText(row, firedAlertText(alert, money))
                    views.setOnClickPendingIntent(row, open(context, "card:${alert.name}", 10 + i))
                }
            }
            views.setViewVisibility(R.id.widget_no_alerts, if (alerts.isEmpty()) View.VISIBLE else View.GONE)

            views.setOnClickPendingIntent(R.id.widget_life, open(context, "life", 1))
            views.setOnClickPendingIntent(R.id.widget_scan, open(context, "scan", 2))
            manager.updateAppWidget(ids, views)
        }

        /** Opens the app at [what] ("life", "scan", "value", "card:Name"); [code] keeps each tap's intent apart. */
        private fun open(context: Context, what: String, code: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(PushNotifications.EXTRA_OPEN, what)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(context, "widget".hashCode() + code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
