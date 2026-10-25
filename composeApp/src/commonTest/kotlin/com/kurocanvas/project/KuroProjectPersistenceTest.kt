package com.kurocanvas.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class KuroProjectPersistenceTest {
    @Test
    fun codecRoundTripsNativeProjectAndAuditHistory() {
        val initial = KuroProjectState.empty(projectId = "persist-test", title = "Sample chapter")
        val add = AddStoryboardPanel(
            operationId = "persist-add-1",
            projectId = initial.projectId,
            expectedRevision = initial.revision,
            actor = "test"
        )
        val result = KuroOperationBroker().execute(initial, add)
        assertEquals(OperationStatus.COMMITTED, result.status)

        val decoded = KuroProjectCodec.decode(KuroProjectCodec.encode(result.project))

        assertEquals(result.project, decoded)
        assertEquals("Sample chapter", decoded.title)
        assertEquals(1, decoded.panels.size)
        assertEquals("persist-add-1", decoded.auditLog.single().operationId)
    }

    @Test
    fun codecIgnoresUnknownFieldsForForwardCompatibility() {
        val base = KuroProjectState.empty(projectId = "forward-test")
        val withUnknownField = KuroProjectCodec.encode(base).dropLast(1) + ",\n  \"futureField\": true\n}"

        val decoded = KuroProjectCodec.decode(withUnknownField)

        assertEquals(base, decoded)
    }
}
