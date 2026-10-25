package com.kurocanvas.project

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KuroSqliteStorageTest {
    @Test
    fun projectAndVaultRoundTripThroughSharedSqliteDatabase() {
        val root = Files.createTempDirectory("kuro-sqlite-roundtrip").toFile()
        try {
            val project = KuroProjectState.empty(
                projectId = "sqlite-roundtrip-project",
                title = "SQLite round-trip"
            ).copy(revision = 7L)

            val savedProject = createKuroProjectStorage(root.absolutePath).save(project)
            assertTrue(savedProject.success, savedProject.message)
            val loadedProject = createKuroProjectStorage(root.absolutePath).load()
            assertEquals(project, loadedProject.state)
            assertEquals(null, loadedProject.warning)

            val vault = KuroVaultIndex(
                assets = listOf(
                    KuroVaultAsset(
                        id = "asset-test-1",
                        name = "test-model.glb",
                        kind = KuroAssetKind.MODEL_3D,
                        mimeType = "model/gltf-binary",
                        relativePath = "assets/sha256/test-model.glb",
                        sha256 = "a".repeat(64),
                        byteSize = 1234L,
                        tags = listOf("character", "3d"),
                        licenseId = "CC-BY-4.0",
                        licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
                        attribution = "Test creator",
                        sourceReference = "fixture",
                        importedAtEpochMillis = 100L
                    )
                )
            )
            val savedVault = createKuroVaultStorage(root.absolutePath).save(vault)
            assertTrue(savedVault.success, savedVault.message)
            val loadedVault = createKuroVaultStorage(root.absolutePath).load()
            assertEquals(vault, loadedVault.index)
            assertEquals(null, loadedVault.warning)
            assertTrue(File(root, KURO_DATABASE_NAME).isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun legacyJsonProjectIsImportedAndOriginalIsPreserved() {
        val root = Files.createTempDirectory("kuro-sqlite-migration").toFile()
        try {
            val expected = KuroProjectState.empty(
                projectId = "legacy-project",
                title = "From legacy JSON"
            )
            val legacyFile = File(root, "project.json")
            legacyFile.writeText(KuroProjectCodec.encode(expected), Charsets.UTF_8)

            val loaded = createKuroProjectStorage(root.absolutePath).load()
            assertEquals(expected, loaded.state)
            assertEquals(null, loaded.warning)
            assertTrue(legacyFile.isFile, "Migration must not delete the original JSON file.")
            assertTrue(File(root, KURO_DATABASE_NAME).isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun legacyVaultIndexIsImportedAndOriginalIsPreserved() {
        val root = Files.createTempDirectory("kuro-sqlite-vault-migration").toFile()
        try {
            val expected = KuroVaultIndex()
            val legacyFile = File(root, "vault-index.json")
            legacyFile.writeText(KuroVaultCodec.encode(expected), Charsets.UTF_8)

            val loaded = createKuroVaultStorage(root.absolutePath).load()
            assertEquals(expected, loaded.index)
            assertEquals(null, loaded.warning)
            assertTrue(legacyFile.isFile, "Migration must not delete the original JSON file.")
            assertTrue(File(root, KURO_DATABASE_NAME).isFile)
        } finally {
            root.deleteRecursively()
        }
    }
}
