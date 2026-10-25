package com.kurocanvas.project

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale

@Composable
actual fun KuroPlatformAssetImportButton(
    rootPath: String,
    enabled: Boolean,
    onResult: (KuroVaultAsset?, String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    try {
                        importAndroidAsset(context, rootPath, uri) to null
                    } catch (error: Exception) {
                        null to (error.message ?: "The selected file could not be imported.")
                    }
                }
                latestOnResult(result.first, result.second)
            }
        }
    }
    Button(
        enabled = enabled,
        onClick = { launcher.launch(arrayOf("*/*")) }
    ) { Text("Import local asset") }
}

private fun importAndroidAsset(context: Context, rootPath: String, uri: Uri): KuroVaultAsset {
    val resolver = context.contentResolver
    val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "imported-asset"
    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
    val source = resolver.openInputStream(uri)
        ?: throw IllegalStateException("Android could not open the selected file.")
    return copyAssetIntoVault(rootPath, displayName, mimeType, source)
}

private fun copyAssetIntoVault(
    rootPath: String,
    name: String,
    mimeType: String,
    source: InputStream
): KuroVaultAsset {
    val assetDirectory = File(rootPath, "vault/assets")
    if (!assetDirectory.exists() && !assetDirectory.mkdirs()) {
        source.close()
        throw IllegalStateException("Could not create the local asset directory.")
    }
    val temporary = File.createTempFile("asset-import-", ".part", assetDirectory)
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        source.use { input ->
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
        val extension = name.substringAfterLast('.', "")
            .takeIf { it.isNotBlank() && it.length <= 10 && it.all { char -> char.isLetterOrDigit() } }
            ?.lowercase(Locale.ROOT)
            ?.let { ".$it" } ?: ""
        val existing = assetDirectory.listFiles()?.firstOrNull { it.name.startsWith("$sha.") }
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
            name = name,
            kind = inferKuroAssetKind(name, mimeType),
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
