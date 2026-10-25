package com.kurocanvas.project

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File

actual fun createKuroVaultStorage(rootPath: String): KuroVaultStorage =
    SqliteKuroVaultStorage(rootPath)

private class SqliteKuroVaultStorage(private val rootPath: String) : KuroVaultStorage {
    private val databaseFile = File(rootPath, KURO_DATABASE_NAME)
    private val legacyFile = File(rootPath, "vault-index.json")

    override fun load(): KuroVaultReadResult {
        if (!databaseFile.exists() && !legacyFile.exists()) return KuroVaultReadResult(KuroVaultIndex())
        try {
            val database = openKuroDatabase(rootPath)
            val stored = try { readStoredIndex(database) } finally { database.close() }
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
            val database = openKuroDatabase(rootPath)
            try {
                database.beginTransaction()
                try {
                    val values = ContentValues().apply {
                        put("singleton_id", 1)
                        put("schema_version", index.schemaVersion)
                        put("payload", KuroVaultCodec.encode(index))
                        put("updated_at_epoch_ms", System.currentTimeMillis())
                    }
                    val rowId = database.insertWithOnConflict(
                        "vault_index", null, values, SQLiteDatabase.CONFLICT_REPLACE
                    )
                    if (rowId < 0L) throw IllegalStateException("SQLite did not store the Vault index.")
                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
            } finally {
                database.close()
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

    private fun readStoredIndex(database: SQLiteDatabase): KuroVaultReadResult? {
        val cursor = database.rawQuery("SELECT payload FROM vault_index WHERE singleton_id = 1", null)
        return try {
            if (!cursor.moveToFirst()) return null
            try {
                KuroVaultReadResult(KuroVaultCodec.decode(cursor.getString(0)))
            } catch (error: Exception) {
                KuroVaultReadResult(
                    index = KuroVaultIndex(),
                    warning = "The stored Vault index is invalid and was preserved. " +
                        (error.message ?: error::class.simpleName ?: "invalid data")
                )
            }
        } finally {
            cursor.close()
        }
    }
}
