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
            val stored = try { readActiveProject(connection) } finally { connection.close() }
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
                        "INSERT INTO projects (project_id,title,revision,payload,updated_at_epoch_ms) " +
                            "VALUES (?,?,?,?,?) ON CONFLICT(project_id) DO UPDATE SET " +
                            "title=excluded.title, revision=excluded.revision, payload=excluded.payload, " +
                            "updated_at_epoch_ms=excluded.updated_at_epoch_ms"
                    ).use { statement ->
                        statement.setString(1, state.projectId)
                        statement.setString(2, state.title)
                        statement.setLong(3, state.revision)
                        statement.setString(4, KuroProjectCodec.encode(state))
                        statement.setLong(5, System.currentTimeMillis())
                        if (statement.executeUpdate() != 1) {
                            throw IllegalStateException("SQLite did not store the project state.")
                        }
                    }
                    setActiveProject(connection, state.projectId)
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
            val connection = openKuroDatabase(rootPath)
            try {
                val activeId = activeProjectId(connection)
                val projects = connection.prepareStatement(
                    "SELECT project_id,title,revision,updated_at_epoch_ms FROM projects " +
                        "ORDER BY updated_at_epoch_ms DESC, title COLLATE NOCASE ASC"
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        buildList {
                            while (result.next()) {
                                add(KuroProjectSummary(
                                    projectId = result.getString(1),
                                    title = result.getString(2),
                                    revision = result.getLong(3),
                                    updatedAtEpochMillis = result.getLong(4)
                                ))
                            }
                        }
                    }
                }
                KuroProjectCatalogReadResult(projects, activeId)
            } finally {
                connection.close()
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
            val connection = openKuroDatabase(rootPath)
            try {
                val result = readProjectById(connection, projectId)
                    ?: return KuroStorageReadResult(
                        state = null,
                        found = true,
                        warning = "That project is no longer available in the local catalog."
                    )
                val state = result.state ?: return result
                connection.autoCommit = false
                try {
                    setActiveProject(connection, state.projectId)
                    connection.commit()
                } catch (error: Exception) {
                    connection.rollback()
                    throw error
                } finally {
                    connection.autoCommit = true
                }
                result
            } finally {
                connection.close()
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

    private fun readActiveProject(connection: Connection): KuroStorageReadResult? {
        val activeId = activeProjectId(connection)
        if (activeId != null) {
            val active = readProjectById(connection, activeId)
            if (active != null) return active
        }
        val latest = connection.prepareStatement(
            "SELECT project_id,payload FROM projects ORDER BY updated_at_epoch_ms DESC LIMIT 1"
        ).use { statement ->
            statement.executeQuery().use { result ->
                if (!result.next()) null else result.getString(1) to result.getString(2)
            }
        } ?: return null
        val decoded = decodeProject(latest.second)
        if (decoded.state != null && decoded.warning == null && activeId != decoded.state.projectId) {
            connection.autoCommit = false
            try {
                setActiveProject(connection, decoded.state.projectId)
                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
        return decoded
    }

    private fun readProjectById(connection: Connection, projectId: String): KuroStorageReadResult? =
        connection.prepareStatement("SELECT payload FROM projects WHERE project_id = ?").use { statement ->
            statement.setString(1, projectId)
            statement.executeQuery().use { result ->
                if (!result.next()) null else decodeProject(result.getString(1))
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

    private fun activeProjectId(connection: Connection): String? =
        connection.prepareStatement("SELECT active_project_id FROM app_settings WHERE singleton_id = 1").use { statement ->
            statement.executeQuery().use { result ->
                if (result.next()) result.getString(1) else null
            }
        }
}
