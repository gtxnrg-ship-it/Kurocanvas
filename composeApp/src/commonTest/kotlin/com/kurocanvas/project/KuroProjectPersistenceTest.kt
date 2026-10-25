package com.kurocanvas.project

import kotlin.test.Test
import kotlin.test.assertEquals

class KuroProjectPersistenceTest {
    @Test
    fun codecRoundTripsNativeProjectPanelsStrokesAndAuditHistory() {
        val initial = KuroProjectState.empty(projectId = "persist-test", title = "Sample chapter")
        val stroke = KuroStroke(
            id = "stroke-persist-1",
            points = listOf(KuroStrokePoint(0.12f, 0.3f), KuroStrokePoint(0.86f, 0.91f)),
            colorArgb = -1,
            widthRatio = 0.004f
        )
        val broker = KuroOperationBroker()
        val panelResult = broker.execute(
            initial,
            AddStoryboardPanel("persist-panel-1", initial.projectId, initial.revision, actor = "test")
        )
        val strokeResult = broker.execute(
            panelResult.project,
            AddCanvasStroke(
                "persist-stroke-1",
                panelResult.project.projectId,
                panelResult.project.revision,
                stroke,
                actor = "test"
            )
        )
        assertEquals(OperationStatus.COMMITTED, strokeResult.status)

        val decoded = KuroProjectCodec.decode(KuroProjectCodec.encode(strokeResult.project))

        assertEquals(strokeResult.project, decoded)
        assertEquals("Sample chapter", decoded.title)
        assertEquals(1, decoded.panels.size)
        assertEquals(stroke, decoded.strokes.single())
        assertEquals(2, decoded.auditLog.size)
    }

    @Test
    fun codecIgnoresUnknownFieldsForForwardCompatibility() {
        val base = KuroProjectState.empty(projectId = "forward-test")
        val withUnknownField = KuroProjectCodec.encode(base).dropLast(1) + ",\n  \"futureField\": true\n}"
        assertEquals(base, KuroProjectCodec.decode(withUnknownField))
    }

    @Test
    fun savedProjectsWithoutStrokeFieldRemainReadable() {
        val legacyJson = """{
            "projectId":"old-project",
            "title":"Old project",
            "revision":0,
            "panels":[],
            "auditLog":[],
            "nextPanelNumber":1
        }""".trimIndent()

        val loaded = KuroProjectCodec.decode(legacyJson)

        assertEquals("old-project", loaded.projectId)
        assertEquals(emptyList(), loaded.strokes)
    }
}
