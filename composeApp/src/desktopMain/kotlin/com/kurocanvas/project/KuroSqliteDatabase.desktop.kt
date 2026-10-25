package com.kurocanvas.project

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

internal const val KURO_DATABASE_NAME = "kuro-canvas.db"
private const val CURRENT_DATABASE_VERSION = 2

internal fun openKuroDatabase(rootPath: String): Connection {
    val directory = File(rootPath)
    if (!directory.exists() && !directory.mkdirs()) {
        throw IllegalStateException("Could not create the private Kuro data directory.")
    }
    if (!directory.isDirectory) throw IllegalStateException("The Kuro data path is not a directory.")

    Class.forName("org.sqlite.JDBC")
    val connection = DriverManager.getConnection(
        "jdbc:sqlite:" + File(directory, KURO_DATABASE_NAME).absolutePath
    )
    try {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA busy_timeout = 5000")
            statement.execute("PRAGMA journal_mode = WAL")
            statement.execute("PRAGMA synchronous = FULL")
        }
        var version = connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA user_version").use { result ->
                if (result.next()) result.getInt(1) else 0
            }
        }
        if (version > CURRENT_DATABASE_VERSION) {
            throw IllegalStateException("This database was created by a newer Kuro Canvas version.")
        }
        if (version == 0) {
            connection.autoCommit = false
            try {
                connection.createStatement().use { statement ->
                    statement.execute(
                        "CREATE TABLE project_state (" +
                            "singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1), " +
                            "schema_version INTEGER NOT NULL, project_id TEXT NOT NULL, " +
                            "revision INTEGER NOT NULL, payload TEXT NOT NULL, updated_at_epoch_ms INTEGER NOT NULL)"
                    )
                    statement.execute(
                        "CREATE TABLE vault_index (" +
                            "singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1), " +
                            "schema_version INTEGER NOT NULL, payload TEXT NOT NULL, updated_at_epoch_ms INTEGER NOT NULL)"
                    )
                    statement.execute("PRAGMA user_version = 1")
                }
                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
            version = 1
        }
        if (version == 1) migrateV1ToV2(connection)
        requireTable(connection, "projects")
        requireTable(connection, "app_settings")
        requireTable(connection, "vault_index")
        return connection
    } catch (error: Exception) {
        connection.close()
        throw error
    }
}

private fun migrateV1ToV2(connection: Connection) {
    connection.autoCommit = false
    try {
        connection.createStatement().use { statement ->
            statement.execute(
                "CREATE TABLE projects (" +
                    "project_id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, revision INTEGER NOT NULL, " +
                    "payload TEXT NOT NULL, updated_at_epoch_ms INTEGER NOT NULL)"
            )
            statement.execute(
                "CREATE TABLE app_settings (" +
                    "singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1), active_project_id TEXT)"
            )
        }
        connection.prepareStatement(
            "SELECT project_id, revision, payload, updated_at_epoch_ms FROM project_state WHERE singleton_id = 1"
        ).use { statement ->
            statement.executeQuery().use { result ->
                if (result.next()) {
                    val legacyId = result.getString(1)
                    val revision = result.getLong(2)
                    val payload = result.getString(3)
                    val updatedAt = result.getLong(4)
                    val state = KuroProjectCodec.decode(payload)
                    if (state.projectId != legacyId || state.revision != revision) {
                        throw IllegalStateException("The legacy project metadata does not match its saved payload.")
                    }
                    connection.prepareStatement(
                        "INSERT INTO projects (project_id,title,revision,payload,updated_at_epoch_ms) VALUES (?,?,?,?,?)"
                    ).use { insert ->
                        insert.setString(1, state.projectId)
                        insert.setString(2, state.title)
                        insert.setLong(3, state.revision)
                        insert.setString(4, payload)
                        insert.setLong(5, updatedAt)
                        insert.executeUpdate()
                    }
                    connection.prepareStatement(
                        "INSERT INTO app_settings (singleton_id,active_project_id) VALUES (1,?)"
                    ).use { insert ->
                        insert.setString(1, state.projectId)
                        insert.executeUpdate()
                    }
                }
            }
        }
        connection.createStatement().use { it.execute("PRAGMA user_version = " + CURRENT_DATABASE_VERSION) }
        connection.commit()
    } catch (error: Exception) {
        connection.rollback()
        throw error
    } finally {
        connection.autoCommit = true
    }
}

internal fun setActiveProject(connection: Connection, projectId: String) {
    connection.prepareStatement(
        "INSERT INTO app_settings (singleton_id, active_project_id) VALUES (1, ?) " +
            "ON CONFLICT(singleton_id) DO UPDATE SET active_project_id = excluded.active_project_id"
    ).use { statement ->
        statement.setString(1, projectId)
        statement.executeUpdate()
    }
}

private fun requireTable(connection: Connection, tableName: String) {
    val exists = connection.prepareStatement(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?"
    ).use { statement ->
        statement.setString(1, tableName)
        statement.executeQuery().use { it.next() }
    }
    if (!exists) throw IllegalStateException("The Kuro database is missing the " + tableName + " table.")
}
