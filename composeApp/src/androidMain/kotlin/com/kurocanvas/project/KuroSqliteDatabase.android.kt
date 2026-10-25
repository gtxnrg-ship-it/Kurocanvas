package com.kurocanvas.project

import android.database.sqlite.SQLiteDatabase
import java.io.File

internal const val KURO_DATABASE_NAME = "kuro-canvas.db"
private const val CURRENT_DATABASE_VERSION = 1

internal fun openKuroDatabase(rootPath: String): SQLiteDatabase {
    val directory = File(rootPath)
    if (!directory.exists() && !directory.mkdirs()) {
        throw IllegalStateException("Could not create the private Kuro data directory.")
    }
    if (!directory.isDirectory) throw IllegalStateException("The Kuro data path is not a directory.")

    val database = SQLiteDatabase.openOrCreateDatabase(File(directory, KURO_DATABASE_NAME), null)
    try {
        val version = database.version
        if (version > CURRENT_DATABASE_VERSION) {
            throw IllegalStateException("This database was created by a newer Kuro Canvas version.")
        }
        if (version == 0) {
            database.beginTransaction()
            try {
                database.execSQL(
                    "CREATE TABLE project_state (" +
                        "singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1), " +
                        "schema_version INTEGER NOT NULL, project_id TEXT NOT NULL, " +
                        "revision INTEGER NOT NULL, payload TEXT NOT NULL, updated_at_epoch_ms INTEGER NOT NULL)"
                )
                database.execSQL(
                    "CREATE TABLE vault_index (" +
                        "singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1), " +
                        "schema_version INTEGER NOT NULL, payload TEXT NOT NULL, updated_at_epoch_ms INTEGER NOT NULL)"
                )
                database.version = CURRENT_DATABASE_VERSION
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        } else {
            requireTable(database, "project_state")
            requireTable(database, "vault_index")
        }
        return database
    } catch (error: Exception) {
        database.close()
        throw error
    }
}

private fun requireTable(database: SQLiteDatabase, tableName: String) {
    val cursor = database.rawQuery(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
        arrayOf(tableName)
    )
    val exists = try {
        cursor.moveToFirst()
    } finally {
        cursor.close()
    }
    if (!exists) throw IllegalStateException("The Kuro database is missing the " + tableName + " table.")
}
