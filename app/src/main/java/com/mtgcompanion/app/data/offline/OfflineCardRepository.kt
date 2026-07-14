package com.mtgcompanion.app.data.offline

import android.content.Context
import android.database.sqlite.SQLiteStatement
import com.mtgcompanion.app.data.isOffline
import com.mtgcompanion.app.data.localMoshi
import com.mtgcompanion.app.network.NetworkModule
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.search.SearchFilters
import com.squareup.moshi.JsonReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

/** Progress/state of the downloadable offline card database, surfaced in Settings. */
data class OfflineDbStatus(
    val cardCount: Int = 0,
    val updatedAt: Long = 0L,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val message: String? = null
) {
    val hasData: Boolean get() = cardCount > 0
}

/**
 * Downloads and searches the local card database used for offline search. The download streams
 * Scryfall's oracle-cards bulk JSON straight into SQLite (constant memory), and search runs plain
 * SQL over the indexed columns.
 */
class OfflineCardRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = OfflineCardStore(appContext)
    private val prefs = appContext.getSharedPreferences("offline_cards", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cardAdapter = localMoshi.adapter(ScryfallCard::class.java)

    private val _status = MutableStateFlow(OfflineDbStatus(updatedAt = prefs.getLong(KEY_UPDATED, 0L)))
    val status: StateFlow<OfflineDbStatus> = _status.asStateFlow()

    init {
        scope.launch { refreshCount() }
    }

    /** Download (or refresh) the full card database. No-op while a download is already running. */
    fun downloadDatabase() {
        if (_status.value.downloading) return
        scope.launch {
            _status.update { it.copy(downloading = true, progress = 0, message = "Fetching card list…") }
            try {
                val entry = NetworkModule.scryfallApi.getBulkData().data
                    .firstOrNull { it.type == "oracle_cards" }
                    ?: throw IllegalStateException("Scryfall has no oracle-cards file right now.")
                val count = downloadAndStore(entry.downloadUri)
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_UPDATED, now).apply()
                _status.update {
                    it.copy(
                        downloading = false,
                        cardCount = count,
                        updatedAt = now,
                        progress = count,
                        message = "Ready — $count cards available offline."
                    )
                }
            } catch (e: Exception) {
                _status.update {
                    it.copy(
                        downloading = false,
                        message = if (isOffline(e)) {
                            "You're offline — connect to the internet to download the card database."
                        } else {
                            "Download failed: ${e.message ?: "unknown error"}"
                        }
                    )
                }
            }
        }
    }

    /** Stream the bulk JSON array into SQLite, replacing any existing rows. Returns the row count. */
    private fun downloadAndStore(url: String): Int {
        val request = Request.Builder().url(url).build()
        // Use the non-caching client so the ~35 MB body doesn't evict the small JSON HTTP cache.
        NetworkModule.imageOkHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} downloading card data")
            val body = response.body ?: throw IOException("Empty response downloading card data")

            val db = store.writableDatabase
            var n = 0
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM cards")
                val insert = db.compileStatement(
                    "INSERT OR REPLACE INTO cards" +
                        "(oracle_id,name,name_lower,type_lower,oracle_lower,cmc,colors,rarity,pow,tou,json) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?)"
                )
                val reader = JsonReader.of(body.source())
                reader.beginArray()
                while (reader.hasNext()) {
                    val card = try {
                        cardAdapter.fromJson(reader)
                    } catch (e: Exception) {
                        // Stop on the first malformed element and keep what we have rather than
                        // failing the whole import; the reader can't safely continue past it.
                        break
                    } ?: continue
                    bindCard(insert, card)
                    insert.executeInsert()
                    n++
                    if (n % 2000 == 0) {
                        _status.update { it.copy(progress = n, message = "Importing cards… $n") }
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            return n
        }
    }

    private fun bindCard(stmt: SQLiteStatement, card: ScryfallCard) {
        stmt.clearBindings()
        val oracleId = card.oracleId ?: card.id
        stmt.bindString(1, oracleId)
        stmt.bindString(2, card.name)
        stmt.bindString(3, card.name.lowercase())
        bindStringOrNull(stmt, 4, card.typeLine?.lowercase())
        bindStringOrNull(stmt, 5, card.displayOracleText?.lowercase())
        bindDoubleOrNull(stmt, 6, card.cmc)
        bindStringOrNull(stmt, 7, card.colors?.joinToString(""))
        bindStringOrNull(stmt, 8, card.rarity)
        bindDoubleOrNull(stmt, 9, card.power?.toDoubleOrNull())
        bindDoubleOrNull(stmt, 10, card.toughness?.toDoubleOrNull())
        stmt.bindString(11, cardAdapter.toJson(card))
    }

    /** Search the local database. Returns up to 60 cards, best name matches first. */
    suspend fun search(text: String, filters: SearchFilters): List<ScryfallCard> = withContext(Dispatchers.IO) {
        if (_status.value.cardCount == 0 && count() == 0) return@withContext emptyList()

        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        val q = text.trim().lowercase()

        if (q.isNotBlank()) {
            where += "name_lower LIKE ?"
            args += "%$q%"
        }
        filters.typeLine.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach {
            where += "type_lower LIKE ?"
            args += "%${it.lowercase()}%"
        }
        if (filters.oracle.isNotBlank()) {
            where += "oracle_lower LIKE ?"
            args += "%${filters.oracle.trim().lowercase()}%"
        }
        if (filters.rarities.isNotEmpty()) {
            where += "rarity IN (${filters.rarities.joinToString(",") { "?" }})"
            args += filters.rarities
        }
        filters.powerMin.toDoubleOrNull()?.let { where += "pow >= ?"; args += it.toString() }
        filters.powerMax.toDoubleOrNull()?.let { where += "pow <= ?"; args += it.toString() }
        filters.toughnessMin.toDoubleOrNull()?.let { where += "tou >= ?"; args += it.toString() }
        filters.toughnessMax.toDoubleOrNull()?.let { where += "tou <= ?"; args += it.toString() }

        // Nothing to constrain on (e.g. only offline-unsupported filters set) — don't dump the whole DB.
        if (where.isEmpty()) return@withContext emptyList()

        val order = if (q.isNotBlank()) {
            args += q
            args += "$q%"
            "ORDER BY CASE WHEN name_lower = ? THEN 0 WHEN name_lower LIKE ? THEN 1 ELSE 2 END, length(name_lower) ASC"
        } else {
            "ORDER BY name_lower ASC"
        }

        val sql = "SELECT json FROM cards WHERE ${where.joinToString(" AND ")} $order LIMIT 60"
        val results = ArrayList<ScryfallCard>()
        store.readableDatabase.rawQuery(sql, args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                runCatching { cardAdapter.fromJson(cursor.getString(0)) }.getOrNull()?.let { results += it }
            }
        }
        results
    }

    /** Exact-name lookup for offline card detail. */
    suspend fun getByName(name: String): ScryfallCard? = withContext(Dispatchers.IO) {
        store.readableDatabase
            .rawQuery("SELECT json FROM cards WHERE name_lower = ? LIMIT 1", arrayOf(name.trim().lowercase()))
            .use { cursor ->
                if (cursor.moveToFirst()) runCatching { cardAdapter.fromJson(cursor.getString(0)) }.getOrNull()
                else null
            }
    }

    private fun count(): Int =
        store.readableDatabase.rawQuery("SELECT COUNT(*) FROM cards", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private suspend fun refreshCount() = withContext(Dispatchers.IO) {
        val c = count()
        _status.update { it.copy(cardCount = c, updatedAt = prefs.getLong(KEY_UPDATED, 0L)) }
    }

    private fun bindStringOrNull(stmt: SQLiteStatement, index: Int, value: String?) {
        if (value == null) stmt.bindNull(index) else stmt.bindString(index, value)
    }

    private fun bindDoubleOrNull(stmt: SQLiteStatement, index: Int, value: Double?) {
        if (value == null) stmt.bindNull(index) else stmt.bindDouble(index, value)
    }

    companion object {
        private const val KEY_UPDATED = "updated_at"
    }
}
