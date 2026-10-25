package com.kurocanvas.project

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

internal const val KURO_DATABASE_NAME = "kuro-canvas.db"
private const val CURRENT_DATABASE_VERSION = 1

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
        val version = connection.createStatement().use { statement ->
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
                    statement.execute("PRAGMA user_version = " + CURRENT_DATABASE_VERSION)
                }
                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        } else {
            requireTable(connection, "project_state")
            requireTable(connection, "vault_index")
        }
        return connection
    } catch (error: Exception) {
        connection.close()
        throw error
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
