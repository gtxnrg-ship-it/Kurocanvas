package com.kurocanvas.project

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

actual fun createKuroProjectStorage(rootPath: String): KuroProjectStorage =
    FileKuroProjectStorage(rootPath)

private class FileKuroProjectStorage(rootPath: String) : KuroProjectStorage {
    private val destination = File(rootPath, "project.json")

    override fun load(): KuroStorageReadResult {
        if (!destination.exists()) return KuroStorageReadResult(state = null, found = false)
        return try {
            KuroStorageReadResult(
                state = KuroProjectCodec.decode(destination.readText(Charsets.UTF_8)),
                found = true
            )
        } catch (error: Exception) {
            KuroStorageReadResult(
                state = null,
                found = true,
                warning = "Saved project was not loaded, and its file was left untouched. " +
                    "Reason: ${error.message ?: error::class.simpleName ?: "invalid data"}"
            )
        }
    }

    override fun save(state: KuroProjectState): KuroStorageWriteResult {
        val parent = destination.parentFile
            ?: return KuroStorageWriteResult(false, "The project storage directory is invalid.")
        return try {
            if (!parent.exists() && !parent.mkdirs()) {
                throw IllegalStateException("Could not create the private project directory.")
            }
            val temporary = File(parent, "${destination.name}.tmp")
            temporary.writeText(KuroProjectCodec.encode(state), Charsets.UTF_8)
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            KuroStorageWriteResult(true, "Saved locally.")
        } catch (error: Exception) {
            KuroStorageWriteResult(
                false,
                "Could not save the project: ${error.message ?: error::class.simpleName ?: "storage error"}"
            )
        }
    }
}
