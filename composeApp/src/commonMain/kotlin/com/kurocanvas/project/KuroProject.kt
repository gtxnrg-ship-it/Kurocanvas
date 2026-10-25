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
enum class KuroStoryEntityType { CHARACTER, LOCATION, ORGANIZATION, OBJECT, EVENT }

@Serializable
enum class KuroCanonFactStatus { PROPOSED, CONFIRMED, RETRACTED }

@Serializable
data class KuroStoryEntity(
    val id: String,
    val name: String,
    val type: KuroStoryEntityType = KuroStoryEntityType.CHARACTER,
    val aliases: List<String> = emptyList(),
    val createdRevision: Long = 0L
)

@Serializable
data class KuroCanonFact(
    val id: String,
    val subjectId: String,
    val predicate: String,
    val value: String,
    val branchId: String = "main",
    val fromChapter: Int,
    /** Exclusive upper bound; null means the fact remains valid indefinitely. */
    val untilChapterExclusive: Int? = null,
    val status: KuroCanonFactStatus = KuroCanonFactStatus.CONFIRMED,
    val sourceNote: String = "",
    val createdRevision: Long = 0L
)

@Serializable
data class KuroProjectState(
    val projectId: String,
    val title: String,
    val revision: Long,
    val panels: List<StoryboardPanel>,
    val auditLog: List<KuroAuditEvent>,
    val nextPanelNumber: Int,
    val strokes: List<KuroStroke> = emptyList(),
    val storyEntities: List<KuroStoryEntity> = emptyList(),
    val canonFacts: List<KuroCanonFact> = emptyList(),
    val storyTitle: String = "Untitled story"
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
            strokes = emptyList(),
            storyEntities = emptyList(),
            canonFacts = emptyList(),
            storyTitle = "Untitled story"
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

data class AddStoryEntity(
    override val operationId: String,
    override val projectId: String,
    override val expectedRevision: Long,
    val entity: KuroStoryEntity,
    override val actor: String = "user"
) : KuroOperation

data class AddCanonFact(
    override val operationId: String,
    override val projectId: String,
    override val expectedRevision: Long,
    val fact: KuroCanonFact,
    override val actor: String = "user"
) : KuroOperation

data class UndoAddedStoryItem(
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
    INVALID_STROKE,
    INVALID_STORY_ENTITY,
    INVALID_CANON_FACT,
    MISSING_CANON_SUBJECT,
    CANON_CONTRADICTION,
    DEPENDENCY_EXISTS,
    DUPLICATE_OBJECT_ID
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
 * AI model access and plugin execution are intentionally outside this broker. Every operation
 * is typed, revision-checked, dependency-checked where needed, and recorded in the audit log.
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
            is AddStoryEntity -> addStoryEntity(current, operation)
            is AddCanonFact -> addCanonFact(current, operation)
            is UndoAddedStoryItem -> undoStoryItem(current, operation)
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

    private fun addStoryEntity(
        current: KuroProjectState,
        operation: AddStoryEntity
    ): KuroOperationResult {
        val entity = operation.entity
        val name = entity.name.trim()
        if (entity.id.isBlank() || name.isBlank()) {
            return KuroOperationResult(
                OperationStatus.INVALID_STORY_ENTITY,
                current,
                "A story entity needs a stable ID and a non-empty name."
            )
        }
        if (current.storyEntities.any { it.id == entity.id }) {
            return KuroOperationResult(
                OperationStatus.DUPLICATE_OBJECT_ID,
                current,
                "This story entity ID already exists."
            )
        }
        if (current.storyEntities.any { it.type == entity.type && it.name.equals(name, ignoreCase = true) }) {
            return KuroOperationResult(
                OperationStatus.DUPLICATE_OBJECT_ID,
                current,
                "An entity of this type with that name already exists."
            )
        }
        val revision = current.revision + 1
        val savedEntity = entity.copy(
            name = name,
            aliases = entity.aliases.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            createdRevision = revision
        )
        val next = current.copy(
            revision = revision,
            storyEntities = current.storyEntities + savedEntity,
            auditLog = current.auditLog + KuroAuditEvent(
                operationId = operation.operationId,
                revision = revision,
                actor = operation.actor,
                action = "add_story_entity",
                targetId = savedEntity.id,
                summary = "Added story entity: $name."
            )
        )
        return KuroOperationResult(
            OperationStatus.COMMITTED,
            next,
            "Story entity committed to the project.",
            savedEntity.id
        )
    }

    private fun addCanonFact(
        current: KuroProjectState,
        operation: AddCanonFact
    ): KuroOperationResult {
        val fact = operation.fact
        if (fact.id.isBlank() || fact.predicate.isBlank() || fact.value.isBlank() ||
            fact.branchId.isBlank() || fact.fromChapter < 1 ||
            (fact.untilChapterExclusive != null && fact.untilChapterExclusive <= fact.fromChapter)
        ) {
            return KuroOperationResult(
                OperationStatus.INVALID_CANON_FACT,
                current,
                "A canon fact needs a predicate, value, branch and valid chapter interval."
            )
        }
        if (current.storyEntities.none { it.id == fact.subjectId }) {
            return KuroOperationResult(
                OperationStatus.MISSING_CANON_SUBJECT,
                current,
                "Add or select a story entity before recording facts about it."
            )
        }
        if (current.canonFacts.any { it.id == fact.id }) {
            return KuroOperationResult(
                OperationStatus.DUPLICATE_OBJECT_ID,
                current,
                "This canon fact ID already exists."
            )
        }
        if (fact.status == KuroCanonFactStatus.CONFIRMED) {
            val end = fact.untilChapterExclusive ?: Int.MAX_VALUE
            val conflict = current.canonFacts.firstOrNull { existing ->
                existing.status == KuroCanonFactStatus.CONFIRMED &&
                    existing.subjectId == fact.subjectId &&
                    existing.predicate.equals(fact.predicate.trim(), ignoreCase = true) &&
                    existing.branchId == fact.branchId &&
                    !existing.value.equals(fact.value.trim(), ignoreCase = true) &&
                    fact.fromChapter < (existing.untilChapterExclusive ?: Int.MAX_VALUE) &&
                    existing.fromChapter < end
            }
            if (conflict != null) {
                return KuroOperationResult(
                    OperationStatus.CANON_CONTRADICTION,
                    current,
                    "This confirmed fact conflicts with another confirmed value over an overlapping chapter range. Resolve or explicitly re-time the old fact first."
                )
            }
        }
        val revision = current.revision + 1
        val savedFact = fact.copy(
            predicate = fact.predicate.trim(),
            value = fact.value.trim(),
            branchId = fact.branchId.trim(),
            sourceNote = fact.sourceNote.trim(),
            createdRevision = revision
        )
        val next = current.copy(
            revision = revision,
            canonFacts = current.canonFacts + savedFact,
            auditLog = current.auditLog + KuroAuditEvent(
                operationId = operation.operationId,
                revision = revision,
                actor = operation.actor,
                action = "add_canon_fact",
                targetId = savedFact.id,
                summary = "Recorded a time-scoped canon fact."
            )
        )
        return KuroOperationResult(
            OperationStatus.COMMITTED,
            next,
            "Canon fact committed to the project.",
            savedFact.id
        )
    }

    private fun undoStoryItem(
        current: KuroProjectState,
        operation: UndoAddedStoryItem
    ): KuroOperationResult {
        val target = current.auditLog.lastOrNull { it.operationId == operation.targetOperationId }
        if (target == null || target.action !in setOf("add_story_entity", "add_canon_fact")) {
            return KuroOperationResult(
                OperationStatus.INVALID_UNDO_TARGET,
                current,
                "Only a newly added story entity or canon fact can be undone here."
            )
        }
        if (current.auditLog.any { it.action == "undo_story_item" && it.targetId == target.targetId }) {
            return KuroOperationResult(
                OperationStatus.ALREADY_UNDONE,
                current,
                "This story item has already been undone."
            )
        }
        if (target.action == "add_story_entity" &&
            current.canonFacts.any { it.subjectId == target.targetId && it.status != KuroCanonFactStatus.RETRACTED }
        ) {
            return KuroOperationResult(
                OperationStatus.DEPENDENCY_EXISTS,
                current,
                "This entity is referenced by canon facts. Undo or reassign those facts first."
            )
        }
        val revision = current.revision + 1
        val next = when (target.action) {
            "add_story_entity" -> current.copy(
                revision = revision,
                storyEntities = current.storyEntities.filterNot { it.id == target.targetId },
                auditLog = current.auditLog + KuroAuditEvent(
                    operationId = operation.operationId,
                    revision = revision,
                    actor = operation.actor,
                    action = "undo_story_item",
                    targetId = target.targetId,
                    summary = "Undid creation of story entity."
                )
            )
            else -> current.copy(
                revision = revision,
                canonFacts = current.canonFacts.filterNot { it.id == target.targetId },
                auditLog = current.auditLog + KuroAuditEvent(
                    operationId = operation.operationId,
                    revision = revision,
                    actor = operation.actor,
                    action = "undo_story_item",
                    targetId = target.targetId,
                    summary = "Undid creation of canon fact."
                )
            )
        }
        return KuroOperationResult(
            OperationStatus.COMMITTED,
            next,
            "Story item undone and retained in audit history.",
            target.targetId
        )
    }

}
