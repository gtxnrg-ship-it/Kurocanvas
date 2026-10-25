package com.kurocanvas.project

import kotlin.test.Test
import kotlin.test.assertEquals

class KuroStoryGraphPersistenceTest {
    @Test
    fun relationshipsEventsAndLayersSurviveProjectCodecRoundTrip() {
        val broker = KuroOperationBroker()
        var project = KuroProjectState.empty(projectId = "graph-persistence")
        val addedA = broker.execute(
            project,
            AddStoryEntity("entity-a-op", project.projectId, project.revision, KuroStoryEntity("a", "Mira"))
        )
        project = addedA.project
        val addedB = broker.execute(
            project,
            AddStoryEntity("entity-b-op", project.projectId, project.revision, KuroStoryEntity("b", "Jin"))
        )
        project = addedB.project
        val addedLayer = broker.execute(
            project,
            AddCanvasLayer("layer-op", project.projectId, project.revision, KuroLayer("layer-2", "Sketch", 1))
        )
        project = addedLayer.project
        val addedRelationship = broker.execute(
            project,
            AddStoryRelationship(
                "relationship-op",
                project.projectId,
                project.revision,
                KuroStoryRelationship(
                    "relationship-1",
                    "a",
                    "b",
                    "friends",
                    branchId = "main",
                    fromChapter = 1,
                    untilChapterExclusive = 10
                )
            )
        )
        project = addedRelationship.project
        val addedEvent = broker.execute(
            project,
            AddStoryEvent(
                "event-op",
                project.projectId,
                project.revision,
                KuroStoryEvent(
                    "event-1",
                    "They meet",
                    chapter = 2,
                    participantEntityIds = listOf("a", "b")
                )
            )
        )
        assertEquals(OperationStatus.COMMITTED, addedEvent.status)

        val decoded = KuroProjectCodec.decode(KuroProjectCodec.encode(addedEvent.project))

        assertEquals(addedEvent.project, decoded)
        assertEquals("friends", decoded.relationships.single().relationshipType)
        assertEquals("They meet", decoded.storyEvents.single().title)
        assertEquals(2, decoded.layers.size)
    }
}
