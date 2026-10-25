package com.kurocanvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kurocanvas.project.AddCanonFact
import com.kurocanvas.project.AddCanvasLayer
import com.kurocanvas.project.AddCanvasStroke
import com.kurocanvas.project.AddStoryEntity
import com.kurocanvas.project.AddStoryEvent
import com.kurocanvas.project.AddStoryRelationship
import com.kurocanvas.project.KuroLayer
import com.kurocanvas.project.KuroContextRequest
import com.kurocanvas.project.KuroCharacterProfileResolver
import com.kurocanvas.project.KuroProfileFieldStatus
import com.kurocanvas.project.KuroModelCatalog
import com.kurocanvas.project.KuroModelDownloadProgress
import com.kurocanvas.project.KuroModelManifest
import com.kurocanvas.project.KuroNativeActionParser
import com.kurocanvas.project.KuroNativeActionProposal
import com.kurocanvas.project.KuroNativeActionDispatcher
import com.kurocanvas.project.KuroNativeActionType
import com.kurocanvas.project.KuroPromptBuilder
import com.kurocanvas.project.KuroStoryContextCompiler
import com.kurocanvas.project.createKuroLocalModelRuntime
import com.kurocanvas.project.downloadKuroModel
import com.kurocanvas.project.findInstalledKuroModelPath
import com.kurocanvas.project.KuroPlatformAssetImportButton
import com.kurocanvas.project.KuroVaultAsset
import com.kurocanvas.project.KuroVaultIndex
import com.kurocanvas.project.createKuroVaultStorage
import com.kurocanvas.project.AddStoryboardPanel
import com.kurocanvas.project.KuroOperationBroker
import com.kurocanvas.project.KuroCanonFact
import com.kurocanvas.project.KuroProjectState
import com.kurocanvas.project.KuroStroke
import com.kurocanvas.project.KuroStrokePoint
import com.kurocanvas.project.KuroStoryEntity
import com.kurocanvas.project.KuroStoryEvent
import com.kurocanvas.project.KuroStoryRelationship
import com.kurocanvas.project.KuroStoryEntityType
import com.kurocanvas.project.createKuroProjectStorage
import com.kurocanvas.project.OperationStatus
import com.kurocanvas.project.UndoAddedCanvasLayer
import com.kurocanvas.project.UndoAddedCanvasStroke
import com.kurocanvas.project.UpdateCanvasLayer
import com.kurocanvas.project.eventsInTimeline
import com.kurocanvas.project.UndoAddedStoryboardPanel

private val Ink = Color(0xFF0A0C13)
private val Panel = Color(0xFF121522)
private val Raised = Color(0xFF191D2B)
private val Line = Color(0xFF292E40)
private val PrimaryText = Color(0xFFF2F4FA)
private val MutedText = Color(0xFF9AA3B8)
private val Violet = Color(0xFF9A82FF)
private val Cyan = Color(0xFF63D6E7)
private val Green = Color(0xFF6DDBA3)
private val Amber = Color(0xFFFFC66D)

private enum class Section(val label: String, val glyph: String) {
    Create("Create", "✦"), Story("Story", "▤"), Vault("Vault", "◈"),
    Style("Style Lab", "◉"), ThreeD("3D", "◇"), Research("Research", "⌕"), Publish("Publish", "↗")
}

private data class InkStroke(val points: List<Offset>, val color: Color, val widthRatio: Float, val layerId: String)

private fun Int.toStudioColor(): Color = Color(
    red = ((this ushr 16) and 0xFF) / 255f,
    green = ((this ushr 8) and 0xFF) / 255f,
    blue = (this and 0xFF) / 255f,
    alpha = ((this ushr 24) and 0xFF) / 255f
)

private fun Color.toArgbInt(): Int =
    ((alpha * 255f).toInt().coerceIn(0, 255) shl 24) or
        ((red * 255f).toInt().coerceIn(0, 255) shl 16) or
        ((green * 255f).toInt().coerceIn(0, 255) shl 8) or
        (blue * 255f).toInt().coerceIn(0, 255)

private fun KuroStroke.toInkStroke(): InkStroke = InkStroke(
    points = points.map { Offset(it.x, it.y) },
    color = colorArgb.toStudioColor(),
    widthRatio = widthRatio,
    layerId = layerId
)

private fun InkStroke.toKuroStroke(id: String): KuroStroke = KuroStroke(
    id = id,
    points = points.map { KuroStrokePoint(it.x, it.y) },
    colorArgb = color.toArgbInt(),
    widthRatio = widthRatio,
    layerId = layerId
)

@Composable
fun App(storageRoot: String) {
    var section by remember { mutableStateOf(Section.Create) }
    var showKuro by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val localModelManifest = KuroModelCatalog.defaultModel
    val localModelRuntime = remember(storageRoot) { createKuroLocalModelRuntime(storageRoot) }
    var localModelPath by remember(storageRoot) {
        mutableStateOf(findInstalledKuroModelPath(storageRoot, localModelManifest))
    }
    var localModelLoaded by remember(localModelRuntime) { mutableStateOf(false) }
    var modelBusy by remember { mutableStateOf(false) }
    var modelDownloadProgress by remember { mutableStateOf<KuroModelDownloadProgress?>(null) }
    var modelDownloadJob by remember { mutableStateOf<Job?>(null) }
    var modelStatusMessage by remember(storageRoot) {
        mutableStateOf(
            if (localModelPath != null) "Model installed locally; load it when you are ready."
            else "No local model downloaded yet. Creative tools work without AI."
        )
    }
    var kuroOutput by remember { mutableStateOf("") }
    var kuroLastPrompt by remember { mutableStateOf("") }
    var kuroAssistantStatus by remember { mutableStateOf<String?>(null) }
    var kuroGenerating by remember { mutableStateOf(false) }
    var pendingKuroAction by remember { mutableStateOf<KuroNativeActionProposal?>(null) }
    val projectStorage = remember(storageRoot) { createKuroProjectStorage(storageRoot) }
    val initialLoad = remember(projectStorage) { projectStorage.load() }
    val vaultStorage = remember(storageRoot) { createKuroVaultStorage(storageRoot) }
    val initialVaultLoad = remember(vaultStorage) { vaultStorage.load() }
    var vaultIndex by remember(vaultStorage) { mutableStateOf(initialVaultLoad.index) }
    var vaultWarning by remember(vaultStorage) { mutableStateOf(initialVaultLoad.warning) }
    var vaultMessage by remember(vaultStorage) { mutableStateOf<String?>(null) }
    val vaultWritesAllowed = vaultWarning == null
    val onVaultImportResult: (KuroVaultAsset?, String?) -> Unit = { asset, error ->
        if (error != null) {
            vaultMessage = error
        } else if (asset != null && vaultWritesAllowed) {
            if (vaultIndex.assets.any { it.id == asset.id }) {
                vaultMessage = "Already in your Vault. Matching file content was deduplicated by SHA-256."
            } else {
                val nextIndex = vaultIndex.copy(
                    assets = (vaultIndex.assets + asset).sortedBy { it.name.lowercase() }
                )
                val saved = vaultStorage.save(nextIndex)
                if (saved.success) {
                    vaultIndex = nextIndex
                    vaultMessage = "Imported ${asset.name}. License status is unreviewed; verify rights before redistribution."
                } else {
                    vaultWarning = saved.message
                    vaultMessage = saved.message
                }
            }
        } else if (asset != null) {
            vaultMessage = "The Vault index needs repair. Import was not indexed, and the existing index was preserved."
        }
    }
    val onVaultMetadataUpdate: (KuroVaultAsset) -> Unit = { updated ->
        if (!vaultWritesAllowed) {
            vaultMessage = "The Vault index needs repair before metadata can be changed."
        } else {
            val nextIndex = vaultIndex.copy(
                assets = vaultIndex.assets.map { if (it.id == updated.id) updated else it }
            )
            val saved = vaultStorage.save(nextIndex)
            if (saved.success) {
                vaultIndex = nextIndex
                vaultMessage = "Saved metadata for ${updated.name}."
            } else {
                vaultWarning = saved.message
                vaultMessage = saved.message
            }
        }
    }
    var project by remember(projectStorage) {
        mutableStateOf((initialLoad.state ?: KuroProjectState.empty()).let {
            if (it.layers.isEmpty()) it.copy(layers = listOf(KuroLayer.default())) else it
        })
    }
    var selectedLayerId by remember(projectStorage) {
        mutableStateOf(project.layers.first().id)
    }
    var storageWarning by remember(projectStorage) { mutableStateOf(initialLoad.warning) }
    val projectWritesAllowed = initialLoad.warning == null
    val operationBroker = remember { KuroOperationBroker() }
    val commitProject: (KuroProjectState) -> Unit = { next ->
        project = next
        if (projectWritesAllowed) {
            val saved = projectStorage.save(next)
            storageWarning = if (saved.success) null else saved.message
        }
    }
    val addStoryboardPanel: () -> Unit = {
        val operation = AddStoryboardPanel(
            operationId = "op-add-panel-${project.revision + 1}",
            projectId = project.projectId,
            expectedRevision = project.revision,
            actor = "user"
        )
        val result = operationBroker.execute(project, operation)
        if (result.status == OperationStatus.COMMITTED) commitProject(result.project)
    }
    val applyKuroAction: (KuroNativeActionProposal) -> OperationStatus = { proposal ->
        val result = KuroNativeActionDispatcher.apply(project, proposal, operationBroker)
        if (result.status == OperationStatus.COMMITTED) commitProject(result.project)
        result.status
    }
    val undoLastStoryboardPanel: () -> Unit = {
        val undonePanelIds = project.auditLog
            .filter { it.action == "undo_add_storyboard_panel" }
            .map { it.targetId }
            .toSet()
        val lastAdd = project.auditLog.lastOrNull {
            it.action == "add_storyboard_panel" && it.targetId !in undonePanelIds
        }
        if (lastAdd != null) {
            val result = operationBroker.execute(
                project,
                UndoAddedStoryboardPanel(
                    operationId = "op-undo-panel-${project.revision + 1}",
                    projectId = project.projectId,
                    expectedRevision = project.revision,
                    targetOperationId = lastAdd.operationId,
                    actor = "user"
                )
            )
            if (result.status == OperationStatus.COMMITTED) commitProject(result.project)
        }
    }
    val addCanvasLayer: () -> Unit = {
        val revision = project.revision
        val id = "layer-${revision + 1}"
        val result = operationBroker.execute(
            project,
            AddCanvasLayer(
                operationId = "op-add-layer-${revision + 1}",
                projectId = project.projectId,
                expectedRevision = revision,
                layer = KuroLayer(
                    id = id,
                    name = "Layer ${project.layers.size + 1}",
                    order = project.layers.size
                )
            )
        )
        if (result.status == OperationStatus.COMMITTED) {
            commitProject(result.project)
            selectedLayerId = id
        }
    }
    val updateCanvasLayer: (String, String?, Boolean?, Boolean?, Float?) -> OperationStatus = {
            id, name, visible, locked, opacity ->
        val revision = project.revision
        val result = operationBroker.execute(
            project,
            UpdateCanvasLayer(
                operationId = "op-update-layer-${revision + 1}",
                projectId = project.projectId,
                expectedRevision = revision,
                layerId = id,
                name = name,
                visible = visible,
                locked = locked,
                opacity = opacity
            )
        )
        if (result.status == OperationStatus.COMMITTED) commitProject(result.project)
        result.status
    }
    val removeCanvasLayer: (String) -> OperationStatus = { id ->
        val addEvent = project.auditLog.lastOrNull {
            it.action == "add_canvas_layer" && it.targetId == id
        }
        if (addEvent == null) {
            OperationStatus.INVALID_UNDO_TARGET
        } else {
            val revision = project.revision
            val result = operationBroker.execute(
                project,
                UndoAddedCanvasLayer(
                    operationId = "op-remove-layer-${revision + 1}",
                    projectId = project.projectId,
                    expectedRevision = revision,
                    targetOperationId = addEvent.operationId
                )
            )
            if (result.status == OperationStatus.COMMITTED) {
                commitProject(result.project)
                if (selectedLayerId == id) selectedLayerId = project.layers.first().id
            }
            result.status
        }
    }
    val addStoryRelationship: (String, String, String, Int, Int?, String) -> OperationStatus = {
            fromId, toId, relationshipType, fromChapter, untilChapter, branchId ->
        val revision = project.revision
        val result = operationBroker.execute(
            project,
            AddStoryRelationship(
                operationId = "op-add-relationship-${revision + 1}",
                projectId = project.projectId,
                expectedRevision = revision,
                relationship = KuroStoryRelationship(
                    id = "relationship-${revision + 1}",
                    fromEntityId = fromId,
                    toEntityId = toId,
                    relationshipType = relationshipType,
                    branchId = branchId,
                    fromChapter = fromChapter,
                    untilChapterExclusive = untilChapter
                )
            )
        )
        if (result.status == OperationStatus.COMMITTED) commitProject(result.project)
        result.status
    }
    val addStoryEvent: (String, Int, String, List<String>, String) -> OperationStatus = {
            title, chapter, description, participants, branchId ->
        val revision = project.revision
        val result = operationBroker.execute(
            project,
            AddStoryEvent(
                operationId = "op-add-event-${revision + 1}",
                projectId = project.projectId,
                expectedRevision = revision,
                event = KuroStoryEvent(
                    id = "event-${revision + 1}",
                    title = title,
                    chapter = chapter,
                    branchId = branchId,
                    description = description,
                    participantEntityIds = participants
                )
            )
        )
        if (result.status == OperationStatus.COMMITTED) commitProject(result.project)
        result.status
    }
    val addStoryEntity: (String, KuroStoryEntityType) -> OperationStatus = { name, type ->
        val nextRevision = project.revision + 1
        val result = operationBroker.execute(
            project,
            AddStoryEntity(
                operationId = "op-add-entity-$nextRevision",
                projectId = project.projectId,
                expectedRevision = project.revision,
                entity = KuroStoryEntity(
                    id = "entity-$nextRevision",
                    name = name,
                    type = type
                )
            )
        )
        if (result.status == OperationStatus.COMMITTED) commitProject(result.project)
        result.status
    }
    val addCanonFact: (String, String, String, Int, Int?, String) -> OperationStatus = {
            subjectId, predicate, value, fromChapter, untilChapter, branchId ->
        val nextRevision = project.revision + 1
        val result = operationBroker.execute(
            project,
            AddCanonFact(
                operationId = "op-add-canon-$nextRevision",
                projectId = project.projectId,
                expectedRevision = project.revision,
                fact = KuroCanonFact(
                    id = "fact-$nextRevision",
                    subjectId = subjectId,
                    predicate = predicate,
                    value = value,
                    branchId = branchId,
                    fromChapter = fromChapter,
                    untilChapterExclusive = untilChapter
                )
            )
        )
        if (result.status == OperationStatus.COMMITTED) commitProject(result.project)
        result.status
    }
    val downloadLocalModel: () -> Unit = {
        if (!modelBusy && !localModelLoaded && localModelPath == null) {
            modelBusy = true
            modelDownloadProgress = KuroModelDownloadProgress(0L, localModelManifest.expectedSizeBytes)
            modelStatusMessage = "Downloading the local model. You can cancel and resume later."
            modelDownloadJob = coroutineScope.launch {
                try {
                    val result = downloadKuroModel(storageRoot, localModelManifest) { progress ->
                        coroutineScope.launch { modelDownloadProgress = progress }
                    }
                    modelBusy = false
                    modelDownloadJob = null
                    modelDownloadProgress = null
                    if (result.success) {
                        localModelPath = result.filePath
                        modelStatusMessage = result.message + " It is stored only in this app's private data directory."
                    } else {
                        modelStatusMessage = result.message
                    }
                } catch (cancelled: CancellationException) {
                    modelBusy = false
                    modelDownloadJob = null
                    modelDownloadProgress = null
                    modelStatusMessage = "Download cancelled. The partial file was kept so a later retry can resume."
                } catch (error: Exception) {
                    modelBusy = false
                    modelDownloadJob = null
                    modelDownloadProgress = null
                    modelStatusMessage = "Download failed: ${error.message ?: "unknown error"}"
                }
            }
        }
    }
    val cancelLocalModelDownload: () -> Unit = {
        modelDownloadJob?.cancel()
    }
    val loadLocalModel: () -> Unit = {
        val path = localModelPath
        if (!modelBusy && !kuroGenerating && !localModelLoaded) {
            if (path == null) {
                modelStatusMessage = "Download the local model before loading it."
            } else {
                modelBusy = true
                modelStatusMessage = "Loading local model into memory…"
                coroutineScope.launch {
                    val result = localModelRuntime.load(path)
                    localModelLoaded = result.success
                    modelStatusMessage = result.message
                    modelBusy = false
                }
            }
        }
    }
    val unloadLocalModel: () -> Unit = {
        if (!modelBusy && !kuroGenerating && localModelLoaded) {
            modelBusy = true
            coroutineScope.launch {
                val result = localModelRuntime.unload()
                localModelLoaded = false
                modelStatusMessage = result.message
                modelBusy = false
            }
        }
    }
    val sendKuroMessage: (String, Int, String, Set<String>) -> Unit = {
            prompt, chapter, branchId, entityIds ->
        when {
            prompt.isBlank() -> kuroAssistantStatus = "Write a message first."
            !localModelLoaded -> kuroAssistantStatus = "Load the local model before chatting."
            modelBusy || kuroGenerating -> kuroAssistantStatus = "Kuro is already handling a task."
            else -> {
                kuroGenerating = true
                pendingKuroAction = null
                kuroLastPrompt = prompt.trim()
                kuroOutput = ""
                kuroAssistantStatus = "Using the on-device model; no prompt is sent to a remote service."
                val projectSnapshot = project
                coroutineScope.launch {
                    val contextText = withContext(Dispatchers.Default) {
                        if (entityIds.isEmpty()) {
                            "No story entities were selected. Answer general creative questions without inventing any project canon."
                        } else {
                            KuroStoryContextCompiler.compile(
                                projectSnapshot,
                                KuroContextRequest(
                                    chapter = chapter,
                                    branchId = branchId,
                                    entityIds = entityIds,
                                    lookbackChapters = 3
                                )
                            ).renderForModel()
                        }
                    }
                    val result = localModelRuntime.generate(
                        systemInstruction = KuroPromptBuilder.systemInstruction(contextText),
                        userPrompt = prompt.trim(),
                        onPartialText = { chunk ->
                            coroutineScope.launch {
                                kuroOutput = (kuroOutput + chunk).takeLast(18000)
                            }
                        }
                    )
                    kuroGenerating = false
                    val proposedAction = if (result.success) KuroNativeActionParser.parse(result.text) else null
                    pendingKuroAction = proposedAction
                    kuroAssistantStatus = when {
                        !result.success -> result.message
                        proposedAction != null -> "Kuro prepared a native action proposal. Review and approve it before any project change."
                        else -> "Local response complete. No project changes were applied."
                    }
                    if (proposedAction != null) {
                        kuroOutput = "Proposed action: ${proposedAction.summary}\nNothing changes until you approve the proposal below."
                    }
                }
            }
        }
    }
    var tool by remember { mutableStateOf("Brush") }
    var brushSize by remember { mutableFloatStateOf(4.5f) }
    var strokes by remember(projectStorage) { mutableStateOf(project.strokes.map { it.toInkStroke() }) }
    var redoStrokes by remember { mutableStateOf(emptyList<InkStroke>()) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet, secondary = Cyan, background = Ink,
            surface = Panel, onBackground = PrimaryText, onSurface = PrimaryText
        )
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Ink)) {
            val wide = maxWidth >= 900.dp
            Column(Modifier.fillMaxSize()) {
                if (storageWarning != null) {
                    Text(
                        text = storageWarning.orEmpty(),
                        color = Amber,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().background(Raised).padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
                TopBar(
                    wide = wide,
                    onUndo = {
                        val lastStroke = project.strokes.lastOrNull()
                        if (lastStroke != null) {
                            val addEvent = project.auditLog.lastOrNull {
                                it.action == "add_canvas_stroke" && it.targetId == lastStroke.id
                            }
                            if (addEvent != null) {
                                val result = operationBroker.execute(
                                    project,
                                    UndoAddedCanvasStroke(
                                        operationId = "op-undo-stroke-${project.revision + 1}",
                                        projectId = project.projectId,
                                        expectedRevision = project.revision,
                                        targetOperationId = addEvent.operationId
                                    )
                                )
                                if (result.status == OperationStatus.COMMITTED) {
                                    redoStrokes = redoStrokes + lastStroke.toInkStroke()
                                    commitProject(result.project)
                                    strokes = result.project.strokes.map { it.toInkStroke() }
                                }
                            }
                        } else {
                            undoLastStoryboardPanel()
                        }
                    },
                    onRedo = {
                        val redo = redoStrokes.lastOrNull()
                        if (redo != null) {
                            val newStroke = redo.toKuroStroke("stroke-${project.revision + 1}")
                            val result = operationBroker.execute(
                                project,
                                AddCanvasStroke(
                                    operationId = "op-add-stroke-${project.revision + 1}",
                                    projectId = project.projectId,
                                    expectedRevision = project.revision,
                                    stroke = newStroke
                                )
                            )
                            if (result.status == OperationStatus.COMMITTED) {
                                commitProject(result.project)
                                strokes = result.project.strokes.map { it.toInkStroke() }
                                redoStrokes = redoStrokes.dropLast(1)
                            }
                        }
                    },
                    onKuro = { showKuro = !showKuro }
                )
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (wide) SideNavigation(section) { section = it }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when (section) {
                            Section.Create -> CreateWorkspace(
                                wide = wide,
                                strokes = strokes,
                                layers = project.layers,
                                selectedLayerId = selectedLayerId,
                                onSelectLayer = { selectedLayerId = it },
                                onAddLayer = addCanvasLayer,
                                onUpdateLayer = updateCanvasLayer,
                                onRemoveLayer = removeCanvasLayer,
                                demoPanels = project.panels.size,
                                tool = tool,
                                onTool = { tool = it },
                                brushSize = brushSize,
                                onBrushSize = { brushSize = it },
                                onStroke = { newInkStroke ->
                                    val nativeStroke = newInkStroke.copy(layerId = selectedLayerId)
                                        .toKuroStroke("stroke-${project.revision + 1}")
                                    val result = operationBroker.execute(
                                        project,
                                        AddCanvasStroke(
                                            operationId = "op-add-stroke-${project.revision + 1}",
                                            projectId = project.projectId,
                                            expectedRevision = project.revision,
                                            stroke = nativeStroke
                                        )
                                    )
                                    if (result.status == OperationStatus.COMMITTED) {
                                        commitProject(result.project)
                                        strokes = result.project.strokes.map { it.toInkStroke() }
                                        redoStrokes = emptyList()
                                    }
                                }
                            )
                            Section.Story -> StoryWorkspace(project, addStoryEntity, addCanonFact, addStoryRelationship, addStoryEvent)
                            Section.Vault -> VaultWorkspace(vaultIndex, vaultWarning, vaultMessage, storageRoot, vaultWritesAllowed, onVaultImportResult, onVaultMetadataUpdate)
                            Section.Style -> PlannedWorkspace("Style Lab", "Define linework, anatomy, palette, rendering and lettering as versioned Style DNA. Preview changes before approval.", "◉")
                            Section.ThreeD -> PlannedWorkspace("3D Studio", "Reusable glTF/GLB assets, real 3D viewports, rigs, cameras, lighting and animation will remain editable project data.", "◇")
                            Section.Research -> PlannedWorkspace("Research", "Keep sources, claims, contradictions and provenance linked. Research stays a proposal until you approve it.", "⌕")
                            Section.Publish -> PlannedWorkspace("Publish", "Create reproducible episode, page, image-sequence and print exports from a specific project revision.", "↗")
                        }
                    }
                    if (wide && showKuro) {
                        KuroPanel(
                            Modifier.width(320.dp).fillMaxHeight(),
                            onClose = { showKuro = false },
                            onAddPanel = addStoryboardPanel,
            project = project,
            modelManifest = localModelManifest,
            modelPath = localModelPath,
            modelLoaded = localModelLoaded,
            modelBusy = modelBusy,
            modelMessage = modelStatusMessage,
            modelProgress = modelDownloadProgress,
            lastPrompt = kuroLastPrompt,
            reply = kuroOutput,
            replyError = kuroAssistantStatus,
            isGenerating = kuroGenerating,
            onDownloadModel = downloadLocalModel,
            onCancelDownload = cancelLocalModelDownload,
            onLoadModel = loadLocalModel,
            onUnloadModel = unloadLocalModel,
            onSendMessage = sendKuroMessage,
                            pendingAction = pendingKuroAction,
                            onApplyAction = { proposal ->
                                val status = applyKuroAction(proposal)
                                if (status == OperationStatus.COMMITTED) pendingKuroAction = null
                                status
                            },
                            onDismissAction = { pendingKuroAction = null }
                        )
                    } else if (wide && section == Section.Create) {
                        InspectorPanel(strokes.size, project.panels.size, tool, project.revision, project.auditLog.size)
                    }
                }
                if (!wide) BottomNavigation(section) { section = it }
            }

            if (!wide && showKuro) {
                KuroPanel(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 72.dp),
                    onClose = { showKuro = false },
                    onAddPanel = addStoryboardPanel,
                    project = project,
                    modelManifest = localModelManifest,
                    modelPath = localModelPath,
                    modelLoaded = localModelLoaded,
                    modelBusy = modelBusy,
                    modelMessage = modelStatusMessage,
                    modelProgress = modelDownloadProgress,
                    lastPrompt = kuroLastPrompt,
                    reply = kuroOutput,
                    replyError = kuroAssistantStatus,
                    isGenerating = kuroGenerating,
                    onDownloadModel = downloadLocalModel,
                    onCancelDownload = cancelLocalModelDownload,
                    onLoadModel = loadLocalModel,
                    onUnloadModel = unloadLocalModel,
                    onSendMessage = sendKuroMessage,
                    pendingAction = pendingKuroAction,
                    onApplyAction = { proposal ->
                        val status = applyKuroAction(proposal)
                        if (status == OperationStatus.COMMITTED) pendingKuroAction = null
                        status
                    },
                    onDismissAction = { pendingKuroAction = null }
                )
            }
        }
    }
}

@Composable
private fun TopBar(wide: Boolean, onUndo: () -> Unit, onRedo: () -> Unit, onKuro: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Panel).border(1.dp, Line).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Violet, Cyan))),
            contentAlignment = Alignment.Center
        ) { Text("K", color = Ink, fontWeight = FontWeight.Black, fontSize = 19.sp) }
        Column(Modifier.weight(1f)) {
            Text("KURO CANVAS", fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.2.sp)
            Text(if (wide) "Untitled project  /  Creative workspace" else "Untitled project", color = MutedText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("PROTOTYPE", color = Amber, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
        TextButton(onClick = onUndo) { Text("↶", color = PrimaryText, fontSize = 20.sp) }
        TextButton(onClick = onRedo) { Text("↷", color = PrimaryText, fontSize = 20.sp) }
        Button(
            onClick = onKuro,
            colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Ink),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) { Text("◉ Kuro", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
    }
}

@Composable
private fun SideNavigation(active: Section, onSelect: (Section) -> Unit) {
    Column(
        Modifier.width(176.dp).fillMaxHeight().background(Panel).border(1.dp, Line).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("WORKSPACE", color = MutedText, fontSize = 10.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(8.dp))
        Section.entries.forEach { item ->
            NavigationItem(item, item == active) { onSelect(item) }
        }
        Spacer(Modifier.weight(1f))
        Text("Local-first by design", color = MutedText, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
    }
}

@Composable
private fun BottomNavigation(active: Section, onSelect: (Section) -> Unit) {
    val items = listOf(Section.Create, Section.Story, Section.Vault, Section.Style)
    Row(
        Modifier.fillMaxWidth().background(Panel).border(1.dp, Line).padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelect(item) }
                    .background(if (active == item) Color(0xFF28223F) else Color.Transparent).padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(item.glyph, color = if (active == item) Violet else MutedText, fontSize = 17.sp)
                Text(item.label, color = if (active == item) PrimaryText else MutedText, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun NavigationItem(item: Section, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .background(if (selected) Color(0xFF28223F) else Color.Transparent)
            .padding(horizontal = 11.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Text(item.glyph, color = if (selected) Violet else MutedText, fontSize = 16.sp)
        Text(item.label, color = if (selected) PrimaryText else MutedText, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun CreateWorkspace(
    wide: Boolean,
    strokes: List<InkStroke>,
    layers: List<KuroLayer>,
    selectedLayerId: String,
    onSelectLayer: (String) -> Unit,
    onAddLayer: () -> Unit,
    onUpdateLayer: (String, String?, Boolean?, Boolean?, Float?) -> OperationStatus,
    onRemoveLayer: (String) -> OperationStatus,
    demoPanels: Int,
    tool: String,
    onTool: (String) -> Unit,
    brushSize: Float,
    onBrushSize: (Float) -> Unit,
    onStroke: (InkStroke) -> Unit
) {
    var livePoints by remember { mutableStateOf(emptyList<Offset>()) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Canvas", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Editable strokes • autosaved to this device", color = MutedText, fontSize = 10.sp)
            }
            StatusPill("Local autosave", Green)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Brush", "Eraser").forEach { label -> ToolChip(label, label == tool) { onTool(label) } }
            Spacer(Modifier.weight(1f))
            Text("STROKES " + strokes.size, color = MutedText, fontSize = 10.sp)
        }
        val activeLayer = layers.firstOrNull { it.id == selectedLayerId }
        var layerMessage by remember(selectedLayerId) { mutableStateOf<String?>(null) }
        var layerOpacity by remember(selectedLayerId, activeLayer?.opacity) {
            mutableFloatStateOf(activeLayer?.opacity ?: 1f)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("LAYERS", color = MutedText, fontSize = 9.sp, letterSpacing = 0.8.sp)
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                layers.sortedBy { it.order }.forEach { layer ->
                    ToolChip(
                        layer.name + if (layer.locked) " 🔒" else "",
                        layer.id == selectedLayerId
                    ) { onSelectLayer(layer.id); layerMessage = null }
                }
            }
            ToolChip("+", false) { onAddLayer(); layerMessage = null }
        }
        if (activeLayer != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolChip(if (activeLayer.visible) "Visible" else "Hidden", activeLayer.visible) {
                    val status = onUpdateLayer(activeLayer.id, null, !activeLayer.visible, null, null)
                    layerMessage = if (status == OperationStatus.COMMITTED) null else status.name
                }
                ToolChip(if (activeLayer.locked) "Locked" else "Unlocked", activeLayer.locked) {
                    val status = onUpdateLayer(activeLayer.id, null, null, !activeLayer.locked, null)
                    layerMessage = if (status == OperationStatus.COMMITTED) null else status.name
                }
                Text("Opacity", color = MutedText, fontSize = 10.sp)
                Slider(
                    value = layerOpacity,
                    onValueChange = { layerOpacity = it },
                    onValueChangeFinished = {
                        val status = onUpdateLayer(activeLayer.id, null, null, null, layerOpacity)
                        layerMessage = if (status == OperationStatus.COMMITTED) null else status.name
                    },
                    modifier = Modifier.weight(1f),
                    valueRange = 0f..1f
                )
                Button(
                    onClick = {
                        val status = onRemoveLayer(activeLayer.id)
                        layerMessage = when (status) {
                            OperationStatus.COMMITTED -> null
                            OperationStatus.DEPENDENCY_EXISTS -> "Move or remove this layer's strokes first."
                            OperationStatus.INVALID_UNDO_TARGET -> "The starter layer cannot be removed this way."
                            else -> status.name.lowercase().replace('_', ' ')
                        }
                    },
                    enabled = layers.size > 1,
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp)
                ) { Text("Remove", fontSize = 10.sp) }
            }
        }
        if (layerMessage != null) {
            Text(layerMessage.orEmpty(), color = Amber, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 14.dp))
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, Line, RoundedCornerShape(18.dp))
        ) {
            SketchCanvas(
                Modifier.fillMaxSize(),
                strokes = strokes,
                layers = layers,
                activeLayerId = selectedLayerId,
                canDraw = activeLayer != null && activeLayer.visible && !activeLayer.locked,
                brushSize = brushSize,
                panelCount = demoPanels,
                eraser = tool == "Eraser",
                livePoints = livePoints,
                onLivePoints = { livePoints = it },
                onStroke = onStroke
            )
            Column(Modifier.align(Alignment.TopStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                TinyChip("STORYBOARD")
                TinyChip("PANELS " + demoPanels)
            }
            Text("Touch or stylus to sketch", color = Color(0xFF777C8B), fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
        }
        Row(
            Modifier.fillMaxWidth().background(Panel).padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Brush size", color = MutedText, fontSize = 10.sp)
                Text(brushSize.toInt().toString() + " px", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
            listOf(2.5f, 4.5f, 7.5f, 12f).forEach { size ->
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(if (brushSize == size) Color(0xFF342B55) else Raised)
                        .border(1.dp, if (brushSize == size) Violet else Line, CircleShape).clickable { onBrushSize(size) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size((size + 3).dp.coerceAtMost(16.dp)).clip(CircleShape).background(PrimaryText))
                }
            }
        }
    }
}

@Composable
private fun SketchCanvas(
    modifier: Modifier,
    strokes: List<InkStroke>,
    layers: List<KuroLayer>,
    activeLayerId: String,
    canDraw: Boolean,
    brushSize: Float,
    panelCount: Int,
    eraser: Boolean,
    livePoints: List<Offset>,
    onLivePoints: (List<Offset>) -> Unit,
    onStroke: (InkStroke) -> Unit
) {
    Canvas(
        modifier.background(Color(0xFF111522)).pointerInput(brushSize, eraser, activeLayerId, canDraw) {
            if (canDraw) {
                val canvasWidth = size.width.coerceAtLeast(1).toFloat()
                val canvasHeight = size.height.coerceAtLeast(1).toFloat()
                val minDimension = minOf(canvasWidth, canvasHeight).coerceAtLeast(1f)
                fun normalized(point: Offset) = Offset(
                    (point.x / canvasWidth).coerceIn(0f, 1f),
                    (point.y / canvasHeight).coerceIn(0f, 1f)
                )
                var draft = emptyList<Offset>()
                detectDragGestures(
                    onDragStart = { point ->
                        draft = listOf(normalized(point))
                        onLivePoints(draft)
                    },
                    onDragEnd = {
                        if (draft.size > 1) onStroke(
                            InkStroke(
                                points = draft,
                                color = if (eraser) Color(0xFFF5F1EA) else Color(0xFF25283B),
                                widthRatio = (if (eraser) brushSize * 3f else brushSize) / minDimension,
                                layerId = activeLayerId
                            )
                        )
                        draft = emptyList()
                        onLivePoints(emptyList())
                    },
                    onDragCancel = {
                        draft = emptyList()
                        onLivePoints(emptyList())
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        draft = draft + normalized(change.position)
                        onLivePoints(draft)
                    }
                )
            }
        }
    ) {
        drawRect(Color(0xFF111522))
        val left = size.width * 0.075f
        val right = size.width * 0.925f
        val top = size.height * 0.055f
        val bottom = size.height * 0.945f
        drawRect(Color(0xFFF5F1EA), Offset(left, top), Size(right - left, bottom - top))
        var y = top + 22f
        while (y < bottom) {
            drawLine(Color(0xFFE5E0D9), Offset(left + 12f, y), Offset(right - 12f, y), strokeWidth = 0.7f)
            y += 34f
        }
        for (index in 0 until panelCount.coerceAtMost(8)) {
            val panelTop = top + 42f + index * 88f
            if (panelTop + 72f < bottom - 8f) {
                drawRect(
                    color = Color(0xFF8C7AEC),
                    topLeft = Offset(left + 16f, panelTop),
                    size = Size(right - left - 32f, 68f),
                    style = Stroke(width = 1.5f)
                )
            }
        }

        val minDimension = minOf(size.width, size.height).coerceAtLeast(1f)
        layers.filter { it.visible }.sortedBy { it.order }.forEach { layer ->
            strokes.filter { it.layerId == layer.id }.forEach { stroke ->
                val points = stroke.points.map { Offset(it.x * size.width, it.y * size.height) }
                val width = (stroke.widthRatio * minDimension).coerceAtLeast(1f)
                val color = stroke.color.copy(alpha = stroke.color.alpha * layer.opacity.coerceIn(0f, 1f))
                if (points.size == 1) drawCircle(color, width / 2f, points.first())
                for (pointIndex in 0 until points.lastIndex) {
                    drawLine(color, points[pointIndex], points[pointIndex + 1], width, StrokeCap.Round)
                }
            }
        }

        val activeLayer = layers.firstOrNull { it.id == activeLayerId }
        if (canDraw && activeLayer != null && livePoints.isNotEmpty()) {
            val previewColor = (if (eraser) Color(0xFFF5F1EA) else Color(0xFF25283B))
                .copy(alpha = activeLayer.opacity.coerceIn(0f, 1f))
            val points = livePoints.map { Offset(it.x * size.width, it.y * size.height) }
            val width = ((if (eraser) brushSize * 3f else brushSize) / minDimension).coerceAtLeast(1f / minDimension) * minDimension
            if (points.size == 1) drawCircle(previewColor, width / 2f, points.first())
            for (pointIndex in 0 until points.lastIndex) {
                drawLine(previewColor, points[pointIndex], points[pointIndex + 1], width, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun InspectorPanel(strokes: Int, panels: Int, tool: String, revision: Long, auditCount: Int) {
    Column(
        Modifier.width(236.dp).fillMaxHeight().background(Panel).border(1.dp, Line).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text("Inspector", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("CURRENT DOCUMENT", color = MutedText, fontSize = 9.sp, letterSpacing = 1.sp)
        DetailRow("Tool", tool)
        DetailRow("Strokes", strokes.toString())
        DetailRow("Panels", panels.toString())
        DetailRow("Revision", revision.toString())
        DetailRow("Audit events", auditCount.toString())
        DividerLine()
        Text("Project status", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        StatusPill("Local project", Green)
        Text("Panel operations and editable strokes are saved locally with revision and undo history.", color = MutedText, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.weight(1f))
        Text("Kuro Canvas • UI foundation", color = MutedText, fontSize = 10.sp)
    }
}

@Composable
private fun StoryWorkspace(
    project: KuroProjectState,
    onAddEntity: (String, KuroStoryEntityType) -> OperationStatus,
    onAddFact: (String, String, String, Int, Int?, String) -> OperationStatus,
    onAddRelationship: (String, String, String, Int, Int?, String) -> OperationStatus,
    onAddEvent: (String, Int, String, List<String>, String) -> OperationStatus
) {
    var entityName by remember(project.projectId) { mutableStateOf("") }
    var entityType by remember(project.projectId) { mutableStateOf(KuroStoryEntityType.CHARACTER) }
    var showEntityTypeMenu by remember { mutableStateOf(false) }
    var selectedEntityId by remember(project.projectId) {
        mutableStateOf(project.storyEntities.firstOrNull()?.id.orEmpty())
    }
    var profileChapter by remember { mutableStateOf("1") }
    var predicate by remember { mutableStateOf("Hair color") }
    var value by remember { mutableStateOf("") }
    var fromChapter by remember { mutableStateOf("1") }
    var untilChapter by remember { mutableStateOf("") }
    var branchId by remember { mutableStateOf("main") }
    var message by remember { mutableStateOf<String?>(null) }
    var relatedEntityId by remember(project.projectId) {
        mutableStateOf(project.storyEntities.firstOrNull { it.id != selectedEntityId }?.id.orEmpty())
    }
    var showRelatedMenu by remember { mutableStateOf(false) }
    var relationshipType by remember { mutableStateOf("friend") }
    var graphMessage by remember { mutableStateOf<String?>(null) }
    var eventTitle by remember { mutableStateOf("") }
    var eventDescription by remember { mutableStateOf("") }
    var eventChapter by remember { mutableStateOf("1") }
    var eventMessage by remember { mutableStateOf<String?>(null) }

    val selectedEntity = project.storyEntities.firstOrNull { it.id == selectedEntityId }
    val relatedEntity = project.storyEntities.firstOrNull { it.id == relatedEntityId && it.id != selectedEntityId }
    val selectedRelationships = project.relationships.filter {
        (it.fromEntityId == selectedEntityId || it.toEntityId == selectedEntityId) &&
            (it.status == com.kurocanvas.project.KuroCanonFactStatus.CONFIRMED ||
                it.status == com.kurocanvas.project.KuroCanonFactStatus.PROPOSED)
    }.sortedWith(compareBy({ it.fromChapter }, { it.relationshipType }))
    val entityFacts = project.canonFacts
        .filter { it.subjectId == selectedEntityId && it.status == com.kurocanvas.project.KuroCanonFactStatus.CONFIRMED }
        .sortedBy { it.fromChapter }
    val characterProfile = selectedEntity?.takeIf { it.type == KuroStoryEntityType.CHARACTER }?.let {
        KuroCharacterProfileResolver.resolve(
            project = project,
            entityId = it.id,
            chapter = profileChapter.toIntOrNull() ?: 0,
            branchId = branchId
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Text("Story workspace", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Canon is structured project data, not something the chat has to remember.",
            color = MutedText,
            fontSize = 13.sp
        )
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("STORY ENTITY VAULT", color = MutedText, fontSize = 10.sp, letterSpacing = 1.1.sp)
                Text("Add a story entity", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = entityName,
                    onValueChange = { entityName = it; message = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("${entityType.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }} name") },
                    singleLine = true
                )
                Box {
                    OutlinedButton(
                        onClick = { showEntityTypeMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Entity type: ${entityType.name.lowercase().replace('_', ' ')}") }
                    DropdownMenu(
                        expanded = showEntityTypeMenu,
                        onDismissRequest = { showEntityTypeMenu = false }
                    ) {
                        KuroStoryEntityType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) },
                                onClick = { entityType = type; showEntityTypeMenu = false; message = null }
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        val newId = "entity-${project.revision + 1}"
                        val status = onAddEntity(entityName, entityType)
                        message = when (status) {
                            OperationStatus.COMMITTED -> "${entityType.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }} saved to this project."
                            OperationStatus.DUPLICATE_OBJECT_ID -> "That entity already exists, or the new item ID is not unique."
                            OperationStatus.INVALID_STORY_ENTITY -> "Enter an entity name first."
                            else -> "Could not save this entity: ${status.name.lowercase().replace('_', ' ')}."
                        }
                        if (status == OperationStatus.COMMITTED) {
                            selectedEntityId = newId
                            entityName = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add entity") }
                if (project.storyEntities.isEmpty()) {
                    Text("No characters yet. The story starts empty on purpose.", color = MutedText, fontSize = 12.sp)
                } else {
                    Text("PROJECT CHARACTERS", color = MutedText, fontSize = 10.sp, letterSpacing = 1.sp)
                    project.storyEntities.forEach { entity ->
                        Surface(
                            color = if (entity.id == selectedEntityId) Color(0xFF28223F) else Raised,
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                                .clickable { selectedEntityId = entity.id; message = null }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entity.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(entity.type.name.lowercase(), color = MutedText, fontSize = 10.sp)
                                }
                                if (entity.id == selectedEntityId) StatusPill("SELECTED", Violet)
                            }
                        }
                    }
                }
            }
        }

        if (selectedEntity?.type == KuroStoryEntityType.CHARACTER) {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("CHARACTER PROFILE SNAPSHOT", color = MutedText, fontSize = 10.sp, letterSpacing = 1.1.sp)
                    Text(
                        "Resolved canon for ${selectedEntity.name}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = profileChapter,
                            onValueChange = { profileChapter = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("View at chapter") },
                            singleLine = true
                        )
                        Text("Branch: $branchId", color = MutedText, fontSize = 11.sp)
                    }
                    Text(
                        "This is a read-only design reference. Unknown traits stay unknown; conflicts are surfaced instead of guessed.",
                        color = MutedText,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    if (characterProfile == null) {
                        Text("Enter a chapter number greater than zero.", color = Amber, fontSize = 12.sp)
                    } else {
                        characterProfile.fields.forEach { field ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(field.predicate, color = MutedText, fontSize = 12.sp, modifier = Modifier.weight(0.38f))
                                Column(Modifier.weight(0.62f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    when (field.status) {
                                        KuroProfileFieldStatus.KNOWN -> Text(field.value.orEmpty(), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        KuroProfileFieldStatus.UNKNOWN -> Text("Unknown — not in canon", color = MutedText, fontSize = 12.sp)
                                        KuroProfileFieldStatus.CONFLICT -> Text(
                                            "Conflict: ${field.alternatives.joinToString(" / ")}",
                                            color = Amber,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("TIME-SCOPED CANON", color = MutedText, fontSize = 10.sp, letterSpacing = 1.1.sp)
                Text(
                    selectedEntity?.let { "Facts for ${it.name}" } ?: "Select or add a character first",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Chapter ranges use a start-inclusive, end-exclusive interval. Conflicting confirmed values are rejected instead of silently overwriting canon.",
                    color = MutedText,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                OutlinedTextField(
                    value = predicate,
                    onValueChange = { predicate = it; message = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Fact (e.g. hair color, age, status)") },
                    singleLine = true,
                    enabled = selectedEntity != null
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; message = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Canonical value") },
                    singleLine = true,
                    enabled = selectedEntity != null
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fromChapter,
                        onValueChange = { fromChapter = it; message = null },
                        modifier = Modifier.weight(1f),
                        label = { Text("From chapter") },
                        singleLine = true,
                        enabled = selectedEntity != null
                    )
                    OutlinedTextField(
                        value = untilChapter,
                        onValueChange = { untilChapter = it; message = null },
                        modifier = Modifier.weight(1f),
                        label = { Text("Until chapter (optional)") },
                        singleLine = true,
                        enabled = selectedEntity != null
                    )
                }
                OutlinedTextField(
                    value = branchId,
                    onValueChange = { branchId = it; message = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Timeline branch") },
                    singleLine = true,
                    enabled = selectedEntity != null
                )
                Button(
                    onClick = {
                        val startChapter = fromChapter.toIntOrNull()
                        val endChapter = untilChapter.takeIf { it.isNotBlank() }?.toIntOrNull()
                        if (selectedEntity == null) {
                            message = "Select a character before adding a fact."
                        } else if (startChapter == null || startChapter < 1 ||
                            (untilChapter.isNotBlank() && endChapter == null) ||
                            (endChapter != null && endChapter <= startChapter)
                        ) {
                            message = "Enter a valid chapter interval. The end chapter must be greater than the start."
                        } else {
                            val status = onAddFact(
                                selectedEntity.id,
                                predicate,
                                value,
                                startChapter,
                                endChapter,
                                branchId
                            )
                            message = when (status) {
                                OperationStatus.COMMITTED -> "Fact saved and added to the audit history."
                                OperationStatus.CANON_CONTRADICTION -> "Canon conflict: a different confirmed value overlaps this chapter range. Re-time the old fact first."
                                OperationStatus.INVALID_CANON_FACT -> "Enter the fact, value, branch and a valid chapter interval."
                                OperationStatus.MISSING_CANON_SUBJECT -> "The selected character no longer exists in this project."
                                else -> "Could not save the fact: ${status.name.lowercase().replace('_', ' ')}."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedEntity != null
                ) { Text("Save canon fact") }
                if (message != null) {
                    Text(message.orEmpty(), color = if (message.orEmpty().startsWith("Canon conflict")) Amber else MutedText, fontSize = 11.sp)
                }
                DividerLine()
                Text("CONFIRMED FACTS", color = MutedText, fontSize = 10.sp, letterSpacing = 1.sp)
                if (entityFacts.isEmpty()) {
                    Text("No confirmed facts for this character yet.", color = MutedText, fontSize = 12.sp)
                } else {
                    entityFacts.forEach { fact ->
                        Surface(color = Raised, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${fact.predicate}: ${fact.value}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                val endLabel = fact.untilChapterExclusive?.let { "Ch ${it - 1}" } ?: "ongoing"
                                Text(
                                    "Ch ${fact.fromChapter}–$endLabel • branch ${fact.branchId}",
                                    color = MutedText,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("RELATIONSHIP GRAPH", color = MutedText, fontSize = 10.sp, letterSpacing = 1.1.sp)
                Text("Connect story entities", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Relationships are native edges between two existing entities and can change by chapter or branch.",
                    color = MutedText,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showRelatedMenu = true },
                            enabled = selectedEntity != null && project.storyEntities.any { it.id != selectedEntityId },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(relatedEntity?.name ?: "Choose related character", maxLines = 1)
                        }
                        DropdownMenu(
                            expanded = showRelatedMenu,
                            onDismissRequest = { showRelatedMenu = false }
                        ) {
                            project.storyEntities.filter { it.id != selectedEntityId }.forEach { entity ->
                                DropdownMenuItem(
                                    text = { Text(entity.name) },
                                    onClick = {
                                        relatedEntityId = entity.id
                                        showRelatedMenu = false
                                        graphMessage = null
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = relationshipType,
                        onValueChange = { relationshipType = it; graphMessage = null },
                        modifier = Modifier.weight(1f),
                        label = { Text("Relationship") },
                        singleLine = true
                    )
                }
                Text(
                    "Range: chapter ${fromChapter.ifBlank { "?" }}–${untilChapter.takeIf { it.isNotBlank() } ?: "ongoing"} · branch $branchId",
                    color = MutedText,
                    fontSize = 10.sp
                )
                Button(
                    onClick = {
                        val from = fromChapter.toIntOrNull()
                        val until = untilChapter.takeIf { it.isNotBlank() }?.toIntOrNull()
                        if (selectedEntity == null || relatedEntity == null) {
                            graphMessage = "Choose two different characters first."
                        } else if (from == null || from < 1 ||
                            (untilChapter.isNotBlank() && until == null) ||
                            (until != null && until <= from)
                        ) {
                            graphMessage = "Enter a valid chapter range."
                        } else {
                            val status = onAddRelationship(
                                selectedEntity.id,
                                relatedEntity.id,
                                relationshipType,
                                from,
                                until,
                                branchId
                            )
                            graphMessage = when (status) {
                                OperationStatus.COMMITTED -> "Relationship saved to this timeline."
                                OperationStatus.MISSING_RELATIONSHIP_ENTITY -> "Both characters must exist in the project."
                                OperationStatus.INVALID_RELATIONSHIP -> "Provide a relationship type and a valid chapter range."
                                else -> "Could not save relationship: ${status.name.lowercase().replace('_', ' ')}."
                            }
                        }
                    },
                    enabled = selectedEntity != null && relatedEntity != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add relationship") }
                if (graphMessage != null) Text(graphMessage.orEmpty(), color = MutedText, fontSize = 11.sp)
                DividerLine()
                Text("RELATIONSHIPS FOR ${selectedEntity?.name ?: "SELECTED CHARACTER"}", color = MutedText, fontSize = 10.sp)
                if (selectedRelationships.isEmpty()) {
                    Text("No relationships recorded yet.", color = MutedText, fontSize = 12.sp)
                } else {
                    selectedRelationships.forEach { relation ->
                        val fromName = project.storyEntities.firstOrNull { it.id == relation.fromEntityId }?.name ?: relation.fromEntityId
                        val toName = project.storyEntities.firstOrNull { it.id == relation.toEntityId }?.name ?: relation.toEntityId
                        val end = relation.untilChapterExclusive?.let { (it - 1).toString() } ?: "ongoing"
                        Surface(color = Raised, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("$fromName — ${relation.relationshipType} — $toName", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Ch ${relation.fromChapter}–$end · branch ${relation.branchId} · ${relation.status.name.lowercase()}",
                                    color = MutedText,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("EVENT TIMELINE", color = MutedText, fontSize = 10.sp, letterSpacing = 1.1.sp)
                Text("Record a story event", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = eventTitle,
                    onValueChange = { eventTitle = it; eventMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Event title") },
                    singleLine = true,
                    enabled = selectedEntity != null
                )
                OutlinedTextField(
                    value = eventChapter,
                    onValueChange = { eventChapter = it; eventMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Chapter number") },
                    singleLine = true,
                    enabled = selectedEntity != null
                )
                OutlinedTextField(
                    value = eventDescription,
                    onValueChange = { eventDescription = it; eventMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("What happens? (optional)") },
                    enabled = selectedEntity != null,
                    minLines = 2
                )
                Text(
                    "The selected character will be linked as a participant. Timeline branch: $branchId",
                    color = MutedText,
                    fontSize = 10.sp
                )
                Button(
                    onClick = {
                        val chapter = eventChapter.toIntOrNull()
                        if (selectedEntity == null) {
                            eventMessage = "Select a character first."
                        } else if (eventTitle.isBlank() || chapter == null || chapter < 1) {
                            eventMessage = "Enter an event title and a chapter number greater than zero."
                        } else {
                            val status = onAddEvent(
                                eventTitle,
                                chapter,
                                eventDescription,
                                listOf(selectedEntity.id),
                                branchId
                            )
                            eventMessage = when (status) {
                                OperationStatus.COMMITTED -> {
                                    eventTitle = ""
                                    eventDescription = ""
                                    "Event saved to the timeline."
                                }
                                OperationStatus.MISSING_EVENT_PARTICIPANT -> "Event participants must be existing story entities."
                                OperationStatus.INVALID_STORY_EVENT -> "Enter a title, branch and valid chapter number."
                                else -> "Could not save event: ${status.name.lowercase().replace('_', ' ')}."
                            }
                        }
                    },
                    enabled = selectedEntity != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add timeline event") }
                if (eventMessage != null) Text(eventMessage.orEmpty(), color = MutedText, fontSize = 11.sp)
                DividerLine()
                val timelineEvents = project.eventsInTimeline(branchId = branchId)
                Text("TIMELINE · ${timelineEvents.size} EVENT(S)", color = MutedText, fontSize = 10.sp)
                if (timelineEvents.isEmpty()) {
                    Text("No events in this branch yet.", color = MutedText, fontSize = 12.sp)
                } else {
                    timelineEvents.forEach { event ->
                        Surface(color = Raised, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Ch ${event.chapter} · ${event.title}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                if (event.description.isNotBlank()) Text(event.description, color = MutedText, fontSize = 11.sp)
                                Text(
                                    "Participants: " + event.participantEntityIds.mapNotNull { id -> project.storyEntities.firstOrNull { it.id == id }?.name }.joinToString(", "),
                                    color = MutedText,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Text(
            "Facts are stored separately from the conversation. They can be resolved by character, property, chapter and branch when Kuro's local context compiler is connected.",
            color = MutedText,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun VaultWorkspace(
    index: KuroVaultIndex,
    warning: String?,
    message: String?,
    storageRoot: String,
    importEnabled: Boolean,
    onImported: (KuroVaultAsset?, String?) -> Unit,
    onUpdateAsset: (KuroVaultAsset) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedAssetId by remember { mutableStateOf<String?>(null) }
    val selectedAsset = index.assets.firstOrNull { it.id == selectedAssetId }
    var licenseId by remember(selectedAsset?.id, selectedAsset?.licenseId) {
        mutableStateOf(selectedAsset?.licenseId.orEmpty())
    }
    var licenseUrl by remember(selectedAsset?.id, selectedAsset?.licenseUrl) {
        mutableStateOf(selectedAsset?.licenseUrl.orEmpty())
    }
    var attribution by remember(selectedAsset?.id, selectedAsset?.attribution) {
        mutableStateOf(selectedAsset?.attribution.orEmpty())
    }
    var sourceReference by remember(selectedAsset?.id, selectedAsset?.sourceReference) {
        mutableStateOf(selectedAsset?.sourceReference.orEmpty())
    }
    var tags by remember(selectedAsset?.id, selectedAsset?.tags) {
        mutableStateOf(selectedAsset?.tags?.joinToString(", ").orEmpty())
    }
    val visibleAssets = index.assets.filter { asset ->
        query.isBlank() ||
            asset.name.contains(query, ignoreCase = true) ||
            asset.kind.name.contains(query, ignoreCase = true) ||
            asset.tags.any { it.contains(query, ignoreCase = true) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Vault", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("A reusable local asset library with provenance and license tracking.", color = MutedText, fontSize = 12.sp)
            }
            StatusPill("${index.assets.size} ASSETS", Cyan)
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("IMPORT ASSET", color = MutedText, fontSize = 10.sp, letterSpacing = 1.1.sp)
                Text("Bring a local file into your Vault", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Files are copied into Kuro Canvas app storage, identified by SHA-256, and indexed locally. Import does not upload them anywhere.",
                    color = MutedText,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                KuroPlatformAssetImportButton(
                    rootPath = storageRoot,
                    enabled = importEnabled,
                    onResult = onImported
                )
                if (warning != null) {
                    Text(warning, color = Amber, fontSize = 11.sp, lineHeight = 16.sp)
                }
                if (message != null) {
                    Text(message, color = if (message.contains("unreviewed", ignoreCase = true)) Amber else MutedText, fontSize = 11.sp, lineHeight = 16.sp)
                }
                Text(
                    "Imported assets default to UNREVIEWED license status. Confirm the source, license, attribution and redistribution rights before sharing or publishing them.",
                    color = Amber,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        if (index.assets.isEmpty()) {
            Surface(color = Panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        Modifier.size(66.dp).clip(RoundedCornerShape(22.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF2A2543), Color(0xFF173442)))),
                        contentAlignment = Alignment.Center
                    ) { Text("◈", color = Cyan, fontSize = 28.sp) }
                    Text("Your Vault is empty", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Imported characters, outfits, poses, props, environments, 3D models, brushes, textures and templates will be listed here. Nothing is generated or downloaded automatically.",
                        color = MutedText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search Vault by name, type or tag") },
                singleLine = true
            )
            Text("${visibleAssets.size} matching asset(s)", color = MutedText, fontSize = 11.sp)
            visibleAssets.forEach { asset ->
                Surface(
                    color = if (asset.id == selectedAssetId) Color(0xFF28223F) else Panel,
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                        .clickable { selectedAssetId = asset.id }
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(asset.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(asset.kind.name.lowercase().replace('_', ' '), color = MutedText, fontSize = 10.sp)
                            }
                            StatusPill(if (asset.licenseId == "UNREVIEWED") "LICENSE REVIEW" else asset.licenseId, if (asset.licenseId == "UNREVIEWED") Amber else Green)
                        }
                        Text("${asset.mimeType} • ${formatVaultSize(asset.byteSize)}", color = MutedText, fontSize = 10.sp)
                        Text("Path: ${asset.relativePath}", color = MutedText, fontSize = 10.sp)
                        Text("SHA-256: ${asset.sha256}", color = MutedText, fontSize = 9.sp)
                        if (asset.tags.isNotEmpty()) Text(asset.tags.joinToString(" · "), color = Cyan, fontSize = 10.sp)
                        Text("Select to edit license and provenance metadata.", color = MutedText, fontSize = 10.sp)
                    }
                }
            }
            if (selectedAsset != null) {
                Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("ASSET RIGHTS & PROVENANCE", color = MutedText, fontSize = 10.sp, letterSpacing = 1.1.sp)
                        Text(selectedAsset.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = licenseId,
                            onValueChange = { licenseId = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("License identifier (e.g. CC BY 4.0)") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = licenseUrl,
                            onValueChange = { licenseUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("License URL") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = attribution,
                            onValueChange = { attribution = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Required attribution") }
                        )
                        OutlinedTextField(
                            value = sourceReference,
                            onValueChange = { sourceReference = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Source reference / provenance") }
                        )
                        OutlinedTextField(
                            value = tags,
                            onValueChange = { tags = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Tags separated by commas") },
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                onUpdateAsset(
                                    selectedAsset.copy(
                                        licenseId = licenseId.trim().ifBlank { "UNREVIEWED" },
                                        licenseUrl = licenseUrl.trim(),
                                        attribution = attribution.trim(),
                                        sourceReference = sourceReference.trim(),
                                        tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                                    )
                                )
                            },
                            enabled = importEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save rights & metadata") }
                        Text(
                            "This editor records your supplied information; it does not independently verify licenses.",
                            color = MutedText,
                            fontSize = 10.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatVaultSize(bytes: Long): String = when {
    bytes < 1024L -> "${bytes} B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    bytes < 1024L * 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    else -> "${bytes / (1024L * 1024L * 1024L)} GB"
}

@Composable
private fun PlannedWorkspace(title: String, description: String, icon: String) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Surface(color = Panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF28223F)), contentAlignment = Alignment.Center) {
                    Text(icon, color = Violet, fontSize = 23.sp)
                }
                Text("Foundation planned", color = Amber, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(description, color = MutedText, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun KuroPanel(
    modifier: Modifier = Modifier,
    project: KuroProjectState,
    modelManifest: KuroModelManifest,
    modelPath: String?,
    modelLoaded: Boolean,
    modelBusy: Boolean,
    modelMessage: String,
    modelProgress: KuroModelDownloadProgress?,
    lastPrompt: String,
    reply: String,
    replyError: String?,
    isGenerating: Boolean,
    onClose: () -> Unit,
    onAddPanel: () -> Unit,
    onDownloadModel: () -> Unit,
    onCancelDownload: () -> Unit,
    onLoadModel: () -> Unit,
    onUnloadModel: () -> Unit,
    onSendMessage: (String, Int, String, Set<String>) -> Unit,
    pendingAction: KuroNativeActionProposal?,
    onApplyAction: (KuroNativeActionProposal) -> OperationStatus,
    onDismissAction: () -> Unit
) {
    var draft by remember(project.projectId) { mutableStateOf("") }
    var contextChapterText by remember(project.projectId) { mutableStateOf("1") }
    var contextBranch by remember(project.projectId) { mutableStateOf("main") }
    var entitySearch by remember(project.projectId) { mutableStateOf("") }
    var contextSelectionMessage by remember(project.projectId) { mutableStateOf<String?>(null) }
    var nativeActionMessage by remember { mutableStateOf<String?>(null) }
    var selectedEntityIds by remember(project.projectId, project.storyEntities.map { it.id }) {
        mutableStateOf(project.storyEntities.take(12).map { it.id }.toSet())
    }
    val visibleEntities = project.storyEntities
        .filter { entity -> entitySearch.isBlank() || entity.name.contains(entitySearch, ignoreCase = true) }
        .take(24)
    val usableSelectedEntityIds = selectedEntityIds.intersect(project.storyEntities.map { it.id }.toSet())
    val progressPercent = modelProgress?.percent ?: 0

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .border(1.dp, Color(0xFF514578), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        color = Raised,
        shadowElevation = 16.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(15.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Cyan, Violet, Color(0xFF25213D)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("K", color = Ink, fontWeight = FontWeight.Black, fontSize = 17.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Kuro", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Creative companion", color = MutedText, fontSize = 10.sp)
                }
                StatusPill(
                    when {
                        modelLoaded -> "ON-DEVICE"
                        modelPath != null -> "LOCAL READY"
                        else -> "NO MODEL"
                    },
                    if (modelLoaded) Green else Amber
                )
                TextButton(onClick = onClose) { Text("Close", color = MutedText, fontSize = 11.sp) }
            }

            Surface(color = Color(0xFF10131D), shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LOCAL MODEL", color = MutedText, fontSize = 10.sp, letterSpacing = 1.1.sp)
                    Text(modelManifest.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "An on-device language model for Kuro chat and story context. It does not draw images; native Kuro Canvas tools remain responsible for editable work.",
                        color = MutedText,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    Text(
                        "≈614 MB download · ${modelManifest.contextLength}-token context · ${modelManifest.licenseId}",
                        color = Cyan,
                        fontSize = 10.sp
                    )
                    Text("Source: ${modelManifest.repositoryUrl}", color = MutedText, fontSize = 9.sp)
                    Text("Model files are verified by SHA-256 before installation and stay in app-private storage.", color = MutedText, fontSize = 10.sp, lineHeight = 15.sp)

                    if (modelBusy && modelProgress != null) {
                        Box(
                            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)).background(Line)
                        ) {
                            Box(
                                Modifier.fillMaxWidth((progressPercent / 100f).coerceIn(0f, 1f))
                                    .fillMaxHeight().background(Cyan)
                            )
                        }
                        Text(
                            "$progressPercent% · ${formatVaultSize(modelProgress.receivedBytes)} of ${formatVaultSize(modelProgress.totalBytes)}",
                            color = MutedText,
                            fontSize = 10.sp
                        )
                    } else if (modelBusy) {
                        Text("Preparing the local runtime…", color = MutedText, fontSize = 10.sp)
                    }

                    when {
                        modelBusy && modelProgress != null -> {
                            OutlinedButton(
                                onClick = onCancelDownload,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Cancel download") }
                        }
                        modelBusy -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Working…") }
                        }
                        modelLoaded -> {
                            Button(
                                onClick = onUnloadModel,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Raised, contentColor = PrimaryText)
                            ) { Text("Unload model · release memory") }
                        }
                        modelPath != null -> {
                            Button(
                                onClick = onLoadModel,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Ink)
                            ) { Text("Load local model") }
                        }
                        else -> {
                            Button(
                                onClick = onDownloadModel,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Ink)
                            ) { Text("Download free local model") }
                        }
                    }
                    Text(modelMessage, color = if (modelMessage.contains("failed", ignoreCase = true) || modelMessage.contains("could not", ignoreCase = true)) Amber else MutedText, fontSize = 10.sp, lineHeight = 15.sp)
                }
            }

            Text("Story context", color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Select only the story entities relevant to this turn. Kuro retrieves their chapter- and branch-scoped canon, active relationships, and recent events instead of sending the entire story history.",
                color = MutedText,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = contextChapterText,
                    onValueChange = { contextChapterText = it; contextSelectionMessage = null },
                    modifier = Modifier.weight(1f),
                    label = { Text("Chapter") },
                    singleLine = true,
                    enabled = modelLoaded && !isGenerating
                )
                OutlinedTextField(
                    value = contextBranch,
                    onValueChange = { contextBranch = it; contextSelectionMessage = null },
                    modifier = Modifier.weight(1.4f),
                    label = { Text("Timeline branch") },
                    singleLine = true,
                    enabled = modelLoaded && !isGenerating
                )
            }
            OutlinedTextField(
                value = entitySearch,
                onValueChange = { entitySearch = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Find a story entity") },
                singleLine = true,
                enabled = project.storyEntities.isNotEmpty() && modelLoaded && !isGenerating
            )
            if (visibleEntities.isEmpty()) {
                Text("No story entities match yet. The model can still help with general creative questions.", color = MutedText, fontSize = 10.sp)
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    visibleEntities.forEach { entity ->
                        val selected = entity.id in selectedEntityIds
                        ToolChip(entity.name, selected) {
                            if (selected) {
                                selectedEntityIds = selectedEntityIds - entity.id
                                contextSelectionMessage = null
                            } else if (selectedEntityIds.size >= 24) {
                                contextSelectionMessage = "Choose up to 24 relevant entities for a bounded context."
                            } else {
                                selectedEntityIds = selectedEntityIds + entity.id
                                contextSelectionMessage = null
                            }
                        }
                    }
                }
            }
            Text(
                "${usableSelectedEntityIds.size} character(s) selected · ${project.relationships.size} relationship record(s) · ${project.storyEvents.size} story event(s)",
                color = MutedText,
                fontSize = 10.sp
            )
            if (contextSelectionMessage != null) {
                Text(contextSelectionMessage.orEmpty(), color = Amber, fontSize = 10.sp)
            }

            DividerLine()
            Text("Chat with Kuro", color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (lastPrompt.isNotBlank()) {
                Surface(color = Color(0xFF25213D), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("YOU", color = Cyan, fontSize = 9.sp, letterSpacing = 1.sp)
                        Text(lastPrompt, color = PrimaryText, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
            if (reply.isNotBlank()) {
                Surface(color = Color(0xFF10131D), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("KURO · LOCAL", color = Violet, fontSize = 9.sp, letterSpacing = 1.sp)
                        Text(reply, color = PrimaryText, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
            if (pendingAction != null) {
                Surface(color = Color(0xFF211D32), shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("NATIVE ACTION PROPOSAL", color = Violet, fontSize = 10.sp, letterSpacing = 1.1.sp)
                        Text(pendingAction.summary, color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (pendingAction.type) {
                                KuroNativeActionType.ADD_STORYBOARD_PANEL ->
                                    "Nothing has changed yet. Approving this will add one storyboard panel to the current project and save it."
                                KuroNativeActionType.ADD_STORY_ENTITY ->
                                    "Nothing has changed yet. Approving this will add a ${pendingAction.entityType?.name?.lowercase()?.replace('_', ' ')} named ${pendingAction.entityName} to the story graph and save it."
                                KuroNativeActionType.ADD_CANON_FACT ->
                                    "Nothing has changed yet. Approving this will record ${pendingAction.predicate}: ${pendingAction.value} for ${pendingAction.subjectId}, chapters ${pendingAction.fromChapter}–${pendingAction.untilChapterExclusive?.minus(1) ?: "ongoing"} on branch ${pendingAction.branchId}."
                            },
                            color = MutedText,
                            fontSize = 10.sp,
                            lineHeight = 15.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val status = onApplyAction(pendingAction)
                                    nativeActionMessage = if (status == OperationStatus.COMMITTED) {
                                        "Approved and saved. The project revision was updated."
                                    } else {
                                        "Not applied: ${status.name.lowercase().replace('_', ' ')}."
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Ink)
                            ) { Text("Approve & apply") }
                            OutlinedButton(
                                onClick = {
                                    onDismissAction()
                                    nativeActionMessage = "Proposal dismissed. The project is unchanged."
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Dismiss") }
                        }
                    }
                }
            }
            if (nativeActionMessage != null) {
                Text(nativeActionMessage.orEmpty(), color = MutedText, fontSize = 10.sp, lineHeight = 15.sp)
            }
            if (isGenerating) {
                Text("Kuro is thinking on your device…", color = Cyan, fontSize = 10.sp)
            }
            if (!replyError.isNullOrBlank()) {
                Text(replyError, color = if (replyError.contains("failed", ignoreCase = true) || replyError.contains("could not", ignoreCase = true)) Amber else MutedText, fontSize = 10.sp, lineHeight = 15.sp)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ask Kuro about your story or creative plan…", fontSize = 12.sp) },
                enabled = modelLoaded && !isGenerating && !modelBusy,
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(14.dp)
            )
            Button(
                onClick = {
                    val chapter = contextChapterText.toIntOrNull()
                    when {
                        chapter == null || chapter < 1 -> contextSelectionMessage = "Enter a chapter number greater than zero."
                        contextBranch.isBlank() -> contextSelectionMessage = "Enter a timeline branch."
                        draft.isBlank() -> contextSelectionMessage = "Write a message first."
                        else -> {
                            nativeActionMessage = null
                            onSendMessage(draft, chapter, contextBranch.trim(), usableSelectedEntityIds)
                            draft = ""
                            contextSelectionMessage = null
                        }
                    }
                },
                enabled = modelLoaded && !isGenerating && !modelBusy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (isGenerating) "Kuro is thinking…" else "Send to Kuro") }

            Text(
                "Responses are suggestions, not canon. Any supported native action appears as a proposal and requires your approval; chat alone never changes canon.",
                color = MutedText,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
            DividerLine()
            Text("Native project action", color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Kuro can propose adding a storyboard panel, a story entity, or one time-scoped canon fact. Every proposal is validated, revision-checked and only applied after you approve it.",
                color = MutedText,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
            Button(
                onClick = onAddPanel,
                colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Ink),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add storyboard panel")
            }
            Text("Voice session is not connected yet.", color = MutedText, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ToolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Color(0xFF332B50) else Panel,
        shape = RoundedCornerShape(11.dp),
        modifier = Modifier.clip(RoundedCornerShape(11.dp)).clickable(onClick = onClick).border(1.dp, if (selected) Violet else Line, RoundedCornerShape(11.dp))
    ) {
        Text(label, color = if (selected) PrimaryText else MutedText, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun TinyChip(text: String) {
    Surface(color = Color(0xFF1C2030), shape = RoundedCornerShape(7.dp)) {
        Text(text, color = Color(0xFFAAAFC1), fontSize = 8.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp))
    }
}

@Composable
private fun StatusPill(label: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(label, color = tint, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MutedText, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, color = PrimaryText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DividerLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
}
