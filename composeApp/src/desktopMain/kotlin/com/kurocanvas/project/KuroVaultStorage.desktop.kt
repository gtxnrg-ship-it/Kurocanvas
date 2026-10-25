package com.kurocanvas.project

import java.io.File
import java.sql.Connection

actual fun createKuroVaultStorage(rootPath: String): KuroVaultStorage =
    SqliteKuroVaultStorage(rootPath)

private class SqliteKuroVaultStorage(private val rootPath: String) : KuroVaultStorage {
    private val databaseFile = File(rootPath, KURO_DATABASE_NAME)
    private val legacyFile = File(rootPath, "vault-index.json")

    override fun load(): KuroVaultReadResult {
        if (!databaseFile.exists() && !legacyFile.exists()) return KuroVaultReadResult(KuroVaultIndex())
        try {
            val connection = openKuroDatabase(rootPath)
            val stored = try { readStoredIndex(connection) } finally { connection.close() }
            if (stored != null) return stored
        } catch (error: Exception) {
            return KuroVaultReadResult(
                index = KuroVaultIndex(),
                warning = "The Vault data was not changed because the SQLite database could not be read. " +
                    (error.message ?: error::class.simpleName ?: "storage error")
            )
        }

        if (!legacyFile.exists()) return KuroVaultReadResult(KuroVaultIndex())
        val legacyIndex = try {
            KuroVaultCodec.decode(legacyFile.readText(Charsets.UTF_8))
        } catch (error: Exception) {
            return KuroVaultReadResult(
                index = KuroVaultIndex(),
                warning = "The legacy Vault index could not be imported and was left untouched. " +
                    (error.message ?: error::class.simpleName ?: "invalid data")
            )
        }
        val migrated = save(legacyIndex)
        return if (migrated.success) KuroVaultReadResult(legacyIndex) else KuroVaultReadResult(
            index = legacyIndex,
            warning = "The legacy Vault index was loaded, but migration to SQLite failed. The original file was preserved. " +
                migrated.message
        )
    }

    override fun save(index: KuroVaultIndex): KuroVaultWriteResult {
        return try {
            val connection = openKuroDatabase(rootPath)
            try {
                connection.autoCommit = false
                try {
                    connection.prepareStatement(
                        "INSERT INTO vault_index (singleton_id, schema_version, payload, updated_at_epoch_ms) " +
                            "VALUES (1, ?, ?, ?) ON CONFLICT(singleton_id) DO UPDATE SET " +
                            "schema_version = excluded.schema_version, payload = excluded.payload, " +
                            "updated_at_epoch_ms = excluded.updated_at_epoch_ms"
                    ).use { statement ->
                        statement.setInt(1, index.schemaVersion)
                        statement.setString(2, KuroVaultCodec.encode(index))
                        statement.setLong(3, System.currentTimeMillis())
                        if (statement.executeUpdate() != 1) {
                            throw IllegalStateException("SQLite did not store the Vault index.")
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
            KuroVaultWriteResult(true, "Vault index saved to SQLite.")
        } catch (error: Exception) {
            KuroVaultWriteResult(
                false,
                "Could not save the Vault index to SQLite: " +
                    (error.message ?: error::class.simpleName ?: "storage error")
            )
        }
    }

    private fun readStoredIndex(connection: Connection): KuroVaultReadResult? =
        connection.prepareStatement("SELECT payload FROM vault_index WHERE singleton_id = 1").use { statement ->
            statement.executeQuery().use { result ->
                if (!result.next()) return null
                try {
                    KuroVaultReadResult(KuroVaultCodec.decode(result.getString(1)))
                } catch (error: Exception) {
                    KuroVaultReadResult(
                        index = KuroVaultIndex(),
                        warning = "The stored Vault index is invalid and was preserved. " +
                            (error.message ?: error::class.simpleName ?: "invalid data")
                    )
                }
            }
        }
}
