package com.kurocanvas.project

import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun projectCatalogKeepsStoriesIsolatedAndPersistsTheActiveSelection() {
        val root = Files.createTempDirectory("kuro-project-catalog").toFile()
        try {
            val first = KuroProjectState.empty(
                projectId = "story-a",
                title = "Story A"
            ).copy(storyTitle = "A separate world")
            val second = KuroProjectState.empty(
                projectId = "story-b",
                title = "Story B"
            ).copy(
                storyTitle = "A different world",
                storyEntities = listOf(KuroStoryEntity("b-hero", "B Hero"))
            )
            val storage = createKuroProjectStorage(root.absolutePath)
            assertTrue(storage.save(first).success)
            assertTrue(storage.save(second).success)

            val catalog = storage.listProjects()
            assertEquals(setOf("story-a", "story-b"), catalog.projects.map { it.projectId }.toSet())
            assertEquals("story-b", catalog.activeProjectId)

            val activated = createKuroProjectStorage(root.absolutePath).activateProject("story-a")
            assertEquals(first, activated.state)
            assertEquals("story-a", createKuroProjectStorage(root.absolutePath).listProjects().activeProjectId)
            assertEquals(first, createKuroProjectStorage(root.absolutePath).load().state)

            val switchedBack = createKuroProjectStorage(root.absolutePath).activateProject("story-b")
            assertEquals(second, switchedBack.state)
            assertEquals("B Hero", createKuroProjectStorage(root.absolutePath).load().state?.storyEntities?.single()?.name)
            assertFalse(first.storyEntities.any { it.id == "b-hero" })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun versionOneSqliteProjectMigratesIntoTheMultiProjectCatalog() {
        val root = Files.createTempDirectory("kuro-sqlite-v1-migration").toFile()
        try {
            val expected = KuroProjectState.empty(
                projectId = "pre-catalog-project",
                title = "Existing before project catalog"
            ).copy(revision = 4L)
            val databaseFile = File(root, KURO_DATABASE_NAME)
            DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        "CREATE TABLE project_state (" +
                            "singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1), " +
                            "schema_version INTEGER NOT NULL, project_id TEXT NOT NULL, " +
                            "revision INTEGER NOT NULL, payload TEXT NOT NULL, updated_at_epoch_ms INTEGER NOT NULL)"
                    )
                    statement.execute(
                        "CREATE TABLE vault_index (" +
                            "singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1), " +
                            "schema_version INTEGER NOT NULL, payload TEXT NOT NULL, updated_at_epoch_ms INTEGER NOT NULL)"
                    )
                    statement.execute("PRAGMA user_version = 1")
                }
                connection.prepareStatement(
                    "INSERT INTO project_state (singleton_id,schema_version,project_id,revision,payload,updated_at_epoch_ms) " +
                        "VALUES (1,1,?,?,?,123)"
                ).use { statement ->
                    statement.setString(1, expected.projectId)
                    statement.setLong(2, expected.revision)
                    statement.setString(3, KuroProjectCodec.encode(expected))
                    statement.executeUpdate()
                }
            }

            val loaded = createKuroProjectStorage(root.absolutePath).load()
            assertEquals(expected, loaded.state)
            assertEquals(null, loaded.warning)
            val catalog = createKuroProjectStorage(root.absolutePath).listProjects()
            assertEquals(listOf(expected.projectId), catalog.projects.map { it.projectId })
            assertEquals(expected.projectId, catalog.activeProjectId)
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
