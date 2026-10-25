package com.kurocanvas.project

import kotlinx.serialization.Serializable

@Serializable
data class StoryboardPanel(
    val id: String,
    val order: Int,
    val createdRevision: Long
)

@Serializable
data class KuroAuditEvent(
    val operationId: String,
    val revision: Long,
    val actor: String,
    val action: String,
    val targetId: String,
    val summary: String
)

@Serializable
data class KuroStrokePoint(
    val x: Float,
    val y: Float
)

@Serializable
data class KuroStroke(
    val id: String,
    val points: List<KuroStrokePoint>,
    val colorArgb: Int,
    /** Width as a fraction of the drawing area's shorter dimension. */
    val widthRatio: Float
)

@Serializable
data class KuroProjectState(
    val projectId: String,
    val title: String,
    val revision: Long,
    val panels: List<StoryboardPanel>,
    val auditLog: List<KuroAuditEvent>,
    val nextPanelNumber: Int,
    val strokes: List<KuroStroke> = emptyList()
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
            nextPanelNumber = 1,
            strokes = emptyList()
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

data class UndoAddedStoryboardPanel(
    override val operationId: String,
    override val projectId: String,
    override val expectedRevision: Long,
    val targetOperationId: String,
    override val actor: String = "user"
) : KuroOperation

data class AddCanvasStroke(
    override val operationId: String,
    override val projectId: String,
    override val expectedRevision: Long,
    val stroke: KuroStroke,
    override val actor: String = "user"
) : KuroOperation

data class UndoAddedCanvasStroke(
    override val operationId: String,
    override val projectId: String,
    override val expectedRevision: Long,
    val targetOperationId: String,
    override val actor: String = "user"
) : KuroOperation

enum class OperationStatus {
    COMMITTED,
    REVISION_CONFLICT,
    WRONG_PROJECT,
    DUPLICATE_OPERATION,
    INVALID_UNDO_TARGET,
    ALREADY_UNDONE,
    INVALID_STROKE
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
            is UndoAddedStoryboardPanel -> undoAddedPanel(current, operation)
            is AddCanvasStroke -> addStroke(current, operation)
            is UndoAddedCanvasStroke -> undoAddedStroke(current, operation)
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

    private fun undoAddedPanel(
        current: KuroProjectState,
        operation: UndoAddedStoryboardPanel
    ): KuroOperationResult {
        val targetEvent = current.auditLog.lastOrNull { it.operationId == operation.targetOperationId }
        if (targetEvent == null || targetEvent.action != "add_storyboard_panel") {
            return KuroOperationResult(
                status = OperationStatus.INVALID_UNDO_TARGET,
                project = current,
                message = "Only a committed panel-add operation can be undone by this command."
            )
        }

        val panelId = targetEvent.targetId
        if (current.auditLog.any { it.action == "undo_add_storyboard_panel" && it.targetId == panelId }) {
            return KuroOperationResult(
                status = OperationStatus.ALREADY_UNDONE,
                project = current,
                message = "This panel-add operation has already been undone."
            )
        }
        if (current.panels.none { it.id == panelId }) {
            return KuroOperationResult(
                status = OperationStatus.INVALID_UNDO_TARGET,
                project = current,
                message = "The target panel no longer exists in the current project state."
            )
        }

        val newRevision = current.revision + 1
        val next = current.copy(
            revision = newRevision,
            panels = current.panels.filterNot { it.id == panelId }
                .mapIndexed { index, panel -> panel.copy(order = index) },
            auditLog = current.auditLog + KuroAuditEvent(
                operationId = operation.operationId,
                revision = newRevision,
                actor = operation.actor,
                action = "undo_add_storyboard_panel",
                targetId = panelId,
                summary = "Undid creation of $panelId."
            )
        )
        return KuroOperationResult(
            status = OperationStatus.COMMITTED,
            project = next,
            message = "Panel creation was undone and retained in the audit log.",
            affectedObjectId = panelId
        )
    }
    private fun addStroke(
        current: KuroProjectState,
        operation: AddCanvasStroke
    ): KuroOperationResult {
        val stroke = operation.stroke
        if (stroke.points.size < 2 ||
            stroke.points.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..1f || it.y !in 0f..1f } ||
            !stroke.widthRatio.isFinite() || stroke.widthRatio <= 0f ||
            stroke.colorArgb ushr 24 == 0
        ) {
            return KuroOperationResult(
                status = OperationStatus.INVALID_STROKE,
                project = current,
                message = "The stroke must contain finite normalized points and a positive width."
            )
        }
        if (current.strokes.any { it.id == stroke.id }) {
            return KuroOperationResult(
                status = OperationStatus.DUPLICATE_OPERATION,
                project = current,
                message = "A stroke with this ID already exists."
            )
        }
        val revision = current.revision + 1
        val next = current.copy(
            revision = revision,
            strokes = current.strokes + stroke,
            auditLog = current.auditLog + KuroAuditEvent(
                operationId = operation.operationId,
                revision = revision,
                actor = operation.actor,
                action = "add_canvas_stroke",
                targetId = stroke.id,
                summary = "Added editable canvas stroke."
            )
        )
        return KuroOperationResult(
            status = OperationStatus.COMMITTED,
            project = next,
            message = "Canvas stroke saved to project state.",
            affectedObjectId = stroke.id
        )
    }

    private fun undoAddedStroke(
        current: KuroProjectState,
        operation: UndoAddedCanvasStroke
    ): KuroOperationResult {
        val targetEvent = current.auditLog.lastOrNull { it.operationId == operation.targetOperationId }
        if (targetEvent == null || targetEvent.action != "add_canvas_stroke") {
            return KuroOperationResult(
                status = OperationStatus.INVALID_UNDO_TARGET,
                project = current,
                message = "Only a committed canvas-stroke operation can be undone by this command."
            )
        }
        val strokeId = targetEvent.targetId
        if (current.auditLog.any { it.action == "undo_add_canvas_stroke" && it.targetId == strokeId }) {
            return KuroOperationResult(
                status = OperationStatus.ALREADY_UNDONE,
                project = current,
                message = "This canvas stroke has already been undone."
            )
        }
        if (current.strokes.none { it.id == strokeId }) {
            return KuroOperationResult(
                status = OperationStatus.INVALID_UNDO_TARGET,
                project = current,
                message = "The target stroke no longer exists in the current project state."
            )
        }
        val revision = current.revision + 1
        val next = current.copy(
            revision = revision,
            strokes = current.strokes.filterNot { it.id == strokeId },
            auditLog = current.auditLog + KuroAuditEvent(
                operationId = operation.operationId,
                revision = revision,
                actor = operation.actor,
                action = "undo_add_canvas_stroke",
                targetId = strokeId,
                summary = "Undid creation of canvas stroke."
            )
        )
        return KuroOperationResult(
            status = OperationStatus.COMMITTED,
            project = next,
            message = "Canvas stroke removed and undo recorded in audit log.",
            affectedObjectId = strokeId
        )
    }

}
