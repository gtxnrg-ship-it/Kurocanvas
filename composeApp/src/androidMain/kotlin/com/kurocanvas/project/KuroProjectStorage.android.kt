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
            val stored = try { readActiveProject(database) } finally { database.close() }
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
                        put("project_id", state.projectId)
                        put("title", state.title)
                        put("revision", state.revision)
                        put("payload", KuroProjectCodec.encode(state))
                        put("updated_at_epoch_ms", System.currentTimeMillis())
                    }
                    val rowId = database.insertWithOnConflict(
                        "projects", null, values, SQLiteDatabase.CONFLICT_REPLACE
                    )
                    if (rowId < 0L) throw IllegalStateException("SQLite did not store the project state.")
                    setActiveProject(database, state.projectId)
                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
            } finally {
                database.close()
            }
            KuroStorageWriteResult(true, "Saved to the local SQLite project catalog.")
        } catch (error: Exception) {
            KuroStorageWriteResult(
                false,
                "Could not save the project to SQLite: " +
                    (error.message ?: error::class.simpleName ?: "storage error")
            )
        }
    }

    override fun listProjects(): KuroProjectCatalogReadResult {
        return try {
            val database = openKuroDatabase(rootPath)
            try {
                val activeId = activeProjectId(database)
                val cursor = database.rawQuery(
                    "SELECT project_id, title, revision, updated_at_epoch_ms FROM projects " +
                        "ORDER BY updated_at_epoch_ms DESC, title COLLATE NOCASE ASC",
                    null
                )
                val projects = try {
                    buildList {
                        while (cursor.moveToNext()) {
                            add(KuroProjectSummary(
                                projectId = cursor.getString(0),
                                title = cursor.getString(1),
                                revision = cursor.getLong(2),
                                updatedAtEpochMillis = cursor.getLong(3)
                            ))
                        }
                    }
                } finally {
                    cursor.close()
                }
                KuroProjectCatalogReadResult(projects, activeId)
            } finally {
                database.close()
            }
        } catch (error: Exception) {
            KuroProjectCatalogReadResult(
                projects = emptyList(),
                warning = "The project catalog could not be read. " +
                    (error.message ?: error::class.simpleName ?: "storage error")
            )
        }
    }

    override fun activateProject(projectId: String): KuroStorageReadResult {
        return try {
            val database = openKuroDatabase(rootPath)
            try {
                val result = readProjectById(database, projectId)
                    ?: return KuroStorageReadResult(
                        state = null,
                        found = true,
                        warning = "That project is no longer available in the local catalog."
                    )
                val state = result.state ?: return result
                database.beginTransaction()
                try {
                    setActiveProject(database, state.projectId)
                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
                result
            } finally {
                database.close()
            }
        } catch (error: Exception) {
            KuroStorageReadResult(
                state = null,
                found = true,
                warning = "Could not open the selected project. " +
                    (error.message ?: error::class.simpleName ?: "storage error")
            )
        }
    }

    private fun readActiveProject(database: SQLiteDatabase): KuroStorageReadResult? {
        val activeId = activeProjectId(database)
        if (activeId != null) {
            val active = readProjectById(database, activeId)
            if (active != null) return active
        }
        val cursor = database.rawQuery(
            "SELECT project_id, payload FROM projects ORDER BY updated_at_epoch_ms DESC LIMIT 1",
            null
        )
        val found = try {
            if (!cursor.moveToFirst()) null else cursor.getString(0) to cursor.getString(1)
        } finally {
            cursor.close()
        } ?: return null
        val result = decodeProject(found.second)
        if (result.state != null && result.warning == null && activeId != result.state.projectId) {
            database.beginTransaction()
            try {
                setActiveProject(database, result.state.projectId)
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
        return result
    }

    private fun readProjectById(database: SQLiteDatabase, projectId: String): KuroStorageReadResult? {
        val cursor = database.rawQuery(
            "SELECT payload FROM projects WHERE project_id = ?",
            arrayOf(projectId)
        )
        return try {
            if (!cursor.moveToFirst()) null else decodeProject(cursor.getString(0))
        } finally {
            cursor.close()
        }
    }

    private fun decodeProject(payload: String): KuroStorageReadResult {
        return try {
            KuroStorageReadResult(KuroProjectCodec.decode(payload), found = true)
        } catch (error: Exception) {
            KuroStorageReadResult(
                state = null,
                found = true,
                warning = "The selected project's saved data is invalid and was preserved. " +
                    (error.message ?: error::class.simpleName ?: "invalid data")
            )
        }
    }

    private fun activeProjectId(database: SQLiteDatabase): String? {
        val cursor = database.rawQuery(
            "SELECT active_project_id FROM app_settings WHERE singleton_id = 1", null
        )
        return try {
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } finally {
            cursor.close()
        }
    }
}
