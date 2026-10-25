package com.kurocanvas.project

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

actual fun createKuroLocalModelRuntime(rootPath: String): KuroLocalModelRuntime =
    LiteRtKuroLocalModelRuntime(rootPath)

private class LiteRtKuroLocalModelRuntime(rootPath: String) : KuroLocalModelRuntime {
    private val cacheDirectory = File(rootPath, "model-cache")
    private val operationLock = Mutex()
    @Volatile private var engine: Engine? = null
    @Volatile private var currentPath: String? = null

    override val loadedModelPath: String?
        get() = currentPath

    override suspend fun load(modelPath: String): KuroModelRuntimeResult = operationLock.withLock {
        withContext(Dispatchers.Default) {
            val model = File(modelPath)
            if (!model.isFile) {
                return@withContext KuroModelRuntimeResult(
                    success = false,
                    message = "The local model file does not exist. Download it before loading."
                )
            }
            unloadEngine()
            var candidate: Engine? = null
            try {
                if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
                    throw IllegalStateException("Could not create the local model cache directory.")
                }
                candidate = Engine(
                    EngineConfig(
                        modelPath = model.absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = cacheDirectory.absolutePath
                    )
                )
                candidate.initialize()
                engine = candidate
                currentPath = model.absolutePath
                KuroModelRuntimeResult(
                    success = true,
                    message = "Local model loaded. Inference runs on this device."
                )
            } catch (cancelled: CancellationException) {
                runCatching { candidate?.close() }
                throw cancelled
            } catch (error: Exception) {
                runCatching { candidate?.close() }
                engine = null
                currentPath = null
                KuroModelRuntimeResult(
                    success = false,
                    message = "The local model could not be loaded: ${error.message ?: error::class.simpleName ?: "runtime error"}"
                )
            }
        }
    }

    override suspend fun generate(
        systemInstruction: String,
        userPrompt: String,
        onPartialText: (String) -> Unit
    ): KuroModelRuntimeResult = operationLock.withLock {
        val activeEngine = engine ?: return@withLock KuroModelRuntimeResult(
            success = false,
            message = "Load a local model before sending a message."
        )
        withContext(Dispatchers.Default) {
            try {
                val transcript = StringBuilder()
                activeEngine.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(systemInstruction)
                    )
                ).use { conversation ->
                    conversation.sendMessageAsync(userPrompt).collect { message ->
                        val chunk = message.toString()
                        if (chunk.isNotEmpty()) {
                            transcript.append(chunk)
                            onPartialText(chunk)
                        }
                    }
                }
                KuroModelRuntimeResult(
                    success = true,
                    text = transcript.toString(),
                    message = "Local generation completed."
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                KuroModelRuntimeResult(
                    success = false,
                    text = "",
                    message = "Local generation failed: ${error.message ?: error::class.simpleName ?: "runtime error"}"
                )
            }
        }
    }

    override suspend fun unload(): KuroModelRuntimeResult = operationLock.withLock {
        withContext(Dispatchers.Default) {
            unloadEngine()
            KuroModelRuntimeResult(success = true, message = "Local model unloaded; model memory released.")
        }
    }

    private fun unloadEngine() {
        val old = engine
        engine = null
        currentPath = null
        runCatching { old?.close() }
    }
}
