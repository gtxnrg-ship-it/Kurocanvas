package com.kurocanvas.project

import kotlin.test.Test
import kotlin.test.assertEquals

class KuroStoryGraphTest {
    private val broker = KuroOperationBroker()

    private fun character(id: String, name: String, revision: Long) =
        AddStoryEntity("entity-op-$id", "graph", revision, KuroStoryEntity(id, name))

    @Test
    fun relationshipsAreBranchAndChapterScoped() {
        var state = KuroProjectState.empty(projectId = "graph")
        state = broker.execute(state, character("a", "Mira", state.revision)).project
        state = broker.execute(state, character("b", "Jin", state.revision)).project
        val relation = KuroStoryRelationship(
            id = "rel-1",
            fromEntityId = "a",
            toEntityId = "b",
            relationshipType = "friends",
            branchId = "main",
            fromChapter = 2,
            untilChapterExclusive = 8
        )
        val added = broker.execute(
            state,
            AddStoryRelationship("rel-op-1", state.projectId, state.revision, relation)
        )
        assertEquals(OperationStatus.COMMITTED, added.status)
        assertEquals(listOf("rel-1"), added.project.relationshipsFor("a", 4).map { it.id })
        assertEquals(emptyList(), added.project.relationshipsFor("a", 8))
        assertEquals(emptyList(), added.project.relationshipsFor("a", 4, "alternate"))
    }

    @Test
    fun eventsAreOrderedAndValidateEveryParticipant() {
        var state = KuroProjectState.empty(projectId = "graph")
        state = broker.execute(state, character("a", "Mira", state.revision)).project
        val first = broker.execute(
            state,
            AddStoryEvent(
                "event-op-1",
                state.projectId,
                state.revision,
                KuroStoryEvent("event-2", "The confession", chapter = 12, participantEntityIds = listOf("a"))
            )
        )
        val second = broker.execute(
            first.project,
            AddStoryEvent(
                "event-op-2",
                first.project.projectId,
                first.project.revision,
                KuroStoryEvent("event-1", "First meeting", chapter = 1, participantEntityIds = listOf("a"))
            )
        )
        assertEquals(OperationStatus.COMMITTED, first.status)
        assertEquals(OperationStatus.COMMITTED, second.status)
        assertEquals(listOf("First meeting", "The confession"), second.project.eventsInTimeline().map { it.title })

        val invalid = broker.execute(
            second.project,
            AddStoryEvent(
                "event-op-3",
                second.project.projectId,
                second.project.revision,
                KuroStoryEvent("event-invalid", "Unknown person", chapter = 14, participantEntityIds = listOf("missing"))
            )
        )
        assertEquals(OperationStatus.MISSING_EVENT_PARTICIPANT, invalid.status)
        assertEquals(second.project, invalid.project)
    }

    @Test
    fun eventUndoPreservesAuditRecord() {
        val state = KuroProjectState.empty(projectId = "graph")
        val added = broker.execute(
            state,
            AddStoryEvent("event-op-1", state.projectId, state.revision, KuroStoryEvent("event-1", "Meet", 1))
        )
        val undone = broker.execute(
            added.project,
            UndoAddedStoryEvent("event-undo-1", state.projectId, added.project.revision, "event-op-1")
        )
        assertEquals(OperationStatus.COMMITTED, undone.status)
        assertEquals(emptyList(), undone.project.storyEvents)
        assertEquals("undo_add_story_event", undone.project.auditLog.last().action)
    }

    @Test
    fun relationshipUndoPreservesAuditRecord() {
        var state = KuroProjectState.empty(projectId = "graph")
        state = broker.execute(state, character("a", "Mira", state.revision)).project
        state = broker.execute(state, character("b", "Jin", state.revision)).project
        val added = broker.execute(
            state,
            AddStoryRelationship(
                "rel-op-1",
                state.projectId,
                state.revision,
                KuroStoryRelationship("rel-1", "a", "b", "rivals", fromChapter = 1)
            )
        )
        val undone = broker.execute(
            added.project,
            UndoAddedStoryRelationship("rel-undo-1", state.projectId, added.project.revision, "rel-op-1")
        )
        assertEquals(OperationStatus.COMMITTED, undone.status)
        assertEquals(emptyList(), undone.project.relationships)
        assertEquals("undo_add_story_relationship", undone.project.auditLog.last().action)
    }
}
