package com.kurocanvas.project

import kotlin.test.Test
import kotlin.test.assertEquals

class KuroProjectRenameTest {
    @Test
    fun renameUpdatesTitleRevisionAndAudit() {
        val broker = KuroOperationBroker()
        val project = KuroProjectState.empty(projectId = "rename", title = "Untitled chapter")
        val result = broker.execute(
            project,
            RenameProject(
                operationId = "rename-1",
                projectId = project.projectId,
                expectedRevision = project.revision,
                title = "  The First Chapter  "
            )
        )

        assertEquals(OperationStatus.COMMITTED, result.status)
        assertEquals("The First Chapter", result.project.title)
        assertEquals(1L, result.project.revision)
        assertEquals("rename_project", result.project.auditLog.single().action)
        assertEquals(project, broker.execute(
            project,
            RenameProject("rename-2", project.projectId, project.revision, "   ")
        ).project)
    }

    @Test
    fun rejectsBlankOrOverlongTitleWithoutMutatingProject() {
        val broker = KuroOperationBroker()
        val project = KuroProjectState.empty(projectId = "rename")
        val blank = broker.execute(
            project,
            RenameProject("rename-blank", project.projectId, project.revision, " \n ")
        )
        val long = broker.execute(
            project,
            RenameProject("rename-long", project.projectId, project.revision, "x".repeat(121))
        )

        assertEquals(OperationStatus.INVALID_PROJECT_TITLE, blank.status)
        assertEquals(OperationStatus.INVALID_PROJECT_TITLE, long.status)
        assertEquals(project, blank.project)
        assertEquals(project, long.project)
    }

    @Test
    fun rejectsStaleRenameRevision() {
        val broker = KuroOperationBroker()
        val project = KuroProjectState.empty(projectId = "rename")
        val renamed = broker.execute(
            project,
            RenameProject("rename-1", project.projectId, project.revision, "Chapter One")
        )
        val stale = broker.execute(
            project,
            RenameProject("rename-2", project.projectId, project.revision, "Chapter Two")
        )

        assertEquals(OperationStatus.COMMITTED, renamed.status)
        assertEquals(OperationStatus.REVISION_CONFLICT, stale.status)
        assertEquals(project, stale.project)
    }
}
