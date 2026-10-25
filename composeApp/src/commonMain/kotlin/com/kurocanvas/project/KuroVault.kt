package com.kurocanvas.project

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
enum class KuroAssetKind {
    IMAGE_2D, MODEL_3D, MOTION, BRUSH, TEXTURE, AUDIO, VIDEO, FONT, TEMPLATE, OTHER
}

@Serializable
data class KuroVaultAsset(
    val id: String,
    val name: String,
    val kind: KuroAssetKind,
    val mimeType: String,
    val relativePath: String,
    val sha256: String,
    val byteSize: Long,
    val tags: List<String> = emptyList(),
    /** Must be reviewed before this asset is redistributed or published in the Shop. */
    val licenseId: String = "UNREVIEWED",
    val licenseUrl: String = "",
    val attribution: String = "",
    val sourceReference: String = "",
    val importedAtEpochMillis: Long
)

@Serializable
data class KuroVaultIndex(
    val schemaVersion: Int = 1,
    val assets: List<KuroVaultAsset> = emptyList()
)

data class KuroVaultReadResult(
    val index: KuroVaultIndex,
    val warning: String? = null
)

data class KuroVaultWriteResult(
    val success: Boolean,
    val message: String
)

object KuroVaultCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(index: KuroVaultIndex): String = json.encodeToString(index)

    fun decode(contents: String): KuroVaultIndex = json.decodeFromString(contents)
}

interface KuroVaultStorage {
    fun load(): KuroVaultReadResult
    fun save(index: KuroVaultIndex): KuroVaultWriteResult
}

expect fun createKuroVaultStorage(rootPath: String): KuroVaultStorage

@Composable
expect fun KuroPlatformAssetImportButton(
    rootPath: String,
    enabled: Boolean,
    onResult: (KuroVaultAsset?, String?) -> Unit
)

fun inferKuroAssetKind(fileName: String, mimeType: String): KuroAssetKind {
    val name = fileName.lowercase()
    val mime = mimeType.lowercase()
    return when {
        name.endsWith(".glb") || name.endsWith(".gltf") || name.endsWith(".obj") ||
            name.endsWith(".fbx") || name.endsWith(".stl") || name.endsWith(".blend") ->
            KuroAssetKind.MODEL_3D
        name.endsWith(".pose") || name.endsWith(".bvh") || name.endsWith(".mocap") ||
            name.endsWith(".npy") -> KuroAssetKind.MOTION
        name.endsWith(".abr") || name.endsWith(".brush") || name.endsWith(".myb") ->
            KuroAssetKind.BRUSH
        name.endsWith(".pat") || name.endsWith(".texture") ->
            KuroAssetKind.TEXTURE
        name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".woff") ||
            name.endsWith(".woff2") -> KuroAssetKind.FONT
        mime.startsWith("image/") || name.endsWith(".ora") || name.endsWith(".kra") ||
            name.endsWith(".psd") || name.endsWith(".clip") -> KuroAssetKind.IMAGE_2D
        mime.startsWith("audio/") -> KuroAssetKind.AUDIO
        mime.startsWith("video/") -> KuroAssetKind.VIDEO
        name.endsWith(".zip") || name.endsWith(".kuroasset") -> KuroAssetKind.TEMPLATE
        else -> KuroAssetKind.OTHER
    }
}
