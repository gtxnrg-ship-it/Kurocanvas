package com.kurocanvas.project

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

actual fun createKuroVaultStorage(rootPath: String): KuroVaultStorage =
    FileKuroVaultStorage(rootPath)

private class FileKuroVaultStorage(rootPath: String) : KuroVaultStorage {
    private val destination = File(rootPath, "vault-index.json")

    override fun load(): KuroVaultReadResult {
        if (!destination.exists()) return KuroVaultReadResult(KuroVaultIndex())
        return try {
            KuroVaultReadResult(KuroVaultCodec.decode(destination.readText(Charsets.UTF_8)))
        } catch (error: Exception) {
            KuroVaultReadResult(
                index = KuroVaultIndex(),
                warning = "The Vault index could not be read and was preserved. " +
                    (error.message ?: error::class.simpleName ?: "invalid data")
            )
        }
    }

    override fun save(index: KuroVaultIndex): KuroVaultWriteResult {
        val parent = destination.parentFile
            ?: return KuroVaultWriteResult(false, "The Vault storage directory is invalid.")
        return try {
            if (!parent.exists() && !parent.mkdirs()) {
                throw IllegalStateException("Could not create the local Vault directory.")
            }
            val temporary = File(parent, "${destination.name}.tmp")
            temporary.writeText(KuroVaultCodec.encode(index), Charsets.UTF_8)
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            KuroVaultWriteResult(true, "Vault index saved locally.")
        } catch (error: Exception) {
            KuroVaultWriteResult(
                false,
                "Could not save the Vault index: " +
                    (error.message ?: error::class.simpleName ?: "storage error")
            )
        }
    }
}
