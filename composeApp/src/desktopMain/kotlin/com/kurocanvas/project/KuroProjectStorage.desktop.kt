package com.kurocanvas.project

import java.io.File
import java.sql.Connection

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
            val connection = openKuroDatabase(rootPath)
            val stored = try { readStoredProject(connection) } finally { connection.close() }
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
            val connection = openKuroDatabase(rootPath)
            try {
                connection.autoCommit = false
                try {
                    connection.prepareStatement(
                        "INSERT INTO project_state " +
                            "(singleton_id, schema_version, project_id, revision, payload, updated_at_epoch_ms) " +
                            "VALUES (1, 1, ?, ?, ?, ?) " +
                            "ON CONFLICT(singleton_id) DO UPDATE SET " +
                            "schema_version = excluded.schema_version, project_id = excluded.project_id, " +
                            "revision = excluded.revision, payload = excluded.payload, " +
                            "updated_at_epoch_ms = excluded.updated_at_epoch_ms"
                    ).use { statement ->
                        statement.setString(1, state.projectId)
                        statement.setLong(2, state.revision)
                        statement.setString(3, KuroProjectCodec.encode(state))
                        statement.setLong(4, System.currentTimeMillis())
                        if (statement.executeUpdate() != 1) {
                            throw IllegalStateException("SQLite did not store the project state.")
                        }
                    }
                    connection.commit()
                } catch (error: Exception) {
                    connection.rollback()
                    throw error
                } finally {
                    connection.autoCommit = true
                }
            } finally {
                connection.close()
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

    private fun readStoredProject(connection: Connection): KuroStorageReadResult? =
        connection.prepareStatement("SELECT payload FROM project_state WHERE singleton_id = 1").use { statement ->
            statement.executeQuery().use { result ->
                if (!result.next()) return null
                try {
                    KuroStorageReadResult(KuroProjectCodec.decode(result.getString(1)), found = true)
                } catch (error: Exception) {
                    KuroStorageReadResult(
                        state = null,
                        found = true,
                        warning = "The stored project payload is invalid and was preserved. " +
                            (error.message ?: error::class.simpleName ?: "invalid data")
                    )
                }
            }
        }
}
