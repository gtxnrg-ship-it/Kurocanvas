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

interface KuroProjectStorage {
    fun load(): KuroStorageReadResult
    fun save(state: KuroProjectState): KuroStorageWriteResult
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
