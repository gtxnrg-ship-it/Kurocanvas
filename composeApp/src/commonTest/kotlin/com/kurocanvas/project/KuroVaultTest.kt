package com.kurocanvas.project

import kotlin.test.Test
import kotlin.test.assertEquals

class KuroVaultTest {
    @Test
    fun metadataIndexRoundTripsAndKeepsLicenseUnreviewedByDefault() {
        val asset = KuroVaultAsset(
            id = "asset-abc123",
            name = "character.glb",
            kind = KuroAssetKind.MODEL_3D,
            mimeType = "model/gltf-binary",
            relativePath = "vault/assets/abc123.glb",
            sha256 = "abc123",
            byteSize = 4096L,
            importedAtEpochMillis = 123456L
        )
        val index = KuroVaultIndex(assets = listOf(asset))
        val decoded = KuroVaultCodec.decode(KuroVaultCodec.encode(index))

        assertEquals(index, decoded)
        assertEquals("UNREVIEWED", decoded.assets.single().licenseId)
    }

    @Test
    fun emptyOrOlderIndexDefaultsAreReadable() {
        assertEquals(KuroVaultIndex(), KuroVaultCodec.decode("{}"))
        assertEquals(KuroVaultIndex(), KuroVaultCodec.decode("""{"schemaVersion":1}"""))
    }

    @Test
    fun assetKindInferenceRecognizesCommonCreativeFormats() {
        assertEquals(KuroAssetKind.MODEL_3D, inferKuroAssetKind("character.glb", "application/octet-stream"))
        assertEquals(KuroAssetKind.MOTION, inferKuroAssetKind("dance.bvh", "application/octet-stream"))
        assertEquals(KuroAssetKind.BRUSH, inferKuroAssetKind("ink.abr", "application/octet-stream"))
        assertEquals(KuroAssetKind.IMAGE_2D, inferKuroAssetKind("page.ora", "application/octet-stream"))
        assertEquals(KuroAssetKind.FONT, inferKuroAssetKind("studio.otf", "application/octet-stream"))
    }
}
