package com.mtgcompanion.app.data

import android.content.Context
import com.mtgcompanion.app.network.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.Locale

// Prices come from Scryfall in US dollars (TCGplayer's market price) and stay in US dollars
// everywhere they're kept — price alerts, value history, trades. Only showing them converts: to the
// currency picked in Settings, at the European Central Bank's rate of the day (from Frankfurter,
// free and keyless). Mirrors the web app's src/money/currency.ts.

/** A currency prices can show in. [decimals]: 0 for yen, won…; [after]: the symbol follows the amount ("12 kr"). */
data class DisplayCurrency(val code: String, val name: String, val symbol: String, val decimals: Int = 2, val after: Boolean = false)

object Currencies {
    /** US dollars first; the rest by name. Every one of them has an ECB rate. */
    val ALL = listOf(
        DisplayCurrency("USD", "US dollar", "$"),
        DisplayCurrency("AUD", "Australian dollar", "A$"),
        DisplayCurrency("BRL", "Brazilian real", "R$"),
        DisplayCurrency("GBP", "British pound", "£"),
        DisplayCurrency("CAD", "Canadian dollar", "C$"),
        DisplayCurrency("CNY", "Chinese yuan", "CN¥"),
        DisplayCurrency("CZK", "Czech koruna", "Kč", after = true),
        DisplayCurrency("DKK", "Danish krone", "kr", after = true),
        DisplayCurrency("EUR", "Euro", "€"),
        DisplayCurrency("HKD", "Hong Kong dollar", "HK$"),
        DisplayCurrency("HUF", "Hungarian forint", "Ft", decimals = 0, after = true),
        DisplayCurrency("ISK", "Icelandic króna", "kr", decimals = 0, after = true),
        DisplayCurrency("INR", "Indian rupee", "₹"),
        DisplayCurrency("IDR", "Indonesian rupiah", "Rp", decimals = 0),
        DisplayCurrency("ILS", "Israeli shekel", "₪"),
        DisplayCurrency("JPY", "Japanese yen", "¥", decimals = 0),
        DisplayCurrency("MYR", "Malaysian ringgit", "RM"),
        DisplayCurrency("MXN", "Mexican peso", "MX$"),
        DisplayCurrency("NZD", "New Zealand dollar", "NZ$"),
        DisplayCurrency("NOK", "Norwegian krone", "kr", after = true),
        DisplayCurrency("PHP", "Philippine peso", "₱"),
        DisplayCurrency("PLN", "Polish złoty", "zł", after = true),
        DisplayCurrency("RON", "Romanian leu", "lei", after = true),
        DisplayCurrency("SGD", "Singapore dollar", "S$"),
        DisplayCurrency("ZAR", "South African rand", "R"),
        DisplayCurrency("KRW", "South Korean won", "₩", decimals = 0),
        DisplayCurrency("SEK", "Swedish krona", "kr", after = true),
        DisplayCurrency("CHF", "Swiss franc", "CHF "),
        DisplayCurrency("THB", "Thai baht", "฿"),
        DisplayCurrency("TRY", "Turkish lira", "₺")
    )

    fun of(code: String?): DisplayCurrency = ALL.firstOrNull { it.code == code } ?: ALL.first()
}

/** How prices show: in [currency], at [rate] of it to the US dollar (1.0 for dollars). */
data class Money(val currency: DisplayCurrency, val rate: Double) {
    fun toLocal(usd: Double): Double = usd * rate
    fun toUsd(local: Double): Double = if (rate > 0) local / rate else local

    /** [usd] in this currency: "₱1,234.50"; [whole] drops the cents ("₱1,235"). */
    fun format(usd: Double, whole: Boolean = false): String = formatLocal(usd * rate, whole)

    /** An amount already in this currency. */
    fun formatLocal(amount: Double, whole: Boolean = false): String {
        val decimals = if (whole) 0 else currency.decimals
        val number = String.format(Locale.US, "%,.${decimals}f", amount)
        return if (currency.after) "$number ${currency.symbol}" else currency.symbol + number
    }

    /** A Scryfall price ("1.23"), formatted; null when there's none. */
    fun format(usd: String?): String? = usd?.toDoubleOrNull()?.let { format(it) }

    val isUsd: Boolean get() = currency.code == "USD"

    companion object {
        val USD = Money(Currencies.ALL.first(), 1.0)
    }
}

object Prices {
    private const val FILE = "fx_rates.json"
    private const val URL = "https://api.frankfurter.dev/v1/latest?from=USD"
    /** The ECB publishes once a working day; asking twice a day is plenty. */
    private const val FRESH_MS = 12L * 60 * 60 * 1000

    private val _money = MutableStateFlow(Money.USD)
    /** How prices show right now. Falls back to US dollars until a rate for the chosen currency is known. */
    val money: StateFlow<Money> = _money.asStateFlow()

    /** The chosen currency (maybe not showing yet, if its rate isn't known). */
    private val _chosen = MutableStateFlow("USD")
    val chosen: StateFlow<String> = _chosen.asStateFlow()

    /** The day the rates are from ("2026-09-18"), or null before any have been fetched. */
    private val _ratesDate = MutableStateFlow<String?>(null)
    val ratesDate: StateFlow<String?> = _ratesDate.asStateFlow()

    private val _loading = MutableStateFlow(false)
    /** Whether today's rates are being fetched. */
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var rates: Map<String, Double> = emptyMap()
    private var fetchedAt = 0L
    private var dir: File? = null
    private val lock = Mutex()

    /** Loads the rates from last time and follows the chosen currency. Called once, from the Application. */
    fun init(context: Context, settings: SettingsRepository, scope: CoroutineScope) {
        dir = context.filesDir
        runCatching {
            val o = JSONObject(File(context.filesDir, FILE).readText())
            val r = o.getJSONObject("rates")
            rates = r.keys().asSequence().associateWith { r.getDouble(it) }
            fetchedAt = o.getLong("at")
            _ratesDate.value = o.optString("date").ifEmpty { null }
        }
        scope.launch {
            settings.currency.collect { code ->
                _chosen.value = code
                apply()
                if (code != "USD") refresh()
            }
        }
    }

    private fun apply() {
        val currency = Currencies.of(_chosen.value)
        val rate = if (currency.code == "USD") 1.0 else rates[currency.code]
        _money.value = if (rate != null && rate > 0) Money(currency, rate) else Money.USD
    }

    /** Fetches today's rates, unless the ones kept are recent. False if they couldn't be fetched. */
    suspend fun refresh(force: Boolean = false): Boolean = lock.withLock {
        if (!force && rates.isNotEmpty() && System.currentTimeMillis() - fetchedAt < FRESH_MS) return@withLock true
        _loading.value = true
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val body = NetworkModule.noCacheOkHttpClient.newCall(Request.Builder().url(URL).build()).execute().use { r ->
                    check(r.isSuccessful) { "rates ${r.code}" }
                    r.body!!.string()
                }
                val o = JSONObject(body)
                val r = o.getJSONObject("rates")
                rates = r.keys().asSequence().associateWith { r.getDouble(it) }
                fetchedAt = System.currentTimeMillis()
                _ratesDate.value = o.optString("date").ifEmpty { null }
                dir?.let { File(it, FILE).writeText(JSONObject().put("rates", r).put("at", fetchedAt).put("date", o.optString("date")).toString()) }
            }.isSuccess
        }
        _loading.value = false
        apply()
        ok
    }
}
