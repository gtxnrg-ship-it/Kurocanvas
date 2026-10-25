package com.kurocanvas.project

import kotlin.test.Test
import kotlin.test.assertEquals

class KuroLayerOperationTest {
    private val broker = KuroOperationBroker()

    @Test
    fun layerCreationAndSettingsAreNativeAuditedProjectState() {
        val initial = KuroProjectState.empty(projectId = "layers")
        val added = broker.execute(
            initial,
            AddCanvasLayer(
                "add-layer-1",
                initial.projectId,
                initial.revision,
                KuroLayer(id = "layer-2", name = "Ink", order = 99)
            )
        )
        assertEquals(OperationStatus.COMMITTED, added.status)
        assertEquals(1, added.project.layers.last().order)
        assertEquals("add_canvas_layer", added.project.auditLog.last().action)

        val update = broker.execute(
            added.project,
            UpdateCanvasLayer(
                "update-layer-1",
                added.project.projectId,
                added.project.revision,
                layerId = "layer-2",
                visible = false,
                locked = true,
                opacity = 0.35f
            )
        )
        assertEquals(OperationStatus.COMMITTED, update.status)
        val layer = update.project.layers.single { it.id == "layer-2" }
        assertEquals(false, layer.visible)
        assertEquals(true, layer.locked)
        assertEquals(0.35f, layer.opacity)
        assertEquals(3L, update.project.revision)
    }

    @Test
    fun hiddenOrLockedLayerRejectsNewStrokes() {
        val initial = KuroProjectState.empty(projectId = "layers")
        val added = broker.execute(
            initial,
            AddCanvasLayer("add-layer-1", initial.projectId, 0L, KuroLayer("layer-2", "Ink", 1))
        )
        val hidden = broker.execute(
            added.project,
            UpdateCanvasLayer("update-layer-1", initial.projectId, added.project.revision, "layer-2", visible = false)
        )
        val stroke = KuroStroke(
            "stroke-1",
            listOf(KuroStrokePoint(0.1f, 0.2f), KuroStrokePoint(0.8f, 0.9f)),
            -1,
            0.004f,
            layerId = "layer-2"
        )
        val rejectedHidden = broker.execute(
            hidden.project,
            AddCanvasStroke("add-stroke-hidden", initial.projectId, hidden.project.revision, stroke)
        )
        assertEquals(OperationStatus.LAYER_HIDDEN, rejectedHidden.status)
        assertEquals(hidden.project, rejectedHidden.project)

        val shownAndLocked = broker.execute(
            hidden.project,
            UpdateCanvasLayer("update-layer-2", initial.projectId, hidden.project.revision, "layer-2", visible = true, locked = true)
        )
        val rejectedLocked = broker.execute(
            shownAndLocked.project,
            AddCanvasStroke("add-stroke-locked", initial.projectId, shownAndLocked.project.revision, stroke)
        )
        assertEquals(OperationStatus.LAYER_LOCKED, rejectedLocked.status)
        assertEquals(shownAndLocked.project, rejectedLocked.project)
    }

    @Test
    fun layerCannotBeRemovedWhileStrokesReferenceIt() {
        val initial = KuroProjectState.empty(projectId = "layers")
        val added = broker.execute(
            initial,
            AddCanvasLayer("add-layer-1", initial.projectId, 0L, KuroLayer("layer-2", "Ink", 1))
        )
        val stroke = broker.execute(
            added.project,
            AddCanvasStroke(
                "add-stroke-1",
                initial.projectId,
                added.project.revision,
                KuroStroke(
                    "stroke-1",
                    listOf(KuroStrokePoint(0.1f, 0.2f), KuroStrokePoint(0.8f, 0.9f)),
                    -1,
                    0.004f,
                    layerId = "layer-2"
                )
            )
        )
        val remove = broker.execute(
            stroke.project,
            UndoAddedCanvasLayer(
                "remove-layer-1",
                initial.projectId,
                stroke.project.revision,
                targetOperationId = "add-layer-1"
            )
        )
        assertEquals(OperationStatus.DEPENDENCY_EXISTS, remove.status)
        assertEquals(stroke.project, remove.project)
    }
}
