package com.mtgcompanion.app.data.offline

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * On-device SQLite store for the full card pool, populated from Scryfall's "oracle_cards" bulk
 * file so the app can search every card while offline. One row per oracle card. Indexed lowercase
 * columns back substring search on name / type / oracle text; the full card is kept as a JSON blob
 * so results and detail can be reconstructed without the network.
 */
class OfflineCardStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE cards(
                oracle_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                name_lower TEXT NOT NULL,
                type_lower TEXT,
                oracle_lower TEXT,
                cmc REAL,
                colors TEXT,
                rarity TEXT,
                pow REAL,
                tou REAL,
                json TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_cards_name_lower ON cards(name_lower)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // The table is a disposable cache re-downloaded from Scryfall; just rebuild it.
        db.execSQL("DROP TABLE IF EXISTS cards")
        onCreate(db)
    }

    companion object {
        private const val DB_NAME = "offline_cards.db"
        private const val DB_VERSION = 1
    }
}
