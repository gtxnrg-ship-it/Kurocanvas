package com.kurocanvas.project

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

actual fun findInstalledKuroModelPath(
    rootPath: String,
    manifest: KuroModelManifest
): String? {
    val file = File(File(rootPath, "models"), manifest.fileName)
    return file.absolutePath.takeIf {
        file.isFile && file.length() == manifest.expectedSizeBytes &&
            runCatching { sha256(file).equals(manifest.sha256, ignoreCase = true) }.getOrDefault(false)
    }
}

actual suspend fun downloadKuroModel(
    rootPath: String,
    manifest: KuroModelManifest,
    onProgress: (KuroModelDownloadProgress) -> Unit
): KuroModelDownloadResult = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        require(manifest.expectedSizeBytes > 0L) { "The model manifest has an invalid size." }
        require(manifest.sha256.matches(Regex("[a-f0-9]{64}"))) { "The model manifest has an invalid SHA-256 digest." }
        require(manifest.downloadUrl.startsWith("https://")) { "Model downloads must use HTTPS." }

        val coroutineContext = currentCoroutineContext()
        val directory = File(rootPath, "models")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Could not create the private model directory.")
        }
        val destination = File(directory, manifest.fileName)
        val partial = File(directory, manifest.fileName + ".part")

        if (destination.isFile && destination.length() == manifest.expectedSizeBytes) {
            val existingHash = sha256(destination)
            if (existingHash.equals(manifest.sha256, ignoreCase = true)) {
                return@withContext KuroModelDownloadResult(
                    success = true,
                    filePath = destination.absolutePath,
                    message = "The verified model is already downloaded."
                )
            }
            destination.delete()
        }

        var offset = if (partial.isFile) partial.length() else 0L
        if (offset < 0L || offset >= manifest.expectedSizeBytes) {
            partial.delete()
            offset = 0L
        }

        connection = (URL(manifest.downloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "KuroCanvas/0.1.0")
            setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
        }

        val response = connection.responseCode
        val append = offset > 0L && response == HttpURLConnection.HTTP_PARTIAL
        if (response !in 200..299) {
            throw IllegalStateException("Model download returned HTTP $response.")
        }
        if (!append) {
            offset = 0L
            partial.delete()
        }

        val initialOffset = offset
        onProgress(KuroModelDownloadProgress(initialOffset, manifest.expectedSizeBytes))
        var downloadedThisSession = 0L
        var lastReportedBucket = initialOffset / (2L * 1024L * 1024L)
        connection.inputStream.use { input ->
            FileOutputStream(partial, append).use { output ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    downloadedThisSession += count.toLong()
                    val downloaded = initialOffset + downloadedThisSession
                    if (downloaded > manifest.expectedSizeBytes) {
                        throw IllegalStateException("The server sent more bytes than declared in the verified model manifest.")
                    }
                    output.write(buffer, 0, count)
                    val currentBucket = downloaded / (2L * 1024L * 1024L)
                    if (currentBucket != lastReportedBucket || downloaded == manifest.expectedSizeBytes) {
                        onProgress(KuroModelDownloadProgress(downloaded, manifest.expectedSizeBytes))
                        lastReportedBucket = currentBucket
                    }
                }
                output.fd.sync()
            }
        }

        if (partial.length() != manifest.expectedSizeBytes) {
            return@withContext KuroModelDownloadResult(
                success = false,
                message = "Download paused at ${partial.length()} of ${manifest.expectedSizeBytes} bytes. Retry to resume."
            )
        }

        val actualHash = sha256(partial)
        if (!actualHash.equals(manifest.sha256, ignoreCase = true)) {
            partial.delete()
            throw SecurityException("The model SHA-256 check failed. The downloaded file was discarded.")
        }

        try {
            Files.move(
                partial.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        KuroModelDownloadResult(
            success = true,
            filePath = destination.absolutePath,
            message = "Model download verified and installed.",
            resumed = initialOffset > 0L
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        KuroModelDownloadResult(
            success = false,
            message = error.message ?: error::class.simpleName ?: "Model download failed."
        )
    } finally {
        connection?.disconnect()
    }
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
        val buffer = ByteArray(128 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
