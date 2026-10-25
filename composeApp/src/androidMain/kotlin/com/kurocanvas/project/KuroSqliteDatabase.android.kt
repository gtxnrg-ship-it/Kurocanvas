package com.kurocanvas.project

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File

internal const val KURO_DATABASE_NAME = "kuro-canvas.db"
private const val CURRENT_DATABASE_VERSION = 2

internal fun openKuroDatabase(rootPath: String): SQLiteDatabase {
    val directory = File(rootPath)
    if (!directory.exists() && !directory.mkdirs()) {
        throw IllegalStateException("Could not create the private Kuro data directory.")
    }
    if (!directory.isDirectory) throw IllegalStateException("The Kuro data path is not a directory.")

    val database = SQLiteDatabase.openOrCreateDatabase(File(directory, KURO_DATABASE_NAME), null)
    try {
        if (database.version > CURRENT_DATABASE_VERSION) {
            throw IllegalStateException("This database was created by a newer Kuro Canvas version.")
        }
        if (database.version == 0) {
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
                database.version = 1
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
        if (database.version == 1) migrateV1ToV2(database)
        requireTable(database, "projects")
        requireTable(database, "app_settings")
        requireTable(database, "vault_index")
        return database
    } catch (error: Exception) {
        database.close()
        throw error
    }
}

private fun migrateV1ToV2(database: SQLiteDatabase) {
    database.beginTransaction()
    try {
        database.execSQL(
            "CREATE TABLE projects (" +
                "project_id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, revision INTEGER NOT NULL, " +
                "payload TEXT NOT NULL, updated_at_epoch_ms INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE app_settings (" +
                "singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1), active_project_id TEXT)"
        )
        val cursor = database.rawQuery(
            "SELECT project_id, revision, payload, updated_at_epoch_ms FROM project_state WHERE singleton_id = 1",
            null
        )
        try {
            if (cursor.moveToFirst()) {
                val legacyId = cursor.getString(0)
                val revision = cursor.getLong(1)
                val payload = cursor.getString(2)
                val updatedAt = cursor.getLong(3)
                val state = KuroProjectCodec.decode(payload)
                if (state.projectId != legacyId || state.revision != revision) {
                    throw IllegalStateException("The legacy project metadata does not match its saved payload.")
                }
                val values = ContentValues().apply {
                    put("project_id", state.projectId)
                    put("title", state.title)
                    put("revision", state.revision)
                    put("payload", payload)
                    put("updated_at_epoch_ms", updatedAt)
                }
                if (database.insertOrThrow("projects", null, values) < 0L) {
                    throw IllegalStateException("Could not migrate the existing project into the project catalog.")
                }
                val settings = ContentValues().apply {
                    put("singleton_id", 1)
                    put("active_project_id", state.projectId)
                }
                database.insertOrThrow("app_settings", null, settings)
            }
        } finally {
            cursor.close()
        }
        database.version = CURRENT_DATABASE_VERSION
        database.setTransactionSuccessful()
    } finally {
        database.endTransaction()
    }
}

internal fun setActiveProject(database: SQLiteDatabase, projectId: String) {
    val values = ContentValues().apply {
        put("singleton_id", 1)
        put("active_project_id", projectId)
    }
    database.insertWithOnConflict("app_settings", null, values, SQLiteDatabase.CONFLICT_REPLACE)
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
