package com.kurocanvas.project

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLConnection
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import javax.swing.JFileChooser

@Composable
actual fun KuroPlatformAssetImportButton(
    rootPath: String,
    enabled: Boolean,
    onResult: (KuroVaultAsset?, String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val latestOnResult by rememberUpdatedState(onResult)
    Button(
        enabled = enabled,
        onClick = {
            val chooser = JFileChooser()
            chooser.dialogTitle = "Import an asset into Kuro Canvas Vault"
            chooser.isMultiSelectionEnabled = false
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val selectedFile = chooser.selectedFile
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        try {
                            importDesktopAsset(rootPath, selectedFile) to null
                        } catch (error: Exception) {
                            null to (error.message ?: "The selected file could not be imported.")
                        }
                    }
                    latestOnResult(result.first, result.second)
                }
            }
        }
    ) { Text("Import local asset") }
}

private fun importDesktopAsset(rootPath: String, sourceFile: File): KuroVaultAsset {
    if (!sourceFile.isFile || !sourceFile.canRead()) {
        throw IllegalArgumentException("Choose a readable file.")
    }
    val mimeType = Files.probeContentType(sourceFile.toPath())
        ?: URLConnection.guessContentTypeFromName(sourceFile.name)
        ?: "application/octet-stream"
    val assetDirectory = File(rootPath, "vault/assets")
    if (!assetDirectory.exists() && !assetDirectory.mkdirs()) {
        throw IllegalStateException("Could not create the local asset directory.")
    }
    val temporary = File.createTempFile("asset-import-", ".part", assetDirectory)
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        FileInputStream(sourceFile).use { input ->
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    size += count
                    if (size > 2147483648L) {
                        throw IllegalArgumentException("This import is larger than the current 2 GiB per-asset limit.")
                    }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
        }
        val sha = digest.digest().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        val extension = sourceFile.name.substringAfterLast('.', "")
            .takeIf { it.isNotBlank() && it.length <= 10 && it.all { char -> char.isLetterOrDigit() } }
            ?.lowercase(Locale.ROOT)
            ?.let { ".$it" } ?: ""
        val existing = assetDirectory.listFiles()?.firstOrNull { it.name == sha || it.name.startsWith("$sha.") }
        val destination = existing ?: File(assetDirectory, "$sha$extension")
        if (existing == null) {
            try {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } else {
            temporary.delete()
        }
        return KuroVaultAsset(
            id = "asset-$sha",
            name = sourceFile.name,
            kind = inferKuroAssetKind(sourceFile.name, mimeType),
            mimeType = mimeType,
            relativePath = "vault/assets/${destination.name}",
            sha256 = sha,
            byteSize = destination.length(),
            importedAtEpochMillis = System.currentTimeMillis()
        )
    } catch (error: Exception) {
        temporary.delete()
        throw error
    }
}
