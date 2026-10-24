package com.kurocanvas.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class KuroOperationBrokerTest {
    private val broker = KuroOperationBroker()

    @Test
    fun addPanelCommitsRevisionAndAuditEvent() {
        val initial = KuroProjectState.empty()
        val result = broker.execute(
            initial,
            AddStoryboardPanel(
                operationId = "op-1",
                projectId = initial.projectId,
                expectedRevision = initial.revision
            )
        )

        assertEquals(OperationStatus.COMMITTED, result.status)
        assertEquals(1L, result.project.revision)
        assertEquals(1, result.project.panels.size)
        assertEquals("panel-0001", result.project.panels.single().id)
        assertEquals("op-1", result.project.auditLog.single().operationId)
        assertEquals("add_storyboard_panel", result.project.auditLog.single().action)
    }

    @Test
    fun staleRevisionIsRejectedWithoutMutation() {
        val initial = KuroProjectState.empty()
        val current = broker.execute(
            initial,
            AddStoryboardPanel("op-1", initial.projectId, initial.revision)
        ).project

        val stale = broker.execute(
            current,
            AddStoryboardPanel("op-stale", current.projectId, expectedRevision = 0L)
        )

        assertEquals(OperationStatus.REVISION_CONFLICT, stale.status)
        assertEquals(current, stale.project)
    }

    @Test
    fun operationForAnotherProjectIsRejected() {
        val initial = KuroProjectState.empty(projectId = "project-a")
        val result = broker.execute(
            initial,
            AddStoryboardPanel("op-1", projectId = "project-b", expectedRevision = 0L)
        )

        assertEquals(OperationStatus.WRONG_PROJECT, result.status)
        assertEquals(initial, result.project)
    }

    @Test
    fun applyingSameOperationTwiceDoesNotDuplicateThePanel() {
        val initial = KuroProjectState.empty()
        val operation = AddStoryboardPanel("op-1", initial.projectId, initial.revision)
        val once = broker.execute(initial, operation)
        val twice = broker.execute(once.project, operation)

        assertEquals(OperationStatus.COMMITTED, once.status)
        assertEquals(OperationStatus.DUPLICATE_OPERATION, twice.status)
        assertEquals(1, twice.project.panels.size)
        assertEquals(once.project, twice.project)
    }

    @Test
    fun undoRemovesPanelButPreservesAuditHistory() {
        val initial = KuroProjectState.empty()
        val add = broker.execute(
            initial,
            AddStoryboardPanel("op-add", initial.projectId, initial.revision)
        )
        val undone = broker.execute(
            add.project,
            UndoAddedStoryboardPanel(
                operationId = "op-undo",
                projectId = add.project.projectId,
                expectedRevision = add.project.revision,
                targetOperationId = "op-add"
            )
        )

        assertEquals(OperationStatus.COMMITTED, undone.status)
        assertEquals(0, undone.project.panels.size)
        assertEquals(2L, undone.project.revision)
        assertEquals(2, undone.project.auditLog.size)
        assertEquals("undo_add_storyboard_panel", undone.project.auditLog.last().action)
        assertEquals("panel-0001", undone.project.auditLog.last().targetId)
    }

    @Test
    fun undonePanelCannotBeUndoneAgain() {
        val initial = KuroProjectState.empty()
        val add = broker.execute(initial, AddStoryboardPanel("op-add", initial.projectId, 0L))
        val undo = broker.execute(
            add.project,
            UndoAddedStoryboardPanel("op-undo-1", add.project.projectId, add.project.revision, "op-add")
        )
        val undoAgain = broker.execute(
            undo.project,
            UndoAddedStoryboardPanel("op-undo-2", undo.project.projectId, undo.project.revision, "op-add")
        )

        assertEquals(OperationStatus.COMMITTED, undo.status)
        assertEquals(OperationStatus.ALREADY_UNDONE, undoAgain.status)
        assertEquals(undo.project, undoAgain.project)
    }

    @Test
    fun invalidUndoTargetDoesNotChangeProject() {
        val initial = KuroProjectState.empty()
        val result = broker.execute(
            initial,
            UndoAddedStoryboardPanel(
                operationId = "op-undo",
                projectId = initial.projectId,
                expectedRevision = 0L,
                targetOperationId = "missing-op"
            )
        )

        assertEquals(OperationStatus.INVALID_UNDO_TARGET, result.status)
        assertEquals(initial, result.project)
        assertNull(result.affectedObjectId)
    }

    @Test
    fun undoDoesNotReusePanelIds() {
        val initial = KuroProjectState.empty()
        val add1 = broker.execute(initial, AddStoryboardPanel("op-add-1", initial.projectId, 0L))
        val undo1 = broker.execute(
            add1.project,
            UndoAddedStoryboardPanel("op-undo-1", add1.project.projectId, add1.project.revision, "op-add-1")
        )
        val add2 = broker.execute(
            undo1.project,
            AddStoryboardPanel("op-add-2", undo1.project.projectId, undo1.project.revision)
        )

        assertEquals(OperationStatus.COMMITTED, add2.status)
        assertNotEquals("panel-0001", add2.project.panels.single().id)
        assertEquals("panel-0002", add2.project.panels.single().id)
    }
}
