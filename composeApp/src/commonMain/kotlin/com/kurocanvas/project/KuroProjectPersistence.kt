package com.kurocanvas.project

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class KuroStorageReadResult(
    val state: KuroProjectState?,
    val found: Boolean,
    val warning: String? = null
)

data class KuroStorageWriteResult(
    val success: Boolean,
    val message: String
)

data class KuroProjectSummary(
    val projectId: String,
    val title: String,
    val revision: Long,
    val updatedAtEpochMillis: Long
)

data class KuroProjectCatalogReadResult(
    val projects: List<KuroProjectSummary>,
    val activeProjectId: String? = null,
    val warning: String? = null
)

interface KuroProjectStorage {
    /** Loads the currently active project; if none is selected, the most recently saved project. */
    fun load(): KuroStorageReadResult
    /** Saves this project and makes it the active project. */
    fun save(state: KuroProjectState): KuroStorageWriteResult
    /** Lists metadata for all projects without switching the active one. */
    fun listProjects(): KuroProjectCatalogReadResult
    /** Switches to an existing project. Missing or invalid projects are never replaced. */
    fun activateProject(projectId: String): KuroStorageReadResult
}

object KuroProjectCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(state: KuroProjectState): String = json.encodeToString(state)

    fun decode(contents: String): KuroProjectState = json.decodeFromString(contents)
}

/** Creates storage rooted in the app-owned private-data directory for the current platform. */
expect fun createKuroProjectStorage(rootPath: String): KuroProjectStorage
