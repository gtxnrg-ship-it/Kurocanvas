package com.kurocanvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import com.kurocanvas.project.AddCanonFact
import com.kurocanvas.project.AddCanvasStroke
import com.kurocanvas.project.AddStoryEntity
import com.kurocanvas.project.AddStoryboardPanel
import com.kurocanvas.project.KuroOperationBroker
import com.kurocanvas.project.KuroCanonFact
import com.kurocanvas.project.KuroProjectState
import com.kurocanvas.project.KuroStroke
import com.kurocanvas.project.KuroStrokePoint
import com.kurocanvas.project.KuroStoryEntity
import com.kurocanvas.project.KuroStoryEntityType
import com.kurocanvas.project.createKuroProjectStorage
import com.kurocanvas.project.OperationStatus
import com.kurocanvas.project.UndoAddedCanvasStroke
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

private data class InkStroke(val points: List<Offset>, val color: Color, val widthRatio: Float)

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
    widthRatio = widthRatio
)

private fun InkStroke.toKuroStroke(id: String): KuroStroke = KuroStroke(
    id = id,
    points = points.map { KuroStrokePoint(it.x, it.y) },
    colorArgb = color.toArgbInt(),
    widthRatio = widthRatio
)

@Composable
fun App(storageRoot: String) {
    var section by remember { mutableStateOf(Section.Create) }
    var showKuro by remember { mutableStateOf(false) }
    val projectStorage = remember(storageRoot) { createKuroProjectStorage(storageRoot) }
    val initialLoad = remember(projectStorage) { projectStorage.load() }
    var project by remember(projectStorage) {
        mutableStateOf(initialLoad.state ?: KuroProjectState.empty())
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
    val addStoryCharacter: (String) -> OperationStatus = { name ->
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
                    type = KuroStoryEntityType.CHARACTER
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
                                demoPanels = project.panels.size,
                                tool = tool,
                                onTool = { tool = it },
                                brushSize = brushSize,
                                onBrushSize = { brushSize = it },
                                onStroke = { newInkStroke ->
                                    val nativeStroke = newInkStroke.toKuroStroke("stroke-${project.revision + 1}")
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
                            Section.Story -> StoryWorkspace(project, addStoryCharacter, addCanonFact)
                            Section.Vault -> VaultWorkspace()
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
                            onAddPanel = addStoryboardPanel
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
                    onAddPanel = addStoryboardPanel
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
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, Line, RoundedCornerShape(18.dp))
        ) {
            SketchCanvas(
                Modifier.fillMaxSize(),
                strokes = strokes,
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
    brushSize: Float,
    panelCount: Int,
    eraser: Boolean,
    livePoints: List<Offset>,
    onLivePoints: (List<Offset>) -> Unit,
    onStroke: (InkStroke) -> Unit
) {
    Canvas(
        modifier.background(Color(0xFF111522)).pointerInput(brushSize, eraser) {
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
                            widthRatio = (if (eraser) brushSize * 3f else brushSize) / minDimension
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
        val previewColor = if (eraser) Color(0xFFF5F1EA) else Color(0xFF25283B)
        val visibleStrokes = if (livePoints.isNotEmpty()) {
            strokes + InkStroke(livePoints, previewColor, (if (eraser) brushSize * 3f else brushSize) / minOf(size.width, size.height).coerceAtLeast(1f))
        } else {
            strokes
        }
        val minDimension = minOf(size.width, size.height)
        visibleStrokes.forEach { stroke ->
            val points = stroke.points.map { Offset(it.x * size.width, it.y * size.height) }
            val width = (stroke.widthRatio * minDimension).coerceAtLeast(1f)
            if (points.size == 1) drawCircle(stroke.color, width / 2f, points.first())
            for (pointIndex in 0 until points.lastIndex) {
                drawLine(stroke.color, points[pointIndex], points[pointIndex + 1], width, StrokeCap.Round)
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
    onAddCharacter: (String) -> OperationStatus,
    onAddFact: (String, String, String, Int, Int?, String) -> OperationStatus
) {
    var characterName by remember(project.projectId) { mutableStateOf("") }
    var selectedEntityId by remember(project.projectId) {
        mutableStateOf(project.storyEntities.firstOrNull()?.id.orEmpty())
    }
    var predicate by remember { mutableStateOf("Hair color") }
    var value by remember { mutableStateOf("") }
    var fromChapter by remember { mutableStateOf("1") }
    var untilChapter by remember { mutableStateOf("") }
    var branchId by remember { mutableStateOf("main") }
    var message by remember { mutableStateOf<String?>(null) }

    val selectedEntity = project.storyEntities.firstOrNull { it.id == selectedEntityId }
    val entityFacts = project.canonFacts
        .filter { it.subjectId == selectedEntityId && it.status == com.kurocanvas.project.KuroCanonFactStatus.CONFIRMED }
        .sortedBy { it.fromChapter }

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
                Text("CHARACTER VAULT", color = MutedText, fontSize = 10.sp, letterSpacing = 1.1.sp)
                Text("Add a character", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = characterName,
                    onValueChange = { characterName = it; message = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Character name") },
                    singleLine = true
                )
                Button(
                    onClick = {
                        val newId = "entity-${project.revision + 1}"
                        val status = onAddCharacter(characterName)
                        message = when (status) {
                            OperationStatus.COMMITTED -> "Character saved to this project."
                            OperationStatus.DUPLICATE_OBJECT_ID -> "That character already exists, or the new item ID is not unique."
                            OperationStatus.INVALID_STORY_ENTITY -> "Enter a character name first."
                            else -> "Could not save this character: ${status.name.lowercase().replace('_', ' ')}."
                        }
                        if (status == OperationStatus.COMMITTED) {
                            selectedEntityId = newId
                            characterName = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add character") }
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

        Text(
            "Facts are stored separately from the conversation. They can be resolved by character, property, chapter and branch when Kuro's local context compiler is connected.",
            color = MutedText,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun VaultWorkspace() {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text("Vault", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("One identity, many reusable representations and variants.", color = MutedText, fontSize = 13.sp)
        Surface(color = Panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF2A2543), Color(0xFF173442)))), contentAlignment = Alignment.Center) {
                    Text("◈", color = Cyan, fontSize = 30.sp)
                }
                Spacer(Modifier.height(18.dp))
                Text("Your Vault is empty", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Saved characters, outfits, poses, props, environments, 3D models, brushes and templates will appear here. This prototype has not generated or downloaded assets.", color = MutedText, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
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
private fun KuroPanel(modifier: Modifier = Modifier, onClose: () -> Unit, onAddPanel: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).border(1.dp, Color(0xFF514578), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        color = Raised,
        shadowElevation = 16.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Cyan, Violet, Color(0xFF25213D))),), contentAlignment = Alignment.Center) {
                    Text("K", color = Ink, fontWeight = FontWeight.Black, fontSize = 17.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Kuro", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Creative companion", color = MutedText, fontSize = 10.sp)
                }
                StatusPill("OFFLINE", Amber)
                TextButton(onClick = onClose) { Text("Close", color = MutedText, fontSize = 11.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StatusPill("Canvas ready", Green)
                StatusPill("Model not connected", Amber)
            }
            Surface(color = Color(0xFF10131D), shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Kuro runtime status", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("The local language model and voice runtime are not connected yet. Project data and drawing operations stay on this device; no prompt is sent anywhere from this prototype.", color = MutedText, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
            Text("Native-operation preview", color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("This commits a typed storyboard-panel object through the revision-checked operation broker, increments the project revision and audit history, then autosaves the project locally. The language model is still not connected.", color = MutedText, fontSize = 11.sp, lineHeight = 16.sp)
            Button(onClick = onAddPanel, colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Ink), modifier = Modifier.fillMaxWidth()) {
                Text("Add storyboard panel • demo tool", fontSize = 12.sp)
            }
            DividerLine()
            OutlinedTextField(
                value = "",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Tell Kuro what you want to build…", fontSize = 12.sp) },
                enabled = false,
                shape = RoundedCornerShape(14.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Voice session not connected", color = MutedText, fontSize = 10.sp, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = {}, enabled = false, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)) { Text("◉ Voice", fontSize = 11.sp) }
                Spacer(Modifier.width(7.dp))
                Button(onClick = {}, enabled = false, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text("Send", fontSize = 11.sp) }
            }
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
