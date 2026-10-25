package com.kurocanvas.project

import kotlin.test.Test
import kotlin.test.assertEquals

class KuroStrokeOperationTest {
    @Test
    fun addStrokeCommitsAndUndoRemovesWithAudit() {
        val project = KuroProjectState.empty(projectId = "stroke-test")
        val stroke = KuroStroke(
            id = "stroke-1",
            points = listOf(KuroStrokePoint(0.1f, 0.2f), KuroStrokePoint(0.8f, 0.9f)),
            colorArgb = -1,
            widthRatio = 0.004f
        )
        val broker = KuroOperationBroker()
        val added = broker.execute(project, AddCanvasStroke("add-1", project.projectId, 0L, stroke))
        assertEquals(OperationStatus.COMMITTED, added.status)
        assertEquals(stroke, added.project.strokes.single())

        val undone = broker.execute(
            added.project,
            UndoAddedCanvasStroke("undo-1", project.projectId, added.project.revision, "add-1")
        )
        assertEquals(OperationStatus.COMMITTED, undone.status)
        assertEquals(emptyList(), undone.project.strokes)
        assertEquals("undo_add_canvas_stroke", undone.project.auditLog.last().action)
    }

    @Test
    fun rejectsCoordinatesOutsideNormalizedCanvas() {
        val project = KuroProjectState.empty(projectId = "stroke-test")
        val stroke = KuroStroke(
            "invalid",
            listOf(KuroStrokePoint(-0.1f, 0.2f), KuroStrokePoint(1.2f, 0.9f)),
            -1,
            0.004f
        )
        val result = KuroOperationBroker().execute(
            project, AddCanvasStroke("add-invalid", project.projectId, 0L, stroke)
        )
        assertEquals(OperationStatus.INVALID_STROKE, result.status)
        assertEquals(project, result.project)
    }
}
