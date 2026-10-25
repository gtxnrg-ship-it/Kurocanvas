package com.kurocanvas.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KuroStoryContextCompilerTest {
    private val broker = KuroOperationBroker()

    private fun addEntity(project: KuroProjectState, id: String, name: String): KuroProjectState =
        broker.execute(
            project,
            AddStoryEntity(
                operationId = "entity-$id",
                projectId = project.projectId,
                expectedRevision = project.revision,
                entity = KuroStoryEntity(id, name)
            )
        ).project

    @Test
    fun compilesOnlyRelevantCanonForRequestedChapterAndBranch() {
        var project = KuroProjectState.empty(projectId = "context")
        project = addEntity(project, "mira", "Mira")
        project = addEntity(project, "jin", "Jin")
        project = broker.execute(
            project,
            AddCanonFact(
                "fact-hair",
                project.projectId,
                project.revision,
                KuroCanonFact("hair", "mira", "Hair color", "Black", fromChapter = 1, untilChapterExclusive = 8)
            )
        ).project
        project = broker.execute(
            project,
            AddStoryRelationship(
                "relationship-op",
                project.projectId,
                project.revision,
                KuroStoryRelationship("friends", "mira", "jin", "friends", fromChapter = 2, untilChapterExclusive = 6)
            )
        ).project
        project = broker.execute(
            project,
            AddStoryEvent(
                "event-1-op",
                project.projectId,
                project.revision,
                KuroStoryEvent("event-1", "First meeting", chapter = 1, participantEntityIds = listOf("mira"))
            )
        ).project
        project = broker.execute(
            project,
            AddStoryEvent(
                "event-3-op",
                project.projectId,
                project.revision,
                KuroStoryEvent("event-3", "Promise", chapter = 3, participantEntityIds = listOf("mira", "jin"))
            )
        ).project
        project = broker.execute(
            project,
            AddStoryEvent(
                "alt-event-op",
                project.projectId,
                project.revision,
                KuroStoryEvent("alt-event", "Alternate timeline", chapter = 3, branchId = "alternate")
            )
        ).project

        val compiled = KuroStoryContextCompiler.compile(
            project,
            KuroContextRequest(
                chapter = 3,
                branchId = "main",
                entityIds = setOf("mira"),
                requestedPredicates = setOf("Hair color", "Height"),
                lookbackChapters = 1
            )
        )

        assertEquals(KuroContextStatus.READY, compiled.status)
        assertEquals(setOf("mira", "jin"), compiled.entities.map { it.id }.toSet())
        assertEquals(listOf("Black"), compiled.facts.map { it.value })
        assertEquals(listOf("friends"), compiled.relationships.map { it.relationshipType })
        assertEquals(listOf("Promise"), compiled.recentEvents.map { it.title })
        assertTrue(compiled.issues.any { it.kind == KuroContextIssueKind.UNKNOWN_FACT && it.predicate == "Height" })
        assertTrue(compiled.renderForModel().contains("Unknown facts are unknown"))
    }

    @Test
    fun refusesUnscopedOrInvalidRequestsRatherThanSendingWholeStory() {
        val project = KuroProjectState.empty(projectId = "context")
        val unscoped = KuroStoryContextCompiler.compile(project, KuroContextRequest(chapter = 1, entityIds = emptySet()))
        val invalid = KuroStoryContextCompiler.compile(project, KuroContextRequest(chapter = 0, entityIds = setOf("missing")))

        assertEquals(KuroContextStatus.STORY_SCOPE_NOT_FOUND, unscoped.status)
        assertEquals(KuroContextStatus.INVALID_REQUEST, invalid.status)
        assertTrue(unscoped.renderForModel().contains("Do not invent missing canon"))
    }

    @Test
    fun proposedRecordsAreClearlyLabelledAsUnconfirmed() {
        var project = KuroProjectState.empty(projectId = "context-proposed")
        project = addEntity(project, "mira", "Mira")
        project = broker.execute(
            project,
            AddCanonFact(
                "fact-proposed-op",
                project.projectId,
                project.revision,
                KuroCanonFact(
                    "fact-proposed",
                    "mira",
                    "Hair color",
                    "Silver",
                    status = KuroCanonFactStatus.PROPOSED,
                    fromChapter = 1
                )
            )
        ).project

        val compiled = KuroStoryContextCompiler.compile(
            project,
            KuroContextRequest(chapter = 1, entityIds = setOf("mira"), includeProposed = true)
        )
        val rendered = compiled.renderForModel()

        assertTrue(compiled.includesProposedRecords)
        assertTrue(rendered.contains("[proposed]"))
        assertTrue(rendered.contains("must never be treated as established facts"))
    }

    @Test
    fun contextDoesNotUseFactsFromAnotherTimelineBranch() {
        var project = KuroProjectState.empty(projectId = "context")
        project = addEntity(project, "mira", "Mira")
        project = broker.execute(
            project,
            AddCanonFact(
                "fact-alt-op",
                project.projectId,
                project.revision,
                KuroCanonFact("fact-alt", "mira", "Status", "Dead", branchId = "alternate", fromChapter = 1)
            )
        ).project

        val compiled = KuroStoryContextCompiler.compile(
            project,
            KuroContextRequest(
                chapter = 2,
                branchId = "main",
                entityIds = setOf("mira"),
                requestedPredicates = setOf("Status")
            )
        )

        assertEquals(emptyList(), compiled.facts)
        assertTrue(compiled.issues.any { it.kind == KuroContextIssueKind.UNKNOWN_FACT })
    }
}
