package com.kurocanvas.project

data class StoryboardPanel(
    val id: String,
    val order: Int,
    val createdRevision: Long
)

data class KuroAuditEvent(
    val operationId: String,
    val revision: Long,
    val actor: String,
    val action: String,
    val targetId: String,
    val summary: String
)

data class KuroProjectState(
    val projectId: String,
    val title: String,
    val revision: Long,
    val panels: List<StoryboardPanel>,
    val auditLog: List<KuroAuditEvent>,
    val nextPanelNumber: Int
) {
    companion object {
        fun empty(
            projectId: String = "local-project",
            title: String = "Untitled project"
        ) = KuroProjectState(
            projectId = projectId,
            title = title,
            revision = 0L,
            panels = emptyList(),
            auditLog = emptyList(),
            nextPanelNumber = 1
        )
    }
}

sealed interface KuroOperation {
    val operationId: String
    val projectId: String
    val expectedRevision: Long
    val actor: String
}

data class AddStoryboardPanel(
    override val operationId: String,
    override val projectId: String,
    override val expectedRevision: Long,
    override val actor: String = "user"
) : KuroOperation

enum class OperationStatus {
    COMMITTED,
    REVISION_CONFLICT,
    WRONG_PROJECT,
    DUPLICATE_OPERATION
}

data class KuroOperationResult(
    val status: OperationStatus,
    val project: KuroProjectState,
    val message: String,
    val affectedObjectId: String? = null
)

/**
 * Deterministic operation broker for native project objects.
 *
 * This first implementation has no model, disk persistence, plugin access, or arbitrary code
 * execution. Every operation is typed, revision-checked, and recorded in the project audit log.
 */
class KuroOperationBroker {
    fun execute(
        current: KuroProjectState,
        operation: KuroOperation
    ): KuroOperationResult {
        if (operation.projectId != current.projectId) {
            return KuroOperationResult(
                status = OperationStatus.WRONG_PROJECT,
                project = current,
                message = "Operation targets a different project."
            )
        }

        if (current.auditLog.any { it.operationId == operation.operationId }) {
            return KuroOperationResult(
                status = OperationStatus.DUPLICATE_OPERATION,
                project = current,
                message = "This operation was already applied."
            )
        }

        if (operation.expectedRevision != current.revision) {
            return KuroOperationResult(
                status = OperationStatus.REVISION_CONFLICT,
                project = current,
                message = "The project changed after this operation was prepared. Review and retry against the latest revision."
            )
        }

        return when (operation) {
            is AddStoryboardPanel -> addPanel(current, operation)
        }
    }

    private fun addPanel(
        current: KuroProjectState,
        operation: AddStoryboardPanel
    ): KuroOperationResult {
        val newRevision = current.revision + 1
        val panel = StoryboardPanel(
            id = "panel-${current.nextPanelNumber.toString().padStart(4, '0')}",
            order = current.panels.size,
            createdRevision = newRevision
        )
        val next = current.copy(
            revision = newRevision,
            panels = current.panels + panel,
            auditLog = current.auditLog + KuroAuditEvent(
                operationId = operation.operationId,
                revision = newRevision,
                actor = operation.actor,
                action = "add_storyboard_panel",
                targetId = panel.id,
                summary = "Added storyboard panel ${panel.order + 1}."
            ),
            nextPanelNumber = current.nextPanelNumber + 1
        )
        return KuroOperationResult(
            status = OperationStatus.COMMITTED,
            project = next,
            message = "Storyboard panel committed to project state.",
            affectedObjectId = panel.id
        )
    }
}
