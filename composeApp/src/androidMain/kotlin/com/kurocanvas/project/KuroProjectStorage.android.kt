package com.kurocanvas.project

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File

actual fun createKuroProjectStorage(rootPath: String): KuroProjectStorage =
    SqliteKuroProjectStorage(rootPath)

private class SqliteKuroProjectStorage(private val rootPath: String) : KuroProjectStorage {
    private val databaseFile = File(rootPath, KURO_DATABASE_NAME)
    private val legacyFile = File(rootPath, "project.json")

    override fun load(): KuroStorageReadResult {
        if (!databaseFile.exists() && !legacyFile.exists()) {
            return KuroStorageReadResult(state = null, found = false)
        }
        try {
            val database = openKuroDatabase(rootPath)
            val stored = try { readStoredProject(database) } finally { database.close() }
            if (stored != null) return stored
        } catch (error: Exception) {
            return KuroStorageReadResult(
                state = null,
                found = true,
                warning = "Saved project data was not changed because the SQLite database could not be read. " +
                    (error.message ?: error::class.simpleName ?: "storage error")
            )
        }

        if (!legacyFile.exists()) return KuroStorageReadResult(state = null, found = databaseFile.exists())
        val legacyState = try {
            KuroProjectCodec.decode(legacyFile.readText(Charsets.UTF_8))
        } catch (error: Exception) {
            return KuroStorageReadResult(
                state = null,
                found = true,
                warning = "The legacy project file could not be imported and was left untouched. " +
                    (error.message ?: error::class.simpleName ?: "invalid data")
            )
        }
        val migrated = save(legacyState)
        return if (migrated.success) {
            KuroStorageReadResult(state = legacyState, found = true)
        } else {
            KuroStorageReadResult(
                state = legacyState,
                found = true,
                warning = "The legacy project was loaded, but migration to SQLite failed. The original file was preserved. " +
                    migrated.message
            )
        }
    }

    override fun save(state: KuroProjectState): KuroStorageWriteResult {
        return try {
            val database = openKuroDatabase(rootPath)
            try {
                database.beginTransaction()
                try {
                    val values = ContentValues().apply {
                        put("singleton_id", 1)
                        put("schema_version", 1)
                        put("project_id", state.projectId)
                        put("revision", state.revision)
                        put("payload", KuroProjectCodec.encode(state))
                        put("updated_at_epoch_ms", System.currentTimeMillis())
                    }
                    val rowId = database.insertWithOnConflict(
                        "project_state", null, values, SQLiteDatabase.CONFLICT_REPLACE
                    )
                    if (rowId < 0L) throw IllegalStateException("SQLite did not store the project state.")
                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
            } finally {
                database.close()
            }
            KuroStorageWriteResult(true, "Saved to the local SQLite database.")
        } catch (error: Exception) {
            KuroStorageWriteResult(
                false,
                "Could not save the project to SQLite: " +
                    (error.message ?: error::class.simpleName ?: "storage error")
            )
        }
    }

    private fun readStoredProject(database: SQLiteDatabase): KuroStorageReadResult? {
        val cursor = database.rawQuery(
            "SELECT payload FROM project_state WHERE singleton_id = 1",
            null
        )
        return try {
            if (!cursor.moveToFirst()) return null
            try {
                KuroStorageReadResult(KuroProjectCodec.decode(cursor.getString(0)), found = true)
            } catch (error: Exception) {
                KuroStorageReadResult(
                    state = null,
                    found = true,
                    warning = "The stored project payload is invalid and was preserved. " +
                        (error.message ?: error::class.simpleName ?: "invalid data")
                )
            }
        } finally {
            cursor.close()
        }
    }
}
